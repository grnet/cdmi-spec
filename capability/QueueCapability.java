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
public enum QueueCapability implements ICapability {
    // CDMI/v1.0.2/12.1.7
    cdmi_read_value,
    cdmi_read_metadata,
    cdmi_modify_value,
    cdmi_modify_metadata,
    cdmi_modify_deserialize_queue,
    cdmi_delete_queue,
    cdmi_move_queue,
    cdmi_copy_queue,
    cdmi_reference_queue,
}
