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

package io.github.marcocipriani01.telescopetouch.indi;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.indilib.i4j.Constants;
import org.indilib.i4j.client.INDIDevice;
import org.indilib.i4j.client.INDIDeviceListener;
import org.indilib.i4j.client.INDINumberElement;
import org.indilib.i4j.client.INDINumberProperty;
import org.indilib.i4j.client.INDIProperty;
import org.indilib.i4j.client.INDIPropertyListener;
import org.indilib.i4j.client.INDIServerConnection;
import org.indilib.i4j.client.INDIServerConnectionListener;
import org.indilib.i4j.client.INDISwitchElement;
import org.indilib.i4j.client.INDISwitchProperty;
import org.indilib.i4j.properties.INDIStandardElement;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.astronomy.EquatorialCoordinates;
import io.github.marcocipriani01.telescopetouch.layers.TelescopeLayer;

/**
 * Manages an {@link INDIServerConnection} object, listens to INDI messages and notifies listeners.
 *
 * @author marcocipriani01
 */
public class ConnectionManager implements INDIServerConnectionListener, INDIDeviceListener, INDIPropertyListener {

    private static final String TAG = TelescopeTouchApp.getTag(ConnectionManager.class);
    public final EquatorialCoordinates telescopeCoordinates = new EquatorialCoordinates();
    public final Map<INDIDevice, INDICamera> indiCameras = new HashMap<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Set<ManagerListener> managerListeners = new HashSet<>();
    private final Set<INDIServerConnectionListener> indiListeners = new HashSet<>();
    private final List<LogItem> logs = new ArrayList<>();
    private final HandlerThread indiThread = new HandlerThread("INDI manager thread");
    // Telescope
    public volatile String telescopeName = null;
    public volatile INDINumberProperty telescopeCoordP = null;
    public volatile INDINumberElement telescopeCoordRA = null;
    public volatile INDINumberElement telescopeCoordDec = null;
    public volatile INDISwitchProperty telescopeOnCoordSetP = null;
    public volatile INDISwitchElement telescopeOnCoordSetSync = null;
    public volatile INDISwitchElement telescopeOnCoordSetSlew = null;
    public volatile INDISwitchElement telescopeOnCoordSetTrack = null;
    private Handler indiHandler;
    // Formatting
    private java.text.DateFormat dateFormat = null;
    private java.text.DateFormat timeFormat = null;
    /**
     * INDI Server connection
     */
    private volatile INDIServerConnection indiConnection;
    private volatile boolean busy = false;
    private SharedPreferences preferences;
    private Context context;
    private Resources resources;
    private volatile boolean connectDevices = false;
    private final Runnable stopAutoDeviceConnection = () -> connectDevices = false;

    public void post(@NonNull Runnable r) {
        indiHandler.post(r);
    }

    public void postDelayed(@NonNull Runnable r, long delayMillis) {
        indiHandler.postDelayed(r, delayMillis);
    }

    public void updateProperties(INDIProperty<?>... properties) {
        indiHandler.post(() -> {
            try {
                for (INDIProperty<?> prop : properties) {
                    prop.sendChangesToDriver();
                }
            } catch (Exception e) {
                Log.e(TAG, "Property update error!", e);
                TelescopeTouchApp.connectionManager.log(e);
            }
        });
    }

    public List<LogItem> getLogs() {
        return logs;
    }

    public void init(Context context) {
        this.context = context;
        this.resources = context.getResources();
        dateFormat = DateFormat.getDateFormat(context);
        timeFormat = DateFormat.getTimeFormat(context);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putBoolean(TelescopeLayer.PREFERENCE_ID, false).apply();
        indiThread.start();
        indiHandler = new Handler(indiThread.getLooper());
    }

    public State getState() {
        return busy ? State.BUSY :
                (((indiConnection != null) && indiConnection.isConnected()) ? State.CONNECTED : State.DISCONNECTED);
    }

    public boolean isConnected() {
        return (!busy) && (indiConnection != null) && indiConnection.isConnected();
    }

    /**
     * @param state the new state of the Connection button.
     */
    public void updateState(State state) {
        uiHandler.post(() -> {
            synchronized (managerListeners) {
                for (ManagerListener listener : managerListeners) {
                    listener.updateConnectionState(state);
                }
            }
        });
    }

