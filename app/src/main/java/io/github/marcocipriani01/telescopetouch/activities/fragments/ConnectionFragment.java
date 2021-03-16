/*
 * Copyright 2020 Marco Cipriani (@marcocipriani01)
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.NSDHelper;
import io.github.marcocipriani01.telescopetouch.ProUtils;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.activities.MainActivity;
import io.github.marcocipriani01.telescopetouch.activities.WebManagerActivity;
import io.github.marcocipriani01.telescopetouch.activities.dialogs.NewServerDialog;
import io.github.marcocipriani01.telescopetouch.activities.util.ImprovedSpinnerListener;
import io.github.marcocipriani01.telescopetouch.activities.views.SameSelectionSpinner;
import io.github.marcocipriani01.telescopetouch.indi.ConnectionManager;

import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;
import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.nsdHelper;
import static io.github.marcocipriani01.telescopetouch.activities.ServersActivity.getServers;

/**
 * The main screen of the application, which manages the connection.
 *
 * @author marcocipriani01
 */
public class ConnectionFragment extends ActionFragment implements ConnectionManager.ManagerListener,
        NSDHelper.NSDListener, Toolbar.OnMenuItemClickListener, NewServerDialog.Callback {

    private static int selectedSpinnerItem = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private Button connectionButton;
    private SameSelectionSpinner serversSpinner;
    private EditText portEditText;
    private LogAdapter logAdapter;
    private CheckBox connectDevicesBox;
    private RecyclerView logsList;

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        View rootView = inflater.inflate(R.layout.fragment_connection, container, false);
        setHasOptionsMenu(true);
        logAdapter = new LogAdapter(context);
        logsList = rootView.findViewById(R.id.logs_recycler);
        logsList.setAdapter(logAdapter);
        logsList.setLayoutManager(new LinearLayoutManager(context));
        logsList.setItemAnimator(new LogsAnimator());

        connectionButton = rootView.findViewById(R.id.connect_button);
        connectDevicesBox = rootView.findViewById(R.id.connect_all_checkbox);
        // PRO
        if (ProUtils.isPro) {
            boolean autoConnectDev = preferences.getBoolean(ApplicationConstants.AUTO_CONNECT_DEVICES_PREF, false);
            connectDevicesBox.setChecked(autoConnectDev);
            connectDevicesBox.setSelected(autoConnectDev);
            Toolbar toolbar = rootView.findViewById(R.id.connection_fragment_toolbar);
            if (toolbar != null) toolbar.setTitle(context.getString(R.string.app_name) + " Pro");
        } else {
            connectDevicesBox.setText(context.getString(R.string.connect_all_devices) + " [PRO]");
            connectDevicesBox.setChecked(false);
            connectDevicesBox.setSelected(false);
            connectDevicesBox.setEnabled(false);
        }
        // END PRO
        serversSpinner = rootView.findViewById(R.id.host_spinner);
        loadServers(getServers(preferences));
        portEditText = rootView.findViewById(R.id.port_field);
        portEditText.setText(String.valueOf(preferences.getInt(ApplicationConstants.INDI_PORT_PREF, 7624)));
        final FragmentActivity activity = getActivity();
        connectionButton.setOnClickListener(v -> {
            ((InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(portEditText.getWindowToken(), 0);
            String host = (String) serversSpinner.getSelectedItem();
            if (host == null) {
                requestActionSnack(R.string.unknown_error);
                return;
            }
            if (host.equals(context.getString(R.string.host_add))) {
                serversSpinner.post(() -> serversSpinner.setSelection(0));
                NewServerDialog.show(context, preferences, ConnectionFragment.this);
            } else if (host.equals(context.getString(R.string.host_manage))) {
                serversSpinner.post(() -> serversSpinner.setSelection(0));
                if (activity instanceof MainActivity)
                    ((MainActivity) activity).launchServersActivity();
            } else {
                ConnectionManager.State state = connectionManager.getState();
                if (state == ConnectionManager.State.DISCONNECTED) {
                    if (host.contains("@")) {
                        String[] split = host.split("@");
                        if (split.length == 2) host = split[1];
                    }
                    String portStr = portEditText.getText().toString();
                    int port;
                    if (portStr.equals("")) {
                        port = 7624;
                        portEditText.setText("7624");
                    } else {
                        try {
                            port = Integer.parseInt(portStr);
                        } catch (NumberFormatException e) {
                            requestActionSnack(R.string.invalid_port);
                            return;
                        }
                        if ((port <= 0) || (port >= 0xFFFF)) {
                            requestActionSnack(R.string.invalid_port);
                            return;
                        }
                    }
                    preferences.edit().putInt(ApplicationConstants.INDI_PORT_PREF, port).apply();
                    boolean connectDev = connectDevicesBox.isChecked();
                    preferences.edit().putBoolean(ApplicationConstants.AUTO_CONNECT_DEVICES_PREF, connectDev).apply();
                    connectionManager.connect(host, port, connectDev);
                } else if (state == ConnectionManager.State.CONNECTED) {
                    connectionManager.disconnect();
                }
            }
        });
        new ImprovedSpinnerListener() {
            @Override
            protected void onImprovedItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = parent.getItemAtPosition(pos).toString();
                if (selected.equals(context.getString(R.string.host_add))) {
                    serversSpinner.post(() -> serversSpinner.setSelection(0));
                    NewServerDialog.show(context, preferences, ConnectionFragment.this);
                } else if (selected.equals(context.getString(R.string.host_manage))) {
                    serversSpinner.post(() -> serversSpinner.setSelection(0));
                    if (activity instanceof MainActivity)
                        ((MainActivity) activity).launchServersActivity();
                }
            }
        }.attach(serversSpinner);

        refreshUi(connectionManager.getState());
        connectionManager.addManagerListener(this);
        nsdHelper.setListener(this);
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.connection, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_open_browser) {
            String host = (String) serversSpinner.getSelectedItem();
            if (host == null) {
                requestActionSnack(R.string.unknown_error);
                return super.onOptionsItemSelected(item);
            }
            if (host.contains("@")) {
                String[] split = host.split("@");
                if (split.length == 2) host = split[1];
            }
            if (host.equals(context.getString(R.string.host_add)) || host.equals(context.getString(R.string.host_manage))) {
                requestActionSnack(R.string.select_host_first);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + host)));
            }
            return true;
        } else if (itemId == R.id.menu_indi_web) {
            String host = (String) serversSpinner.getSelectedItem();
            if (host == null) {
                requestActionSnack(R.string.unknown_error);
                return super.onOptionsItemSelected(item);
            }
            if (host.contains("@")) {
                String[] split = host.split("@");
                if (split.length == 2) host = split[1];
            }
            if (host.equals(context.getString(R.string.host_add)) || host.equals(context.getString(R.string.host_manage))) {
                requestActionSnack(R.string.select_host_first);
            } else {
                Intent intent = new Intent(context, WebManagerActivity.class);
                intent.putExtra(WebManagerActivity.INTENT_HOST, host);
                startActivity(intent);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        selectedSpinnerItem = serversSpinner.getSelectedItemPosition();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        nsdHelper.setListener(null);
        connectionManager.removeManagerListener(this);
    }

    @Override
    public void updateConnectionState(ConnectionManager.State state) {
        refreshUi(state);
    }

    @Override
    public void addLog(final ConnectionManager.LogItem log) {
        if (logAdapter != null)
            logAdapter.notifyItemInserted(connectionManager.getLogs().indexOf(log));
        notifyActionChange();
        if (logsList != null) logsList.scrollToPosition(logAdapter.getItemCount() - 1);
    }

    @Override
    public void deviceLog(final ConnectionManager.LogItem log) {
        if (logAdapter != null) logAdapter.notifyDataSetChanged();
        notifyActionChange();
        if (logsList != null) logsList.scrollToPosition(logAdapter.getItemCount() - 1);
    }

    private void refreshUi(ConnectionManager.State state) {
        switch (state) {
            case CONNECTED: {
                connectionButton.setText(context.getString(R.string.disconnect));
                connectionButton.setEnabled(true);
                serversSpinner.setEnabled(false);
                portEditText.setEnabled(false);
                break;
            }
            case DISCONNECTED: {
                connectionButton.setText(context.getString(R.string.connect));
                connectionButton.setEnabled(true);
                serversSpinner.setEnabled(true);
                portEditText.setEnabled(true);
                break;
            }
            case BUSY: {
                connectionButton.setText(context.getString(R.string.connecting));
                connectionButton.setEnabled(false);
                serversSpinner.setEnabled(false);
                portEditText.setEnabled(false);
                break;
            }
        }
    }

    @Override
    public void loadServers(ArrayList<String> servers) {
        if (nsdHelper.isAvailable()) {
            HashMap<String, String> services = nsdHelper.getDiscoveredServices();
            for (String name : services.keySet()) {
                String ip = services.get(name);
                if (ip != null) servers.add(name.replace("@", "") + "@" + ip);
            }
        }
        servers.add(context.getString(R.string.host_add));
        servers.add(context.getString(R.string.host_manage));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, servers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serversSpinner.setAdapter(adapter);
        if (adapter.getCount() > selectedSpinnerItem)
            serversSpinner.setSelection(selectedSpinnerItem);
    }

    @Override
    public void onNSDChange() {
        handler.post(() -> loadServers(getServers(preferences)));
    }

    @Override
    public boolean isActionEnabled() {
        return !connectionManager.getLogs().isEmpty();
    }

    @Override
    public int getActionDrawable() {
        return R.drawable.clear;
    }

    @Override
    public void run() {
        if (logAdapter == null) {
            connectionManager.getLogs().clear();
        } else {
            List<ConnectionManager.LogItem> logs = connectionManager.getLogs();
            Iterator<ConnectionManager.LogItem> iterator = logs.iterator();
            while (iterator.hasNext()) {
                ConnectionManager.LogItem next = iterator.next();
                int i = logs.indexOf(next);
                iterator.remove();
                logAdapter.notifyItemRemoved(i);
            }
        }
        notifyActionChange();
    }

    /**
     * {@code ArrayAdapter} for logs.
     *
     * @author marcocipriani01
     */
    private static class LogAdapter extends RecyclerView.Adapter<LogViewHolder> {

        private final LayoutInflater inflater;

        LogAdapter(Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new LogViewHolder(inflater.inflate(R.layout.logs_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            ConnectionManager.LogItem log = connectionManager.getLogs().get(position);
            holder.log.setText(log.getLog());
            holder.timestamp.setText(log.getTimestamp());
        }

        @Override
        public int getItemCount() {
            return connectionManager.getLogs().size();
        }
    }

    private static class LogViewHolder extends RecyclerView.ViewHolder {

        TextView log, timestamp;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            log = itemView.findViewById(R.id.logs_item1);
            timestamp = itemView.findViewById(R.id.logs_item2);
        }
    }

    private static class LogsAnimator extends SimpleItemAnimator {

        @Override
        public boolean animateRemove(RecyclerView.ViewHolder holder) {
            holder.itemView.animate().alpha(0f).translationX(-50f);
            return false;
        }

        @Override
        public boolean animateAdd(RecyclerView.ViewHolder holder) {
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationX(50f);
            holder.itemView.animate().alpha(1f).translationX(0f);
            return false;
        }

        @Override
        public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            return false;
        }

        @Override
        public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder,
                                     int fromLeft, int fromTop, int toLeft, int toTop) {
            return false;
        }

        @Override
        public void runPendingAnimations() {

        }

        @Override
        public void endAnimation(@NonNull RecyclerView.ViewHolder item) {

        }

        @Override
        public void endAnimations() {

        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }
}