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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.indilib.i4j.Constants;
import org.indilib.i4j.INDIBLOBValue;
import org.indilib.i4j.INDIException;
import org.indilib.i4j.client.INDIBLOBElement;
import org.indilib.i4j.client.INDIBLOBProperty;
import org.indilib.i4j.client.INDIDevice;
import org.indilib.i4j.client.INDINumberElement;
import org.indilib.i4j.client.INDINumberProperty;
import org.indilib.i4j.client.INDIProperty;
import org.indilib.i4j.client.INDIPropertyListener;
import org.indilib.i4j.client.INDISwitchElement;
import org.indilib.i4j.client.INDISwitchProperty;
import org.indilib.i4j.client.INDITextElement;
import org.indilib.i4j.client.INDITextProperty;
import org.indilib.i4j.client.INDIValueException;
import org.indilib.i4j.properties.INDIStandardElement;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.marcocipriani01.telescopetouch.BuildConfig;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;

import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

public class INDICamera implements INDIPropertyListener, Parcelable {

    public static final Parcelable.Creator<INDICamera> CREATOR = new Parcelable.Creator<INDICamera>() {
        @Override
        public INDICamera createFromParcel(Parcel in) {
            int deviceHash = in.readInt();
            synchronized (connectionManager.indiCameras) {
                Set<INDIDevice> devices = connectionManager.indiCameras.keySet();
                for (INDIDevice device : devices) {
                    if (device.hashCode() == deviceHash)
                        return connectionManager.indiCameras.get(device);
                }
            }
            return null;
        }

        @Override
        public INDICamera[] newArray(int size) {
            return new INDICamera[size];
        }
    };
    private static final String TAG = TelescopeTouchApp.getTag(INDICamera.class);
    public final INDIDevice device;
    private final Context context;
    private final Handler uiHandler;
    private final Set<CameraListener> listeners = new HashSet<>();
    public volatile INDIBLOBProperty blobP;
    public volatile INDIBLOBElement blobE;
    public volatile INDINumberProperty exposureP;
    public volatile INDINumberElement exposureE;
    public volatile INDISwitchProperty exposurePresetsP;
    public volatile INDISwitchElement[] exposurePresetsE;
    public volatile INDISwitchElement[] availableExposurePresetsE;
    public volatile INDISwitchProperty abortP;
    public volatile INDISwitchElement abortE;
    public volatile INDISwitchProperty forceBulbP;
    public volatile INDISwitchElement forceBulbOnE;
    public volatile INDISwitchElement forceBulbOffE;
    public volatile INDINumberProperty binningP;
    public volatile INDINumberElement binningXE;
    public volatile INDINumberElement binningYE;
    public volatile INDINumberProperty gainP;
    public volatile INDINumberElement gainE;
    public volatile INDISwitchProperty isoP;
    public volatile INDISwitchElement[] isoE;
    public volatile INDISwitchProperty uploadModeP;
    public volatile INDISwitchElement uploadLocalE;
    public volatile INDISwitchElement uploadClientE;
    public volatile INDISwitchElement uploadBothE;
    public volatile INDITextProperty uploadSettingsP;
    public volatile INDITextElement uploadDirE;
    public volatile INDITextElement uploadPrefixE;
    public volatile INDISwitchProperty frameTypeP;
    public volatile INDISwitchElement[] frameTypesE;
    public volatile INDISwitchProperty formatP;
    public volatile INDISwitchElement[] formatsE;
    public volatile INDISwitchProperty transferFormatP;
    public volatile INDISwitchElement[] transferFormatsE;
    private volatile Thread loadingThread = null;
    private volatile INDIBLOBValue queuedValue = null;
    private volatile boolean stretch = false;
    private volatile Bitmap lastBitmap = null;
    private volatile boolean bitmapSaved = false;
    private volatile SaveMode saveMode = SaveMode.SHOW_ONLY;
    private volatile int jpgQuality = 100;
    private volatile boolean captureLoop = false;
    private volatile INDISwitchElement captureLoopPreset = null;
    private volatile double captureLoopExposure = -1;
    private volatile String[] metadata = new String[]{null, null, null, null};
    private volatile int loopDelay = 0;
    private volatile int loopTotalCaptures = 0;
    private volatile int loopRemainingCaptures = 0;
    private volatile String filePrefix = null;

    public INDICamera(INDIDevice device, Context context, Handler uiHandler) {
        this.device = device;
        this.context = context;
        this.uiHandler = uiHandler;
    }

    public static boolean isCameraProp(INDIProperty<?> property) {
        String name = property.getName();
        return name.startsWith("CCD_") || name.startsWith("CAPTURE_");
    }

    private static int findFITSLineValue(String in) {
        if (in.contains("=")) in = in.split("=")[1];
        Matcher matcher = Pattern.compile("[0-9]+").matcher(in);
        if (matcher.find())
            return Integer.parseInt(matcher.group());
        return -1;
    }

    public int getRemainingCaptures() {
        return loopRemainingCaptures;
    }

    public int getLoopTotalCaptures() {
        return loopTotalCaptures;
    }

    public String getFilePrefix() {
        if (!hasUploadSettings())
            throw new UnsupportedOperationException("Unsupported upload settings!");
        if (filePrefix == null) filePrefix = uploadPrefixE.getValue().replace("_XXX", "");
        return filePrefix;
    }

