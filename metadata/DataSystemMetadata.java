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

package gr.grnet.cdmi.metadata;

/**
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
public enum DataSystemMetadata {
    // CDMI/v1.0.2/16.4
    cdmi_data_redundancy,
    cdmi_immediate_redundancy,
    cdmi_assignedsize,
    cdmi_infrastructure_redundancy,
    cdmi_data_dispersion,
    cdmi_geographic_placement,
    cdmi_retention_id,
    cdmi_retention_period,
    cdmi_retention_autodelete,
    cdmi_hold_id,
    cdmi_encryption,
    cdmi_value_hash,
    cdmi_latency,
    cdmi_throughput,
    cdmi_sanitization_method,
    cdmi_RPO,
    cdmi_RTO,

    // CDMI/v1.0.2/16.5
    cdmi_data_redundancy_provided,
    cdmi_immediate_redundancy_provided,
    cdmi_infrastructure_redundancy_provided,
    cdmi_data_dispersion_provided,
    cdmi_geographic_placement_provided,
    cdmi_retention_period_provided,
    cdmi_retention_autodelete_provided,
    cdmi_hold_id_provided,
    cdmi_encryption_provided,
    cdmi_value_hash_provided,
    cdmi_latency_provided,
    cdmi_throughput_provided,
    cdmi_sanitization_method_provided,
    cdmi_RPO_provided,
    cdmi_RTO_provided,
}
