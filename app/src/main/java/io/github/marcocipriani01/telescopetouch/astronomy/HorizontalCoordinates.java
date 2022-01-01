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

package io.github.marcocipriani01.telescopetouch.astronomy;

import static java.lang.Math.asin;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import android.location.Location;

import androidx.annotation.NonNull;

import java.util.Calendar;

import io.github.marcocipriani01.telescopetouch.maths.Formatters;

public class HorizontalCoordinates {

    public double alt;
    public double az;

    public HorizontalCoordinates(double alt, double az) {
        this.alt = alt;
        this.az = az;
    }

    public static HorizontalCoordinates getInstance(GeocentricCoordinates coord, Location observer, Calendar time) {
        return getInstance(EquatorialCoordinates.getInstance(coord), observer.getLatitude(), TimeUtils.meanSiderealTime(time, observer.getLongitude()));
    }

    public static HorizontalCoordinates getInstance(EquatorialCoordinates eq, Location observer, Calendar time) {
        return getInstance(eq, observer.getLatitude(), TimeUtils.meanSiderealTime(time, observer.getLongitude()));
    }

    public static boolean isObjectAboveHorizon(EquatorialCoordinates eq, double latitude, double siderealTime) {
        double latRadians = toRadians(latitude),
                dec = toRadians(eq.dec),
                alt = asin(sin(dec) * sin(latRadians) + cos(dec) * cos(latRadians) * cos(toRadians(siderealTime - eq.ra)));
        return alt > 0.0;
    }

    public static HorizontalCoordinates getInstance(EquatorialCoordinates eq, double latitude, double siderealTime) {
        double latRadians = toRadians(latitude),
                dec = toRadians(eq.dec),
                ha = toRadians(siderealTime - eq.ra),
                alt = asin(sin(dec) * sin(latRadians) + cos(dec) * cos(latRadians) * cos(ha));
        return new HorizontalCoordinates(toDegrees(alt),
                toDegrees(atan2(-sin(ha) * cos(dec) / cos(alt), (sin(dec) - sin(latRadians) * sin(alt)) / (cos(latRadians) * cos(alt)))));
    }

    public String getAltString() {
        return Formatters.formatDegrees(alt);
    }

    public String getAzString() {
        return Formatters.formatDegrees(getNormalizedAz());
    }

    public double getNormalizedAz() {
        return (az + 360.0) % 360.0;
    }

    @NonNull
    @Override
    public String toString() {
        return "Alt: " + getAltString() + ", Az: " + getAzString();
    }

    public String toStringArcmin() {
        return "Alt: " + Formatters.formatDegreesArcmin(alt) + ", Az: " + Formatters.formatDegreesArcmin(getNormalizedAz());
    }
}