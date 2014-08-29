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

import gr.grnet.cdmi.capability.{ICapability, ContainerCapability}
import gr.grnet.cdmi.http.CdmiMediaType

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
case class CapabilityModel(
  objectType: String,
  objectID: String,
  objectName: String,
  parentURI: String,
  parentID: String,
  capabilities: Map[ICapability, String],
  childrenRange: String,
  children: List[String]
)

object CapabilityModel {
  def booleanCapabilitiesMap(caps: ICapability*): Map[ICapability, String] =
    (for(cap ← caps) yield (cap, true.toString)).toMap

  def rootOf(
    children: List[String] = Nil,
    parentID: String = "",
    parentURI: String = "/",
    objectID: String = "",
    objectName: String = "cdmi_capabilities/"
  ) =
    CapabilityModel(
      objectType = CdmiMediaType.Application_CdmiCapability.value(),
      objectID = objectID,
      objectName = objectName,
      parentURI = parentURI,
      parentID = parentID,
      capabilities = booleanCapabilitiesMap(
        ContainerCapability.cdmi_list_children,
        ContainerCapability.cdmi_create_container,
        ContainerCapability.cdmi_delete_container
      ),
      childrenRange = if(children.size == 0) "0-0" else s"0-${children.size - 1}",
      children = children
    )
}
