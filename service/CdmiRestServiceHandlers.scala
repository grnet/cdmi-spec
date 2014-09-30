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

import com.twitter.finagle.http.Method
import com.twitter.util.Future

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
trait CdmiRestServiceHandlers { self: CdmiRestService
                                 with CdmiRestServiceTypes
                                 with CdmiRestServiceMethods
                                 with CdmiRestServiceResponse ⇒

  @inline final def MANDATORY[T](t: T) = t // for documentation purposes; communicates spec-defined behavior
  @inline final def OPTIONAL [T](t: T) = t // for documentation purposes; communicates spec-defined behavior
  @inline final def HELPER   [T](t: T) = t // for documentation purposes; communicates not spec-defined behavior but one that is helping to understand the situation

  def handleContainerCall(request: Request, containerPath: List[String]): Future[Response] = {
    def NotAllowed() = notAllowed(request)
    val method = request.method
    val headers = request.headers()
    val hSpecVersion = headers.get(HeaderNames.X_CDMI_Specification_Version)
    val hContentType = headers.get(HeaderNames.Content_Type)
    val mediaType = request.mediaType.orNull // Content-Type without any baggage
    val hAccept = headers.get(HeaderNames.Accept)

    val haveContentType = hContentType ne null
    val haveSpecVersion = hSpecVersion ne null
    val haveAccept = hAccept ne null

    val isContainerAccept = haveAccept && Accept.isCdmiContainer(request)
    val isObjectAccept = !isContainerAccept && Accept.isCdmiObject(request)
    val isAnyAccept =  !isObjectAccept && Accept.isAny(request)
    val isQueueAccept = !isAnyAccept && Accept.isCdmiQueue(request)
    val isContainerContentType = haveContentType && MediaTypes.isCdmiContainer(mediaType)
    val isObjectContentType = !isContainerContentType && MediaTypes.isCdmiObject(mediaType)
    val isQueueContentType = !isObjectContentType && MediaTypes.isCdmiQueue(mediaType)

    def handleContainerCdmiCall(): Future[Response] =
      method match {
        case Method.Get if OPTIONAL(isContainerAccept || isAnyAccept || !haveAccept) ⇒
          GET_container_cdmi(request, containerPath)

        case Method.Get if HELPER(Accept.isCdmiLike(request)) ⇒
          badRequest(
            request,
            StdErrorRef.BR017,
            s"Bad use of '${HeaderNames.Accept}: $hAccept' with the '${HeaderNames.X_CDMI_Specification_Version}' present" +
            s". Should be '${HeaderNames.Accept}: ${MediaTypes.Application_CdmiContainer}'"
          )

        case Method.Get /*if HELPER(haveAccept) */⇒
          badRequest(
            request,
            StdErrorRef.BR018,
            s"Bad use of '${HeaderNames.Accept}: $hAccept' with the '${HeaderNames.X_CDMI_Specification_Version}' present" +
            s". Should be '${HeaderNames.Accept}: ${MediaTypes.Application_CdmiContainer}'"
          )

        case Method.Put if MANDATORY(isContainerContentType) && OPTIONAL(isContainerAccept || isAnyAccept) ⇒
          // Section 9.2 Create a Container Object using CDMI Content Type
          PUT_container_cdmi_create(request, containerPath)

        case Method.Put if MANDATORY(isContainerContentType) && OPTIONAL(!haveAccept) ⇒
          // Section 9.2 Create a Container Object using CDMI Content Type
          // Section 9.5 Update a Container Object using CDMI Content Type
          PUT_container_cdmi_create_or_update(request, containerPath)

        case Method.Put if MediaTypes.isCdmiLike(mediaType) ⇒
          badRequest(
            request,
            StdErrorRef.BR019,
            s"Bad use of '${HeaderNames.Content_Type}: $hContentType' with the '${HeaderNames.X_CDMI_Specification_Version}' present" +
            s". Should be '${HeaderNames.Content_Type}: ${MediaTypes.Application_CdmiContainer}'"
          )

        case Method.Put if MANDATORY(haveContentType) ⇒
          // This is an irrelevant content type
          badRequest(
            request,
            StdErrorRef.BR020,
            s"Bad use of '${HeaderNames.Content_Type}: $hContentType' with the '${HeaderNames.X_CDMI_Specification_Version}' present"
          )

        case Method.Put ⇒
          badRequest(
            request,
            StdErrorRef.BR021,
            s"'${HeaderNames.Content_Type}' is not set"
          )

        case Method.Post if MANDATORY(isObjectContentType) && OPTIONAL(isObjectAccept || isAnyAccept || !haveAccept) ⇒
          // Section 9.8 Create (POST) a New Data Object using CDMI Content Type
          POST_object_to_container_cdmi(request, containerPath)

        case Method.Post if MANDATORY(isQueueContentType) && OPTIONAL(isQueueAccept || isAnyAccept || !haveAccept) ⇒
          // Section 9.10 Create (POST) a New Queue Object using CDMI Content Type
          POST_queue_to_container_cdmi(request, containerPath)

        case Method.Post ⇒
          // I could refine the errors more, just like I did with other cases but this manual procedure is getting
          // tedious, it already is error prone and certainly is the wrong way to implement a specification, as far
          // as I am concerned.
          // We really need to derive an implementation automatically from the specification.
          NotAllowed()

        case Method.Delete ⇒
          // Section 9.6 Delete a Container Object using CDMI Content Type
          DELETE_container_cdmi(request, containerPath)

        case _ ⇒
          NotAllowed()
      }

    def handleContainerNonCdmiCall(): Future[Response] =
      method match {
        case Method.Get ⇒
          NotAllowed()
        case Method.Put ⇒
          NotAllowed()
        case Method.Post ⇒
          NotAllowed()
        case Method.Delete ⇒
          NotAllowed()
        case _ ⇒
          NotAllowed()
      }

    haveSpecVersion match {
      case true  ⇒
        log.debug("handleContainerCdmiCall")
        handleContainerCdmiCall()

      case false ⇒
        log.debug("handleContainerNonCdmiCall")
        handleContainerNonCdmiCall()
    }
  }

