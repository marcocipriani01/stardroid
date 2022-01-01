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

import static io.github.marcocipriani01.telescopetouch.astronomy.TimeUtils.meanSiderealTime;
import static io.github.marcocipriani01.telescopetouch.maths.Formatters.formatDegreesAsHours;
import static io.github.marcocipriani01.telescopetouch.maths.Formatters.formatHours;

import java.util.Calendar;

import io.github.marcocipriani01.telescopetouch.R;

public class Polaris {

    public static final double POLARIS_J2000_RA = 37.954560666666666;    // Might be 30.5303040444 (?)
    public static final double POLARIS_J2000_DEC = 89.26410897222;
    public static final double SIG_OCT_J2000_RA = 315.14634539559486;
    public static final double SIG_OCT_J2000_DEC = -88.956503248687222;
    private boolean locationValid = false;
    private boolean autoHemisphereDetection = true;
    private double latitude;
    private double longitude;
    private boolean northernHemisphere;
    private double hourAngle = 0.0;
    private double scopePosition;

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setNorthernHemisphere(boolean northernHemisphere) {
        this.northernHemisphere = northernHemisphere;
    }

    public void setAutoHemisphereDetection(boolean autoHemisphereDetection) {
        this.autoHemisphereDetection = autoHemisphereDetection;
    }

    public String getHourAngleString() {
        return formatDegreesAsHours(this.hourAngle);
    }

    public float getScopePosition() {
        return (float) this.scopePosition;
    }

    public String getScopePositionString() {
        return formatHours(this.scopePosition / 30.0);
    }

    public int getStarName() {
        return this.northernHemisphere ? R.string.polaris_star_finder : R.string.sigma_octantis;
    }

    public void refresh() {
        if (this.locationValid) {
            if (this.autoHemisphereDetection)
                northernHemisphere = (this.latitude >= 0.0);
            Calendar calendar = Calendar.getInstance();
            double rightAscension;
            if (northernHemisphere) {
                rightAscension = StarsPrecession.precess(calendar, POLARIS_J2000_RA, POLARIS_J2000_DEC).ra;
            } else {
                rightAscension = StarsPrecession.precess(calendar, SIG_OCT_J2000_RA, SIG_OCT_J2000_DEC).ra;
            }
            double siderealTime = meanSiderealTime(calendar, longitude);
            if (siderealTime > rightAscension) {
                this.hourAngle = siderealTime - rightAscension;
            } else {
                this.hourAngle = (siderealTime + 360.0d) - rightAscension;
            }
            if (northernHemisphere) {
                this.scopePosition = ((360.0 - this.hourAngle) + 180.0) % 360.0;
            } else {
                this.scopePosition = (this.hourAngle + 180.0) % 360.0;
            }
        } else {
            this.hourAngle = 0.0d;
        }
    }

    public void setLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        locationValid = true;
        refresh();
    }
}