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

import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.EXIT_ACTION_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.NSD_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.POLARIS_HEMISPHERE_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.POLARIS_RETICLE_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.SKY_MAP_HIGH_REFRESH_PREF;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

/**
 * Pro.
 */
public class ProUtils {

    public static final String PRO_ID = "io.github.marcocipriani01.telescopetouchpro";
    public static final String PRO_MESSAGE_PREF = "pro_message_3";
    public static final int MAX_CAPTURES = 3;
    public static final int MAX_PHD2_CONNECTIONS = 3;
    public static final int MAX_SFTP_CONNECTIONS = 3;
    public static final String[] PRO_PREFERENCES = {
            NSD_PREF, EXIT_ACTION_PREF, POLARIS_HEMISPHERE_PREF, POLARIS_RETICLE_PREF, SKY_MAP_HIGH_REFRESH_PREF
    };
    public static final String CAPTURE_PRO_COUNTER = "capture_pro_counter";
    public static final String PHD2_PRO_COUNTER = "phd2_connection_counter";
    public static final String SFTP_PRO_COUNTER = "sftp_connection_counter";
    public static boolean isPro = false;

    public static void maybeProVersionDialog(SharedPreferences preferences, Context context) {
        if (preferences.getBoolean(PRO_MESSAGE_PREF, true)) {
            new AlertDialog.Builder(context).setTitle(R.string.app_name)
                    .setMessage(R.string.pro_new_msg)
                    .setIcon(R.drawable.star_circle).setCancelable(false)
                    .setPositiveButton(R.string.get_it, (a, b) -> ProUtils.playStore(context))
                    .setNegativeButton(android.R.string.cancel, null).show();
            preferences.edit().putBoolean(PRO_MESSAGE_PREF, false).apply();
        }
    }

    public static void update(Context context) {
        try {
            context.getPackageManager().getApplicationInfo(PRO_ID, 0);
            isPro = true;
        } catch (PackageManager.NameNotFoundException e) {
            isPro = false;
        }
    }

    public static void toast(Context context) {
        Toast.makeText(context, R.string.pro_feature, Toast.LENGTH_LONG).show();
    }

    public static void playStore(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PRO_ID)));
        } catch (ActivityNotFoundException e) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + PRO_ID)));
        }
    }
}