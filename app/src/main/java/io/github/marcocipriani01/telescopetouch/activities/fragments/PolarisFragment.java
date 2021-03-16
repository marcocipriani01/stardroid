/*
 * Copyright 2020 Marco Cipriani (@marcocipriani01) and the Sky Map Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.marcocipriani01.telescopetouch.activities.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.astronomy.Polaris;
import io.github.marcocipriani01.telescopetouch.maths.Formatters;
import io.github.marcocipriani01.telescopetouch.sensors.LocationHelper;

public class PolarisFragment extends ActionFragment {

    private final Polaris polaris = new Polaris();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float lastRotation = 0.0f;
    private boolean running = false;
    private boolean wasRunning = true;
    private ImageView stillImage;
    private ImageView rotatingImage;
    private TextView gpsText;
    private TextView spotText;
    private TextView hourAngleText;
    private String reticle;
    private final Runnable handlerTask = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            polaris.refresh();
            gpsText.setText(Formatters.latitudeToString((float) polaris.getLatitude(), context) + " / " +
                    Formatters.longitudeToString((float) polaris.getLongitude(), context));
            hourAngleText.setText(String.format(context.getString(R.string.hour_angle), polaris.getHourAngleString()));
            spotText.setText(String.format(context.getString(R.string.in_finder), context.getString(polaris.getStarName()), polaris.getScopePositionString()));
            float rotation = polaris.getScopePosition();
            if (reticle.equals("1"))
                rotation = (rotation + 90.0f) % 90.0f;
            Animation animation = new RotateAnimation(lastRotation, rotation,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            animation.setDuration(500);
            animation.setFillAfter(true);
            rotatingImage.startAnimation(animation);
            lastRotation = rotation;
            if (running)
                handler.postDelayed(handlerTask, 1000);
        }
    };
    private LocationHelper locationHelper;
    private SharedPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_polaris, container, false);
        stillImage = rootView.findViewById(R.id.polaris_reticle_skywatcher);
        rotatingImage = rootView.findViewById(R.id.polaris_crosshair);
        gpsText = rootView.findViewById(R.id.polaris_gps);
        spotText = rootView.findViewById(R.id.polaris_spot);
        hourAngleText = rootView.findViewById(R.id.polaris_hour_angle);
        locationHelper = new LocationHelper(getActivity()) {
            @Override
            protected void onLocationOk(Location location) {
                polaris.setLocation(location.getLatitude(), location.getLongitude());
            }

            @Override
            protected void requestLocationPermission() {
                requestPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            }

            @Override
            protected void makeSnack(String string) {
                requestActionSnack(string);
            }
        };
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        setReticle();
        return rootView;
    }

    @Override
    public void onPermissionAcquired(String permission) {
        locationHelper.restartLocation();
    }

    @Override
    public void onResume() {
        String hemisphere = preferences.getString(ApplicationConstants.POLARIS_HEMISPHERE_PREF, ApplicationConstants.HEMISPHERE_AUTO);
        if (ApplicationConstants.HEMISPHERE_AUTO.equals(hemisphere)) {
            polaris.setAutoHemisphereDetection(true);
        } else {
            polaris.setAutoHemisphereDetection(false);
            polaris.setNorthernHemisphere(ApplicationConstants.HEMISPHERE_NORTHERN.equals(hemisphere));
        }
        setReticle();
        locationHelper.start();
        lastRotation = 0.0f;
        Animation animation = new RotateAnimation(0.0f, 0.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(0);
        animation.setFillAfter(true);
        rotatingImage.startAnimation(animation);
        setRunning(wasRunning);
        notifyActionDrawableChange();
        super.onResume();
    }

    private void setReticle() {
        reticle = preferences.getString(ApplicationConstants.POLARIS_RETICLE_PREF, ApplicationConstants.RETICLE_SKY_WATCHER);
        if (ApplicationConstants.RETICLE_SKY_WATCHER.equals(reticle)) {
            rotatingImage.setImageResource(R.drawable.polaris_crosshair);
            stillImage.setImageResource(R.drawable.reticle_skywatcher);
        } else {
            rotatingImage.setImageResource(R.drawable.reticle_bigdipper);
            stillImage.setImageBitmap(null);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        wasRunning = running;
        setRunning(false);
        locationHelper.stop();
    }

    private void setRunning(boolean running) {
        this.running = running;
        handler.removeCallbacks(handlerTask);
        if (running)
            handler.postDelayed(handlerTask, 1000);
    }

    @Override
    public void run() {
        setRunning(!running);
        notifyActionDrawableChange();
    }

    @Override
    public boolean isActionEnabled() {
        return true;
    }

    @Override
    public int getActionDrawable() {
        return running ? R.drawable.pause : R.drawable.resume;
    }
}