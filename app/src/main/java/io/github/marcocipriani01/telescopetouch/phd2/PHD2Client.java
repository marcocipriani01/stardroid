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

package io.github.marcocipriani01.telescopetouch.phd2;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.marcocipriani01.graphview.GraphView;
import io.github.marcocipriani01.graphview.series.DataPoint;
import io.github.marcocipriani01.graphview.series.LineGraphSeries;
import io.github.marcocipriani01.simplesocket.SimpleClient;
import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;

public class PHD2Client extends SimpleClient {

    public static final String[] DEC_GUIDE_MODES = new String[]{
            "Off", "Auto", "North", "South"
    };
    private static final String TAG = TelescopeTouchApp.getTag(PHD2Client.class);
    private static final int SUPPORTED_MSG_VERSION = 1;
    private static final int RA_COLOR = Color.parseColor("#448AFF");
    private static final int DEC_COLOR = Color.parseColor("#FF1744");
    public final LineGraphSeries<DataPoint> guidingDataRA = new LineGraphSeries<>();
    public final LineGraphSeries<DataPoint> guidingDataDec = new LineGraphSeries<>();
    public final Map<String, Integer> profiles = new HashMap<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Set<PHD2Listener> listeners = new HashSet<>();
    private final AtomicInteger graphIndex = new AtomicInteger();
    public String currentProfile = null;
    public int raCorrection = 0;
    public boolean raCorrectionSign = false;
    public int decCorrection = 0;
    public boolean decCorrectionSign = false;
    public double hdf = -1.0;
    public double snr = -1.0;
    public volatile String version;
    public volatile AppState appState;
    public volatile ConnectionState connectionState;
    public volatile double[] exposureTimes = null;
    public volatile double exposureTime = -1;
    public volatile Bitmap bitmap = null;
    public volatile boolean receiveImages = false;
    public volatile boolean stretchImages = false;
    public volatile String currentDecGuideMode = null;

    public PHD2Client() {
        super();
        guidingDataRA.setColor(RA_COLOR);
        guidingDataDec.setColor(DEC_COLOR);
    }

    public int getGraphIndex() {
        return graphIndex.get();
    }

