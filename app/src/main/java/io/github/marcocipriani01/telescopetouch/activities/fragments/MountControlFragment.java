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

import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import org.indilib.i4j.Constants;
import org.indilib.i4j.client.INDIDevice;
import org.indilib.i4j.client.INDIDeviceListener;
import org.indilib.i4j.client.INDIProperty;
import org.indilib.i4j.client.INDIPropertyListener;
import org.indilib.i4j.client.INDIServerConnection;
import org.indilib.i4j.client.INDIServerConnectionListener;
import org.indilib.i4j.client.INDISwitchElement;
import org.indilib.i4j.client.INDISwitchProperty;
import org.indilib.i4j.properties.INDIStandardElement;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.activities.util.ImprovedSpinnerListener;
import io.github.marcocipriani01.telescopetouch.activities.util.ImprovedToggleListener;

/**
 * This fragment shows directional buttons to move a telescope. It also provides
 * buttons to change speed. To activate the buttons, the driver must provide the following properties:
 * {@code TELESCOPE_MOTION_NS}, {@code TELESCOPE_MOTION_WE}, {@code TELESCOPE_ABORT_MOTION}, {@code TELESCOPE_MOTION_RATE}
 *
 * @author marcocipriani01
 */
public class MountControlFragment extends ActionFragment implements INDIServerConnectionListener,
        INDIPropertyListener, INDIDeviceListener, OnTouchListener, OnClickListener, Toolbar.OnMenuItemClickListener {

    private static final String TAG = TelescopeTouchApp.getTag(MountControlFragment.class);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MenuItem trackingMenu;
    // Properties and elements associated to the buttons
    private volatile INDISwitchProperty telescopeMotionNSP = null;
    private volatile INDISwitchElement telescopeMotionNE = null;
    private volatile INDISwitchElement telescopeMotionSE = null;
    private volatile INDISwitchProperty telescopeMotionWEP = null;
    private volatile INDISwitchElement telescopeMotionWE = null;
    private volatile INDISwitchElement telescopeMotionEE = null;
    private volatile INDISwitchProperty telescopeMotionAbort = null;
    private volatile INDISwitchElement telescopeMotionAbortE = null;
    private volatile INDISwitchProperty telescopeSlewRateP = null;
    private volatile INDISwitchProperty telescopeParkP = null;
    private volatile INDISwitchElement telescopeParkE = null;
    private volatile INDISwitchElement telescopeUnParkE = null;
    private volatile INDISwitchProperty telescopeTrackP = null;
    private volatile INDISwitchElement telescopeTrackE = null;
    private volatile INDISwitchElement telescopeUnTrackE = null;
    // Views
    private ToggleButton btnPark = null;
    private Button btnMoveN = null;
    private Button btnMoveS = null;
    private Button btnMoveE = null;
    private Button btnMoveW = null;
    private Button btnMoveNE = null;
    private Button btnMoveNW = null;
    private Button btnMoveSE = null;
    private Button btnMoveSW = null;
    private Button btnStop = null;
    private Spinner slewRateSpinner = null;
    private final ImprovedSpinnerListener spinnerListener = new ImprovedSpinnerListener() {
        @Override
        protected void onImprovedItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            try {
                String selected = ((String) slewRateSpinner.getAdapter().getItem(pos));
                if ((selected != null) && (!selected.equals(context.getString(R.string.unavailable)))) {
                    for (INDISwitchElement element : telescopeSlewRateP.getElementsAsList()) {
                        String label = element.getLabel();
                        if (label.equals(selected)) {
                            element.setDesiredValue(Constants.SwitchStatus.ON);
                        } else {
                            element.setDesiredValue(Constants.SwitchStatus.OFF);
                        }
                    }
                    connectionManager.updateProperties(telescopeSlewRateP);
                }
            } catch (Exception e) {
                Log.e(TAG, "Slew rate error!", e);
            }
        }
    };
    private final ImprovedToggleListener parkListener = new ImprovedToggleListener() {
        @Override
        protected void onImprovedCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.telescope_parking)
                    .setMessage(R.string.telescope_parking_confirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        if ((telescopeParkE != null) && (telescopeUnParkE != null)) {
                            try {
                                if (isChecked) {
                                    telescopeParkE.setDesiredValue(Constants.SwitchStatus.ON);
                                    telescopeUnParkE.setDesiredValue(Constants.SwitchStatus.OFF);
                                } else {
                                    telescopeUnParkE.setDesiredValue(Constants.SwitchStatus.ON);
                                    telescopeParkE.setDesiredValue(Constants.SwitchStatus.OFF);
                                }
                                connectionManager.updateProperties(telescopeParkP);
                            } catch (Exception e) {
                                Log.e(TAG, e.getLocalizedMessage(), e);
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener(dialog -> setParkState())
                    .setIcon(R.drawable.warning)
                    .show();
        }
    };
    private TextView mountName = null;
    private Toolbar toolbar = null;

    @Override
    public boolean isActionEnabled() {
        return false;
    }

    @Override
    public int getActionDrawable() {
        return 0;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_mount, container, false);
        setHasOptionsMenu(true);
        // Set up the UI
        btnPark = rootView.findViewById(R.id.mount_parked_toggle);
        btnMoveN = rootView.findViewById(R.id.buttonN);
        btnMoveNE = rootView.findViewById(R.id.buttonNE);
        btnMoveE = rootView.findViewById(R.id.buttonE);
        btnMoveSE = rootView.findViewById(R.id.buttonSE);
        btnMoveS = rootView.findViewById(R.id.buttonS);
        btnMoveSW = rootView.findViewById(R.id.buttonSW);
        btnMoveW = rootView.findViewById(R.id.buttonW);
        btnMoveNW = rootView.findViewById(R.id.buttonNW);
        btnStop = rootView.findViewById(R.id.buttonStop);
        slewRateSpinner = rootView.findViewById(R.id.mount_slew_rate);
        mountName = rootView.findViewById(R.id.mount_name);
        toolbar = rootView.findViewById(R.id.mount_control_toolbar);
        btnMoveN.setOnTouchListener(this);
        btnMoveNE.setOnTouchListener(this);
        btnMoveE.setOnTouchListener(this);
        btnMoveSE.setOnTouchListener(this);
        btnMoveS.setOnTouchListener(this);
        btnMoveSW.setOnTouchListener(this);
        btnMoveW.setOnTouchListener(this);
        btnMoveNW.setOnTouchListener(this);
        btnStop.setOnClickListener(this);
        spinnerListener.attach(slewRateSpinner);
        parkListener.attach(btnPark);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        connectionManager.addINDIListener(this);
        // Enumerate existing properties
        if (connectionManager.isConnected()) {
            List<INDIDevice> list = connectionManager.getIndiConnection().getDevicesAsList();
            if (list != null) {
                for (INDIDevice device : list) {
                    device.addINDIDeviceListener(this);
                    List<INDIProperty<?>> properties = device.getPropertiesAsList();
                    for (INDIProperty<?> property : properties) {
                        newProperty0(device, property);
                    }
                }
            }
        } else {
            clearVars();
        }
        // Update UI
        enableUi();
        initSlewRate();
    }

    @Override
    public void onStop() {
        super.onStop();
        connectionManager.removeINDIListener(this);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.mount_control, menu);
        trackingMenu = Objects.requireNonNull(menu.findItem(R.id.menu_toggle_tracking));
        if (telescopeTrackP == null) {
            trackingMenu.setVisible(false);
        } else {
            trackingMenu.setVisible(true);
            trackingMenu.setIcon((telescopeTrackE.getValue() == Constants.SwitchStatus.ON) ?
                    R.drawable.lock_closed : R.drawable.lock_open);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.menu_toggle_tracking) {
            if (telescopeTrackP != null) {
                try {
                    if (telescopeTrackE.getValue() == Constants.SwitchStatus.ON) {
                        telescopeTrackE.setDesiredValue(Constants.SwitchStatus.OFF);
                        telescopeUnTrackE.setDesiredValue(Constants.SwitchStatus.ON);
                    } else {
                        telescopeTrackE.setDesiredValue(Constants.SwitchStatus.ON);
                        telescopeUnTrackE.setDesiredValue(Constants.SwitchStatus.OFF);
                    }
                    connectionManager.updateProperties(telescopeTrackP);
                    requestActionSnack(R.string.tracking_toggled);
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);
                    requestActionSnack(R.string.tracking_error);
                }
            }
            return true;
        }
        return false;
    }

    private void clearVars() {
        telescopeMotionNSP = null;
        telescopeMotionNE = null;
        telescopeMotionSE = null;
        telescopeMotionWEP = null;
        telescopeMotionWE = null;
        telescopeMotionEE = null;
        telescopeMotionAbort = null;
        telescopeMotionAbortE = null;
        telescopeSlewRateP = null;
        telescopeParkP = null;
        telescopeParkE = null;
        telescopeUnParkE = null;
        telescopeTrackP = null;
        telescopeTrackE = null;
        telescopeUnTrackE = null;
    }

    private void initSlewRate() {
        handler.post(() -> {
            if (slewRateSpinner != null) {
                ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item);
                arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                int selectedItem = 0;
                if (telescopeSlewRateP == null) {
                    arrayAdapter.add(context.getString(R.string.unavailable));
                } else {
                    List<INDISwitchElement> elements = telescopeSlewRateP.getElementsAsList();
                    if (elements.isEmpty()) {
                        arrayAdapter.add(context.getString(R.string.unavailable));
                    } else {
                        for (int i = 0, elementsSize = elements.size(); i < elementsSize; i++) {
                            INDISwitchElement element = elements.get(i);
                            arrayAdapter.add(element.getLabel());
                            if (element.getValue() == Constants.SwitchStatus.ON) selectedItem = i;
                        }
                    }
                }
                slewRateSpinner.setAdapter(arrayAdapter);
                slewRateSpinner.setSelection(selectedItem);
            }
        });
    }

    /**
     * Enables the buttons if the corresponding property was found
     */
    public void enableUi() {
        handler.post(() -> {
            boolean parkEnabled = telescopeParkP != null;
            if (btnPark != null) btnPark.setEnabled(parkEnabled);
            boolean moveEnabled = (telescopeMotionWEP != null) && (telescopeMotionNSP != null);
            boolean unparked = true;
            if (parkEnabled) {
                unparked = (telescopeParkE.getValue() == Constants.SwitchStatus.OFF);
                moveEnabled = moveEnabled && unparked;
            }
            if (btnMoveE != null) btnMoveE.setEnabled(moveEnabled);
            if (btnMoveW != null) btnMoveW.setEnabled(moveEnabled);
            if (btnMoveN != null) btnMoveN.setEnabled(moveEnabled);
            if (btnMoveS != null) btnMoveS.setEnabled(moveEnabled);
            if (btnMoveNE != null) btnMoveNE.setEnabled(moveEnabled);
            if (btnMoveNW != null) btnMoveNW.setEnabled(moveEnabled);
            if (btnMoveSE != null) btnMoveSE.setEnabled(moveEnabled);
            if (btnMoveSW != null) btnMoveSW.setEnabled(moveEnabled);
            if (btnStop != null) {
                boolean stopEnabled = (telescopeMotionWEP != null) || (telescopeMotionNSP != null) || (telescopeMotionAbort != null);
                if (parkEnabled)
                    stopEnabled = stopEnabled && unparked;
                btnStop.setEnabled(stopEnabled);
            }
            if (slewRateSpinner != null) slewRateSpinner.setEnabled(telescopeSlewRateP != null);
            if (trackingMenu != null)
                trackingMenu.setVisible(telescopeTrackP != null);
        });
    }

    private void setParkState() {
        if (telescopeParkE != null) {
            if (btnPark != null) {
                boolean b = telescopeParkE.getValue() == Constants.SwitchStatus.ON;
                btnPark.setChecked(b);
                btnPark.setSelected(b);
            }
            enableUi();
        }
    }

    /**
     * Called when a directional button is pressed or released. Send the
     * corresponding order to the driver.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final Constants.SwitchStatus status;
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            status = Constants.SwitchStatus.ON;
        } else if (action == MotionEvent.ACTION_UP) {
            status = Constants.SwitchStatus.OFF;
        } else {
            return true;
        }
        int id = v.getId();
        try {
            if (id == R.id.buttonE) {
                telescopeMotionEE.setDesiredValue(status);
                telescopeMotionWE.setDesiredValue(Constants.SwitchStatus.OFF);
                connectionManager.updateProperties(telescopeMotionWEP);
                return true;
            } else if (id == R.id.buttonW) {
                telescopeMotionWE.setDesiredValue(status);
                telescopeMotionEE.setDesiredValue(Constants.SwitchStatus.OFF);
                connectionManager.updateProperties(telescopeMotionWEP);
            } else if (id == R.id.buttonN) {
                telescopeMotionNE.setDesiredValue(status);
                telescopeMotionSE.setDesiredValue(Constants.SwitchStatus.OFF);
                connectionManager.updateProperties(telescopeMotionNSP);
            } else if (id == R.id.buttonS) {
                telescopeMotionSE.setDesiredValue(status);
                telescopeMotionNE.setDesiredValue(Constants.SwitchStatus.OFF);
                connectionManager.updateProperties(telescopeMotionNSP);
            } else if (id == R.id.buttonNE) {
                telescopeMotionEE.setDesiredValue(status);
                telescopeMotionWE.setDesiredValue(Constants.SwitchStatus.OFF);
                telescopeMotionNE.setDesiredValue(status);
                telescopeMotionSE.setDesiredValue(Constants.SwitchStatus.OFF);
                connectionManager.updateProperties(telescopeMotionWEP, telescopeMotionNSP);
            } else if (id == R.id.buttonNW) {
                telescopeMotionWE.setDesiredValue(status);
                telescopeMotionEE.setDesiredValue(Constants.SwitchStatus.OFF);
                telescopeMotionNE.setDesiredValue(status);
                telescopeMotionSE.setDesiredValue(Constants.SwitchStatus.OFF);
                connectionManager.updateProperties(telescopeMotionWEP, telescopeMotionNSP);
            } else if (id == R.id.buttonSE) {
                telescopeMotionEE.setDesiredValue(status);
                telescopeMotionWE.setDesiredValue(Constants.SwitchStatus.OFF);
                telescopeMotionSE.setDesiredValue(status);
                telescopeMotionNE.setDesiredValue(Constants.SwitchStatus.OFF);
                connectionManager.updateProperties(telescopeMotionWEP, telescopeMotionNSP);
            } else if (id == R.id.buttonSW) {
                telescopeMotionWE.setDesiredValue(status);
                telescopeMotionEE.setDesiredValue(Constants.SwitchStatus.OFF);
                telescopeMotionSE.setDesiredValue(status);
                telescopeMotionNE.setDesiredValue(Constants.SwitchStatus.OFF);
                connectionManager.updateProperties(telescopeMotionWEP, telescopeMotionNSP);
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            return false;
        }
        return true;
    }

    /**
     * Called when one of the stop, speed up and speed down buttons is clicked.
     * Sends the corresponding order to the driver.
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.buttonStop) {
            try {
                if (telescopeMotionWEP != null) {
                    telescopeMotionWE.setDesiredValue(Constants.SwitchStatus.OFF);
                    telescopeMotionEE.setDesiredValue(Constants.SwitchStatus.OFF);
                    connectionManager.updateProperties(telescopeMotionWEP);
                }
                if (telescopeMotionNSP != null) {
                    telescopeMotionSE.setDesiredValue(Constants.SwitchStatus.OFF);
                    telescopeMotionNE.setDesiredValue(Constants.SwitchStatus.OFF);
                    connectionManager.updateProperties(telescopeMotionNSP);
                }
                if (telescopeMotionAbort != null) {
                    telescopeMotionAbortE.setDesiredValue(Constants.SwitchStatus.ON);
                    connectionManager.updateProperties(telescopeMotionAbort);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
            }
        }
    }

    // ------ Listener functions from INDI ------

    @Override
    public void connectionLost(INDIServerConnection arg0) {
        clearVars();
        enableUi();
        initSlewRate();
    }

    @Override
    public void newDevice(INDIServerConnection connection, INDIDevice device) {
        Log.i(TAG, "New device: " + device.getName());
        device.addINDIDeviceListener(this);
    }

    @Override
    public void removeDevice(INDIServerConnection connection, INDIDevice device) {
        Log.i(TAG, "Device removed: " + device.getName());
        device.removeINDIDeviceListener(this);
    }

    @Override
    public void newMessage(INDIServerConnection connection, Date timestamp, String message) {

    }

    @Override
    public void newProperty(INDIDevice device, INDIProperty<?> property) {
        newProperty0(device, property);
        enableUi();
    }

    private void newProperty0(INDIDevice device, INDIProperty<?> property) {
        String name = property.getName(), devName = device.getName();
        Log.i(TAG, "New Property (" + name + ") added to device " + devName
                + ", elements: " + Arrays.toString(property.getElementNames()));
        switch (name) {
            case "TELESCOPE_MOTION_NS": {
                if (((telescopeMotionNE = (INDISwitchElement) property.getElement(INDIStandardElement.MOTION_NORTH)) != null)
                        && ((telescopeMotionSE = (INDISwitchElement) property.getElement(INDIStandardElement.MOTION_SOUTH)) != null)) {
                    telescopeMotionNSP = (INDISwitchProperty) property;
                    property.addINDIPropertyListener(this);
                    handler.post(() -> {
                        if (mountName != null) mountName.setText(devName);
                        if (toolbar != null) toolbar.setTitle(devName);
                    });
                }
                break;
            }
            case "TELESCOPE_MOTION_WE": {
                if (((telescopeMotionEE = (INDISwitchElement) property.getElement(INDIStandardElement.MOTION_EAST)) != null)
                        && ((telescopeMotionWE = (INDISwitchElement) property.getElement(INDIStandardElement.MOTION_WEST)) != null)) {
                    telescopeMotionWEP = (INDISwitchProperty) property;
                    property.addINDIPropertyListener(this);
                }
                break;
            }
            case "TELESCOPE_ABORT_MOTION": {
                if ((telescopeMotionAbortE = (INDISwitchElement) property.getElement(INDIStandardElement.ABORT_MOTION)) != null) {
                    telescopeMotionAbort = (INDISwitchProperty) property;
                    property.addINDIPropertyListener(this);
                }
                break;
            }
            case "TELESCOPE_SLEW_RATE": {
                telescopeSlewRateP = (INDISwitchProperty) property;
                property.addINDIPropertyListener(this);
                initSlewRate();
                break;
            }
            case "TELESCOPE_PARK": {
                if (((telescopeParkE = (INDISwitchElement) property.getElement(INDIStandardElement.PARK)) != null)
                        && ((telescopeUnParkE = (INDISwitchElement) property.getElement(INDIStandardElement.UNPARK)) != null)) {
                    telescopeParkP = (INDISwitchProperty) property;
                    property.addINDIPropertyListener(this);
                    handler.post(this::setParkState);
                }
                break;
            }
            case "TELESCOPE_TRACK_STATE": {
                if (((telescopeTrackE = (INDISwitchElement) property.getElement("TRACK_ON")) != null)
                        && ((telescopeUnTrackE = (INDISwitchElement) property.getElement("TRACK_OFF")) != null)) {
                    telescopeTrackP = (INDISwitchProperty) property;
                    property.addINDIPropertyListener(this);
                    handler.post(() -> {
                        if (trackingMenu != null)
                            trackingMenu.setIcon((telescopeTrackE.getValue() == Constants.SwitchStatus.ON) ?
                                    R.drawable.lock_closed : R.drawable.lock_open);
                    });
                }
                break;
            }
        }
    }

    @Override
    public void removeProperty(INDIDevice device, INDIProperty<?> property) {
        String name = property.getName();
        Log.d(TAG, "Removed property (" + name + ") to device " + device.getName());
        switch (name) {
            case "TELESCOPE_MOTION_NS": {
                telescopeMotionNSP = null;
                telescopeMotionNE = null;
                telescopeMotionSE = null;
                handler.post(() -> {
                    if (mountName != null) mountName.setText(R.string.mount_control);
                    if (toolbar != null) toolbar.setTitle(R.string.mount_control);
                });
                break;
            }
            case "TELESCOPE_MOTION_WE": {
                telescopeMotionWEP = null;
                telescopeMotionWE = null;
                telescopeMotionEE = null;
                break;
            }
            case "TELESCOPE_ABORT_MOTION": {
                telescopeMotionAbort = null;
                telescopeMotionAbortE = null;
                break;
            }
            case "TELESCOPE_SLEW_RATE": {
                telescopeSlewRateP = null;
                initSlewRate();
                break;
            }
            case "TELESCOPE_PARK": {
                telescopeParkP = null;
                telescopeParkE = null;
                telescopeUnParkE = null;
                break;
            }
            case "TELESCOPE_TRACK_STATE": {
                telescopeTrackP = null;
                telescopeTrackE = null;
                telescopeUnTrackE = null;
                break;
            }
            default: {
                return;
            }
        }
        enableUi();
    }

    @Override
    public void propertyChanged(final INDIProperty<?> property) {
        switch (property.getName()) {
            case "TELESCOPE_MOTION_NS": {
                handler.post(() -> {
                    if ((btnMoveN != null) && (telescopeMotionNE != null))
                        btnMoveN.setPressed(telescopeMotionNE.getValue() == Constants.SwitchStatus.ON);
                    if ((btnMoveS != null) && (telescopeMotionSE != null))
                        btnMoveS.setPressed(telescopeMotionSE.getValue() == Constants.SwitchStatus.ON);
                });
                break;
            }
            case "TELESCOPE_MOTION_WE": {
                handler.post(() -> {
                    if ((btnMoveE != null) && (telescopeMotionEE != null))
                        btnMoveE.setPressed(telescopeMotionEE.getValue() == Constants.SwitchStatus.ON);
                    if ((btnMoveW != null) && (telescopeMotionWE != null))
                        btnMoveW.setPressed(telescopeMotionWE.getValue() == Constants.SwitchStatus.ON);
                });
                break;
            }
            case "TELESCOPE_SLEW_RATE": {
                handler.post(() -> {
                    if ((slewRateSpinner != null) && (telescopeSlewRateP != null)) {
                        String selected = null;
                        for (INDISwitchElement element : telescopeSlewRateP) {
                            if (element.getValue() == Constants.SwitchStatus.ON)
                                selected = element.getLabel();
                        }
                        if (selected != null) {
                            SpinnerAdapter adapter = slewRateSpinner.getAdapter();
                            int i;
                            for (i = 0; i < adapter.getCount(); i++) {
                                if (adapter.getItem(i).equals(selected)) break;
                            }
                            slewRateSpinner.setSelection(i);
                        }
                    }
                });
                break;
            }
            case "TELESCOPE_PARK": {
                handler.post(this::setParkState);
                break;
            }
            case "TELESCOPE_TRACK_STATE": {
                handler.post(() -> {
                    if ((trackingMenu != null) && (telescopeTrackE != null))
                        trackingMenu.setIcon((telescopeTrackE.getValue() == Constants.SwitchStatus.ON) ?
                                R.drawable.lock_closed : R.drawable.lock_open);
                });
                break;
            }
        }
    }

    @Override
    public void messageChanged(INDIDevice device) {

    }

    @Override
    public void run() {

    }
}