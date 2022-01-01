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

import static java.lang.Math.asin;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.VIZIER_WELCOME;
import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;

import org.indilib.i4j.Constants;
import org.indilib.i4j.client.INDIValueException;

import java.util.Calendar;

import in.myinnos.alphabetsindexfastscrollrecycler.IndexFastScrollRecyclerView;
import io.github.marcocipriani01.graphview.DefaultLabelFormatter;
import io.github.marcocipriani01.graphview.GraphView;
import io.github.marcocipriani01.graphview.GridLabelRenderer;
import io.github.marcocipriani01.graphview.Viewport;
import io.github.marcocipriani01.graphview.series.DataPoint;
import io.github.marcocipriani01.graphview.series.LineGraphSeries;
import io.github.marcocipriani01.livephotoview.PhotoView;
import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.ProUtils;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.activities.views.AladinView;
import io.github.marcocipriani01.telescopetouch.astronomy.EquatorialCoordinates;
import io.github.marcocipriani01.telescopetouch.astronomy.HeliocentricCoordinates;
import io.github.marcocipriani01.telescopetouch.astronomy.Planet;
import io.github.marcocipriani01.telescopetouch.astronomy.StarsPrecession;
import io.github.marcocipriani01.telescopetouch.astronomy.TimeUtils;
import io.github.marcocipriani01.telescopetouch.catalog.Catalog;
import io.github.marcocipriani01.telescopetouch.catalog.CatalogArrayAdapter;
import io.github.marcocipriani01.telescopetouch.catalog.CatalogEntry;
import io.github.marcocipriani01.telescopetouch.catalog.DSOEntry;
import io.github.marcocipriani01.telescopetouch.catalog.PlanetEntry;
import io.github.marcocipriani01.telescopetouch.catalog.StarEntry;
import io.github.marcocipriani01.telescopetouch.sensors.LocationHelper;

/**
 * Allows the user to look for an astronomical object and slew the telescope.
 */
