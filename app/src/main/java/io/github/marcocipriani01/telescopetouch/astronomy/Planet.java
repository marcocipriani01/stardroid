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

import static io.github.marcocipriani01.telescopetouch.astronomy.EquatorialCoordinates.getInstance;

import android.content.res.Resources;
import android.location.Location;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import java.util.Calendar;
import java.util.TimeZone;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.maths.MathsUtils;

public enum Planet {
    // The order here is the order in which they are drawn. To ensure that during
    // conjunctions they display "naturally" order them in reverse distance from Earth.
    Pluto(R.drawable.pluto, R.drawable.gallery_pluto, R.string.pluto, TimeUtils.MILLISECONDS_PER_HOUR),
    Neptune(R.drawable.neptune, R.drawable.gallery_neptune, R.string.neptune, TimeUtils.MILLISECONDS_PER_HOUR),
    Uranus(R.drawable.uranus, R.drawable.gallery_uranus, R.string.uranus, TimeUtils.MILLISECONDS_PER_HOUR),
    Saturn(R.drawable.saturn, R.drawable.gallery_saturn, R.string.saturn, TimeUtils.MILLISECONDS_PER_HOUR),
    Jupiter(R.drawable.jupiter, R.drawable.gallery_jupiter, R.string.jupiter, TimeUtils.MILLISECONDS_PER_HOUR),
    Mars(R.drawable.mars, R.drawable.gallery_mars, R.string.mars, TimeUtils.MILLISECONDS_PER_HOUR),
    Sun(R.drawable.sun, R.drawable.gallery_sun, R.string.sun, TimeUtils.MILLISECONDS_PER_HOUR),
    Mercury(R.drawable.mercury, R.drawable.gallery_mercury, R.string.mercury, TimeUtils.MILLISECONDS_PER_HOUR),
    Venus(R.drawable.venus, R.drawable.gallery_venus, R.string.venus, TimeUtils.MILLISECONDS_PER_HOUR),
    Moon(R.drawable.moon4, R.drawable.moon4, R.string.moon, TimeUtils.MILLISECONDS_PER_MINUTE);

    private static final String TAG = TelescopeTouchApp.getTag(Planet.class);
    /**
     * Maximum number of times to calculate rise/set times. If we cannot
     * converge after this many iterations, we will fail.
     */
    private final static int MAX_ITERATIONS = 25;
    private final long updateFreqMs;
    /**
     * Resource ID to use for a planet's image.
     */
    private final int mapResourceId;
    private final int galleryResourceId;
    /**
     * String ID
     */
    private final int nameResourceId;

    Planet(@DrawableRes int mapResourceId, @DrawableRes int galleryResourceId,
           @StringRes int nameResourceId, long updateFreqMs) {
        this.mapResourceId = mapResourceId;
        this.galleryResourceId = galleryResourceId;
        this.nameResourceId = nameResourceId;
        this.updateFreqMs = updateFreqMs;
    }

