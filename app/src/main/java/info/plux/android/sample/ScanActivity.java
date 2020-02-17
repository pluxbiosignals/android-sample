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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.*;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import info.plux.api.DeviceScan;
import info.plux.api.interfaces.Constants;

import java.util.ArrayList;

public class ScanActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private final String TAG = this.getClass().getSimpleName();

    private DeviceListAdapter deviceListAdapter;
    private boolean scanning;
    private Handler handler = new Handler();

    private DeviceScan deviceScan;
    private boolean isScanDevicesUpdateReceiverRegistered = false;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private ListView devicesListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scan);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.scan_activity_title);
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        deviceScan = new DeviceScan(this);

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
        switch (item.getItemId()) {
            case R.id.menu_scan:
                deviceListAdapter.clear();
                scanDevice(true);
                break;
            case R.id.menu_stop:
                scanDevice(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(scanDevicesUpdateReceiver, new IntentFilter(Constants.ACTION_MESSAGE_SCAN));
        isScanDevicesUpdateReceiverRegistered = true;
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

        if(deviceScan != null){
            deviceScan.closeScanReceiver();
        }

        if(isScanDevicesUpdateReceiverRegistered){
            unregisterReceiver(scanDevicesUpdateReceiver);
            isScanDevicesUpdateReceiverRegistered = false;
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
    private void initView(){
        devicesListView = findViewById(R.id.list_view);
        devicesListView.setOnItemClickListener(this);

        // Initializes list view adapter.
        deviceListAdapter = new DeviceListAdapter();
        devicesListView.setAdapter(deviceListAdapter);
        scanDevice(true);
    }

    private void scanDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    deviceScan.stopScan();
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            scanning = true;
            deviceScan.doDiscovery();
        } else {
            scanning = false;
            deviceScan.stopScan();
        }
        invalidateOptionsMenu();
    }

    private final BroadcastReceiver scanDevicesUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(action.equals(Constants.ACTION_MESSAGE_SCAN)){
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(Constants.EXTRA_DEVICE_SCAN);

                if(bluetoothDevice != null){
                    deviceListAdapter.addDevice(bluetoothDevice);
                    deviceListAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    // Adapter for holding devices found through scanning.
    private class DeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> devices;
        private LayoutInflater inflater;

        public DeviceListAdapter() {
            super();
            devices = new ArrayList<>();
            inflater = ScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!devices.contains(device)) {
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
            }
            else {
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