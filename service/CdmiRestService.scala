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

import java.io.File
import java.net.{InetSocketAddress, SocketAddress, URLDecoder}

import com.twitter.app.GlobalFlag
import com.twitter.conversions.storage.intToStorageUnitableWholeNumber
import com.twitter.finagle.dispatch.SerialServerDispatcher
import com.twitter.finagle.http.Status
import com.twitter.finagle.netty3.{Netty3Listener, Netty3ListenerTLSConfig}
import com.twitter.finagle.server.DefaultServer
import com.twitter.finagle.ssl.Ssl
import com.twitter.finagle.{Filter, Http, ServerCodecConfig, http}
import com.twitter.logging.Logger
import com.twitter.util.{Await, Future, FutureTransformer}
import gr.grnet.cdmi.capability.SystemWideCapability
import gr.grnet.cdmi.http.{CdmiHeader, CdmiMediaType}
import gr.grnet.cdmi.model.CapabilityModel
import gr.grnet.common.http.{StdHeader, StdMediaType}
import gr.grnet.common.text.{NormalizePath, PathToList}
import gr.grnet.pithosj.core.http.PithosHeader
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http.{DefaultHttpResponse, HttpRequest, HttpResponse, HttpVersion}
import org.jboss.netty.util.CharsetUtil.UTF_8

import scala.collection.immutable.Seq

object port          extends GlobalFlag[InetSocketAddress](new InetSocketAddress(8080), "http port")
object dev           extends GlobalFlag[Boolean](false, "enable development mode")
object tolerateDoubleSlash extends GlobalFlag[Boolean](false, "Tolerate // in URIs. If true, will collapse them to /")
object maxRequestSize  extends GlobalFlag[Int](10, "Max request size (MB)")
object sslPort       extends GlobalFlag[InetSocketAddress](new InetSocketAddress(443), "https port")
object sslCertPath   extends GlobalFlag[String]("", "SSL certificate path")
object sslKeyPath    extends GlobalFlag[String]("", "SSL key path")

