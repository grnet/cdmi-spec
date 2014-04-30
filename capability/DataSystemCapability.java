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
public enum DataSystemCapability implements ICapability {
    // CDMI/v1.0.2/12.1.3
    cdmi_assignedsize,
    cdmi_data_redundancy,
    cdmi_data_dispersion,
    cdmi_data_retention,
    cdmi_data_autodelete,
    cdmi_data_holds,
    cdmi_encryption,
    cdmi_geographic_placement,
    cdmi_immediate_redundancy,
    cdmi_infrastructure_redundancy,
    cdmi_latency,
    cdmi_RPO,
    cdmi_RTO,
    cdmi_sanitization_method,
    cdmi_throughput,
    cdmi_value_hash,
}
