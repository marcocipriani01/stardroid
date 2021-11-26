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

package io.github.marcocipriani01.telescopetouch;

import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.ACTION_BACKGROUND_ALWAYS;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.ACTION_DO_NOTHING;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.EXIT_ACTION_PREF;
import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;
import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.phd2;

import android.annotation.SuppressLint;
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
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.preference.PreferenceManager;

import io.github.marcocipriani01.telescopetouch.activities.MainActivity;
import io.github.marcocipriani01.telescopetouch.indi.ConnectionManager;

public class AppForegroundService extends Service implements ConnectionManager.ManagerListener {

    public static final String SERVICE_START = "service_start";
    public static final String SERVICE_STOP = "service_stop";
    public static final String SERVICE_ACTION_EXIT = "action_stop";
    public static final String SERVICE_ACTION_DISCONNECT = "action_disconnect";
    public static final int SERVICE_PENDING_INTENT_FLAG;
    private static final String TAG = TelescopeTouchApp.getTag(AppForegroundService.class);
    private static final String NOTIFICATION_CHANNEL = "TELESCOPE_TOUCH_SERVICE";
    private static final int NOTIFICATION_ID = 1;
    private static final int PENDING_INTENT_OPEN_APP = 1;
    private static final int PENDING_INTENT_MOUNT = 2;
    private static final int PENDING_INTENT_DISCONNECT = 3;
    private static final int PENDING_INTENT_EXIT = 4;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            SERVICE_PENDING_INTENT_FLAG = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        else SERVICE_PENDING_INTENT_FLAG = PendingIntent.FLAG_UPDATE_CURRENT;
    }

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
                    if (phd2.isConnected()) {
                        try {
                            phd2.disconnect();
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
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

    @SuppressLint("WrongConstant")
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

        builder.setContentIntent(stackBuilder.getPendingIntent(PENDING_INTENT_OPEN_APP, SERVICE_PENDING_INTENT_FLAG));
        if (connectionManager.isConnected()) {
            Intent mountIntent = new Intent(this, MainActivity.class);
            mountIntent.putExtra(MainActivity.ACTION, MainActivity.ACTION_MOUNT_CONTROL);
            stackBuilder.addNextIntentWithParentStack(mountIntent);
            builder.addAction(new NotificationCompat.Action(null, getString(R.string.mount),
                    stackBuilder.getPendingIntent(PENDING_INTENT_MOUNT, SERVICE_PENDING_INTENT_FLAG)));

            Intent disconnectIntent = new Intent(this, AppForegroundService.class);
            disconnectIntent.setAction(SERVICE_ACTION_DISCONNECT);
            builder.addAction(new NotificationCompat.Action(null, getString(R.string.disconnect),
                    PendingIntent.getService(this, PENDING_INTENT_DISCONNECT, disconnectIntent, SERVICE_PENDING_INTENT_FLAG)));
        } else {
            Intent closeIntent = new Intent(this, AppForegroundService.class);
            closeIntent.setAction(SERVICE_ACTION_EXIT);
            builder.addAction(new NotificationCompat.Action(null, getString(R.string.exit),
                    PendingIntent.getService(this, PENDING_INTENT_EXIT, closeIntent, SERVICE_PENDING_INTENT_FLAG)));
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
            if (!phd2.isConnected()) {
                stopForeground(true);
                if (PreferenceManager.getDefaultSharedPreferences(this)
                        .getString(EXIT_ACTION_PREF, ACTION_DO_NOTHING).equals(ACTION_BACKGROUND_ALWAYS)) {
                    start();
                } else {
                    stopSelf();
                }
            }
        });
    }
}