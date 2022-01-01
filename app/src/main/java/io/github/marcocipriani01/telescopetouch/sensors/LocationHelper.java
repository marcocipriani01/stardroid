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

package io.github.marcocipriani01.telescopetouch.sensors;

import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.GPS_OFF_NO_DIALOG_PREF;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;

public abstract class LocationHelper implements LocationListener {

    private static final String TAG = TelescopeTouchApp.getTag(LocationHelper.class);
    private static final int MINIMUM_DISTANCE_UPDATES_METRES = 2000;
    private static final int LOCATION_UPDATE_TIME_MS = 180000;
    private final Activity activity;
    private final View rootView;
    private final LocationManager locationManager;
    private final SharedPreferences preferences;
    private boolean permissionRequested = false;

    public LocationHelper(Activity activity, LocationManager locationManager) {
        this.activity = activity;
        rootView = activity.getWindow().getDecorView().getRootView();
        this.locationManager = locationManager;
        preferences = PreferenceManager.getDefaultSharedPreferences(activity);
    }

    public LocationHelper(Activity activity) {
        this(activity, ContextCompat.getSystemService(activity, LocationManager.class));
    }

    public boolean start() {
        Log.d(TAG, "Location helper start");
        if (preferences.getBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF, false)) {
            Log.d(TAG, "User has set manual location.");
            setLocationFromPrefs();
            return true;
        }
        try {
            if (locationManager == null) {
                Log.e(TAG, "Location manager is null - using preferences");
                setLocationFromPrefs();
                return true;
            }
            Criteria locationCriteria = getLocationCriteria();
            String locationProvider = locationManager.getBestProvider(locationCriteria, true);
            if (locationProvider == null) {
                Log.w(TAG, "No location provider is enabled");
                if (locationManager.getBestProvider(locationCriteria, false) == null) {
                    Log.i(TAG, "No location provider is even available");
                    if (!permissionRequested) {
                        requestLocationPermission();
                        permissionRequested = true;
                    }
                } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                        !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) &&
                        !preferences.getBoolean(GPS_OFF_NO_DIALOG_PREF, false)) {
                    LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    Resources resources = activity.getResources();
                    int padding = resources.getDimensionPixelSize(R.dimen.dialog_margin);
                    layoutParams.setMargins(padding, resources.getDimensionPixelSize(R.dimen.padding_medium), padding, 0);
                    CheckBox checkBox = new AppCompatCheckBox(activity);
                    checkBox.setText(R.string.do_not_show_again);
                    layout.addView(checkBox, layoutParams);
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.location_offer_to_enable_gps_title).setCancelable(false)
                            .setMessage(R.string.location_offer_to_enable)
                            .setView(layout)
                            .setPositiveButton(android.R.string.ok, (dialog12, which) -> {
                                preferences.edit().putBoolean(GPS_OFF_NO_DIALOG_PREF, checkBox.isChecked()).apply();
                                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                activity.startActivity(intent);
                            })
                            .setNegativeButton(android.R.string.cancel, (dialog1, which) -> {
                                preferences.edit().putBoolean(GPS_OFF_NO_DIALOG_PREF, checkBox.isChecked())
                                        .putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF, true).apply();
                                makeSnack(activity.getString(R.string.msg_manual_location_on));
                                setLocationFromPrefs();
                            }).show();
                }
                setLocationFromPrefs();
                return true;
            }

            Log.d(TAG, "Got location provider " + locationProvider);
            locationManager.requestLocationUpdates(locationProvider, LOCATION_UPDATE_TIME_MS,
                    MINIMUM_DISTANCE_UPDATES_METRES, this, Looper.getMainLooper());
            Location location = locationManager.getLastKnownLocation(locationProvider);
            if (location != null) onLocationOk(location);
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Caught " + e.getLocalizedMessage(), e);
            return false;
        }
    }

    public void restartLocation() {
        try {
            String locationProvider = locationManager.getBestProvider(getLocationCriteria(), true);
            if (locationProvider != null) {
                locationManager.requestLocationUpdates(locationProvider, LOCATION_UPDATE_TIME_MS, MINIMUM_DISTANCE_UPDATES_METRES, this);
                Location location = locationManager.getLastKnownLocation(locationProvider);
                if (location != null) onLocationOk(location);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Caught " + e.getLocalizedMessage(), e);
        }
    }

    private Criteria getLocationCriteria() {
        Criteria locationCriteria = new Criteria();
        locationCriteria.setAccuracy(preferences.getBoolean(ApplicationConstants.FORCE_GPS_PREF, false) ?
                Criteria.ACCURACY_FINE : Criteria.ACCURACY_COARSE);
        locationCriteria.setAltitudeRequired(true);
        locationCriteria.setBearingRequired(false);
        locationCriteria.setCostAllowed(true);
        locationCriteria.setSpeedRequired(false);
        locationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        return locationCriteria;
    }

    protected abstract void requestLocationPermission();

    private void setLocationFromPrefs() {
        Log.d(TAG, "Setting location from preferences");
        double latitude = 0.0, longitude = 0.0;
        try {
            latitude = Double.parseDouble(preferences.getString(ApplicationConstants.LATITUDE_PREF, "0"));
            longitude = Double.parseDouble(preferences.getString(ApplicationConstants.LONGITUDE_PREF, "0"));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing latitude or longitude preference");
            makeSnack(activity.getString(R.string.malformed_loc_error));
        }
        Location location = new Location(activity.getString(R.string.preferences));
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAltitude(0.0f);
        Log.d(TAG, "Latitude " + longitude);
        Log.d(TAG, "Longitude " + latitude);
        onLocationOk(location);
    }

    public void stop() {
        Log.d(TAG, "Location helper stop");
        if (locationManager != null)
            locationManager.removeUpdates(this);
    }

    protected void makeSnack(String string) {
        Snackbar.make(rootView, string, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged");
        if (location == null) {
            Log.e(TAG, "Didn't get location even though onLocationChanged called");
            setLocationFromPrefs();
            return;
        }
        onLocationOk(location);
        // Only need get the location once.
        locationManager.removeUpdates(this);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {

    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {

    }

    protected abstract void onLocationOk(Location location);

    public void showLocationToUser(Location location, String provider) {
        Log.d(TAG, "Reverse geocoding location");
        List<Address> addresses = new ArrayList<>();
        String place;
        try {
            addresses = new Geocoder(activity).getFromLocation(location.getLatitude(), location.getLongitude(), 1);
        } catch (IOException e) {
            Log.e(TAG, "Unable to reverse geocode location " + location);
        }
        if (addresses == null || addresses.isEmpty()) {
            Log.d(TAG, "No addresses returned");
            place = String.format(activity.getString(R.string.location_long_lat), location.getLongitude(), location.getLatitude());
        } else {
            place = getPlaceSummary(location, addresses.get(0));
        }
        Log.d(TAG, "Location set to " + place);
        makeSnack(String.format(activity.getString(R.string.location_set_auto), provider, place));
    }

    private String getPlaceSummary(Location location, Address address) {
        String longLat = String.format(activity.getString(R.string.location_long_lat),
                location.getLongitude(), location.getLatitude());
        if (address == null) return longLat;
        String place = address.getLocality();
        if (place == null) place = address.getSubAdminArea();
        if (place == null) place = address.getAdminArea();
        if (place == null) place = longLat;
        return place;
    }
}