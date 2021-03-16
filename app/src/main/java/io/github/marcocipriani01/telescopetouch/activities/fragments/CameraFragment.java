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

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.preference.PreferenceManager;

import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;

import org.indilib.i4j.Constants;
import org.indilib.i4j.client.INDIDevice;
import org.indilib.i4j.client.INDISwitchElement;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.marcocipriani01.livephotoview.PhotoView;
import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.ProUtils;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.activities.PIPCameraViewerActivity;
import io.github.marcocipriani01.telescopetouch.activities.util.ImprovedSpinnerListener;
import io.github.marcocipriani01.telescopetouch.activities.util.SimpleAdapter;
import io.github.marcocipriani01.telescopetouch.indi.ConnectionManager;
import io.github.marcocipriani01.telescopetouch.indi.INDICamera;

import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.CCD_LOOP_DELAY_PREF;
import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

public class CameraFragment extends ActionFragment implements INDICamera.CameraListener,
        CompoundButton.OnCheckedChangeListener, Toolbar.OnMenuItemClickListener, ConnectionManager.ManagerListener {

    private static final String TAG = TelescopeTouchApp.getTag(CameraFragment.class);
    private static INDIDevice selectedCameraDev = null;
    private final List<INDICamera> cameras = new ArrayList<>();
    private SharedPreferences preferences;
    private SwitchCompat fitsStretchSwitch;
    private TextView fileSizeText;
    private TextView dimensionsText;
    private TextView formatText;
    private TextView bppText;
    private TextView errorText;
    private PhotoView photoViewer;
    private ProgressBar progressBar;
    private Button exposeBtn;
    private Button abortBtn;
    private Button loopBtn;
    private Spinner isoSpinner;
    private Spinner binningSpinner;
    private Spinner frameTypeSpinner;
    private Spinner saveModeSpinner;
    private AutoCompleteTextView exposureTimeField;
    private EditText prefixField;
    private EditText remoteFolderField;
    private Slider gainSlider;
    private Slider countSlider;
    private Spinner formatSpinner;
    private Spinner transferFormatSpinner;
    private TextView logText;
    private Slider delaySlider;
    private final ImprovedSpinnerListener cameraSelectListener = new ImprovedSpinnerListener() {
        @Override
        protected void onImprovedItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            INDICamera camera = getCamera();
            if (camera != null) {
                camera.removeListener(CameraFragment.this);
                camera.stopReceiving();
            }
            selectedCameraDev = cameras.get(pos).device;
            camera = getCamera();
            if (camera == null) {
                logText.setText("-");
            } else {
                camera.addListener(CameraFragment.this);
                onImageLoaded(camera.getLastBitmap(), camera.getLastMetadata());
                String lastMessage = camera.device.getLastMessage();
                logText.setText(((lastMessage == null) || (lastMessage.equals(""))) ? "-" : lastMessage);
            }
            onCameraFunctionsChange();
        }
    };
    private boolean storagePermissionRequested = false;
    private Spinner cameraSelectSpinner;
    private CamerasArrayAdapter cameraSelectAdapter;
    private boolean pipSupported = false;
    private MenuItem pipMenuItem = null;
    private LayoutInflater inflater;
    private InputMethodManager inputMethodManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        this.inflater = inflater;
        View rootView = inflater.inflate(R.layout.fragment_camera, container, false);
        pipSupported = PIPCameraViewerActivity.isSupported(context);
        if (pipSupported) setHasOptionsMenu(true);
        fitsStretchSwitch = rootView.findViewById(R.id.ccd_image_stretch_switch);
        fileSizeText = rootView.findViewById(R.id.blob_size);
        dimensionsText = rootView.findViewById(R.id.blob_dimensions);
        formatText = rootView.findViewById(R.id.blob_format);
        bppText = rootView.findViewById(R.id.blob_bpp);
        errorText = rootView.findViewById(R.id.blob_error_label);
        photoViewer = rootView.findViewById(R.id.blob_viewer);
        photoViewer.setMaximumScale(20f);
        progressBar = rootView.findViewById(R.id.blob_loading);
        exposeBtn = rootView.findViewById(R.id.ccd_expose_button);
        exposeBtn.setOnClickListener(this::capture);
        abortBtn = rootView.findViewById(R.id.ccd_abort_btn);
        abortBtn.setOnClickListener(this::abortCapture);
        loopBtn = rootView.findViewById(R.id.ccd_loop_btn);
        loopBtn.setOnClickListener(this::loopCapture);
        gainSlider = rootView.findViewById(R.id.ccd_gain_slider);
        delaySlider = rootView.findViewById(R.id.ccd_loop_delay_slider);
        isoSpinner = rootView.findViewById(R.id.ccd_iso_spinner);
        binningSpinner = rootView.findViewById(R.id.ccd_binning_spinner);
        frameTypeSpinner = rootView.findViewById(R.id.ccd_frame_type_spinner);
        saveModeSpinner = rootView.findViewById(R.id.ccd_image_receive_mode);
        saveModeSpinner.setAdapter(new SaveModeAdapter());
        prefixField = rootView.findViewById(R.id.ccd_file_prefix_field);
        remoteFolderField = rootView.findViewById(R.id.ccd_remote_folder_field);
        exposureTimeField = rootView.findViewById(R.id.ccd_exposure_time_field);
        countSlider = rootView.findViewById(R.id.ccd_loop_count_slider);
        formatSpinner = rootView.findViewById(R.id.ccd_format_spinner);
        transferFormatSpinner = rootView.findViewById(R.id.ccd_transfer_format_spinner);
        logText = rootView.findViewById(R.id.ccd_log_label);
        NestedScrollView captureTab = rootView.findViewById(R.id.ccd_viewer_capture_tab);
        View viewTab = rootView.findViewById(R.id.ccd_viewer_image_tab);
        rootView.<TabLayout>findViewById(R.id.ccd_viewer_tabs)
                .addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {
                        if (tab.getPosition() == 0) {
                            captureTab.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(Animator animation) {
                                    captureTab.setVisibility(View.VISIBLE);
                                }
                            });
                            viewTab.animate().alpha(0f).setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    viewTab.setVisibility(View.GONE);
                                }
                            });
                        } else {
                            captureTab.stopNestedScroll();
                            captureTab.animate().alpha(0f).setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    captureTab.setVisibility(View.GONE);
                                }
                            });
                            viewTab.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(Animator animation) {
                                    viewTab.setVisibility(View.VISIBLE);
                                    showActionbar();
                                }
                            });
                        }
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {

                    }

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {

                    }
                });
        cameraSelectAdapter = new CamerasArrayAdapter();
        cameraSelectSpinner = rootView.findViewById(R.id.ccd_selection_spinner);
        cameraSelectSpinner.setAdapter(cameraSelectAdapter);
        cameraSelectListener.attach(cameraSelectSpinner);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        boolean stretch = preferences.getBoolean(ApplicationConstants.STRETCH_FITS_PREF, false);
        fitsStretchSwitch.setOnCheckedChangeListener(null);
        fitsStretchSwitch.setChecked(stretch);
        fitsStretchSwitch.setSelected(stretch);
        fitsStretchSwitch.setOnCheckedChangeListener(this);
        delaySlider.setValue(preferences.getInt(CCD_LOOP_DELAY_PREF, 1));

        connectionManager.addManagerListener(this);
        cameras.clear();
        if (connectionManager.isConnected()) {
            synchronized (connectionManager.indiCameras) {
                cameras.addAll(connectionManager.indiCameras.values());
            }
            cameraSelectAdapter.notifyDataSetChanged();
            if (cameras.isEmpty()) {
                selectedCameraDev = null;
                cameraSelectSpinner.setEnabled(false);
            } else {
                INDICamera selectedCamera = cameras.get(0);
                selectedCameraDev = selectedCamera.device;
                selectedCamera.addListener(this);
                selectedCamera.setStretch(stretch);
                cameraSelectSpinner.setSelection(cameras.indexOf(selectedCamera));
                cameraSelectSpinner.setEnabled(true);
                Bitmap lastBitmap = selectedCamera.getLastBitmap();
                photoViewer.setImageBitmap(lastBitmap);
                if (lastBitmap == null) {
                    errorText.setText(R.string.no_incoming_data);
                    photoViewer.setVisibility(View.GONE);
                    errorText.setVisibility(View.VISIBLE);
                } else {
                    photoViewer.setVisibility(View.VISIBLE);
                    errorText.setVisibility(View.GONE);
                }
            }
        } else {
            selectedCameraDev = null;
            cameraSelectAdapter.notifyDataSetChanged();
            cameraSelectSpinner.setEnabled(false);
            errorText.setText(R.string.no_incoming_data);
            photoViewer.setVisibility(View.GONE);
            errorText.setVisibility(View.VISIBLE);
        }
        onCameraFunctionsChange();
        progressBar.setVisibility(View.INVISIBLE);
        notifyActionChange();
    }

    @Override
    public void onStop() {
        super.onStop();
        connectionManager.removeManagerListener(this);
        photoViewer.setImageBitmap(null);
        for (INDICamera camera : cameras) {
            camera.removeListener(this);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.camera, menu);
        if (pipSupported) {
            pipMenuItem = menu.add(R.string.floating_image);
            pipMenuItem.setIcon(R.drawable.picture_in_picture);
            pipMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    private INDICamera getCamera() {
        if (selectedCameraDev == null) return null;
        return connectionManager.indiCameras.get(selectedCameraDev);
    }

    public void capture(View v) {
        // PRO
        if (!ProUtils.isPro) {
            int count = preferences.getInt(ProUtils.CAPTURE_PRO_COUNTER, 0);
            if (count >= ProUtils.MAX_CAPTURES) {
                requestActionSnack(R.string.buy_pro_continue_capture);
                synchronized (connectionManager.indiCameras) {
                    Collection<INDICamera> cameras = connectionManager.indiCameras.values();
                    for (INDICamera camera : cameras) {
                        camera.stopReceiving();
                    }
                }
                return;
            }
            preferences.edit().putInt(ProUtils.CAPTURE_PRO_COUNTER, count + 1).apply();
        }
        // END PRO
        String str = exposureTimeField.getText().toString();
        INDICamera camera = getCamera();
        if (camera == null) {
            inputMethodManager.hideSoftInputFromWindow(exposureTimeField.getWindowToken(), 0);
            requestActionSnack(R.string.no_camera_available);
            onCameraFunctionsChange();
        } else if (str.isEmpty()) {
            exposureTimeField.requestFocus();
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        } else {
            inputMethodManager.hideSoftInputFromWindow(exposureTimeField.getWindowToken(), 0);
            try {
                camera.setSaveMode((INDICamera.SaveMode) saveModeSpinner.getSelectedItem());
                INDISwitchElement frameType = null;
                try {
                    camera.setJpgQuality(Integer.parseInt(preferences.getString(ApplicationConstants.JPG_QUALITY_PREF, "100")));
                } catch (NumberFormatException e) {
                    Log.e(TAG, e.getMessage(), e);
                    camera.setJpgQuality(100);
                }
                if (camera.hasFrameTypes())
                    camera.setFrameType(frameType = (INDISwitchElement) frameTypeSpinner.getSelectedItem());
                if (camera.hasBinning())
                    camera.setBinning(((Integer) binningSpinner.getSelectedItem()));
                if (camera.hasISO())
                    camera.setISO(((INDISwitchElement) isoSpinner.getSelectedItem()));
                if (camera.hasGain())
                    camera.setGain(gainSlider.getValue());
                if (camera.hasFormats())
                    camera.setFormat(((INDISwitchElement) formatSpinner.getSelectedItem()));
                if (camera.hasTransferFormats())
                    camera.setTransferFormat(((INDISwitchElement) transferFormatSpinner.getSelectedItem()));
                if (camera.hasUploadSettings()) {
                    String prefix = prefixField.getText().toString();
                    if (!prefix.equals("")) {
                        if (frameType == null) {
                            camera.setUploadPrefixSpecial(prefix, str.replace("/", "over"));
                        } else {
                            camera.setUploadPrefixSpecial(prefix, str.replace("/", "over"), frameType);
                        }
                    }
                    String dir = remoteFolderField.getText().toString();
                    if (!dir.equals("")) camera.setUploadDir(dir);
                }
                camera.capture(str);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                requestActionSnack(context.getString(R.string.error) + " " + e.getLocalizedMessage());
            }
        }
    }

    public void abortCapture(View v) {
        inputMethodManager.hideSoftInputFromWindow(exposureTimeField.getWindowToken(), 0);
        INDICamera camera = getCamera();
        if (camera == null) {
            requestActionSnack(R.string.no_camera_available);
            onCameraFunctionsChange();
        } else {
            try {
                camera.abort();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                requestActionSnack(context.getString(R.string.error) + " " + e.getLocalizedMessage());
            }
        }
    }

    public void loopCapture(View v) {
        // PRO
        if (!ProUtils.isPro) {
            int count = preferences.getInt(ProUtils.CAPTURE_PRO_COUNTER, 0);
            if (count >= ProUtils.MAX_CAPTURES) {
                requestActionSnack(R.string.buy_pro_continue_capture);
                synchronized (connectionManager.indiCameras) {
                    Collection<INDICamera> cameras = connectionManager.indiCameras.values();
                    for (INDICamera camera : cameras) {
                        camera.stopReceiving();
                    }
                }
                return;
            }
            preferences.edit().putInt(ProUtils.CAPTURE_PRO_COUNTER, count + 1).apply();
        }
        // END PRO
        String str = exposureTimeField.getText().toString();
        INDICamera camera = getCamera();
        if (camera == null) {
            inputMethodManager.hideSoftInputFromWindow(exposureTimeField.getWindowToken(), 0);
            requestActionSnack(R.string.no_camera_available);
            onCameraFunctionsChange();
        } else if (str.isEmpty()) {
            exposureTimeField.requestFocus();
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        } else {
            inputMethodManager.hideSoftInputFromWindow(exposureTimeField.getWindowToken(), 0);
            int loopDelay = (int) delaySlider.getValue();
            preferences.edit().putInt(CCD_LOOP_DELAY_PREF, loopDelay).apply();
            setButtonColor(exposeBtn, Color.WHITE);
            exposeBtn.setEnabled(false);
            loopBtn.setEnabled(false);
            try {
                camera.setSaveMode((INDICamera.SaveMode) saveModeSpinner.getSelectedItem());
                INDISwitchElement frameType = null;
                try {
                    camera.setJpgQuality(Integer.parseInt(preferences.getString(ApplicationConstants.JPG_QUALITY_PREF, "100")));
                } catch (NumberFormatException e) {
                    Log.e(TAG, e.getMessage(), e);
                    camera.setJpgQuality(100);
                }
                if (camera.hasFrameTypes())
                    camera.setFrameType(frameType = (INDISwitchElement) frameTypeSpinner.getSelectedItem());
                if (camera.hasBinning())
                    camera.setBinning(((Integer) binningSpinner.getSelectedItem()));
                if (camera.hasISO())
                    camera.setISO(((INDISwitchElement) isoSpinner.getSelectedItem()));
                if (camera.hasGain())
                    camera.setGain(gainSlider.getValue());
                if (camera.hasFormats())
                    camera.setFormat(((INDISwitchElement) formatSpinner.getSelectedItem()));
                if (camera.hasTransferFormats())
                    camera.setTransferFormat(((INDISwitchElement) transferFormatSpinner.getSelectedItem()));
                if (camera.hasUploadSettings()) {
                    String prefix = prefixField.getText().toString();
                    if (!prefix.equals("")) {
                        if (frameType == null) {
                            camera.setUploadPrefixSpecial(prefix, str.replace("/", "-"));
                        } else {
                            camera.setUploadPrefixSpecial(prefix, str.replace("/", "-"), frameType);
                        }
                    }
                    if (!prefix.equals("")) camera.setUploadPrefix(prefix);
                    String dir = remoteFolderField.getText().toString();
                    if (!dir.equals("")) camera.setUploadDir(dir);
                }
                camera.setLoopDelay(loopDelay * 1000);
                camera.startCaptureLoop(exposureTimeField.getText().toString(), (int) countSlider.getValue());
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                requestActionSnack(context.getString(R.string.error) + " " + e.getLocalizedMessage());
                onCameraLoopStop();
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item == pipMenuItem) {
            if (ProUtils.isPro) {
                if (PIPCameraViewerActivity.isVisible()) {
                    PIPCameraViewerActivity.finishInstance();
                } else if (((AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE))
                        .checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(),
                                context.getPackageName()) != AppOpsManager.MODE_ALLOWED) {
                    requestActionSnack(R.string.pip_permission_required);
                } else {
                    INDICamera camera = getCamera();
                    if (camera == null) {
                        requestActionSnack(R.string.no_camera_selected);
                    } else {
                        Intent intent = new Intent(context, PIPCameraViewerActivity.class);
                        intent.putExtra(PIPCameraViewerActivity.INDI_CAMERA_EXTRA, camera);
                        startActivity(intent);
                    }
                }
            } else {
                requestActionSnack(R.string.pro_feature);
            }
            return true;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.menu_free_ram) {
            synchronized (connectionManager.indiCameras) {
                Collection<INDICamera> cameras = connectionManager.indiCameras.values();
                for (INDICamera camera : cameras) {
                    camera.freeMemory();
                }
            }
            return true;
        } else if (itemId == R.id.menu_stop_receiving_camera) {
            synchronized (connectionManager.indiCameras) {
                Collection<INDICamera> cameras = connectionManager.indiCameras.values();
                for (INDICamera camera : cameras) {
                    camera.stopReceiving();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == fitsStretchSwitch) {
            if (ProUtils.isPro) {
                INDICamera camera = getCamera();
                if (camera != null) {
                    camera.setStretch(isChecked);
                    camera.reloadBitmap();
                }
                preferences.edit().putBoolean(ApplicationConstants.STRETCH_FITS_PREF, isChecked).apply();
            } else {
                fitsStretchSwitch.setOnCheckedChangeListener(null);
                fitsStretchSwitch.setSelected(false);
                fitsStretchSwitch.setChecked(false);
                fitsStretchSwitch.setOnCheckedChangeListener(this);
                ProUtils.toast(context);
            }
        }
    }

    @Override
    public boolean isActionEnabled() {
        INDICamera camera = getCamera();
        if (camera == null) return false;
        return camera.hasBitmap() && (!camera.isBitmapSaved());
    }

    @Override
    public int getActionDrawable() {
        return R.drawable.save;
    }

    @Override
    @SuppressLint("SimpleDateFormat")
    public void run() {
        INDICamera camera = getCamera();
        if (camera == null) return;
        if (camera.hasBitmap() && (!camera.isBitmapSaved())) {
            try {
                Uri uri = Objects.requireNonNull(getCamera()).saveImage();
                if (uri != null) {
                    notifyActionChange();
                    requestActionSnack(R.string.saved_snackbar, R.string.view_image, v -> {
                        Intent intent = new Intent();
                        intent.setDataAndType(uri, "image/*");
                        intent.setAction(Intent.ACTION_VIEW);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, context.getString(R.string.open_photo)));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Saving error", e);
                requestActionSnack(R.string.saving_error);
            }
        } else {
            requestActionSnack(R.string.nothing_to_save);
        }
    }

    private void setBlobInfo(String[] info) {
        if (info == null) {
            fileSizeText.setText(R.string.unknown);
            dimensionsText.setText(R.string.unknown);
            formatText.setText(R.string.unknown);
            bppText.setText(R.string.unknown);
        } else if (info.length != 4) {
            throw new IllegalArgumentException();
        } else {
            String unknown = context.getString(R.string.unknown);
            fileSizeText.setText((info[0] == null) ? unknown : info[0]);
            dimensionsText.setText((info[1] == null) ? unknown : info[1]);
            formatText.setText((info[2] == null) ? unknown : info[2]);
            bppText.setText((info[3] == null) ? unknown : info[3]);
        }
    }

    private void onError(int errorMsg) {
        setBlobInfo(null);
        errorText.setText(errorMsg);
        photoViewer.setVisibility(View.GONE);
        progressBar.setVisibility(View.INVISIBLE);
        errorText.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestStoragePermission() {
        if (storagePermissionRequested) {
            requestActionSnack(R.string.storage_permission_required);
        } else {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            storagePermissionRequested = true;
        }
    }

    @Override
    public void onCameraStateChange(Constants.PropertyStates state) {
        if (exposeBtn == null) return;
        int color = Color.WHITE;
        switch (state) {
            case OK:
                exposeBtn.setEnabled(true);
                color = context.getResources().getColor(R.color.light_green);
                break;
            case ALERT:
                exposeBtn.setEnabled(true);
                color = context.getResources().getColor(R.color.light_red);
                break;
            case BUSY:
                exposeBtn.setEnabled(false);
                color = context.getResources().getColor(R.color.light_yellow);
                break;
            case IDLE:
                exposeBtn.setEnabled(true);
                break;
        }
        setButtonColor(exposeBtn, color);
    }

    @Override
    public void onCameraLoopStateChange(Constants.PropertyStates state) {
        int color = Color.WHITE;
        switch (state) {
            case OK:
                color = context.getResources().getColor(R.color.light_green);
                break;
            case ALERT:
                color = context.getResources().getColor(R.color.light_red);
                break;
            case BUSY:
                color = context.getResources().getColor(R.color.light_yellow);
                break;
        }
        setButtonColor(loopBtn, color);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onLoopProgressChange(int progress, int total) {
        if (loopBtn != null)
            loopBtn.setText(context.getString(R.string.loop) + " (" + progress + "/" + total + ")");
    }

    private void setButtonColor(Button btn, int color) {
        if (btn != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                btn.getCompoundDrawables()[0].setColorFilter(new BlendModeColorFilter(color, BlendMode.SRC_ATOP));
            } else {
                btn.getCompoundDrawables()[0].setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
            btn.setTextColor(color);
        }
    }

    @Override
    public void onCameraFunctionsChange() {
        INDICamera camera = getCamera();
        if (camera == null) {
            disableControls();
            return;
        }
        if (saveModeSpinner != null) saveModeSpinner.setSelection(camera.getSaveMode().ordinal());
        boolean canCapture = camera.canCapture(), hasPresets = camera.hasPresets(), captureOrPreset = canCapture || hasPresets,
                canLoop = captureOrPreset && (!camera.isCaptureLooping());
        if (exposeBtn != null) {
            exposeBtn.setEnabled(canLoop);
            if (canCapture) {
                int color = Color.WHITE;
                switch (camera.exposureP.getState()) {
                    case OK:
                        color = context.getResources().getColor(R.color.light_green);
                        break;
                    case ALERT:
                        color = context.getResources().getColor(R.color.light_red);
                        break;
                    case BUSY:
                        color = context.getResources().getColor(R.color.light_yellow);
                        break;
                }
                setButtonColor(exposeBtn, color);
            }
        }
        if (loopBtn != null) {
            loopBtn.setEnabled(canLoop);
            loopBtn.setText(R.string.loop);
            setButtonColor(loopBtn, Color.WHITE);
        }
        if (delaySlider != null) {
            delaySlider.setEnabled(canLoop);
            delaySlider.setValue(camera.getLoopDelay() / 1000f);
        }
        if (countSlider != null) {
            countSlider.setEnabled(canLoop);
            countSlider.setValue(camera.getLoopTotalCaptures());
        }
        if (exposureTimeField != null) {
            exposureTimeField.setEnabled(captureOrPreset);
            exposureTimeField.setAdapter(hasPresets ? new SwitchPropertyStringAdapter(camera.availableExposurePresetsE) : null);
            if (canCapture) {
                try {
                    String expString = camera.exposureE.getValueAsString().trim();
                    if (!expString.equals("0.00")) exposureTimeField.setText(expString);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            } else if (hasPresets) {
                INDISwitchElement selectedPreset = camera.getSelectedPreset();
                if (selectedPreset != null) exposureTimeField.setText(selectedPreset.getLabel());
            }
        }
        if (abortBtn != null) abortBtn.setEnabled(true);
        if (gainSlider != null) {
            boolean hasGain = camera.hasGain();
            gainSlider.setEnabled(hasGain);
            if (hasGain) {
                gainSlider.setValueFrom((float) camera.gainE.getMin());
                gainSlider.setValueTo((float) camera.gainE.getMax());
                gainSlider.setStepSize((float) camera.gainE.getStep());
                gainSlider.setValue((float) (double) camera.gainE.getValue());
            }
        }
        if (isoSpinner != null) {
            boolean hasISO = camera.hasISO();
            isoSpinner.setEnabled(hasISO);
            if (hasISO) {
                SwitchPropertyAdapter adapter = new SwitchPropertyAdapter(camera.isoE);
                isoSpinner.setAdapter(adapter);
                INDISwitchElement selectedISO = camera.getSelectedISO();
                if (selectedISO != null) isoSpinner.setSelection(adapter.list.indexOf(selectedISO));
            } else {
                isoSpinner.setAdapter(null);
            }
        }
        if (formatSpinner != null) {
            boolean hasFormats = camera.hasFormats();
            formatSpinner.setEnabled(hasFormats);
            if (hasFormats) {
                SwitchPropertyAdapter adapter = new SwitchPropertyAdapter(camera.formatsE);
                formatSpinner.setAdapter(adapter);
                INDISwitchElement selectedFormat = camera.getSelectedFormat();
                if (selectedFormat != null)
                    formatSpinner.setSelection(adapter.list.indexOf(selectedFormat));
            } else {
                formatSpinner.setAdapter(null);
            }
        }
        if (transferFormatSpinner != null) {
            boolean hasTransferFormats = camera.hasTransferFormats();
            transferFormatSpinner.setEnabled(hasTransferFormats);
            if (hasTransferFormats) {
                SwitchPropertyAdapter adapter = new SwitchPropertyAdapter(camera.transferFormatsE);
                transferFormatSpinner.setAdapter(adapter);
                INDISwitchElement selectedFormat = camera.getSelectedTransferFormat();
                if (selectedFormat != null)
                    transferFormatSpinner.setSelection(adapter.list.indexOf(selectedFormat));
            } else {
                transferFormatSpinner.setAdapter(null);
            }
        }
        if (frameTypeSpinner != null) {
            boolean hasFrameTypes = camera.hasFrameTypes();
            frameTypeSpinner.setEnabled(hasFrameTypes);
            if (hasFrameTypes) {
                SwitchPropertyAdapter adapter = new SwitchPropertyAdapter(camera.frameTypesE);
                frameTypeSpinner.setAdapter(adapter);
                INDISwitchElement frameType = camera.getSelectedFrameType();
                if (frameType != null)
                    frameTypeSpinner.setSelection(adapter.list.indexOf(frameType));
            } else {
                frameTypeSpinner.setAdapter(null);
            }
        }
        if (binningSpinner != null) {
            boolean hasBinning = camera.hasBinning();
            binningSpinner.setEnabled(hasBinning);
            binningSpinner.setAdapter(hasBinning ? new BinningValuesAdapter() : null);
        }
        boolean hasUploadSettings = camera.hasUploadSettings();
        if (prefixField != null) {
            prefixField.setEnabled(hasUploadSettings);
            prefixField.setText(hasUploadSettings ? camera.getFilePrefix() : "");
        }
        if (remoteFolderField != null) {
            remoteFolderField.setEnabled(hasUploadSettings);
            remoteFolderField.setText(hasUploadSettings ? camera.uploadDirE.getValue() : "");
        }
    }

    private void disableControls() {
        if (exposeBtn != null) {
            exposeBtn.setEnabled(false);
            setButtonColor(exposeBtn, Color.WHITE);
        }
        if (loopBtn != null) {
            loopBtn.setEnabled(false);
            setButtonColor(loopBtn, Color.WHITE);
        }
        if (delaySlider != null) delaySlider.setEnabled(false);
        if (countSlider != null) countSlider.setEnabled(false);
        if (exposureTimeField != null) {
            exposureTimeField.setEnabled(false);
            exposureTimeField.setAdapter(null);
        }
        if (abortBtn != null) abortBtn.setEnabled(false);
        if (gainSlider != null) gainSlider.setEnabled(false);
        if (isoSpinner != null) {
            isoSpinner.setEnabled(false);
            isoSpinner.setAdapter(null);
        }
        if (formatSpinner != null) {
            formatSpinner.setEnabled(false);
            formatSpinner.setAdapter(null);
        }
        if (transferFormatSpinner != null) {
            transferFormatSpinner.setEnabled(false);
            transferFormatSpinner.setAdapter(null);
        }
        if (frameTypeSpinner != null) {
            frameTypeSpinner.setEnabled(false);
            frameTypeSpinner.setAdapter(null);
        }
        if (binningSpinner != null) {
            binningSpinner.setEnabled(false);
            binningSpinner.setAdapter(null);
        }
        if (prefixField != null) prefixField.setEnabled(false);
        if (remoteFolderField != null) remoteFolderField.setEnabled(false);
    }

    @Override
    public void onCameraLoopStop() {
        INDICamera camera = getCamera();
        if (camera == null) {
            disableControls();
            return;
        }
        boolean captureOrPreset = camera.canCapture() || camera.hasPresets();
        if (exposeBtn != null) exposeBtn.setEnabled(captureOrPreset);
        if (loopBtn != null) {
            loopBtn.setText(R.string.loop);
            loopBtn.setEnabled(captureOrPreset);
            setButtonColor(loopBtn, Color.WHITE);
        }
        if (delaySlider != null) delaySlider.setEnabled(captureOrPreset);
        if (countSlider != null) countSlider.setEnabled(captureOrPreset);
    }

    @Override
    public void onImageLoading() {
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onImageLoaded(Bitmap bitmap, String[] metadata) {
        setBlobInfo(metadata);
        if (bitmap == null) {
            errorText.setText(R.string.unsupported_format);
            photoViewer.setVisibility(View.GONE);
            errorText.setVisibility(View.VISIBLE);
        } else {
            photoViewer.setImageBitmap(bitmap);
            photoViewer.setVisibility(View.VISIBLE);
            errorText.setVisibility(View.GONE);
        }
        progressBar.setVisibility(View.INVISIBLE);
        notifyActionChange();
    }

    @Override
    public void onBitmapDestroy() {
        photoViewer.setImageBitmap(null);
        notifyActionChange();
        onError(R.string.no_incoming_data);
    }

    @Override
    public void onImageLoadingError(Throwable e) {
        photoViewer.setImageBitmap(null);
        notifyActionChange();
        if (e instanceof Error) {
            onError(R.string.out_of_memory);
        } else if (e instanceof FileNotFoundException) {
            onError(R.string.no_incoming_data);
        } else if (e instanceof EOFException) {
            onError(R.string.truncated_file);
        } else if (e instanceof IndexOutOfBoundsException) {
            onError(R.string.unsupported_color_fits);
        } else if (e instanceof UnsupportedOperationException) {
            onError(R.string.unsupported_bit_depth);
        } else if (e instanceof IllegalStateException) {
            onError(R.string.invalid_fits_image);
        } else {
            onError(R.string.unknown_exception);
        }
    }

    @Override
    public void onCameraError(Throwable e) {
        requestActionSnack(context.getString(R.string.error) + " " + e.getLocalizedMessage());
    }

    @Override
    public void onCamerasListChange() {
        cameras.clear();
        synchronized (connectionManager.indiCameras) {
            cameras.addAll(connectionManager.indiCameras.values());
        }
        if (cameraSelectAdapter != null)
            cameraSelectAdapter.notifyDataSetChanged();
        if (cameraSelectSpinner != null)
            cameraSelectSpinner.setEnabled(!cameras.isEmpty());
        onCameraFunctionsChange();
    }

    @Override
    public void onConnectionLost() {
        selectedCameraDev = null;
        cameras.clear();
        if (cameraSelectAdapter != null)
            cameraSelectAdapter.notifyDataSetChanged();
        if (cameraSelectSpinner != null)
            cameraSelectSpinner.setEnabled(false);
        disableControls();
    }

    @Override
    public void deviceLog(ConnectionManager.LogItem log) {
        if (logText == null) return;
        INDICamera camera = getCamera();
        if (camera == null) return;
        if (log.getDevice() == camera.device) logText.setText(log.getLog());
    }

    private static class SwitchPropFilter extends Filter {

        private final SwitchPropertyAdapter adapter;

        SwitchPropFilter(SwitchPropertyAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            final ArrayList<INDISwitchElement> filtered = new ArrayList<>();
            FilterResults results = new FilterResults();
            if (constraint == null) {
                filtered.addAll(Arrays.asList(adapter.array));
            } else {
                String filterString = constraint.toString().trim().toLowerCase();
                for (INDISwitchElement el : adapter.array) {
                    if (el.getLabel().toLowerCase().contains(filterString)) filtered.add(el);
                }
            }
            results.values = filtered;
            results.count = filtered.size();
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            adapter.list.clear();
            if (results.values != null)
                adapter.list.addAll((List<INDISwitchElement>) results.values);
            adapter.notifyDataSetChanged();
        }
    }

    private class CamerasArrayAdapter extends SimpleAdapter {

        CamerasArrayAdapter() {
            super(inflater);
        }

        @Override
        public int getCount() {
            return cameras.size();
        }

        @Override
        public INDICamera getItem(int position) {
            return cameras.get(position);
        }

        @Override
        public long getItemId(int position) {
            return cameras.get(position).hashCode();
        }

        @Override
        protected String getStringAt(int position) {
            return cameras.get(position).toString();
        }
    }

    private class SwitchPropertyAdapter extends SimpleAdapter implements Filterable {

        protected final ArrayList<INDISwitchElement> list = new ArrayList<>();
        private final INDISwitchElement[] array;

        SwitchPropertyAdapter(INDISwitchElement[] array) {
            super(inflater);
            this.array = array;
            Collections.addAll(list, array);
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return list.get(position).hashCode();
        }

        @Override
        protected String getStringAt(int position) {
            return list.get(position).getLabel();
        }

        @Override
        public Filter getFilter() {
            return new SwitchPropFilter(this);
        }
    }

    private class SwitchPropertyStringAdapter extends SwitchPropertyAdapter {

        SwitchPropertyStringAdapter(INDISwitchElement[] array) {
            super(array);
        }

        @Override
        public String getItem(int position) {
            return list.get(position).getLabel();
        }
    }

    private class BinningValuesAdapter extends SimpleAdapter {

        BinningValuesAdapter() {
            super(inflater);
        }

        @Override
        public int getCount() {
            INDICamera camera = getCamera();
            if (camera == null) return 0;
            return camera.hasBinning() ? ((int) camera.binningXE.getMax()) : 0;
        }

        @Override
        public Integer getItem(int position) {
            return position + 1;
        }

        @Override
        public long getItemId(int position) {
            return getStringAt(position).hashCode();
        }

        @Override
        protected String getStringAt(int position) {
            position++;
            return position + "x" + position;
        }
    }

    private class SaveModeAdapter extends SimpleAdapter {

        private final INDICamera.SaveMode[] values = INDICamera.SaveMode.values();

        SaveModeAdapter() {
            super(inflater);
        }

        @Override
        public int getCount() {
            return values.length;
        }

        @Override
        public INDICamera.SaveMode getItem(int position) {
            return values[position];
        }

        @Override
        public long getItemId(int position) {
            return values[position].ordinal();
        }

        @Override
        protected String getStringAt(int position) {
            return values[position].toString(context);
        }
    }
}