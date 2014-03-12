/*
 * Copyright 2012-2014 GRNET S.A. All rights reserved.
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

import com.twitter.app.{GlobalFlag, Flags}
import com.twitter.finagle.http.{Status, Method}
import com.twitter.finagle.{Filter, Http}
import com.twitter.logging.Logger
import com.twitter.util.{Await, Future}
import gr.grnet.cdmi.http.CdmiHeader
import gr.grnet.cdmi.model.CapabilityModel
import gr.grnet.common.json.Json
import gr.grnet.common.text.{UriToList, NormalizeUri}
import java.net.{URLDecoder, InetSocketAddress}
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http.{HttpResponse, HttpRequest, HttpMethod, HttpResponseStatus, DefaultHttpResponse}
import org.jboss.netty.util.CharsetUtil.UTF_8

object port          extends GlobalFlag[InetSocketAddress](new InetSocketAddress(8080), "http port")
object dev           extends GlobalFlag[Boolean](false, "enable development mode")
object pithosTimeout extends GlobalFlag[Long](1000L * 60L * 3L /* 3 min*/, "millis to wait for Pithos response")

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
trait CdmiRestService {
  type Request = com.twitter.finagle.http.Request
  val Request = com.twitter.finagle.http.Request
  type Response = com.twitter.finagle.http.Response
  val Response = com.twitter.finagle.http.Response
  type Service = com.twitter.finagle.Service[Request, Response]
  type Filter = com.twitter.finagle.Filter[Request, Response, Request, Response]

  /**
   * This will normally be provided by [[com.twitter.app.App]]
   */
  val flag: Flags

  /**
   * This will normally be provided by [[com.twitter.logging.Logging]]
   */
  val log: Logger

  val supportedCdmiVersions = Set("1.0.2")

  def response(
    request: Request,
    status: HttpResponseStatus = Status.Ok,
    body: CharSequence = ""
  ): Response = {
    val response = new DefaultHttpResponse(request.getProtocolVersion(), status)
    response.setContent(copiedBuffer(body, UTF_8))
    Response(response)
  }

  def badResponse(
    request: Request,
    status: HttpResponseStatus,
    body: CharSequence = ""
  ): Response = {
    val response = new DefaultHttpResponse(request.getProtocolVersion(), status)

    val buffer = new java.lang.StringBuilder
    if(dev()) {
      buffer.append(status.toString)
      buffer.append('\n')
      buffer.append(body)
      buffer.append("\n\n")

      val method = request.method
      val uri = request.uri
      val decodedUri = try URLDecoder.decode(uri, "UTF-8") catch { case e: Exception ⇒ s"(${e.getMessage}) ${uri}"}

      buffer.append(method.getName)
      buffer.append(' ')
      buffer.append(uri)
      buffer.append('\n')
      buffer.append(" " * (method.getName.length + 1))
      buffer.append(decodedUri)
      buffer.append('\n')
    }
    else {
      buffer.append(body)
    }

    response.setContent(copiedBuffer(buffer, UTF_8))
    Response(response)
  }

  object Headers {
    final val X_CDMI_Specification_Version = CdmiHeader.X_CDMI_Specification_Version.headerName()
  }

  object Filters {
    final val CheckCdmiVersionHeader: Filter = new Filter {
      override def apply(request: Request, service: Service): Future[Response] = {
        request.headers().get(Headers.X_CDMI_Specification_Version) match {
          case null ⇒
            badResponse(
              request,
              Status.BadRequest,
              s"Please set ${Headers.X_CDMI_Specification_Version}"
            ).future

          case version if supportedCdmiVersions(version) ⇒
            service(request)

          case version ⇒
            badResponse(
              request,
              Status.BadRequest,
              s"Unknown protocol version ${version}. Supported versions are: ${supportedCdmiVersions.mkString(",")}"
            ).future
        }
      }
    }
  }

  val rootCapabilities: CapabilityModel = CapabilityModel.rootOf()

  /**
   * Return the capabilities of this CDMI implementation.
   */
  def GET_capabilities(request: Request): Future[Response] = {
    val caps = rootCapabilities
    val jsonCaps = Json.objectToJsonString(caps)

    response(request, Status.Ok, jsonCaps).future
  }

  def GET_objectById(request: Request, objectIdPath: List[String]): Future[Response] =
    badResponse(request, Status.NotImplemented).future

