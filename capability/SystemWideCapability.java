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
