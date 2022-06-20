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

import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.DISABLE_GYRO_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.SKY_MAP_HIGH_REFRESH_PREF;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import javax.inject.Inject;

import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;

/**
 * Sets the direction of view from the orientation sensors.
 *
 * @author John Taylor
 */
public class SensorOrientationController extends AbstractController implements SensorEventListener {

    private final static String TAG = TelescopeTouchApp.getTag(SensorOrientationController.class);
    private final SensorManager manager;
    private final Sensor rotationSensor;
    private final Sensor geomagneticRotationSensor;
    private final SharedPreferences preferences;

    @Inject
    SensorOrientationController(SensorManager manager, SharedPreferences preferences) {
        this.manager = manager;
        this.rotationSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        this.geomagneticRotationSensor = manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        this.preferences = preferences;
    }

    @Override
    public void start() {
        if (manager != null) {
            if (preferences.getBoolean(DISABLE_GYRO_PREF, false)) {
                Log.d(TAG, "Using geomagnetic rotation sensor");
                manager.registerListener(this, geomagneticRotationSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.d(TAG, "Using gyroscope sensor");
                manager.registerListener(this, rotationSensor,
                        preferences.getBoolean(SKY_MAP_HIGH_REFRESH_PREF, true) ?
                                SensorManager.SENSOR_DELAY_FASTEST : SensorManager.SENSOR_DELAY_GAME);
            }
        }
        Log.d(TAG, "Registered sensor listener");
    }

    @Override
    public void stop() {
        manager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ROTATION_VECTOR) {
            model.setPhoneSensorValues(event.values);
        } else if (type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            model.setPhoneSensorValues(event.values);
        } else {
            Log.w(TAG, "Unknown Sensor readings");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}