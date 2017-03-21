package uri.egr.biosensing.magicsocks;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Arrays;

import uri.egr.biosensing.magicsocks.fragments.DeviceScanFragment;
import uri.egr.biosensing.magicsocks.fragments.DeviceStreamingFragment;
import uri.egr.biosensing.magicsocks.models.BluetoothDeviceModel;

public class MainActivity extends AppCompatActivity {
    public static final int PERMISSIONS_REQUEST_CODE = 3496;

    private FrameLayout mFragmentContainer;
    private CoordinatorLayout mMessageContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFragmentContainer = (FrameLayout) findViewById(R.id.fragment_container);
        mMessageContainer = (CoordinatorLayout) findViewById(R.id.message_container);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissionsToRequest = new ArrayList<>();
            String[] permissionsRequired = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_COARSE_LOCATION};
            for (String permission : permissionsRequired) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
            if (permissionsToRequest.size() > 0) {
                Object[] permissionsToRequestObjects = permissionsToRequest.toArray();
                String[] permissionsToRequestStrings = Arrays.copyOf(permissionsToRequestObjects, permissionsToRequestObjects.length, String[].class);
                requestPermissions(permissionsToRequestStrings, PERMISSIONS_REQUEST_CODE);
            } else {
                getFragmentManager().beginTransaction().replace(R.id.fragment_container,new DeviceScanFragment()).commit();
            }
        } else {
            getFragmentManager().beginTransaction().replace(R.id.fragment_container,new DeviceScanFragment()).commit();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean granted = true;
            int i;
            for (i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    showMessage("Please Grant All Permissions");
                    granted = false;
                }
            }
            if (granted) {
                getFragmentManager().beginTransaction().replace(R.id.fragment_container,new DeviceScanFragment()).commit();
            }
        }
    }

    public void connect(BluetoothDeviceModel bluetoothDeviceModel) {
        Bundle bundle = new Bundle();
        bundle.putString(DeviceStreamingFragment.BUNDLE_DEVICE_ADDRESS, bluetoothDeviceModel.getDeviceAddress());
        DeviceStreamingFragment deviceStreamingFragment = new DeviceStreamingFragment();
        deviceStreamingFragment.setArguments(bundle);
        getFragmentManager().beginTransaction().replace(R.id.fragment_container,deviceStreamingFragment).commit();
    }

    public void disconnect() {
        getFragmentManager().beginTransaction().replace(R.id.fragment_container,new DeviceScanFragment()).commit();
    }

    public void showMessage(String message) {
        Snackbar.make(mMessageContainer, message, Snackbar.LENGTH_LONG).show();
    }
}
