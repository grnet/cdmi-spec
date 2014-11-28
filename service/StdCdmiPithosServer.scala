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

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Files
import java.util.Locale

import com.fasterxml.jackson.databind.node.JsonNodeType
import com.ning.http.client
import com.ning.http.client.{AsyncCompletionHandler, AsyncHttpClient}
import com.twitter.app.{App, GlobalFlag}
import com.twitter.finagle.http.Status
import com.twitter.logging.{Level, Logging}
import com.twitter.util.{Future, Promise, Return, Throw}
import gr.grnet.cdmi.metadata.StorageSystemMetadata
import gr.grnet.cdmi.model.{ContainerModel, Model, ObjectModel}
import gr.grnet.common.http.{StdMediaType, StdHeader, TResult}
import gr.grnet.common.io.{Base64, CloseAnyway, DeleteAnyway}
import gr.grnet.common.json.Json
import gr.grnet.common.text.{NoTrailingSlash, ParentPath, RemovePrefix}
import gr.grnet.pithosj.api.PithosApi
import gr.grnet.pithosj.core.ServiceInfo
import gr.grnet.pithosj.core.command.CheckExistsObjectResultData
import gr.grnet.pithosj.core.keymap.PithosHeaderKeys
import gr.grnet.pithosj.impl.asynchttp.PithosClientFactory
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.{DefaultHttpResponse, HttpResponseStatus}

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object pithosTimeout extends GlobalFlag[Long](1000L * 60L * 3L /* 3 min*/, "millis to wait for Pithos response")
object pithosURL    extends GlobalFlag[String] ("https://pithos.okeanos.grnet.gr/object-store/v1", "Pithos service URL")
object pithosUUID   extends GlobalFlag[String] ("", "Pithos (Astakos) UUID. Usually set for debugging")
object pithosToken  extends GlobalFlag[String] ("", "Pithos (Astakos) Token. Set this only for debugging")
object authURL      extends GlobalFlag[String] ("https://okeanos-occi2.hellasgrid.gr:5000/main", "auth proxy")
object authRedirect extends GlobalFlag[Boolean](true, "Redirect to 'authURL' if token is not present (in an attempt to get one)")
object tokensURL    extends GlobalFlag[String]("https://accounts.okeanos.grnet.gr/identity/v2.0/tokens", "Used to obtain UUID from token")

