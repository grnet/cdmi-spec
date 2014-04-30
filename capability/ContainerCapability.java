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
