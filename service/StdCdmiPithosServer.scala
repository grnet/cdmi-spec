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
import gr.grnet.pithosj.core.keymap.PithosResultKeys
import gr.grnet.pithosj.impl.asynchttp.PithosClientFactory
import org.jboss.netty.handler.codec.http.{HttpResponse, HttpRequest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import gr.grnet.cdmi.model.{Model, ContainerModel}
import gr.grnet.common.json.Json
import java.util.Locale
import gr.grnet.cdmi.http.CdmiContentType

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
   * @param container
   * @return
   */
  override def GET_container(
    request: Request, container: String
  ): Future[Response] = {

    val (pithosURL, pithosUUID, pithosToken) = pithosInfo(request)
    println("pithosURL = " + pithosURL)
    println("pithosUUID = " + pithosUUID)
    println("pithosToken = " + pithosToken)
    // TODO check nulls
    val serviceInfo = ServiceInfo(pithosURL, pithosUUID, pithosToken)

    val promise = Promise[List[String]]()
    val sf_result = pithos.listObjectsInContainer(serviceInfo, container)

    sf_result.onComplete {
      case Success(result) ⇒
        val listObjectsInPath = result.resultData.get(PithosResultKeys.ListObjectsInPath, Nil)
        val children =
          for {
            oip ← listObjectsInPath
          } yield {
            s"${oip.container}/${oip.path}"
          }
        promise.setValue(children)

      case Failure(t) ⇒
        promise.setException(t)
    }

    promise.transform {
      case Return(children) ⇒
        val container = ContainerModel(
          objectType = CdmiContentType.Application_CdmiContainer.contentType(),
          objectID = request.uri,
          objectName = request.uri,
          parentURI = request.uri.substring(0, request.uri.lastIndexOf("/")),
          parentID = request.uri.substring(0, request.uri.lastIndexOf("/")),
          domainURI = "",
          capabilitiesURI = "",
          completionStatus = "Complete",
          metadata = Map(),
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
   *
   * @param request
   * @param container
   * @return
   */
  override def PUT_container(
    request: Request, container: String
  ): Future[Response] = {
    bodyToFutureResponse(request, Status.Ok, request.toString())
  }

  def main() {
    val server = Http.serve(cdmiHttpPortFlag(), nettyToFinagle andThen mainService)

    Await.ready(server)
  }
}