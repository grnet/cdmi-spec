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

import com.twitter.app.GlobalFlag
import com.twitter.finagle.http.{Status, Method}
import com.twitter.finagle.netty3.{Netty3Listener, Netty3ListenerTLSConfig}
import com.twitter.finagle.ssl.Ssl
import com.twitter.finagle.{ServerCodecConfig, http, Filter, Http}
import com.twitter.logging.Logger
import com.twitter.util.{FutureTransformer, Await, Future}
import gr.grnet.cdmi.http.{CdmiContentType, CdmiHeader}
import gr.grnet.cdmi.model.CapabilityModel
import gr.grnet.common.http.StdHeader
import gr.grnet.common.json.Json
import gr.grnet.common.text.{UriToList, NormalizeUri}
import java.io.File
import java.net.{SocketAddress, URLDecoder, InetSocketAddress}
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http.{HttpVersion, HttpResponse, HttpRequest, HttpMethod, HttpResponseStatus, DefaultHttpResponse}
import org.jboss.netty.util.CharsetUtil.UTF_8
import scala.collection.immutable.Seq
import com.twitter.finagle.server.DefaultServer
import com.twitter.finagle.dispatch.SerialServerDispatcher
import com.twitter.conversions.storage.intToStorageUnitableWholeNumber

object port          extends GlobalFlag[InetSocketAddress](new InetSocketAddress(8080), "http port")
object dev           extends GlobalFlag[Boolean](false, "enable development mode")
object pithosTimeout extends GlobalFlag[Long](1000L * 60L * 3L /* 3 min*/, "millis to wait for Pithos response")
object tolerateDoubleSlash extends GlobalFlag[Boolean](false, "Tolerate // in URIs. If true, will collapse them to /")
object maxRequestSize  extends GlobalFlag[Int](10, "Max request size (MB)")
object sslPort       extends GlobalFlag[InetSocketAddress](new InetSocketAddress(443), "https port")
object sslCertPath   extends GlobalFlag[String]("", "SSL certificate path")
object sslKeyPath    extends GlobalFlag[String]("", "SSL key path")

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

  def isToleratingDoubleSlash = tolerateDoubleSlash()

  def isCdmiCapabilitiesUri(uri: String): Boolean = {
    val uriToCheck = if(isToleratingDoubleSlash) uri.normalizeUri else uri
    uriToCheck == "/cdmi_capabilities/"
  }

  /**
   * This will normally be provided by [[com.twitter.logging.Logging]]
   */
  val log: Logger

  val httpVersion: HttpVersion = HttpVersion.HTTP_1_1

  def supportedCdmiVersions: Set[String] = Set("1.0.2")

  def flags: Seq[GlobalFlag[_]] = Seq(
    port,
    dev,
    pithosTimeout,
    tolerateDoubleSlash,
    maxRequestSize,
    sslPort,
    sslCertPath,
    sslKeyPath
  )

  def response(
    request: Request,
    status: HttpResponseStatus = Status.Ok,
    body: CharSequence = "",
    devbody: CharSequence = ""
  ): Response = {
    val response = new DefaultHttpResponse(request.getProtocolVersion(), status)
    response.setContent(copiedBuffer(body, UTF_8))

    if(dev()) {
      val buffer = new java.lang.StringBuilder
      buffer.append('\n')
      buffer.append(status.toString)

      val method = request.method
      val uri = request.uri
      val decodedUri = try URLDecoder.decode(uri, "UTF-8") catch { case e: Exception ⇒ s"(${e.getMessage}) ${uri}"}

      buffer.append('\n')
      buffer.append(method.getName)
      buffer.append(' ')
      buffer.append(uri)
      buffer.append('\n')
      buffer.append(" " * (method.getName.length + 1))
      buffer.append(decodedUri)

      if(devbody.length() > 0) {
        buffer.append("\n[")
        buffer.append(devbody)
        buffer.append("]\n")
      }

      if(body.length() > 0) {
        buffer.append('\n')
        buffer.append("=== actual body follows ===\n")
        buffer.append(body)
      }

      log.info(buffer.toString)
    }

    Response(response)
  }

  def internalServerError(request: Request, t: Throwable): Future[Response] = {
    log.error(t, t.toString)
    response(request, Status.InternalServerError, t.toString).future
  }

  object Headers {
    final val X_CDMI_Specification_Version = CdmiHeader.X_CDMI_Specification_Version.headerName()
  }

  object Filters {
    final val RogueExceptionHandler = new Filter {
      val ft = new FutureTransformer[Response, Response] {

        override def map(value: Response): Response = value

        override def rescue(t: Throwable): Future[Response] = {
          log.critical(t, "")
          val response = new DefaultHttpResponse(httpVersion, Status.InternalServerError)
          val body = ""
          response.setContent(copiedBuffer(body, UTF_8))
          Response(response).future
        }
      }

      override def apply(request: Request, service: Service): Future[Response] = {
        service(request).transformedBy(ft)
      }
    }


    final val DoubleSlashCheck = new Filter {
      override def apply(request: Request, service: Service): Future[Response] = {
        val uri = request.uri

        if(!isToleratingDoubleSlash && uri.contains("//")) {
          response(
            request,
            Status.BadRequest,
            s"Double slashes are not tolerated in URIs"
          ).future
        }
        else {
          service(request)
        }
      }
    }

    final val CdmiVersionHeaderCheck = new Filter {
      override def apply(request: Request, service: Service): Future[Response] = {
        request.headers().get(Headers.X_CDMI_Specification_Version) match {
          case null ⇒
            response(
              request,
              Status.BadRequest,
              s"Please set ${Headers.X_CDMI_Specification_Version}"
            ).future

          case version if supportedCdmiVersions(version) ⇒
            service(request)

          case version ⇒
            response(
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
    response(request, Status.NotImplemented).future

  def POST_objectById(request: Request, objectIdPath: List[String]): Future[Response] =
    response(request, Status.NotImplemented).future

  def PUT_objectById(request: Request, objectIdPath: List[String]): Future[Response] =
    response(request, Status.NotImplemented).future

  /**
   * Read the contents or value of a data object (depending on the Accept header).
   */
  def GET_object(request: Request, objectPath: List[String]): Future[Response] =
    response(request, Status.NotImplemented).future

  /**
   * Create a data object in a container.
   */
  def PUT_object(request: Request, objectPath: List[String]): Future[Response] =
    request.headers().get(StdHeader.Content_Type.headerName()) match {
      case s if s == CdmiContentType.Application_CdmiObject.contentType() ⇒
        PUT_object_cdmi(request, objectPath)

      case null ⇒
        response(request, Status.BadRequest, s"${StdHeader.Content_Type.headerName()} is not set").future

      case contentType ⇒
        PUT_object_noncdmi(request, objectPath, contentType)
    }

  /**
   * Create a data object in a container using CDMI content type.
   */
  def PUT_object_cdmi(request: Request, objectPath: List[String]): Future[Response] =
    response(request, Status.NotImplemented).future

  /**
   * Create a data object in a container using non-CDMI content type.
   * The given `contentType` is guaranteed to be not null.
   */
  def PUT_object_noncdmi(request: Request, objectPath: List[String], contentType: String): Future[Response] =
    response(request, Status.NotImplemented).future

  /**
   * Delete a data object.
   */
  def DELETE_object(request: Request, objectPath: List[String]): Future[Response] =
    response(request, Status.NotImplemented).future

  /**
   * Lists the contents of a container.
   */
  def GET_container(request: Request, containerPath: List[String]): Future[Response] =
    response(request, Status.NotImplemented).future

  /**
   * Creates a new container.
   */
  def PUT_container(request: Request, containerPath: List[String]): Future[Response] =
    response(request, Status.NotImplemented).future

  /**
   * Deletes a container.
   */
  def DELETE_container(request: Request, containerPath: List[String]): Future[Response] =
    response(request, Status.NotImplemented).future

  def routingTable: PartialFunction[Request, Future[Response]] = {
    case request ⇒
      val method = request.method
      val normalizedUri = request.uri.normalizeUri

      val getObjectByIdPF: (HttpMethod, List[String]) ⇒ Future[Response] =
        (method, objectIdPath) ⇒ method match {
          case Method.Get  ⇒ GET_objectById(request, objectIdPath)
          case Method.Post ⇒ POST_objectById(request, objectIdPath)
          case Method.Put  ⇒ PUT_objectById(request, objectIdPath)
          case _           ⇒ response(request, Status.MethodNotAllowed).future
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
          response(request, Status.MethodNotAllowed).future


        case ("" :: Nil, _) ⇒
          // ""
          response(request, Status.MethodNotAllowed).future

        case ("" ::  "cdmi_capabilities" :: Nil, SLASH) ⇒
          // "/cdmi_capabilities/"
          method match {
            case Method.Get ⇒ GET_capabilities(request)
            case _ ⇒ response(request, Status.MethodNotAllowed).future
          }

        case ("" ::  "cdmi_capabilities" :: Nil, NOSLASH) ⇒
          // "/cdmi_capabilities"
          // Just being helpful here
          response(
            request,
            Status.BadRequest,
            "Probably you meant to call /cdmi_capabilities/ instead of /cdmi_capabilities"
          ).future

        case ("" :: "cdmi_objectid" :: objectIdPath, _) ⇒
          getObjectByIdPF(method, objectIdPath)
        case ("" :: "cdmi_objectId" :: objectIdPath, _) ⇒
          getObjectByIdPF(method, objectIdPath)
        case ("" :: "cdmi_objectID" :: objectIdPath, _) ⇒
          getObjectByIdPF(method, objectIdPath)

        case ("" :: containerPath, SLASH) ⇒
          method match {
            case Method.Get    ⇒ GET_container   (request, containerPath)
            case Method.Put    ⇒ PUT_container   (request, containerPath)
            case Method.Delete ⇒ DELETE_container(request, containerPath)
            case _ ⇒ response(request, Status.MethodNotAllowed).future
          }

        case ("" :: objectPath, NOSLASH) ⇒
          method match {
            case Method.Get    ⇒ GET_object   (request, objectPath)
            case Method.Put    ⇒ PUT_object   (request, objectPath)
            case Method.Delete ⇒ DELETE_object(request, objectPath)
            case _ ⇒ response(request, Status.MethodNotAllowed).future
          }

        case _ ⇒
          response(request, Status.MethodNotAllowed).future
      }
  }

  def banner = gr.grnet.cdmi.Banner
  def printBanner(): Unit = log.info(banner)

  def logFlag[T](flag: GlobalFlag[T]): Unit =
    log.info(s"${flag.name} = ${flag.apply()}")

  def logFlags(): Unit = for(flag ← flags) logFlag(flag)

  def mainService: Service =
    new Service {
      override def apply(request: Request): Future[Response] = routingTable(request)
    }

  def mainFilters: Vector[Filter] =
    Vector(
      Filters.RogueExceptionHandler,
      Filters.DoubleSlashCheck,
      Filters.CdmiVersionHeaderCheck
    )

  val nettyToFinagle =
    Filter.mk[HttpRequest, HttpResponse, Request, Response] { (req, service) =>
      service(Request(req)) map { _.httpResponse }
    }

  def haveSslCertPath =
    sslCertPath() match {
      case null ⇒ false
      case s if s.isEmpty ⇒ false
      case _ ⇒ true
    }

  def haveSslKeyPath =
    sslKeyPath() match {
      case null ⇒ false
      case s if s.isEmpty ⇒ false
      case _ ⇒ true
    }

  def main(): Unit = {
    printBanner()
    logFlags()

    val service = (mainFilters :\ mainService) { (filter, service) ⇒ filter andThen service }

    (haveSslCertPath, haveSslKeyPath) match {
      case (false, false) ⇒
        // No SSL. Just start an http server
        log.info("Starting HTTP server on " + port().getPort)
        val server = Http.serve(
          port(),
          nettyToFinagle andThen service
        )

        Await.ready(server)

      case (_, false) | (false, _) ⇒
        System.err.println(s"You specified only one of ${sslCertPath.name}, ${sslKeyPath.name}. Either omit them both or given them values")
        sys.exit(3)

      case (true, true) ⇒
        val certFile = new File(sslCertPath())
        val certFileOK = certFile.isFile && certFile.canRead
        if(!certFileOK) {
          System.err.println("SSL certificate not found")
          sys.exit(1)
        }

        val keyFile = new File(sslKeyPath())
        val keyFileOK = keyFile.isFile && keyFile.canRead
        if(!keyFileOK) {
          System.err.println("SSL key not found")
          sys.exit(2)
        }

        val codec = {
          http.Http()
            .maxRequestSize(maxRequestSize().megabytes)
            .server(ServerCodecConfig("cdmi", new SocketAddress{}))
            .pipelineFactory
        }
        val tlsConfig = Some(Netty3ListenerTLSConfig(() => Ssl.server(sslCertPath(), sslKeyPath(), null, null, null)))
        object HttpsListener extends Netty3Listener[HttpResponse, HttpRequest]("https", codec, tlsConfig = tlsConfig)
        object Https extends DefaultServer[HttpRequest, HttpResponse, HttpResponse, HttpRequest](
          "https", HttpsListener, new SerialServerDispatcher(_, _)
        )

        log.info("Starting HTTPS server on " + sslPort().getPort)
        val server = Https.serve(sslPort(), nettyToFinagle andThen service)
        Await.ready(server)
    }
  }
}
