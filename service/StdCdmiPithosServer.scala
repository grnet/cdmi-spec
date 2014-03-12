/*
 * Copyright 2012-2013 GRNET S.A. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 *   1. Redistributions of source code must retain the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer.
 *
 *   2. Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials
 *      provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY GRNET S.A. ``AS IS'' AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL GRNET S.A OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and
 * documentation are those of the authors and should not be
 * interpreted as representing official policies, either expressed
 * or implied, of GRNET S.A.
 */

package gr.grnet.cdmi.service

import com.twitter.app.GlobalFlag
import com.twitter.finagle.http.Status
import com.twitter.logging.Level
import com.twitter.server.TwitterServer
import com.twitter.util.{Return, Throw, Promise, Future}
import gr.grnet.cdmi.metadata.StorageSystemMetadata
import gr.grnet.cdmi.model.{DataObjectModel, Model, ContainerModel}
import gr.grnet.common.io.{Base64, CloseAnyway, DeleteAnyway}
import gr.grnet.common.json.Json
import gr.grnet.common.text.{ParentUri, RemovePrefix}
import gr.grnet.pithosj.api.PithosApi
import gr.grnet.pithosj.core.ServiceInfo
import gr.grnet.pithosj.core.keymap.{PithosHeaderKeys, PithosResultKeys}
import gr.grnet.pithosj.impl.asynchttp.PithosClientFactory
import java.io.{FileOutputStream, File}
import java.nio.file.Files
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object pithosURL   extends GlobalFlag[String]("https://pithos.okeanos.grnet.gr/object-store/v1", "Pithos service URL")
object pithosUUID  extends GlobalFlag[String]("", "Pithos (Astakos) UUID. Usually set for debugging")
object pithosToken extends GlobalFlag[String]("", "Pithos (Astakos) UUID. Set this only for debugging")

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
object StdCdmiPithosServer extends CdmiRestService with TwitterServer {
  sealed trait Info[+T]
  final case class GoodInfo[T](value: T) extends Info[T]
  final case class BadInfo(status: HttpResponseStatus, extraInfo: String = "") extends Info[Nothing]

  final case class GetFileInfo(file: File, contentType: String)

  override def defaultLogLevel: Level = Level.DEBUG

