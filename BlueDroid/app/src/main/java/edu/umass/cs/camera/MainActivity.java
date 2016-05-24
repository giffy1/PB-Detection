package edu.umass.cs.camera;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main UI and entry point for the data collection application. The UI is responsible for handling
 * the user interactions with the available services, such as starting the sensor service connection
 * with available Wearables and LightBlue Bean sensors, as well as the video recording service.
 * It also handles all necessary permission requests that are required for all application components
 * to run properly.
 *
 * @author snoran
 * @affiliation University of Massachusetts Amherst
 *
 * @see SensorService
 * @see RecordingService
 * @see Activity
 * @see android.Manifest.permission
 */
public class MainActivity extends AppCompatActivity {

    /** Used during debugging to identify logs by class */
    public static final String TAG = MainActivity.class.getName();

    /** video display surface - mustn't be visible **/
    private SurfaceHolder mHolder;

    /** whether the application should record video during data collection **/
    private boolean record_video;

    /** whether video recording should include audio **/
    private boolean record_audio;

    /** Permission request identifier **/
    private static final int PERMISSION_REQUEST = 1;

    /** code to post/handler request for permission */
    public final static int WINDOW_OVERLAY_REQUEST = 2;

    /** Used to display status messages **/
    private TextView tvStatus, tvSensor;

    public static SurfaceView mSurfaceView;

    public static ViewGroup mSurfaceLayout;

    public static int actionBarHeight;

    private Bitmap batteryLevelBitmap;

    /** Used to access user preferences shared across different application components **/
    SharedPreferences preferences;

    /**
     * Messenger service for exchanging messages with the background service
     */
    private Messenger mService = null;
    /**
     * Variable indicating if this activity is connected to the service
     */
    private boolean mIsBound;
    /**
     * Messenger receiving messages from the background service to update UI
     */
    private final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    /**
     * Handler to handle incoming messages
     */
    static class IncomingHandler extends Handler {
        private final WeakReference<MainActivity> mMainActivity;

        IncomingHandler(MainActivity mainActivity) {
            mMainActivity = new WeakReference<>(mainActivity);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE.SENSOR_STARTED:
                {
                    mMainActivity.get().updateStatus("sensor started.");
                    mMainActivity.get().onSensorStarted();
                    break;
                }
                case Constants.MESSAGE.SENSOR_STOPPED:
                {
                    mMainActivity.get().updateStatus("sensor stopped.");
                    break;
                }
                case Constants.MESSAGE.STATUS:
                {
                    mMainActivity.get().updateStatus(msg.getData().getString(Constants.KEY.STATUS));
                    break;
                }
                case Constants.MESSAGE.ACCELEROMETER_READING:
                {
                    mMainActivity.get().updateAccelerometerReading(msg.getData().getDoubleArray(Constants.KEY.ACCELEROMETER_READING));
                    break;
                }
                case Constants.MESSAGE.BATTERY_LEVEL:
                {
                    mMainActivity.get().updateBatteryLevel(msg.getData().getInt(Constants.KEY.BATTERY_LEVEL));
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Connection with the service
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            updateStatus("Attached to the sensor service.");
            mIsBound = true;
            try {
                Message msg = Message.obtain(null, Constants.MESSAGE.REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mIsBound = false;
            mService = null;
            updateStatus("Disconnected from the sensor service.");
        }
    };

    /**
     * Binds the activity to the background service
     */
    void doBindService() {
        bindService(new Intent(this, SensorService.class), mConnection, Context.BIND_AUTO_CREATE);
        updateStatus("Binding to Service...");
    }

    /**
     * Unbind this activity from the background service
     */
    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, Constants.MESSAGE.UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mConnection);
            updateStatus("Unbinding from Service...");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        maximizeVideo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        minimizeVideo();
    }


    @Override
    protected void onDestroy() {
        doUnbindService();
        super.onDestroy();
    }

    /**
     * Check the specified permissions
     * @param permissions list of Strings indicating permissions
     * @return true if ALL permissions are granted, false otherwise
     */
    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @TargetApi(23)
    public void checkDrawOverlayPermission() {
        /** check if we already  have permission to draw over other apps */
        if (record_video && !Settings.canDrawOverlays(getApplicationContext())) {
            /** if not construct intent to request permission */
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            /** request permission via start activity for result */
            startActivityForResult(intent, WINDOW_OVERLAY_REQUEST);
        }else{
            startSensorService();
        }
    }

