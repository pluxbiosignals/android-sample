/*
 *
 * Copyright (c) PLUX S.A., All Rights Reserved.
 * (www.plux.info)
 *
 * This software is the proprietary information of PLUX S.A.
 * Use is subject to license terms.
 *
 */
package info.plux.android.sample;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;

import info.plux.api.DeviceScan;

public class ScanActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private final String TAG = this.getClass().getSimpleName();

    private DeviceListAdapter deviceListAdapter;
    private boolean scanning;
    private final Handler handler = new Handler();

    private DeviceScan deviceScan;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scan);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setTitle(R.string.scan_activity_title);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        deviceScan = new DeviceScan(this, bluetoothDevice -> {
            if (bluetoothDevice != null) {
                deviceListAdapter.addDevice(bluetoothDevice);
                deviceListAdapter.notifyDataSetChanged();
            }
        });

        initView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan, menu);
        if (!scanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_scan) {
            deviceListAdapter.clear();
            scanDevice(true);

        } else if (item.getItemId() == R.id.menu_stop) {
            scanDevice(false);
        }

        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanDevice(false);
        deviceListAdapter.clear();
        deviceListAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (deviceScan != null) {
            deviceScan.closeScanReceiver();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final BluetoothDevice device = deviceListAdapter.getDevice(position);
        if (device == null) return;
        final Intent intent = new Intent(this, DeviceActivity.class);
        intent.putExtra(DeviceActivity.EXTRA_DEVICE, device);
        if (scanning) {
            deviceScan.stopScan();
            scanning = false;
        }
        startActivity(intent);
    }

    /*
     * UI elements
     */
    private void initView() {
        ListView devicesListView = findViewById(R.id.list_view);
        devicesListView.setOnItemClickListener(this);

        // Initializes list view adapter.
        deviceListAdapter = new DeviceListAdapter();
        devicesListView.setAdapter(deviceListAdapter);
        scanDevice(true);
    }

    private void scanDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(() -> {
                scanning = false;
                deviceScan.stopScan();
                invalidateOptionsMenu();
            }, SCAN_PERIOD);

            scanning = true;
            deviceScan.doDiscovery();
        } else {
            scanning = false;
            deviceScan.stopScan();
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class DeviceListAdapter extends BaseAdapter {
        private final ArrayList<BluetoothDevice> devices;
        private final LayoutInflater inflater;

        public DeviceListAdapter() {
            super();
            devices = new ArrayList<>();
            inflater = ScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if (!devices.contains(device)) {
                devices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return devices.get(position);
        }

        public void clear() {
            devices.clear();
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int i) {
            return devices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = inflater.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = view.findViewById(R.id.device_address);
                viewHolder.deviceName = view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = devices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
            } else {
                viewHolder.deviceName.setText("unknown device");
            }
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}