/**
 * A Pithos-based implementation for the CDMI service
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
object StdCdmiPithosServer extends CdmiRestService
  with App with Logging
  with CdmiRestServiceTypes
  with CdmiRestServiceHandlers
  with CdmiRestServiceMethods
  with CdmiRestServiceResponse {

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
      rh.set(HeaderNames.Content_Type,     MediaTypes.Text_Html)
      rh.set(HeaderNames.WWW_Authenticate, s"Keystone uri='${authURL()}'")
      rh.set(HeaderNames.Content_Length,   "0")

      response.future
    }

    override def apply(request: Request, service: Service): Future[Response] = {
      if(isCdmiCapabilitiesUri(request.uri)) {
        return service(request)
      }

      // If we do not have the X-Auth-Token header present, then we need to send the user for authentication
      getPithosToken(request) match {
        case null if authRedirect() ⇒
          log.warning(s"Unauthenticated ${request.method.getName} ${request.uri}")
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
      reqBuilder.setHeader(StdHeader.Content_Type.headerName(), StdMediaType.Application_Json.value())
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
                        log.info(s"Derived X-Pithos-UUID: $uuid")
                      }
                    }
                  }
                }
              }

              getPithosUUID(request) match {
                case null ⇒
                  // still not found
                  internalServerError(
                    request,
                    new Exception(s"Could not retrieve UUID from ${tokensURL()}"),
                    PithosErrorRef.PIE001
                  )
                case _ ⇒
                  service(request)
              }

            case Return(bpr @ BadPithosResult(status, extraInfo)) ⇒
              textPlain(request, status, extraInfo, bpr.toString)

            case Throw(t) ⇒
              log.error(s"Calling ${tokensURL()}")
              internalServerError(request, t, PithosErrorRef.PIE009)
          }

        case uuid if uuid ne null ⇒
          log.info(s"Given X-Pithos-UUID: $uuid")
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
        if(errorBuffer.length() > 0) { errorBuffer.append('\n') }
        errorBuffer.append(s)
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
        badRequest(
          request,
          PithosErrorRef.PBR001,
          errorBuffer
        )
      }
      else {
        service(request)
      }
    }
  }

  val myFilters = Vector(authFilter, uuidCheck, pithosHeadersFilter)
  override def mainFilters = super.mainFilters ++ myFilters

  override def flags: Seq[GlobalFlag[_]] = super.flags ++ Seq(pithosTimeout, pithosURL, pithosUUID, authURL, authRedirect, tokensURL)

  def newResultPromise[T]: Promise[PithosResult[T]] = Promise[PithosResult[T]]()

  def fixPathFromContentType(path: String, contentType: String): String =
    contentType match {
      case MediaTypes.Application_Directory | MediaTypes.Application_Folder ⇒
        s"$path/"

      case MediaTypes.Application_CdmiContainer ⇒
        s"$path/"

      case _ if contentType.startsWith(MediaTypes.Application_DirectorySemi) ||
                contentType.startsWith(MediaTypes.Application_FolderSemi) ⇒
        s"$path/"

      case _ ⇒
        path
    }

  def isPithosFolderOrContainer(result: CheckExistsObjectResultData): Boolean =
    result.isDirectory || result.isContainer

  def checkExistsPithosFolderOrContainer(serviceInfo: ServiceInfo, container: String, folderPath: String): Future[PithosResult[Boolean]] = {
    val promise = newResultPromise[Boolean]
    // beware that folderPath must not end in '/'.
    val sf_result = pithos.checkExistsObject(serviceInfo, container, folderPath.noTrailingSlash)
    sf_result.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          result.successData match {
            case Some(data) ⇒
              log.debug(s"checkExistsObject('$container', '$folderPath') ⇒ $data")
              promise.setValue(GoodPithosResult(isPithosFolderOrContainer(data)))

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

  def splitPithosContainerAndPath(objectPath: List[String]): (String, String) = {
    val container = objectPath.head
    val path = objectPath.tail.mkString("/")
    (container, path)
  }

  def completeToPromise(sFuture: scala.concurrent.Future[TResult[Unit]]): Promise[PithosResult[Unit]] = {
    val tPromise = newResultPromise[Unit]

    sFuture.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          tPromise.setValue(GoodPithosResult(()))
        }
        else {
          val errorDetails = result.errorDetails.getOrElse("")
          tPromise.setValue(BadPithosResult(HttpResponseStatus.valueOf(result.statusCode), extraInfo = errorDetails))
        }

      case Failure(t) ⇒
        tPromise.setException(t)
    }

    tPromise
  }

  def completeToPromiseAndTransform[B](sFuture: scala.concurrent.Future[TResult[Unit]])(transform: com.twitter.util.Try[PithosResult[Unit]] ⇒ Future[B]): Future[B] =
    completeToPromise(sFuture).transform(transform)


  /**
   * We delegate to `DELETE_object_cdmi`.
   *
   * @note The relevant sections from CDMI 1.0.2 are 8.8, 11.5 and 11.7.
   */
  def DELETE_object_or_queue_or_queuevalue_cdmi(request: Request, path: List[String]): Future[StdCdmiPithosServer.Response] = {
    // We support only data objects
    DELETE_object_cdmi(request, path)
  }

  /**
   * Lists the contents of a container.
   */
  override def GET_container_cdmi(
    request: Request, containerPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val container = containerPath.head
    val path = containerPath.tail mkString "/" match { case "" ⇒ "/"; case s ⇒ "/" + s }

    checkExistsPithosFolderOrContainer(serviceInfo, container, path).flatMap {
      case bpr @ BadPithosResult(status, extraInfo) ⇒
        textPlain(request, status, extraInfo, bpr.toString)

      case GoodPithosResult(false) ⇒
        notFound(request)

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
                  log.debug(s"Child: '${oip.container}/${oip.path}' = ${oip.contentType}")
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
            val requestPath = request.path
            val parentPath = requestPath.parentPath

            val container = ContainerModel(
              objectID = requestPath,
              objectName = requestPath,
              parentURI = parentPath,
              parentID = parentPath,
              domainURI = "",
              childrenrange = Model.childrenRangeOf(children),
              children = children
            )
            val jsonContainer = Json.objectToJsonString(container)
            okAppCdmiContainer(request, jsonContainer)

          case Return(bpr @ BadPithosResult(status, extraInfo)) ⇒
            textPlain(request, status, extraInfo, bpr.toString)

          case Throw(t) ⇒
            internalServerError(request, t, PithosErrorRef.PIE002)
        }
    }
  }

  def PUT_container_(
    request: Request, containerPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val container = containerPath.head
    val isContainerRoot = containerPath.tail.isEmpty
    val path = containerPath.tail mkString "/" match { case "" ⇒ "/"; case s ⇒ "/" + s }
    // FIXME If the folder does not exist, the result here is just an empty folder
    val sf_result = pithos.createDirectory(serviceInfo, container, path)

    completeToPromiseAndTransform(sf_result) {
      case Return(GoodPithosResult(_)) ⇒

        val requestPath = request.uri
        val parentPath = requestPath.parentPath
        val children = Seq()

        val container = ContainerModel(
          objectID = requestPath,
          objectName = requestPath,
          parentURI = parentPath,
          parentID = parentPath,
          domainURI = "",
          childrenrange = Model.childrenRangeOf(children),
          children = children
        )
        val jsonContainer = Json.objectToJsonString(container)
        okAppCdmiContainer(request, jsonContainer)

      case Return(bpr @ BadPithosResult(status, extraInfo)) ⇒
        badRequest(
          request,
          PithosErrorRef.PBR002,
          bpr.toString
        )

      case Throw(t) ⇒
        internalServerError(request, t, PithosErrorRef.PIE003)
    }
  }


  /**
   * Creates a container using CDMI content type.
   *
   * @note Section 9.2 of CDMI 1.0.2: Create a Container Object using CDMI Content Type
   */
  override def PUT_container_cdmi_create(
    request: Request, containerPath: List[String]
  ): Future[Response] =
    PUT_container_(request, containerPath)


  /**
   * Creates/updates a container using CDMI content type.
   *
   * @note Section 9.2 of CDMI 1.0.2: Create a Container Object using CDMI Content Type
   * @note Section 9.5 of CDMI 1.0.2: Update a Container Object using CDMI Content Type
   */
  override def PUT_container_cdmi_create_or_update(
    request: Request, containerPath: List[String]
  ): Future[Response] =
    PUT_container_(request, containerPath)
  
  def DELETE_container_(
    request: Request, containerPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val container = containerPath.head
    val path = containerPath.tail mkString "/" match { case "" ⇒ "/"; case s ⇒ "/" + s }
    val sf_result = pithos.deleteDirectory(serviceInfo, container, path)

    completeToPromiseAndTransform(sf_result) {
      case Return(GoodPithosResult(_)) ⇒
        okTextPlain(request)

      case Return(bpr @ BadPithosResult(status, extraInfo)) ⇒
        textPlain(request, status, extraInfo, bpr.toString)

      case Throw(t) ⇒
        internalServerError(request, t, PithosErrorRef.PIE004)
    }
  }

  /**
   * Deletes a container using a CDMI content type.
   *
   * @note Section 9.6 of CDMI 1.0.2: Delete a Container Object using CDMI Content Type
   */
  override def DELETE_container_cdmi(
    request: Request, containerPath: List[String]
  ): Future[Response] = DELETE_container_(request, containerPath)

  /**
   * Deletes a container using a non-CDMI content type.
   *
   * @note Section 9.7 of CDMI 1.0.2: Delete a Container Object using a Non-CDMI Content Type
   */
  override def DELETE_container_noncdmi(
    request: Request, containerPath: List[String]
  ): Future[Response] = DELETE_container_(request, containerPath)

  def GET_object_(
    request: Request,
    objectPath: List[String]
  )(continuation: (GetFileInfo) ⇒ Future[Response]): Future[Response] = {
    val serviceInfo = getPithosServiceInfo(request)

    val (container, path) = splitPithosContainerAndPath(objectPath)

    checkExistsPithosFolderOrContainer(serviceInfo, container, path).flatMap {
      case bpr @ BadPithosResult(status, extraInfo) ⇒
        textPlain(request, status, extraInfo, bpr.toString)

      case GoodPithosResult(true) ⇒
        // This is a folder or container, not a file. Go away!
        notFound(request)

      case GoodPithosResult(false) ⇒
        val promise = newResultPromise[GetFileInfo]
        val tmpFile = Files.createTempFile("cdmi-pithos-", null).toFile
        val tmpFileOut = new FileOutputStream(tmpFile)
        log.info(s"Saving file at $container/$path to ${tmpFile.getAbsolutePath}")
        val sf_result = pithos.getObject(serviceInfo, container, path, null, tmpFileOut)
        sf_result.onComplete {
          case Success(result) ⇒
            tmpFileOut.closeAnyway()

            if(result.isSuccess) {
              val resultContentType = result.responseHeaders.getEx(PithosHeaderKeys.Standard.Content_Type)
              log.info(s"'${HeaderNames.Content_Type}' for $container/$path is '$resultContentType'")
              promise.setValue(GoodPithosResult(GetFileInfo(tmpFile, resultContentType)))
            }
            else {
              promise.setValue(BadPithosResult(HttpResponseStatus.valueOf(result.statusCode)))
            }

          case Failure(t) ⇒
            tmpFileOut.closeAnyway()
            promise.setException(t)
        }

        promise.transform {
          case Return(GoodPithosResult(getFileInfo)) ⇒
            continuation(getFileInfo)

          case bpr @ Return(BadPithosResult(status, extraInfo)) ⇒
            textPlain(request, status, extraInfo, bpr.toString)

          case Throw(t) ⇒
            internalServerError(request, t, PithosErrorRef.PIE005)
        }
    }
  }


  /**
   * Read a data object using CDMI content type.
   *
   * @note Section 8.4 of CDMI 1.0.2: Read a Data Object using CDMI Content Type
   */
  override def GET_object_cdmi(request: Request, objectPath: List[String]): Future[Response] = {
    GET_object_(request, objectPath) {
      case GetFileInfo(file, contentType) ⇒
        val size = file.length()
        val requestPath = request.path
        val requestPathNoObjectIdPrefix = requestPath.removePrefix("/cdmi_objectid")
        val parentPath = requestPathNoObjectIdPrefix.parentPath
        val isTextPlain = contentType == MediaTypes.Text_Plain
        val value = if(isTextPlain) new String(Files.readAllBytes(file.toPath), "UTF-8") else Base64.encodeFile(file)
        val vte = if(isTextPlain) "utf-8" else "base64"

        val model = ObjectModel(
          objectID = requestPathNoObjectIdPrefix,
          objectName = requestPathNoObjectIdPrefix,
          parentURI = parentPath,
          parentID = parentPath,
          domainURI = "",
          mimetype = contentType,
          metadata = Map(StorageSystemMetadata.cdmi_size.name() → size.toString),
          valuetransferencoding = vte,
          valuerange = s"0-${size - 1}",
          value = value
        )
        val jsonModel = Json.objectToJsonString(model)
        file.deleteAnyway()

        okAppCdmiObject(request, jsonModel)
    }
  }

  /**
   * Read a data object using non-CDMI content type.
   *
   * @note Section 8.5 of CDMI 1.0.2: Read a Data Object using a Non-CDMI Content Type
   */
  override def GET_object_noncdmi(request: Request, objectPath: List[String]): Future[Response] = {
    GET_object_(request, objectPath) {
      case GetFileInfo(file, contentType) ⇒
        val status = Status.Ok
        val httpResponse = new DefaultHttpResponse(request.getProtocolVersion(), status)
        val response = Response(httpResponse)

        response.headers().set(HeaderNames.X_CDMI_Specification_Version, currentCdmiVersion)

        val fis = new FileInputStream(file)
        val fileChannel = fis.getChannel
        val length = file.length().toInt
        val bodyChannelBuffer = ChannelBuffers.dynamicBuffer()

        val howmanyWritten = bodyChannelBuffer.writeBytes(fileChannel, length)
        response.contentType = contentType
        response.contentLength = howmanyWritten
        response.content = bodyChannelBuffer

        end(request, response).future
    }
  }


  /**
   * Create a data object in a container using CDMI content type.
   */
  override def PUT_object_cdmi_create_or_update(
    request: Request, objectPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val (container, path) = splitPithosContainerAndPath(objectPath)

    val content = request.contentString
    val jsonTree =
      try Json.jsonStringToTree(content)
      catch {
        case e: com.fasterxml.jackson.core.JsonParseException ⇒
          log.error(e.toString)
          return badRequest(
            request,
            PithosErrorRef.PBR008,
            s"Could not parse input as JSON.\n${e.getMessage}"
          )
      }

    val mimeTypeNode = jsonTree.get("mimetype")
    val valueNode = jsonTree.get("value")
    val vteNode = jsonTree.get("valuetransferencoding")

    if((mimeTypeNode ne null) && !mimeTypeNode.isTextual) {
      return badRequest(
        request,
        PithosErrorRef.PBR003,
        s"Incorrect type ${mimeTypeNode.getNodeType} of 'mimetype' field. Should be ${JsonNodeType.STRING}"
      )
    }

    if((vteNode ne null) && !vteNode.isTextual) {
      return badRequest(
        request,
        PithosErrorRef.PBR004,
        s"Incorrect type ${vteNode.getNodeType} of 'valuetransferencoding' field. Should be ${JsonNodeType.STRING}"
      )
    }

    // Not mandated by the spec but we currently support only the presence of "value"
    if(valueNode eq null) {
      return badRequest(
        request,
        PithosErrorRef.PBR005,
        "'value' is not present"
      )
    }
    if(!valueNode.isTextual) {
      return badRequest(
        request,
        PithosErrorRef.PBR006,
        s"Incorrect type ${valueNode.getNodeType} of 'value' field. Should be ${JsonNodeType.STRING}"
      )
    }

    val mimetype = mimeTypeNode match {
      case null ⇒ MediaTypes.Text_Plain
      case _    ⇒ mimeTypeNode.asText()
    }

    val vte = vteNode match {
      case null ⇒
        "utf-8"

      case node ⇒
        node.asText().toLowerCase(Locale.US) match {
          case text @ ("utf-8" | "base64") ⇒
            text

          case text ⇒
            return badRequest(
              request,
              PithosErrorRef.PBR007,
              s"Incorrect value of 'valuetransferencoding' field [$text]"
            )
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

    completeToPromiseAndTransform(sf_result) {
      case Return(GoodPithosResult(_)) ⇒
        val size = bytes.length
        val requestPath = request.path
        val requestPathNoObjectIdPrefix = requestPath.removePrefix("/cdmi_objectid")
        val parentPath = requestPathNoObjectIdPrefix.parentPath
        val valueRangeEnd = if(size == 0) 0 else size - 1

        val model = ObjectModel(
          objectID = requestPath,
          objectName = requestPath,
          parentURI = parentPath,
          parentID = parentPath,
          domainURI = "",
          mimetype = mimetype,
          metadata = Map(StorageSystemMetadata.cdmi_size.name() → size.toString),
          valuetransferencoding = vte,
          valuerange = s"0-$valueRangeEnd",
          value = "" // TODO technically should not be present
        )

        val jsonModel = Json.objectToJsonString(model)
        okJson(request, jsonModel)

      case Return(bpr @ BadPithosResult(status, extraInfo)) ⇒
        textPlain(request, status, extraInfo, bpr.toString)

      case Throw(t) ⇒
        internalServerError(request, t, PithosErrorRef.PIE006)
    }
  }


  /**
   * Creates a data object in a container using CDMI content type.
   *
   * @note Section 8.2 of CDMI 1.0.2: Create a Data Object Using CDMI Content Type
   */
  override def PUT_object_cdmi_create(request: Request, objectPath: List[String]): Future[Response] =
    PUT_object_cdmi_create_or_update(request, objectPath)

  /**
   * Create a data object in a container using non-CDMI content type.
   * The given `contentType` is guaranteed to be not null.
   */
  override def PUT_object_noncdmi(
    request: Request, objectPath: List[String], contentType: String
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val (container, path) = splitPithosContainerAndPath(objectPath)

    val payload = request.getContent()
    val sf_result = pithos.putObject(serviceInfo, container, path, payload, contentType)

    completeToPromiseAndTransform(sf_result) {
      case Return(GoodPithosResult(_)) ⇒
        okTextPlain(request)

      case Return(bpr @ BadPithosResult(status, extraInfo)) ⇒
        textPlain(request, status, extraInfo, bpr.toString)

      case Throw(t) ⇒
        internalServerError(request, t, PithosErrorRef.PIE007)
    }
  }

  /**
   * Delete a data object (file).
   */
  def DELETE_object_(request: Request, objectPath: List[String]): Future[Response] = {
    val serviceInfo = getPithosServiceInfo(request)
    val (container, path) = splitPithosContainerAndPath(objectPath)

    val sf_result = pithos.deleteFile(serviceInfo, container, path)

    completeToPromiseAndTransform(sf_result) {
      case Return(GoodPithosResult(_)) ⇒
        okTextPlain(request)

      case Return(bpr @ BadPithosResult(status, extraInfo)) ⇒
        textPlain(request, status, extraInfo, bpr.toString)

      case Throw(t) ⇒
        internalServerError(request, t, PithosErrorRef.PIE008)
    }
  }

  /**
   * Deletes a data object in a container using CDMI content type.
   *
   * @note Section 8.8 of CDMI 1.0.2: Delete a Data Object using CDMI Content Type
   */
  override def DELETE_object_cdmi(request: Request, objectPath: List[String]): Future[Response] =
    DELETE_object_(request, objectPath)


  /**
   * Deletes a data object in a container using non-CDMI content type.
   *
   * @note Section 8.9 of CDMI 1.0.2: Delete a Data Object using a Non-CDMI Content Type
   */
  override def DELETE_object_noncdmi(request: Request, objectPath: List[String]): Future[Response] =
    DELETE_object_(request, objectPath)
}
