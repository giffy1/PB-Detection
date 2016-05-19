package edu.umass.cs.camera;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandPendingResult;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.SampleRate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Main UI and entry point for the data collection application. The UI is responsible for handling
 * the user interactions with the available services, such as starting the sensor service connection
 * with available Wearables and LightBlue Bean sensors. It also handles all necessary permission
 * requests that are required for the application services to run properly.
 *
 * @author snoran
 * @affiliation University of Massachusetts Amherst
 *
 * @see SensorService
 * @see Activity
 * @see android.Manifest.permission
 */
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    /** Used during debugging to identify logs by class */
    public static final String TAG = MainActivity.class.getName();

    /** recording object for capturing videos **/
    private MediaRecorder recorder;

    /** video display surface - mustn't be visible **/
    private SurfaceHolder holder;

    /** whether currently recording video **/
    private boolean recording = false;

    /** whether the application should record video during data collection **/
    private boolean record_video = false;

    /** Permission identifier for the set of required permissions **/
    private static final int VIDEO_BLE_PERMISSION_REQUEST = 1;

    /** All required permissions necessary for the application services to run properly **/
    private static final String[] VIDEO_BLE_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private final String BAND_TAG = "BAND";

    private BandClient bandClient;

    /** Used to display status messages **/
    private TextView tvStatus, tvSensor;

    private Button attach;

    private Boolean attached;

    private BandPendingResult<ConnectionState> pendingResult;

    private ArrayList<BandAccelerometerEvent> accelReadings;

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
     * Binds this activity to the service if the service is already running
     */
    private void bindToServiceIfIsRunning() {
        //If the service is running when the activity starts, we want to automatically bind to it.
        if (SensorService.isRunning()) {
            doBindService();//
            updateStatus("Request to bind service");
        }
    }

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
    }

    @Override
    protected void onPause() {
        super.onPause();
    }


    @Override
    protected void onDestroy() {
        doUnbindService();
        super.onDestroy();
    }

    /**
     * Check the specified permissions
     * @param permissions list of Strings indicating permissinos
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
            case VIDEO_BLE_PERMISSION_REQUEST: {
                //If the request is cancelled, the result array is empty.
                if (grantResults.length == 0) {
                    updateStatus("Permission Request Cancelled.");
                    return;
                }
                for (int i = 0; i < permissions.length; i++){
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED){
                        updateStatus("Permission Denied for " + permissions[i]);
                        return;
                    }
                }
                // all permissions granted:
                updateStatus("Permission Granted.");
                startSensorService();
            }
        }
    }

    private void loadPreferences(){
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        record_video = preferences.getBoolean(Constants.PREFERENCES.AVAILABLE_SENSORS.VIDEO.KEY,
                Constants.PREFERENCES.AVAILABLE_SENSORS.VIDEO.DEFAULT);
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

        bindToServiceIfIsRunning();

        Button startButton = (Button)findViewById(R.id.start_button);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsBound) {
                    doBindService();
                }
                if (mIsBound) {
                    if (!hasPermissionsGranted(VIDEO_BLE_PERMISSIONS) || !hasPermissionsGranted(VIDEO_BLE_PERMISSIONS)) {
                        ActivityCompat.requestPermissions(MainActivity.this, VIDEO_BLE_PERMISSIONS, VIDEO_BLE_PERMISSION_REQUEST);
                        return;
                    }
                    startSensorService();
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
                    //create an intent for stopping sensor service: We will pass this to the sensor service
                    Intent stopServiceIntent = new Intent(MainActivity.this, SensorService.class);

                    //identify the intent by the STOP_SERVICE action, defined in the Constants class
                    stopServiceIntent.setAction(Constants.ACTION.STOP_SERVICE);

                    //send intent to the sensor service
                    startService(stopServiceIntent);

                    stopVideoService();
                }
            }
        });

        recorder = new MediaRecorder();
        initRecorder();

        SurfaceView cameraView = (SurfaceView) findViewById(R.id.surface_camera);
        holder = cameraView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); //not needed since API 11
    }

    /**
     * Starts the {@link SensorService} via an {@link Intent}
     */
    private void startSensorService(){

        if(!mIsBound) {
            doBindService();
        }
        if (mIsBound) {
            Intent startServiceIntent = new Intent(MainActivity.this, SensorService.class);

            //identify the intent by the START_SERVICE action, defined in the Constants class
            startServiceIntent.setAction(Constants.ACTION.START_SERVICE);

            //start sensor service
            startService(startServiceIntent);
        }
    }

    private void onSensorStarted(){
        if (record_video) {
            recording = true;
            recorder.start();
        }
    }

    /**
     * Stops video recording and re-prepares it for future recordings
     */
    private void stopVideoService(){
        if (recording) {
            recorder.stop();
            recording = false;
            //Toast.makeText(getApplicationContext(), "Recording stopped", Toast.LENGTH_LONG).show();

            // Let's initRecorder so we can record again
            //initRecorder();
            //prepareRecorder();
        }
    }

    /**
     * initialize the recorder and set the appropriate recording parameters
     */
    private void initRecorder() {
        //recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //recorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        String directory = preferences.getString(Constants.PREFERENCES.SAVE_DIRECTORY.KEY,
                Constants.PREFERENCES.SAVE_DIRECTORY.DEFAULT);
        File f = new File(directory);
        if(!f.exists())
            if (!f.mkdir()){
                Log.w(TAG, "Failed to create directory! It may already exist");
            }
        recorder.setOutputFile(new File(directory, "VIDEO.mp4").getAbsolutePath());
        recorder.setVideoEncodingBitRate(10000000);
        recorder.setVideoFrameRate(30);
        //recorder.setMaxDuration(50000); // 50 seconds
        //recorder.setMaxFileSize(5000000); // Approximately 5 megabytes
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        //recorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        prepareRecorder();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (recording) {
            recorder.stop();
            recording = false;
        }
        recorder.release();
        finish();
    }

    private void prepareRecorder() {
        recorder.setPreviewDisplay(holder.getSurface());

        try {
            recorder.prepare();
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
            finish();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Toast.makeText(getApplicationContext(), requestCode + ", " + resultCode, Toast.LENGTH_LONG).show();
    }

    public boolean attachBand() {
        BandInfo[] pairedBands = BandClientManager.getInstance().getPairedBands();
        bandClient = BandClientManager.getInstance().create(this, pairedBands[0]);

        //TODO: Need to place this in an Async Task
        new ConnectBandTask().execute();
        return true;
    }

    public void readData() {
        final BandAccelerometerEventListener accelListener = new BandAccelerometerEventListener() {
            @Override
            public void onBandAccelerometerChanged(BandAccelerometerEvent bandAccelerometerEvent) {
                accelReadings.add(bandAccelerometerEvent);
                Log.v(BAND_TAG, "X: " + bandAccelerometerEvent.getAccelerationX()
                            + ", Y: " + bandAccelerometerEvent.getAccelerationY()
                            + ", Z: " + bandAccelerometerEvent.getAccelerationZ());
            }
        };


        Log.d(BAND_TAG, "Checking consent");

//        try {
        // register the listener
        if(bandClient.getSensorManager().getCurrentHeartRateConsent() != UserConsent.GRANTED) {
            // user hasn’t consented, request consent
            // the calling class is an Activity and implements HeartRateConsentListener
            Log.d(BAND_TAG, "Not consented");
            bandClient.getSensorManager().requestHeartRateConsent(this, new HeartRateConsentListener() {
                @Override
                public void userAccepted(boolean b) {
                    registerAccelListener(accelListener);
                }
            });
        }
        else {
            Log.d(BAND_TAG, "Consented");
            registerAccelListener(accelListener);
        }


    }

    public void registerAccelListener(BandAccelerometerEventListener listener)  {

        try {
            bandClient.getSensorManager().registerAccelerometerEventListener(listener, SampleRate.MS32);
        }
        catch (BandException e) {
            Log.d(BAND_TAG, "Heart rate band exception: " + e);
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ConnectBandTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... nothing) {
            pendingResult = bandClient.connect();
            try {
                ConnectionState state = pendingResult.await();
                if(state == ConnectionState.CONNECTED) {
                    // do work on success
                    Log.d(BAND_TAG, "Success");
                } else {
                    // do work on failure
                    Log.d(BAND_TAG, "Unsuccessful");
                }
            } catch(InterruptedException | BandException e) {
                // handle InterruptedException
            }
            return null;
        }
    }

}
