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

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import org.indilib.i4j.client.INDIDevice;
import org.indilib.i4j.client.INDIDeviceListener;
import org.indilib.i4j.client.INDIProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.indi.PropPref;

public class DeviceControlFragment extends PreferenceFragmentCompat implements INDIDeviceListener {

    private static final String KEY_RECYCLER_STATE = "PrefsRecyclerViewState";
    private static final HashMap<INDIDevice, Bundle> recyclerviewBundles = new HashMap<>();
    private final HashMap<INDIProperty<?>, PropPref<?>> preferencesMap = new HashMap<>();
    private final HashMap<String, PreferenceCategory> groups = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private INDIDevice device = null;
    private PreferenceScreen prefScreen;
    private Context context;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.empty_preferences);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefScreen = getPreferenceScreen();
        if (device != null) {
            this.device.addINDIDeviceListener(this);
            preferencesMap.clear();
            groups.clear();
            for (String group : device.getGroupNames()) {
                List<INDIProperty<?>> props = device.getPropertiesOfGroup(group);
                if (!props.isEmpty()) {
                    PreferenceCategory prefGroup = new PreferenceCategory(context);
                    prefGroup.setIconSpaceReserved(false);
                    groups.put(group, prefGroup);
                    prefGroup.setTitle(group);
                    prefScreen.addPreference(prefGroup);
                    for (INDIProperty<?> prop : props) {
                        PropPref<?> pref = PropPref.create(context, prop);
                        pref.setIconSpaceReserved(false);
                        preferencesMap.put(prop, pref);
                        prefGroup.addPreference(pref);
                    }
                }
            }
        }
        super.onViewCreated(view, savedInstanceState);
        if (device != null) {
            Bundle recyclerviewBundle = recyclerviewBundles.get(device);
            if (recyclerviewBundle != null) {
                RecyclerView.LayoutManager layoutManager = getListView().getLayoutManager();
                if (layoutManager != null)
                    layoutManager.onRestoreInstanceState(recyclerviewBundle.getParcelable(KEY_RECYCLER_STATE));
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (device != null) {
            Bundle recyclerviewBundle = new Bundle();
            RecyclerView.LayoutManager layoutManager = getListView().getLayoutManager();
            if (layoutManager != null)
                recyclerviewBundle.putParcelable(KEY_RECYCLER_STATE, layoutManager.onSaveInstanceState());
            recyclerviewBundles.put(device, recyclerviewBundle);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        RecyclerView list = getListView();
        list.stopNestedScroll();
        list.stopScroll();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (device != null)
            device.removeINDIDeviceListener(this);
    }

    public void setDevice(INDIDevice device) {
        this.device = device;
    }

    public void stopSearch() {
        for (PropPref<?> pref : preferencesMap.values()) {
            PreferenceCategory group = groups.get(pref.getProp().getGroup());
            if (group != null) {
                prefScreen.addPreference(group);
                group.addPreference(pref);
            }
        }
    }

    public void findPref(String newText) {
        newText = newText.toLowerCase();
        for (PropPref<?> pref : preferencesMap.values()) {
            PreferenceCategory group = groups.get(pref.getProp().getGroup());
            if (group != null) {
                if (Objects.requireNonNull(pref.getTitle()).toString().toLowerCase().contains(newText)) {
                    group.addPreference(pref);
                } else {
                    group.removePreference(pref);
                }
                if (group.getPreferenceCount() == 0) {
                    prefScreen.removePreference(group);
                } else {
                    prefScreen.addPreference(group);
                }
            }
        }
    }

    @Override
    public void newProperty(INDIDevice device, final INDIProperty<?> property) {
        handler.post(() -> {
            String group = property.getGroup();
            PreferenceCategory prefGroup = groups.get(group);
            if (prefGroup == null) {
                prefGroup = new PreferenceCategory(context);
                prefGroup.setIconSpaceReserved(false);
                groups.put(group, prefGroup);
                prefGroup.setTitle(group);
                prefScreen.addPreference(prefGroup);
            }
            PropPref<?> pref = PropPref.create(context, property);
            pref.setIconSpaceReserved(false);
            preferencesMap.put(property, pref);
            prefGroup.addPreference(pref);
        });
    }

    @Override
    public void removeProperty(INDIDevice device, final INDIProperty<?> property) {
        handler.post(() -> {
            PropPref<?> pref = preferencesMap.get(property);
            if (pref != null) {
                String group = property.getGroup();
                PreferenceCategory prefGroup = groups.get(group);
                if (prefGroup != null) {
                    prefGroup.removePreference(pref);
                    if (prefGroup.getPreferenceCount() == 0) {
                        prefScreen.removePreference(prefGroup);
                        groups.remove(group);
                    }
                }
                preferencesMap.remove(property);
            }
        });
    }

    @Override
    public void messageChanged(INDIDevice device) {

    }
}