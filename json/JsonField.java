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

package gr.grnet.cdmi.json;

/**
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
public enum JsonField {
    mimetype("mimetype"),   // 8.2.5/Table 8
    metadata("metadata"),   // 8.2.5/Table 8
    domainURI("domainURI"), // 8.2.5/Table 8
    deserialize("deserialize"), // 8.2.5/Table 8
    serialize("serialize"), // 8.2.5/Table 8
    copy("copy"), // 8.2.5/Table 8
    move("move"), // 8.2.5/Table 8
    reference("reference"), // 8.2.5/Table 8
    deserializevalue("deserializevalue"), // 8.2.5/Table 8
    valuetransferencoding("valuetransferencoding"), // 8.2.5/Table 8
    value("value"), // 8.2.5/Table 8

    objectType("objectType"), // 8.2.7/Table 10
    objectID("objectID"), // 8.2.7/Table 10
    objectName("objectName"), // 8.2.7/Table 10
    parentURI("parentURI"), // 8.2.7/Table 10
    parentID("parentID"), // 8.2.7/Table 10
    capabilitiesURI("capabilitiesURI"), // 8.2.7/Table 10
    completionStatus("completionStatus"), // 8.2.7/Table 10, "Processing", "Completed", "Error"
    percentComplete("percentComplete"), // 8.2.7/Table 10

    valuerange("valuerange"); // 8.4.6/Table 16



    public final String jsonField;

    JsonField(String jsonField) {
        this.jsonField = jsonField;
    }
}