  def POST_objectById(request: Request, objectIdPath: List[String]): Future[Response] =
    badResponse(request, Status.NotImplemented).future

  def PUT_objectById(request: Request, objectIdPath: List[String]): Future[Response] =
    badResponse(request, Status.NotImplemented).future

  /**
   * Read the contents or value of a data object (depending on the Accept header).
   */
  def GET_object(request: Request, objectPath: List[String]): Future[Response] =
    badResponse(request, Status.NotImplemented).future

  /**
   * Create a data object in a container.
   */
  def PUT_object(request: Request, objectPath: List[String]): Future[Response] =
    badResponse(request, Status.NotImplemented).future

  /**
   * Lists the contents of a container.
   */
  def GET_container(request: Request, containerPath: List[String]): Future[Response] =
    badResponse(request, Status.NotImplemented).future

  /**
   * Creates a new container.
   */
  def PUT_container(request: Request, containerPath: List[String]): Future[Response] =
    badResponse(request, Status.NotImplemented).future

  def routingTable: PartialFunction[Request, Future[Response]] = {
    case request ⇒
      val method = request.method
      val normalizedUri = request.uri.normalizeUri

      val getObjectByIdPF: (HttpMethod, List[String]) ⇒ Future[Response] =
        (method, objectIdPath) ⇒ method match {
          case Method.Get  ⇒ GET_objectById(request, objectIdPath)
          case Method.Post ⇒ POST_objectById(request, objectIdPath)
          case Method.Put  ⇒ PUT_objectById(request, objectIdPath)
          case _           ⇒ badResponse(request, Status.MethodNotAllowed).future
        }

      log.debug(method.getName + " " + normalizedUri)
      val uriList = normalizedUri.uriToList
      val lastSlash = normalizedUri(normalizedUri.length - 1) == '/'
      log.debug(method.getName + " " + uriList.map(s ⇒ "\"" + s + "\"").mkString(" ") + (if(lastSlash) " [/]" else ""))
      val SLASH = true
      val NOSLASH = false

      (uriList, lastSlash) match {
        case (Nil, _) ⇒
          // "/"
          badResponse(request, Status.MethodNotAllowed).future


        case ("" :: Nil, _) ⇒
          // ""
          badResponse(request, Status.MethodNotAllowed).future

        case ("" ::  "cdmi_capabilities" :: Nil, SLASH) ⇒
          // "/cdmi_capabilities/"
          method match {
            case Method.Get ⇒ GET_capabilities(request)
            case _ ⇒ badResponse(request, Status.MethodNotAllowed).future
          }

        case ("" :: "cdmi_objectid" :: objectIdPath, _) ⇒
          getObjectByIdPF(method, objectIdPath)
        case ("" :: "cdmi_objectId" :: objectIdPath, _) ⇒
          getObjectByIdPF(method, objectIdPath)
        case ("" :: "cdmi_objectID" :: objectIdPath, _) ⇒
          getObjectByIdPF(method, objectIdPath)

        case ("" :: containerPath, SLASH) ⇒
          method match {
            case Method.Get ⇒ GET_container(request, containerPath)
            case Method.Put ⇒ PUT_container(request, containerPath)
            case _ ⇒ badResponse(request, Status.MethodNotAllowed).future
          }

        case ("" :: objectPath, NOSLASH) ⇒
          method match {
            case Method.Get ⇒ GET_object(request, objectPath)
            case Method.Put ⇒ PUT_object(request, objectPath)
            case _ ⇒ badResponse(request, Status.MethodNotAllowed).future
          }

        case _ ⇒
          badResponse(request, Status.MethodNotAllowed).future
      }
  }

  def banner = gr.grnet.cdmi.Banner
  def printBanner(): Unit = log.info(banner)

  def mainService: Service =
    new Service {
      override def apply(request: Request): Future[Response] = routingTable(request)
    }

  def mainFilters: Vector[Filter] = Vector(Filters.CheckCdmiVersionHeader)

  val nettyToFinagle =
    Filter.mk[HttpRequest, HttpResponse, Request, Response] { (req, service) =>
      service(Request(req)) map { _.httpResponse }
    }

  def main(): Unit = {
    printBanner()

    val service = (mainFilters :\ mainService) { (filter, service) ⇒ filter andThen service }
    val server = Http.serve(
      port(),
      nettyToFinagle andThen service
    )

    Await.ready(server)
  }
}