/**
 * A skeleton for the implementation of a CDMI-compliant REST service.
 *
 * @note We base our implementation on version 1.0.2 of the spec.
 * @note When we use the term `object` alone, we mean `data object`.
 * @note When we use the term `queue` alone, we mean `queue object`
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
trait CdmiRestService { self: CdmiRestServiceTypes
                         with CdmiRestServiceHandlers
                         with CdmiRestServiceMethods
                         with CdmiRestServiceResponse ⇒

  def isToleratingDoubleSlash = tolerateDoubleSlash()

  def isCdmiCapabilitiesUri(uri: String): Boolean = {
    val uriToCheck = if(isToleratingDoubleSlash) uri.normalizePath else uri
    (uriToCheck == "/cdmi_capabilities/") || (uriToCheck == "/cdmi_capabilities")
  }

  /**
   * This will normally be provided by [[com.twitter.logging.Logging]]
   */
  val log: Logger

  val httpVersion: HttpVersion = HttpVersion.HTTP_1_1

  def supportedCdmiVersions: Set[String] = Set("1.0.2")

  /**
   * The CDMI version we report back to an HTTP response.
   */
  def currentCdmiVersion: String = "1.0.2"

  def flags: Seq[GlobalFlag[_]] = Seq(
    port,
    dev,
    tolerateDoubleSlash,
    maxRequestSize,
    sslPort,
    sslCertPath,
    sslKeyPath
  )

  def end(request: Request, response: Response): Response = {
    logEndRequest(request)
    response
  }


  object MediaTypes {
    final val Text_Plain = StdMediaType.Text_Plain.value()
    final val Text_Html = StdMediaType.Text_Html.value()
    final val Application_Directory = StdMediaType.Application_Directory.value()
    final val Application_DirectorySemi = s"$Application_Directory;"
    final val Application_Folder = StdMediaType.Application_Folder.value()
    final val Application_FolderSemi = s"$Application_Folder;"

    final val Application_CdmiCapability = CdmiMediaType.Application_CdmiCapability.value()
    final val Application_CdmiContainer = CdmiMediaType.Application_CdmiContainer.value()
    final val Application_CdmiDomain = CdmiMediaType.Application_CdmiDomain.value()
    final val Application_CdmiObject = CdmiMediaType.Application_CdmiObject.value()
    final val Application_CdmiQueue = CdmiMediaType.Application_CdmiQueue.value()

    def isCdmiCapability(mediaType: String): Boolean = mediaType == Application_CdmiCapability

    def isCdmiContainer(mediaType: String): Boolean = mediaType == Application_CdmiContainer

    def isCdmiDomain(mediaType: String): Boolean = mediaType == Application_CdmiDomain

    def isCdmiObject(mediaType: String): Boolean = mediaType == Application_CdmiObject

    def isCdmiQueue(mediaType: String): Boolean = mediaType == Application_CdmiQueue

    def isCdmi(mediaType: String): Boolean =
      isCdmiCapability(mediaType) ||
      isCdmiContainer(mediaType) ||
      isCdmiDomain(mediaType) ||
      isCdmiObject(mediaType) ||
      isCdmiQueue(mediaType)

    def isCdmiLike(mediaType: String): Boolean = (mediaType ne null) && mediaType.startsWith("application/cdmi-")
  }

  object Accept {
    /**
     * Checks the `Accept` header value and returns `true` iff its first media type makes `p` succeed.
     * Returns `false` if `Accept` either does not exist or is empty.
     *
     * @note We ignore any `q` weights and we rely only on what media type comes first.
     */
    def checkAcceptByFirstElem(request: Request)(p: (String) ⇒ Boolean): Boolean = {
      val mediaTypes = request.acceptMediaTypes
      mediaTypes.nonEmpty && {
        val first = mediaTypes(0)
        // FIXME we ignore any weights and we just check if the first is what we want
        p(first)
      }
    }

    def isAny(request: Request): Boolean =
      checkAcceptByFirstElem(request)(_ == "*/*")

    def isCdmiObject(request: Request): Boolean =
      checkAcceptByFirstElem(request)(_ == MediaTypes.Application_CdmiObject)

    def isCdmiObjectOrAny(request: Request): Boolean =
      checkAcceptByFirstElem(request)(first ⇒ first == MediaTypes.Application_CdmiObject || first == "*/*")

    def isCdmiContainer(request: Request): Boolean =
      checkAcceptByFirstElem(request)(_ == MediaTypes.Application_CdmiContainer)

    def isCdmiContainerOrAny(request: Request): Boolean =
      checkAcceptByFirstElem(request)(first ⇒ first == MediaTypes.Application_CdmiContainer || first == "*/*")

    def isCdmiQueue(request: Request): Boolean =
      checkAcceptByFirstElem(request)(_ == MediaTypes.Application_CdmiQueue)

    def isCdmiLike(request: Request): Boolean =
      checkAcceptByFirstElem(request)(MediaTypes.isCdmiLike)
  }

  object HeaderNames {
    final val X_CDMI_Specification_Version = CdmiHeader.X_CDMI_Specification_Version.headerName()
    final val X_Auth_Token = PithosHeader.X_Auth_Token.headerName()
    final val Content_Type = StdHeader.Content_Type.headerName()
    final val Accept = StdHeader.Accept.headerName()
    final val WWW_Authenticate = StdHeader.WWW_Authenticate.headerName()
    final val Content_Length = StdHeader.Content_Length.headerName()
  }

  object Filters {
    final val RogueExceptionHandler = new Filter {
      val ft = new FutureTransformer[Response, Response] {

        override def map(value: Response): Response = value

        override def rescue(t: Throwable): Future[Response] = {
          log.error(t, "Unexpected Error")
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
          badRequest(request, StdErrorRef.BR001, s"Double slashes are not tolerated in URIs")
        }
        else {
          service(request)
        }
      }
    }

    // If X-CDMI-Specification-Version is present then we check the value
    final val CdmiHeaderCheck = new Filter {
      override def apply(request: Request, service: Service): Future[Response] = {
        def JustGoOn() = service(request)
        val headers = request.headers()
        val contentType = headers.get(HeaderNames.Content_Type)
        val xCdmiSpecificationVersion = headers.get(HeaderNames.X_CDMI_Specification_Version)

        xCdmiSpecificationVersion match {
          case null ⇒
            JustGoOn()

          case "" ⇒
            badRequest(
              request,
              StdErrorRef.BR002,
              s"Empty value for header '${HeaderNames.X_CDMI_Specification_Version}'"
            )

          case v if supportedCdmiVersions(v) ⇒
            JustGoOn()

          case v ⇒ badRequest(
            request,
            StdErrorRef.BR003,
            s"Unknown protocol version $v. Supported versions are: ${supportedCdmiVersions.mkString(",")}"
          )
        }
      }
    }
  }

  val defaultSystemWideCapabilities = CapabilityModel.rootOf(
    capabilities = Map(
      SystemWideCapability.cdmi_dataobjects → true.toString,
      SystemWideCapability.cdmi_metadata_maxitems → 0.toString // TODO no metadata currently supported
    )
  )

  def systemWideCapabilities: CapabilityModel = defaultSystemWideCapabilities

  def logBeginRequest(request: Request): Unit = {
    log.info(s"### BEGIN ${request.remoteSocketAddress} ${request.method} ${request.uri} ###")
  }

  def logEndRequest(request: Request): Unit = {
    log.info(s"### END ${request.remoteSocketAddress} ${request.method} ${request.uri} ###")
  }

  def routingTable: PartialFunction[Request, Future[Response]] = {
    case request ⇒
      def NotAllowed() = notAllowed(request)

      val headers = request.headers()
      val method = request.method
      val uri = request.uri
      val decodedUri = try URLDecoder.decode(uri, "UTF-8") catch { case e: Exception ⇒ s"(${e.getMessage}) $uri"}
      val requestPath = request.path
      val normalizedPath = requestPath.normalizePath

      logBeginRequest(request)
      log.debug(s"(original) ${method.getName} $uri")
      if(decodedUri != uri) {
        log.debug(s"(decoded)  ${method.getName} $decodedUri")
      }

      val pathElements = normalizedPath.pathToList
      val lastIsSlash = normalizedPath(normalizedPath.length - 1) == '/'
      val pathElementsDebugStr = pathElements.map(s ⇒ "\"" + s + "\"").mkString(" ") + (if(lastIsSlash) " [/]" else "")
      log.debug(s"(as list)  ${method.getName} $pathElementsDebugStr")

      def logHeader(name: String, exact: Boolean = true): Unit = if(headers.contains(name) ) {
        val value = headers.get(name)
        if(exact) { log.debug(s"'$name: $value'") }
        else      { log.debug(s"'$name: ${value.substring(0, value.length / 3)}${"*" * (2 * value.length / 3)}'") }
      }

      logHeader(HeaderNames.X_CDMI_Specification_Version)
      logHeader(HeaderNames.X_Auth_Token, exact = false)
      logHeader(HeaderNames.Content_Type)
      logHeader(HeaderNames.Accept)

      val HAVE_SLASH = true
      val HAVE_NO_SLASH = false

      (pathElements, lastIsSlash) match {
        case (Nil, _) ⇒
          // "/"
          log.debug("handleRootCall")
          handleRootCall(request)

        case ("" :: Nil, _) ⇒
          // ""
          log.debug("handleRootNoSlashCall")
          handleRootNoSlashCall(request)

        case ("" ::  "cdmi_capabilities" :: Nil, HAVE_SLASH) ⇒
          // "/cdmi_capabilities/"
          log.debug("handleCapabilitiesCall")
          handleCapabilitiesCall(request)

        case ("" ::  "cdmi_capabilities" :: Nil, HAVE_NO_SLASH) ⇒
          // "/cdmi_capabilities"
          log.debug("handleCapabilitiesNoSlashCall")
          handleCapabilitiesNoSlashCall(request)

        case ("" :: ("cdmi_objectid" | "cdmi_objectId" | "cdmi_objectID") :: objectIdPath, _) ⇒
          log.debug("handleObjectByIdCall")
          handleObjectByIdCall(request, objectIdPath)

        case ("" ::  "cdmi_domains" :: domainPath, HAVE_SLASH) ⇒
          // "/cdmi_domains/"
          log.debug("handleDomainCall")
          // According to Section 10.1 CDMI 1.0.2, this prefix is reserved for domain URIs
          handleDomainCall(request, domainPath)

        case ("" ::  "cdmi_domains" :: Nil, HAVE_NO_SLASH) ⇒
          // "/cdmi_domains"
          log.debug("handleDomainNoSlashCall")
          handleDomainNoSlashCall(request)

        case ("" :: containerPath, HAVE_SLASH) ⇒
          log.debug("handleContainerCall")
          // An ending slash means a container-related call
          handleContainerCall(request, containerPath)

        case ("" :: pathList /* for object or queue */, HAVE_NO_SLASH) ⇒
          log.debug("handleObjectOrQueueCall")
          handleObjectOrQueueCall(request, pathList)

        case _ ⇒
          log.debug("CATCHALL")
          NotAllowed()
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
      Filters.CdmiHeaderCheck
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
