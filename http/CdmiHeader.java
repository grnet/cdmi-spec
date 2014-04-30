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

package gr.grnet.cdmi.http;

import gr.grnet.common.http.IHeader;

/**
* @author Christos KK Loverdos <loverdos@gmail.com>
*/
public enum CdmiHeader implements IHeader {
    X_CDMI_Specification_Version("X-CDMI-Specification-Version"), // 8.2.4
    X_CDMI_Partial("X-CDMI-Partial"); // 8.2.4

    private final String headerName;

    CdmiHeader(String headerName) {
        this.headerName = headerName;
    }


    @Override
    public String headerName() {
        return this.headerName;
    }
}
