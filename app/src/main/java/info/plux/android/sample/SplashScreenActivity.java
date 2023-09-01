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

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SplashScreenActivity extends Activity {
    private final String TAG = getClass().getSimpleName();

    //Permission
    final private int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 123;
    final private int REQUEST_ENABLE_BT = 0;

    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        //Initializes a BluetoothAdapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        //checks if bluetooth is supported on the device
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Error - Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        new Handler().postDelayed(() -> {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                askPermissions();
            } else {
                testBluetooth();
            }
        }, 2 * 1000); //wait for 5 seconds
    }

    private void startScanActivity() {
        Intent intent = new Intent(SplashScreenActivity.this, ScanActivity.class);
        startActivity(intent);

        //close this activity
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // if user chose not to grant permissions
        if (requestCode == REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS) {
            if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "All permissions should be granted", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        // if user chose not to enable bluetooth
        else if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
                return;
            } else {
                startScanActivity();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS) {
            Map<String, Integer> perms = new HashMap<String, Integer>();
            // Initial
            perms.put(Manifest.permission.ACCESS_COARSE_LOCATION, PackageManager.PERMISSION_GRANTED);
            perms.put(Manifest.permission.READ_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
            perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
            // Fill with results
            for (int i = 0; i < permissions.length; i++) {
                perms.put(permissions[i], grantResults[i]);
            }

            // Check for ACCESS_FINE_LOCATION
            if (perms.get(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && perms.get(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                // All Permissions Granted
                Log.d(TAG, "All Permissions Granted");

                testBluetooth();
            } else {
                // Permission Denied
                Toast.makeText(this, "Some Permission was denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void askPermissions() {
        List<String> permissionsNeeded = new ArrayList<String>();

        final List<String> permissionsList = new ArrayList<String>();
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissionsNeeded.add("Bluetooth Scan");
        }
        if (!addPermission(permissionsList, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            permissionsNeeded.add("Read");
        }
        if (!addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissionsNeeded.add("Write in Storage");
        }

        if (!permissionsList.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsList.toArray(new String[0]), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        } else {
            testBluetooth();
        }
    }

    private boolean addPermission(List<String> permissionsList, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            return ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
        }
        return true;
    }

    private void testBluetooth() {
        // Ensures Bluetooth is enabled on the device. If Bluetooth is not currently enabled
        // fire an intent to display a dialog asking the user to grant permission to enable it
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBthIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBthIntent, REQUEST_ENABLE_BT);
        } else {
            startScanActivity();
        }
    }
}
