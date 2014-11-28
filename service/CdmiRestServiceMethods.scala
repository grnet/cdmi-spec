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

import com.twitter.finagle.http.Status
import com.twitter.util.Future
import gr.grnet.cdmi.http.CdmiMediaType
import gr.grnet.common.json.Json

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
trait CdmiRestServiceMethods { self: CdmiRestService with CdmiRestServiceTypes with CdmiRestServiceResponse ⇒
  /**
   * Return the capabilities of this CDMI implementation.
   */
  def GET_capabilities(request: Request): Future[Response] = {
    val caps = systemWideCapabilities
    val jsonCaps = Json.objectToJsonString(caps)

    response(request, Status.Ok, CdmiMediaType.Application_CdmiCapability, jsonCaps).future
  }

  def GET_objectById(request: Request, objectIdPath: List[String]): Future[Response] =
    notImplemented(request)

  def POST_objectById(request: Request, objectIdPath: List[String]): Future[Response] =
    notImplemented(request)

  def PUT_objectById(request: Request, objectIdPath: List[String]): Future[Response] =
    notImplemented(request)

  /////////////////////////////////////////////////////////////
  //+ Create/Update a data object /////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * Creates/updates a data object in a container using CDMI content type.
   *
   * @note Section 8.2 of CDMI 1.0.2: Create a Data Object Using CDMI Content Type
   * @note Section 8.6 of CDMI 1.0.2: Update a Data Object using CDMI Content Type
   */
  def PUT_object_cdmi_create_or_update(request: Request, objectPath: List[String]): Future[Response] =
    notImplemented(request)
  /**
   * Creates a data object in a container using CDMI content type.
   *
   * @note Section 8.2 of CDMI 1.0.2: Create a Data Object Using CDMI Content Type
   */
  def PUT_object_cdmi_create(request: Request, objectPath: List[String]): Future[Response] =
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
   *
   * @note Section 8.9 of CDMI 1.0.2: Delete a Data Object using a Non-CDMI Content Type
   */
  def DELETE_object_noncdmi(request: Request, objectPath: List[String]): Future[Response] =
    notImplemented(request)
  /////////////////////////////////////////////////////////////
  //- Delete a data object ////////////////////////////////////
  /////////////////////////////////////////////////////////////


  /////////////////////////////////////////////////////////////
  //+ Create/Update a container  //////////////////////////////
  /////////////////////////////////////////////////////////////
  /**
   * Creates a container using CDMI content type.
   *
   * @note Section 9.2 of CDMI 1.0.2: Create a Container Object using CDMI Content Type
   */
  def PUT_container_cdmi_create(request: Request, containerPath: List[String]): Future[Response] =
    notImplemented(request)

  /**
   * Creates/updates a container using CDMI content type.
   *
   * @note Section 9.2 of CDMI 1.0.2: Create a Container Object using CDMI Content Type
   * @note Section 9.5 of CDMI 1.0.2: Update a Container Object using CDMI Content Type
   */
  def PUT_container_cdmi_create_or_update(request: Request, containerPath: List[String]): Future[Response] =
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
  /////////////////////////////////////////////////////////////
  //- Create/Update a container  //////////////////////////////
  /////////////////////////////////////////////////////////////


  /////////////////////////////////////////////////////////////
  //+ POST object/queue to container  /////////////////////////
  /////////////////////////////////////////////////////////////
  def POST_object_to_container_cdmi(request: Request, containerPath: List[String]): Future[Response] =
    notImplemented(request)

  def POST_queue_to_container_cdmi(request: Request, containerPath: List[String]): Future[Response] =
    notImplemented(request)
  /////////////////////////////////////////////////////////////
  //- POST object/queue to container  /////////////////////////
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
    notImplemented(request)
  /////////////////////////////////////////////////////////////
  //- Delete a domain  ////////////////////////////////////////
  /////////////////////////////////////////////////////////////

  /////////////////////////////////////////////////////////////
  //+ Queue object and queue object values operations /////////
  /////////////////////////////////////////////////////////////
  def GET_queue_cdmi(request: Request, queuePath: List[String]): Future[Response] =
    notImplemented(request)

  def POST_queue_value_cdmi(request: Request, queuePath: List[String]): Future[Response] =
    notImplemented(request)

  def PUT_queue_cdmi_create(request: Request, queuePath: List[String]): Future[Response] =
    notImplemented(request)

  def PUT_queue_cdmi_update(request: Request, queuePath: List[String]): Future[Response] =
    notImplemented(request)

  def DELETE_queue(request: Request, queuePath: List[String]): Future[Response] =
    notImplemented(request)
  /////////////////////////////////////////////////////////////
  //- Queue object and queue object values operations /////////
  /////////////////////////////////////////////////////////////

  /**
   * An implementations that provides both data objects and queue objects must be able to detect
   * the intended semantics. This one of a) delete a data object, b) delete a queue object and
   * c) delete a queue object value.
   *
   * This is deliberately left as not implemented here.
   *
   * @note The relevant sections from CDMI 1.0.2 are 8.8, 11.5 and 11.7.
   */
  def DELETE_object_or_queue_or_queuevalue_cdmi(request: Request, path: List[String]): Future[Response]
}
