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

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.HashMap;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

public class NSDHelper implements ServiceListener {

    private static final String SERVICE_TYPE = "_http._tcp.local.";
    private static final String TAG = TelescopeTouchApp.getTag(NSDHelper.class);
    private final HashMap<String, String> discoveredServices = new HashMap<>();
    private WifiManager.MulticastLock multiCastLock;
    private JmDNS jmdns = null;
    private boolean available = false;
    private NSDListener listener;

    public void start(Context context) {
        if (available) return;
        new Thread(() -> {
            try {
                available = false;
                Log.i(TAG, "Starting Multicast Lock...");
                WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                multiCastLock = wifi.createMulticastLock(TAG);
                multiCastLock.setReferenceCounted(true);
                multiCastLock.acquire();
                Log.i(TAG, "Starting ZeroConf probe....");
                int address = wifi.getConnectionInfo().getIpAddress();
                byte[] byteAddress = new byte[]{(byte) (address & 0xff), (byte) (address >> 8 & 0xff),
                        (byte) (address >> 16 & 0xff), (byte) (address >> 24 & 0xff)};
                jmdns = JmDNS.create(InetAddress.getByAddress(byteAddress));
                jmdns.addServiceListener(SERVICE_TYPE, this);
                available = true;
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                stop();
            }
        }).start();
    }

    public boolean isAvailable() {
        return available;
    }

    public void stop() {
        Log.i(TAG, "Stopping NSD...");
        available = false;
        discoveredServices.clear();
        if (jmdns != null) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
            jmdns = null;
        }
        if (multiCastLock != null) {
            multiCastLock.release();
            multiCastLock = null;
        }
    }

    public void setListener(NSDListener listener) {
        this.listener = listener;
    }

    public HashMap<String, String> getDiscoveredServices() {
        return discoveredServices;
    }

    @Override
    public final void serviceAdded(ServiceEvent event) {
        Log.d(TAG, "Service added: " + event.getInfo());
    }

    @Override
    public final void serviceRemoved(ServiceEvent event) {
        Log.d(TAG, "Service lost: " + event.getInfo());
        discoveredServices.remove(event.getName().replace("@", ""));
        if (listener != null) listener.onNSDChange();
    }

    @Override
    public final void serviceResolved(ServiceEvent event) {
        Log.d(TAG, "Service resolved: " + event.getInfo());
        Inet4Address[] addresses = event.getInfo().getInet4Addresses();
        if (addresses.length > 0) {
            discoveredServices.put(event.getName(), addresses[0].getHostAddress());
            if (listener != null) listener.onNSDChange();
        }
    }

    public interface NSDListener {
        void onNSDChange();
    }
}