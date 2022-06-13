/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.ble_connection;

import static com.example.ble_connection.BluetoothLeService.UUID_READ_FROM_ESP;
import static com.example.ble_connection.BluetoothLeService.UUID_WRITE_TO_ESP;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ble_communication.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class MainActivity extends Activity {

    private final static String TAG = MainActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    public static boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic mWriteCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private EditText textToSend;
    private Button btnSend;

    private ListAdapter mListAdapter;
    private ListView dataList;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {

                mConnected = true;

                Log.i(TAG, getString(R.string.connected));

                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();

                btnSend.setEnabled(true);

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;

                Log.i(TAG, getString(R.string.disconnected));

                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();

                btnSend.setEnabled(false);

                Toast.makeText(MainActivity.this, getString(R.string.disconnected), Toast.LENGTH_SHORT).show();
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final Intent i = new Intent(MainActivity.this, DeviceScanActivity.class);
                        i.putExtra("CONNECT_AUTO", false);
                        startActivity(i);
                        finish();
                        //doConnect(null);
                    }
                }, 1000);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                String s = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.i(TAG, "ESP says: " + s);

                if (s.startsWith("ESP32=")) {
                    s = s.replace("ESP32=", "");
                    Log.d(TAG, "Command: ESP32");
                    Log.d(TAG, "Value: " + s);
                    mListAdapter.addEspMessage(s);
                } else {
                    Log.e(TAG, "Unknown command");
                }
            } /*else {
                Log.e(TAG, "Action=" + action);
            }*/
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        textToSend = findViewById(R.id.text_to_send);
        btnSend = findViewById(R.id.btn_send);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String data = textToSend.getText().toString();
                if (!data.isEmpty()) {
                    sendData(data);
                }
            }
        });

        dataList = findViewById(R.id.data_list);

        mListAdapter = new ListAdapter();
        dataList.setAdapter(mListAdapter);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.app_name) + " - " + getString(R.string.init));
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ActionBar actionBar = getActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(String.format(Locale.US, "%s - %s",
                            getString(R.string.app_name), getString(resourceId)));
                }
            }
        });
    }

    private void sendData(String data) {

        if (mConnected) {
            if (mWriteCharacteristic != null) {
                String newData = "ECHO=" + data;
                byte[] strBytes = newData.getBytes();
                mWriteCharacteristic.setValue(strBytes);
                mBluetoothLeService.writeCharacteristic(mWriteCharacteristic);
                mListAdapter.addAndroidMessage(data);
                textToSend.setText("");
            } else {
                Log.e(TAG, "mWriteCharacteristic is null");
                doDisconnect(null);

                Toast.makeText(MainActivity.this, getString(R.string.disconnected), Toast.LENGTH_SHORT).show();
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final Intent i = new Intent(MainActivity.this, DeviceScanActivity.class);
                        i.putExtra("CONNECT_AUTO", false);
                        startActivity(i);
                        finish();
                    }
                }, 1000);
            }
        } else {
            Toast.makeText(this, getString(R.string.device_not_connected), Toast.LENGTH_SHORT).show();
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);

                if (gattCharacteristic.getUuid().equals(UUID_READ_FROM_ESP)) {
                    //Log.e(TAG, "------------------ Found -------------------");
                    mNotifyCharacteristic = gattCharacteristic;
                    mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                }

                if (gattCharacteristic.getUuid().equals(UUID_WRITE_TO_ESP)) {
                    mWriteCharacteristic = gattCharacteristic;
                    //Log.e(TAG, "------------------ Found -------------------");
                }
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void doDisconnect(View view) {
        if (mBluetoothLeService != null) {
            mBluetoothLeService.disconnect();
        }
        //finish();
    }

    public void doConnect(View view) {
        if (mBluetoothLeService != null) {
            mBluetoothLeService.connect(mDeviceAddress);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        /*
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }*/
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            /*case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;*/
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Adapter for holding devices found through scanning.
    private class ListAdapter extends BaseAdapter {
        private ArrayList<myMessages> messages;
        private LayoutInflater mInflater;

        class myMessages {
            int type;
            String msg;
        }

        public ListAdapter() {
            super();
            messages = new ArrayList<myMessages>();
            mInflater = MainActivity.this.getLayoutInflater();
        }

        public void addAndroidMessage(String message) {

            myMessages myMsg = new myMessages();
            myMsg.type = 1;
            myMsg.msg = message;

            messages.add(myMsg);
            notifyDataSetChanged();
        }

        public void addEspMessage(String message) {

            myMessages myMsg = new myMessages();
            myMsg.type = 2;
            myMsg.msg = message;

            messages.add(myMsg);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return messages.size();
        }

        @Override
        public Object getItem(int i) {
            return messages.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @SuppressLint("MissingPermission")
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            MainActivity.ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflater.inflate(R.layout.listitem, null);
                viewHolder = new MainActivity.ViewHolder();
                viewHolder.msg = (TextView) view.findViewById(R.id.msg);
                view.setTag(viewHolder);
            } else {
                viewHolder = (MainActivity.ViewHolder) view.getTag();
            }

            myMessages msg = messages.get(i);
            if (msg.type == 1) {
                viewHolder.msg.setText("Android: " + messages.get(i).msg);
            } else {
                viewHolder.msg.setText("ESP: " + messages.get(i).msg);
            }

            return view;
        }
    }

    static class ViewHolder {
        TextView msg;
    }
}
