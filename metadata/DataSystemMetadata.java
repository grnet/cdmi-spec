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
