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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import javax.inject.Inject;

import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.activities.util.DarkerModeManager;

/**
 * Edit the user's preferences.
 */
public class SettingsActivity extends AppCompatActivity implements Preference.OnPreferenceChangeListener {

    @Inject
    SharedPreferences preferences;
    private Preference jpgQualityPref;
    private Preference indiWebPortPref;
    private Preference latitudePref;
    private Preference longitudePref;
    private final ActivityResultLauncher<Intent> resultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent intent = result.getData();
                    if (intent == null) return;
                    String latitude = intent.getStringExtra(ApplicationConstants.LATITUDE_PREF),
                            longitude = intent.getStringExtra(ApplicationConstants.LONGITUDE_PREF);
                    if ((latitude == null) || (longitude == null)) return;
                    preferences.edit().putString(ApplicationConstants.LATITUDE_PREF, latitude)
                            .putString(ApplicationConstants.LONGITUDE_PREF, longitude).apply();
                    if (latitudePref != null)
                        latitudePref.setSummary(latitude);
                    if (longitudePref != null)
                        longitudePref.setSummary(longitude);
                }
            });
    private Preference limitMagPref;
    private AppPreferenceFragment preferenceFragment;
    private DarkerModeManager darkerModeManager;
    private Preference gyroPref;
    private View rootView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((TelescopeTouchApp) getApplication()).getApplicationComponent().inject(this);
        darkerModeManager = new DarkerModeManager(this, null, PreferenceManager.getDefaultSharedPreferences(this));
        preferenceFragment = new AppPreferenceFragment();
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, preferenceFragment).commit();
        rootView = getWindow().getDecorView().getRootView();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        gyroPref = Objects.requireNonNull(preferenceFragment.findPreference(ApplicationConstants.DISABLE_GYRO_PREF));
        gyroPref.setOnPreferenceChangeListener(this);
        latitudePref = Objects.requireNonNull(preferenceFragment.findPreference(ApplicationConstants.LATITUDE_PREF));
        latitudePref.setOnPreferenceChangeListener(this);
        jpgQualityPref = Objects.requireNonNull(preferenceFragment.findPreference(ApplicationConstants.JPG_QUALITY_PREF));
        jpgQualityPref.setOnPreferenceChangeListener(this);
        indiWebPortPref = Objects.requireNonNull(preferenceFragment.findPreference(ApplicationConstants.WEB_MANAGER_PORT_PREF));
        indiWebPortPref.setOnPreferenceChangeListener(this);
        longitudePref = Objects.requireNonNull(preferenceFragment.findPreference(ApplicationConstants.LONGITUDE_PREF));
        longitudePref.setOnPreferenceChangeListener(this);
        limitMagPref = Objects.requireNonNull(preferenceFragment.findPreference(ApplicationConstants.CATALOG_LIMIT_MAGNITUDE));
        limitMagPref.setOnPreferenceChangeListener(this);
        enableGyroPrefs(preferences.getBoolean(ApplicationConstants.DISABLE_GYRO_PREF, false));
        latitudePref.setSummary(preferences.getString(ApplicationConstants.LATITUDE_PREF, "0.0"));
        longitudePref.setSummary(preferences.getString(ApplicationConstants.LONGITUDE_PREF, "0.0"));
        Objects.<Preference>requireNonNull(preferenceFragment.findPreference(ApplicationConstants.PICK_LOCATION_PREF))
                .setOnPreferenceClickListener(preference -> {
                    resultLauncher.launch(new Intent(SettingsActivity.this, MapsActivity.class));
                    return true;
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        darkerModeManager.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        darkerModeManager.stop();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void enableGyroPrefs(boolean enabled) {
        Objects.<Preference>requireNonNull(
                preferenceFragment.findPreference(ApplicationConstants.REVERSE_MAGNETIC_Z_PREF)).setEnabled(enabled);
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        if (preference == latitudePref) {
            String string = (String) newValue;
            try {
                double latitude = Double.parseDouble(string);
                if ((latitude > 90.0) || (latitude < -90.0)) {
                    Snackbar.make(rootView, R.string.out_of_bounds_loc_error, Snackbar.LENGTH_SHORT).setTextColor(getResources().getColor(R.color.colorAccent)).show();
                } else {
                    latitudePref.setSummary(string);
                    return true;
                }
            } catch (NumberFormatException e) {
                Snackbar.make(rootView, R.string.malformed_loc_error, Snackbar.LENGTH_SHORT).setTextColor(getResources().getColor(R.color.colorAccent)).show();
            }
        } else if (preference == longitudePref) {
            String string = (String) newValue;
            try {
                double longitude = Double.parseDouble(string);
                if ((longitude > 180.0) || (longitude < -180.0)) {
                    Snackbar.make(rootView, R.string.out_of_bounds_loc_error, Snackbar.LENGTH_SHORT).setTextColor(getResources().getColor(R.color.colorAccent)).show();
                } else {
                    longitudePref.setSummary(string);
                    return true;
                }
            } catch (NumberFormatException e) {
                Snackbar.make(rootView, R.string.malformed_loc_error, Snackbar.LENGTH_SHORT).setTextColor(getResources().getColor(R.color.colorAccent)).show();
            }
        } else if (preference == limitMagPref) {
            try {
                Double.parseDouble((String) newValue);
                return true;
            } catch (NumberFormatException e) {
                Snackbar.make(rootView, R.string.not_a_number, Snackbar.LENGTH_SHORT).setTextColor(getResources().getColor(R.color.colorAccent)).show();
                return false;
            }
        } else if (preference == gyroPref) {
            enableGyroPrefs(((Boolean) newValue));
            return true;
        } else if (preference == jpgQualityPref) {
            try {
                int quality = Integer.parseInt((String) newValue);
                if ((quality < 10) || quality > 100) {
                    Snackbar.make(rootView, R.string.invalid_value, Snackbar.LENGTH_SHORT).setTextColor(getResources().getColor(R.color.colorAccent)).show();
                    return false;
                }
                return true;
            } catch (NumberFormatException e) {
                Snackbar.make(rootView, R.string.not_a_number, Snackbar.LENGTH_SHORT).setTextColor(getResources().getColor(R.color.colorAccent)).show();
                return false;
            }
        } else if (preference == indiWebPortPref) {
            try {
                int port = Integer.parseInt((String) newValue);
                if ((port <= 0) || (port >= 0xFFFF)) {
                    Snackbar.make(rootView, R.string.invalid_value, Snackbar.LENGTH_SHORT).setTextColor(getResources().getColor(R.color.colorAccent)).show();
                    return false;
                }
                return true;
            } catch (NumberFormatException e) {
                Snackbar.make(rootView, R.string.not_a_number, Snackbar.LENGTH_SHORT).setTextColor(getResources().getColor(R.color.colorAccent)).show();
                return false;
            }
        }
        return false;
    }

    public static class AppPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.preference_screen);
        }
    }
}