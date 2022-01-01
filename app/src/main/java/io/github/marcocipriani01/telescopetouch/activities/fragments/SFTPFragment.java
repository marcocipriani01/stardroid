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

import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.nsdHelper;
import static io.github.marcocipriani01.telescopetouch.activities.ServersActivity.getServers;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputEditText;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.ProUtils;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.activities.MainActivity;
import io.github.marcocipriani01.telescopetouch.activities.SFTPFolderActivity;

public class SFTPFragment extends ActionFragment {

    public static byte[] privateKeyBytes;
    private static int selectedSpinnerItem = 0;
    private final String TAG = TelescopeTouchApp.getTag(SFTPFragment.class);
    private SwitchCompat usePEMKeySwitch, pemPasswordSwitch;
    private TextInputEditText passwordField, pemPasswordField, usernameField, portField;
    private AppCompatSpinner ipSpinner;
    private CheckBox savePasswordBox;
    private SharedPreferences preferences;
    private Button privateKeyButton;
    private String remoteFolder;
    private InputMethodManager inputMethodManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        setHasOptionsMenu(true);
        View rootView = inflater.inflate(R.layout.fragment_sftp, container, false);
        savePasswordBox = rootView.findViewById(R.id.sftp_save_password_box);
        usePEMKeySwitch = rootView.findViewById(R.id.sftp_pem_switch);
        passwordField = rootView.findViewById(R.id.sftp_password_field);
        passwordField.setOnEditorActionListener(this::onEditorAction);
        usernameField = rootView.findViewById(R.id.sftp_username_field);
        ipSpinner = rootView.findViewById(R.id.sftp_host_spinner);
        portField = rootView.findViewById(R.id.sftp_port_field);
        View passwordFieldLayout = rootView.findViewById(R.id.password_field_layout),
                pemPasswordLayout = rootView.findViewById(R.id.pem_password_layout);
        privateKeyButton = rootView.findViewById(R.id.sftp_choose_pem_btn);
        pemPasswordSwitch = rootView.findViewById(R.id.sftp_pem_has_password_switch);
        pemPasswordField = rootView.findViewById(R.id.sftp_pem_password_field);
        pemPasswordField.setOnEditorActionListener(this::onEditorAction);
        usePEMKeySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                passwordFieldLayout.setVisibility(View.GONE);
                privateKeyButton.setVisibility(View.VISIBLE);
                pemPasswordSwitch.setVisibility(View.VISIBLE);
                int pemPassword = pemPasswordSwitch.isChecked() ? View.VISIBLE : View.GONE;
                pemPasswordLayout.setVisibility(pemPassword);
                savePasswordBox.setVisibility(pemPassword);
            } else {
                passwordFieldLayout.setVisibility(View.VISIBLE);
                privateKeyButton.setVisibility(View.GONE);
                pemPasswordSwitch.setVisibility(View.GONE);
                pemPasswordLayout.setVisibility(View.GONE);
                savePasswordBox.setVisibility(View.VISIBLE);
            }
        });
        pemPasswordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                pemPasswordLayout.setVisibility(View.VISIBLE);
                savePasswordBox.setVisibility(View.VISIBLE);
            } else {
                pemPasswordLayout.setVisibility(View.GONE);
                savePasswordBox.setVisibility(View.GONE);
            }
        });
        rootView.<Button>findViewById(R.id.sftp_connect_btn).setOnClickListener(this::connectAction);
        rootView.<Button>findViewById(R.id.sftp_choose_pem_btn).setOnClickListener(this::chosePEMKeyAction);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        loadServers(getServers(preferences));
        usernameField.setText(preferences.getString(ApplicationConstants.USERNAME_PREF, ""));
        portField.setText(preferences.getString(ApplicationConstants.PORT_PREF, "22"));
        usePEMKeySwitch.setChecked(preferences.getBoolean(ApplicationConstants.USE_PEM_PREF, false));
        pemPasswordSwitch.setChecked(preferences.getBoolean(ApplicationConstants.USE_PEM_PASSWORD_PREF, false));
        boolean savePasswords = preferences.getBoolean(ApplicationConstants.SAVE_PASSWORDS_PREF, true);
        savePasswordBox.setChecked(savePasswords);
        savePasswordBox.setSelected(savePasswords);
        if (savePasswords) {
            passwordField.setText(preferences.getString(ApplicationConstants.PASSWORD_PREF, ""));
            pemPasswordField.setText(preferences.getString(ApplicationConstants.PEM_PASSWORD_PREF, ""));
        }
        if (privateKeyBytes != null)
            privateKeyButton.setText(R.string.private_key_selected);

        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R) &&
                (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED))
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return rootView;
    }

    private void loadServers(ArrayList<String> servers) {
        if (nsdHelper.isAvailable()) {
            HashMap<String, String> services = nsdHelper.getDiscoveredServices();
            for (String name : services.keySet()) {
                String ip = services.get(name);
                if (ip != null) servers.add(name.replace("@", "") + "@" + ip);
            }
        }
        boolean empty = servers.isEmpty();
        ipSpinner.setEnabled(!empty);
        if (empty) servers.add(context.getString(R.string.no_host_available));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, servers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ipSpinner.setAdapter(adapter);
        if ((!empty) && (adapter.getCount() > selectedSpinnerItem))
            ipSpinner.setSelection(selectedSpinnerItem);
    }

    @SuppressWarnings("ConstantConditions")
    private void connectAction(View view) {
        // PRO
        if (!ProUtils.isPro) {
            int count = preferences.getInt(ProUtils.SFTP_PRO_COUNTER, 0);
            if (count >= ProUtils.MAX_SFTP_CONNECTIONS) {
                requestActionSnack(R.string.buy_pro_continue_sftp);
                return;
            }
            preferences.edit().putInt(ProUtils.SFTP_PRO_COUNTER, count + 1).apply();
        }
        // END PRO
        inputMethodManager.hideSoftInputFromWindow(usernameField.getWindowToken(), 0);
        inputMethodManager.hideSoftInputFromWindow(passwordField.getWindowToken(), 0);
        inputMethodManager.hideSoftInputFromWindow(pemPasswordField.getWindowToken(), 0);
        inputMethodManager.hideSoftInputFromWindow(portField.getWindowToken(), 0);
        String ip = (String) ipSpinner.getSelectedItem();
        if (ip == null) {
            requestActionSnack(R.string.no_host_selected);
            return;
        }
        if (ip.contains("@")) {
            String[] split = ip.split("@");
            if (split.length == 2) ip = split[1];
        }
        String user = usernameField.getText().toString();
        remoteFolder = "/home/" + user;
        if (!user.isEmpty() && !ip.isEmpty()) {
            String portString = portField.getText().toString(),
                    password = passwordField.getText().toString(),
                    pemPassword = pemPasswordField.getText().toString();
            boolean usePEM = usePEMKeySwitch.isChecked(),
                    usePEMPassword = usePEM && pemPasswordSwitch.isChecked();
            if (usePEM && (privateKeyBytes == null)) {
                requestActionSnack(R.string.no_pem_loaded);
            } else if ((!usePEM && password.isEmpty()) || (usePEMPassword && pemPassword.isEmpty())) {
                requestActionSnack(R.string.please_write_password);
            } else {
                try {
                    int port = portString.isEmpty() ? 22 : Integer.parseInt(portString);
                    boolean savePasswords = savePasswordBox.isChecked();
                    preferences.edit().putBoolean(ApplicationConstants.SAVE_PASSWORDS_PREF, savePasswords)
                            .putString(ApplicationConstants.USERNAME_PREF, user)
                            .putString(ApplicationConstants.PORT_PREF, portString)
                            .putBoolean(ApplicationConstants.USE_PEM_PREF, usePEM)
                            .putBoolean(ApplicationConstants.USE_PEM_PASSWORD_PREF, usePEMPassword)
                            .putString(ApplicationConstants.PASSWORD_PREF, savePasswords ? password : "")
                            .putString(ApplicationConstants.PEM_PASSWORD_PREF, savePasswords ? pemPassword : "").apply();
                    if ((port <= 0) || (port >= 0xFFFF)) {
                        requestActionSnack(R.string.invalid_port);
                    } else {
                        new ConnectTask(user, ip, password, port, usePEM,
                                usePEMPassword, pemPassword).start();
                    }
                } catch (NumberFormatException e) {
                    requestActionSnack(R.string.invalid_port);
                }
            }
        } else {
            requestActionSnack(R.string.something_missing);
        }
    }

    private void chosePEMKeyAction(View view) {
        inputMethodManager.hideSoftInputFromWindow(usernameField.getWindowToken(), 0);
        inputMethodManager.hideSoftInputFromWindow(passwordField.getWindowToken(), 0);
        inputMethodManager.hideSoftInputFromWindow(pemPasswordField.getWindowToken(), 0);
        inputMethodManager.hideSoftInputFromWindow(portField.getWindowToken(), 0);
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ||
                (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED)) {
            FragmentActivity activity = getActivity();
            if (activity instanceof MainActivity)
                ((MainActivity) activity).launchSFTPFileChooser();
        } else {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    public void onFileChosen(ActivityResult result) {
        Intent resultData = result.getData();
        if ((result.getResultCode() == Activity.RESULT_OK) && (resultData != null)) {
            try {
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                InputStream inputStream = context.getContentResolver().openInputStream(resultData.getData());
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }
                byte[] bytes = byteBuffer.toByteArray();
                if (pemPasswordSwitch.isChecked() || new String(bytes).contains("PRIVATE KEY")) {
                    privateKeyBytes = bytes;
                    privateKeyButton.setText(R.string.private_key_selected);
                } else {
                    privateKeyBytes = null;
                    requestActionSnack(R.string.pem_key_error);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                requestActionSnack(R.string.could_not_read_key);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        selectedSpinnerItem = ipSpinner.getSelectedItemPosition();
    }

    @Override
    public void onPermissionNotAcquired(String permission) {
        requestActionSnack(R.string.storage_permission_required);
    }

    private boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_GO) {
            SFTPFragment.this.connectAction(null);
            return true;
        }
        return false;
    }

    class ConnectTask extends Thread {

        private final String TAG = TelescopeTouchApp.getTag(ConnectTask.class);
        private final ProgressDialog progressDialog;
        private final String user;
        private final String ip;
        private final String password;
        private final int port;
        private final boolean useKey;
        private final boolean usePEM;
        private final String pemPassword;
        private final Handler handler = new Handler(Looper.getMainLooper());

        ConnectTask(String user, String ip, String password, int port,
                    boolean useKey, boolean usePEM, String pemPassword) {
            super();
            this.user = user;
            this.ip = ip;
            this.password = password;
            this.port = port;
            this.useKey = useKey;
            this.usePEM = usePEM;
            this.pemPassword = pemPassword;
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage(getString(R.string.connecting));
            progressDialog.show();
        }

        @Override
        public void run() {
            try {
                JSch jsch = new JSch();
                Session session;
                if (!useKey) {
                    session = jsch.getSession(user, ip, port);
                    session.setPassword(password);
                } else {
                    if (usePEM) {
                        jsch.addIdentity("connection", privateKeyBytes, null, pemPassword.getBytes());
                    } else {
                        jsch.addIdentity("connection", privateKeyBytes, null, null);
                    }
                    session = jsch.getSession(user, ip, port);
                    session.setConfig("PreferredAuthentications", "publickey");
                }
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect(15000);
                ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(15000);
                Log.d(TAG, "Connected, returning session");
                TelescopeTouchApp.session = session;
                TelescopeTouchApp.channel = channel;
                handler.post(() -> {
                    progressDialog.dismiss();
                    Intent intent = new Intent(context, SFTPFolderActivity.class);
                    intent.putExtra(SFTPFolderActivity.EXTRA_REMOTE_FOLDER, remoteFolder);
                    context.startActivity(intent);
                });
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.post(() -> {
                    progressDialog.dismiss();
                    requestActionSnack(R.string.connection_error);
                });
            }
        }
    }
}