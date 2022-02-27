/*
 * Copyright 2021 Marco Cipriani (@marcocipriani01)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.marcocipriani01.telescopetouch.control;

import android.location.Location;

import androidx.annotation.NonNull;

/**
 * A trivial calculator that returns zero magnetic declination.  Used when
 * the user does not want magnetic correction.
 *
 * @author John Taylor
 */
public final class ZeroMagneticDeclinationCalculator implements MagneticDeclinationCalculator {

    @Override
    public float getDeclination() {
        return 0;
    }

    @Override
    public void setLocationAndTime(Location location, long timeInMills) {
        // Do nothing.
    }

    @NonNull
    @Override
    public String toString() {
        return "Zero Magnetic Correction";
    }
}