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

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.EXIT_ACTION_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.NSD_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.POLARIS_HEMISPHERE_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.POLARIS_RETICLE_PREF;
import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.SKY_MAP_HIGH_REFRESH_PREF;

/**
 * Pro.
 */
public class ProUtils {

    public static final String PRO_ID = "io.github.marcocipriani01.telescopetouchpro";
    public static final String PRO_MESSAGE_PREF = "pro_message_1";
    public static final int MAX_CAPTURES = 3;
    public static final String[] PRO_PREFERENCES = {
            NSD_PREF, EXIT_ACTION_PREF, POLARIS_HEMISPHERE_PREF, POLARIS_RETICLE_PREF, SKY_MAP_HIGH_REFRESH_PREF
    };
    public static final String CAPTURE_PRO_COUNTER = "capture_pro_counter";
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