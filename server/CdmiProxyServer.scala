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

package gr.grnet.cdmi.server

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Filter, Http, Service}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import gr.grnet.cdmi.service.CdmiService
import java.net.InetSocketAddress
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, DefaultHttpResponse, HttpResponse, HttpRequest}
import org.jboss.netty.util.CharsetUtil.UTF_8


/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
object CdmiProxyServer extends CdmiService with TwitterServer {
  val cdmiHttpPortFlag = flag("http.port", new InetSocketAddress(defaultCdmiHttpPort), "CDMI http server port")

  val rootService = new Service[Request, Response] {
    override def apply(request: Request): Future[Response] = {
      val response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.OK)
      response.setContent(copiedBuffer("hello", UTF_8))
      Future.value(Response(response))
    }
  }

  final val nettyToFinagle =
    Filter.mk[HttpRequest, HttpResponse, Request, Response] { (req, service) =>
      service(Request(req)) map { _.httpResponse }
    }

  def main() {
    val server = Http.serve(cdmiHttpPortFlag(), nettyToFinagle andThen router)

    Await.ready(server)
  }
}