    /**
     * Calculate the geocentric right ascension and declination of the moon using
     * an approximation as described on page D22 of the 2008 Astronomical Almanac
     * All of the variables in this method use the same names as those described
     * in the text: lambda = Ecliptic longitude (degrees) beta = Ecliptic latitude
     * (degrees) pi = horizontal parallax (degrees) r = distance (Earth radii)
     * <p>
     * NOTE: The text does not give a specific time period where the approximation
     * is valid, but it should be valid through at least 2009.
     */
    public static EquatorialCoordinates calculateLunarGeocentricLocation(Calendar time) {
        // First, calculate the number of Julian centuries from J2000.0.
        double t = TimeUtils.julianDayToCentury(TimeUtils.julianDayGreenwich(time));

        // Second, calculate the approximate geocentric orbital elements.
        double lambda = 218.32 + 481267.881 * t + 6.29
                * Math.sin((135.0 + 477198.87 * t) * MathsUtils.DEGREES_TO_RADIANS) - 1.27
                * Math.sin((259.3 - 413335.36 * t) * MathsUtils.DEGREES_TO_RADIANS) + 0.66
                * Math.sin((235.7 + 890534.22 * t) * MathsUtils.DEGREES_TO_RADIANS) + 0.21
                * Math.sin((269.9 + 954397.74 * t) * MathsUtils.DEGREES_TO_RADIANS) - 0.19
                * Math.sin((357.5 + 35999.05 * t) * MathsUtils.DEGREES_TO_RADIANS) - 0.11
                * Math.sin((186.5 + 966404.03 * t) * MathsUtils.DEGREES_TO_RADIANS);
        double beta = 5.13f * Math.sin((93.3f + 483202.02f * t) * MathsUtils.DEGREES_TO_RADIANS) + 0.28
                * Math.sin((228.2 + 960400.89 * t) * MathsUtils.DEGREES_TO_RADIANS) - 0.28
                * Math.sin((318.3 + 6003.15 * t) * MathsUtils.DEGREES_TO_RADIANS) - 0.17
                * Math.sin((217.6 - 407332.21 * t) * MathsUtils.DEGREES_TO_RADIANS);
        //double pi =
        //    0.9508 + 0.0518 * MathUtil.cos((135.0 + 477198.87 * t) * Geometry.DEGREES_TO_RADIANS)
        //        + 0.0095 * MathUtil.cos((259.3 - 413335.36 * t) * Geometry.DEGREES_TO_RADIANS)
        //        + 0.0078 * MathUtil.cos((235.7 + 890534.22 * t) * Geometry.DEGREES_TO_RADIANS)
        //        + 0.0028 * MathUtil.cos((269.9 + 954397.74 * t) * Geometry.DEGREES_TO_RADIANS);
        // double r = 1.0 / MathUtil.sin(pi * Geometry.DEGREES_TO_RADIANS);

        // Third, convert to RA and Dec.
        double l = Math.cos(beta * MathsUtils.DEGREES_TO_RADIANS)
                * Math.cos(lambda * MathsUtils.DEGREES_TO_RADIANS);
        double m = 0.9175 * Math.cos(beta * MathsUtils.DEGREES_TO_RADIANS)
                * Math.sin(lambda * MathsUtils.DEGREES_TO_RADIANS) - 0.3978
                * Math.sin(beta * MathsUtils.DEGREES_TO_RADIANS);
        double n = 0.3978 * Math.cos(beta * MathsUtils.DEGREES_TO_RADIANS)
                * Math.sin(lambda * MathsUtils.DEGREES_TO_RADIANS) + 0.9175
                * Math.sin(beta * MathsUtils.DEGREES_TO_RADIANS);

        return new EquatorialCoordinates(
                MathsUtils.mod2pi(Math.atan2(m, l)) * MathsUtils.RADIANS_TO_DEGREES,
                Math.asin(n) * MathsUtils.RADIANS_TO_DEGREES);
    }

    /**
     * Return the date of the next full moon after today.
     */
    // TODO(serafini): This could also be error prone right around the time of the full and new moons...
    public static Calendar getNextFullMoon(Calendar now) {
        // First, get the moon's current phase.
        double phase = Moon.calculatePhaseAngle(now);

        // Next, figure out if the moon is waxing or waning.
        Calendar later = (Calendar) now.clone();
        later.add(Calendar.HOUR, 1);
        double phase2 = Moon.calculatePhaseAngle(later);
        boolean isWaxing = phase2 > phase;

        // If moon is waxing, next full moon is (180.0 - phase)/360.0 * 29.53.
        // If moon is waning, next full moon is (360.0 - phase)/360.0 * 29.53.
        final double LUNAR_CYCLE = 29.53;  // In days.
        double baseAngle = (isWaxing ? 180.0 : 360.0);
        double numDays = (baseAngle - phase) / 360.0 * LUNAR_CYCLE;

        Calendar res = Calendar.getInstance();
        res.setTimeInMillis(now.getTimeInMillis() + (long) (numDays * 24.0 * 3600.0 * 1000.0));
        return res;
    }

    /**
     * Calculates the hour angle of a given declination for the given location.
     * This is a helper application for the rise and set calculations. Its
     * probably not worth using as a general purpose method.
     * All values are in degrees.
     * This method calculates the hour angle from the meridian using the
     * following equation from the Astronomical Almanac (p487):
     * cos ha = (sin alt - sin lat * sin dec) / (cos lat * cos dec)
     */
    public static double calculateHourAngle(double altitude, double latitude, double declination) {
        double altRads = altitude * Math.PI / 180.0,
                latRads = latitude * Math.PI / 180.0,
                decRads = declination * Math.PI / 180.0,
                cosHa = (Math.sin(altRads) - Math.sin(latRads) * Math.sin(decRads)) / (Math.cos(latRads) * Math.cos(decRads));
        return 180.0 / Math.PI * Math.acos(cosHa);
    }