    public void addListener(PHD2Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(PHD2Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyParamUpdate(PHD2Param... params) {
        synchronized (listeners) {
            for (PHD2Listener l : listeners) {
                uiHandler.post(() -> {
                    for (PHD2Param p : params) {
                        l.onPHD2ParamUpdate(p);
                    }
                });
            }
        }
    }

    public void clearGraphReference(GraphView graph) {
        guidingDataRA.clearReference(graph);
        guidingDataDec.clearReference(graph);
    }

    public void clearData() {
        graphIndex.set(0);
        guidingDataRA.resetData(new DataPoint[0]);
        guidingDataDec.resetData(new DataPoint[0]);
    }

    @Override
    protected void onConnected() {
        PHD2Command.get_connected.run(this);
        PHD2Command.get_exposure_durations.run(this);
        PHD2Command.get_profiles.run(this);
        PHD2Command.get_profile.run(this);
        PHD2Command.get_dec_guide_mode.run(this);
        uiHandler.postDelayed(() -> {
            synchronized (listeners) {
                for (PHD2Listener l : listeners) {
                    l.onPHD2Connected();
                }
            }
        }, 100);
    }

    @Override
    public void disconnect() {
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (PHD2Listener l : listeners) {
                    l.onPHD2Disconnected();
                }
            }
            appState = null;
            connectionState = null;
            synchronized (profiles) {
                profiles.clear();
            }
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
        });
        super.disconnect();
    }

    @Override
    public void onMessage(Socket from, String msg) {
        Log.i(TAG, msg.replace("\n", " | "));
        try {
            JSONObject block = new JSONObject(msg);
            if (block.has("Event")) {
                Event event = Event.get(block.getString("Event"));
                if (event != null) event.parse(this, block);
            } else if (block.has("jsonrpc")) {
                PHD2Command.parseResponse(this, block);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onError(Exception e) {
        Log.e(TAG, e.getMessage(), e);
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (PHD2Listener l : listeners) {
                    l.onPHD2Error(e);
                }
            }
        });
    }

    private void noStarSelected() {
        receiveImages = false;
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (PHD2Listener l : listeners) {
                    l.onPHD2NoStarSelected();
                }
            }
        });
    }

    public enum PHD2Command {
        loop,
        find_star,
        stop_capture,
        guide("{\"settle\": {\"pixels\": 3.0, \"time\": 10, \"timeout\": 40}}"),
        get_exposure(null, (phd, msg) -> {
            phd.exposureTime = msg.getInt("result") / 1000.0;
        }, PHD2Param.EXPOSURE_TIME),
        get_exposure_durations(null, (phd, msg) -> {
            JSONArray array = msg.getJSONArray("result");
            int length = array.length();
            phd.exposureTimes = new double[length];
            for (int i = 0; i < length; i++) {
                phd.exposureTimes[i] = array.getInt(i) / 1000.0;
            }
            PHD2Command.get_exposure.run(phd);
        }, PHD2Param.EXPOSURE_TIMES),
        set_exposure("[%d]"),
        get_connected(null, (phd, msg) -> {
            phd.connectionState = msg.getBoolean("result") ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED;
        }, PHD2Param.CONNECTION),
        get_star_image("[%d]", (phd, msg) -> {
            JSONObject result = msg.getJSONObject("result");
            JSONArray star_pos = result.getJSONArray("star_pos");
            ImageLoadingThread thread = phd.new ImageLoadingThread(result.getInt("width"),
                    result.getInt("height"), result.getString("pixels"),
                    star_pos.getDouble(0), star_pos.getDouble(1));
            thread.start();
        }, PHD2Param.IMAGE),
        set_connected("[%b]", PHD2Command.get_connected::run),
        set_profile("[%d]"),
        get_profile(null, (phd, msg) -> {
            phd.currentProfile = msg.getJSONObject("result").getString("name");
        }, PHD2Param.PROFILE),
        get_profiles(null, (phd, msg) -> {
            synchronized (phd.profiles) {
                phd.profiles.clear();
                JSONArray result = msg.getJSONArray("result");
                for (int i = 0; i < result.length(); i++) {
                    JSONObject profile = result.getJSONObject(i);
                    phd.profiles.put(profile.getString("name"), profile.getInt("id"));
                }
            }
        }, PHD2Param.PROFILES),
        set_dec_guide_mode("[\"%s\"]"),
        get_dec_guide_mode(null, (phd, msg) -> {
            phd.currentDecGuideMode = msg.getString("result");
        }, PHD2Param.DEC_GUIDE_MODE);

        private final String paramsFormat;
        private final PHD2Action responseAction;
        private final PHD2Param[] updatedParams;

        PHD2Command() {
            this.paramsFormat = null;
            responseAction = null;
            updatedParams = new PHD2Param[0];
        }

        PHD2Command(String paramsFormat) {
            this.paramsFormat = paramsFormat;
            responseAction = null;
            updatedParams = new PHD2Param[0];
        }

        PHD2Command(String paramsFormat, PHD2Action responseAction, PHD2Param... updatedParams) {
            this.paramsFormat = paramsFormat;
            this.responseAction = responseAction;
            this.updatedParams = updatedParams;
        }

        public static void parseResponse(PHD2Client phd, JSONObject msg) throws JSONException {
            if (msg.has("error")) {
                String message = msg.getJSONObject("error").getString("message");
                if (message.equals("no star selected")) {
                    phd.noStarSelected();
                } else {
                    phd.onError(new RuntimeException("PHD2 returned " + message));
                }
            } else if (msg.has("result")) {
                int id = msg.getInt("id");
                PHD2Command[] values = values();
                if ((id < values.length) && (values[id].responseAction != null)) {
                    values[id].responseAction.run(phd, msg);
                    if (values[id].updatedParams.length != 0)
                        phd.notifyParamUpdate(values[id].updatedParams);
                }
            }
        }

        public void run(PHD2Client phd, Object... params) {
            try {
                if (paramsFormat == null) {
                    phd.println("{\"method\": \"" + name() + "\", \"id\": " + ordinal() + "}");
                } else {
                    phd.println("{\"method\": \"" + name() + "\", \"params\": " + String.format(paramsFormat, params) + ", \"id\": " + ordinal() + "}");
                }
            } catch (IllegalStateException e) {
                phd.onError(e);
            }
        }
    }

    public enum PHD2Param {
        VERSION, STATE, GUIDE_VALUES, SETTLE_DONE,
        EXPOSURE_TIMES, EXPOSURE_TIME, CONNECTION,
        IMAGE, PROFILE, PROFILES, DEC_GUIDE_MODE
    }

    private enum Event {
        Version((phd, msg) -> {
            phd.version = msg.getString("PHDVersion");
            if (msg.getInt("MsgVersion") != SUPPORTED_MSG_VERSION) {
                phd.onError(new UnsupportedOperationException("Unsupported PHD2 version!"));
                phd.disconnect();
            }
        }, PHD2Param.VERSION),
        LockPositionSet,
        Calibrating,
        CalibrationComplete,
        StarSelected,
        StartGuiding,
        Paused((phd, msg) -> {
            phd.appState = PHD2Client.AppState.Paused;
        }, PHD2Param.STATE),
        StartCalibration((phd, msg) -> {
            phd.appState = PHD2Client.AppState.Calibrating;
        }, PHD2Param.STATE),
        AppState((phd, msg) -> {
            phd.appState = PHD2Client.AppState.fromString(msg.getString("State"));
        }, PHD2Param.STATE),
        CalibrationFailed,
        CalibrationDataFlipped,
        LockPositionShiftLimitReached,
        LoopingExposures((phd, msg) -> {
            phd.appState = PHD2Client.AppState.Looping;
            if (phd.receiveImages && (!phd.listeners.isEmpty()))
                PHD2Command.get_star_image.run(phd, 500);
        }, PHD2Param.STATE),
        LoopingExposuresStopped((phd, msg) -> {
            phd.appState = PHD2Client.AppState.Stopped;
        }, PHD2Param.STATE),
        SettleBegin,
        Settling,
        SettleDone(null, PHD2Param.SETTLE_DONE),
        StarLost((phd, msg) -> {
            phd.appState = PHD2Client.AppState.LostLock;
        }, PHD2Param.STATE),
        GuidingStopped,
        Resumed,
        GuideStep((phd, msg) -> {
            phd.appState = PHD2Client.AppState.Guiding;
            final int maxDataPoints = 200;
            int graphIndex = phd.graphIndex.incrementAndGet();
            DataPoint raVal = new DataPoint(graphIndex, msg.getDouble("RADistanceRaw")),
                    decVal = new DataPoint(graphIndex, msg.getDouble("DECDistanceRaw"));
            phd.uiHandler.post(() -> {
                phd.guidingDataRA.appendData(raVal, true, maxDataPoints);
                phd.guidingDataDec.appendData(decVal, true, maxDataPoints);
            });
            if (msg.has("RADuration")) {
                phd.raCorrection = msg.getInt("RADuration");
                phd.raCorrectionSign = msg.getString("RADirection").equals("East");
            } else {
                phd.raCorrection = 0;
            }
            if (msg.has("DECDuration")) {
                phd.decCorrection = msg.getInt("DECDuration");
                phd.decCorrectionSign = msg.getString("DECDirection").equals("North");
            } else {
                phd.decCorrection = 0;
            }
            phd.hdf = msg.getDouble("HFD");
            phd.snr = msg.getDouble("SNR");
            if (phd.receiveImages && (!phd.listeners.isEmpty()))
                PHD2Command.get_star_image.run(phd, 500);
        }, PHD2Param.STATE, PHD2Param.GUIDE_VALUES),
        GuidingDithered,
        LockPositionLost,
        Alert,
        GuideParamChange,
        ConfigurationChange;

        private final PHD2Action action;
        private final PHD2Param[] updatedParams;

        Event() {
            this.action = null;
            this.updatedParams = new PHD2Param[0];
        }

        Event(PHD2Action action, PHD2Param... updatedParams) {
            this.action = action;
            this.updatedParams = updatedParams;
        }

        public static Event get(String event) {
            for (Event s : values()) {
                if (s.name().equals(event)) return s;
            }
            return null;
        }

        void parse(PHD2Client phd, JSONObject msg) throws JSONException {
            if (action != null) action.run(phd, msg);
            if (updatedParams.length != 0) phd.notifyParamUpdate(updatedParams);
        }
    }

    public enum ConnectionState {
        CONNECTED, DISCONNECTED
    }

    public enum AppState {
        Stopped(R.string.phd2_stopped),
        Selected(R.string.phd2_selected),
        Calibrating(R.string.phd2_calibrating),
        Guiding(R.string.phd2_guiding),
        LostLock(R.string.phd2_star_lost),
        Paused(R.string.phd2_paused),
        Looping(R.string.phd2_looping);

        private final int descriptionRes;

        AppState(int descriptionRes) {
            this.descriptionRes = descriptionRes;
        }

        public static AppState fromString(String appState) {
            for (AppState s : values()) {
                if (s.name().equals(appState)) return s;
            }
            return null;
        }

        public String getDescription(Resources res) {
            return res.getString(descriptionRes);
        }
    }

    public interface PHD2Listener {
        void onPHD2Connected();

        void onPHD2Disconnected();

        void onPHD2NoStarSelected();

        void onPHD2Error(Exception e);

        void onPHD2ParamUpdate(PHD2Param param);
    }

    private interface PHD2Action {
        void run(PHD2Client phd, JSONObject msg) throws JSONException;
    }

    class ImageLoadingThread extends Thread {

        private final int width;
        private final int height;
        private final String pixels;
        private final double starX;
        private final double starY;

        private ImageLoadingThread(int width, int height, String pixels, double starX, double starY) {
            super("PHD2 image loading thread");
            this.width = width;
            this.height = height;
            this.pixels = pixels;
            this.starX = starX;
            this.starY = starY;
        }

        @Override
        public void run() {
            try {
                byte[] blobData = Base64.decode(pixels, Base64.DEFAULT);
                try (InputStream stream = new ByteArrayInputStream(blobData)) {
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    if (stretchImages) {
                        int[][] img = new int[width][height];
                        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                        for (int w = 0; w < width; w++) {
                            for (int h = 0; h < height; h++) {
                                int val = stream.read() | (stream.read() << 8);
                                img[w][h] = val;
                                if (val > max) max = val;
                                if (min > val) min = val;
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
                        for (int w = 0; w < width; w++) {
                            for (int h = 0; h < height; h++) {
                                int val = stream.read() | (stream.read() << 8);
                                val /= 257;
                                bitmap.setPixel(w, h, Color.rgb(val, val, val));
                            }
                        }
                    }
                    Canvas canvas = new Canvas(bitmap);
                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(Color.GREEN);
                    canvas.drawRect((int) starX - 20, (int) starY + 20, (int) starX + 20, (int) starY - 20, paint);
                    uiHandler.post(() -> {
                        Bitmap lastBitmap = PHD2Client.this.bitmap;
                        PHD2Client.this.bitmap = bitmap;
                        synchronized (listeners) {
                            for (PHD2Listener l : listeners) {
                                l.onPHD2ParamUpdate(PHD2Param.IMAGE);
                            }
                        }
                        if (lastBitmap != null) lastBitmap.recycle();
                    });
                }
            } catch (Exception t) {
                onError(t);
            }
        }
    }
}