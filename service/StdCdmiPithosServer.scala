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
import gr.grnet.pithosj.api.PithosApi
import gr.grnet.pithosj.core.ServiceInfo
import gr.grnet.pithosj.core.keymap.{PithosHeaderKeys, PithosResultKeys}
import gr.grnet.pithosj.impl.asynchttp.PithosClientFactory
import org.jboss.netty.handler.codec.http.{HttpResponse, HttpRequest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import gr.grnet.cdmi.model.{DataObjectModel, Model, ContainerModel}
import gr.grnet.common.json.Json
import java.util.Locale
import gr.grnet.common.text.NoTrailingSlash
import gr.grnet.common.text.{NormalizeUri, UriToList}
import java.io.{FileOutputStream, File}
import java.nio.file.Files
import gr.grnet.common.io.{Base64, CloseAnyway, DeleteAnyway}
import gr.grnet.cdmi.metadata.StorageSystemMetadata

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
object StdCdmiPithosServer extends CdmiRestService with TwitterServer {
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

  def pithosInfo(request: Request) = (pithosURL(request), pithosUUID(request), pithosToken(request))

  /**
   * Lists the contents of a container.
   *
   * @param request
   * @return
   */
  override def GET_container(
    request: Request, containerPath: List[String]
  ): Future[Response] = {

    val (pithosURL, pithosUUID, pithosToken) = pithosInfo(request)
    println("pithosURL = " + pithosURL)
    println("pithosUUID = " + pithosUUID)
    println("pithosToken = " + pithosToken)
    // TODO check nulls
    val serviceInfo = ServiceInfo(pithosURL, pithosUUID, pithosToken)

    val promise = Promise[List[String]]()
    val container = containerPath.head
    val path = containerPath.tail mkString "/" match { case "" ⇒ "/"; case s ⇒ "/" + s }
    val sf_result = pithos.listObjectsInPath(serviceInfo, container, path)

    sf_result.onComplete {
      case Success(result) ⇒
        if(result.isSuccess) {
          val listObjectsInPath = result.resultData.get(PithosResultKeys.ListObjectsInPath, Nil)
          val children =
            for {
              oip ← listObjectsInPath
            } yield {
              oip.contentType match {
                case "application/directory" | "application/folder" ⇒
                  s"${oip.container}/${oip.path}/"

                case contentType if contentType.startsWith("application/directory;") ||
                  contentType.startsWith("application/folder;") ⇒
                  s"${oip.container}/${oip.path}/"

                case _ ⇒
                  s"${oip.container}/${oip.path}"
              }

            }
          promise.setValue(children)
        }
        else {
          promise.setException(new Exception(s"Original response: ${result.statusCode} ${result.statusText}"))
        }

      case Failure(t) ⇒
        promise.setException(t)
    }

    promise.transform {
      case Return(children) ⇒
        val uri = request.uri
        val parentURI = uri.substring(0, uri.noTrailingSlash.lastIndexOf('/') + 1)

        val container = ContainerModel(
          objectID = uri,
          objectName = uri,
          parentURI = parentURI,
          parentID = parentURI,
          domainURI = "",
          capabilitiesURI = "",
          childrenrange = Model.childrenRangeOf(children),
          children = children
        )
        val jsonContainer = Json.objectToJsonString(container)
        bodyToFutureResponse(request, Status.Ok, jsonContainer)

      case Throw(t) ⇒
        t.printStackTrace(System.err)
        bodyToFutureResponse(request, Status.InternalServerError, t.toString)
    }
  }

  /**
   * Creates a new container.
   */
  override def PUT_container(
    request: Request, containerPath: List[String]
  ): Future[Response] = {
    bodyToFutureResponse(request, Status.Ok, request.toString())
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
    val (pithosURL, pithosUUID, pithosToken) = pithosInfo(request)
    println("pithosURL = " + pithosURL)
    println("pithosUUID = " + pithosUUID)
    println("pithosToken = " + pithosToken)
    // TODO check nulls
    val serviceInfo = ServiceInfo(pithosURL, pithosUUID, pithosToken)

    case class GetFileInfo(file: File, contentType: String)
    val promise = Promise[GetFileInfo]()
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
          promise.setValue(GetFileInfo(tmpFile, result.responseHeaders.getEx(PithosHeaderKeys.Standard.Content_Type)))
        }
        else {
          promise.setException(new Exception(s"Original response: ${result.statusCode} ${result.statusText}"))
        }

      case Failure(t) ⇒
        tmpFileOut.closeAnyway()
        promise.setException(t)
    }

    promise.transform {
      case Return(GetFileInfo(file, contentType)) ⇒
        val size = file.length()
        val uri = request.uri
        val parentURI = uri.substring(0, uri.noTrailingSlash.lastIndexOf('/') + 1)
        val base64Value = Base64.encodeFile(file)

        val container = DataObjectModel(
          objectID = uri,
          objectName = uri,
          parentURI = parentURI,
          parentID = parentURI,
          domainURI = "",
          mimetype = contentType,
          metadata = Map(StorageSystemMetadata.cdmi_size.name() → size.toString),
          valuetransferencoding = "base64",
          valuerange = s"0-${size - 1}",
          value = base64Value
        )
        val jsonContainer = Json.objectToJsonString(container)
        file.deleteAnyway()
        bodyToFutureResponse(request, Status.Ok, jsonContainer)

      case Throw(t) ⇒
        t.printStackTrace(System.err)
        bodyToFutureResponse(request, Status.InternalServerError, t.toString)
    }
  }

  def main() {
    val server = Http.serve(cdmiHttpPortFlag(), nettyToFinagle andThen mainService)

    Await.ready(server)
  }
}