    public int getGalleryResourceId() {
        if (this == Planet.Moon) return getLunarPhaseImageId(Calendar.getInstance());
        return galleryResourceId;
    }

    public EquatorialCoordinates getEquatorialCoordinates(Calendar time, HeliocentricCoordinates earthCoordinates) {
        if (this == Planet.Moon) {
            return Planet.calculateLunarGeocentricLocation(time);
        }
        HeliocentricCoordinates coords;
        if (this == Planet.Sun) {
            // Invert the view, since we want the Sun in earth coordinates, not the Earth in sun coordinates.
            coords = new HeliocentricCoordinates(earthCoordinates.radius, earthCoordinates.x * -1.0f,
                    earthCoordinates.y * -1.0f, earthCoordinates.z * -1.0f);
        } else {
            coords = HeliocentricCoordinates.getInstance(this, time);
            coords.subtract(earthCoordinates);
        }
        HeliocentricCoordinates equ = coords.calculateEquatorialCoordinates();
        return getInstance(equ);
    }

    // TODO(serafini): We need to correct the Ra/Dec for the user's location. The
    // current calculation is probably accurate to a degree or two, but we can,
    // and should, do better.

    public long getUpdateFrequencyMs() {
        return updateFreqMs;
    }

    /**
     * Returns the resource id for the string corresponding to the name of this
     * planet.
     */
    public String getName(Resources resources) {
        return resources.getString(nameResourceId);
    }

    /**
     * Returns the resource id for the planet's image.
     */
    public int getMapResourceId(Calendar time) {
        if (this == Planet.Moon) return getLunarPhaseImageId(time);
        return this.mapResourceId;
    }

    /**
     * Determine the Moon's phase and return the resource ID of the correct
     * image.
     */
    private int getLunarPhaseImageId(Calendar time) {
        // First, calculate phase angle:
        double phase = calculatePhaseAngle(time);
        Log.d(TAG, "Lunar phase = " + phase);

        // Next, figure out what resource id to return.
        if (phase < 22.5f) {
            // New moon.
            return R.drawable.moon0;
        } else if (phase > 150.0f) {
            // Full moon.
            return R.drawable.moon4;
        }

        // Either crescent, quarter, or gibbous. Need to see whether we are
        // waxing or waning. Calculate the phase angle one day in the future.
        // If phase is increasing, we are waxing. If not, we are waning.
        Calendar tomorrow = (Calendar) time.clone();
        tomorrow.add(Calendar.DATE, 1);
        double phase2 = calculatePhaseAngle(tomorrow);
        Log.d(TAG, "Tomorrow's phase = " + phase2);

        if (phase < 67.5f) {
            // Crescent
            return (phase2 > phase) ? R.drawable.moon1 : R.drawable.moon7;
        } else if (phase < 112.5f) {
            // Quarter
            return (phase2 > phase) ? R.drawable.moon2 : R.drawable.moon6;
        }
        // Gibbous
        return (phase2 > phase) ? R.drawable.moon3 : R.drawable.moon5;
    }

