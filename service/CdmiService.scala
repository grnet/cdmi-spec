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

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Response, Request}
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future
import gr.grnet.cdmi.model.CdmiCapabilityModel
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, DefaultHttpResponse, HttpRequest, HttpResponse}
import com.twitter.finagle.http.service.{NotFoundService, RoutingService}
import com.twitter.finagle.http.path.{/, Root, Path}
import java.net.InetSocketAddress
import com.twitter.app.Flag
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.util.CharsetUtil.UTF_8

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
trait CdmiService {
  val defaultCdmiHttpPort = 8080
  val cdmiHttpPortFlag: Flag[InetSocketAddress]

  val capabilitiesResponse: HttpResponse = JsonConverter(CdmiCapabilityModel.Cached)

  /**
   * The service that responds to `/cdmi_capabilities`
   */
  val capabilitiesService: Service[Request, Response] =
    new Service[Request, Response] {
      override def apply(request: Request): Future[Response] = {
        Future.value(Response(capabilitiesResponse))
      }
    }

  /**
   * The service that responds to `/`
   */
  val rootService: Service[Request, Response]

  def makeNotFound(reason: String): Service[Request, Response] =
    new Service[Request, Response] {
      override def apply(request: Request): Future[Response] = {
        val response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.NOT_FOUND)
        val body =
          if((reason eq null) || reason.isEmpty)
            request.uri + " " + HttpResponseStatus.NOT_FOUND.getReasonPhrase
          else
            request.uri + " " + HttpResponseStatus.NOT_FOUND.getReasonPhrase + " [" + reason + "]"
        response.setContent(copiedBuffer(body, UTF_8))
        Future.value(Response(response))
      }
    }

  val notFoundService: Service[Request, Response] = makeNotFound("")

  val getObjectByIDService: Service[Request, Response] = makeNotFound("getObjectByID")

  val getObjectByNameService: Service[Request, Response] = makeNotFound("getObjectByName")

  /**
   * The main router
   */
  def router: RoutingService[Request with Request] = RoutingService.byPathObject {
    case Root ⇒
      rootService

    case Root / "cdmi_capabilities" ⇒
      capabilitiesService

    case Root / "cdmi_objectID" / objectId ⇒
      getObjectByIDService

    case Root / container / objectName ⇒
      getObjectByNameService

    case _ ⇒
      notFoundService
  }
}
