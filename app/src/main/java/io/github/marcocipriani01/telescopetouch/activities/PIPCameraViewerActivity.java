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

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.util.Collection;

import io.github.marcocipriani01.livephotoview.PhotoView;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.activities.util.DarkerModeManager;
import io.github.marcocipriani01.telescopetouch.indi.ConnectionManager;
import io.github.marcocipriani01.telescopetouch.indi.INDICamera;

public class PIPCameraViewerActivity extends AppCompatActivity
        implements INDICamera.CameraListener, ConnectionManager.ManagerListener {

    public static final String INDI_CAMERA_EXTRA = "indi_camera";
    @SuppressLint("StaticFieldLeak")
    private static PIPCameraViewerActivity instance;
    private INDICamera camera;
    private PhotoView photoView;
    private DarkerModeManager darkerModeManager;

    public static boolean isVisible() {
        return instance != null;
    }

    public static boolean isSupported(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
        return false;
    }

    public static void finishInstance() {
        if (instance != null) instance.finish();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        camera = getIntent().getParcelableExtra(INDI_CAMERA_EXTRA);
        if (camera == null) {
            Toast.makeText(this, R.string.no_camera_available, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            pip();
            setContentView(R.layout.activity_pip_blob);
            darkerModeManager = new DarkerModeManager(this, null, PreferenceManager.getDefaultSharedPreferences(this));
            photoView = findViewById(R.id.pip_blob_view);
            photoView.setMaximumScale(20f);
            photoView.setImageBitmap(camera.getLastBitmap());
        }
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isInPictureInPictureMode()) pip();
        }
    }

    private void pip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational ratio;
            Bitmap bitmap = camera.getLastBitmap();
            if (bitmap == null) {
                ratio = new Rational(1, 1);
            } else {
                ratio = new Rational(bitmap.getWidth(), bitmap.getHeight());
            }
            enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(ratio).build());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            enterPictureInPictureMode();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (((AppOpsManager) getSystemService(Context.APP_OPS_SERVICE))
                .checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), getPackageName())
                != AppOpsManager.MODE_ALLOWED) {
            Toast.makeText(this, R.string.pip_permission_required, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        camera.addListener(this);
        connectionManager.addManagerListener(this);
        instance = this;
    }

    @Override
    public void onResume() {
        super.onResume();
        darkerModeManager.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        darkerModeManager.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        camera.removeListener(this);
        connectionManager.removeManagerListener(this);
        instance = null;
    }

    @Override
    public void onImageLoaded(@Nullable Bitmap bitmap, String[] metadata) {
        if (photoView != null) photoView.setImageBitmap(bitmap);
    }

    @Override
    public void onImageLoadingError(Throwable e) {
        if (photoView != null) photoView.setImageBitmap(null);
    }

    @Override
    public void onBitmapDestroy() {
        if (photoView != null) photoView.setImageBitmap(null);
    }

    @Override
    public void onConnectionLost() {
        if (photoView != null) photoView.setImageBitmap(null);
        finishInstance();
    }

    @Override
    public void onCamerasListChange() {
        synchronized (connectionManager.indiCameras) {
            Collection<INDICamera> cameras = connectionManager.indiCameras.values();
            for (INDICamera camera : cameras) {
                if (camera == this.camera) return;
            }
        }
        finishInstance();
    }
}