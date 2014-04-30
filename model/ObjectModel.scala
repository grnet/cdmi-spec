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

package gr.grnet.cdmi.model

import gr.grnet.cdmi.http.CdmiContentType

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
case class ObjectModel(
  objectType: String = CdmiContentType.Application_CdmiObject.contentType(),
  objectID: String,
  objectName: String,
  parentURI: String,
  parentID: String,
  domainURI: String,
  capabilitiesURI: String = "/cdmi_capabilities/dataobject/",
  completionStatus: String = "Complete",
  mimetype: String,
  metadata: Map[String, String], // e.g. cdmi_size
  valuetransferencoding: String, // "utf-8" or "base64"
  valuerange: String,
  value: String
)
