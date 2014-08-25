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

import gr.grnet.common.http.IContentType;

/**
 * CDMI-specific content types.
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
public enum CdmiContentType implements IContentType {
    Application_CdmiCapability("application/cdmi-capability"),
    Application_CdmiContainer ("application/cdmi-container"),
    Application_CdmiDomain    ("application/cdmi-domain"),
    Application_CdmiObject    ("application/cdmi-object"),
    Application_CdmiQueue     ("application/cdmi-queue");

    private final String contentType;

    CdmiContentType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return this.contentType;
    }
}
