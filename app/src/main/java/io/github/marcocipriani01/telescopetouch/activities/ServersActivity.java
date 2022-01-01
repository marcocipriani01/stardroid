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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.woxthebox.draglistview.DragListView;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.activities.dialogs.NewServerDialog;
import io.github.marcocipriani01.telescopetouch.activities.util.DarkerModeManager;
import io.github.marcocipriani01.telescopetouch.activities.util.ServersItemAdapter;

/**
 * Activity to manage the list of servers.
 *
 * @author marcocipriani01
 */
public class ServersActivity extends AppCompatActivity implements NewServerDialog.Callback {

    private final static Gson gson = new Gson();
    private final static Type STRING_ARRAY_TYPE = new TypeToken<ArrayList<String>>() {
    }.getType();
    private DragListView serversListView;
    private SharedPreferences preferences;
    private DarkerModeManager darkerModeManager;
    private CoordinatorLayout coordinator;

    public static ArrayList<String> getServers(SharedPreferences preferences) {
        String pref = preferences.getString(ApplicationConstants.INDI_SERVERS_PREF, null);
        if (pref == null) return new ArrayList<>();
        ArrayList<String> servers = null;
        try {
            servers = gson.fromJson(pref, STRING_ARRAY_TYPE);
        } catch (Exception e) {
            Log.e("ServersActivity", "Gson error.", e);
        }
        if (servers == null) servers = new ArrayList<>();
        return servers;
    }

    public static void saveServers(SharedPreferences preferences, ArrayList<String> list) {
        preferences.edit().putString(ApplicationConstants.INDI_SERVERS_PREF, gson.toJson(list, STRING_ARRAY_TYPE)).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        darkerModeManager = new DarkerModeManager(this, null, PreferenceManager.getDefaultSharedPreferences(this));
        setTheme(darkerModeManager.getPref() ? R.style.DarkerAppTheme : R.style.AppTheme);
        setContentView(R.layout.activity_servers);
        coordinator = findViewById(R.id.servers_coordinator);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        findViewById(R.id.addServerFab).setOnClickListener(v -> {
            saveFromListView();
            NewServerDialog.show(this, preferences, this);
        });
        serversListView = findViewById(R.id.serversList);
        serversListView.setLayoutManager(new LinearLayoutManager(this));
        serversListView.setCanDragHorizontally(false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        loadServers(getServers(preferences));
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Snackbar.make(coordinator, R.string.servers_list_snack_msg, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        super.onOptionsItemSelected(item);
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        darkerModeManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveFromListView();
        darkerModeManager.stop();
    }

    private void saveFromListView() {
        preferences.edit().putString(ApplicationConstants.INDI_SERVERS_PREF,
                gson.toJson(((ServersItemAdapter) serversListView.getAdapter()).getItemList(), STRING_ARRAY_TYPE)).apply();
    }

    @Override
    public void requestActionSnack(int msg) {
        Snackbar.make(coordinator, msg, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void loadServers(ArrayList<String> servers) {
        serversListView.setAdapter(new ServersItemAdapter(servers, R.layout.servers_list_item, R.id.listview_drag, false) {
            @Override
            public void onItemLongClicked(final TextView view) {
                new AlertDialog.Builder(ServersActivity.this)
                        .setTitle(R.string.host_manage)
                        .setIcon(R.drawable.delete)
                        .setMessage(R.string.remove_server)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                            List<String> list = getItemList();
                            for (int i = 0; i < list.size(); i++) {
                                if (list.get(i) == view.getText()) {
                                    removeItem(i);
                                    break;
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null).show();
            }
        }, false);
    }
}