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

import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.snackbar.Snackbar;

import org.indilib.i4j.Constants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.BuildConfig;
import io.github.marcocipriani01.telescopetouch.ProUtils;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.activities.dialogs.MultipleSearchResultsDialogFragment;
import io.github.marcocipriani01.telescopetouch.activities.dialogs.NoSearchResultsDialogFragment;
import io.github.marcocipriani01.telescopetouch.activities.dialogs.NoSensorsDialogFragment;
import io.github.marcocipriani01.telescopetouch.activities.dialogs.TimeTravelDialogFragment;
import io.github.marcocipriani01.telescopetouch.activities.fragments.GoToFragment;
import io.github.marcocipriani01.telescopetouch.activities.util.DarkerModeManager;
import io.github.marcocipriani01.telescopetouch.activities.util.FullscreenControlsManager;
import io.github.marcocipriani01.telescopetouch.activities.views.FloatingButtonsLayout;
import io.github.marcocipriani01.telescopetouch.astronomy.EquatorialCoordinates;
import io.github.marcocipriani01.telescopetouch.astronomy.GeocentricCoordinates;
import io.github.marcocipriani01.telescopetouch.astronomy.HorizontalCoordinates;
import io.github.marcocipriani01.telescopetouch.control.AstronomerModel;
import io.github.marcocipriani01.telescopetouch.control.ControllerGroup;
import io.github.marcocipriani01.telescopetouch.control.MagneticDeclinationSwitcher;
import io.github.marcocipriani01.telescopetouch.control.Pointing;
import io.github.marcocipriani01.telescopetouch.inject.HasComponent;
import io.github.marcocipriani01.telescopetouch.layers.LayerManager;
import io.github.marcocipriani01.telescopetouch.maths.Vector3;
import io.github.marcocipriani01.telescopetouch.renderer.RendererController;
import io.github.marcocipriani01.telescopetouch.renderer.SkyRenderer;
import io.github.marcocipriani01.telescopetouch.renderer.util.AbstractUpdateClosure;
import io.github.marcocipriani01.telescopetouch.search.SearchResult;
import io.github.marcocipriani01.telescopetouch.sensors.SensorAccuracyMonitor;
import io.github.marcocipriani01.telescopetouch.touch.DragRotateZoomGestureDetector;
import io.github.marcocipriani01.telescopetouch.touch.GestureInterpreter;
import io.github.marcocipriani01.telescopetouch.touch.MapMover;

/**
 * The main map-rendering Activity.
 */
public class SkyMapActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, HasComponent<SkyMapComponent> {

