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

package io.github.marcocipriani01.telescopetouch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.preference.PreferenceManager;

import io.github.marcocipriani01.telescopetouch.activities.MainActivity;
import io.github.marcocipriani01.telescopetouch.indi.ConnectionManager;

import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.ACTION_BACKGROUND_ALWAYS;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.ACTION_DO_NOTHING;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.EXIT_ACTION_PREF;
import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

public class AppForegroundService extends Service implements ConnectionManager.ManagerListener {

    public static final String SERVICE_START = "service_start";
    public static final String SERVICE_STOP = "service_stop";
    public static final String SERVICE_ACTION_EXIT = "action_stop";
    public static final String SERVICE_ACTION_DISCONNECT = "action_disconnect";
    private static final String NOTIFICATION_CHANNEL = "TELESCOPE_TOUCH_SERVICE";
    private static final int NOTIFICATION_ID = 1;
    private static final int PENDING_INTENT_OPEN_APP = 1;
    private static final int PENDING_INTENT_MOUNT = 2;
    private static final int PENDING_INTENT_DISCONNECT = 3;
    private static final int PENDING_INTENT_EXIT = 4;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case SERVICE_START:
                    start();
                    break;
                case SERVICE_STOP:
                    stopForeground(true);
                    stopSelf();
                    break;
                case SERVICE_ACTION_DISCONNECT:
                    if (connectionManager.isConnected()) connectionManager.disconnect();
                    Toast.makeText(this, R.string.disconnected, Toast.LENGTH_SHORT).show();
                    stopForeground(true);
                    stopSelf();
                    break;
                case SERVICE_ACTION_EXIT:
                    stopForeground(true);
                    stopSelf();
                    System.exit(0);
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL,
                    getString(R.string.background_service), NotificationManager.IMPORTANCE_NONE);
            channel.setLightColor(getColor(R.color.colorPrimary));
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL);
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(getString(R.string.app_name));
        bigTextStyle.bigText(getString(R.string.app_background));
        builder.setStyle(bigTextStyle);
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.stars_on);
        builder.setPriority(Notification.PRIORITY_DEFAULT);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        Intent connectionIntent = new Intent(this, MainActivity.class);
        connectionIntent.putExtra(MainActivity.ACTION, MainActivity.ACTION_CONNECT);
        stackBuilder.addNextIntentWithParentStack(connectionIntent);
        builder.setContentIntent(stackBuilder.getPendingIntent(PENDING_INTENT_OPEN_APP, PendingIntent.FLAG_UPDATE_CURRENT));
        if (connectionManager.isConnected()) {
            Intent mountIntent = new Intent(this, MainActivity.class);
            mountIntent.putExtra(MainActivity.ACTION, MainActivity.ACTION_MOUNT_CONTROL);
            stackBuilder.addNextIntentWithParentStack(mountIntent);
            builder.addAction(new NotificationCompat.Action(null, getString(R.string.mount),
                    stackBuilder.getPendingIntent(PENDING_INTENT_MOUNT, PendingIntent.FLAG_UPDATE_CURRENT)));

            Intent disconnectIntent = new Intent(this, AppForegroundService.class);
            disconnectIntent.setAction(SERVICE_ACTION_DISCONNECT);
            builder.addAction(new NotificationCompat.Action(null, getString(R.string.disconnect),
                    PendingIntent.getService(this, PENDING_INTENT_DISCONNECT, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT)));
        } else {
            Intent closeIntent = new Intent(this, AppForegroundService.class);
            closeIntent.setAction(SERVICE_ACTION_EXIT);
            builder.addAction(new NotificationCompat.Action(null, getString(R.string.exit),
                    PendingIntent.getService(this, PENDING_INTENT_EXIT, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT)));
        }
        startForeground(NOTIFICATION_ID, builder.build());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        connectionManager.addManagerListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        connectionManager.removeManagerListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConnectionLost() {
        handler.post(() -> {
            Toast.makeText(this, R.string.connection_indi_lost, Toast.LENGTH_SHORT).show();
            stopForeground(true);
            if (PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(EXIT_ACTION_PREF, ACTION_DO_NOTHING).equals(ACTION_BACKGROUND_ALWAYS)) {
                start();
            } else {
                stopSelf();
            }
        });
    }
}