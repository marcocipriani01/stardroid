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

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;

import org.indilib.i4j.client.INDIDevice;

import java.util.ArrayList;
import java.util.List;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.activities.util.CounterHandler;
import io.github.marcocipriani01.telescopetouch.activities.util.ImprovedSpinnerListener;
import io.github.marcocipriani01.telescopetouch.activities.util.LongPressHandler;
import io.github.marcocipriani01.telescopetouch.activities.util.SimpleAdapter;
import io.github.marcocipriani01.telescopetouch.indi.ConnectionManager;
import io.github.marcocipriani01.telescopetouch.indi.INDIFocuser;
import io.github.marcocipriani01.telescopetouch.indi.NumberPropPref;

/**
 * This fragment shows directional buttons to move a focuser.
 *
 * @author marcocipriani01
 */
public class FocuserFragment extends ActionFragment implements View.OnClickListener, TextWatcher,
        Slider.OnChangeListener, INDIFocuser.FocuserListener, ConnectionManager.ManagerListener {

    private static final String TAG = TelescopeTouchApp.getTag(MountControlFragment.class);
    private static INDIDevice selectedFocuserDev = null;
    private final List<INDIFocuser> focusers = new ArrayList<>();
    private Button inButton;
    private Button outButton;
    private Button stepsPerClickUpBtn;
    private Button stepsPerClickDownBtn;
    private Button abortButton;
    private Button setAbsPosButton;
    private Button syncBtn;
    private EditText stepsPerClickField;
    private EditText positionField;
    private Slider speedSlider;
    private Spinner focuserSelectSpinner;
    private FocusersArrayAdapter focuserSelectAdapter;
    private CounterHandler stepsHandler;
    private final ImprovedSpinnerListener focuserSelectListener = new ImprovedSpinnerListener() {
        @Override
        protected void onImprovedItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            INDIFocuser focuser = getFocuser();
            if (focuser != null)
                focuser.removeListener(FocuserFragment.this);
            focuser = focusers.get(pos);
            selectedFocuserDev = focuser.device;
            focuser.addListener(FocuserFragment.this);
            onFocuserFunctionsChange();
        }
    };
    private LayoutInflater inflater;
    private InputMethodManager inputMethodManager;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        this.inflater = inflater;
        View rootView = inflater.inflate(R.layout.fragment_focuser, container, false);
        inButton = rootView.findViewById(R.id.focus_in);
        outButton = rootView.findViewById(R.id.focus_out);
        stepsPerClickUpBtn = rootView.findViewById(R.id.focuser_steps_click_more);
        stepsPerClickDownBtn = rootView.findViewById(R.id.focuser_steps_click_less);
        stepsPerClickField = rootView.findViewById(R.id.focuser_steps_click_field);
        abortButton = rootView.findViewById(R.id.focuser_abort);
        setAbsPosButton = rootView.findViewById(R.id.fok_abs_pos_button);
        syncBtn = rootView.findViewById(R.id.fok_sync_pos_button);
        positionField = rootView.findViewById(R.id.abs_pos_field);
        speedSlider = rootView.findViewById(R.id.focus_speed_slider);
        stepsHandler = new CounterHandler(stepsPerClickUpBtn, stepsPerClickDownBtn, 1, 1000000, 100, 10, 100, false) {
            @Override
            protected void onIncrement() {
                super.onIncrement();
                stepsPerClickField.setText(String.valueOf(getValue()));
            }

            @Override
            protected void onDecrement() {
                super.onDecrement();
                stepsPerClickField.setText(String.valueOf(getValue()));
            }
        };
        new LongPressHandler(outButton, inButton, 150) {
            @Override
            protected void onIncrement() {
                if (stepsPerClickField != null)
                    inputMethodManager.hideSoftInputFromWindow(stepsPerClickField.getWindowToken(), 0);
                INDIFocuser focuser = getFocuser();
                if ((focuser != null) && focuser.canMoveRelative()) {
                    try {
                        focuser.moveOutward(stepsHandler.getValue());
                    } catch (Exception e) {
                        Log.e(TAG, e.getLocalizedMessage(), e);
                        errorSnackbar(e);
                    }
                } else {
                    onFocuserFunctionsChange();
                }
            }

            @Override
            protected void onDecrement() {
                if (stepsPerClickField != null)
                    inputMethodManager.hideSoftInputFromWindow(stepsPerClickField.getWindowToken(), 0);
                INDIFocuser focuser = getFocuser();
                if ((focuser != null) && focuser.canMoveRelative()) {
                    try {
                        focuser.moveInward(stepsHandler.getValue());
                    } catch (Exception e) {
                        Log.e(TAG, e.getLocalizedMessage(), e);
                        errorSnackbar(e);
                    }
                } else {
                    onFocuserFunctionsChange();
                }
            }
        };
        abortButton.setOnClickListener(this);
        setAbsPosButton.setOnClickListener(this);
        syncBtn.setOnClickListener(this);
        stepsPerClickField.addTextChangedListener(this);
        speedSlider.addOnChangeListener(this);
        focuserSelectAdapter = new FocusersArrayAdapter();
        focuserSelectSpinner = rootView.findViewById(R.id.focuser_selection_spinner);
        focuserSelectSpinner.setAdapter(focuserSelectAdapter);
        focuserSelectListener.attach(focuserSelectSpinner);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        connectionManager.addManagerListener(this);
        focusers.clear();
        if (connectionManager.isConnected()) {
            synchronized (connectionManager.indiFocusers) {
                focusers.addAll(connectionManager.indiFocusers.values());
            }
            focuserSelectAdapter.notifyDataSetChanged();
            if (focusers.isEmpty()) {
                selectedFocuserDev = null;
                focuserSelectSpinner.setEnabled(false);
            } else {
                INDIFocuser selectedFocuser;
                if (selectedFocuserDev == null) {
                    selectedFocuser = focusers.get(0);
                    selectedFocuserDev = selectedFocuser.device;
                } else {
                    synchronized (connectionManager.indiFocusers) {
                        if (connectionManager.indiFocusers.containsKey(selectedFocuserDev)) {
                            selectedFocuser = connectionManager.indiFocusers.get(selectedFocuserDev);
                            if (selectedFocuser == null) {
                                selectedFocuser = focusers.get(0);
                                selectedFocuserDev = selectedFocuser.device;
                            }
                        } else {
                            selectedFocuser = focusers.get(0);
                            selectedFocuserDev = selectedFocuser.device;
                        }
                    }
                }
                selectedFocuser.addListener(this);
                focuserSelectSpinner.setSelection(focusers.indexOf(selectedFocuser));
                focuserSelectSpinner.setEnabled(true);
            }
        } else {
            selectedFocuserDev = null;
            focuserSelectAdapter.notifyDataSetChanged();
            focuserSelectSpinner.setEnabled(false);
        }
        onFocuserFunctionsChange();
    }

    @Override
    public void onStop() {
        super.onStop();
        connectionManager.removeManagerListener(this);
        for (INDIFocuser focuser : focusers) {
            focuser.removeListener(this);
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        try {
            stepsHandler.setValue(Integer.parseInt(s.toString()));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        INDIFocuser focuser = getFocuser();
        if (focuser == null) {
            requestActionSnack(R.string.no_focuser_available);
            disableControls();
            return;
        }
        try {
            if (id == R.id.focuser_abort) {
                focuser.abort();
            } else if (id == R.id.fok_abs_pos_button) {
                if (positionField != null) {
                    try {
                        focuser.setAbsolutePosition(Integer.parseInt(positionField.getText().toString()));
                    } catch (NumberFormatException e) {
                        requestActionSnack(R.string.invalid_abs_position);
                    }
                }
            } else if (id == R.id.fok_sync_pos_button) {
                if (positionField != null) {
                    try {
                        focuser.sync(Integer.parseInt(positionField.getText().toString()));
                    } catch (NumberFormatException e) {
                        requestActionSnack(R.string.invalid_abs_position);
                    }
                }
            } else {
                return;
            }
            if (positionField != null)
                inputMethodManager.hideSoftInputFromWindow(positionField.getWindowToken(), 0);
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            errorSnackbar(e);
        }
    }

    private INDIFocuser getFocuser() {
        if (selectedFocuserDev == null) return null;
        return connectionManager.indiFocusers.get(selectedFocuserDev);
    }

    @Override
    public void onFocusersListChange() {
        focusers.clear();
        synchronized (connectionManager.indiFocusers) {
            focusers.addAll(connectionManager.indiFocusers.values());
        }
        if (focuserSelectAdapter != null)
            focuserSelectAdapter.notifyDataSetChanged();
        if (focuserSelectSpinner != null)
            focuserSelectSpinner.setEnabled(!focusers.isEmpty());
        onFocuserFunctionsChange();
    }

    @Override
    public void onConnectionLost() {
        selectedFocuserDev = null;
        focusers.clear();
        if (focuserSelectAdapter != null)
            focuserSelectAdapter.notifyDataSetChanged();
        if (focuserSelectSpinner != null)
            focuserSelectSpinner.setEnabled(false);
        disableControls();
    }

    public void errorSnackbar(Throwable e) {
        String message = e.getLocalizedMessage();
        if ((message == null) || message.equals("?")) {
            requestActionSnack(context.getString(R.string.unknown_exception));
        } else {
            requestActionSnack(context.getString(R.string.error) + message);
        }
    }

    private void disableControls() {
        if (inButton != null) inButton.setEnabled(false);
        if (outButton != null) outButton.setEnabled(false);
        if (stepsPerClickField != null) {
            stepsPerClickField.setEnabled(false);
            stepsPerClickField.setText(R.string.unavailable);
        }
        if (stepsPerClickUpBtn != null) stepsPerClickUpBtn.setEnabled(false);
        if (stepsPerClickDownBtn != null) stepsPerClickDownBtn.setEnabled(false);
        if (abortButton != null) abortButton.setEnabled(false);
        if (syncBtn != null) syncBtn.setEnabled(false);
        if (setAbsPosButton != null) setAbsPosButton.setEnabled(false);
        if (positionField != null) {
            positionField.setEnabled(false);
            positionField.setText(R.string.unavailable);
        }
        if (speedSlider != null) speedSlider.setEnabled(false);
    }

    @Override
    public void onFocuserFunctionsChange() {
        INDIFocuser focuser = getFocuser();
        if (focuser == null) {
            disableControls();
            return;
        }
        boolean canMoveRelative = focuser.canMoveRelative();
        if (inButton != null) inButton.setEnabled(canMoveRelative);
        if (outButton != null) outButton.setEnabled(canMoveRelative);
        if (stepsPerClickField != null) {
            stepsPerClickField.setEnabled(canMoveRelative);
            if (canMoveRelative) {
                int steps = (int) (double) focuser.relPositionE.getValue();
                if (steps == 0) steps = 10;
                stepsPerClickField.setText(String.valueOf(steps));
                stepsHandler.setValue(steps);
            } else {
                stepsPerClickField.setText(R.string.unavailable);
            }
        }
        if (stepsPerClickUpBtn != null) stepsPerClickUpBtn.setEnabled(canMoveRelative);
        if (stepsPerClickDownBtn != null) stepsPerClickDownBtn.setEnabled(canMoveRelative);
        if (abortButton != null) abortButton.setEnabled(focuser.canAbort());
        boolean canSync = focuser.canSync();
        if (syncBtn != null) syncBtn.setEnabled(canSync);
        boolean hasAbsolutePosition = focuser.hasAbsolutePosition();
        if (setAbsPosButton != null) setAbsPosButton.setEnabled(hasAbsolutePosition);
        if (positionField != null) {
            positionField.setEnabled(hasAbsolutePosition || canSync);
            if (hasAbsolutePosition) {
                positionField.setText(String.valueOf((int) (double) focuser.absPositionE.getValue()));
            } else if (canSync) {
                positionField.setText(String.valueOf((int) (double) focuser.syncPositionE.getValue()));
            } else {
                positionField.setText(R.string.unavailable);
            }
        }
        if (speedSlider != null) {
            boolean hasSpeed = focuser.hasSpeed();
            speedSlider.setEnabled(hasSpeed);
            if (hasSpeed) NumberPropPref.setSliderValues(speedSlider, focuser.speedE);
        }
    }

    @Override
    public void onFocuserPositionChange(int position) {
        if (positionField != null) positionField.setText(String.valueOf(position));
    }

    @Override
    public void onFocuserSpeedChange(int value) {
        INDIFocuser focuser = getFocuser();
        if ((speedSlider != null) && (focuser != null))
            NumberPropPref.setSliderValues(speedSlider, (float) focuser.speedE.getMin(),
                    (float) focuser.speedE.getMax(), (float) focuser.speedE.getStep(), value);
    }

    @Override
    public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
        if (fromUser) {
            INDIFocuser focuser = getFocuser();
            if ((slider == speedSlider) && (focuser != null)) {
                try {
                    focuser.setSpeed(speedSlider.getValue());
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    errorSnackbar(e);
                }
            }
        }
    }

    private class FocusersArrayAdapter extends SimpleAdapter {

        FocusersArrayAdapter() {
            super(inflater);
        }

        @Override
        public int getCount() {
            return focusers.size();
        }

        @Override
        public INDIFocuser getItem(int position) {
            return focusers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return focusers.get(position).hashCode();
        }

        @Override
        protected String getStringAt(int position) {
            return focusers.get(position).toString();
        }
    }
}