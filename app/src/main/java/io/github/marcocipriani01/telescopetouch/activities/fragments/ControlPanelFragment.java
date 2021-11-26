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

import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.indilib.i4j.client.INDIDevice;
import org.indilib.i4j.client.INDIServerConnection;
import org.indilib.i4j.client.INDIServerConnectionListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import io.github.marcocipriani01.telescopetouch.R;

@SuppressLint("NotifyDataSetChanged")
public class ControlPanelFragment extends ActionFragment implements INDIServerConnectionListener,
        SearchView.OnQueryTextListener, View.OnClickListener, SearchView.OnCloseListener {

    private static final String KEY_VIEWPAGER_STATE = "DevicesViewPagerState";
    private static Bundle viewPagerBundle;
    private final List<INDIDevice> devices = Collections.synchronizedList(new ArrayList<>());
    private final HashMap<Integer, DeviceControlFragment> fragmentsMap = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DevicesFragmentAdapter fragmentAdapter;
    private TextView noDevicesText;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private TabLayoutMediator tabLayoutMediator;
    private Context context;
    private MenuItem searchMenu;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_control_panel, container, false);
        noDevicesText = rootView.findViewById(R.id.no_devices_label);
        viewPager = rootView.findViewById(R.id.indi_control_pager);
        viewPager.setOffscreenPageLimit(5);
        tabLayout = rootView.findViewById(R.id.indi_control_tabs);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showActionbar();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                showActionbar();
                DeviceControlFragment fragment = fragmentsMap.get(viewPager.getCurrentItem());
                if (fragment != null) {
                    RecyclerView listView = fragment.getListView();
                    if (listView != null) listView.smoothScrollToPosition(0);
                }
            }
        });
        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(devices.get(position).getName()));
        searchMenu = rootView.<Toolbar>findViewById(R.id.control_panel_toolbar).getMenu().add(R.string.search);
        searchMenu.setIcon(R.drawable.search);
        searchMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        SearchView searchView = new SearchView(context);
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnSearchClickListener(this);
        searchView.setOnCloseListener(this);
        searchView.setOnQueryTextListener(this);
        searchView.setImeOptions(searchView.getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchMenu.setActionView(searchView);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        connectionManager.addINDIListener(this);
        // Enumerate existing properties
        if (!connectionManager.isConnected()) {
            noDevices();
        } else {
            List<INDIDevice> list = connectionManager.getIndiConnection().getDevicesAsList();
            if (list.isEmpty()) {
                noDevices();
            } else {
                for (INDIDevice device : list) {
                    if (!devices.contains(device)) newDevice(device);
                }
                viewPager.post(() -> {
                    fragmentAdapter.notifyDataSetChanged();
                    if (viewPagerBundle != null) {
                        int position = viewPagerBundle.getInt(KEY_VIEWPAGER_STATE);
                        viewPager.setCurrentItem(position, false);
                        tabLayout.selectTab(tabLayout.getTabAt(position));
                    }
                });
                devices();
            }
        }
        fragmentAdapter = new DevicesFragmentAdapter(this);
        viewPager.setAdapter(fragmentAdapter);
        tabLayoutMediator.attach();
    }

    @Override
    public void onPause() {
        super.onPause();
        viewPagerBundle = new Bundle();
        viewPagerBundle.putInt(KEY_VIEWPAGER_STATE, viewPager.getCurrentItem());
    }

    @Override
    public void onStop() {
        super.onStop();
        noDevices();
        tabLayoutMediator.detach();
        viewPager.setAdapter(null);
        connectionManager.removeINDIListener(this);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        DeviceControlFragment fragment = fragmentsMap.get(viewPager.getCurrentItem());
        if (fragment != null) fragment.findPref(newText);
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    private void noDevices() {
        devices.clear();
        handler.post(() -> {
            if (noDevicesText != null) noDevicesText.setVisibility(View.VISIBLE);
            if ((fragmentAdapter != null) && (viewPager != null)) {
                fragmentAdapter.notifyDataSetChanged();
                viewPager.setVisibility(View.GONE);
            }
            if (searchMenu != null) searchMenu.setVisible(false);
        });
    }

    private void devices() {
        handler.post(() -> {
            if (noDevicesText != null) noDevicesText.setVisibility(View.GONE);
            if ((fragmentAdapter != null) && (viewPager != null)) {
                fragmentAdapter.notifyDataSetChanged();
                viewPager.setVisibility(View.VISIBLE);
            }
            if (searchMenu != null) searchMenu.setVisible(true);
        });
    }

    @Override
    public void connectionLost(INDIServerConnection connection) {
        noDevices();
    }

    @Override
    public void newDevice(INDIServerConnection connection, INDIDevice device) {
        Log.i("ControlPanelFragment", "New device: " + device.getName());
        handler.post(() -> {
            if (fragmentAdapter != null)
                fragmentAdapter.notifyItemInserted(newDevice(device));
        });
        devices();
    }

    private int newDevice(INDIDevice device) {
        devices.add(device);
        return devices.indexOf(device);
    }

    @Override
    public void removeDevice(INDIServerConnection connection, INDIDevice device) {
        Log.d("ControlPanelFragment", "Device removed: " + device.getName());
        int index = devices.indexOf(device);
        if (index != -1) {
            devices.remove(device);
            if (devices.isEmpty()) {
                noDevices();
            } else {
                viewPager.post(() -> fragmentAdapter.notifyItemRemoved(index));
            }
        }
    }

    @Override
    public void newMessage(INDIServerConnection connection, Date timestamp, String message) {

    }

    @Override
    public void onClick(View v) {
        tabLayout.setVisibility(View.GONE);
        viewPager.setUserInputEnabled(false);
    }

    @Override
    public boolean onClose() {
        tabLayout.setVisibility(View.VISIBLE);
        viewPager.setUserInputEnabled(true);
        DeviceControlFragment fragment = fragmentsMap.get(viewPager.getCurrentItem());
        if (fragment != null) fragment.stopSearch();
        return false;
    }

    private class DevicesFragmentAdapter extends FragmentStateAdapter {

        public DevicesFragmentAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            DeviceControlFragment fragment = new DeviceControlFragment();
            fragment.setDevice(devices.get(position));
            fragmentsMap.put(position, fragment);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }
    }
}