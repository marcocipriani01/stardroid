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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Vector;

import io.github.marcocipriani01.telescopetouch.BuildConfig;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.sftp.DirectoryElement;
import io.github.marcocipriani01.telescopetouch.sftp.FolderAdapter;

public class SFTPFolderActivity extends AppCompatActivity {

    public static final String EXTRA_REMOTE_FOLDER = "REMOTE_FOLDER";
    private static final String TAG = TelescopeTouchApp.getTag(SFTPFolderActivity.class);
    private static final int STORAGE_PERMISSION_REQUEST = 20;
    private final ArrayList<DirectoryElement> elements = new ArrayList<>();
    private final HandlerThread sftpThread = new HandlerThread("SFTP thread");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RecyclerView recyclerView;
    private Handler sftpHandler;
    private ActionBar actionBar;
    private FolderAdapter adapter;
    private CoordinatorLayout coordinator;
    private String currentPath;
    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent intent = result.getData();
                if ((result.getResultCode() == Activity.RESULT_OK) && (intent != null))
                    sftpHandler.post(new UploadTask(intent.getData(), currentPath));
            });
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentPath = Objects.requireNonNull(getIntent().getStringExtra(EXTRA_REMOTE_FOLDER));
        setContentView(R.layout.activity_folder);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        coordinator = findViewById(R.id.folder_activity_coordinator);
        actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        this.<FloatingActionButton>findViewById(R.id.upload_fab).setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            fileChooserLauncher.launch(intent);
        });

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_view);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            sftpHandler.post(new GetFilesTask());
            swipeRefreshLayout.setRefreshing(false);
        });

        recyclerView = findViewById(R.id.folder_list);
        adapter = new FolderAdapter(elements);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addOnItemTouchListener(new FolderItemListener(this, recyclerView));

        sftpThread.start();
        sftpHandler = new Handler(sftpThread.getLooper());
        sftpHandler.post(new GetFilesTask());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if ("/".equals(currentPath)) {
            finish();
        } else {
            sftpHandler.post(new GetFilesTask(".."));
        }
    }

    @Override
    public void finish() {
        super.finish();
        sftpThread.quit();
        TelescopeTouchApp.channel.disconnect();
        TelescopeTouchApp.channel = null;
        TelescopeTouchApp.session.disconnect();
        TelescopeTouchApp.session = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((requestCode == STORAGE_PERMISSION_REQUEST) && (grantResults[0] != PackageManager.PERMISSION_GRANTED))
            Snackbar.make(coordinator, R.string.storage_permission_required, Snackbar.LENGTH_SHORT).show();
    }

    private void wakeLock() {
        if (wakeLock != null) {
            try {
                wakeLock.acquire(10 * 60);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private void noWakeLock() {
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private class GetFilesTask implements Runnable {

        private final String TAG = TelescopeTouchApp.getTag(GetFilesTask.class);
        private final String targetPath;

        GetFilesTask() {
            this.targetPath = null;
        }

        GetFilesTask(String dir) {
            if (dir.equals("..")) { // Check if going up
                if (currentPath.equals("/")) {
                    this.targetPath = null;
                } else if (currentPath.endsWith("/")) {
                    String tmp = currentPath.substring(0, currentPath.lastIndexOf("/"));
                    this.targetPath = tmp.substring(0, tmp.lastIndexOf("/") + 1);
                } else {
                    this.targetPath = currentPath.substring(0, currentPath.lastIndexOf("/") + 1);
                }
            } else {
                if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 2);
                if (currentPath.equals("/") || currentPath.endsWith("/")) {
                    this.targetPath = currentPath + dir;
                } else {
                    this.targetPath = currentPath + "/" + dir;
                }
            }
            Log.d(TAG, "Going to " + dir + " (" + this.targetPath + ")");
        }

        @SuppressLint("NotifyDataSetChanged")
        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            try {
                Vector<ChannelSftp.LsEntry> list;
                if (targetPath == null) {
                    list = TelescopeTouchApp.channel.ls("*");
                } else {
                    TelescopeTouchApp.channel.cd(targetPath);
                    list = TelescopeTouchApp.channel.ls("*");
                    currentPath = targetPath;
                }
                handler.post(() -> {
                    elements.clear();
                    if (!currentPath.equals("/"))
                        elements.add(new DirectoryElement("..", true, 0, null));
                    for (ChannelSftp.LsEntry entry : list) {
                        elements.add(new DirectoryElement(entry.getFilename(), entry.getAttrs().isDir(),
                                entry.getAttrs().getSize(), entry));
                    }
                    Collections.sort(elements);
                    adapter.notifyDataSetChanged();
                    actionBar.setTitle(currentPath);
                    recyclerView.scheduleLayoutAnimation();
                });
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> Snackbar.make(coordinator,
                        String.format(getString(R.string.read_error), e.getMessage()), Snackbar.LENGTH_SHORT).show());
            }
        }
    }

    private class UploadTask implements Runnable {

        private final String TAG = TelescopeTouchApp.getTag(UploadTask.class);
        private final Uri file;
        private final String path;
        private final ProgressDialog progressDialog;

        UploadTask(Uri file, String remotePath) {
            this.file = file;
            this.path = remotePath;
            // Take CPU lock to prevent CPU from going off if the user presses the power button during download
            wakeLock();
            progressDialog = new ProgressDialog(SFTPFolderActivity.this);
            progressDialog.setMessage(String.format(getString(R.string.uploading), file.getLastPathSegment()));
            progressDialog.setMax(100);
            progressDialog.setIndeterminate(true);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        public void run() {
            String name = null;
            if (file.getScheme().equals("content")) {
                try (Cursor cursor = getContentResolver().query(file, null, null, null, null)) {
                    if ((cursor != null) && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (index >= 0) name = cursor.getString(index);
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
            if (name == null) {
                name = file.getPath();
                int cut = name.lastIndexOf('/');
                if (cut != -1) name = name.substring(cut + 1);
            }
            try (InputStream bis = getContentResolver().openInputStream(file);
                 BufferedOutputStream bos = new BufferedOutputStream(TelescopeTouchApp.channel.put(path + "/" + name))) {
                byte[] buffer = new byte[1024];
                Log.d(TAG, "Destination: " + path + "/" + name);
                int readCount;
                long progress = 0;
                long size = bis.available();
                Log.d(TAG, "Upload size: " + size);
                while ((readCount = bis.read(buffer)) > 0) {
                    bos.write(buffer, 0, readCount);
                    progress += buffer.length;
                    final int percentage = (int) (progress * 100 / size);
                    Log.d(TAG, "Writing: " + percentage + "%");
                    handler.post(() -> {
                        // If we get here, length is known, now set indeterminate to false
                        progressDialog.setIndeterminate(false);
                        progressDialog.setProgress(percentage);
                    });
                }
                handler.post(() -> {
                    sftpHandler.post(new GetFilesTask());
                    noWakeLock();
                    progressDialog.dismiss();
                    Snackbar.make(coordinator, R.string.file_uploaded, Snackbar.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> {
                    noWakeLock();
                    progressDialog.dismiss();
                    Snackbar.make(coordinator, String.format(getString(R.string.upload_error), e.getMessage()), Snackbar.LENGTH_SHORT).show();
                });
            }
        }
    }

    private class DownloadTask implements Runnable {

        private final String TAG = TelescopeTouchApp.getTag(DownloadTask.class);
        private final DirectoryElement element;
        private final ProgressDialog progressDialog;

        DownloadTask(DirectoryElement element) {
            this.element = element;
            // Take CPU lock to prevent CPU from going off if the user presses the power button during download
            wakeLock();
            progressDialog = new ProgressDialog(SFTPFolderActivity.this);
            progressDialog.setMessage(String.format(getString(R.string.downloading_message), element.shortName, element.sizeMB));
            progressDialog.setMax(100);
            progressDialog.setIndeterminate(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        public void run() {
            OutputStream stream = null;
            try (BufferedInputStream bis = new BufferedInputStream(TelescopeTouchApp.channel.get(element.name))) {
                Uri uri;
                String folderName;
                final boolean isImage;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = getContentResolver();
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, element.shortName);
                    if (element.getFileType() == DirectoryElement.FileType.IMAGE) {
                        isImage = true;
                        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/*");
                        folderName = Environment.DIRECTORY_DCIM + File.separator + getString(R.string.app_name);
                        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, folderName);
                        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                    } else {
                        isImage = false;
                        contentValues.put(MediaStore.Downloads.MIME_TYPE, MediaStore.Downloads.CONTENT_TYPE);
                        folderName = Environment.DIRECTORY_DOWNLOADS;
                        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, folderName);
                        uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                    }
                    stream = resolver.openOutputStream(uri);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    isImage = false;
                    folderName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() +
                            File.separator + getString(R.string.app_name);
                    File dir = new File(folderName);
                    if (!dir.exists()) dir.mkdir();
                    File file = new File(dir, element.shortName);
                    uri = FileProvider.getUriForFile(SFTPFolderActivity.this, BuildConfig.APPLICATION_ID + ".provider", file);
                    stream = new FileOutputStream(file);
                    MediaScannerConnection.scanFile(SFTPFolderActivity.this, new String[]{dir.getPath()}, null, null);
                } else {
                    isImage = false;
                    folderName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() +
                            File.separator + getString(R.string.app_name);
                    File dir = new File(folderName);
                    if (!dir.exists()) dir.mkdir();
                    File file = new File(dir, element.shortName);
                    uri = Uri.fromFile(file);
                    stream = new FileOutputStream(file);
                    MediaScannerConnection.scanFile(SFTPFolderActivity.this, new String[]{dir.getPath()}, null, null);
                }
                byte[] buffer = new byte[1024];
                int readCount;
                long progress = 0;
                long size = element.size;
                while ((readCount = bis.read(buffer)) > 0) {
                    stream.write(buffer, 0, readCount);
                    progress += buffer.length;
                    final int percentage = (int) (progress * 100 / size);
                    handler.post(() -> progressDialog.setProgress(percentage));
                }
                stream.flush();
                stream.close();
                handler.post(() -> {
                    noWakeLock();
                    progressDialog.dismiss();
                    Snackbar.make(coordinator, String.format(getString(R.string.file_downloaded_message),
                            folderName), Snackbar.LENGTH_SHORT).setAction(R.string.open, v -> {
                        Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_VIEW);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        if (isImage) {
                            intent.setDataAndType(uri, "image/*");
                            startActivity(Intent.createChooser(intent, getString(R.string.open_photo)));
                        } else {
                            intent.setDataAndType(uri, "*/*");
                            startActivity(Intent.createChooser(intent, getString(R.string.open_file)));
                        }
                    }).show();
                });
            } catch (Exception e) {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception ex) {
                        Log.e(TAG, e.getMessage(), ex);
                    }
                }
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> {
                    noWakeLock();
                    progressDialog.dismiss();
                    Snackbar.make(coordinator, String.format(getString(R.string.download_error), e.getMessage()),
                            Snackbar.LENGTH_SHORT).show();
                });
            }
        }
    }

    private class DeleteTask implements Runnable {

        private final String TAG = TelescopeTouchApp.getTag(DeleteTask.class);
        private final DirectoryElement element;

        DeleteTask(DirectoryElement element) {
            this.element = element;
        }

        @Override
        public void run() {
            try {
                String path = currentPath + (currentPath.endsWith("/") ? "" : "/") + element.name;
                if (element.isDirectory) {
                    recursiveFolderDelete(path);
                } else {
                    TelescopeTouchApp.channel.rm(path);
                }
                sftpHandler.post(new GetFilesTask());
                handler.post(() -> Snackbar.make(coordinator,
                        getString(R.string.deleted_ok), Snackbar.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> Snackbar.make(coordinator,
                        String.format(getString(R.string.delete_error), e.getMessage()),
                        Snackbar.LENGTH_SHORT).show());
            }
        }

        /**
         * @see <a href="https://stackoverflow.com/a/41490348/6267019">Source</a>
         */
        @SuppressWarnings("unchecked")
        private void recursiveFolderDelete(String path) throws SftpException {
            // List source directory structure.
            Collection<ChannelSftp.LsEntry> list = TelescopeTouchApp.channel.ls(path);
            // Iterate objects in the list to get file/folder names.
            for (ChannelSftp.LsEntry item : list) {
                String filename = item.getFilename();
                if (item.getAttrs().isDir()) {
                    if (!(".".equals(filename) || "..".equals(filename))) { // If it is a sub-directory
                        try {
                            TelescopeTouchApp.channel.rmdir(path + "/" + filename);
                        } catch (Exception e) {
                            // If sub-directory is not empty and error occurs,
                            // repeat on this directory to clear its contents.
                            recursiveFolderDelete(path + "/" + filename);
                        }
                    }
                } else {
                    TelescopeTouchApp.channel.rm(path + "/" + filename); // Remove file.
                }
            }
            TelescopeTouchApp.channel.rmdir(path); // Delete the parent directory after empty
        }
    }

    private class FolderItemListener implements RecyclerView.OnItemTouchListener {

        private final GestureDetector gestureDetector;

        FolderItemListener(Context context, RecyclerView recyclerView) {
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (child != null) {
                        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ||
                                (ContextCompat.checkSelfPermission(SFTPFolderActivity.this,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
                            DirectoryElement element = elements.get(recyclerView.getChildAdapterPosition(child));
                            if (element.isDirectory) {
                                new AlertDialog.Builder(SFTPFolderActivity.this).setTitle(R.string.app_name)
                                        .setMessage(String.format(getString(R.string.delete_folder_question), element.name))
                                        .setIcon(R.drawable.delete)
                                        .setPositiveButton(android.R.string.ok, (dialog, which) ->
                                                sftpHandler.post(new DeleteTask(element)))
                                        .setNegativeButton(android.R.string.cancel, null).show();
                            } else if (element.isLink()) {
                                Snackbar.make(coordinator, R.string.links_unsupported, Snackbar.LENGTH_SHORT).show();
                            } else {
                                new AlertDialog.Builder(SFTPFolderActivity.this).setTitle(R.string.app_name)
                                        .setMessage(String.format(getString(R.string.select_action), element.name))
                                        .setIcon(R.drawable.file)
                                        .setPositiveButton(R.string.download, (dialog, which) ->
                                                sftpHandler.post(new DownloadTask(element)))
                                        .setNeutralButton(R.string.delete, (dialog, which) ->
                                                sftpHandler.post(new DeleteTask(element)))
                                        .setNegativeButton(android.R.string.cancel, null).show();
                            }
                        } else {
                            ActivityCompat.requestPermissions(SFTPFolderActivity.this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
                        }
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent e) {
            View childView = view.findChildViewUnder(e.getX(), e.getY());
            if ((childView != null) && gestureDetector.onTouchEvent(e)) {
                DirectoryElement element = elements.get(view.getChildAdapterPosition(childView));
                if (element.isDirectory || element.isLink()) {
                    sftpHandler.post(new GetFilesTask(element.name));
                } else {
                    new AlertDialog.Builder(SFTPFolderActivity.this).setTitle(R.string.app_name)
                            .setMessage(String.format(getString(R.string.select_action), element.name))
                            .setIcon(R.drawable.file)
                            .setPositiveButton(R.string.download, (dialog, which) ->
                                    sftpHandler.post(new DownloadTask(element)))
                            .setNeutralButton(R.string.delete, (dialog, which) ->
                                    sftpHandler.post(new DeleteTask(element)))
                            .setNegativeButton(android.R.string.cancel, null).show();
                }
                return true;
            }
            return false;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent motionEvent) {
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        }
    }
}