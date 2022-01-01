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

package io.github.marcocipriani01.telescopetouch.activities.fragments;

import static io.github.marcocipriani01.telescopetouch.maths.Formatters.latitudeToString;
import static io.github.marcocipriani01.telescopetouch.maths.Formatters.longitudeToString;
import static io.github.marcocipriani01.telescopetouch.maths.Formatters.magDeclinationToString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.activities.CompassCalibrationActivity;
import io.github.marcocipriani01.telescopetouch.sensors.CompassHelper;

public class CompassFragment extends ActionFragment implements Toolbar.OnMenuItemClickListener {

    private CompassHelper compass;

    @Nullable
    @Override
    @SuppressLint("SetTextI18n")
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_compass, container, false);
        setHasOptionsMenu(true);
        final ImageView arrow = rootView.findViewById(R.id.compass_arrow);
        final TextView heading = rootView.findViewById(R.id.compass_heading);
        final TextView gps = rootView.findViewById(R.id.compass_gps);
        final TextView declination = rootView.findViewById(R.id.compass_declination);
        final ImageView level = rootView.findViewById(R.id.compass_level);
        compass = new CompassHelper(getActivity()) {
            private float lastAzimuth = 0;

            @Override
            protected void onLocationAndDeclination(Location location, float magneticDeclination) {
                gps.setText(latitudeToString((float) location.getLatitude(), context) + " / " +
                        longitudeToString((float) location.getLongitude(), context));
                declination.setText(context.getString(R.string.magnetic_declination) + ": " + magDeclinationToString(magneticDeclination, context));
            }

            @Override
            protected void onAzimuth(float azimuth, float arrowRotation) {
                heading.setText(((int) azimuth) + "°");
                Animation animation = new RotateAnimation(lastAzimuth, arrowRotation,
                        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                lastAzimuth = arrowRotation;
                animation.setDuration(500);
                animation.setRepeatCount(0);
                animation.setFillAfter(true);
                arrow.startAnimation(animation);
            }

            @Override
            protected void requestLocationPermission() {
                requestPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            }

            @Override
            protected void onDeclinationEnabledChange(boolean show) {
                int visibility = show ? View.VISIBLE : View.GONE;
                gps.setVisibility(visibility);
                declination.setVisibility(visibility);
            }

            @Override
            protected void onLevelChange(float x, float y) {
                level.setTranslationX(25 * x);
                level.setTranslationY(-25 * y);
            }

            @Override
            protected void makeSnack(String string) {
                requestActionSnack(string);
            }
        };
        return rootView;
    }

    @Override
    public void onPermissionAcquired(String permission) {
        compass.restartLocation();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.compass, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.menu_compass_calibration) {
            Intent intent = new Intent(context, CompassCalibrationActivity.class);
            intent.putExtra(CompassCalibrationActivity.HIDE_CHECKBOX, true);
            startActivity(intent);
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!compass.start())
            requestActionSnack(R.string.compass_not_available);
    }

    @Override
    public void onPause() {
        super.onPause();
        compass.stop();
    }
}