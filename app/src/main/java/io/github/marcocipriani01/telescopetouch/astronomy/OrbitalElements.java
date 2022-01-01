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

import static java.lang.Math.abs;

import android.util.Log;

import androidx.annotation.NonNull;

import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.maths.MathsUtils;

/**
 * This class wraps the six parameters which define the path an object takes as
 * it orbits the sun.
 * <p>
 * The equations come from JPL's Solar System Dynamics site:
 * http://ssd.jpl.nasa.gov/?planet_pos
 * <p>
 * The original source for the calculations is based on the approximations described in:
 * Van Flandern T. C., Pulkkinen, K. F. (1979): "Low-Precision Formulae for
 * Planetary Positions", 1979, Astrophysical Journal Supplement Series, Vol. 41,
 * pp. 391-411.
 *
 * @author Kevin Serafini
 * @author Brent Bryan
 */
public class OrbitalElements {

    // calculation error
    private final static double EPSILON = 1.0e-6f;
    private static final String TAG = TelescopeTouchApp.getTag(OrbitalElements.class);
    public final double distance;       // Mean distance (AU)
    public final double eccentricity;   // Eccentricity of orbit
    public final double inclination;    // Inclination of orbit (AngleUtils.RADIANS)
    public final double ascendingNode;  // Longitude of ascending node (AngleUtils.RADIANS)
    public final double perihelion;     // Longitude of perihelion (AngleUtils.RADIANS)
    public final double meanLongitude;  // Mean longitude (AngleUtils.RADIANS)

    public OrbitalElements(double d, double e, double i, double a, double p, double l) {
        this.distance = d;
        this.eccentricity = e;
        this.inclination = i;
        this.ascendingNode = a;
        this.perihelion = p;
        this.meanLongitude = l;
    }

    // compute the true anomaly from mean anomaly using iteration
    // m - mean anomaly in radians
    // e - orbit eccentricity
    // Return value is in radians.
    private static double calculateTrueAnomaly(double m, double e) {
        // initial approximation of eccentric anomaly
        double e0 = m + e * Math.sin(m) * (1.0f + e * Math.cos(m));
        double e1;

        // iterate to improve accuracy
        int counter = 0;
        do {
            e1 = e0;
            e0 = e1 - (e1 - e * Math.sin(e1) - m) / (1.0f - e * Math.cos(e1));
            if (counter++ > 100) {
                Log.d(TAG, "Failed to converge! Exiting.");
                Log.d(TAG, "e1 = " + e1 + ", e0 = " + e0);
                Log.d(TAG, "diff = " + abs(e0 - e1));
                break;
            }
        } while (abs(e0 - e1) > EPSILON);

        // convert eccentric anomaly to true anomaly
        double v = 2f * Math.atan(Math.sqrt((1 + e) / (1 - e)) * (Math.sin(0.5f * e0) / Math.cos(0.5f * e0)));
        return MathsUtils.mod2pi(v);
    }

    public double getAnomaly() {
        return calculateTrueAnomaly(meanLongitude - perihelion, eccentricity);
    }

    @NonNull
    @Override
    public String toString() {
        return "Mean Distance: " + distance + " (AU)\n" +
                "Eccentricity: " + eccentricity + "\n" +
                "Inclination: " + inclination + " (AngleUtils.RADIANS)\n" +
                "Ascending Node: " + ascendingNode + " (AngleUtils.RADIANS)\n" +
                "Perihelion: " + perihelion + " (AngleUtils.RADIANS)\n" +
                "Mean Longitude: " + meanLongitude + " (AngleUtils.RADIANS)\n";
    }
}