  def handleObjectOrQueueCall(request: Request, pathList: List[String]): Future[Response] = {
    def NotAllowed() = notAllowed(request)
    val headers = request.headers()
    val method = request.method

    val hSpecVersion = headers.get(HeaderNames.X_CDMI_Specification_Version)
    val hContentType = headers.get(HeaderNames.Content_Type)
    val mediaType = request.mediaType.orNull // Content-Type without any baggage
    val hAccept = headers.get(HeaderNames.Accept)

    val haveContentType = hContentType ne null
    val haveSpecVersion = hSpecVersion ne null
    val haveAccept = hAccept ne null

    val isQueueContentType = MediaTypes.isCdmiQueue(mediaType)
    val isObjectContentType = !isQueueContentType && MediaTypes.isCdmiObject(mediaType)
    val isQueueAccept = Accept.isCdmiQueue(request)
    val isObjectAccept = !isQueueAccept && Accept.isCdmiObject(request)
    val isAnyAccept = !isQueueAccept && !isObjectAccept && Accept.isAny(request)

    // Separation of queues and data objects is not well defined
    // and this is an approximate solution/approach.
    //
    // In particular:
    //  - Section 11.2 of CDMI 1.0.2 "Create a Queue Object using CDMI Content Type"
    //      requires Content-Type: application/cdmi-queue and
    //      requires the presence of X-CDMI-Specification-Version header
    //  - Section 11.3 of CDMI 1.0.2 "Read a Queue Object using CDMI Content Type"
    //      has an optional Accept header and
    //      requires the presence of X-CDMI-Specification-Version header
    //  - Section 11.4 of CDMI 1.0.2 "Update a Queue Object using CDMI Content Type"
    //      requires Content-Type: application/cdmi-queue and
    //      requires the presence of X-CDMI-Specification-Version header
    //  - Section 11.5 of CDMI 1.0.2 "Delete a Queue Object using CDMI Content Type"
    //      requires the presence of X-CDMI-Specification-Version header
    //  - Section 11.6 of CDMI 1.0.2 "Enqueue a New Queue Value using CDMI Content Type"
    //      requires Content-Type: application/cdmi-queue and
    //      requires the presence of X-CDMI-Specification-Version header
    //  - Section 11.7 of CDMI 1.0.2 "Delete a Queue Object Value using CDMI Content Type"
    //      requires the presence of X-CDMI-Specification-Version header
    //
    //
    // The common requirement for queue operations is the presence of X-CDMI-Specification-Version
    // header and this is what we check first:

    // Handles all cases when the header X-CDMI-Specification-Version is present
    // That the version is valid must be guaranteed by the filters run before we reach here.
    // This is true with the default setup.
    def handleObjectOrQueueCdmiCall(): Future[Response] = {
      method match {
        //+ GET //////////////////////////////////////////////////////////////
        case Method.Get if OPTIONAL(isQueueAccept) ⇒
          // Section 11.3 Read a Queue Object using CDMI Content Type
          GET_queue_cdmi(request, pathList)

        case Method.Get if OPTIONAL(isObjectAccept || isAnyAccept) || HELPER(!haveAccept) ⇒
          // If `Accept` is not present or it is '*/*', we default to data objects.
          // Section 8.4 Read a Data Object using CDMI Content Type
          GET_object_cdmi(request, pathList)

        case Method.Get if HELPER(Accept.isCdmiLike(request)) ⇒
          badRequest(
            request,
            StdErrorRef.BR006,
            s"Bad use of '${HeaderNames.Accept}: $hAccept' with the '${HeaderNames.X_CDMI_Specification_Version}' present" +
              s". Should be '${HeaderNames.Accept}: ${MediaTypes.Application_CdmiObject}'" +
              s" or '${HeaderNames.Accept}: ${MediaTypes.Application_CdmiQueue}'"
          )

        case Method.Get if HELPER(haveAccept) ⇒
          // There must be an irrelevant media type in `Accept`
          badRequest(
            request,
            StdErrorRef.BR004,
            s"Bad use of '${HeaderNames.Accept}: $hAccept' with the '${HeaderNames.X_CDMI_Specification_Version}' present"
          )
        //- GET //////////////////////////////////////////////////////////////

        //+ PUT //////////////////////////////////////////////////////////////
        case Method.Put if MANDATORY(isQueueContentType) && MANDATORY(isQueueAccept) ⇒
          // Section 11.2 Create a Queue Object using CDMI Content Type
          PUT_queue_cdmi_create(request, pathList)

        case Method.Put if MANDATORY(isQueueContentType) ⇒
          // Section 11.4 Update a Queue Object using CDMI Content Type
          PUT_queue_cdmi_update(request, pathList)

        case Method.Put if MANDATORY(isObjectContentType) && OPTIONAL(isObjectAccept || isAnyAccept) ⇒
          // Section 8.2 Create a Data Object Using CDMI Content Type
          PUT_object_cdmi_create(request, pathList)

        case Method.Put if MANDATORY(isObjectContentType) ⇒
          // Section 8.2 Create a Data Object Using CDMI Content Type
          // Section 8.6 Update a Data Object using CDMI Content Type
          PUT_object_cdmi_create_or_update(request, pathList)

        case Method.Put if MANDATORY(MediaTypes.isCdmiLike(mediaType)) ⇒
          badRequest(
            request,
            StdErrorRef.BR006,
            s"Bad use of '${HeaderNames.Content_Type}: $hContentType' with the '${HeaderNames.X_CDMI_Specification_Version}' present" +
              s". Should be '${HeaderNames.Content_Type}: ${MediaTypes.Application_CdmiObject}'" +
              s" or '${HeaderNames.Content_Type}: ${MediaTypes.Application_CdmiQueue}'"
          )

        case Method.Put if MANDATORY(haveContentType) ⇒
          // This is an irrelevant content type
          badRequest(
            request,
            StdErrorRef.BR016,
            s"Bad use of '${HeaderNames.Content_Type}: $hContentType' with the '${HeaderNames.X_CDMI_Specification_Version}' present"
          )

        case Method.Put ⇒
          badRequest(
            request,
            StdErrorRef.BR005,
            s"'${HeaderNames.Content_Type}' is not set"
          )
        //- PUT //////////////////////////////////////////////////////////////

        //+ POST /////////////////////////////////////////////////////////////
        case Method.Post if isQueueContentType ⇒
          // Section 11.6 Enqueue a New Queue Value using CDMI Content Type
          POST_queue_value_cdmi(request, pathList)

        case Method.Post ⇒
          NotAllowed()

        //- POST /////////////////////////////////////////////////////////////

        //+ DELETE ///////////////////////////////////////////////////////////
        case Method.Delete ⇒
          // No other way to understand if this is about a data object or a queue object or a queue object value
          // Section  8.8 Delete a Data Object using CDMI Content Type
          // Section 11.5 Delete a Queue Object using CDMI Content Type
          // Section 11.7 Delete a Queue Object Value using CDMI Content Type
          DELETE_object_or_queue_or_queuevalue_cdmi(request, pathList)

        //- DELETE ///////////////////////////////////////////////////////////

        case _ ⇒
          NotAllowed()
      }
    }

    // Handles all cases when the header X-CDMI-Specification-Version is absent.
    def handleObjectOrQueueNonCdmiCall(): Future[Response] = {
      method match {
        //+ GET //////////////////////////////////////////////////////////////
        case Method.Get if HELPER(isAnyAccept) || HELPER(!haveAccept) ⇒
          // Section 8.5 Read a Data Object using a Non-CDMI Content Type
          GET_object_noncdmi(request, pathList)

        case Method.Get if HELPER(Accept.isCdmiLike(request)) ⇒
          badRequest(
            request,
            StdErrorRef.BR013,
            s"Bad use of CDMI-aware '${HeaderNames.Accept}: $hAccept' without the presence of '${HeaderNames.X_CDMI_Specification_Version}'"
          )

        case Method.Get ⇒
          // Let the implementation handle any other value for the `Accept` header.
          // Section 8.5 Read a Data Object using a Non-CDMI Content Type
          GET_object_noncdmi(request, pathList)
        //- GET //////////////////////////////////////////////////////////////

        //+ PUT //////////////////////////////////////////////////////////////
        case Method.Put if MANDATORY(haveContentType) && MANDATORY(MediaTypes.isCdmiLike(mediaType)) ⇒
          badRequest(
            request,
            StdErrorRef.BR014,
            s"Bad use of CDMI-aware '${HeaderNames.Content_Type}: $hContentType' without the presence of '${HeaderNames.X_CDMI_Specification_Version}'"
          )

        case Method.Put if MANDATORY(haveContentType) ⇒
          // Section 8.3 Create a Data Object using a Non-CDMI Content Type
          // Section 8.7 Update a Data Object using a Non-CDMI Content Type
          PUT_object_noncdmi(request, pathList, hContentType)

        case Method.Put ⇒
          badRequest(
            request,
            StdErrorRef.BR015,
            s"'${HeaderNames.Content_Type}' is not set"
          )
        //- PUT //////////////////////////////////////////////////////////////

        //+ POST /////////////////////////////////////////////////////////////
        case Method.Post ⇒
          NotAllowed()
        //- POST /////////////////////////////////////////////////////////////

        //+ DELETE ///////////////////////////////////////////////////////////
        case Method.Delete ⇒
          // Section 8.9 Delete a Data Object using a Non-CDMI Content Type
          DELETE_object_noncdmi(request, pathList)
        //+ DELETE ///////////////////////////////////////////////////////////

        case _ ⇒
          NotAllowed()
      }
    }

    haveSpecVersion match {
      case true  ⇒
        log.debug("handleObjectOrQueueCdmiCall")
        handleObjectOrQueueCdmiCall()

      case false ⇒
        log.debug("handleObjectOrQueueNonCdmiCall")
        handleObjectOrQueueNonCdmiCall()
    }
  }

