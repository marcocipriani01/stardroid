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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import io.github.marcocipriani01.telescopetouch.ApplicationConstants;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.activities.util.DarkerModeManager;

/**
 * @author marcocipriani01
 */
public class WebManagerActivity extends AppCompatActivity implements MenuItem.OnMenuItemClickListener {

    public static final String INTENT_HOST = "intent_host";
    private DarkerModeManager darkerModeManager;
    private WebView webView;
    private MenuItem refresh;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        darkerModeManager = new DarkerModeManager(this, null, preferences);
        setTheme(darkerModeManager.getPref() ? R.style.DarkerAppTheme : R.style.AppTheme);
        setContentView(R.layout.activity_web_manager);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        ProgressBar progressBar = findViewById(R.id.web_manager_progress);
        webView = findViewById(R.id.web_manager_view);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            settings.setForceDark(WebSettings.FORCE_DARK_ON);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView web, String url) {
                progressBar.setVisibility(View.GONE);
                webView.loadUrl("javascript:document.body.style.margin=\"16px\"; void 0");
            }

            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Intent intent = new Intent(WebManagerActivity.this, MainActivity.class);
                intent.putExtra(MainActivity.MESSAGE, R.string.can_not_connect_indi_web);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                WebManagerActivity.this.finish();
            }
        });
        webView.loadUrl("http://" + Objects.requireNonNull(getIntent().getStringExtra(INTENT_HOST)) +
                ":" + preferences.getString(ApplicationConstants.WEB_MANAGER_PORT_PREF, "8624"));
        if (preferences.getBoolean(ApplicationConstants.WEB_MANAGER_INFO_PREF, true)) {
            new AlertDialog.Builder(this).setIcon(R.drawable.internet)
                    .setTitle(R.string.indi_web_manager)
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            preferences.edit().putBoolean(ApplicationConstants.WEB_MANAGER_INFO_PREF, false).apply())
                    .setMessage(R.string.web_manager_info).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        super.onOptionsItemSelected(item);
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        refresh = menu.add(R.string.refresh);
        refresh.setIcon(R.drawable.refresh);
        refresh.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        refresh.setOnMenuItemClickListener(this);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onResume() {
        super.onResume();
        darkerModeManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        darkerModeManager.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        webView.stopLoading();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item == refresh) {
            webView.reload();
            return true;
        }
        return false;
    }
}