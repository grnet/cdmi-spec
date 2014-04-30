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
public enum DomainCapability implements ICapability {
    // CDMI/v1.0.2/12.1.6
    cdmi_create_domain,
    cdmi_delete_domain,
    cdmi_domain_summary,
    cdmi_domain_members,
    cdmi_list_children,
    cdmi_read_metadata,
    cdmi_modify_metadata,
    cdmi_modify_deserialize_domain,
    cdmi_copy_domain,
    cdmi_deserialize_domain,
}