    public boolean hasFormats() {
        return (formatP != null) && (formatsE != null);
    }

    public boolean hasTransferFormats() {
        return (transferFormatP != null) && (transferFormatsE != null);
    }

    public int getLoopDelay() {
        return loopDelay;
    }

    public void setLoopDelay(int loopDelay) {
        this.loopDelay = loopDelay;
    }

    public String[] getLastMetadata() {
        return metadata;
    }

    public boolean isCaptureLooping() {
        return captureLoop;
    }

    public void startCaptureLoop(double captureLoopExposure, int count) throws INDIValueException {
        if (!canCapture())
            throw new UnsupportedOperationException("Unsupported capture loop!");
        if (captureLoopExposure <= 0) throw new IllegalArgumentException("Negative exposure time!");
        if (count < 0) throw new IllegalArgumentException("Invalid loop count!");
        this.captureLoopExposure = captureLoopExposure;
        this.captureLoopPreset = null;
        this.loopRemainingCaptures = this.loopTotalCaptures = count;
        if (hasForceBulb()) {
            forceBulbOffE.setDesiredValue(Constants.SwitchStatus.OFF);
            forceBulbOnE.setDesiredValue(Constants.SwitchStatus.ON);
            connectionManager.updateProperties(forceBulbP);
        }
        captureLoop = true;
        connectionManager.post(this::captureLoopExposureRunnable);
        startProgressNotification();
    }

    public void startCaptureLoop(INDISwitchElement captureLoopPreset, int count) throws INDIValueException {
        if ((!canCapture()) || (!hasPresets()))
            throw new UnsupportedOperationException("Unsupported capture loop!");
        if (count < 0) throw new IllegalArgumentException("Invalid loop count!");
        this.captureLoopExposure = -1;
        this.captureLoopPreset = Objects.requireNonNull(captureLoopPreset);
        this.loopRemainingCaptures = this.loopTotalCaptures = count;
        if (hasForceBulb()) {
            forceBulbOnE.setDesiredValue(Constants.SwitchStatus.OFF);
            forceBulbOffE.setDesiredValue(Constants.SwitchStatus.ON);
            connectionManager.updateProperties(forceBulbP);
        }
        captureLoop = true;
        connectionManager.post(this::captureLoopPresetRunnable);
        startProgressNotification();
    }

    public void startCaptureLoop(String exposureOrPreset, int count) throws INDIException {
        exposureOrPreset = exposureOrPreset.trim();
        boolean canCapture = canCapture(), hasPresets = hasPresets();
        if (canCapture && (!hasPresets)) {
            startCaptureLoop(Double.parseDouble(exposureOrPreset), count);
        } else if (hasPresets && (!canCapture)) {
            INDISwitchElement e = stringToCameraPreset(exposureOrPreset);
            if (e == null) {
                throw new INDIException("Camera preset not found!");
            } else {
                startCaptureLoop(e, count);
            }
        } else if (canCapture) {
            INDISwitchElement e = stringToCameraPreset(exposureOrPreset);
            if (e == null) {
                startCaptureLoop(Double.parseDouble(exposureOrPreset), count);
            } else {
                startCaptureLoop(e, count);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported capture!");
        }
    }

    private void startProgressNotification() {
        Intent intent = new Intent(context, CameraForegroundService.class);
        intent.setAction(CameraForegroundService.SERVICE_START);
        intent.putExtra(CameraForegroundService.INDI_CAMERA_EXTRA, this);
        ContextCompat.startForegroundService(context, intent);
    }

    @SuppressLint("DefaultLocale")
    private void captureLoopExposureRunnable() {
        try {
            if (captureLoop && canCapture()) {
                if (hasUploadSettings()) {
                    if (hasFrameTypes()) {
                        setUploadPrefixSpecial(getFilePrefix(), String.format("%.2f", captureLoopExposure));
                    } else {
                        setUploadPrefixSpecial(getFilePrefix(), String.format("%.2f", captureLoopExposure), getSelectedFrameType());
                    }
                }
                exposureE.setDesiredValue(captureLoopExposure);
                exposureP.sendChangesToDriver();
            } else {
                cameraLoopStop();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            cameraLoopStop();
        }
    }

    private void captureLoopPresetRunnable() {
        try {
            if (captureLoop && canCapture() && hasPresets()) {
                if (hasUploadSettings()) {
                    if (hasFrameTypes()) {
                        setUploadPrefixSpecial(getFilePrefix(), captureLoopPreset.getLabel().replace("/", "over"));
                    } else {
                        setUploadPrefixSpecial(getFilePrefix(), captureLoopPreset.getLabel().replace("/", "over"), getSelectedFrameType());
                    }
                }
                for (INDISwitchElement e : exposurePresetsE) {
                    e.setDesiredValue((e == captureLoopPreset) ? Constants.SwitchStatus.ON : Constants.SwitchStatus.OFF);
                }
                exposurePresetsP.sendChangesToDriver();
            } else {
                cameraLoopStop();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            cameraLoopStop();
        }
    }

    private void cameraLoopStop() {
        captureLoop = false;
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (CameraListener listener : listeners) {
                    listener.onCameraLoopStop();
                }
            }
        });
    }