  def handleDomainCall(request: Request, domainPath: List[String]): Future[Response] = {
    def NotAllowed() = notAllowed(request)
    val method = request.method

    method match {
      case Method.Get    ⇒ GET_domain_cdmi(request, domainPath)
      case Method.Put    ⇒ PUT_domain_cdmi(request, domainPath)
      case Method.Delete ⇒ DELETE_domain_cdmi(request, domainPath)
      case _             ⇒ NotAllowed()
    }
  }

  def handleDomainNoSlashCall(request: Request): Future[Response] =
    badRequest(
      request,
      StdErrorRef.BR011,
      "Probably you meant to call /cdmi_domains/ instead of /cdmi_domains"
    )

  def handleRootCall(request: Request): Future[Response] = notAllowed(request)

  def handleRootNoSlashCall(request: Request): Future[Response] = notAllowed(request)

  def handleCapabilitiesCall(request: Request): Future[Response] = {
    def NotAllowed() = notAllowed(request)
    val method = request.method

    method match {
      case Method.Get ⇒ GET_capabilities(request)
      case _          ⇒ NotAllowed()
    }
  }

  def handleCapabilitiesNoSlashCall(request: Request): Future[Response] =
    badRequest(
      request,
      StdErrorRef.BR012,
      "Probably you meant to call /cdmi_capabilities/ instead of /cdmi_capabilities"
    )

  def handleObjectByIdCall(request: Request, objectIdPath: List[String]): Future[Response] = {
    def NotAllowed() = notAllowed(request)
    val method = request.method

    method match {
      case Method.Get ⇒ GET_objectById(request, objectIdPath)
      case Method.Post ⇒ POST_objectById(request, objectIdPath)
      case Method.Put ⇒ PUT_objectById(request, objectIdPath)
      case _ ⇒ NotAllowed()
    }
  }

}
