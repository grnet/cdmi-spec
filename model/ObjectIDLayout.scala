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

package gr.grnet.cdmi.model

/**
 *
 * @author Christos KK Loverdos <loverdos@gmail.com>
 */
trait ObjectIDLayout {
  def reserved0: Byte
  def enterpriseNumber: Int // high byte is always 0
  def reserved4: Byte
  def length: Byte
  def crc: Int              // Highest 2 bytes are always zero
  def data: Array[Byte]     // 32 bytes long
}