    public void setJpgQuality(int jpgQuality) {
        this.jpgQuality = jpgQuality;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isBitmapSaved() {
        return bitmapSaved;
    }

    public SaveMode getSaveMode() {
        return saveMode;
    }

    public void setSaveMode(final SaveMode mode) {
        if (!hasBLOB()) throw new UnsupportedOperationException("Unsupported BLOBs!");
        this.saveMode = mode;
        if (hasUploadModes()) {
            try {
                switch (saveMode) {
                    case REMOTE_SAVE:
                        blobP.removeINDIPropertyListener(this);
                        uploadClientE.setDesiredValue(Constants.SwitchStatus.OFF);
                        uploadBothE.setDesiredValue(Constants.SwitchStatus.OFF);
                        uploadLocalE.setDesiredValue(Constants.SwitchStatus.ON);
                        break;
                    case REMOTE_SAVE_AND_SHOW:
                        blobP.addINDIPropertyListener(this);
                        uploadClientE.setDesiredValue(Constants.SwitchStatus.OFF);
                        uploadLocalE.setDesiredValue(Constants.SwitchStatus.OFF);
                        uploadBothE.setDesiredValue(Constants.SwitchStatus.ON);
                        break;
                    case SAVE_JPG_AND_SHOW:
                    case SHOW_ONLY:
                        blobP.addINDIPropertyListener(this);
                        uploadBothE.setDesiredValue(Constants.SwitchStatus.OFF);
                        uploadLocalE.setDesiredValue(Constants.SwitchStatus.OFF);
                        uploadClientE.setDesiredValue(Constants.SwitchStatus.ON);
                        break;
                }
                connectionManager.updateProperties(uploadModeP);
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
                cameraError(e);
            }
        } else if (saveMode == SaveMode.REMOTE_SAVE) {
            blobP.removeINDIPropertyListener(this);
        } else {
            blobP.addINDIPropertyListener(this);
        }
        connectionManager.post(() -> {
            try {
                device.blobsEnable((mode == SaveMode.REMOTE_SAVE) ? Constants.BLOBEnables.NEVER : Constants.BLOBEnables.ALSO, blobP);
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
                cameraError(e);
            }
        });
    }

    private void cameraError(Throwable throwable) {
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (CameraListener listener : listeners) {
                    listener.onCameraError(throwable);
                }
            }
        });
    }

    public void stopReceiving() {
        connectionManager.post(() -> {
            try {
                device.blobsEnable(Constants.BLOBEnables.NEVER);
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
                cameraError(e);
            }
        });
    }

    public Uri saveImage() throws IOException {
        if (lastBitmap == null) throw new IllegalStateException("No Bitmap in memory!");
        Uri uri = saveImage(lastBitmap);
        if (uri == null) {
            return null;
        } else {
            bitmapSaved = true;
            return uri;
        }
    }

    @SuppressLint("SimpleDateFormat")
    @SuppressWarnings({"ResultOfMethodCallIgnored", "SameParameterValue"})
    public Uri saveImage(@NonNull Bitmap bitmap) throws IOException {
        String folderName = context.getString(R.string.app_name),
                fileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        OutputStream stream;
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + File.separator + folderName);
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            stream = resolver.openOutputStream(uri);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                uiHandler.post(() -> {
                    synchronized (listeners) {
                        for (CameraListener listener : listeners) {
                            listener.onRequestStoragePermission();
                        }
                    }
                });
                return null;
            }
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + File.separator + folderName);
            if (!dir.exists()) dir.mkdir();
            File file = new File(dir, fileName + ".jpg");
            uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
            stream = new FileOutputStream(file);
            MediaScannerConnection.scanFile(context, new String[]{dir.getPath()}, null, null);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + File.separator + folderName);
            if (!dir.exists()) dir.mkdir();
            File file = new File(dir, fileName + ".jpg");
            uri = Uri.fromFile(file);
            stream = new FileOutputStream(file);
            MediaScannerConnection.scanFile(context, new String[]{dir.getPath()}, null, null);
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpgQuality, stream);
        stream.flush();
        stream.close();
        return uri;
    }

    @Override
    public synchronized void propertyChanged(INDIProperty<?> indiProperty) {
        if (indiProperty == blobP) {
            if (listeners.isEmpty()) return;
            queuedValue = blobE.getValue();
            if ((loadingThread == null) || (!loadingThread.isAlive())) startProcessing();
        } else if (indiProperty == exposureP) {
            final Constants.PropertyStates state = indiProperty.getState();
            if (captureLoop) {
                if (loopTotalCaptures == 0) {
                    switch (state) {
                        case OK:
                            if ((captureLoopExposure != -1) && (captureLoopPreset == null)) {
                                connectionManager.postDelayed(this::captureLoopExposureRunnable, loopDelay);
                                break;
                            } else if ((captureLoopPreset != null) && (captureLoopExposure == -1)) {
                                connectionManager.postDelayed(this::captureLoopPresetRunnable, loopDelay);
                                break;
                            }
                        case ALERT:
                        case IDLE:
                            cameraLoopStop();
                            break;
                    }
                    uiHandler.post(() -> {
                        synchronized (listeners) {
                            for (CameraListener listener : listeners) {
                                listener.onCameraLoopStateChange(state);
                            }
                        }
                    });
                } else if (loopRemainingCaptures <= 1) {
                    cameraLoopStop();
                } else {
                    switch (state) {
                        case OK:
                            if ((captureLoopExposure != -1) && (captureLoopPreset == null)) {
                                connectionManager.postDelayed(this::captureLoopExposureRunnable, loopDelay);
                            } else if ((captureLoopPreset != null) && (captureLoopExposure == -1)) {
                                connectionManager.postDelayed(this::captureLoopPresetRunnable, loopDelay);
                            } else {
                                break;
                            }
                            loopRemainingCaptures--;
                            uiHandler.post(() -> {
                                synchronized (listeners) {
                                    for (CameraListener listener : listeners) {
                                        listener.onLoopProgressChange(loopTotalCaptures - loopRemainingCaptures, loopTotalCaptures);
                                    }
                                }
                            });
                            break;
                        case ALERT:
                        case IDLE:
                            cameraLoopStop();
                            break;
                    }
                    uiHandler.post(() -> {
                        synchronized (listeners) {
                            for (CameraListener listener : listeners) {
                                listener.onCameraLoopStateChange(state);
                            }
                        }
                    });
                }
            } else {
                uiHandler.post(() -> {
                    synchronized (listeners) {
                        for (CameraListener listener : listeners) {
                            listener.onCameraStateChange(state);
                        }
                    }
                });
            }
        }
    }

    public boolean canCapture() {
        return (exposureP != null) && (exposureE != null);
    }

    public boolean canAbort() {
        return (abortP != null) && (abortE != null);
    }

    public boolean hasBinning() {
        return (binningP != null) && (binningXE != null) && (binningYE != null);
    }

    public boolean hasPresets() {
        return (exposurePresetsP != null) && (exposurePresetsE != null);
    }

    public boolean hasISO() {
        return (isoP != null) && (isoE != null);
    }

    public INDISwitchElement getSelectedISO() {
        if (!hasISO()) throw new UnsupportedOperationException("Unsupported ISO!");
        for (INDISwitchElement e : isoE) {
            if (e.getValue() == Constants.SwitchStatus.ON) return e;
        }
        return null;
    }

    public INDISwitchElement getSelectedFormat() {
        if (!hasFormats()) throw new UnsupportedOperationException("Unsupported formats!");
        for (INDISwitchElement e : formatsE) {
            if (e.getValue() == Constants.SwitchStatus.ON) return e;
        }
        return null;
    }

    public INDISwitchElement getSelectedTransferFormat() {
        if (!hasTransferFormats())
            throw new UnsupportedOperationException("Unsupported transfer formats!");
        for (INDISwitchElement e : transferFormatsE) {
            if (e.getValue() == Constants.SwitchStatus.ON) return e;
        }
        return null;
    }

    public INDISwitchElement getSelectedPreset() {
        if (!hasPresets()) throw new UnsupportedOperationException("Unsupported camera presets!");
        for (INDISwitchElement e : availableExposurePresetsE) {
            if (e.getValue() == Constants.SwitchStatus.ON) return e;
        }
        return null;
    }

    public INDISwitchElement getSelectedFrameType() {
        if (!hasFrameTypes()) throw new UnsupportedOperationException("Unsupported frame types!");
        for (INDISwitchElement e : frameTypesE) {
            if (e.getValue() == Constants.SwitchStatus.ON) return e;
        }
        return null;
    }

    public boolean hasBLOB() {
        return (blobP != null) && (blobE != null);
    }

    public boolean hasForceBulb() {
        return (forceBulbP != null) && (forceBulbOnE != null) && (forceBulbOffE != null);
    }

    public boolean hasFrameTypes() {
        return (frameTypeP != null) && (frameTypesE != null);
    }

    public boolean hasGain() {
        return (gainP != null) && (gainE != null);
    }

    public boolean hasUploadModes() {
        return (uploadModeP != null) && (uploadLocalE != null) && (uploadClientE != null) && (uploadBothE != null);
    }

    public boolean hasUploadSettings() {
        return (uploadSettingsP != null) && (uploadDirE != null) && (uploadPrefixE != null);
    }

    public void capture(String exposureOrPreset) throws INDIException {
        exposureOrPreset = exposureOrPreset.trim();
        boolean canCapture = canCapture(), hasPresets = hasPresets();
        if (canCapture && (!hasPresets)) {
            capture(Double.parseDouble(exposureOrPreset));
        } else if (hasPresets && (!canCapture)) {
            INDISwitchElement e = stringToCameraPreset(exposureOrPreset);
            if (e == null) {
                throw new INDIException("Camera preset not found!");
            } else {
                capture(e);
            }
        } else if (canCapture) {
            INDISwitchElement e = stringToCameraPreset(exposureOrPreset);
            if (e == null) {
                capture(Double.parseDouble(exposureOrPreset));
            } else {
                capture(e);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported capture!");
        }
    }

    private INDISwitchElement stringToCameraPreset(String preset) {
        for (INDISwitchElement e : availableExposurePresetsE) {
            if (e.getLabel().equals(preset)) return e;
        }
        return null;
    }

    public void capture(double exposure) throws INDIValueException {
        if (!canCapture()) throw new UnsupportedOperationException("Unsupported capture!");
        exposureE.setDesiredValue(exposure);
        if (hasForceBulb()) {
            forceBulbOffE.setDesiredValue(Constants.SwitchStatus.OFF);
            forceBulbOnE.setDesiredValue(Constants.SwitchStatus.ON);
            connectionManager.updateProperties(forceBulbP, exposureP);
        } else {
            connectionManager.updateProperties(exposureP);
        }
    }

    public void capture(INDISwitchElement preset) throws INDIValueException {
        Objects.requireNonNull(preset);
        if (!hasPresets()) throw new UnsupportedOperationException("Unsupported presets!");
        for (INDISwitchElement e : exposurePresetsE) {
            e.setDesiredValue((e == preset) ? Constants.SwitchStatus.ON : Constants.SwitchStatus.OFF);
        }
        if (hasForceBulb()) {
            forceBulbOnE.setDesiredValue(Constants.SwitchStatus.OFF);
            forceBulbOffE.setDesiredValue(Constants.SwitchStatus.ON);
            connectionManager.updateProperties(forceBulbP, exposurePresetsP);
        } else {
            connectionManager.updateProperties(exposurePresetsP);
        }
    }

    public void abort() throws INDIValueException {
        if (captureLoop) cameraLoopStop();
        if (canAbort()) {
            abortE.setDesiredValue(Constants.SwitchStatus.ON);
            connectionManager.updateProperties(abortP);
        }
    }

    public void setGain(double gain) throws INDIValueException {
        if (!hasGain()) throw new UnsupportedOperationException("Unsupported gain!");
        gainE.setDesiredValue(gain);
        connectionManager.updateProperties(gainP);
    }

    public void setBinning(int binning) throws INDIValueException {
        if (!hasBinning()) throw new UnsupportedOperationException("Unsupported binning!");
        binningXE.setDesiredValue((double) binning);
        binningYE.setDesiredValue((double) binning);
        connectionManager.updateProperties(binningP);
    }

    public void setISO(INDISwitchElement iso) throws INDIValueException {
        if (!hasISO()) throw new UnsupportedOperationException("Unsupported ISO!");
        for (INDISwitchElement e : isoE) {
            e.setDesiredValue((e == iso) ? Constants.SwitchStatus.ON : Constants.SwitchStatus.OFF);
        }
        connectionManager.updateProperties(isoP);
    }

    public void setFormat(INDISwitchElement format) throws INDIValueException {
        if (!hasFormats()) throw new UnsupportedOperationException("Unsupported format!");
        for (INDISwitchElement e : formatsE) {
            e.setDesiredValue((e == format) ? Constants.SwitchStatus.ON : Constants.SwitchStatus.OFF);
        }
        connectionManager.updateProperties(formatP);
    }

    public void setTransferFormat(INDISwitchElement format) throws INDIValueException {
        if (!hasTransferFormats())
            throw new UnsupportedOperationException("Unsupported transfer format!");
        for (INDISwitchElement e : transferFormatsE) {
            e.setDesiredValue((e == format) ? Constants.SwitchStatus.ON : Constants.SwitchStatus.OFF);
        }
        connectionManager.updateProperties(transferFormatP);
    }

    public void setFrameType(INDISwitchElement frameType) throws INDIValueException {
        if (!hasFrameTypes()) throw new UnsupportedOperationException("Unsupported frame types!");
        for (INDISwitchElement e : frameTypesE) {
            e.setDesiredValue((e == frameType) ? Constants.SwitchStatus.ON : Constants.SwitchStatus.OFF);
        }
        connectionManager.updateProperties(frameTypeP);
    }

    public void setUploadDir(String uploadDir) throws INDIValueException {
        if (!hasUploadSettings())
            throw new UnsupportedOperationException("Unsupported upload settings!");
        uploadDirE.setDesiredValue(uploadDir);
        connectionManager.updateProperties(uploadSettingsP);
    }

    public void setUploadPrefix(String uploadPrefix) throws INDIValueException {
        if (!hasUploadSettings())
            throw new UnsupportedOperationException("Unsupported upload settings!");
        uploadPrefixE.setDesiredValue(uploadPrefix);
        connectionManager.updateProperties(uploadSettingsP);
    }

    @SuppressLint("SimpleDateFormat")
    public void setUploadPrefixSpecial(String base, String expTime) throws INDIValueException {
        this.filePrefix = base;
        Date date = new Date();
        setUploadPrefix(base.replace("%e", expTime).replace("%d", new SimpleDateFormat("yyyy-MM-dd").format(date))
                .replace("%t", new SimpleDateFormat("HH-mm").format(date)) + "_XXX");
    }

    @SuppressLint("SimpleDateFormat")
    public void setUploadPrefixSpecial(String base, String expTime, INDISwitchElement frameType) throws INDIValueException {
        this.filePrefix = base;
        Date date = new Date();
        setUploadPrefix(base.replace("%e", expTime).replace("%d", new SimpleDateFormat("yyyy-MM-dd").format(date))
                .replace("%t", new SimpleDateFormat("HH-mm").format(date)).replace("%f", frameType.getLabel().toLowerCase()) + "_XXX");
    }

    @SuppressWarnings("SuspiciousToArrayCall")
    public synchronized void processNewProp(INDIProperty<?> property) {
        String name = property.getName(), devName = device.getName();
        Log.i(TAG, "New Property (" + name + ") added to camera " + devName
                + ", elements: " + Arrays.toString(property.getElementNames()));
        switch (name) {
            case "CCD_EXPOSURE":
                if ((property instanceof INDINumberProperty) &&
                        ((exposureE = (INDINumberElement) property.getElement(INDIStandardElement.CCD_EXPOSURE_VALUE)) != null)) {
                    exposureP = (INDINumberProperty) property;
                    exposureP.addINDIPropertyListener(this);
                }
                break;
            case "CCD1":
                if ((property instanceof INDIBLOBProperty) && ((blobE = (INDIBLOBElement) property.getElement("CCD1")) != null)) {
                    blobP = (INDIBLOBProperty) property;
                }
                break;
            case "CCD_EXPOSURE_PRESETS":
                if (property instanceof INDISwitchProperty) {
                    List<?> presets = property.getElementsAsList();
                    exposurePresetsE = presets.toArray(new INDISwitchElement[0]);
                    final Iterator<?> iterator = presets.listIterator();
                    while (iterator.hasNext()) {
                        String label = ((INDISwitchElement) iterator.next()).getLabel().toLowerCase();
                        if (label.equals("time") || label.equals("bulb")) iterator.remove();
                    }
                    availableExposurePresetsE = presets.toArray(new INDISwitchElement[0]);
                    exposurePresetsP = (INDISwitchProperty) property;
                }
                break;
            case "CCD_ISO":
                if (property instanceof INDISwitchProperty) {
                    isoE = property.getElementsAsList().toArray(new INDISwitchElement[0]);
                    isoP = (INDISwitchProperty) property;
                }
                break;
            case "CCD_ABORT_EXPOSURE":
                if ((property instanceof INDISwitchProperty) && ((abortE = (INDISwitchElement) property.getElement(INDIStandardElement.ABORT)) != null)) {
                    abortP = (INDISwitchProperty) property;
                }
                break;
            case "CCD_GAIN":
                if ((property instanceof INDINumberProperty) &&
                        ((gainE = (INDINumberElement) property.getElement("GAIN")) != null)) {
                    gainP = (INDINumberProperty) property;
                }
                break;
            case "CCD_FORCE_BLOB":
                if ((property instanceof INDISwitchProperty) &&
                        ((forceBulbOnE = (INDISwitchElement) property.getElement("On")) != null) &&
                        ((forceBulbOffE = (INDISwitchElement) property.getElement("Off")) != null)) {
                    forceBulbP = (INDISwitchProperty) property;
                }
                break;
            case "CCD_BINNING":
                if (property instanceof INDINumberProperty &&
                        ((binningXE = (INDINumberElement) property.getElement(INDIStandardElement.HOR_BIN)) != null) &&
                        ((binningYE = (INDINumberElement) property.getElement(INDIStandardElement.VER_BIN)) != null)) {
                    binningP = (INDINumberProperty) property;
                }
                break;
            case "CCD_FRAME_TYPE":
                if (property instanceof INDISwitchProperty) {
                    frameTypesE = property.getElementsAsList().toArray(new INDISwitchElement[0]);
                    frameTypeP = (INDISwitchProperty) property;
                }
                break;
            case "UPLOAD_MODE":
                if ((property instanceof INDISwitchProperty) &&
                        ((uploadClientE = (INDISwitchElement) property.getElement("UPLOAD_CLIENT")) != null) &&
                        ((uploadLocalE = (INDISwitchElement) property.getElement("UPLOAD_LOCAL")) != null) &&
                        ((uploadBothE = (INDISwitchElement) property.getElement("UPLOAD_BOTH")) != null)) {
                    uploadModeP = (INDISwitchProperty) property;
                }
                break;
            case "UPLOAD_SETTINGS":
                if ((property instanceof INDITextProperty) &&
                        ((uploadDirE = (INDITextElement) property.getElement("UPLOAD_DIR")) != null) &&
                        ((uploadPrefixE = (INDITextElement) property.getElement("UPLOAD_PREFIX")) != null)) {
                    uploadSettingsP = (INDITextProperty) property;
                }
                break;
            case "CAPTURE_FORMAT":
                if (property instanceof INDISwitchProperty) {
                    formatsE = property.getElementsAsList().toArray(new INDISwitchElement[0]);
                    formatP = (INDISwitchProperty) property;
                }
            case "CCD_TRANSFER_FORMAT":
                if (property instanceof INDISwitchProperty) {
                    transferFormatsE = property.getElementsAsList().toArray(new INDISwitchElement[0]);
                    transferFormatP = (INDISwitchProperty) property;
                }
                break;
            default:
                return;
        }
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (CameraListener listener : listeners) {
                    listener.onCameraFunctionsChange();
                }
            }
        });
    }

    public synchronized boolean removeProp(INDIProperty<?> property) {
        switch (property.getName()) {
            case "CCD_EXPOSURE":
                exposureP.removeINDIPropertyListener(this);
                exposureE = null;
                exposureP = null;
                break;
            case "CCD1":
                blobE = null;
                blobP = null;
                break;
            case "CCD_EXPOSURE_PRESETS":
                exposurePresetsP.removeINDIPropertyListener(this);
                availableExposurePresetsE = exposurePresetsE = null;
                exposurePresetsP = null;
                break;
            case "CCD_FORCE_BLOB":
                forceBulbP = null;
                forceBulbOnE = forceBulbOffE = null;
                break;
            case "CCD_GAIN":
                gainP = null;
                gainE = null;
                break;
            case "CCD_ISO":
                isoE = null;
                isoP = null;
                break;
            case "CCD_BINNING":
                binningXE = binningYE = null;
                binningP = null;
                break;
            case "CCD_FRAME_TYPE":
                frameTypesE = null;
                frameTypeP = null;
                break;
            case "UPLOAD_MODE":
                uploadClientE = uploadLocalE = uploadBothE = null;
                uploadModeP = null;
                break;
            case "UPLOAD_SETTINGS":
                uploadDirE = uploadPrefixE = null;
                uploadSettingsP = null;
                break;
            case "CAPTURE_FORMAT":
                formatP = null;
                formatsE = null;
            case "CCD_TRANSFER_FORMAT":
                transferFormatP = null;
                transferFormatsE = null;
                break;
            default:
                return false;
        }
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (CameraListener listener : listeners) {
                    listener.onCameraFunctionsChange();
                }
            }
        });
        return (blobP == null) && (exposureP == null) && (exposurePresetsP == null) &&
                (abortP == null) && (binningP == null) && (isoP == null) &&
                (forceBulbP == null) && (gainP == null);
    }

    public synchronized void terminate() {
        synchronized (listeners) {
            listeners.clear();
        }
        blobP = null;
        blobE = null;
        if (exposureP != null) {
            exposureP.removeINDIPropertyListener(this);
            exposureP = null;
        }
        exposureE = null;
        exposurePresetsP = null;
        exposurePresetsE = availableExposurePresetsE = null;
        abortP = null;
        abortE = null;
        forceBulbP = null;
        forceBulbOnE = forceBulbOffE = null;
        binningP = null;
        binningXE = binningYE = null;
        isoP = null;
        isoE = null;
        uploadModeP = null;
        uploadLocalE = uploadClientE = uploadBothE = null;
        uploadSettingsP = null;
        uploadDirE = uploadPrefixE = null;
        frameTypeP = null;
        frameTypesE = null;
        formatP = transferFormatP = null;
        formatsE = transferFormatsE = null;
    }

    @NonNull
    @Override
    public String toString() {
        return device.getName();
    }

    public boolean hasBitmap() {
        return lastBitmap != null;
    }

    public Bitmap getLastBitmap() {
        return lastBitmap;
    }

    public void freeMemory() {
        synchronized (listeners) {
            for (CameraListener listener : listeners) {
                listener.onBitmapDestroy();
            }
        }
        if (lastBitmap != null) {
            lastBitmap.recycle();
            lastBitmap = null;
        }
    }

    public synchronized void reloadBitmap() {
        if (!listeners.isEmpty() && (blobE != null)) {
            queuedValue = blobE.getValue();
            if ((loadingThread == null) || (!loadingThread.isAlive())) startProcessing();
        }
    }

    public void addListener(CameraListener listener) {
        synchronized (listeners) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(CameraListener listener) {
        synchronized (listeners) {
            this.listeners.remove(listener);
        }
    }

    private void onImageLoadingException(final Throwable throwable) {
        Log.e(TAG, throwable.getLocalizedMessage(), throwable);
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (CameraListener listener : listeners) {
                    listener.onImageLoadingError(throwable);
                }
            }
            if (lastBitmap != null) {
                lastBitmap.recycle();
                lastBitmap = null;
            }
        });
    }

    public void setStretch(boolean stretch) {
        this.stretch = stretch;
    }

    private synchronized void loadingFinished(Bitmap bitmap, String[] metadata) throws IOException {
        this.metadata = metadata;
        if ((saveMode == SaveMode.SAVE_JPG_AND_SHOW) && (bitmap != null)) {
            saveImage(bitmap);
            bitmapSaved = true;
        } else {
            bitmapSaved = false;
        }
        if (listeners.isEmpty()) {
            if (lastBitmap != null) {
                lastBitmap.recycle();
                lastBitmap = null;
            }
            if (bitmap != null) bitmap.recycle();
            return;
        }
        uiHandler.post(() -> {
            Bitmap oldBitmap = lastBitmap;
            lastBitmap = bitmap;
            synchronized (listeners) {
                for (CameraListener listener : listeners) {
                    listener.onImageLoaded(bitmap, metadata);
                }
            }
            if (oldBitmap != null) oldBitmap.recycle();
        });
        if (queuedValue != null) startProcessing();
    }

    private synchronized void startProcessing() {
        loadingThread = new LoadingThread(queuedValue);
        loadingThread.start();
        queuedValue = null;
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (CameraListener listener : listeners) {
                    listener.onImageLoading();
                }
            }
        });
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(device.hashCode());
    }

    public enum SaveMode {
        SHOW_ONLY(R.string.ccd_image_show_only),
        SAVE_JPG_AND_SHOW(R.string.ccd_image_save_show),
        REMOTE_SAVE(R.string.ccd_image_remote_save),
        REMOTE_SAVE_AND_SHOW(R.string.ccd_image_remote_and_display);

        private final int stringId;

        SaveMode(int stringId) {
            this.stringId = stringId;
        }

        public String toString(Context context) {
            return context.getString(stringId);
        }
    }

    public interface CameraListener {

        default void onCameraFunctionsChange() {
        }

        default void onRequestStoragePermission() {
        }

        default void onImageLoading() {
        }

        default void onImageLoaded(@Nullable Bitmap bitmap, String[] metadata) {
        }

        default void onBitmapDestroy() {
        }

        default void onImageLoadingError(Throwable e) {
        }

        default void onCameraError(Throwable e) {
        }

        default void onLoopProgressChange(int progress, int total) {
        }

        default void onCameraStateChange(Constants.PropertyStates state) {
        }

        default void onCameraLoopStateChange(Constants.PropertyStates state) {
        }

        default void onCameraLoopStop() {
        }
    }

    private class LoadingThread extends Thread {

        private final INDIBLOBValue blobValue;

        private LoadingThread(@NonNull INDIBLOBValue blobValue) {
            super("INDICamera loading thread");
            this.blobValue = blobValue;
        }

        @SuppressWarnings("SpellCheckingInspection")
        @SuppressLint("DefaultLocale")
        @Override
        public void run() {
            try {
                String format = blobValue.getFormat();
                int blobSize = blobValue.getSize();
                if (format.equals("") || (blobSize == 0))
                    throw new FileNotFoundException();
                String blobSizeString = String.format("%.2f MB", blobSize / 1000000.0);
                byte[] blobData = blobValue.getBlobData();
                if (format.equals(".fits") || format.equals(".fit") || format.equals(".fts")) {
                    try (InputStream stream = new ByteArrayInputStream(blobData)) {
                        int width = 0, height = 0;
                        byte bitPerPix = 0;
                        byte[] headerBuffer = new byte[80];
                        int extraByte = -1;
                        headerLoop:
                        while (stream.read(headerBuffer, 0, 80) != -1) {
                            String card = new String(headerBuffer);
                            if (card.contains("BITPIX")) {
                                bitPerPix = (byte) findFITSLineValue(card);
                            } else if (card.contains("NAXIS1")) {
                                width = findFITSLineValue(card);
                            } else if (card.contains("NAXIS2")) {
                                height = findFITSLineValue(card);
                            } else if (card.contains("NAXIS")) {
                                if (findFITSLineValue(card) != 2)
                                    throw new IndexOutOfBoundsException("Color FITS are not yet supported.");
                            } else if (card.startsWith("END ")) {
                                while (true) {
                                    extraByte = stream.read();
                                    if (((char) extraByte) != ' ') break headerLoop;
                                    if (stream.skip(79) != 79) throw new EOFException();
                                }
                            }
                        }
                        if ((bitPerPix == 0) || (width <= 0) || (height <= 0))
                            throw new IllegalStateException("Invalid FITS image");
                        if (bitPerPix == 32)
                            throw new UnsupportedOperationException("32 bit FITS are not yet supported.");
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        if (stretch) {
                            int[][] img = new int[width][height];
                            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                            if (bitPerPix == 8) {
                                for (int h = 0; h < height; h++) {
                                    for (int w = 0; w < width; w++) {
                                        int val;
                                        if (extraByte == -1) {
                                            val = stream.read();
                                        } else {
                                            val = extraByte;
                                            extraByte = -1;
                                        }
                                        img[w][h] = val;
                                        if (val > max) max = val;
                                        if (min > val) min = val;
                                    }
                                }
                            } else if (bitPerPix == 16) {
                                for (int h = 0; h < height; h++) {
                                    for (int w = 0; w < width; w++) {
                                        int val;
                                        if (extraByte == -1) {
                                            val = (stream.read() << 8) | stream.read();
                                        } else {
                                            val = (extraByte << 8) | stream.read();
                                            extraByte = -1;
                                        }
                                        img[w][h] = val;
                                        if (val > max) max = val;
                                        if (min > val) min = val;
                                    }
                                }
                            }
                            double logMin = Math.log10(min), multiplier = 255.0 / (Math.log10(max) - logMin);
                            for (int w = 0; w < width; w++) {
                                for (int h = 0; h < height; h++) {
                                    int interpolation = (int) ((Math.log10(img[w][h]) - logMin) * multiplier);
                                    bitmap.setPixel(w, h, Color.rgb(interpolation, interpolation, interpolation));
                                }
                            }
                        } else {
                            if (bitPerPix == 8) {
                                for (int h = 0; h < height; h++) {
                                    for (int w = 0; w < width; w++) {
                                        int val;
                                        if (extraByte == -1) {
                                            val = stream.read();
                                        } else {
                                            val = extraByte;
                                            extraByte = -1;
                                        }
                                        bitmap.setPixel(w, h, Color.rgb(val, val, val));
                                    }
                                }
                            } else if (bitPerPix == 16) {
                                for (int h = 0; h < height; h++) {
                                    for (int w = 0; w < width; w++) {
                                        int val;
                                        if (extraByte == -1) {
                                            val = (stream.read() << 8) | stream.read();
                                        } else {
                                            val = (extraByte << 8) | stream.read();
                                            extraByte = -1;
                                        }
                                        val /= 257;
                                        bitmap.setPixel(w, h, Color.rgb(val, val, val));
                                    }
                                }
                            }
                        }
                        loadingFinished(bitmap, new String[]{
                                blobSizeString, width + "x" + height, format, String.valueOf(bitPerPix)});
                    }
                } else {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(blobData, 0, blobSize);
                    if (bitmap == null) {
                        loadingFinished(null, new String[]{blobSizeString, null, format, null});
                    } else {
                        loadingFinished(bitmap, new String[]{
                                blobSizeString, bitmap.getWidth() + "x" + bitmap.getHeight(), format,
                                (format.equals(".jpg") || format.equals(".jpeg")) ? "8" : null});
                    }
                }
            } catch (Throwable t) {
                onImageLoadingException(t);
            }
        }
    }
}