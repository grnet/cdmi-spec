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

package gr.grnet.cdmi.capability;

/**
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
public enum ContainerCapability implements ICapability {
    // CDMI/v1.0.2/12.1.5
    cdmi_list_children,
    cdmi_list_children_range,
    cdmi_read_metadata,
    cdmi_modify_metadata,
    cdmi_modify_deserialize_container,
    cdmi_snapshot,
    cdmi_serialize_dataobject,
    cdmi_serialize_container,
    cdmi_serialize_queue,
    cdmi_serialize_domain,
    cdmi_deserialize_container,
    cdmi_deserialize_queue,
    cdmi_deserialize_dataobject,
    cdmi_create_dataobject,
    cdmi_post_dataobject,
    cdmi_post_queue,
    cdmi_create_container,
    cdmi_create_queue,
    cdmi_create_reference,
    cdmi_export_container_cifs,
    cdmi_export_container_nfs,
    cdmi_export_container_iscsi,
    cdmi_export_container_occi,
    cdmi_export_container_webdav,
    cdmi_delete_container,
    cdmi_move_container,
    cdmi_copy_container,
    cdmi_move_dataobject,
    cdmi_copy_dataobject,
}
