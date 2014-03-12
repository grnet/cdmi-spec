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
 * All known (to us) cdmi capabilities.
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
public enum SystemWideCapability implements ICapability {
    // CDMI/v1.0.2/12.1.1
    cdmi_domains,
    cdmi_export_cifs,
    cdmi_dataobjects,
    cdmi_export_iscsi,
    cdmi_export_nfs,
    cdmi_export_occi_iscsi,
    cdmi_export_webdav,
    cdmi_metadata_maxitems,
    cdmi_metadata_maxsize,
    cdmi_metadata_maxtotalsize,
    cdmi_notification,
    cdmi_logging,
    cdmi_query,
    cdmi_query_regex,
    cdmi_query_contains,
    cdmi_query_tags,
    cdmi_query_value,
    cdmi_queues,
    cdmi_security_access_control,
    cdmi_security_audit,
    cdmi_security_data_integrity,
    cdmi_security_encryption,
    cdmi_security_immutability,
    cdmi_security_sanitization,
    cdmi_serialization_json,
    cdmi_snapshots,
    cdmi_references,
    cdmi_object_move_from_local,
    cdmi_object_move_from_remote,
    cdmi_object_move_from_ID,
    cdmi_object_move_to_ID,
    cdmi_object_copy_from_local,
    cdmi_object_copy_from_remote,
    cdmi_object_access_by_ID,
    cdmi_post_dataobject_by_ID,
    cdmi_post_queue_by_ID,
    cdmi_deserialize_dataobject_by_ID,
    cdmi_deserialize_queue_by_ID,
    cdmi_serialize_dataobject_to_ID,
    cdmi_serialize_domain_to_ID,
    cdmi_serialize_container_to_ID,
    cdmi_serialize_queue_to_ID,
    cdmi_copy_dataobject_by_ID,
    cdmi_copy_queue_by_ID,
    cdmi_create_reference_by_ID,
}