    @TargetApi(23)
    @Override
    protected void onActivityResult(int requestCode, int resultCode,  Intent data) {
        /** check if received result code
         is equal our requested code for draw permission  */
        if (requestCode == WINDOW_OVERLAY_REQUEST) {
            /** if so check once again if we have permission */
            if (Settings.canDrawOverlays(this)) {
                startSensorService();
            }
        }
    }

    /**
     * Request required permissions, depending on the application settings. Permissions
     * {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE},
     * {@link android.Manifest.permission#BLUETOOTH BLUETOOTH},
     * {@link android.Manifest.permission#BLUETOOTH_ADMIN BLUETOOTH_ADMIN},
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION}
     * are always required, because the data collection from the Bean cannot work without
     * these permissions. If video recording is enabled, then additionally the
     * {@link android.Manifest.permission#CAMERA CAMERA} permission is required. For audio
     * recording, which is disabled by default, the
     * {@link android.Manifest.permission#RECORD_AUDIO RECORD_AUDIO} permission is required.
     */
    private void requestPermissions(){
        List<String> permissionGroup = new ArrayList<>(Arrays.asList(new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }));

        if (record_video) {
            permissionGroup.add(Manifest.permission.CAMERA);
            if (record_audio){
                permissionGroup.add(Manifest.permission.RECORD_AUDIO);
            }
        }

        String[] permissions = permissionGroup.toArray(new String[permissionGroup.size()]);

        if (!hasPermissionsGranted(permissions)) {
            ActivityCompat.requestPermissions(MainActivity.this, permissions, PERMISSION_REQUEST);
            return;
        }
        checkDrawOverlayPermission();
    }

    /**
     * display the status message in the main UI
     * @param status status message
     */
    private void updateStatus(final String status){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText(status);
            }
        });
        Log.d(TAG, status);
    }

    /**
     * display the battery level in the UI
     * @param percentage battery level in the range of [0,100]
     */
    private void updateBatteryLevel(final int percentage){
        if (batteryLevelBitmap == null)
            batteryLevelBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_battery_image_set);
        int nImages = 11;
        int height = batteryLevelBitmap.getHeight();
        int width = batteryLevelBitmap.getWidth();
        int width_per_image = width / nImages;
        int index = (percentage + 5) / (nImages - 1);
        int x = width_per_image * index;
        Bitmap batteryLevelSingleBitmap = Bitmap.createBitmap(batteryLevelBitmap, x, 0, width_per_image, height);

        Resources res = getResources();
        BitmapDrawable icon = new BitmapDrawable(res,batteryLevelSingleBitmap);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(icon);
        getSupportActionBar().setTitle("");
    }

    /**
     * display the accelerometer readings in the main UI
     * @param values length-3 array of xyz accelerometer readings
     */
    private void updateAccelerometerReading(final double[] values){
        final String output = String.format(getString(R.string.initial_sensor_readings), values[0], values[1], values[2]);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvSensor.setText(output);
            }
        });
        Log.d(TAG, output);
    }

    //Callback method called following permissions request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST: {
                //If the request is cancelled, the result array is empty.
                if (grantResults.length == 0) {
                    updateStatus("Permission Request Cancelled.");
                    return;
                }
                for (int i = 0; i < permissions.length; i++){
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED){
                        switch (permissions[i]) {
                            case Manifest.permission.CAMERA:
                                record_video = false;
                                updateStatus("Permission Denied : Continuing with video disabled.");
                                break;
                            case Manifest.permission.RECORD_AUDIO:
                                record_audio = false;
                                updateStatus("Permission Denied : Continuing with audio disabled.");
                                break;
                            default:
                                //required permission not granted, abort
                                updateStatus(permissions[i] + " Permission Denied - cannot continue");
                                return;
                        }
                    }
                }
                checkDrawOverlayPermission();
            }
        }
    }

    /**
     * Loads shared user preferences, e.g. whether video/audio is enabled
     */
    private void loadPreferences(){
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        record_video = preferences.getBoolean(getString(R.string.pref_video_key),
                getResources().getBoolean(R.bool.pref_video_default));
        record_audio = preferences.getBoolean(getString(R.string.pref_audio_key),
                getResources().getBoolean(R.bool.pref_audio_default));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_accel);

        loadPreferences();

        tvStatus = (TextView) findViewById(R.id.status);
        tvStatus.setText(getString(R.string.initial_status));

        tvSensor = (TextView) findViewById(R.id.sensor_readings);
        tvSensor.setText(String.format(getString(R.string.initial_sensor_readings), 0.0, 0.0, 0.0));

        doBindService();

        Button startButton = (Button)findViewById(R.id.start_button);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsBound) {
                    doBindService();
                }
                if (mIsBound) {
                    loadPreferences();
                    requestPermissions();
                }
            }
        });

        Button stopButton = (Button)findViewById(R.id.stop_button);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsBound) {
                    doBindService();
                }
                if (mIsBound) {
                    stopSensorService();
                    stopRecordingService();
                }
            }
        });

        mSurfaceView = (SurfaceView) findViewById(R.id.surface_camera); //new SurfaceView(getApplicationContext());
        // Calculate ActionBar height
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
        {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
        }
