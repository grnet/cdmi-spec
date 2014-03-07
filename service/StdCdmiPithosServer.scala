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

import com.twitter.finagle.http.{Status, Response, Request}
import com.twitter.finagle.{Filter, Http}
import com.twitter.server.TwitterServer
import com.twitter.util.{Return, Throw, Promise, Future, Await}
import gr.grnet.cdmi.metadata.StorageSystemMetadata
import gr.grnet.cdmi.model.{DataObjectModel, Model, ContainerModel}
import gr.grnet.common.io.{Base64, CloseAnyway, DeleteAnyway}
import gr.grnet.common.json.Json
import gr.grnet.common.text.{NormalizeUri, UriToList, ParentUri}
import gr.grnet.pithosj.api.PithosApi
import gr.grnet.pithosj.core.ServiceInfo
import gr.grnet.pithosj.core.keymap.{PithosHeaderKeys, PithosResultKeys}
import gr.grnet.pithosj.impl.asynchttp.PithosClientFactory
import java.io.{FileOutputStream, File}
import java.nio.file.Files
import java.util.Locale
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpResponse, HttpRequest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
object StdCdmiPithosServer extends CdmiRestService with TwitterServer {
  sealed trait Info[+T]
  final case class GoodInfo[T](value: T) extends Info[T]
  final case class BadInfo(status: HttpResponseStatus, extraInfo: String = "") extends Info[Nothing]

  val nettyToFinagle =
    Filter.mk[HttpRequest, HttpResponse, Request, Response] { (req, service) =>
      service(Request(req)) map { _.httpResponse }
    }

  val pithos: PithosApi = PithosClientFactory.newPithosClient()

  def pithosURL  (request: Request): String =
    request.headers().get("X-CDMI-Pithos-Service-URL") match {
      case null ⇒ System.getenv("X_CDMI_Pithos_Service_URL".toUpperCase(Locale.US))
      case string ⇒ string
    }

  def pithosUUID (request: Request): String =
    request.headers().get("X-CDMI-Pithos-UUID") match {
      case null ⇒ System.getenv("X_CDMI_Pithos_UUID".toUpperCase(Locale.US))
      case string ⇒ string
    }

  def pithosToken(request: Request): String =
    request.headers().get("X-CDMI-Pithos-Token") match {
      case null ⇒
        System.getenv("X_CDMI_Pithos_Token".toUpperCase(Locale.US)) match {
          case null ⇒
            request.headers().get("X-Auth-Token") match {
              case null ⇒ System.getenv("X_Auth_Token".toUpperCase(Locale.US))
              case string ⇒ string
            }

          case string ⇒ string
        }

      case string ⇒ string
    }

  def getPithosServiceInfo(request: Request): Info[ServiceInfo] = {
    val _pithosURL = pithosURL(request)
    val _pithosUUID = pithosUUID(request)
    val _pithosToken = pithosToken(request)

    println("pithosURL = " + _pithosURL)
    println("pithosUUID = " + _pithosUUID)
    println("pithosToken = " + _pithosToken)

    (_pithosURL, _pithosUUID, _pithosToken) match {
      case (null, _, _) ⇒
        BadInfo(
          Status.BadRequest,
          "Unknown Pithos+ service URL. Please set header X-CDMI-Pithos-Service-URL"
        )

      case (_, null, _) ⇒
        BadInfo(
          Status.BadRequest,
          "Unknown Pithos+ UUID. Please set header X-CDMI-Pithos-UUID"
        )

      case (_, _, null) ⇒
        BadInfo(
          Status.BadRequest,
          "Unknown Pithos+ user token. Please set header X-CDMI-Pithos-Token or X-Auth-Token"
        )

      case good ⇒
        GoodInfo(ServiceInfo(_pithosURL, _pithosUUID, _pithosToken))
    }
  }

  def newResultPromise[T]: Promise[Info[T]] = Promise[Info[T]]()

  /**
   * Lists the contents of a container.
   */
  override def GET_container(
    request: Request, containerPath: List[String]
  ): Future[Response] = {


    getPithosServiceInfo(request) match {
      case BadInfo(status, extraInfo) ⇒
        bodyToFutureResponse(request, status, extraInfo)

      case GoodInfo(serviceInfo) ⇒
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
            bodyToFutureResponse(request, Status.Ok, jsonContainer)

          case Return(BadInfo(status, extraInfo)) ⇒
            bodyToFutureResponse(request, status, extraInfo)

          case Throw(t) ⇒
            t.printStackTrace(System.err)
            bodyToFutureResponse(request, Status.InternalServerError, t.toString)
        }
    }
  }

  /**
   * Creates a new container.
   */
  override def PUT_container(
    request: Request, containerPath: List[String]
  ): Future[Response] = {
    getPithosServiceInfo(request) match {
      case BadInfo(status, extraInfo) ⇒
        bodyToFutureResponse(request, status, extraInfo)

      case GoodInfo(serviceInfo) ⇒
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
            bodyToFutureResponse(request, Status.Ok)

          case Return(BadInfo(status, extraInfo)) ⇒
            bodyToFutureResponse(request, status, extraInfo)

          case Throw(t) ⇒
            t.printStackTrace(System.err)
            bodyToFutureResponse(request, Status.InternalServerError, t.toString)
        }
    }
  }


  override def GET_objectById(
    request: Request, objectId: String
  ): Future[Response] = {
    // No real object IDs here
    GET_object(request, "cdmi_objectid" :: objectId.normalizeUri.uriToList)
  }


  /**
   * Read the contents or value of a data object (depending on the Accept header).
   */
  override def GET_object(
    request: Request, objectPath: List[String]
  ): Future[Response] = {

    getPithosServiceInfo(request) match {
      case BadInfo(status, extraInfo) ⇒
        bodyToFutureResponse(request, status, extraInfo)

      case GoodInfo(serviceInfo) ⇒
        case class GetFileInfo(file: File, contentType: String)
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
            val uri = request.uri
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
            bodyToFutureResponse(request, Status.Ok, jsonContainer)

          case Return(BadInfo(status, extraInfo)) ⇒
            bodyToFutureResponse(request, status, extraInfo)

          case Throw(t) ⇒
            t.printStackTrace(System.err)
            bodyToFutureResponse(request, Status.InternalServerError, t.toString)
        }
    }
  }

  def main() {
    val server = Http.serve(cdmiHttpPortFlag(), nettyToFinagle andThen mainService)

    Await.ready(server)
  }
}