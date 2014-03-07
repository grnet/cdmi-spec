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

import com.twitter.app.Flags
import com.twitter.finagle.Service
import com.twitter.finagle.http.path.{Path, /, /:, Root}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.{Status, Method, Response, Request}
import com.twitter.util.Future
import gr.grnet.cdmi.model.CapabilityModel
import gr.grnet.common.json.Json
import java.net.{URLDecoder, InetSocketAddress}
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http.{HttpMethod, HttpResponseStatus, DefaultHttpResponse}
import org.jboss.netty.util.CharsetUtil.UTF_8
import gr.grnet.common.text.{UriToList, NormalizeUri}

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
trait CdmiRestService {
  /**
   * This will normally be provided by [[com.twitter.app.App]]
   */
  val flag: Flags

  val supportedCdmiVersions = Set("1.0.2")

  lazy val cdmiHttpPortFlag = flag("cdmi.http.port", new InetSocketAddress(8080), "http server port")

  lazy val cdmiPithosTimeoutMillisFlag = flag("cdmi.pithos.timeout.millis", 1000L * 60L * 3L /* 3 min*/, "Millis to wait for Pithos response")

  def bodyToResponse(
    request: Request,
    status: HttpResponseStatus,
    body: String = ""
  ): Response = {
    val response = new DefaultHttpResponse(request.getProtocolVersion(), status)
    response.setContent(copiedBuffer(body, UTF_8))
    Response(response)
  }

  def bodyToFutureResponse(
    request: Request,
    status: HttpResponseStatus,
    body: String = ""
  ): Future[Response] = Future.value(bodyToResponse(request, status, body))

  def makeSpecial(status: HttpResponseStatus, reason: String): Service[Request, Response] =
    new Service[Request, Response] {
      override def apply(request: Request): Future[Response] = {
        val method = request.method
        val status = HttpResponseStatus.NOT_FOUND
        val uri = request.uri
        val decodedUri = try URLDecoder.decode(uri, "UTF-8") catch { case e: Exception ⇒ s"(${e.getMessage}) ${uri}"}
        val body =
          if((reason eq null) || reason.isEmpty) {
            status.getCode.toString + " " + status.getReasonPhrase + "\n" +
            method.getName          + " " + uri + "\n" +
            method.getName          + " " + decodedUri
          }
          else {
            status.getCode.toString + " " + status.getReasonPhrase + "\n" +
              method.getName          + " " + uri + "\n" +
              method.getName          + " " + decodedUri +
            "[" + reason + "]"
          }

        bodyToFutureResponse(request, status, body)
      }
    }

  val badRequestService: Service[Request, Response] = makeSpecial(Status.BadRequest, "")

  val unauthorizedService: Service[Request, Response] = makeSpecial(Status.NotFound, "")

  val forbiddenService: Service[Request, Response] = makeSpecial(Status.Forbidden, "")

  val notFoundService: Service[Request, Response] = makeSpecial(Status.NotFound, "")

  val notAllowedService: Service[Request, Response] = makeSpecial(Status.MethodNotAllowed, "")

  val notImplementedService: Service[Request, Response] = makeSpecial(Status.NotImplemented, "")

  val rootCapabilities: CapabilityModel = CapabilityModel.rootOf()

  /**
   * Return the capabilities of this CDMI implementation.
   */
  def GET_capabilities(request: Request): Future[Response] = {
    val caps = rootCapabilities
    val jsonCaps = Json.objectToJsonString(caps)

    bodyToFutureResponse(request, Status.Ok, jsonCaps)
  }

  def GET_objectById(request: Request, objectId: String): Future[Response] =
    notImplementedService(request)

  def POST_objectById(request: Request, objectId: String): Future[Response] =
    notImplementedService(request)

  def PUT_objectById(request: Request, objectId: String): Future[Response] =
    notImplementedService(request)

  /**
   * Read the contents or value of a data object (depending on the Accept header).
   */
  def GET_object(request: Request, objectPath: List[String]): Future[Response] =
    notImplementedService(request)

  /**
   * Create a data object in a container.
   */
  def PUT_object(request: Request, objectPath: List[String]): Future[Response] =
    notImplementedService(request)

  /**
   * Lists the contents of a container.
   */
  def GET_container(request: Request, containerPath: List[String]): Future[Response] =
    notImplementedService(request)

  /**
   * Creates a new container.
   */
  def PUT_container(request: Request, containerPath: List[String]): Future[Response] =
    notImplementedService(request)

  def routingTable: PartialFunction[Request, Future[Response]] = {
    case request ⇒
      val method = request.method
      val normalizedUri = request.uri.normalizeUri

      val getObjectByIdPF: (HttpMethod, String) ⇒ Future[Response] =
        (method, objectId) ⇒ method match {
          case Method.Get  ⇒ GET_objectById(request, objectId)
          case Method.Post ⇒ POST_objectById(request, objectId)
          case Method.Put  ⇒ PUT_objectById(request, objectId)
          case _           ⇒ notAllowedService(request)
        }

      println(method.getName + " " + normalizedUri)
      val uriList = normalizedUri.uriToList
      val lastSlash = normalizedUri(normalizedUri.length - 1) == '/'
      println(method.getName + " " + uriList.map(s ⇒ "\"" + s + "\"").mkString(" ") + (if(lastSlash) " [/]" else ""))
      val SLASH = true
      val NOSLASH = false

      (uriList, lastSlash) match {
        case (Nil, _) ⇒
          "/"
          notAllowedService(request)

        case ("" :: Nil, _) ⇒
          ""
          notAllowedService(request)

        case ("" ::  "cdmi_capabilities" :: Nil, SLASH) ⇒
          "/cdmi_capabilities/"

          method match {
            case Method.Get ⇒ GET_capabilities(request)
            case _ ⇒ notAllowedService(request)
          }

        case ("" :: "cdmi_objectid" :: objectId :: Nil, _) ⇒
          getObjectByIdPF(method, objectId)
        case ("" :: "cdmi_objectId" :: objectId :: Nil, _) ⇒
          getObjectByIdPF(method, objectId)
        case ("" :: "cdmi_objectID" :: objectId :: Nil, _) ⇒
          getObjectByIdPF(method, objectId)

        case ("" :: containerPath, SLASH) ⇒
          method match {
            case Method.Get ⇒ GET_container(request, containerPath)
            case Method.Put ⇒ PUT_container(request, containerPath)
            case _ ⇒ notAllowedService(request)
          }

        case ("" :: objectPath, NOSLASH) ⇒
          method match {
            case Method.Get ⇒ GET_object(request, objectPath)
            case Method.Put ⇒ PUT_object(request, objectPath)
            case _ ⇒ notAllowedService(request)
          }

        case _ ⇒
          notAllowedService(request)
      }
  }

  def mainService: Service[Request, Response] = {
    val routingTableService = new Service[Request, Response] {
      override def apply(request: Request): Future[Response] = routingTable(request)
    }

    new RoutingService[Request]({case request ⇒ routingTableService})
  }
}
