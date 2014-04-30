/*
 * Copyright (C) 2010-2014 GRNET S.A.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package gr.grnet.cdmi.service

import com.ning.http.client
import com.ning.http.client.{AsyncCompletionHandler, AsyncHttpClient}
import com.twitter.app.App
import com.twitter.app.GlobalFlag
import com.twitter.finagle.http.Status
import com.twitter.logging.{Logging, Level}
import com.twitter.util.{Return, Throw, Promise, Future}
import gr.grnet.cdmi.metadata.StorageSystemMetadata
import gr.grnet.cdmi.model.{ObjectModel, Model, ContainerModel}
import gr.grnet.common.http.{StdContentType, StdHeader}
import gr.grnet.common.io.{Base64, CloseAnyway, DeleteAnyway}
import gr.grnet.common.json.Json
import gr.grnet.common.text.{ParentUri, RemovePrefix, NoTrailingSlash}
import gr.grnet.pithosj.api.PithosApi
import gr.grnet.pithosj.core.ServiceInfo
import gr.grnet.pithosj.core.keymap.PithosHeaderKeys
import gr.grnet.pithosj.impl.asynchttp.PithosClientFactory
import java.io.{FileOutputStream, File}
import java.nio.file.Files
import java.util.Locale
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object pithosURL    extends GlobalFlag[String] ("https://pithos.okeanos.grnet.gr/object-store/v1", "Pithos service URL")
object pithosUUID   extends GlobalFlag[String] ("", "Pithos (Astakos) UUID. Usually set for debugging")
object pithosToken  extends GlobalFlag[String] ("", "Pithos (Astakos) Token. Set this only for debugging")
object authURL      extends GlobalFlag[String] ("https://okeanos-occi2.hellasgrid.gr:5000/main", "auth proxy")
object authRedirect extends GlobalFlag[Boolean](true, "Redirect to 'authURL' if token is not present (in an attempt to get one)")
object tokensURL    extends GlobalFlag[String]("https://accounts.okeanos.grnet.gr/identity/v2.0/tokens", "Used to obtain UUID from token")

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
object StdCdmiPithosServer extends CdmiRestService with App with Logging {
  sealed trait PithosResult[+T]
  final case class GoodPithosResult[T](value: T) extends PithosResult[T]
  final case class BadPithosResult(status: HttpResponseStatus, extraInfo: String = "") extends PithosResult[Nothing]

  final case class GetFileInfo(file: File, contentType: String)

  override def defaultLogLevel: Level = Level.DEBUG

  val asyncHttp: AsyncHttpClient = PithosClientFactory.newDefaultAsyncHttpClient
  val pithos: PithosApi = PithosClientFactory.newPithosClient(asyncHttp)

  def getPithosURL(request: Request): String = {
    val headers = request.headers()

    if(!headers.contains("X-Pithos-URL")) {
      if(!pithosURL().isEmpty) {
        headers.set("X-Pithos-URL", pithosURL())
      }
    }

    headers.get("X-Pithos-URL")
  }

  def getPithosUUID(request: Request): String = {
    val headers = request.headers()

    if(!headers.contains("X-Pithos-UUID")) {
      if(!pithosUUID().isEmpty) {
        headers.set("X-Pithos-UUID", pithosUUID())
      }
    }

    headers.get("X-Pithos-UUID")
  }

  def getPithosToken(request: Request): String = {
    val headers = request.headers()

    if(!headers.contains("X-Pithos-Token")) {
      if(headers.contains("X-Auth-Token")) {
        headers.set("X-Pithos-Token", headers.get("X-Auth-Token"))
      }
    }

    headers.get("X-Pithos-Token")
  }

  def getPithosServiceInfo(request: Request): ServiceInfo = {
    val headers = request.headers()

    ServiceInfo(
      serviceURL = headers.get("X-Pithos-URL"),
      uuid       = headers.get("X-Pithos-UUID"),
      token      = headers.get("X-Pithos-Token")
    )
  }

  val authFilter = new Filter {
    def authenticate(request: Request): Future[Response] = {
      val response = request.response
      response.status = Status.Unauthorized
      val rh = response.headers()
      rh.set(StdHeader.Content_Type.headerName(), StdContentType.Text_Html.contentType())
      rh.set(StdHeader.WWW_Authenticate.headerName(), s"Keystone uri='${authURL()}'")
      rh.set(StdHeader.Content_Length.headerName(), "0")

      response.future
    }

    override def apply(request: Request, service: Service): Future[Response] = {
      if(isCdmiCapabilitiesUri(request.uri)) {
        return service(request)
      }

      // If we do not have the X-Auth-Token header present, then we need to send the user for authentication
      getPithosToken(request) match {
        case null if authRedirect() ⇒
          authenticate(request)

        case _ ⇒
          service(request)
      }
    }
  }

  val postTokensJsonFmt = """{ "auth": { "token": { "id": "%s" } } }"""

  val uuidCheck = new Filter {
    // http://www.synnefo.org/docs/synnefo/latest/identity-api-guide.html#tokens-api-operations
    def postTokens(request: Request): Future[PithosResult[String]] = {
      val jsonFmt = postTokensJsonFmt
      val token = getPithosToken(request)
      val jsonPayload = jsonFmt.format(token)

      val promise = newResultPromise[String]

      val reqBuilder = asyncHttp.preparePost(tokensURL())
      reqBuilder.setHeader(StdHeader.Content_Type.headerName(), StdContentType.Application_Json.contentType())
      reqBuilder.setBody(jsonPayload)
      val handler = new AsyncCompletionHandler[Unit] {
        override def onThrowable(t: Throwable): Unit =
          promise.setException(t)

        override def onCompleted(response: client.Response): Unit = {
          val statusCode = response.getStatusCode
          val statusText = response.getStatusText

          statusCode match {
            case 200 ⇒
              val body = response.getResponseBody
              promise.setValue(GoodPithosResult(body))

            case _ ⇒
              promise.setValue(BadPithosResult(new HttpResponseStatus(statusCode, statusText)))
          }
        }
      }
      reqBuilder.execute(handler)

      promise
    }

    override def apply(request: Request, service: Service): Future[Response] = {
      if(isCdmiCapabilitiesUri(request.uri)) {
        return service(request)
      }

      getPithosUUID(request) match {
        case null if getPithosToken(request) ne null ⇒
          postTokens(request).transform {
            case Return(GoodPithosResult(jsonResponse)) ⇒
              val jsonTree = Json.jsonStringToTree(jsonResponse)

              if(jsonTree.has("access")) {
                val accessTree = jsonTree.get("access")
                if(accessTree.has("token")) {
                  val tokenTree = accessTree.get("token")
                  if(tokenTree.has("tenant")) {
                    val tenantTree = tokenTree.get("tenant")
                    if(tenantTree.has("id")) {
                      val idTree = tenantTree.get("id")
                      if(idTree.isTextual) {
                        val uuid = idTree.asText()
                        request.headers().set("X-Pithos-UUID", uuid)
                        log.info(s"Derived X-Pithos-UUID: ${uuid}")
                      }
                    }
                  }
                }
              }

              getPithosUUID(request) match {
                case null ⇒
                  // still not found
                  internalServerError(request, new Exception(s"Could not retrieve UUID from ${tokensURL()}"))
                case _ ⇒
                  service(request)
              }

            case Return(BadPithosResult(status, extraInfo)) ⇒
              response(request, status, extraInfo, "BadPithosResult").future

            case Throw(t) ⇒
              log.error(s"Calling ${tokensURL()}")
              internalServerError(request, t)
          }

        case uuid if uuid ne null ⇒
          log.info(s"Given X-Pithos-UUID: ${uuid}")
          service(request)

        case _ ⇒
          service(request)
      }
    }
  }

  val pithosHeadersFilter = new Filter {
    override def apply(request: Request, service: Service): Future[Response] = {
      // Pithos header check is needed only for URIs that result in calling Pithos
      if(isCdmiCapabilitiesUri(request.uri)) {
        return service(request)
      }

      val errorBuffer = new java.lang.StringBuilder()
      def addError(s: String): Unit = {
        errorBuffer.append(s)
        errorBuffer.append('\n')
      }

      val url = getPithosURL(request)
      val uuid = getPithosUUID(request)
      val token = getPithosToken(request)
      if((url eq null) || url.isEmpty) {
        addError("No Pithos+ service URL. Please set header X-Pithos-URL")
      }

      if((uuid eq null) || uuid.isEmpty) {
        addError("No Pithos+ UUID. Please set header X-Pithos-UUID")
      }

      if((token eq null) || token.isEmpty) {
        addError("No Pithos+ user token. Please set header X-Pithos-Token or X-Auth-Token")
      }

      if(errorBuffer.length() > 0) {
        response(request, Status.BadRequest, errorBuffer).future
      }
      else {
        service(request)
      }
    }
  }

  val myFilters = Vector(authFilter, uuidCheck, pithosHeadersFilter)
  override def mainFilters = super.mainFilters ++ myFilters

  override def flags: Seq[GlobalFlag[_]] = super.flags ++ Seq(pithosURL, pithosUUID, authURL, authRedirect, tokensURL)

  def newResultPromise[T]: Promise[PithosResult[T]] = Promise[PithosResult[T]]()

  def fixPathFromContentType(path: String, contentType: String): String =
    contentType match {
      case "application/directory" | "application/folder" ⇒
        s"${path}/"

      case contentType if contentType.startsWith("application/directory;") ||
        contentType.startsWith("application/folder;") ⇒
        s"${path}/"

      case _ ⇒
        path
    }

  def checkExistsPithosFolderOrContainer(serviceInfo: ServiceInfo, container: String, folderPath: String): Future[PithosResult[Boolean]] = {
    val promise = newResultPromise[Boolean]
    // beware that folderPath must not end in '/'.
    val sf_result = pithos.checkExistsObject(serviceInfo, container, folderPath.noTrailingSlash)
    sf_result.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          result.successData match {
            case Some(data) ⇒
              promise.setValue(GoodPithosResult(data.isDirectory || data.isContainer))

            case None ⇒
              promise.setValue(GoodPithosResult(false))
          }
        }
        else {
          promise.setValue(BadPithosResult(new HttpResponseStatus(result.statusCode, result.statusText)))
        }
      case Failure(t) ⇒
        promise.setException(t)
    }

    promise
  }

  /**
   * Lists the contents of a container.
   */
  override def GET_container(
    request: Request, containerPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val container = containerPath.head
    val path = containerPath.tail mkString "/" match { case "" ⇒ "/"; case s ⇒ "/" + s }

    checkExistsPithosFolderOrContainer(serviceInfo, container, path).flatMap {
      case BadPithosResult(status, extraInfo) ⇒
        response(request, status, extraInfo, "BadPithosResult").future

      case GoodPithosResult(false) ⇒
        response(request, Status.NotFound).future

      case GoodPithosResult(true) ⇒
        val promise = newResultPromise[Seq[String]]
        val sf_result = pithos.listObjectsInPath(serviceInfo, container, path)

        sf_result.onComplete {
          case Success(result) ⇒
            if(result.isSuccess) {
              val listObjectsInPath = result.successData.get.objects
              val children =
                for {
                  oip ← listObjectsInPath
                } yield {
                  // Pithos returns all the path part after the pithos container.
                  // Note that Pithos container is not the same as CDMI container.
                  val path = oip.path.lastIndexOf('/') match {
                    case -1 ⇒ oip.path
                    case i ⇒ oip.path.substring(i + 1)
                  }

                  fixPathFromContentType(path, oip.contentType)
                }

              promise.setValue(GoodPithosResult(children))
            }
            else {
              promise.setValue(BadPithosResult(HttpResponseStatus.valueOf(result.statusCode)))
            }

          case Failure(t) ⇒
            promise.setException(t)
        }

        promise.transform {
          case Return(GoodPithosResult(children)) ⇒
            val uri = request.uri
            val parentURI = uri.parentUri

            val container = ContainerModel(
              objectID = uri,
              objectName = uri,
              parentURI = parentURI,
              parentID = parentURI,
              domainURI = "",
              childrenrange = Model.childrenRangeOf(children),
              children = children
            )
            val jsonContainer = Json.objectToJsonString(container)
            response(request, Status.Ok, jsonContainer).future

          case Return(BadPithosResult(status, extraInfo)) ⇒
            response(request, status, extraInfo, "BadPithosResult").future

          case Throw(t) ⇒
            internalServerError(request, t)
        }
    }
  }

  /**
   * Creates a new container.
   */
  override def PUT_container(
    request: Request, containerPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val promise = newResultPromise[Unit]
    val container = containerPath.head
    val path = containerPath.tail mkString "/" match { case "" ⇒ "/"; case s ⇒ "/" + s }
    // FIXME If the folder does not exist, the result here is just an empty folder
    val sf_result = pithos.createDirectory(serviceInfo, container, path)

    sf_result.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          promise.setValue(GoodPithosResult(()))
        }
        else {
          promise.setValue(BadPithosResult(HttpResponseStatus.valueOf(result.statusCode)))
        }

      case Failure(t) ⇒
        promise.setException(t)
    }

    promise.transform {
      case Return(GoodPithosResult(_)) ⇒
        response(request, Status.Ok).future

      case Return(BadPithosResult(status, extraInfo)) ⇒
        response(request, status, extraInfo, "BadPithosResult").future

      case Throw(t) ⇒
        internalServerError(request, t)
    }
  }


  /**
   * Deletes a container.
   */
  override def DELETE_container(
    request: Request, containerPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val promise = newResultPromise[Unit]
    val container = containerPath.head
    val path = containerPath.tail mkString "/" match { case "" ⇒ "/"; case s ⇒ "/" + s }
    val sf_result = pithos.deleteDirectory(serviceInfo, container, path)

    sf_result.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          promise.setValue(GoodPithosResult(()))
        }
        else {
          promise.setValue(BadPithosResult(HttpResponseStatus.valueOf(result.statusCode)))
        }

      case Failure(t) ⇒
        promise.setException(t)
    }

    promise.transform {
      case Return(GoodPithosResult(_)) ⇒
        response(request, Status.Ok).future

      case Return(BadPithosResult(status, extraInfo)) ⇒
        response(request, status, extraInfo, "BadPithosResult").future

      case Throw(t) ⇒
        internalServerError(request, t)
    }
  }

  override def GET_objectById(
    request: Request, objectIdPath: List[String]
  ): Future[Response] = {
    // No real object IDs here
    GET_object(request, objectIdPath)
  }


  /**
   * Read the contents or value of a data object.
   */
  override def GET_object(
    request: Request, objectPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)

    val container = objectPath.head
    val path = objectPath.tail.mkString("/")

    checkExistsPithosFolderOrContainer(serviceInfo, container, path).flatMap {
      case BadPithosResult(status, extraInfo) ⇒
        response(request, status, extraInfo, "BadPithosResult").future

      case GoodPithosResult(true) ⇒
        // This is a folder or container, not a file. Go away!
        response(request, Status.NotFound).future

      case GoodPithosResult(false) ⇒
        val promise = newResultPromise[GetFileInfo]
        val tmpFile = Files.createTempFile("cdmi-pithos-", null).toFile
        val tmpFileOut = new FileOutputStream(tmpFile)
        val sf_result = pithos.getObject(serviceInfo, container, path, null, tmpFileOut)
        sf_result.onComplete {
          case Success(result) ⇒
            tmpFileOut.closeAnyway()

            if(result.isSuccess) {
              promise.setValue(
                GoodPithosResult(
                  GetFileInfo(tmpFile, result.responseHeaders.getEx(PithosHeaderKeys.Standard.Content_Type))
                )
              )
            }
            else {
              promise.setValue(BadPithosResult(HttpResponseStatus.valueOf(result.statusCode)))
            }

          case Failure(t) ⇒
            tmpFileOut.closeAnyway()
            promise.setException(t)
        }

        promise.transform {
          case Return(GoodPithosResult(GetFileInfo(file, contentType))) ⇒
            val size = file.length()
            val uri = request.uri.removePrefix("/cdmi_objectid")
            val parentURI = uri.parentUri
            val isTextPlain = contentType == "text/plain"
            val value = if(isTextPlain) new String(Files.readAllBytes(file.toPath), "UTF-8") else Base64.encodeFile(file)
            val vte = if(isTextPlain) "utf-8" else "base64"

            val model = ObjectModel(
              objectID = uri,
              objectName = uri,
              parentURI = parentURI,
              parentID = parentURI,
              domainURI = "",
              mimetype = contentType,
              metadata = Map(StorageSystemMetadata.cdmi_size.name() → size.toString),
              valuetransferencoding = vte,
              valuerange = s"0-${size - 1}",
              value = value
            )
            val jsonModel = Json.objectToJsonString(model)
            file.deleteAnyway()
            response(request, Status.Ok, jsonModel).future

          case Return(BadPithosResult(status, extraInfo)) ⇒
            response(request, status, extraInfo, "BadPithosResult").future

          case Throw(t) ⇒
            internalServerError(request, t)
        }
    }
  }

  /**
   * Create a data object in a container using CDMI content type.
   */
  override def PUT_object_cdmi(
    request: Request, objectPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val promise = newResultPromise[Unit]
    val container = objectPath.head
    val path = objectPath.tail.mkString("/")
    val uri = request.uri.removePrefix("/cdmi_objectid")

    val content = request.contentString
    val jsonTree = Json.jsonStringToTree(content)
    val mimeTypeNode = jsonTree.get("mimetype")
    val valueNode = jsonTree.get("value")
    val vteNode = jsonTree.get("valuetransferencoding")

    if((mimeTypeNode ne null) && !mimeTypeNode.isTextual) {
      return response(request, Status.BadRequest, s"Incorrect type of mimetype field [${mimeTypeNode.getNodeType}]").future
    }

    if((vteNode ne null) && !vteNode.isTextual) {
      return response(request, Status.BadRequest, s"Incorrect type of valuetransferencoding field [${mimeTypeNode.getNodeType}]").future
    }

    // Not mandated by the spec but we currently support only the presence of "value"
    if(valueNode eq null) {
      return response(request, Status.BadRequest, "value is null").future
    }
    if(!valueNode.isTextual) {
      return response(request, Status.BadRequest, s"Incorrect type of value field [${mimeTypeNode.getNodeType}]").future
    }

    val mimetype = mimeTypeNode match {
      case null ⇒ "text/plain"
      case _ ⇒ mimeTypeNode.asText()
    }

    val vte = vteNode match {
      case null ⇒
        "utf-8"

      case node ⇒
        node.asText().toLowerCase(Locale.US) match {
          case text @ ("utf-8" | "base64") ⇒
            text

          case text ⇒
            return response(request, Status.BadRequest, s"Incorrect value of valuetransferencoding field [${text}]").future
        }
    }

    val bytes = valueNode.asText() match {
      case null ⇒
        Array[Byte]()

      case utf8   if vte == "utf-8" ⇒
        utf8.getBytes(vte)

      case base64 if vte == "base64" ⇒
        Base64.decodeString(base64)
    }

    val sf_result = pithos.putObject(serviceInfo, container, path, bytes, mimetype)
    sf_result.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          promise.setValue(GoodPithosResult(()))
        }
        else {
          promise.setValue(BadPithosResult(HttpResponseStatus.valueOf(result.statusCode)))
        }

      case Failure(t) ⇒
        promise.setException(t)
    }

    promise.transform {
      case Return(GoodPithosResult(_)) ⇒
        val size = bytes.length
        val uri = request.uri.removePrefix("/cdmi_objectid")
        val parentURI = uri.parentUri
        val model = ObjectModel(
          objectID = uri,
          objectName = uri,
          parentURI = parentURI,
          parentID = parentURI,
          domainURI = "",
          mimetype = mimetype,
          metadata = Map(StorageSystemMetadata.cdmi_size.name() → size.toString),
          valuetransferencoding = vte,
          valuerange = s"0-${size - 1}",
          value = "" // TODO technically should not be present
        )

        val jsonModel = Json.objectToJsonString(model)
        response(request, Status.Ok, jsonModel).future


      case Return(BadPithosResult(status, extraInfo)) ⇒
        response(request, status, extraInfo, "BadPithosResult").future

      case Throw(t) ⇒
        internalServerError(request, t)
    }
  }


  /**
   * Create a data object in a container using non-CDMI content type.
   * The given `contentType` is guaranteed to be not null.
   */
  override def PUT_object_noncdmi(
    request: Request, objectPath: List[String], contentType: String
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val promise = newResultPromise[Unit]
    val container = objectPath.head
    val path = objectPath.tail.mkString("/")

    val payload = request.getContent()
    val sf_result = pithos.putObject(serviceInfo, container, path, payload, contentType)
    sf_result.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          promise.setValue(GoodPithosResult(()))
        }
        else {
          promise.setValue(BadPithosResult(HttpResponseStatus.valueOf(result.statusCode)))
        }

      case Failure(t) ⇒
        promise.setException(t)
    }

    promise.transform {
      case Return(GoodPithosResult(_)) ⇒
        response(request, Status.Ok).future

      case Return(BadPithosResult(status, extraInfo)) ⇒
        response(request, status, extraInfo, "BadPithosResult").future

      case Throw(t) ⇒
        internalServerError(request, t)
    }
  }

  /**
   * Delete a data object (file).
   */
  override def DELETE_object(
    request: Request, objectPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val promise = newResultPromise[Unit]
    val container = objectPath.head
    val path = objectPath.tail.mkString("/")

    val sf_result = pithos.deleteFile(serviceInfo, container, path)
    sf_result.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          promise.setValue(GoodPithosResult(()))
        }
        else {
          promise.setValue(BadPithosResult(HttpResponseStatus.valueOf(result.statusCode)))
        }

      case Failure(t) ⇒
        internalServerError(request, t)
    }

    promise.transform {
      case Return(GoodPithosResult(_)) ⇒
        response(request, Status.Ok).future

      case Return(BadPithosResult(status, extraInfo)) ⇒
        response(request, status, extraInfo, "BadPithosResult").future

      case Throw(t) ⇒
        internalServerError(request, t)
    }
  }
}