public class GoToFragment extends ActionFragment implements SearchView.OnQueryTextListener,
        Catalog.CatalogLoadingListener, CatalogArrayAdapter.CatalogItemListener, Toolbar.OnMenuItemClickListener {

    private static final String TAG = TelescopeTouchApp.getTag(GoToFragment.class);
    private static final Catalog catalog = new Catalog();
    private static String requestedSearch = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private CatalogArrayAdapter entriesAdapter;
    private MenuItem searchMenu;
    private SearchView searchView;
    private IndexFastScrollRecyclerView list;
    private ProgressBar progressBar;
    private TextView emptyLabel;
    private LocationHelper locationHelper;
    private Location location = null;
    private boolean searching = false;
    private MenuItem aboutMenu;

    public static void setRequestedSearch(String query) {
        requestedSearch = query;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_goto, container, false);
        setHasOptionsMenu(true);
        searchMenu = rootView.<Toolbar>findViewById(R.id.goto_toolbar).getMenu().add(R.string.mount_goto);
        searchMenu.setIcon(R.drawable.search);
        searchMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        searchMenu.setVisible(catalog.isReady());
        searchView = new SearchView(context);
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnQueryTextListener(this);
        searchMenu.setActionView(searchView);
        searchView.setImeOptions(searchView.getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searching = false;
        searchView.setOnSearchClickListener(v -> {
            searching = true;
            notifyActionChange();
        });
        searchView.setOnCloseListener(() -> {
            searching = false;
            notifyActionChange();
            return false;
        });
        list = rootView.findViewById(R.id.goto_database_list);
        entriesAdapter = new CatalogArrayAdapter(context, catalog);
        list.setAdapter(entriesAdapter);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setIndexBarColor(R.color.darkColor);
        list.setIndexBarStrokeVisibility(false);
        entriesAdapter.setCatalogItemListener(this);
        emptyLabel = rootView.findViewById(R.id.goto_empy_label);
        progressBar = rootView.findViewById(R.id.goto_loading);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        locationHelper = new LocationHelper(getActivity()) {
            @Override
            protected void onLocationOk(Location location) {
                GoToFragment.this.location = location;
                entriesAdapter.setLocation(location);
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
        if (!preferences.getBoolean(VIZIER_WELCOME, false))
            vizierDialog();
        return rootView;
    }

    @Override
    public void onPermissionAcquired(String permission) {
        locationHelper.restartLocation();
    }

    private void vizierDialog() {
        new AlertDialog.Builder(context).setView(R.layout.dialog_vizier_welcome)
                .setPositiveButton(R.string.continue_button, (dialog, which) -> preferences.edit().putBoolean(VIZIER_WELCOME, true).apply()).show();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        aboutMenu = menu.add(R.string.about_vizier_menu);
        aboutMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item == aboutMenu) {
            vizierDialog();
        }
        return false;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (entriesAdapter != null)
            entriesAdapter.detachPref();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (catalog.isReady()) {
            setListShown(true);
        } else {
            setListShown(false);
            catalog.setListener(this);
            // List loading
            if (!catalog.isLoading())
                new Thread(() -> catalog.load(context.getResources())).start();
        }
        locationHelper.start();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (context instanceof Activity && ((Activity) context).isInMultiWindowMode())
                list.setIndexBarVisibility(false);
        }

        Activity activity = getActivity();
        if (activity != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            float y = metrics.heightPixels / metrics.ydpi, x = metrics.widthPixels / metrics.xdpi;
            if ((Math.sqrt(x * x + y * y) < 6.5) &&
                    (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE))
                list.setIndexBarVisibility(false);
        }
        handler.postDelayed(this::maybeStartIntentSearch, 300);
    }

    @Override
    public void onStop() {
        super.onStop();
        catalog.setListener(null);
        locationHelper.stop();
        list.stopNestedScroll();
        list.stopScroll();
    }

    @Override
    public boolean isActionEnabled() {
        return catalog.isReady() && (!searching);
    }

    @Override
    public int getActionDrawable() {
        return R.drawable.filter;
    }

    @Override
    public void run() {
        final boolean[] choices = {entriesAdapter.isShowStars(),
                entriesAdapter.isShowDso(),
                entriesAdapter.planetsShown(),
                entriesAdapter.isOnlyAboveHorizon()};
        new AlertDialog.Builder(context).setTitle(R.string.database_filter)
                .setIcon(R.drawable.filter)
                .setMultiChoiceItems(R.array.database_filter_elements, choices,
                        (dialog, which, isChecked) -> choices[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    entriesAdapter.setVisibility(choices);
                    if (entriesAdapter.isEmpty()) {
                        emptyLabel.setVisibility(View.VISIBLE);
                        list.setVisibility(View.GONE);
                    } else {
                        list.updateSections();
                        list.setVisibility(View.VISIBLE);
                        emptyLabel.setVisibility(View.GONE);
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void setListShown(boolean b) {
        list.setVisibility(b ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(b ? View.GONE : View.VISIBLE);
        notifyActionChange();
    }

    private void maybeStartIntentSearch() {
        if (catalog.isReady() && (requestedSearch != null) && (searchView != null) && isVisible()) {
            searchView.setIconified(false);
            searchView.setQuery(requestedSearch, false);
            searchView.clearFocus();
            searching = true;
            notifyActionChange();
        }
    }

    /**
     * Called when the user changes the search string.
     *
     * @param newText the new query.
     * @return {@code false}, because the action is being handled by this listener.
     */
    @Override
    public boolean onQueryTextChange(String newText) {
        if (catalog.isReady()) entriesAdapter.filter(newText.trim().toLowerCase());
        if (requestedSearch != null) {
            if (entriesAdapter.visibleItemsCount() == 1) onListItemClick0(0);
            requestedSearch = null;
        }
        if (entriesAdapter.isEmpty()) {
            emptyLabel.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
        } else {
            list.setVisibility(View.VISIBLE);
            emptyLabel.setVisibility(View.GONE);
        }
        return false;
    }

    /**
     * Called when the user submits the query. Nothing to do since it is done in {@link #onQueryTextChange(String)}
     *
     * @param query the new query. Ignored.
     * @return always {@code false}.
     */
    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public void onCatalogItemClick(View v) {
        int position = list.getChildLayoutPosition(v);
        if (position != -1) {
            onListItemClick0(position);
            searchView.clearFocus();
        }
    }

    private void onListItemClick0(int position) {
        final CatalogEntry entry = entriesAdapter.getEntryAt(position);
        AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle(entry.getName());
        EquatorialCoordinates coordinates = entry.getCoordinates();

        Spannable description = entry.createDescription(context, location);
        // PRO
        if (ProUtils.isPro) {
            // END PRO
            if (entry instanceof PlanetEntry) {
                Planet planet = ((PlanetEntry) entry).getPlanet();
                View rootView = View.inflate(context, R.layout.dialog_planet_details, null);
                rootView.<TextView>findViewById(R.id.details_text).setText(description);
                PhotoView photoView = rootView.findViewById(R.id.details_image);
                photoView.setImageResource(planet.getGalleryResourceId());
                createDetailsBox(rootView, description, photoView, null, planet);
                builder.setView(rootView);
            } else if (AladinView.isSupported(preferences)) {
                View rootView = View.inflate(context, R.layout.dialog_object_details, null);
                rootView.<TextView>findViewById(R.id.details_text).setText(description);
                TextView noInternet = rootView.findViewById(R.id.details_no_internet);
                AladinView aladinView = rootView.findViewById(R.id.details_aladin);
                if (internetAvailable()) {
                    aladinView.setHeight(600);
                    aladinView.setAladinListener(new AladinView.AladinListener() {
                        @Override
                        public void onAladinError() {
                            aladinView.setVisibility(View.GONE);
                            noInternet.setVisibility(View.VISIBLE);
                        }
                    });
                    aladinView.start(coordinates);
                } else {
                    aladinView.setVisibility(View.GONE);
                    noInternet.setVisibility(View.VISIBLE);
                }
                createDetailsBox(rootView, description, rootView.findViewById(R.id.details_aladin_container), coordinates, null);
                builder.setView(rootView);
            } else if (location != null) {
                GraphView graph = new GraphView(context);
                initGraph(graph, coordinates, null);
                ScrollView scrollView = new ScrollView(context);
                scrollView.addView(graph);
                builder.setView(graph);
            }
            // PRO
        } else {
            builder.setMessage(description);
            Button proButton = new AppCompatButton(context);
            proButton.setTextAppearance(context, R.style.TextAppearance_AppCompat_Medium);
            proButton.setText(R.string.goto_dialog_pro);
            proButton.setOnClickListener(v -> ProUtils.playStore(context));
            proButton.setTextColor(context.getResources().getColor(R.color.colorAccent));
            builder.setView(proButton);
        }
        // END PRO

        // Only display buttons if the telescope is ready
        if ((connectionManager.telescopeCoordP != null) && (connectionManager.telescopeOnCoordSetP != null)) {
            builder.setPositiveButton(R.string.go_to, (dialog, which) -> {
                try {
                    connectionManager.telescopeOnCoordSetTrack.setDesiredValue(Constants.SwitchStatus.ON);
                    connectionManager.telescopeOnCoordSetSlew.setDesiredValue(Constants.SwitchStatus.OFF);
                    connectionManager.telescopeOnCoordSetSync.setDesiredValue(Constants.SwitchStatus.OFF);
                    setCoordinatesMaybePrecess(entry, coordinates);
                    connectionManager.updateProperties(connectionManager.telescopeOnCoordSetP, connectionManager.telescopeCoordP);
                    requestActionSnack(R.string.slew_ok);
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);
                    requestActionSnack(R.string.sync_slew_error);
                }
                searchView.clearFocus();
            });
            builder.setNeutralButton(R.string.sync, (dialog, which) -> {
                try {
                    connectionManager.telescopeOnCoordSetSync.setDesiredValue(Constants.SwitchStatus.ON);
                    connectionManager.telescopeOnCoordSetTrack.setDesiredValue(Constants.SwitchStatus.OFF);
                    connectionManager.telescopeOnCoordSetSlew.setDesiredValue(Constants.SwitchStatus.OFF);
                    setCoordinatesMaybePrecess(entry, coordinates);
                    connectionManager.updateProperties(connectionManager.telescopeOnCoordSetP, connectionManager.telescopeCoordP);
                    requestActionSnack(R.string.sync_ok);
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);
                    requestActionSnack(R.string.sync_slew_error);
                }
                searchView.clearFocus();
            });
        }

        builder.setNegativeButton(android.R.string.cancel, null)
                .setIcon(entry.getIconResource()).show();
    }

    private boolean internetAvailable() {
        ConnectivityManager connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager.class);
        if (connectivityManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Network network = connectivityManager.getActiveNetwork();
            return (network != null);
        } else {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return (activeNetwork != null) && activeNetwork.isConnectedOrConnecting();
        }
    }

    private void createDetailsBox(View rootView, Spannable description, View preview, EquatorialCoordinates dsoCoord, Planet planet) {
        rootView.<TextView>findViewById(R.id.details_text).setText(description);
        FrameLayout graphContainer = rootView.findViewById(R.id.details_graph_container);
        GraphView graph = rootView.findViewById(R.id.details_graph);
        if (location == null) {
            rootView.<TextView>findViewById(R.id.details_no_location).setVisibility(View.VISIBLE);
            graph.setVisibility(View.GONE);
        } else {
            initGraph(graph, dsoCoord, planet);
        }

        TabLayout tabLayout = rootView.findViewById(R.id.details_tabs);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                preferences.edit().putInt(ApplicationConstants.GOTO_DETAILS_LAST_TAB, pos).apply();
                if (pos == 0) {
                    preview.setVisibility(View.VISIBLE);
                    graphContainer.setVisibility(View.GONE);
                } else {
                    preview.setVisibility(View.GONE);
                    graphContainer.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        if (preferences.getInt(ApplicationConstants.GOTO_DETAILS_LAST_TAB, 0) == 1)
            tabLayout.selectTab(tabLayout.getTabAt(1));
    }

    private void initGraph(GraphView graph, EquatorialCoordinates dsoCoord, Planet planet) {
        LineGraphSeries<DataPoint> series = new LineGraphSeries<>();
        Calendar calendar = Calendar.getInstance();
        long now = calendar.getTimeInMillis();
        calendar.add(Calendar.DATE, 1);
        long tomorrow = calendar.getTimeInMillis();
        double latitude = location.getLatitude(),
                longitude = location.getLongitude();
        if (dsoCoord == null) {
            for (long millis = now; millis < tomorrow; millis += 120000) {
                calendar.setTimeInMillis(millis);
                EquatorialCoordinates coordinates = planet.getEquatorialCoordinates(calendar,
                        HeliocentricCoordinates.getInstance(Planet.Sun, calendar));
                double latRadians = toRadians(latitude),
                        dec = toRadians(coordinates.dec);
                series.appendData(new DataPoint((double) millis,
                        toDegrees(asin(sin(dec) * sin(latRadians) + cos(dec) * cos(latRadians) *
                                cos(toRadians(TimeUtils.meanSiderealTime(calendar, longitude) - coordinates.ra))))), false, 720);
            }
        } else {
            for (long millis = now; millis < tomorrow; millis += 120000) {
                calendar.setTimeInMillis(millis);
                double latRadians = toRadians(latitude),
                        dec = toRadians(dsoCoord.dec);
                series.appendData(new DataPoint((double) millis,
                        toDegrees(asin(sin(dec) * sin(latRadians) + cos(dec) * cos(latRadians) *
                                cos(toRadians(TimeUtils.meanSiderealTime(calendar, longitude) - dsoCoord.ra))))), false, 720);
            }
        }
        graph.addSeries(series);
        GridLabelRenderer gridLabel = graph.getGridLabelRenderer();
        gridLabel.setLabelFormatter(new TimeFormatter());
        gridLabel.setNumHorizontalLabels(3);
        gridLabel.setNumVerticalLabels(6);
        gridLabel.setHorizontalLabelsAngle(45);
        Viewport viewport = graph.getViewport();
        viewport.setMinX(now);
        viewport.setMaxX(tomorrow);
        viewport.setXAxisBoundsManual(true);
        viewport.setYAxisBoundsManual(true);
        viewport.setScalable(true);
    }

    private void setCoordinatesMaybePrecess(CatalogEntry entry, EquatorialCoordinates coordinates) throws INDIValueException {
        if ((preferences.getBoolean(ApplicationConstants.COMPENSATE_PRECESSION_PREF, true)) &&
                ((entry instanceof StarEntry) || (entry instanceof DSOEntry))) {
            EquatorialCoordinates precessed = StarsPrecession.precess(Calendar.getInstance(), coordinates);
            connectionManager.telescopeCoordRA.setDesiredValue(precessed.getRATelescopeFormat());
            connectionManager.telescopeCoordDec.setDesiredValue(precessed.getDecTelescopeFormat());
        } else {
            connectionManager.telescopeCoordRA.setDesiredValue(coordinates.getRATelescopeFormat());
            connectionManager.telescopeCoordDec.setDesiredValue(coordinates.getDecTelescopeFormat());
        }
    }

    @Override
    public void onLoaded(boolean success) {
        handler.post(() -> {
            if (success) {
                if (searchMenu != null) searchMenu.setVisible(true);
                entriesAdapter.reloadCatalog();
                setListShown(true);
                if (isResumed()) maybeStartIntentSearch();
            } else if (isVisible()) {
                new AlertDialog.Builder(context)
                        .setTitle(R.string.catalog_manager)
                        .setMessage(R.string.catalog_error)
                        .setPositiveButton(android.R.string.ok, null)
                        .setIcon(R.drawable.warning)
                        .show();
            }
        });
    }

    private class TimeFormatter extends DefaultLabelFormatter {

        private final java.text.DateFormat formatter;

        TimeFormatter() {
            formatter = DateFormat.getTimeFormat(context);
        }

        @Override
        public String formatLabel(double value, boolean isValueX) {
            if (isValueX) return formatter.format((long) value);
            return super.formatLabel(value, false);
        }
    }
}