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

package io.github.marcocipriani01.telescopetouch.activities;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.LATITUDE_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.LONGITUDE_PREF;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = TelescopeTouchApp.getTag(MapsActivity.class);
    private static final int DEFAULT_ZOOM = 15;
    private static final int REQUEST_ACCESS_LOCATION = 1;
    private static final String BUNDLE_CAMERA = "camera_position";
    private static final String BUNDLE_MARKER = "marker";
    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private boolean permissionRequested = false;
    private boolean permissionGranted = false;
    private CameraPosition cameraPosition;
    private LatLng mapMarkerPos;
    private FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            cameraPosition = savedInstanceState.getParcelable(BUNDLE_CAMERA);
            mapMarkerPos = savedInstanceState.getParcelable(BUNDLE_MARKER);
        }
        setContentView(R.layout.activity_maps);
        fab = this.findViewById(R.id.maps_done_fab);
        fab.setOnClickListener(v -> finish());
        fab.hide();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        Places.initialize(this, getString(R.string.google_maps_key));
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        ((SupportMapFragment) Objects.requireNonNull(getSupportFragmentManager().findFragmentById(R.id.map))).getMapAsync(this);
    }

    @Override
    public void finish() {
        Intent intent = new Intent();
        if (mapMarkerPos == null) {
            setResult(Activity.RESULT_CANCELED, intent);
        } else {
            intent.putExtra(LATITUDE_PREF, Double.toString(mapMarkerPos.latitude));
            intent.putExtra(LONGITUDE_PREF, Double.toString(mapMarkerPos.longitude));
            setResult(Activity.RESULT_OK, intent);
        }
        super.finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (map != null) {
            outState.putParcelable(BUNDLE_CAMERA, map.getCameraPosition());
            outState.putParcelable(BUNDLE_MARKER, mapMarkerPos);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
        maybeRequestPermission();
        if (this.cameraPosition != null) {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(this.cameraPosition));
        } else if (mapMarkerPos == null) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            String lat = preferences.getString(LATITUDE_PREF, "0"),
                    lng = preferences.getString(LONGITUDE_PREF, "0");
            if ((!lat.equals("0")) || (!lng.equals("0"))) {
                try {
                    mapMarkerPos = new LatLng(Float.parseFloat(lat), Float.parseFloat(lng));
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(mapMarkerPos, DEFAULT_ZOOM));
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(mapMarkerPos, DEFAULT_ZOOM));
        }
        if (mapMarkerPos != null) {
            map.addMarker(getMapMarker());
            fab.show();
        } else if (permissionGranted) {
            try {
                fusedLocationProviderClient.getLastLocation().addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Location result = task.getResult();
                        if (result != null)
                            this.map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(result.getLatitude(), result.getLongitude()), DEFAULT_ZOOM));
                    } else {
                        this.map.getUiSettings().setMyLocationButtonEnabled(false);
                    }
                });
            } catch (SecurityException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        map.setOnMapClickListener(newLatLon -> {
            map.clear();
            mapMarkerPos = newLatLon;
            map.addMarker(getMapMarker());
            fab.show();
        });
        map.setOnMarkerClickListener(marker -> {
            finish();
            return false;
        });
    }

    private MarkerOptions getMapMarker() {
        return new MarkerOptions().position(mapMarkerPos).title(getString(R.string.select_position));
    }

    private void maybeRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true;
            map.setMyLocationEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
        } else {
            permissionGranted = false;
            if (!permissionRequested) {
                ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION}, REQUEST_ACCESS_LOCATION);
                permissionRequested = true;
            }
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ACCESS_LOCATION) {
            permissionGranted = (grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED);
            if (map != null) {
                try {
                    if (permissionGranted) {
                        map.setMyLocationEnabled(true);
                        map.getUiSettings().setMyLocationButtonEnabled(true);
                    } else {
                        map.setMyLocationEnabled(false);
                        map.getUiSettings().setMyLocationButtonEnabled(false);
                        maybeRequestPermission();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }
}