    /**
     * Taken from JPL's Planetary Positions page: http://ssd.jpl.nasa.gov/?planet_pos
     * This gives us a good approximation for the years 1800 to 2050 AD.
     */
    public OrbitalElements getOrbitalElements(Calendar date) {
        // Centuries since J2000
        double jc = TimeUtils.julianDayToCentury(TimeUtils.julianDayGreenwich(date));
        switch (this) {
            case Mercury: {
                double a = 0.38709927 + 0.00000037 * jc;
                double e = 0.20563593 + 0.00001906 * jc;
                double i = (7.00497902 - 0.00594749 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double l = MathsUtils.mod2pi((252.25032350 + 149472.67411175 * jc) * MathsUtils.DEGREES_TO_RADIANS);
                double w = (77.45779628 + 0.16047689 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double o = (48.33076593 - 0.12534081 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                return new OrbitalElements(a, e, i, o, w, l);
            }

            case Venus: {
                double a = 0.72333566 + 0.00000390 * jc;
                double e = 0.00677672 - 0.00004107 * jc;
                double i = (3.39467605 - 0.00078890 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double l = MathsUtils.mod2pi((181.97909950 + 58517.81538729 * jc) * MathsUtils.DEGREES_TO_RADIANS);
                double w = (131.60246718 + 0.00268329 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double o = (76.67984255 - 0.27769418 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                return new OrbitalElements(a, e, i, o, w, l);
            }

            // Note that this is the orbital data or Earth.
            case Sun: {
                double a = 1.00000261 + 0.00000562 * jc;
                double e = 0.01671123 - 0.00004392 * jc;
                double i = (-0.00001531 - 0.01294668 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double l = MathsUtils.mod2pi((100.46457166 + 35999.37244981 * jc) * MathsUtils.DEGREES_TO_RADIANS);
                double w = (102.93768193 + 0.32327364 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double o = 0.0;
                return new OrbitalElements(a, e, i, o, w, l);
            }

            case Mars: {
                double a = 1.52371034 + 0.00001847 * jc;
                double e = 0.09339410 + 0.00007882 * jc;
                double i = (1.84969142 - 0.00813131 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double l = MathsUtils.mod2pi((-4.55343205 + 19140.30268499 * jc) * MathsUtils.DEGREES_TO_RADIANS);
                double w = (-23.94362959 + 0.44441088 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double o = (49.55953891 - 0.29257343 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                return new OrbitalElements(a, e, i, o, w, l);
            }

            case Jupiter: {
                double a = 5.20288700 - 0.00011607 * jc;
                double e = 0.04838624 - 0.00013253 * jc;
                double i = (1.30439695 - 0.00183714 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double l = MathsUtils.mod2pi((34.39644051 + 3034.74612775 * jc) * MathsUtils.DEGREES_TO_RADIANS);
                double w = (14.72847983 + 0.21252668 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double o = (100.47390909 + 0.20469106 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                return new OrbitalElements(a, e, i, o, w, l);
            }

            case Saturn: {
                double a = 9.53667594 - 0.00125060 * jc;
                double e = 0.05386179 - 0.00050991 * jc;
                double i = (2.48599187 + 0.00193609 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double l = MathsUtils.mod2pi((49.95424423 + 1222.49362201 * jc) * MathsUtils.DEGREES_TO_RADIANS);
                double w = (92.59887831 - 0.41897216 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double o = (113.66242448 - 0.28867794 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                return new OrbitalElements(a, e, i, o, w, l);
            }

            case Uranus: {
                double a = 19.18916464 - 0.00196176 * jc;
                double e = 0.04725744 - 0.00004397 * jc;
                double i = (0.77263783 - 0.00242939 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double l = MathsUtils.mod2pi((313.23810451 + 428.48202785 * jc) * MathsUtils.DEGREES_TO_RADIANS);
                double w = (170.95427630 + 0.40805281 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double o = (74.01692503 + 0.04240589 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                return new OrbitalElements(a, e, i, o, w, l);
            }

            case Neptune: {
                double a = 30.06992276 + 0.00026291 * jc;
                double e = 0.00859048 + 0.00005105 * jc;
                double i = (1.77004347 + 0.00035372 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double l = MathsUtils.mod2pi((-55.12002969 + 218.45945325 * jc) * MathsUtils.DEGREES_TO_RADIANS);
                double w = (44.96476227 - 0.32241464 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double o = (131.78422574 - 0.00508664 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                return new OrbitalElements(a, e, i, o, w, l);
            }

            case Pluto: {
                double a = 39.48211675 - 0.00031596 * jc;
                double e = 0.24882730 + 0.00005170 * jc;
                double i = (17.14001206 + 0.00004818 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double l = MathsUtils.mod2pi((238.92903833 + 145.20780515 * jc) * MathsUtils.DEGREES_TO_RADIANS);
                double w = (224.06891629 - 0.04062942 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                double o = (110.30393684 - 0.01183482 * jc) * MathsUtils.DEGREES_TO_RADIANS;
                return new OrbitalElements(a, e, i, o, w, l);
            }

            default:
                throw new RuntimeException("Unknown Planet: " + this);
        }
    }

    /**
     * Calculates the phase angle of the planet, in degrees.
     */
    private double calculatePhaseAngle(Calendar time) {
        // For the moon, we will approximate phase angle by calculating the
        // elongation of the moon relative to the sun. This is accurate to within
        // about 1%.
        if (this == Planet.Moon) {
            EquatorialCoordinates moonRaDec = calculateLunarGeocentricLocation(time);
            GeocentricCoordinates moon = GeocentricCoordinates.getInstance(moonRaDec);

            HeliocentricCoordinates sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, time);
            EquatorialCoordinates sunRaDec = getInstance(sunCoords);
            GeocentricCoordinates sun = GeocentricCoordinates.getInstance(sunRaDec);
            return 180.0 - Math.acos(sun.x * moon.x + sun.y * moon.y + sun.z * moon.z) * MathsUtils.RADIANS_TO_DEGREES;
        }

        // First, determine position in the solar system.
        HeliocentricCoordinates planetCoords = HeliocentricCoordinates.getInstance(this, time);
        // Second, determine position relative to Earth
        HeliocentricCoordinates earthCoords = HeliocentricCoordinates.getInstance(Planet.Sun, time);
        double earthDistance = planetCoords.distanceFrom(earthCoords);
        // Finally, calculate the phase of the body.
        return Math.acos((earthDistance * earthDistance +
                planetCoords.radius * planetCoords.radius -
                earthCoords.radius * earthCoords.radius) /
                (2.0 * earthDistance * planetCoords.radius)) * MathsUtils.RADIANS_TO_DEGREES;
    }

    /**
     * Calculates the planet's magnitude for the given date.
     */
    // TODO(serafini): I need to re-factor this method so it uses the phase
    // calculations above. For now, I'm going to duplicate some code to avoid
    // some redundant calculations at run time.
    public double getMagnitude(Calendar time) {
        if (this == Planet.Sun) {
            return -27.0;
        } else if (this == Planet.Moon) {
            return -10.0;
        }

        // First, determine position in the solar system.
        HeliocentricCoordinates planetCoords = HeliocentricCoordinates.getInstance(this, time);
        // Second, determine position relative to Earth
        HeliocentricCoordinates earthCoords = HeliocentricCoordinates.getInstance(Planet.Sun, time);
        double earthDistance = planetCoords.distanceFrom(earthCoords);
        // Third, calculate the phase of the body.
        double phase = Math.acos((earthDistance * earthDistance +
                planetCoords.radius * planetCoords.radius -
                earthCoords.radius * earthCoords.radius) /
                (2.0 * earthDistance * planetCoords.radius)) * MathsUtils.RADIANS_TO_DEGREES;
        double p = phase / 100.0;     // Normalized phase angle
        // Finally, calculate the magnitude of the body.
        double mag;      // Apparent visual magnitude
        switch (this) {
            case Mercury:
                mag = -0.42 + (3.80 - (2.73 - 2.00 * p) * p) * p;
                break;
            case Venus:
                mag = -4.40 + (0.09 + (2.39 - 0.65 * p) * p) * p;
                break;
            case Mars:
                mag = -1.52 + 1.6 * p;
                break;
            case Jupiter:
                mag = -9.40 + 0.5 * p;
                break;
            case Saturn:
                // TODO(serafini): Add the real calculations that consider the position
                // of the rings. For now, lets assume the following, which gets us a reasonable
                // approximation of Saturn's magnitude for the near future.
                mag = -8.75;
                break;
            case Uranus:
                mag = -7.19;
                break;
            case Neptune:
                mag = -6.87;
                break;
            case Pluto:
                mag = -1.0;
                break;
            default:
                Log.e(TAG, "Invalid planet: " + this);
                // At least make it faint!
                mag = 100;
                break;
        }
        return (mag + 5.0 * Math.log10(planetCoords.radius * earthDistance));
    }

    public float getPlanetaryImageSize() {
        switch (this) {
            case Mercury:
            case Venus:
            case Mars:
            case Pluto:
                return 0.01f;
            case Jupiter:
                return 0.025f;
            case Uranus:
            case Neptune:
                return 0.015f;
            case Saturn:
                return 0.035f;
            case Sun:
            case Moon:
            default:
                return 0.02f;
        }
    }

    /**
     * Calculates the next rise or set time of this planet from a given observer.
     * Returns null if the planet doesn't rise or set during the next day.
     *
     * @param now       Calendar time from which to calculate next rise / set time.
     * @param loc       Location of observer.
     * @param indicator Indicates whether to look for rise or set time.
     * @return New Calendar set to the next rise or set time if within the next day, otherwise null.
     */
    public Calendar calcNextRiseSetTime(Calendar now, Location loc, RiseSetIndicator indicator) {
        // Make a copy of the calendar to return.
        Calendar riseSetTime = Calendar.getInstance();
        double riseSetUt = calcRiseSetTime(now, loc, indicator);
        // Early out if no nearby rise set time.
        if (riseSetUt < 0) {
            return null;
        }

        // Find the start of this day in the local time zone. The (a / b) * b
        // formulation looks weird, it's using the properties of int arithmetic
        // so that (a / b) is really floor(a / b).
        long dayStart = (now.getTimeInMillis() / TimeUtils.MILLISECONDS_PER_DAY)
                * TimeUtils.MILLISECONDS_PER_DAY - riseSetTime.get(Calendar.ZONE_OFFSET);
        long riseSetUtMillis = (long) (calcRiseSetTime(now, loc, indicator)
                * TimeUtils.MILLISECONDS_PER_HOUR);
        long newTime = dayStart + riseSetUtMillis + riseSetTime.get(Calendar.ZONE_OFFSET);
        // If the newTime is before the current time, go forward 1 day.
        if (newTime < now.getTimeInMillis()) {
            Log.d(TAG, "Nearest Rise/Set is in the past. Adding one day.");
            newTime += TimeUtils.MILLISECONDS_PER_DAY;
        }
        riseSetTime.setTimeInMillis(newTime);
        if (!riseSetTime.after(now)) {
            Log.e(TAG, "Next rise set time (" + riseSetTime
                    + ") should be after current time (" + now + ")");
        }
        return riseSetTime;
    }

    /**
     * Internally calculate the rise and set time of an object.
     * Returns a double, the number of hours through the day in UT.
     */
    private double calcRiseSetTime(Calendar calendar, Location loc, RiseSetIndicator indicator) {
        Calendar utc = (Calendar) calendar.clone();
        utc.setTimeZone(TimeZone.getTimeZone("UT"));
        double sign = (indicator == RiseSetIndicator.RISE ? 1.0 : -1.0);
        double delta = 5.0;
        double ut = 12.0;
        int counter = 0;
        while (Math.abs(delta) > 0.008) {
            utc.set(Calendar.HOUR_OF_DAY, (int) Math.floor(ut));
            double minutes = (ut - Math.floor(ut)) * 60.0;
            utc.set(Calendar.MINUTE, (int) minutes);
            utc.set(Calendar.SECOND, (int) ((minutes - Math.floor(minutes)) * 60.0));

            // Calculate the hour angle and declination of the planet.
            // TODO(serafini): Need to fix this for arbitrary RA/Dec locations.
            EquatorialCoordinates raDec = getEquatorialCoordinates(utc, HeliocentricCoordinates.getInstance(Planet.Sun, utc));

            // GHA = GST - RA. (In degrees.)
            double gst = TimeUtils.meanSiderealTime(utc, 0.0);
            double gha = gst - raDec.ra;

            // The value of -0.83 works for the diameter of the Sun and Moon. We
            // assume that other objects are simply points.
            double bodySize = (this == Planet.Sun || this == Planet.Moon) ? -0.83 : 0.0;
            double hourAngle = calculateHourAngle(bodySize, loc.getLatitude(), raDec.dec);

            delta = (gha + (loc.getLongitude()) + (sign * hourAngle)) / 15.0;
            while (delta < -24.0) {
                delta = delta + 24.0;
            }
            while (delta > 24.0) {
                delta = delta - 24.0;
            }
            ut = ut - delta;

            // I think we need to normalize UT
            while (ut < 0.0) {
                ut = ut + 24.0;
            }
            while (ut > 24.0) {
                ut = ut - 24.0;
            }

            counter++;
            // Return failure if we didn't converge.
            if (counter == MAX_ITERATIONS) {
                Log.d(TAG, "Rise/Set calculation didn't converge.");
                return -1.0;
            }
        }
        // TODO(serafini): Need to handle latitudes above 60
        // At latitudes above 60, we need to calculate the following:
        // sin h = sin phi sin delta + cos phi cos delta cos (gha + lambda)
        return ut;
    }

    /**
     * Enum that identifies whether we are interested in rise or set time.
     */
    public enum RiseSetIndicator {RISE, SET}
}