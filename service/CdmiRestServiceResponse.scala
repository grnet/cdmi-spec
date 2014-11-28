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

import com.twitter.finagle.http
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import gr.grnet.cdmi.http.CdmiMediaType
import gr.grnet.common.http.{IMediaType, StdMediaType}
import org.jboss.netty.buffer.ChannelBuffers._
import org.jboss.netty.handler.codec.http.{DefaultHttpResponse, HttpResponseStatus}
import org.jboss.netty.util.CharsetUtil._

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
trait CdmiRestServiceResponse { self: CdmiRestService with CdmiRestServiceTypes ⇒
  def response(
    request: Request,
    status: HttpResponseStatus,
    contentType: IMediaType,
    body: CharSequence = "",
    devbody: CharSequence = ""
  ): Response = {

    val statusCode = status.getCode
    if(dev() && devbody.length() > 0) {
      log.info(devbody.toString)
    }
    if(statusCode < 200 || statusCode >= 300) {
      log.error(s"$status $body")
    }
    else {
      log.info(s"$status, '${HeaderNames.Content_Type}: ${contentType.value()}', '${HeaderNames.Content_Length}: ${body.length()}'")
    }

    val httpResponse = new DefaultHttpResponse(request.getProtocolVersion(), status)
    val response = http.Response(httpResponse)

    response.headers().set(HeaderNames.X_CDMI_Specification_Version, currentCdmiVersion)

    val bodyChannelBuffer = copiedBuffer(body, UTF_8)
    response.contentType = contentType.value()
    response.contentLength = bodyChannelBuffer.readableBytes()
    response.content = bodyChannelBuffer

    end(request, response)
  }

  def textPlain(
    request: Request,
    status: HttpResponseStatus,
    body: CharSequence = "",
    devbody: CharSequence = ""
  ): Future[Response] =
    response(request, status, StdMediaType.Text_Plain, body, devbody).future

  def appJson(
    request: Request,
    status: HttpResponseStatus,
    body: CharSequence = "",
    devbody: CharSequence = ""
  ): Future[Response] =
    response(request, status, StdMediaType.Application_Json, body, devbody).future


  def internalServerError(request: Request, t: Throwable, ref: IErrorRef): Future[Response] = {
    val errMsg = s"[$ref]"
    val devErrMsg = s"$errMsg $t"
    log.error(t, devErrMsg)

    textPlain(request, Status.InternalServerError, errMsg, devErrMsg)
  }

  def notAllowed(
    request: Request,
    body: CharSequence = "",
    contentType: IMediaType = StdMediaType.Text_Plain
  ): Future[Response] =
    response(request, Status.MethodNotAllowed, body = body, contentType = StdMediaType.Text_Plain).future

  def notImplemented(
    request: Request,
    body: CharSequence = "",
    contentType: IMediaType = StdMediaType.Text_Plain
  ): Future[Response] =
    response(request, Status.NotImplemented, body = body, contentType = StdMediaType.Text_Plain).future

  def notFound(
    request: Request,
    body: CharSequence = "",
    contentType: IMediaType = StdMediaType.Text_Plain
  ): Future[Response] =
    response(request, Status.NotFound, body = body, contentType = StdMediaType.Text_Plain).future

  def badRequest(
    request: Request,
    ref: IErrorRef,
    body: CharSequence = "",
    contentType: IMediaType = StdMediaType.Text_Plain
  ): Future[Response] = {
    val errBody = s"[$ref] $body"
    response(request, Status.BadRequest, StdMediaType.Text_Plain, errBody).future
  }

  def okTextPlain(
    request: Request,
    body: CharSequence = "",
    devbody: CharSequence = ""
  ): Future[Response] =
    textPlain(request, Status.Ok, body, devbody)

  def okJson(
    request: Request,
    body: CharSequence = "",
    devbody: CharSequence = ""
  ): Future[Response] =
    appJson(request, Status.Ok, body, devbody)

  def okAppCdmiObject(
    request: Request,
    body: CharSequence,
    devbody: CharSequence = ""
  ): Future[Response] =
    response(
      request,
      Status.Ok,
      CdmiMediaType.Application_CdmiObject,
      body = body,
      devbody = devbody
    ).future

  def okAppCdmiContainer(
    request: Request,
    body: CharSequence,
    devbody: CharSequence = ""
  ): Future[Response] =
    response(
      request,
      Status.Ok,
      CdmiMediaType.Application_CdmiContainer,
      body = body,
      devbody = devbody
    ).future
}
