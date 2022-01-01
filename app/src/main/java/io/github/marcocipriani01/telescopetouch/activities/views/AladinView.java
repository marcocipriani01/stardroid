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

package io.github.marcocipriani01.telescopetouch.activities.views;

import static io.github.marcocipriani01.telescopetouch.ApplicationConstants.ALADIN_FORCE;
import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.astronomy.EquatorialCoordinates;

public class AladinView extends WebView implements Runnable {

    private static final String TAG = TelescopeTouchApp.getTag(AladinView.class);
    private static final int REFRESH_RATE_LONG = 500;
    private static final int REFRESH_RATE_SHORT = 100;
    private static final String[] DEFAULT_TARGETS = {"M45", "M16", "M20", "M33", "M51", "M101"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private AladinListener listener = null;
    private double lastRa = 0, lastDec = 0;
    private int height = 0;

    public AladinView(@NonNull Context context) {
        super(context);
        init();
    }

    public AladinView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AladinView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public static boolean isSupported(SharedPreferences preferences) {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) || preferences.getBoolean(ALADIN_FORCE, false);
    }

    public void setHeight(int height) {
        this.height = height;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void init() {
        setWebViewClient(new AladinWebViewClient());
        setWebChromeClient(new AladinChromeClient());
        addJavascriptInterface(new AladinJSInterface(), "aladinView");
        WebSettings settings = getSettings();
        settings.setAllowFileAccess(false);
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }

    public boolean isRunning() {
        return running;
    }

    public void setAladinListener(AladinListener listener) {
        this.listener = listener;
    }

    private String getAladinHTML() {
        try {
            StringBuilder builder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    getContext().getAssets().open("html/aladin.html"), StandardCharsets.UTF_8));
            String tmp;
            while ((tmp = reader.readLine()) != null) {
                builder.append(tmp);
            }
            reader.close();
            return builder.toString().replace("_STYLES_", (height == 0) ? "" : ("height: " + height + "px"));
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            return null;
        }
    }

    public void gotoRADec(double ra, double dec) {
        if (running)
            evaluateJavascript("aladin.gotoRaDec(" + ra + ", " + dec + ")", null);
    }

    public void gotoObject(String name) {
        if (running)
            evaluateJavascript("aladin.gotoObject('" + name + "', {error: function() { aladinView.onGoToFail(); }})", null);
    }

    public void saveBitmap() {
        evaluateJavascript("aladin.getViewDataURL(\"image/png\")", value -> {
            if (value.equals("null")) return;
            new Thread(() -> {
                if (listener != null) listener.onAladinProgressIndeterminate();
                byte[] bytes = Base64.decode(value.replace("data:image/png;base64,", ""), Base64.DEFAULT);
                if (listener != null)
                    listener.onAladinBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            }, "Aladin PNG decoder").start();
        });
    }

    public void start() {
        if (connectionManager.telescopeName == null) {
            start(DEFAULT_TARGETS[(int) (Math.random() * DEFAULT_TARGETS.length)]);
        } else {
            start(connectionManager.telescopeCoordinates.ra + ", " + connectionManager.telescopeCoordinates.dec);
        }
    }

    public void start(EquatorialCoordinates coordinates) {
        start(coordinates.ra + ", " + coordinates.dec);
    }

    public void start(String target) {
        String aladin = getAladinHTML();
        if (aladin != null) {
            loadDataWithBaseURL(null, aladin.replace("_TARGET_", target), "text/html", "utf-8", null);
        } else if (listener != null) {
            listener.onAladinError();
        }
    }

    private void startCoordRefresh() {
        running = true;
        handler.postDelayed(AladinView.this, REFRESH_RATE_SHORT);
    }

    private void stopCoordRefresh() {
        running = false;
        handler.removeCallbacks(this);
    }

    @Override
    public void run() {
        if (listener != null) {
            evaluateJavascript("aladin.getRaDec()", value -> {
                String[] split = value.replace("[", "").replace("]", "").split(",");
                if (split.length == 2)
                    listener.onAladinCoordUpdate(new EquatorialCoordinates(Double.parseDouble(split[0]), Double.parseDouble(split[1])));
            });
        }
        if (connectionManager.telescopeName == null) {
            evaluateJavascript("if (telescope != null) { catalog.remove(telescope); telescope = null; }", null);
            lastRa = 0;
            lastDec = 0;
            if (running) handler.postDelayed(this, REFRESH_RATE_LONG);
        } else {
            if ((connectionManager.telescopeCoordinates.ra != lastRa) ||
                    (connectionManager.telescopeCoordinates.dec != lastDec)) {
                evaluateJavascript(
                        "if (telescope != null) { catalog.remove(telescope); }; " +
                                "telescope = A.source(" +
                                connectionManager.telescopeCoordinates.ra + ", " + connectionManager.telescopeCoordinates.dec +
                                "); catalog.addSources(telescope);", null);
                lastRa = connectionManager.telescopeCoordinates.ra;
                lastDec = connectionManager.telescopeCoordinates.dec;
            }
            if (running) handler.postDelayed(this, REFRESH_RATE_SHORT);
        }
    }

    public interface AladinListener {

        default void onAladinLoaded() {
        }

        default void onAladinStop() {
        }

        default void onAladinError() {
        }

        default void onAladinGoToFail() {
        }

        default void onAladinCoordUpdate(EquatorialCoordinates coordinates) {
        }

        default void onAladinBitmap(Bitmap bitmap) {
        }

        default void onAladinProgressChange(int progress) {
        }

        default void onAladinProgressIndeterminate() {
        }
    }

    private class AladinJSInterface {

        @JavascriptInterface
        public void onGoToFail() {
            if (listener != null) listener.onAladinGoToFail();
        }
    }

    private class AladinWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (running) AladinView.this.stopCoordRefresh();
            if (listener != null) listener.onAladinStop();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            AladinView.this.startCoordRefresh();
            if (listener != null) listener.onAladinLoaded();
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if ((listener != null) && error.getDescription().equals("net::ERR_INTERNET_DISCONNECTED"))
                listener.onAladinError();
        }
    }

    private class AladinChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (listener != null) listener.onAladinProgressChange(newProgress);
        }
    }
}