//        //mSurfaceLayout = (ViewGroup) findViewById(R.id.camera_layout);
//
//        WindowManager winMan = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
//        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
//                WindowManager.LayoutParams.WRAP_CONTENT,
//                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
//                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
//                PixelFormat.TRANSLUCENT);
//
//        params.x = 0;
//        params.y = 0;
//
//        //mSurfaceLayout.removeView(mSurfaceView);
//        winMan.addView(mSurfaceView, params);
//        mSurfaceView.setZOrderOnTop(true);
//        // Hack to show the video on almost any device (show a 1x1 pixel preview)
//        mSurfaceView.getHolder().setFixedSize(500, 500);
//        mSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
    }

    /**
     * Starts the {@link SensorService} via an {@link Intent}
     */
    private void startSensorService(){

        Intent startServiceIntent = new Intent(MainActivity.this, SensorService.class);

        //identify the intent by the START_SERVICE action, defined in the Constants class
        startServiceIntent.setAction(Constants.ACTION.START_SERVICE);

        //start sensor service
        startService(startServiceIntent);

    }

    /**
     * Stops the {@link SensorService} via an {@link Intent}
     */
    private void stopSensorService(){

        //create an intent for stopping sensor service: We will pass this to the sensor service
        Intent stopServiceIntent = new Intent(MainActivity.this, SensorService.class);

        //identify the intent by the STOP_SERVICE action, defined in the Constants class
        stopServiceIntent.setAction(Constants.ACTION.STOP_SERVICE);

        //send intent to the sensor service
        startService(stopServiceIntent);

    }

    /**
     * Starts the {@link RecordingService} via an {@link Intent}
     */
    private void startRecordingService(){

        Intent startServiceIntent = new Intent(MainActivity.this, RecordingService.class);

        //identify the intent by the START_SERVICE action, defined in the Constants class
        startServiceIntent.setAction(Constants.ACTION.START_SERVICE);

        int[] position = new int[2];
        mSurfaceView.getLocationInWindow(position);
        startServiceIntent.putExtra(Constants.KEY.SURFACE_WIDTH, mSurfaceView.getWidth());
        startServiceIntent.putExtra(Constants.KEY.SURFACE_HEIGHT, mSurfaceView.getHeight());
        startServiceIntent.putExtra(Constants.KEY.SURFACE_X, position[0]);
        startServiceIntent.putExtra(Constants.KEY.SURFACE_Y, position[1]);

        //start sensor service
        startService(startServiceIntent);

    }

    /**
     * Stops the {@link RecordingService} via an {@link Intent}
     */
    private void stopRecordingService(){

        //create an intent for stopping sensor service: We will pass this to the sensor service
        Intent stopServiceIntent = new Intent(MainActivity.this, RecordingService.class);

        //identify the intent by the STOP_SERVICE action, defined in the Constants class
        stopServiceIntent.setAction(Constants.ACTION.STOP_SERVICE);

        //send intent to the sensor service
        startService(stopServiceIntent);

    }

    private void minimizeVideo(){

        //create an intent for stopping sensor service: We will pass this to the sensor service
        Intent minimizeIntent = new Intent(MainActivity.this, RecordingService.class);

        //identify the intent by the STOP_SERVICE action, defined in the Constants class
        minimizeIntent.setAction(Constants.ACTION.MINIMIZE_VIDEO);

        //send intent to the sensor service
        startService(minimizeIntent);

    }

    private void maximizeVideo(){

        //create an intent for stopping sensor service: We will pass this to the sensor service
        Intent maximizeIntent = new Intent(MainActivity.this, RecordingService.class);

        //identify the intent by the STOP_SERVICE action, defined in the Constants class
        maximizeIntent.setAction(Constants.ACTION.MAXIMIZE_VIDEO);

        //send intent to the sensor service
        startService(maximizeIntent);

    }

    /**
     * Called when the {@link SensorService} has started. This should then start the {@link RecordingService}, if enabled.
     */
    private void onSensorStarted(){
        if (record_video) {
            startRecordingService();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_read_accel, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent openSettings = new Intent(MainActivity.this, SettingsActivity.class);
            //myIntent.putExtra("key", value); //Optional parameters
            startActivity(openSettings);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
