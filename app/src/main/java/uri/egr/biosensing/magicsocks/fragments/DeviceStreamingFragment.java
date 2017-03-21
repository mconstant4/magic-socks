package uri.egr.biosensing.magicsocks.fragments;

import android.app.Fragment;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.File;

import uri.egr.biosensing.magicsocks.MainActivity;
import uri.egr.biosensing.magicsocks.R;
import uri.egr.biosensing.magicsocks.gatt_attributes.GattCharacteristics;
import uri.egr.biosensing.magicsocks.gatt_attributes.GattServices;
import uri.egr.biosensing.magicsocks.services.BLEConnectionService;
import uri.egr.biosensing.magicsocks.services.CSVLoggingService;

/**
 * Created by mcons on 12/29/2016.
 */

public class DeviceStreamingFragment extends Fragment {
    public static final String HEADER = "date,time,ax,ay,az,gx,gy,gz,pressure,flexV,flexR,flexV2,flexR2";
    public static final String BUNDLE_DEVICE_ADDRESS = "bundle_device_address";

    private static final float VCC = 4.98f;
    private static final float R_DIV = 10000.0f;

    private String mDeviceAddress;
    private BLEConnectionService mService;
    private MainActivity mActivity;
    private boolean mServiceBound;
    private File mLogFile;
    private ServiceConnection mServiceConnection = new BLEServiceConnection();

    private Button mDisconnectButton;

    private BroadcastReceiver mBLEUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("BLE", "Received Update");
            String action = intent.getStringExtra(BLEConnectionService.INTENT_EXTRA);
            switch (action) {
                case BLEConnectionService.GATT_STATE_CONNECTED:
                    mActivity.showMessage("Gatt Server Connected");
                    mService.discoverServices(mDeviceAddress);
                    break;
                case BLEConnectionService.GATT_STATE_DISCONNECTED:
                    mActivity.showMessage("Gatt Server Disconnected");
                    break;
                case BLEConnectionService.GATT_DISCOVERED_SERVICES:
                    mActivity.showMessage("Gatt Services Discovered");
                    //Enable Notifications for Desired Characteristics Here

                    //Enables notifications for Transmitter (TX) Characteristic by default
                    BluetoothGattCharacteristic characteristic = mService.getCharacteristic(mDeviceAddress, GattServices.UART_SERVICE, GattCharacteristics.TX_CHARACTERISTIC);
                    if (characteristic != null) {
                        mService.enableNotifications(mDeviceAddress, characteristic);
                    }
                    break;
                case BLEConnectionService.GATT_CHARACTERISTIC_READ:
                    byte[] data = intent.getByteArrayExtra(BLEConnectionService.INTENT_DATA);
                    //Parse contents from data here

                    if (data.length < 20) {
                        Log.d(this.getClass().getSimpleName(), "Data length too short (" + data.length + ")");
                        return;
                    }

                    int ax = data[0] | (data[1] << 8);
                    int ay = data[2] | (data[3] << 8);
                    int az = data[4] | (data[5] << 8);
                    int gx = data[6] | (data[7] << 8);
                    int gy = data[8] | (data[9] << 8);
                    int gz = data[10] | (data[11] << 8);
                    int pressure = data[12] | (data[13] << 8);
                    int flex1 = data[14] | (data[15] << 8) | (data[16] << 16);
                    int flex2 = data[17] | (data[18] << 8) | (data[19] << 16);

                    float flexV = (float) (flex1 * VCC / 1023.0);
                    float flexR = (float) (R_DIV * (VCC / flexV - 1.0));

                    float flexV2 = (float) (flex2 * VCC / 1023.0);
                    float flexR2 = (float) (R_DIV * (VCC / flexV2 - 1.0));


                    //Convert contents to String if necessary
                    String contents = ax + "," + ay + "," + az + "," + gx  + "," + gy + "," + gz + "," + pressure + "," + flexV + "," + flexR + "," + flexV2 + "," + flexR2;

                    //Log data
                    Log.d(this.getClass().getSimpleName(), "Logging data");
                    CSVLoggingService.start(mActivity, mLogFile, HEADER, contents);
                    break;
                case BLEConnectionService.GATT_DESCRIPTOR_WRITE:
                    break;
                case BLEConnectionService.GATT_NOTIFICATION_TOGGLED:
                    break;
                case BLEConnectionService.GATT_DEVICE_INFO_READ:
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        mActivity = (MainActivity) getActivity();
        mDeviceAddress = getArguments().getString(BUNDLE_DEVICE_ADDRESS, null);
        if (mDeviceAddress == null) {
            mActivity.finish();
            return;
        }

        mLogFile = new File(Environment.getExternalStorageDirectory(), "Documents/magic_socks/session_1.csv");
        int count = 2;
        while (mLogFile.exists()) {
            mLogFile = new File(Environment.getExternalStorageDirectory(), "Documents/magic_socks/session_" + count++ + ".csv");
        }

        mActivity.registerReceiver(mBLEUpdateReceiver, new IntentFilter(BLEConnectionService.INTENT_FILTER_STRING));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_streaming, container, false);

        mDisconnectButton = (Button) view.findViewById(R.id.disconnect_button);
        mDisconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.disconnect(mDeviceAddress);
                mActivity.disconnect();
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        mActivity.bindService(new Intent(getActivity(), BLEConnectionService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unbindService(mServiceConnection);
        try {
            mActivity.unregisterReceiver(mBLEUpdateReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private class BLEServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mServiceBound = true;
            mService = ((BLEConnectionService.BLEConnectionBinder) iBinder).getService();
            Log.d("BLEServiceConnection", "Connecting to Device...");
            mService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("BLEServiceConnection", "Connection Failed");
            mServiceBound = false;
        }
    }
}
