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

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

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

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.activities.util.CounterHandler;
import io.github.marcocipriani01.telescopetouch.activities.util.LongPressHandler;

import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

/**
 * This fragment shows directional buttons to move a focuser.
 *
 * @author marcocipriani01
 */
public class FocuserFragment extends ActionFragment implements INDIServerConnectionListener, INDIPropertyListener,
        INDIDeviceListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener, TextWatcher {

    private static final String TAG = TelescopeTouchApp.getTag(MountControlFragment.class);
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Properties and elements associated to the buttons
    private volatile INDISwitchProperty directionProp = null;
    private volatile INDISwitchElement inwardDirElem = null;
    private volatile INDISwitchElement outwardDirElem = null;
    private volatile INDINumberProperty relPosProp = null;
    private volatile INDINumberElement relPosElem = null;
    private volatile INDINumberProperty absPosProp = null;
    private volatile INDINumberElement absPosElem = null;
    private volatile INDINumberProperty syncPosProp = null;
    private volatile INDINumberElement syncPosElem = null;
    private volatile INDINumberProperty speedProp = null;
    private volatile INDINumberElement speedElem = null;
    private volatile INDISwitchProperty abortProp = null;
    private volatile INDISwitchElement abortElem = null;
    // Views
    private Button inButton = null;
    private Button outButton = null;
    private Button speedUpButton = null;
    private Button speedDownButton = null;
    private Button abortButton = null;
    private Button setAbsPosButton = null;
    private Button syncPosButton = null;
    private TextView stepsText = null;
    private EditText positionEditText = null;
    private SeekBar speedBar = null;
    private CounterHandler stepsHandler;
    private TextView focuserName = null;
    private Toolbar toolbar = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_focuser, container, false);
        inButton = rootView.findViewById(R.id.focus_in);
        outButton = rootView.findViewById(R.id.focus_out);
        speedUpButton = rootView.findViewById(R.id.focuser_faster);
        speedDownButton = rootView.findViewById(R.id.focuser_slower);
        stepsText = rootView.findViewById(R.id.focuser_steps_box);
        abortButton = rootView.findViewById(R.id.focuser_abort);
        setAbsPosButton = rootView.findViewById(R.id.fok_abs_pos_button);
        syncPosButton = rootView.findViewById(R.id.fok_sync_pos_button);
        positionEditText = rootView.findViewById(R.id.abs_pos_field);
        speedBar = rootView.findViewById(R.id.focus_speed_seekbar);
        focuserName = rootView.findViewById(R.id.focuser_name);
        toolbar = rootView.findViewById(R.id.focuser_toolbar);
        stepsHandler = new CounterHandler(speedUpButton, speedDownButton, 1, 1000000, 100, 10, 100, false) {
            @Override
            protected void onIncrement() {
                super.onIncrement();
                stepsText.setText(String.valueOf(getValue()));
            }

            @Override
            protected void onDecrement() {
                super.onDecrement();
                stepsText.setText(String.valueOf(getValue()));
            }
        };
        new LongPressHandler(outButton, inButton, 150) {
            @Override
            protected void onIncrement() {
                if (outwardDirElem != null && inwardDirElem != null && relPosElem != null) {
                    try {
                        outwardDirElem.setDesiredValue(Constants.SwitchStatus.ON);
                        inwardDirElem.setDesiredValue(Constants.SwitchStatus.OFF);
                        relPosElem.setDesiredValue((double) stepsHandler.getValue());
                        connectionManager.updateProperties(directionProp, relPosProp);
                    } catch (Exception e) {
                        Log.e(TAG, e.getLocalizedMessage(), e);
                    }
                }
            }

            @Override
            protected void onDecrement() {
                if (inwardDirElem != null && outwardDirElem != null && relPosElem != null) {
                    try {
                        inwardDirElem.setDesiredValue(Constants.SwitchStatus.ON);
                        outwardDirElem.setDesiredValue(Constants.SwitchStatus.OFF);
                        relPosElem.setDesiredValue((double) stepsHandler.getValue());
                        connectionManager.updateProperties(directionProp, relPosProp);
                    } catch (Exception e) {
                        Log.e(TAG, e.getLocalizedMessage(), e);
                    }
                }
            }
        };
        abortButton.setOnClickListener(this);
        setAbsPosButton.setOnClickListener(this);
        syncPosButton.setOnClickListener(this);
        stepsText.addTextChangedListener(this);
        speedBar.setOnSeekBarChangeListener(this);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        connectionManager.addINDIListener(this);
        // Enumerate existing properties
        if (connectionManager.isConnected()) {
            List<INDIDevice> list = connectionManager.getIndiConnection().getDevicesAsList();
            for (INDIDevice device : list) {
                device.addINDIDeviceListener(this);
                List<INDIProperty<?>> properties = device.getPropertiesAsList();
                for (INDIProperty<?> property : properties) {
                    newProperty0(device, property);
                }
            }
            updateSpeedBar();
        } else {
            clearVars();
        }
        // Update UI
        enableUi();
        updateStepsText();
        updatePositionText();
    }

    @Override
    public void onStop() {
        super.onStop();
        connectionManager.removeINDIListener(this);
    }

    private void clearVars() {
        relPosProp = null;
        relPosElem = null;
        absPosProp = null;
        absPosElem = null;
        directionProp = null;
        outwardDirElem = null;
        inwardDirElem = null;
        syncPosProp = null;
        syncPosElem = null;
        speedProp = null;
        speedElem = null;
        abortElem = null;
        abortProp = null;
    }

    private void updateSpeedBar() {
        handler.post(() -> {
            if ((speedBar != null) && (speedElem != null)) {
                speedBar.setOnSeekBarChangeListener(null);
                double step = speedElem.getStep(), min = speedElem.getMin(), max = speedElem.getMax();
                speedBar.setMax((int) ((max - min) / step));
                speedBar.setProgress((int) ((speedElem.getValue() - min) / step));
                speedBar.setOnSeekBarChangeListener(this);
            }
        });
    }

    /**
     * Enables the buttons if the corresponding property was found
     */
    private void enableUi() {
        handler.post(() -> {
            if (inButton != null) inButton.setEnabled(inwardDirElem != null);
            if (outButton != null) outButton.setEnabled(outwardDirElem != null);
            boolean relPosEn = relPosElem != null;
            if (speedUpButton != null) speedUpButton.setEnabled(relPosEn);
            if (speedDownButton != null) speedDownButton.setEnabled(relPosEn);
            if (stepsText != null) stepsText.setEnabled(relPosEn);
            if (abortButton != null) abortButton.setEnabled(abortElem != null);
            if (syncPosButton != null) syncPosButton.setEnabled(syncPosElem != null);
            boolean absPosEn = absPosElem != null;
            if (setAbsPosButton != null) setAbsPosButton.setEnabled(absPosEn);
            if (positionEditText != null) positionEditText.setEnabled(absPosEn);
            if (speedBar != null) speedBar.setEnabled(speedElem != null);
        });
    }

    /**
     * Updates the speed text
     */
    private void updateStepsText() {
        handler.post(() -> {
            if (stepsText != null) {
                if (relPosElem == null) {
                    stepsText.setText(R.string.unavailable);
                } else {
                    int steps = (int) (double) relPosElem.getValue();
                    if (steps == 0) steps = 10;
                    stepsText.setText(String.valueOf(steps));
                    stepsHandler.setValue(steps);
                }
            }
        });
    }

    private void updatePositionText() {
        handler.post(() -> {
            if (positionEditText != null) {
                if (absPosElem == null) {
                    positionEditText.setText(R.string.unavailable);
                } else {
                    positionEditText.setText(String.valueOf((int) (double) absPosElem.getValue()));
                }
            }
        });
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        try {
            stepsHandler.setValue(Integer.parseInt(s.toString()));
        } catch (NumberFormatException ignored) {

        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        try {
            if (id == R.id.focuser_abort) {
                if (abortElem != null) {
                    abortElem.setDesiredValue(Constants.SwitchStatus.ON);
                    connectionManager.updateProperties(abortProp);
                }
            } else if (id == R.id.fok_abs_pos_button) {
                if (absPosElem != null && positionEditText != null) {
                    try {
                        absPosElem.setDesiredValue(Double.parseDouble(positionEditText.getText().toString()));
                        connectionManager.updateProperties(absPosProp);
                    } catch (NumberFormatException e) {
                        requestActionSnack(R.string.invalid_abs_position);
                        updatePositionText();
                    }
                }
            } else if (id == R.id.fok_sync_pos_button) {
                if (syncPosElem != null && positionEditText != null) {
                    try {
                        syncPosElem.setDesiredValue(Double.parseDouble(positionEditText.getText().toString()));
                        connectionManager.updateProperties(syncPosProp);
                    } catch (NumberFormatException e) {
                        requestActionSnack(R.string.invalid_abs_position);
                        updatePositionText();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (speedElem != null) {
            try {
                double step = speedElem.getStep(), min = speedElem.getMin();
                speedElem.setDesiredValue(min + (progress * step));
                connectionManager.updateProperties(speedProp);
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    // ------ Listener functions from INDI ------

    @Override
    public void connectionLost(INDIServerConnection connection) {
        clearVars();
        updateSpeedBar();
        updateStepsText();
        updatePositionText();
        enableUi();
    }

    @Override
    public void newDevice(INDIServerConnection connection, INDIDevice device) {
        Log.i(TAG, "New device: " + device.getName());
        device.addINDIDeviceListener(this);
    }

    @Override
    public void removeDevice(INDIServerConnection connection, INDIDevice device) {
        Log.d(TAG, "Device removed: " + device.getName());
        device.removeINDIDeviceListener(this);
    }

    @Override
    public void newMessage(INDIServerConnection connection, Date timestamp, String message) {

    }

    @Override
    public void newProperty(INDIDevice device, INDIProperty<?> property) {
        if (newProperty0(device, property))
            enableUi();
    }

    private boolean newProperty0(INDIDevice device, INDIProperty<?> property) {
        String name = property.getName(), devName = device.getName();
        Log.i(TAG, "New Property (" + name + ") added to device " + devName
                + ", elements: " + Arrays.toString(property.getElementNames()));
        switch (name) {
            case "ABS_FOCUS_POSITION": {
                if ((absPosElem = (INDINumberElement) property.getElement(INDIStandardElement.FOCUS_ABSOLUTE_POSITION)) != null) {
                    property.addINDIPropertyListener(this);
                    absPosProp = (INDINumberProperty) property;
                    updatePositionText();
                }
                return true;
            }
            case "REL_FOCUS_POSITION": {
                if ((relPosElem = (INDINumberElement) property.getElement(INDIStandardElement.FOCUS_RELATIVE_POSITION)) != null) {
                    relPosProp = (INDINumberProperty) property;
                    stepsHandler.setMaxValue((int) relPosElem.getMax());
                    stepsHandler.setMinValue((int) relPosElem.getMin());
                    updateStepsText();
                }
                return true;
            }
            case "FOCUS_MOTION": {
                if (((inwardDirElem = (INDISwitchElement) property.getElement(INDIStandardElement.FOCUS_INWARD)) != null)
                        && ((outwardDirElem = (INDISwitchElement) property.getElement(INDIStandardElement.FOCUS_OUTWARD)) != null)) {
                    directionProp = (INDISwitchProperty) property;
                    handler.post(() -> {
                        if (focuserName != null)
                            focuserName.setText(devName);
                        if (toolbar != null)
                            toolbar.setTitle(devName);
                    });
                }
                return true;
            }
            case "FOCUS_ABORT_MOTION": {
                if ((abortElem = (INDISwitchElement) property.getElement(INDIStandardElement.ABORT)) != null) {
                    abortProp = (INDISwitchProperty) property;
                }
                return true;
            }
            case "FOCUS_SPEED": {
                if ((speedElem = (INDINumberElement) property.getElement(INDIStandardElement.FOCUS_SPEED_VALUE)) != null) {
                    speedProp = (INDINumberProperty) property;
                    property.addINDIPropertyListener(this);
                    updateSpeedBar();
                }
                return true;
            }
            case "FOCUS_SYNC": {
                if ((syncPosElem = (INDINumberElement) property.getElement(INDIStandardElement.FOCUS_SYNC_VALUE)) != null) {
                    syncPosProp = (INDINumberProperty) property;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void removeProperty(INDIDevice device, INDIProperty<?> property) {
        String name = property.getName();
        Log.d(TAG, "Removed property (" + name + ") to device " + device.getName());
        switch (name) {
            case "REL_FOCUS_POSITION": {
                relPosElem = null;
                relPosProp = null;
                updateStepsText();
                break;
            }
            case "FOCUS_MOTION": {
                inwardDirElem = null;
                outwardDirElem = null;
                directionProp = null;
                handler.post(() -> {
                    if (focuserName != null)
                        focuserName.setText(R.string.focuser_control);
                    if (toolbar != null)
                        toolbar.setTitle(R.string.focuser_control);
                });
                break;
            }
            case "FOCUS_ABORT_MOTION": {
                abortElem = null;
                abortProp = null;
                break;
            }
            case "ABS_FOCUS_POSITION": {
                absPosProp = null;
                absPosElem = null;
                updatePositionText();
                break;
            }
            case "FOCUS_SPEED": {
                speedProp = null;
                speedElem = null;
                break;
            }
            case "FOCUS_SYNC": {
                syncPosProp = null;
                syncPosElem = null;
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
        String name = property.getName();
        Log.d(TAG,
                "Changed property (" + name + "), new value" + property.getValuesAsString());
        switch (name) {
            case "ABS_FOCUS_POSITION": {
                updatePositionText();
                break;
            }
            case "FOCUS_SPEED": {
                updateSpeedBar();
                break;
            }
        }
    }

    @Override
    public void messageChanged(INDIDevice device) {

    }

    @Override
    public boolean isActionEnabled() {
        return false;
    }

    @Override
    public int getActionDrawable() {
        return 0;
    }

    @Override
    public void run() {

    }
}