    /**
     * @return the connection. May be {@code null} if the connection doesn't exist.
     */
    public INDIServerConnection getIndiConnection() {
        return indiConnection;
    }

    /**
     * Connects to the driver
     *
     * @param host the host / IP address of the INDI server
     * @param port the port of the INDI server
     */
    public void connect(String host, int port, boolean connectDevices) {
        if (getState() == State.DISCONNECTED) {
            updateState(State.BUSY);
            busy = true;
            log(resources.getString(R.string.try_to_connect) + host + ":" + port);
            indiHandler.post(() -> {
                try {
                    indiConnection = new INDIServerConnection(host, port);
                    indiConnection.addINDIServerConnectionListener(this);
                    synchronized (indiListeners) {
                        for (INDIServerConnectionListener l : indiListeners) {
                            indiConnection.addINDIServerConnectionListener(l);
                        }
                    }
                    indiConnection.connect();
                    this.connectDevices = connectDevices;
                    indiHandler.removeCallbacks(stopAutoDeviceConnection);
                    if (this.connectDevices)
                        indiHandler.postDelayed(stopAutoDeviceConnection, 20000);
                    indiConnection.askForDevices();
                    busy = false;
                    updateState(State.CONNECTED);
                    log(resources.getString(R.string.connected));
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);
                    busy = false;
                    updateState(State.DISCONNECTED);
                    log(e.getLocalizedMessage());
                }
            });
        } else {
            log(resources.getString(R.string.connection_busy_2));
        }
    }

    /**
     * Add the given message to the logs.
     *
     * @param message a new log.
     */
    public void log(String message) {
        uiHandler.post(() -> {
            Date now = new Date();
            LogItem log = new LogItem(message, dateFormat.format(now) + " " + timeFormat.format(now));
            logs.add(log);
            synchronized (managerListeners) {
                for (ManagerListener listener : managerListeners) {
                    listener.addLog(log);
                }
            }
        });
    }

    public void log(Exception e) {
        String message = e.getLocalizedMessage();
        if ((message == null) || message.equals("?")) {
            log(resources.getString(R.string.unknown_exception));
        } else {
            log(resources.getString(R.string.error) + " " + message);
        }
    }

    /**
     * Add the given message to the logs.
     *
     * @param message a new log.
     */
    public void log(String message, INDIDevice device) {
        uiHandler.post(() -> {
            Date now = new Date();
            LogItem log = new LogItem(message, dateFormat.format(now) + " " + timeFormat.format(now), device);
            for (int i = 0, logsSize = logs.size(); i < logsSize; i++) {
                if (logs.get(i).getDevice() == log.getDevice()) {
                    logs.remove(i);
                    break;
                }
            }
            logs.add(log);
            synchronized (managerListeners) {
                for (ManagerListener listener : managerListeners) {
                    listener.deviceLog(log);
                }
            }
        });
    }

    /**
     * @param listener a new {@link ManagerListener}
     */
    public void addManagerListener(ManagerListener listener) {
        synchronized (managerListeners) {
            managerListeners.add(listener);
        }
    }

    /**
     * @param listener a new {@link ManagerListener}
     */
    public void removeManagerListener(ManagerListener listener) {
        synchronized (managerListeners) {
            managerListeners.remove(listener);
        }
    }

    /**
     * Breaks the connection.
     */
    public void disconnect() {
        if (getState() == State.CONNECTED) {
            indiHandler.post(() -> {
                busy = true;
                try {
                    indiConnection.disconnect();
                } catch (Exception e) {
                    log(e.getLocalizedMessage());
                }
                indiConnection = null;
                busy = false;
                updateState(State.DISCONNECTED);
            });
        } else {
            log(resources.getString(R.string.connection_busy));
        }
    }

    /**
     * Add a INDIServerConnectionListener to the connection. If the connection
     * is re-created, the listener will be re-installed
     *
     * @param connectionListener the listener
     */
    public void addINDIListener(INDIServerConnectionListener connectionListener) {
        synchronized (indiListeners) {
            if (indiListeners.add(connectionListener) && (indiConnection != null))
                indiConnection.addINDIServerConnectionListener(connectionListener);
        }
    }

    /**
     * Removes the given listener
     *
     * @param connectionListener the listener
     */
    public void removeINDIListener(INDIServerConnectionListener connectionListener) {
        synchronized (indiListeners) {
            indiListeners.remove(connectionListener);
            if (indiConnection != null)
                indiConnection.removeINDIServerConnectionListener(connectionListener);
        }
    }

    @Override
    public void newDevice(INDIServerConnection connection, INDIDevice device) {
        device.addINDIDeviceListener(this);
        log(resources.getString(R.string.new_device) + " " + device.getName());
    }

    @Override
    public void removeDevice(INDIServerConnection connection, INDIDevice device) {
        device.removeINDIDeviceListener(this);
        log(resources.getString(R.string.device_remove) + " " + device.getName());
        if (device.getName().equals(telescopeName)) clearTelescopeVars();
        synchronized (indiCameras) {
            INDICamera indiCamera = indiCameras.get(device);
            if (indiCamera != null) indiCamera.terminate();
            uiHandler.post(() -> {
                synchronized (managerListeners) {
                    for (ManagerListener listener : managerListeners) {
                        listener.onCamerasListChange();
                    }
                }
            });
        }
    }

    @Override
    public void connectionLost(INDIServerConnection connection) {
        busy = false;
        this.indiConnection = null;
        clearTelescopeVars();
        synchronized (indiCameras) {
            for (INDICamera camera : indiCameras.values()) {
                camera.terminate();
            }
            indiCameras.clear();
        }
        log(resources.getString(R.string.connection_lost));
        uiHandler.post(() -> {
            synchronized (managerListeners) {
                for (ManagerListener listener : managerListeners) {
                    listener.updateConnectionState(State.DISCONNECTED);
                    listener.onConnectionLost();
                }
            }
        });
    }

    @Override
    public void newMessage(INDIServerConnection connection, Date timestamp, String message) {
        log(message);
    }

    @Override
    public void newProperty(INDIDevice device, INDIProperty<?> property) {
        String propName = property.getName();
        if (connectDevices) {
            try {
                if (propName.equals("CONNECTION")) {
                    INDISwitchElement connE = (INDISwitchElement) property.getElement(INDIStandardElement.CONNECT),
                            discE = (INDISwitchElement) property.getElement(INDIStandardElement.DISCONNECT);
                    if ((connE != null) && (discE != null) && (connE.getValue() == Constants.SwitchStatus.OFF)) {
                        connE.setDesiredValue(Constants.SwitchStatus.ON);
                        discE.setDesiredValue(Constants.SwitchStatus.OFF);
                        updateProperties(property);
                        log(String.format(resources.getString(R.string.connecting_device), device.getName()));
                    }
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        INDICamera indiCamera;
        synchronized (indiCameras) {
            indiCamera = indiCameras.get(device);
        }
        if (indiCamera != null) {
            indiCamera.processNewProp(property);
        } else if (INDICamera.isCameraProp(property)) {
            INDICamera camera = new INDICamera(device, context, uiHandler);
            camera.processNewProp(property);
            synchronized (indiCameras) {
                indiCameras.put(device, camera);
            }
            uiHandler.post(() -> {
                synchronized (managerListeners) {
                    for (ManagerListener listener : managerListeners) {
                        listener.onCamerasListChange();
                    }
                }
            });
        } else {
            switch (propName) {
                case "EQUATORIAL_EOD_COORD":
                    if ((property instanceof INDINumberProperty) &&
                            ((telescopeCoordDec = (INDINumberElement) property.getElement(INDIStandardElement.DEC)) != null) &&
                            ((telescopeCoordRA = (INDINumberElement) property.getElement(INDIStandardElement.RA)) != null)) {
                        property.addINDIPropertyListener(this);
                        telescopeCoordP = (INDINumberProperty) property;
                        telescopeName = device.getName();
                        telescopeCoordinates.ra = telescopeCoordRA.getValue() * 15.0;
                        telescopeCoordinates.dec = telescopeCoordDec.getValue();
                        uiHandler.post(() -> preferences.edit().putBoolean(TelescopeLayer.PREFERENCE_ID, true).apply());
                    }
                    break;
                case "ON_COORD_SET":
                    if ((property instanceof INDISwitchProperty) &&
                            ((telescopeOnCoordSetTrack = (INDISwitchElement) property.getElement(INDIStandardElement.TRACK)) != null) &&
                            ((telescopeOnCoordSetSlew = (INDISwitchElement) property.getElement(INDIStandardElement.SLEW)) != null) &&
                            ((telescopeOnCoordSetSync = (INDISwitchElement) property.getElement(INDIStandardElement.SYNC)) != null)) {
                        property.addINDIPropertyListener(this);
                        telescopeOnCoordSetP = (INDISwitchProperty) property;
                    }
                    break;
            }
        }
    }

    @Override
    public void removeProperty(INDIDevice device, INDIProperty<?> property) {
        INDICamera indiCamera;
        synchronized (indiCameras) {
            indiCamera = indiCameras.get(device);
        }
        if (indiCamera != null) {
            if (indiCamera.removeProp(property)) {
                indiCamera.terminate();
                synchronized (indiCameras) {
                    indiCameras.remove(device);
                }
                uiHandler.post(() -> {
                    synchronized (managerListeners) {
                        for (ManagerListener listener : managerListeners) {
                            listener.onCamerasListChange();
                        }
                    }
                });
            }
        } else {
            switch (property.getName()) {
                case "EQUATORIAL_EOD_COORD":
                    telescopeCoordDec = null;
                    telescopeCoordRA = null;
                    telescopeCoordP = null;
                    telescopeName = null;
                    telescopeCoordinates.ra = 0;
                    telescopeCoordinates.dec = 0;
                    uiHandler.post(() -> preferences.edit().putBoolean(TelescopeLayer.PREFERENCE_ID, false).apply());
                    break;
                case "ON_COORD_SET":
                    telescopeCoordP = null;
                    telescopeCoordRA = null;
                    telescopeCoordDec = null;
                    break;
            }
        }
    }

    private void clearTelescopeVars() {
        preferences.edit().putBoolean(TelescopeLayer.PREFERENCE_ID, false).apply();
        telescopeCoordDec = null;
        telescopeCoordRA = null;
        telescopeCoordP = null;
        telescopeOnCoordSetP = null;
        telescopeOnCoordSetSlew = null;
        telescopeOnCoordSetTrack = null;
        telescopeOnCoordSetSync = null;
        telescopeName = null;
        telescopeCoordinates.ra = 0;
        telescopeCoordinates.dec = 0;
    }

    @Override
    public void messageChanged(INDIDevice device) {
        log(device.getName() + ": " + device.getLastMessage(), device);
    }

    @Override
    public void propertyChanged(INDIProperty<?> indiProperty) {
        if ((indiProperty == telescopeCoordP) && (telescopeCoordRA != null) && (telescopeCoordDec != null)) {
            telescopeCoordinates.ra = telescopeCoordRA.getValue() * 15.0;
            telescopeCoordinates.dec = telescopeCoordDec.getValue();
        }
    }

    public enum State {
        DISCONNECTED, CONNECTED, BUSY
    }

    /**
     * This class offers a safe way to update the UI statically instead of keeping in memory Android Widgets,
     * which implement the class {@link Context}.
     *
     * @author marcocipriani01
     */
    public interface ManagerListener {
        default void addLog(LogItem log) {
        }

        default void deviceLog(LogItem log) {
        }

        default void updateConnectionState(State state) {
        }

        default void onConnectionLost() {
        }

        default void onCamerasListChange() {
        }
    }

    /**
     * Represents a single log with its timestamp.
     *
     * @author marcocipriani01
     */
    public static class LogItem {

        private final String log;
        private final String timestamp;
        private INDIDevice device = null;

        LogItem(@NonNull String log, @NonNull String timestamp) {
            this.log = log;
            this.timestamp = timestamp;
        }

        LogItem(@NonNull String log, @NonNull String timestamp, INDIDevice device) {
            this.log = log;
            this.timestamp = timestamp;
            this.device = device;
        }

        public INDIDevice getDevice() {
            return device;
        }

        /**
         * @return the log text.
         */
        public String getLog() {
            return log;
        }

        /**
         * @return the timestamp string.
         */
        public String getTimestamp() {
            return timestamp;
        }
    }
}