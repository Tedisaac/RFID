package com.rr.hf.rrhfoem09getspecdata;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.ClipDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private Button bConnect = null;
    private Button bClear;
    public static TextView msgText = null;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    ClipDrawable batteryLevelDrawable;
    ImageView batteryImage;

    private boolean mScanning;
    private Handler handler;
    private  List<BluetoothDevice> leDeviceList;

    private ArrayAdapter<String> leDeviceAdapter = null;
    private AlertDialog deviceRelatedDialog = null;
    public static StringBuffer msgBuffer;
    private BleCommService mBleCommService;
    private String mDeviceAddress;
    public static String serverDeviceAddress;

    // Stops scanning after 5 seconds.
    private static final long SCAN_PERIOD = 5000;

    //callback in which bluetooth scan results are returned
    private ScanCallback leScanCallback = null;

    //for writing to file
    private String folderPath = "";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBleCommService = ((BleCommService.LocalBinder) service).getService();
            if (!mBleCommService.initialize()) {
                Toast.makeText(MainActivity.this, "Unable to initialize Bluetooth", Toast.LENGTH_LONG).show();
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBleCommService = null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bConnect = findViewById(R.id.btnConnect);
        msgText = findViewById(R.id.logTxt);
        batteryImage = findViewById(R.id.battery_fill);
        bClear = findViewById(R.id.btnClear);
        batteryLevelDrawable = (ClipDrawable) batteryImage.getDrawable();
        leDeviceList = new ArrayList<>();
        leDeviceAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.list_ble_layout);
        msgBuffer = new StringBuffer();
        handler = new Handler();

        //adding custom app bar in activity
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        //checking whether ble is supported in device or not
        //checking whether ble is available or not
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(MainActivity.this, "Bluetooth Low Energy Not Supported, So App can't work.", Toast.LENGTH_LONG).show();
            finish();
        }

        //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        ActivityResultLauncher<Intent> btStartActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK) {
                Toast.makeText(MainActivity.this, "Bluetooth is required to run this app, it will not function without bluetooth", Toast.LENGTH_LONG).show();
                finish();
            }
            else {
                //switch on, and connect with bluetooth
                final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                bluetoothAdapter = bluetoothManager.getAdapter();
                bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        });

        //request location and file writing permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityResultLauncher<String[]> locationPermissionRequest =
                    registerForActivityResult(new ActivityResultContracts
                                    .RequestMultiplePermissions(), result -> {
                                Boolean fineLocationGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                                if(fineLocationGranted == null)
                                    fineLocationGranted = false;
                                Boolean coarseLocationGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                                if(coarseLocationGranted == null)
                                    coarseLocationGranted = false;

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !fineLocationGranted) {
                                    Toast.makeText(MainActivity.this, "Location Permission is required to connect with reader !", Toast.LENGTH_LONG).show();
                                    finish();
                                } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !coarseLocationGranted) {
                                    Toast.makeText(MainActivity.this, "Location Permission is required to connect with reader !", Toast.LENGTH_LONG).show();
                                    finish();
                                }
                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { //android 12 or higher device, check for BLE Scan and Connect permissions also
                                    Boolean bleScanGranted = result.get(Manifest.permission.BLUETOOTH_SCAN);
                                    if(bleScanGranted == null)
                                        bleScanGranted = false;
                                    Boolean bleConnectGranted = result.get(Manifest.permission.BLUETOOTH_CONNECT);
                                    if(bleConnectGranted == null)
                                        bleConnectGranted = false;
                                    if (bleScanGranted && bleConnectGranted) {
                                        // ble scan and connect granted, ask for permissions
                                        //enabling bluetooth
                                        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                                            btStartActivity.launch(enableBtIntent);
                                        }
                                    } else {
                                        Toast.makeText(MainActivity.this, "BLE Scan, and BLE Connect permission required to scan and connect with reader !", Toast.LENGTH_LONG).show();
                                        finish();
                                    }
                                }
                            }
                    );
            //requesting location permission
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationPermissionRequest.launch(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                });
            }
            else {
                locationPermissionRequest.launch(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            }
        }

        //enabling bluetooth if version is less than 12
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && (bluetoothAdapter == null || !bluetoothAdapter.isEnabled())) {
            btStartActivity.launch(enableBtIntent);
        }

        if(bluetoothAdapter == null || bleScanner == null) {
            //switch on, and connect with bluetooth
            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        //establish connection with service...
        Intent gattServiceIntent = new Intent(this, BleCommService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


        leScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, final ScanResult result) {
                super.onScanResult(callbackType, result);
                runOnUiThread(() -> {
                    if(mScanning) {
                        BluetoothDevice device = result.getDevice();
                        if(!leDeviceList.contains(device)) {
                            leDeviceList.add(device);
                            String deviceLabel;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                                    deviceLabel = device.getName()+ " -- " +device.getAddress();
                                else
                                    deviceLabel = device.getAddress();
                            }
                            else {
                                deviceLabel = device.getName()+ " -- " +device.getAddress();
                            }
                            leDeviceAdapter.add(deviceLabel);
                            leDeviceAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
            }
        };

        //click events for buttons
        bConnect.setOnClickListener(view -> {
            if(bConnect.getText().toString().trim().equals("Disconnect")) {
                mBleCommService.disconnect();
                return;
            }
            //starting ble scan
            scanLeDevice(true);
        });

        bClear.setOnClickListener(v -> {
            msgBuffer.setLength(0);
            msgText.setText("");
        });
    }

    //runnable callback function for timer
    private final Runnable bleRunnable = new Runnable() {
        @Override
        public void run() {
            mScanning = false;
            try { bleScanner.stopScan(leScanCallback); } catch(SecurityException sece) { Toast.makeText(MainActivity.this, "Permission to stop scan BLE Device is not provided -- " +sece.getMessage(), Toast.LENGTH_LONG).show(); }
            runOnUiThread(() -> {
                //generate dialog with available devices
                deviceRelatedDialog.dismiss();
                deviceRelatedDialog = null;
                deviceRelatedDialog = generateDeviceListDialog(true, true);
                deviceRelatedDialog.setCanceledOnTouchOutside(false);
                deviceRelatedDialog.show();
            });
        }
    };

    //function with handler to scan ble devices
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(bleRunnable, SCAN_PERIOD);

            mScanning = true;
            try { bleScanner.startScan(leScanCallback); } catch(SecurityException sece) { Toast.makeText(MainActivity.this, "Permission to scan BLE Device is not provided -- " +sece.getMessage(), Toast.LENGTH_LONG).show(); }
            runOnUiThread(() -> {
                //generate dialog with available devices
                deviceRelatedDialog = generateDeviceListDialog(false, false);
                deviceRelatedDialog.setCanceledOnTouchOutside(false);
                deviceRelatedDialog.show();
            });

        } else {
            mScanning = false;
            try { bleScanner.stopScan(leScanCallback); } catch(SecurityException sece) { Toast.makeText(MainActivity.this, "Permission to  stop scan BLE Device is not provided -- " +sece.getMessage(), Toast.LENGTH_LONG).show(); }
        }
    }

    //function to generate dialog with list
    private AlertDialog generateDeviceListDialog(boolean isComplete, boolean isDevices) {
        LayoutInflater inflater = (LayoutInflater)getApplicationContext().getSystemService (Context.LAYOUT_INFLATER_SERVICE);
        final View dialogView = inflater.inflate(R.layout.layout_dialog_scan_device, null);
        final AlertDialog.Builder deviceListDialog = new AlertDialog.Builder(MainActivity.this);
        deviceListDialog.setView(dialogView);
        TextView statusDevice = dialogView.findViewById(R.id.message);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress);
        LinearLayout listLayout = dialogView.findViewById(R.id.listPart);
        LinearLayout progressLayout = dialogView.findViewById(R.id.progressPart);
        statusDevice.setText(R.string.search_device);

        if(!isComplete) {
            listLayout.setVisibility(View.GONE);
            progressLayout.setVisibility(View.VISIBLE);
        }
        else {
            if(!isDevices) {
                listLayout.setVisibility(View.GONE);
                progressLayout.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                statusDevice.setText(R.string.str_msg_mode_no_support);
            }
            if(isDevices) {
                deviceListDialog.setAdapter(leDeviceAdapter, (dialogInterface, i) -> {
                    //code or function to detect clicked device name and connect device.
                    String myDeviceAddress = leDeviceAdapter.getItem(i).substring(leDeviceAdapter.getItem(i).indexOf("--")+3).trim();
                    try {
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append(getResources().getString(R.string.text_connecting));
                        msgText.setText(msgBuffer);
                        mDeviceAddress = myDeviceAddress;
                        mBleCommService.connect(mDeviceAddress);
                    }
                    catch(Exception cex) {
                        msgBuffer.append("\r\n").append("Error in connecting Device !").append("\r\n").append(cex.getMessage());
                    }
                });
                progressLayout.setVisibility(View.GONE);
                listLayout.setVisibility(View.VISIBLE);
            }
        }

        deviceListDialog.setTitle("Select Device");

        deviceListDialog.setNegativeButton("Cancel", (dialogInterface, i) -> {
            scanLeDevice(false);
            leDeviceAdapter.clear();
            handler.removeCallbacks(bleRunnable);
            leDeviceList.clear();
            msgBuffer.append("\r\n").append("You have not selected any device, select device to operate");
            msgText.setText(msgBuffer);
            dialogInterface.dismiss();
        });

        deviceListDialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if(keyCode == KeyEvent.KEYCODE_BACK) {
                scanLeDevice(false);
                leDeviceAdapter.clear();
                leDeviceList.clear();
                handler.removeCallbacks(bleRunnable);
                msgBuffer.append("\r\n").append("You have not selected any device, select device to operate");
                msgText.setText(msgBuffer);
                dialogInterface.dismiss();
                return true;
            }
            return false;
        });
        return deviceListDialog.create();
    }

    //broadcast receiver to react on response of service
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleCommService.ACTION_GATT_CONNECTED.equals(action)) {
                bConnect.setText(R.string.disconnect);
                addLogs("Connected Successfully To -- " + mDeviceAddress);
            } else if (BleCommService.ACTION_GATT_DISCONNECTED.equals(action)) {
                batteryLevelDrawable.setLevel(0);
                bConnect.setText(R.string.search_ble);
                addLogs("Disconnected From -- " + mDeviceAddress);
            } else if (BleCommService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                mBleCommService.enableBatteryNotification();
                displayGattServices(mBleCommService.getSupportedGattServices());
            } else if(BleCommService.ACTION_SERVICE_NOT_FOUND.equals(action)) {
                addLogs("Service Not Found !");
            } else if(BleCommService.ACTION_CHARACTERISTIC_NOT_FOUND.equals(action)) {
                addLogs("Characteristic Not Found !");
            } else if(BleCommService.ACTION_DATA_WRITE_SUC.equals(action)) {
                addLogs("Data Sent Successfully...");
            } else if(BleCommService.ACTION_DATA_WRITE_FAIL.equals(action)) {
                addLogs("Error in writing Data to Device !!");
            } else if(BleCommService.ACTION_CHAR_EMPTY.equals(action)) {
                addLogs("Characteristic is empty...");
            } else if (BleCommService.ACTION_DATA_AVAILABLE.equals(action)) {
                final byte[] txValue = intent.getByteArrayExtra(BleCommService.EXTRA_DATA);
                runOnUiThread(() -> {
                    try {
                        String text = getHexString(txValue);
                        displayData("Received : " + text);
                        displayData(processResponse(txValue));
                    } catch (Exception e) {
                        addLogs("Error in receiving data -- " + e.getClass().getName() + " -- " + e.getMessage());
                    }
                });
            } else if(BleCommService.BATTERY_LEVEL_AVAILABLE.equals(action)) {
                final int batteryLevel = intent.getIntExtra(BleCommService.BATTERY_DATA, 0);
                runOnUiThread(() -> batteryLevelDrawable.setLevel(batteryLevel*100));
            }
        }
    };

    //intent filter to access response type from service
    private static IntentFilter myGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleCommService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleCommService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleCommService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleCommService.ACTION_SERVICE_NOT_FOUND);
        intentFilter.addAction(BleCommService.ACTION_CHARACTERISTIC_NOT_FOUND);
        intentFilter.addAction(BleCommService.ACTION_DATA_WRITE_SUC);
        intentFilter.addAction(BleCommService.ACTION_DATA_WRITE_FAIL);
        intentFilter.addAction(BleCommService.ACTION_CHAR_EMPTY);
        intentFilter.addAction(BleCommService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BleCommService.BATTERY_LEVEL_AVAILABLE);
        return intentFilter;
    }

    //function to display gatt services
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        displayData("Ble Device Connected...");
    }

    //function to display data
    private void displayData(String data) {
        if (data != null) {
            addLogs("Data -- " + data);
        }
    }

    //function to convert byte array to hex string
    private String getHexString(byte[] values) {
        StringBuilder sb = new StringBuilder();
        for (byte b : values) {
            String st = String.format("%02X", b);
            sb.append(st);
        }
        return sb.toString();
    }

    //function to prepend logs - so, latest logs can appear at start of logs area
    private void addLogs(String log) {
        msgBuffer.insert(0, log + "\r\n\r\n");
        msgText.setText(msgBuffer);
    }

    //function to convert hex string to byte array
    private byte[] getHexByte(String hex) {
        byte[] val = new byte[hex.length()/2];
        int index = 0;
        int mainVal = 0;
        for(int i = 0; i < val.length; i++) {
            index = i * 2;
            mainVal = Integer.parseInt(hex.substring(index, index+2), 16);
            val[i] = (byte)mainVal;
        }
        return val;
    }

    //function to calculate bcc, and adding length to data
    private byte getBcc(byte[] buffer) {
        byte myBcc; // temporary variable
        myBcc = buffer[0]; //copy argument value in temporary variable
        int length = buffer.length-1;
        int pos = 1;
        length--;
        while(length >= 0) // loop for each character
        {
            myBcc ^= buffer[pos];
            pos++;
            length--;
        }
        return myBcc;
    }

    //function to send command bytes to ble reader
    private void sendCommand(byte[] commandBytes) {
        try {
            if (!mBleCommService.writeRXData(commandBytes))
                addLogs("Data Write Failed...");
            addLogs("Data Sent -- " + getHexString(commandBytes));
        }
        catch(Exception enex) {
            addLogs("Error occurred -- "+enex.getClass().getName()+" -- "+enex.getMessage());
        }
    }

    //common function to send data/write data to server or to client
    private void sendData(String data) {
        byte[] value;
        try {
            //send data to service
            //value = message.getBytes("UTF-8"); //commented because all are hex string and converted to bytes.
            value = getHexByte(data);
            //code added to append bcc
            byte[] finalValue = new byte[value.length+1];
            byte bcc = getBcc(value);
            System.arraycopy(value, 0, finalValue, 0, value.length);
            finalValue[finalValue.length-1] = bcc;
            //end added
            sendCommand(finalValue);
        }
        //catch(UnsupportedEncodingException enex) { //commented because all are hex string and converted to bytes.
        catch(Exception enex) {
            Toast.makeText(MainActivity.this, "Format Not Supported !!", Toast.LENGTH_LONG).show();
        }
    }

    //function to process command response
    public String processResponse(byte[] data) {
        if(data[1] == (byte)0x2F && data[2] == (byte)0xF1) {
            if(!(data[0] == 0x2C || data[0] == 0x2F) && !(data[6] == 0x04 || data[6] == 0x07))
                return "Packet Length is Unexpected, Wrong data received.";
            //fetching serial number
            String srNo = "";
            byte[] serialNo = new byte[3];
            System.arraycopy(data, 3, serialNo, 0, serialNo.length);
            srNo = getHexString(serialNo);
            //fetching uid
            String myUid = "";
            byte[] uid = new byte[data[6]];
            System.arraycopy(data, 7, uid, 0, data[6]);
            myUid = getHexString(uid);
            //fetching data, and displaying in ascii format
            String myData = "";
            byte[] asciiData = new byte[32];
            if(data[6] == 0x04)
                System.arraycopy(data, 11, asciiData, 0, 32);
            if(data[6] == 0x07)
                System.arraycopy(data, 14, asciiData, 0, 32);
            myData = new String(asciiData, StandardCharsets.US_ASCII);
            //returning data
            return "\n\nSerial Number: "+srNo+"\nUID: "+myUid+"\nData in ASCII Format: "+myData+"\n";
        }
        else {
            return "Check Command code, wrong command received...";
        }
    }

    //function to check existence, and create directory and file, if needed
    public boolean chkFileDir(boolean needToCreate, String fileName) {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            if(Build.VERSION.SDK_INT >= 19) {
                folderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
                folderPath = folderPath.substring(0, folderPath.lastIndexOf('/'));
                folderPath = folderPath + "/RRHFOEM09";
            }
            else {
                folderPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/RRHFOEM09";
            }
            File dirFile = new File(folderPath);
            try {
                if (!(dirFile.exists() && dirFile.isDirectory())) {
                    if(!dirFile.mkdir()) {
                        Toast.makeText(MainActivity.this, "Failed to create Folder for RRHFOEM09", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
                File myFile = new File(folderPath+"/"+fileName);
                if(!myFile.exists()) {
                    if(needToCreate) {
                        if(!myFile.createNewFile())
                            Toast.makeText(MainActivity.this, "Failed to create File in RRHFOEM09", Toast.LENGTH_SHORT).show();
                        return myFile.exists();
                    }
                    else {
                        return false;
                    }
                }
                else {
                    return true;
                }
            }
            catch(Exception fe) {
                Toast.makeText(MainActivity.this, "Error Occurred in File Operation..." + fe.getMessage(), Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        else {
            Toast.makeText(MainActivity.this, "Storage Not available !!", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    //function to write data in log file
    private boolean writeLogToFile(String fileName, String data) {
        if(chkFileDir(true, fileName)) {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(folderPath+"/"+fileName, true));
                writer.write(data+"\r\n");
                writer.close();
                return true;
            }
            catch(Exception fe) {
                Toast.makeText(MainActivity.this, "Error Occurred in File Write Operation..." + fe.getMessage(), Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        else {
            return false;
        }
    }

    //function to get current date time string
    private String getCurDateTime() {
        Date dNow = new Date( );
        SimpleDateFormat ft = new SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.US);
        return ft.format(dNow);
    }

    private String getRtc() {
        Date dNow = new Date( );
        SimpleDateFormat ft = new SimpleDateFormat("kkmmssddMMyy", Locale.getDefault());
        return "09E107"+ft.format(dNow);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, myGattUpdateIntentFilter());
        if (mBleCommService != null) {
            mBleCommService.connect(mDeviceAddress);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(mGattUpdateReceiver); //temporarily
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBleCommService = null;
    }
}