    public static final String SKY_MAP_INTENT_ACTION = "io.github.marcocipriani01.telescopetouch.activities.SkyMapActivity";
    private static final String BUNDLE_X_TARGET = "bundle_x_target";
    private static final String BUNDLE_Y_TARGET = "bundle_y_target";
    private static final String BUNDLE_Z_TARGET = "bundle_z_target";
    private static final String BUNDLE_SEARCH_MODE = "bundle_search";
    private static final int TIME_DISPLAY_DELAY_MILLIS = 1000;
    private static final float ROTATION_SPEED = 10;
    private static final String TAG = TelescopeTouchApp.getTag(SkyMapActivity.class);
    // A list of runnables to post on the handler when we resume.
    private final List<Runnable> onResumeRunnables = new ArrayList<>();
    @Inject
    ControllerGroup controller;
    private final ActivityResultLauncher<String> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            result -> controller.onLocationPermissionAcquired());
    @Inject
    AstronomerModel model;
    @Inject
    SharedPreferences preferences;
    @Inject
    LayerManager layerManager;
    @Inject
    Handler handler;
    @Inject
    FragmentManager fragmentManager;
    @Inject
    TimeTravelDialogFragment timeTravelDialogFragment;
    @Inject
    NoSearchResultsDialogFragment noSearchResultsDialogFragment;
    @Inject
    MultipleSearchResultsDialogFragment multipleSearchResultsDialogFragment;
    @Inject
    NoSensorsDialogFragment noSensorsDialogFragment;
    @Inject
    SensorAccuracyMonitor sensorAccuracyMonitor;
    @Inject
    MagneticDeclinationSwitcher magneticSwitcher;
    @Inject
    Animation flashAnimation;
    @Inject
    SensorManager sensorManager;
    private FullscreenControlsManager fullscreenControlsManager;
    private ImageButton cancelSearchButton;
    private GestureDetector gestureDetector;
    private RendererController rendererController;
    private boolean searchMode = false;
    private GeocentricCoordinates searchTarget = GeocentricCoordinates.getInstance(0, 0);
    private GLSurfaceView skyView;
    private String searchTargetName;
    private View timePlayerUI;
    private SkyMapComponent daggerComponent;
    private DragRotateZoomGestureDetector dragZoomRotateDetector;
    private DarkerModeManager darkerModeManager;
    private SearchView searchView;
    private MenuItem searchMenuItem;
    private TextView pointingText;
    private View rootView;
    private boolean useAltAz = false;
    private GestureInterpreter gestureInterpreter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        daggerComponent = DaggerSkyMapComponent.builder()
                .applicationComponent(((TelescopeTouchApp) getApplication()).getApplicationComponent())
                .skyMapModule(new SkyMapModule(this)).build();
        daggerComponent.inject(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        initializeModelViewController();
        checkForSensorsAndMaybeWarn();
        magneticSwitcher.init();

        // Search related
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        darkerModeManager = new DarkerModeManager(this,
                b -> this.rendererController.queueNightVisionMode(b), preferences);

        Intent intent = getIntent();
        String intentAction = intent.getAction();
        setSupportActionBar(findViewById(R.id.sky_map_toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            boolean isSkyMapOnly = ((intentAction != null) && (intentAction.equals(SKY_MAP_INTENT_ACTION)));
            actionBar.setDisplayHomeAsUpEnabled(!isSkyMapOnly);
            actionBar.setDisplayShowHomeEnabled(!isSkyMapOnly);
        }
        this.<ImageButton>findViewById(R.id.telescope_control_button).setOnClickListener(v -> {
            Intent gotoIntent = new Intent(this, MainActivity.class);
            if (TelescopeTouchApp.connectionManager.isConnected()) {
                gotoIntent.putExtra(MainActivity.ACTION, MainActivity.ACTION_MOUNT_CONTROL);
            } else {
                gotoIntent.putExtra(MainActivity.ACTION, MainActivity.ACTION_CONNECT);
                gotoIntent.putExtra(MainActivity.MESSAGE, R.string.connect_telescope_first);
            }
            gotoIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(gotoIntent);
            finish();
        });
        this.<Button>findViewById(R.id.search_in_database).setOnClickListener(v -> {
            Intent mainIntent = new Intent(SkyMapActivity.this, MainActivity.class);
            if (connectionManager.isConnected()) {
                GoToFragment.setRequestedSearch(searchTargetName);
                mainIntent.putExtra(MainActivity.ACTION, MainActivity.ACTION_SEARCH);
            } else {
                mainIntent.putExtra(MainActivity.ACTION, MainActivity.ACTION_CONNECT);
                mainIntent.putExtra(MainActivity.MESSAGE, R.string.connect_telescope_first);
            }
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mainIntent);
            finish();
        });

        // Were we started as the result of a search?
        if (Intent.ACTION_SEARCH.equals(intentAction))
            doSearchWithIntent(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction()))
            doSearchWithIntent(intent);
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        Log.d(TAG, "Sky Map onRestoreInstanceState");
        super.onRestoreInstanceState(icicle);
        if (icicle == null) return;
        searchMode = icicle.getBoolean(BUNDLE_SEARCH_MODE);
        float x = icicle.getFloat(BUNDLE_X_TARGET);
        float y = icicle.getFloat(BUNDLE_Y_TARGET);
        float z = icicle.getFloat(BUNDLE_Z_TARGET);
        searchTarget = new GeocentricCoordinates(x, y, z);
        searchTargetName = icicle.getString(ApplicationConstants.BUNDLE_TARGET_NAME);
        if (searchMode) {
            Log.d(TAG, "Searching for target " + searchTargetName + " at target=" + searchTarget);
            rendererController.queueEnableSearchOverlay(searchTarget, searchTargetName);
            cancelSearchButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        if (fullscreenControlsManager != null)
            fullscreenControlsManager.flashControls();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.skymap, menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchMenuItem = menu.findItem(R.id.menu_skymap_search);
        searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (preferences.getBoolean(ApplicationConstants.KEEP_SCREEN_ON_PREF, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // PRO
        ProUtils.update(this);
        // END PRO
        Log.i(TAG, "Starting view");
        skyView.onResume();
        Log.i(TAG, "Starting controller");
        controller.start();
        darkerModeManager.start();
        if (controller.isAutoMode()) {
            sensorAccuracyMonitor.start();
        }
        for (Runnable runnable : onResumeRunnables) {
            handler.post(runnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorAccuracyMonitor.stop();
        for (Runnable runnable : onResumeRunnables) {
            handler.removeCallbacks(runnable);
        }
        darkerModeManager.stop();
        controller.stop();
        skyView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        Log.d(TAG, "Sky Map onSaveInstanceState");
        icicle.putBoolean(BUNDLE_SEARCH_MODE, searchMode);
        icicle.putFloat(BUNDLE_X_TARGET, (float) searchTarget.x);
        icicle.putFloat(BUNDLE_Y_TARGET, (float) searchTarget.y);
        icicle.putFloat(BUNDLE_Z_TARGET, (float) searchTarget.z);
        icicle.putString(ApplicationConstants.BUNDLE_TARGET_NAME, searchTargetName);
        super.onSaveInstanceState(icicle);
    }

    @Override
    public SkyMapComponent getComponent() {
        return daggerComponent;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        super.onOptionsItemSelected(item);
        fullscreenControlsManager.delayedHide();
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
        } else if (itemId == R.id.menu_skymap_search) {
            onSearchRequested();
        } else if (itemId == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (itemId == R.id.menu_darker_mode) {
            item.setIcon(darkerModeManager.toggle() ? R.drawable.light_mode : R.drawable.darker_mode);
        } else if (itemId == R.id.menu_skymap_time_travel) {
            if (!timePlayerUI.isShown()) {
                Log.d(TAG, "Resetting time in time travel dialog.");
                controller.goTimeTravel(new Date());
            } else {
                Log.d(TAG, "Resuming current time travel dialog.");
            }
            timeTravelDialogFragment.show(fragmentManager, "Time Travel");
        } else if (itemId == R.id.menu_skymap_gallery) {
            startActivity(new Intent(this, ImageGalleryActivity.class));
        } else if (itemId == R.id.menu_compass_calibration) {
            Intent intent = new Intent(this, CompassCalibrationActivity.class);
            intent.putExtra(CompassCalibrationActivity.HIDE_CHECKBOX, true);
            startActivity(intent);
        } else if (itemId == R.id.menu_skymap_diagnostics) {
            startActivity(new Intent(this, DiagnosticActivity.class));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Either of the following detectors can absorb the event, but one must not hide it from the other
        boolean eventAbsorbed = false;
        if (gestureDetector.onTouchEvent(event)) {
            eventAbsorbed = true;
        }
        if (dragZoomRotateDetector.onTouchEvent(event)) {
            eventAbsorbed = true;
        }
        return eventAbsorbed;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case (KeyEvent.KEYCODE_DPAD_LEFT):
                Log.d(TAG, "Key left");
                controller.rotate(-10.0f);
                break;
            case (KeyEvent.KEYCODE_DPAD_RIGHT):
                Log.d(TAG, "Key right");
                controller.rotate(10.0f);
                break;
            case (KeyEvent.KEYCODE_BACK):
                // If we're in search mode when the user presses 'back' the natural
                // thing is to back out of search.
                Log.d(TAG, "In search mode " + searchMode);
                if (searchMode) {
                    cancelSearch();
                    break;
                }
            default:
                Log.d(TAG, "Key: " + event);
                return super.onKeyDown(keyCode, event);
        }
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        controller.rotate(event.getX() * ROTATION_SPEED);
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (ApplicationConstants.AUTO_MODE_PREF.equals(key)) {
            setAutoMode(preferences.getBoolean(key, true));
        } else if (ApplicationConstants.ROTATE_HORIZON_PREF.equals(key)) {
            model.setHorizontalRotation(preferences.getBoolean(key, false));
        } else if (ApplicationConstants.SKY_MAP_HIGH_REFRESH_PREF.equals(key)) {
            gestureInterpreter.setUpdateRate(preferences.getBoolean(ApplicationConstants.SKY_MAP_HIGH_REFRESH_PREF, false) ? 60 : 30);
        }
    }

    private void initializeModelViewController() {
        Log.i(TAG, "Initializing Model, View and Controller");
        setContentView(R.layout.skyrenderer);
        rootView = getWindow().getDecorView().getRootView();
        skyView = findViewById(R.id.sky_renderer_view);
        // We don't want a depth buffer.
        skyView.setEGLConfigChooser(false);
        SkyRenderer renderer = new SkyRenderer(getResources());
        skyView.setRenderer(renderer);

        rendererController = new RendererController(renderer, skyView);
        // The renderer will now call back every frame to get model updates.
        rendererController.addUpdateClosure(new RendererModelUpdateClosure());

        Log.i(TAG, "Setting layers");
        layerManager.registerWithRenderer(rendererController);
        Log.i(TAG, "Set up controllers");
        controller.setModel(model);

        cancelSearchButton = findViewById(R.id.cancel_search_button);
        cancelSearchButton.setOnClickListener(v1 -> cancelSearch());
        pointingText = findViewById(R.id.skymap_pointing);

        FloatingButtonsLayout providerButtons = findViewById(R.id.layer_buttons_control);
        int numChildren = providerButtons.getChildCount();
        View[] buttonViews = new View[numChildren + 1];
        for (int i = 0; i < numChildren; i++) {
            buttonViews[i] = providerButtons.getChildAt(i);
        }
        buttonViews[numChildren] = findViewById(R.id.manual_auto_toggle);
        fullscreenControlsManager = new FullscreenControlsManager(this,
                new View[]{this.findViewById(R.id.layer_manual_auto_toggle), providerButtons, pointingText}, buttonViews);

        MapMover mapMover = new MapMover(model, controller, this, preferences);
        gestureInterpreter = new GestureInterpreter(fullscreenControlsManager, mapMover);
        gestureInterpreter.setUpdateRate(preferences.getBoolean(ApplicationConstants.SKY_MAP_HIGH_REFRESH_PREF, false) ? 60 : 30);
        gestureDetector = new GestureDetector(this, gestureInterpreter);
        dragZoomRotateDetector = new DragRotateZoomGestureDetector(mapMover);

        Log.d(TAG, "Initializing TimePlayer UI.");
        timePlayerUI = findViewById(R.id.time_player_view);
        ImageButton timePlayerCancelButton = findViewById(R.id.time_player_close);
        ImageButton timePlayerBackwardsButton = findViewById(
                R.id.time_player_play_backwards);
        ImageButton timePlayerStopButton = findViewById(R.id.time_player_play_stop);
        ImageButton timePlayerForwardsButton = findViewById(
                R.id.time_player_play_forwards);
        final TextView timeTravelSpeedLabel = findViewById(R.id.time_travel_speed_label);

        timePlayerCancelButton.setOnClickListener(v -> {
            Log.d(TAG, "Heard time player close click.");
            setNormalTimeModel();
        });
        timePlayerBackwardsButton.setOnClickListener(v -> {
            Log.d(TAG, "Heard time player play backwards click.");
            controller.decelerateTimeTravel();
            timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
        });
        timePlayerStopButton.setOnClickListener(v -> {
            Log.d(TAG, "Heard time player play stop click.");
            controller.pauseTime();
            timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
        });
        timePlayerForwardsButton.setOnClickListener(v -> {
            Log.d(TAG, "Heard time player play forwards click.");
            controller.accelerateTimeTravel();
            timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
        });

        onResumeRunnables.add(new Runnable() {
            private final TextView timeTravelTimeReadout = findViewById(R.id.time_travel_time_readout);
            private final TextView timeTravelStatusLabel = findViewById(R.id.time_travel_status_label);
            private final java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(SkyMapActivity.this);
            private final java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(SkyMapActivity.this);
            private final Date date = new Date();

            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                long time = model.getTimeMillis();
                date.setTime(time);
                timeTravelTimeReadout.setText(dateFormat.format(date) + ", " + timeFormat.format(date));
                if (time > System.currentTimeMillis()) {
                    timeTravelStatusLabel.setText(R.string.time_travel_label_future);
                } else {
                    timeTravelStatusLabel.setText(R.string.time_travel_label_past);
                }
                timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
                handler.postDelayed(this, TIME_DISPLAY_DELAY_MILLIS);
            }
        });
    }

    public void requestLocationPermission() {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void checkForSensorsAndMaybeWarn() {
        if (hasSensors(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)) {
            Log.i(TAG, "Minimum sensors present");
            setAutoMode(preferences.getBoolean(ApplicationConstants.AUTO_MODE_PREF, true));
            // Enable Gyro if available and user hasn't already disabled it.
            if (!preferences.contains(ApplicationConstants.DISABLE_GYRO_PREF))
                preferences.edit().putBoolean(ApplicationConstants.DISABLE_GYRO_PREF,
                        !hasSensors(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GYROSCOPE)).apply();
            if (BuildConfig.DEBUG) {
                // Lastly a dump of all the sensors.
                Log.d(TAG, "List of device sensors:");
                List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
                for (Sensor sensor : allSensors) {
                    Log.d(TAG, sensor.getName() + ", sensor type: " + sensor.getStringType() + " (" + sensor.getType() + ")");
                }
            }
        } else {
            // Missing at least one sensor. Warn the user.
            handler.post(() -> {
                // Force manual mode.
                preferences.edit().putBoolean(ApplicationConstants.AUTO_MODE_PREF, false).apply();
                setAutoMode(false);
                if (!preferences.getBoolean(ApplicationConstants.NO_WARN_MISSING_SENSORS_PREF, false))
                    noSensorsDialogFragment.show(fragmentManager, "No sensors dialog");
            });
        }
    }

    private boolean hasSensors(int... sensorTypes) {
        if (sensorManager == null) return false;
        for (int sensorType : sensorTypes) {
            Sensor sensor = sensorManager.getDefaultSensor(sensorType);
            if (sensor == null) return false;
            SensorEventListener dummy = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };
            boolean b = sensorManager.registerListener(dummy, sensor, SensorManager.SENSOR_DELAY_UI);
            sensorManager.unregisterListener(dummy);
            if (!b) return false;
        }
        return true;
    }

    public void setTimeTravelMode(Date newTime) {
        Log.d(TAG, "Showing TimePlayer UI.");
        pointingText.setVisibility(View.GONE);
        timePlayerUI.setVisibility(View.VISIBLE);
        timePlayerUI.requestFocus();
        flashMap();
        controller.goTimeTravel(newTime);
    }

    public void setNormalTimeModel() {
        flashMap();
        controller.useRealTime();
        Snackbar.make(rootView, R.string.time_travel_close_message, Snackbar.LENGTH_SHORT).show();
        Log.d(TAG, "Leaving Time Travel mode.");
        timePlayerUI.setVisibility(View.GONE);
        pointingText.setVisibility(View.VISIBLE);
    }

    private void flashMap() {
        final View mask = findViewById(R.id.view_mask);
        // We don't need to set it invisible again - the end of the animation will see to that.
        mask.setVisibility(View.VISIBLE);
        mask.startAnimation(flashAnimation);
    }

    private void doSearchWithIntent(Intent searchIntent) {
        // If we're already in search mode, cancel it.
        if (searchMode) cancelSearch();
        Log.d(TAG, "Performing Search");
        final String queryString = searchIntent.getStringExtra(SearchManager.QUERY);
        searchMode = true;
        Log.d(TAG, "Query string " + queryString);
        List<SearchResult> results = layerManager.searchByObjectName(queryString);
        if (results.isEmpty()) {
            Log.d(TAG, "No results returned");
            noSearchResultsDialogFragment.show(fragmentManager, "No Search Results");
        } else if (results.size() > 1) {
            Log.d(TAG, "Multiple results returned");
            showUserChooseResultDialog(results);
        } else {
            Log.d(TAG, "One result returned.");
            final SearchResult result = results.get(0);
            activateSearchTarget(result.coords, result.capitalizedName);
        }
    }

    private void showUserChooseResultDialog(List<SearchResult> results) {
        multipleSearchResultsDialogFragment.clearResults();
        for (SearchResult result : results) {
            multipleSearchResultsDialogFragment.add(result);
        }
        multipleSearchResultsDialogFragment.show(fragmentManager, "Multiple Search Results");
    }

    private void setAutoMode(boolean auto) {
        controller.setAutoMode(auto);
        if (auto) {
            sensorAccuracyMonitor.start();
        } else {
            sensorAccuracyMonitor.stop();
        }
    }

    private void cancelSearch() {
        View searchControlBar = findViewById(R.id.search_control_bar);
        searchControlBar.setVisibility(View.GONE);
        rendererController.queueDisableSearchOverlay();
        searchMode = false;
    }

    public void activateSearchTarget(GeocentricCoordinates target, final String searchTerm) {
        Log.d(TAG, "Item " + searchTerm + " selected");
        if (searchView != null) {
            searchView.clearFocus();
            searchView.setIconified(true);
            searchView.onActionViewCollapsed();
        }
        if (searchMenuItem != null)
            searchMenuItem.collapseActionView();
        // Store these for later.
        searchTarget = target;
        searchTargetName = searchTerm;
        Log.d(TAG, "Searching for target=" + target);
        rendererController.queueViewerUpDirection(model.getZenith().copy());
        rendererController.queueEnableSearchOverlay(target.copy(), searchTerm);
        boolean autoMode = preferences.getBoolean(ApplicationConstants.AUTO_MODE_PREF, true);
        if (!autoMode) {
            controller.teleport(target);
        }

        TextView searchPromptText = findViewById(R.id.search_status_label);
        searchPromptText.setText(
                String.format("%s %s", getString(R.string.search_target_looking_message), searchTerm));
        View searchControlBar = findViewById(R.id.search_control_bar);
        searchControlBar.setVisibility(View.VISIBLE);
    }

    public AstronomerModel getModel() {
        return model;
    }

    public void pointTelescope(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(R.string.point_telescope);
        if (!connectionManager.isConnected()) {
            builder.setMessage(R.string.connect_telescope_first)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.putExtra(MainActivity.ACTION, MainActivity.ACTION_CONNECT);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        } else if ((connectionManager.telescopeName == null) ||
                (connectionManager.telescopeCoordP == null) || (connectionManager.telescopeOnCoordSetP == null)) {
            builder.setMessage(R.string.no_telescope_found);
        } else {
            EquatorialCoordinates coordinates = model.getEquatorialCoordinates();
            String msg = String.format(getString(R.string.point_telescope_message),
                    connectionManager.telescopeName, coordinates.getRAString(), coordinates.getDecString());
            if (HorizontalCoordinates.getInstance(coordinates, model.getLocation(), Calendar.getInstance()).alt < 0)
                msg += getString(R.string.below_horizon_warning);
            builder.setMessage(msg)
                    .setPositiveButton(R.string.go_to, (dialog, which) -> {
                        try {
                            connectionManager.telescopeOnCoordSetTrack.setDesiredValue(Constants.SwitchStatus.ON);
                            connectionManager.telescopeOnCoordSetSlew.setDesiredValue(Constants.SwitchStatus.OFF);
                            connectionManager.telescopeOnCoordSetSync.setDesiredValue(Constants.SwitchStatus.OFF);
                            connectionManager.telescopeCoordRA.setDesiredValue(coordinates.getRATelescopeFormat());
                            connectionManager.telescopeCoordDec.setDesiredValue(coordinates.getDecTelescopeFormat());
                            connectionManager.updateProperties(connectionManager.telescopeOnCoordSetP, connectionManager.telescopeCoordP);
                            Snackbar.make(rootView, R.string.slew_ok, Snackbar.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e(TAG, e.getLocalizedMessage(), e);
                            Snackbar.make(rootView, R.string.sync_slew_error, Snackbar.LENGTH_SHORT).show();
                        }
                    }).setNeutralButton(R.string.sync, (dialog, which) -> {
                try {
                    connectionManager.telescopeOnCoordSetSync.setDesiredValue(Constants.SwitchStatus.ON);
                    connectionManager.telescopeOnCoordSetTrack.setDesiredValue(Constants.SwitchStatus.OFF);
                    connectionManager.telescopeOnCoordSetSlew.setDesiredValue(Constants.SwitchStatus.OFF);
                    connectionManager.telescopeCoordRA.setDesiredValue(coordinates.getRATelescopeFormat());
                    connectionManager.telescopeCoordDec.setDesiredValue(coordinates.getDecTelescopeFormat());
                    connectionManager.updateProperties(connectionManager.telescopeOnCoordSetP, connectionManager.telescopeCoordP);
                    Snackbar.make(rootView, R.string.sync_ok, Snackbar.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);
                    Snackbar.make(rootView, R.string.sync_slew_error, Snackbar.LENGTH_SHORT).show();
                }
            });
        }
        builder.setNegativeButton(android.R.string.cancel, null).setIcon(R.drawable.navigation).show();
    }

    public void switchCoords(View v) {
        useAltAz = !useAltAz;
    }

    private void setPointingText(Pointing pointing) {
        if (useAltAz) {
            pointingText.setText(HorizontalCoordinates.getInstance(pointing.getLineOfSight(),
                    model.getLocation(), Calendar.getInstance()).toStringArcmin());
        } else {
            pointingText.setText(EquatorialCoordinates.getInstance(pointing.getLineOfSight()).toStringArcmin());
        }
    }

    /**
     * Passed to the renderer to get per-frame updates from the model.
     *
     * @author John Taylor
     */
    private class RendererModelUpdateClosure extends AbstractUpdateClosure {

        RendererModelUpdateClosure() {
            model.setHorizontalRotation(preferences.getBoolean(ApplicationConstants.ROTATE_HORIZON_PREF, false));
        }

        @Override
        public void run() {
            Pointing pointing = model.getPointing();
            if (pointingText != null)
                pointingText.post(() -> SkyMapActivity.this.setPointingText(pointing));

            rendererController.queueSetViewOrientation(
                    pointing.getLineOfSightX(), pointing.getLineOfSightY(), pointing.getLineOfSightZ(),
                    pointing.getPerpendicularX(), pointing.getPerpendicularY(), pointing.getPerpendicularZ());

            Vector3 up = model.getPhoneUpDirection();
            rendererController.queueTextAngle((float) Math.atan2(up.x, up.y));
            rendererController.queueViewerUpDirection(model.getZenith().copy());

            rendererController.queueFieldOfView(model.getFieldOfView());
        }
    }
}