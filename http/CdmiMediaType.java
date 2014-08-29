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

import gr.grnet.common.http.IMediaType;

/**
 * CDMI-specific media types.
 *
 * The provided values are according to http://www.ietf.org/rfc/rfc6208.txt.
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
public enum CdmiMediaType implements IMediaType {
    Application_CdmiCapability("application/cdmi-capability"),
    Application_CdmiContainer ("application/cdmi-container"),
    Application_CdmiDomain    ("application/cdmi-domain"),
    Application_CdmiObject    ("application/cdmi-object"),
    Application_CdmiQueue     ("application/cdmi-queue");

    private final String value;

    CdmiMediaType(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