  val pithos: PithosApi = PithosClientFactory.newPithosClient()

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
      uuid = headers.get("X-Pithos-UUID"),
      token = headers.get("X-Pithos-Token")
    )
  }

  val pithosHeadersFilter = new Filter {
    override def apply(request: Request, service: Service): Future[Response] = {
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

  val myFilters = Vector(pithosHeadersFilter)
  override def mainFilters = super.mainFilters ++ myFilters

  def newResultPromise[T]: Promise[Info[T]] = Promise[Info[T]]()

  /**
   * Lists the contents of a container.
   */
  override def GET_container(
    request: Request, containerPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val promise = newResultPromise[List[String]]
    val container = containerPath.head
    val path = containerPath.tail mkString "/" match { case "" ⇒ "/"; case s ⇒ "/" + s }
    // FIXME If the folder does not exist, the result here is just an empty folder
    val sf_result = pithos.listObjectsInPath(serviceInfo, container, path)

    sf_result.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          val listObjectsInPath = result.resultData.get(PithosResultKeys.ListObjectsInPath, Nil)
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

              oip.contentType match {
                case "application/directory" | "application/folder" ⇒
                  s"${path}/"

                case contentType if contentType.startsWith("application/directory;") ||
                  contentType.startsWith("application/folder;") ⇒
                  s"${path}/"

                case _ ⇒
                  path
              }

            }
          promise.setValue(GoodInfo(children))
        }
        else {
          promise.setValue(BadInfo(HttpResponseStatus.valueOf(result.statusCode)))
        }

      case Failure(t) ⇒
        promise.setException(t)
    }

    promise.transform {
      case Return(GoodInfo(children)) ⇒
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

      case Return(BadInfo(status, extraInfo)) ⇒
        response(request, status, extraInfo).future

      case Throw(t) ⇒
        t.printStackTrace(System.err)
        response(request, Status.InternalServerError, t.toString).future
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
          promise.setValue(GoodInfo(()))
        }
        else {
          promise.setValue(BadInfo(HttpResponseStatus.valueOf(result.statusCode)))
        }

      case Failure(t) ⇒
        promise.setException(t)
    }

    promise.transform {
      case Return(GoodInfo(_)) ⇒
        response(request, Status.Ok).future

      case Return(BadInfo(status, extraInfo)) ⇒
        response(request, status, extraInfo).future

      case Throw(t) ⇒
        t.printStackTrace(System.err)
        response(request, Status.InternalServerError, t.toString).future
    }
  }


  override def GET_objectById(
    request: Request, objectIdPath: List[String]
  ): Future[Response] = {
    // No real object IDs here
    GET_object(request, objectIdPath)
  }


  /**
   * Read the contents or value of a data object (depending on the Accept header).
   */
  override def GET_object(
    request: Request, objectPath: List[String]
  ): Future[Response] = {

    val serviceInfo = getPithosServiceInfo(request)
    val promise = newResultPromise[GetFileInfo]
    val container = objectPath.head
    val path = objectPath.tail.mkString("/")
    println("path = " + path)
    val tmpFile = Files.createTempFile("cdmi-pithos-", null).toFile
    val tmpFileOut = new FileOutputStream(tmpFile)
    val sf_result = pithos.getObject(serviceInfo, container, path, null, tmpFileOut)
    sf_result.onComplete {
      case Success(result) ⇒
        tmpFileOut.closeAnyway()

        if(result.isSuccess) {
          promise.setValue(
            GoodInfo(
              GetFileInfo(tmpFile, result.responseHeaders.getEx(PithosHeaderKeys.Standard.Content_Type))
            )
          )
        }
        else {
          promise.setValue(BadInfo(HttpResponseStatus.valueOf(result.statusCode)))
        }

      case Failure(t) ⇒
        tmpFileOut.closeAnyway()
        promise.setException(t)
    }

    promise.transform {
      case Return(GoodInfo(GetFileInfo(file, contentType))) ⇒
        val size = file.length()
        val uri = request.uri.removePrefix("/cdmi_objectid")
        val parentURI = uri.parentUri
        val isTextPlain = contentType == "text/plain"
        val value = if(isTextPlain) new String(Files.readAllBytes(file.toPath), "UTF-8") else Base64.encodeFile(file)
        val valuetransferencoding = if(isTextPlain) "utf-8" else "base64"

        val container = DataObjectModel(
          objectID = uri,
          objectName = uri,
          parentURI = parentURI,
          parentID = parentURI,
          domainURI = "",
          mimetype = contentType,
          metadata = Map(StorageSystemMetadata.cdmi_size.name() → size.toString),
          valuetransferencoding = valuetransferencoding,
          valuerange = s"0-${size - 1}",
          value = value
        )
        val jsonContainer = Json.objectToJsonString(container)
        file.deleteAnyway()
        response(request, Status.Ok, jsonContainer).future

      case Return(BadInfo(status, extraInfo)) ⇒
        response(request, status, extraInfo).future

      case Throw(t) ⇒
        t.printStackTrace(System.err)
        response(request, Status.InternalServerError, t.toString).future
    }
  }


  /**
   * Create a data object in a container.
   */
  override def PUT_object(
    request: Request, objectPath: List[String]
  ): Future[Response] = {
    val serviceInfo = getPithosServiceInfo(request)
    val promise = newResultPromise[GetFileInfo]
    val container = objectPath.head
    val path = objectPath.tail.mkString("/")
    val uri = request.uri.removePrefix("/cdmi_objectid")

    response(request, Status.Ok, s"${request.method.getName} URI=${uri}, PATH=${path}, CONTAINER=${container}").future
  }
}