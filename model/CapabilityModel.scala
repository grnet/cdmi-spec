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

package gr.grnet.cdmi.model

import gr.grnet.cdmi.capability.{ICapability, ContainerCapability}
import gr.grnet.cdmi.http.CdmiContentType

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
      objectType = CdmiContentType.Application_CdmiCapability.contentType(),
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
