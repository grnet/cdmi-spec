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

import com.twitter.app.GlobalFlag
import com.twitter.finagle.http.{Status, Method}
import com.twitter.finagle.netty3.{Netty3Listener, Netty3ListenerTLSConfig}
import com.twitter.finagle.ssl.Ssl
import com.twitter.finagle.{ServerCodecConfig, http, Filter, Http}
import com.twitter.logging.Logger
import com.twitter.util.{FutureTransformer, Await, Future}
import gr.grnet.cdmi.http.{CdmiContentType, CdmiHeader}
import gr.grnet.cdmi.model.CapabilityModel
import gr.grnet.common.http.{StdContentType, IContentType, StdHeader}
import gr.grnet.common.json.Json
import gr.grnet.common.text.{PathToList, NormalizePath}
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
    val uriToCheck = if(isToleratingDoubleSlash) uri.normalizePath else uri
    uriToCheck == "/cdmi_capabilities/"
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

  def response(
    request: Request,
    status: HttpResponseStatus = Status.Ok,
    body: CharSequence = "",
    devbody: CharSequence = "",
    contentType: IContentType = StdContentType.Application_Json
  ): Response = {
    val httpResponse = new DefaultHttpResponse(request.getProtocolVersion(), status)
    val response = Response(httpResponse)

    response.headers().set(HeaderNames.X_CDMI_Specification_Version, currentCdmiVersion)

    val bodyChannelBuffer = copiedBuffer(body, UTF_8)
    response.contentType = contentType.contentType()
    response.contentLength = bodyChannelBuffer.readableBytes()
    response.content = bodyChannelBuffer

    if(dev()) {
      val buffer = new java.lang.StringBuilder
      buffer.append('\n')
      buffer.append(status.toString)

      val method = request.method
      val uri = request.uri
      val decodedUri = try URLDecoder.decode(uri, "UTF-8") catch { case e: Exception ⇒ s"(${e.getMessage}) $uri"}

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

    log.info(s"### END ${request.remoteSocketAddress} ${request.method} ${request.uri} '${request.fileExtension}' ###")
    response
  }

  def internalServerError(request: Request, t: Throwable): Future[Response] = {
    log.error(t, t.toString)
    response(request, Status.InternalServerError, t.toString, "", StdContentType.Text_Plain).future
  }

  def notAllowed(request: Request, body: CharSequence = ""): Future[Response] =
    response(request, Status.MethodNotAllowed, body = body).future

  def notImplemented(request: Request): Future[Response] =
    response(request, Status.NotImplemented).future

  object ContentTypes {
    final val Application_CdmiCapability = CdmiContentType.Application_CdmiCapability.contentType()
    final val Application_CdmiContainer = CdmiContentType.Application_CdmiContainer.contentType()
    final val Application_CdmiDomain = CdmiContentType.Application_CdmiDomain.contentType()
    final val Application_CdmiObject = CdmiContentType.Application_CdmiObject.contentType()
    final val Application_CdmiQueue = CdmiContentType.Application_CdmiQueue.contentType()

    def isCdmiCapability(contentType: String): Boolean = contentType == Application_CdmiCapability

    def isCdmiContainer(contentType: String): Boolean = contentType == Application_CdmiContainer

    def isCdmiDomain(contentType: String): Boolean = contentType == Application_CdmiDomain

    def isCdmiObject(contentType: String): Boolean = contentType == Application_CdmiObject

    def isCdmiQueue(contentType: String): Boolean = contentType == Application_CdmiQueue
  }

  object HeaderNames {
    final val X_CDMI_Specification_Version = CdmiHeader.X_CDMI_Specification_Version.headerName()
    final val Content_Type = StdHeader.Content_Type.headerName()
    final val Accept = StdHeader.Accept.headerName()
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

    // We demand the presence of X-CDMI-Specification-Version only if a cdmi-related Content-Type is present
    final val CdmiVersionHeaderCheck = new Filter {
      override def apply(request: Request, service: Service): Future[Response] = {
        def JustGoOn() = service(request)
        val headers = request.headers()
        val contentType = headers.get(HeaderNames.Content_Type)
        val xCdmiSpecificationVersion = headers.get(HeaderNames.X_CDMI_Specification_Version)

        if(isCdmiRelatedContentType(contentType)) {
          xCdmiSpecificationVersion match {
            case null ⇒
              response(
                request,
                Status.BadRequest,
                s"Please set ${HeaderNames.X_CDMI_Specification_Version}, since a '${HeaderNames.Content_Type}: $contentType' has been requested"
              ).future

            case version if version.isEmpty ⇒
              response(
                request,
                Status.BadRequest,
                s"Please set ${HeaderNames.X_CDMI_Specification_Version}, since a '${HeaderNames.Content_Type}: $contentType' has been requested. In particular, the ${HeaderNames.X_CDMI_Specification_Version} header was present but with an empty value"
              ).future

            case version if supportedCdmiVersions(version) ⇒
              JustGoOn()

            case version ⇒
              response(
                request,
                Status.BadRequest,
                s"Unknown protocol version $version. Supported versions are: ${supportedCdmiVersions.mkString(",")}"
              ).future
          }
        }
        else {
          val methodName = request.method.getName
          // It is OK if X-CDMI-Specification-Header is present in a DELETE, since this means that
          // the request is of CDMI content type.
          if(dev() && (xCdmiSpecificationVersion ne null) && (methodName != HttpMethod.DELETE.getName)) {
            log.warning(s"'${HeaderNames.X_CDMI_Specification_Version}: $xCdmiSpecificationVersion' is present in a ${request.method.getName} request with '${HeaderNames.Content_Type}: $contentType' (non-CDMI)")
          }
          JustGoOn()
        }
      }
    }
  }

  val rootCapabilities: CapabilityModel = CapabilityModel.rootOf()

  // TODO Do we need all the other content types in CdmiContentType?
  def isCdmiRelatedContentType(contentType: String): Boolean =
    contentType match {
      case ContentTypes.Application_CdmiObject |
           ContentTypes.Application_CdmiContainer |
           ContentTypes.Application_CdmiCapability ⇒

        true

      case _ ⇒
        false
    }

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
  def isCdmiObjectAccept(request: Request): Boolean =
    checkAcceptByFirstElem(request)(_ == ContentTypes.Application_CdmiObject)

  def isCdmiObjectOrAnyAccept(request: Request): Boolean =
    checkAcceptByFirstElem(request)(first ⇒ first == ContentTypes.Application_CdmiObject || first == "*/*")

  def isCdmiContainerAccept(request: Request): Boolean =
    checkAcceptByFirstElem(request)(_ == ContentTypes.Application_CdmiContainer)

  def isCdmiContainerOrAnyAccept(request: Request): Boolean =
    checkAcceptByFirstElem(request)(first ⇒ first == ContentTypes.Application_CdmiContainer || first == "*/*")

  def isCdmiQueueAccept(request: Request): Boolean =
    checkAcceptByFirstElem(request)(_ == ContentTypes.Application_CdmiQueue)


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

  /////////////////////////////////////////////////////////////
  //+ Create/Update a data object /////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * Creates/updates a data object in a container using CDMI content type.
   *
   * @note Section 8.2 of CDMI 1.0.2: Create a Data Object Using CDMI Content Type
   * @note Section 8.6 of CDMI 1.0.2: Update a Data Object using CDMI Content Type
   */
  def PUT_object_cdmi(request: Request, objectPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Creates/updates a data object using non-CDMI content type.
   * The given `contentType` is guaranteed to be not null.
   *
   * @note Section 8.3 of CDMI 1.0.2: Create a Data Object using a Non-CDMI Content Type
   * @note Section 8.7 of CDMI 1.0.2: Update a Data Object using a Non-CDMI Content Type
   */
  def PUT_object_noncdmi(request: Request, objectPath: List[String], contentType: String): Future[Response] =
    notImplemented(request)

  /**
   * Create/update a data object.
   */
  def PUT_object(request: Request, objectPath: List[String]): Future[Response] =
    request.headers().get(HeaderNames.Content_Type) match {
      case ContentTypes.Application_CdmiObject ⇒
        PUT_object_cdmi(request, objectPath)

      case null ⇒
        response(request, Status.BadRequest, s"${StdHeader.Content_Type.headerName()} is not set").future

      case contentType ⇒
        PUT_object_noncdmi(request, objectPath, contentType)
    }
  /////////////////////////////////////////////////////////////
  //- Create/Update a data object /////////////////////////////
  /////////////////////////////////////////////////////////////


  /////////////////////////////////////////////////////////////
  //+ Read a data object //////////////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * Read a data object using CDMI content type.
   *
   * @note Section 8.4 of CDMI 1.0.2: Read a Data Object using CDMI Content Type
   */
  def GET_object_cdmi(request: Request, objectPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Read a data object using non-CDMI content type.
   *
   * @note Section 8.5 of CDMI 1.0.2: Read a Data Object using a Non-CDMI Content Type
   */
  def GET_object_noncdmi(request: Request, objectPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Read the contents or value of a data object (depending on the Accept header).
   */
  def GET_object(request: Request, objectPath: List[String]): Future[Response] = {
    val accept = request.headers().get(HeaderNames.Accept)
    if(isCdmiObjectOrAnyAccept(request)) {
      GET_object_cdmi(request, objectPath)
    }
    else if(isCdmiContainerAccept(request)) {
      response(
        request,
        Status.BadRequest,
        s"Requested object at ${request.path} with '${HeaderNames.Accept}: $accept'"
      ).future
    }
    else {
      GET_object_noncdmi(request, objectPath)
    }
  }
  /////////////////////////////////////////////////////////////
  //- Read a data object //////////////////////////////////////
  /////////////////////////////////////////////////////////////

  /////////////////////////////////////////////////////////////
  //+ Delete a data object ////////////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * Deletes a data object in a container using CDMI content type.
   *
   * @note Section 8.8 of CDMI 1.0.2: Delete a Data Object using CDMI Content Type
   */
  def DELETE_object_cdmi(request: Request, objectPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Deletes a data object in a container using non-CDMI content type.
   * The given `contentType` may be `null`.
   *
   * @note Section 8.9 of CDMI 1.0.2: Delete a Data Object using a Non-CDMI Content Type
   */
  def DELETE_object_noncdmi(request: Request, objectPath: List[String], contentType: String): Future[Response] =
    notImplemented(request)

  /**
   * Deletes a data object.
   */
  def DELETE_object(request: Request, objectPath: List[String]): Future[Response] =
    request.headers().get(HeaderNames.Content_Type) match {
      case ContentTypes.Application_CdmiObject ⇒
        DELETE_object_cdmi(request, objectPath)

      case contentType ⇒
        DELETE_object_noncdmi(request, objectPath, contentType)
    }
  /////////////////////////////////////////////////////////////
  //- Delete a data object ////////////////////////////////////
  /////////////////////////////////////////////////////////////


  /////////////////////////////////////////////////////////////
  //+ Create/Update a container  //////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * Creates/updates a container using CDMI content type.
   *
   * @note Section 9.2 of CDMI 1.0.2: Create a Container Object using CDMI Content Type
   * @note Section 9.5 of CDMI 1.0.2: Update a Container Object using CDMI Content Type
   */
  def PUT_container_cdmi(request: Request, containerPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Creates a container using non-CDMI content type.
   * `contentType` may be `null`.
   *
   * @note Section 9.3 of CDMI 1.0.2: Create a Container Object using a Non-CDMI Content Type
   * @note There is no corresponding "Update a Container Object using a Non-CDMI Content Type"
   */
  def PUT_container_noncdmi(request: Request, containerPath: List[String], contentType: String): Future[Response] =
    notImplemented(request)

  /**
   * Creates a new container.
   */
  def PUT_container(request: Request, containerPath: List[String]): Future[Response] =
    request.headers().get(HeaderNames.Content_Type) match {
      case ContentTypes.Application_CdmiContainer ⇒
        PUT_container_cdmi(request, containerPath)

      case contentType ⇒
        PUT_container_noncdmi(request, containerPath, contentType)
    }
  /////////////////////////////////////////////////////////////
  //- Create/Update a container  //////////////////////////////
  /////////////////////////////////////////////////////////////


  /////////////////////////////////////////////////////////////
  //+ Read a container  ///////////////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * Lists the contents of a container using CDMI content type.
   *
   * @note Section 9.4 of CDMI 1.0.2: Read a Container Object using CDMI Content Type
   */
  def GET_container_cdmi(request: Request, containerPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Lists the contents of a container using non-CDMI content type.
   *
   * @note Technically this is not supported by CDMI 1.0.2.
   *       An implementation is free to consolidate with `GET_container_cdmi`
   *       or even override `GET_container`, which in this class delegates here.
   */
  def GET_container_noncdmi(request: Request, containerPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Lists the contents of a container.
   */
  def GET_container(request: Request, containerPath: List[String]): Future[Response] = {
    val accept = request.headers().get(HeaderNames.Accept)
    if(isCdmiContainerOrAnyAccept(request)) {
      GET_container_cdmi(request, containerPath)
    }
    else if(isCdmiObjectAccept(request)) {
      response(
        request,
        Status.BadRequest,
        s"Requested container at ${request.path} with '${HeaderNames.Accept}: $accept'"
      ).future
    }
    else {
      GET_container_noncdmi(request, containerPath)
    }
  }
  /////////////////////////////////////////////////////////////
  //- Read a container  ///////////////////////////////////////
  /////////////////////////////////////////////////////////////


  /////////////////////////////////////////////////////////////
  //+ Delete a container  /////////////////////////////////////
  /////////////////////////////////////////////////////////////

  /**
   * Deletes a container using a CDMI content type.
   *
   * @note Section 9.6 of CDMI 1.0.2: Delete a Container Object using CDMI Content Type
   */
  def DELETE_container_cdmi(request: Request, containerPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Deletes a container using a non-CDMI content type.
   *
   * @note Section 9.7 of CDMI 1.0.2: Delete a Container Object using a Non-CDMI Content Type
   */
  def DELETE_container_noncdmi(request: Request, containerPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Deletes a container. In particular,
   * according to sections 9.6 and 9.7 of CDMI version 1.0.2, the
   * presence of `X-CDMI-Specification-Version` is what designates
   * the request as being of CDMI content type.
   */
  def DELETE_container(request: Request, containerPath: List[String]): Future[Response] =
    request.headers().get(HeaderNames.X_CDMI_Specification_Version) match {
      case null ⇒
        DELETE_container_noncdmi(request, containerPath)

      case _ ⇒
        DELETE_container_cdmi(request, containerPath)
    }
  /////////////////////////////////////////////////////////////
  //- Delete a container  /////////////////////////////////////
  /////////////////////////////////////////////////////////////


  /////////////////////////////////////////////////////////////
  //+ Create/update a domain  /////////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * FIXME This probably needs more refinement
   * @note Section 10.2 of CDMI 1.0.2: Create a Domain Object using CDMI Content Type
   * @note Section 10.4 of CDMI 1.0.2: Update a Domain Object using CDMI Content Type
   */
  def PUT_domain_cdmi(request: Request, domainPath: List[String]): Future[Response] =
    notImplemented(request)
  /////////////////////////////////////////////////////////////
  //- Create/update a domain  /////////////////////////////////
  /////////////////////////////////////////////////////////////


  /////////////////////////////////////////////////////////////
  //+ Read a domain  //////////////////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * FIXME This probably needs more refinement
   * @note Section 10.3 of CDMI 1.0.2: Create a Domain Object using CDMI Content Type
   */
  def GET_domain_cdmi(request: Request, domainPath: List[String]): Future[Response] =
    notImplemented(request)
  /////////////////////////////////////////////////////////////
  //- Read a domain  //////////////////////////////////////////
  /////////////////////////////////////////////////////////////


  /////////////////////////////////////////////////////////////
  //+ Delete a domain  ////////////////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * FIXME This probably needs more refinement
   * @note Section 10.3 of CDMI 1.0.2: Create a Domain Object using CDMI Content Type
   */
  def DELETE_domain_cdmi(request: Request, domainPath: List[String]): Future[Response] =
    response(request, Status.NotImplemented).future
  /////////////////////////////////////////////////////////////
  //- Delete a domain  ////////////////////////////////////////
  /////////////////////////////////////////////////////////////

  /////////////////////////////////////////////////////////////
  //+ Queue object and queue object values operations /////////
  /////////////////////////////////////////////////////////////
  def GET_queue(request: Request, queuePath: List[String]): Future[Response] =
    notImplemented(request)

  def POST_queue(request: Request, queuePath: List[String]): Future[Response] =
    notImplemented(request)

  def PUT_queue(request: Request, queuePath: List[String]): Future[Response] =
    notImplemented(request)

  def DELETE_queue(request: Request, queuePath: List[String]): Future[Response] =
    notImplemented(request)
  /////////////////////////////////////////////////////////////
  //- Queue object and queue object values operations /////////
  /////////////////////////////////////////////////////////////

  def routingTable: PartialFunction[Request, Future[Response]] = {
    case request ⇒
      val method = request.method
      val uri = request.uri
      val requestPath = request.path
      val normalizedPath = requestPath.normalizePath

      val getObjectByIdPF: (HttpMethod, List[String]) ⇒ Future[Response] =
        (method, objectIdPath) ⇒ method match {
          case Method.Get  ⇒ GET_objectById(request, objectIdPath)
          case Method.Post ⇒ POST_objectById(request, objectIdPath)
          case Method.Put  ⇒ PUT_objectById(request, objectIdPath)
          case _           ⇒ response(request, Status.MethodNotAllowed).future
        }

      log.info(s"### BEGIN ${request.remoteSocketAddress} ${request.method} ${request.uri} '${request.fileExtension}' ###")
      log.debug(s"${method.getName} $uri")
      val pathElements = normalizedPath.pathToList
      val lastIsSlash = normalizedPath(normalizedPath.length - 1) == '/'
      val pathElementsDebugStr = pathElements.map(s ⇒ "\"" + s + "\"").mkString(" ") + (if(lastIsSlash) " [/]" else "")
      log.debug(s"${method.getName} $pathElementsDebugStr")
      val HAVE_SLASH = true
      val HAVE_NO_SLASH = false

      (pathElements, lastIsSlash) match {
        case (Nil, _) ⇒
          // "/"
          response(request, Status.MethodNotAllowed).future

        case ("" :: Nil, _) ⇒
          // ""
          response(request, Status.MethodNotAllowed).future

        case ("" ::  "cdmi_capabilities" :: Nil, HAVE_SLASH) ⇒
          // "/cdmi_capabilities/"
          method match {
            case Method.Get ⇒ GET_capabilities(request)
            case _ ⇒ response(request, Status.MethodNotAllowed).future
          }

        case ("" ::  "cdmi_capabilities" :: Nil, HAVE_NO_SLASH) ⇒
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

        case ("" ::  "cdmi_domains" :: domainPath, HAVE_SLASH) ⇒
          // "/cdmi_domains/"
          // According to Section 10.1 CDMI 1.0.2, this prefix is reserved for domain URIs
          method match {
            case Method.Get ⇒ GET_domain_cdmi(request, domainPath)
            case Method.Put ⇒ PUT_domain_cdmi(request, domainPath)
            case Method.Delete ⇒ DELETE_domain_cdmi(request, domainPath)
            case _ ⇒ response(request, Status.MethodNotAllowed).future
          }

        case ("" ::  "cdmi_domains" :: Nil, HAVE_NO_SLASH) ⇒
          // "/cdmi_domains"
          // Just being helpful here
          response(
            request,
            Status.BadRequest,
            "Probably you meant to call /cdmi_domains/ instead of /cdmi_domains"
          ).future

        case ("" :: containerPath, HAVE_SLASH) ⇒
          method match {
            case Method.Get    ⇒ GET_container   (request, containerPath)
            case Method.Put    ⇒ PUT_container   (request, containerPath)
            case Method.Delete ⇒ DELETE_container(request, containerPath)
            case _ ⇒ response(request, Status.MethodNotAllowed).future
          }

        case ("" :: objectPath, HAVE_NO_SLASH) ⇒
          val contentType = request.headers().get(HeaderNames.Content_Type)

          // Separation of queues and data objects is not well defined
          // and this is an approximate solution/approach.
          //
          // In particular:
          //  - Section 11.2 of CDMI 1.0.2 "Create a Queue Object using CDMI Content Type"
          //      requires Content-Type: application/cdmi-queue and requires the presence of X-CDMI-Specification-Version header
          //  - Section 11.3 of CDMI 1.0.2 "Read a Queue Object using CDMI Content Type"
          //      has an optional Accept header and requires the presence of X-CDMI-Specification-Version header
          //  - Section 11.4 of CDMI 1.0.2 "Update a Queue Object using CDMI Content Type"
          //      requires Content-Type: application/cdmi-queue and requires the presence of X-CDMI-Specification-Version header
          //  - Section 11.5 of CDMI 1.0.2 "Delete a Queue Object using CDMI Content Type"
          //      says nothing about Accept header and requires the presence of X-CDMI-Specification-Version header
          //  - Section 11.6 of CDMI 1.0.2 "Enqueue a New Queue Value using CDMI Content Type"
          //      requires Content-Type: application/cdmi-queue and requires the presence of X-CDMI-Specification-Version header
          //  - Section 11.7 of CDMI 1.0.2 "Delete a Queue Object Value using CDMI Content Type"
          //      says nothing about Accept header and requires the presence of X-CDMI-Specification-Version header

          if(ContentTypes.isCdmiQueue(contentType) || isCdmiQueueAccept(request)) {
            val queuePath = objectPath
            contentType match {
              case ContentTypes.Application_CdmiQueue ⇒
                method match {
                  case Method.Get    ⇒ GET_queue   (request, queuePath)
                  case Method.Post   ⇒ POST_queue  (request, queuePath)
                  case Method.Put    ⇒ PUT_queue   (request, queuePath)
                  case Method.Delete ⇒ DELETE_queue(request, queuePath)
                  case _ ⇒ response(request, Status.MethodNotAllowed).future
                }
            }
          }
          else {
            // Not a queue thing, just data objects

            method match {
              case Method.Get    ⇒ GET_object   (request, objectPath)
              case Method.Put    ⇒ PUT_object   (request, objectPath)
              case Method.Delete ⇒ DELETE_object(request, objectPath)
              case _ ⇒ response(request, Status.MethodNotAllowed).future
            }
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
