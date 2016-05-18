package edu.umass.cs.camera;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.punchthrough.bean.sdk.Bean;
import com.punchthrough.bean.sdk.BeanDiscoveryListener;
import com.punchthrough.bean.sdk.BeanListener;
import com.punchthrough.bean.sdk.BeanManager;
import com.punchthrough.bean.sdk.message.Acceleration;
import com.punchthrough.bean.sdk.message.BatteryLevel;
import com.punchthrough.bean.sdk.message.BeanError;
import com.punchthrough.bean.sdk.message.Callback;
import com.punchthrough.bean.sdk.message.DeviceInfo;
import com.punchthrough.bean.sdk.message.ScratchBank;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * The sensor service is responsible for collecting the accelerometer data on
 * the phone. It is an ongoing foreground service that will run even when your
 * application is not running. Note, however, that a process of your application
 * will be running! The sensor service will receive sensor events in the
 * onSensorChanged() method defined in the SensorEventListener interface. There
 * it should write the data to storage.
 *
 * @author snoran
 *
 * @see Service
 * @see <a href="http://developer.android.com/guide/components/services.html#Foreground">
 * Foreground Service</a>
 * @see SensorEventListener#onSensorChanged(SensorEvent)
 * @see SensorEvent
 * @see FileUtil#writeToFile(String, BufferedWriter)
 * @see Constants
 */
public class RecordingService extends Service {

    /** Used during debugging to identify logs by class */
    private static final String TAG = RecordingService.class.getName();

    final String BEAN_TAG = "BEAN";

    private interface FILE_TAG {
        String ACCELEROMETER = "ACCEL";
        String RSSI = "RSSI";
    }

    private interface WRITER {
        BufferedWriter ACCELEROMETER = FileUtil.getFileWriter(FILE_TAG.ACCELEROMETER);
        BufferedWriter RSSI = FileUtil.getFileWriter(FILE_TAG.RSSI);
    }

    private interface SAMPLING_RATE {
        int ACCELEROMETER = 60;
        int RSSI = 60;
    }

    private final List<Bean> beans = new ArrayList<>();

    //Note: onDestroy() is not guaranteed to be called ever!!!
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    //Called when the service is started, but also when passing in an intent via startService()
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(Constants.ACTION.START_SERVICE)){
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_action_info); //TODO: Change icon

            //notify the user that the application has started - the user can also record labels using the notification
            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.app_name))
                    .setTicker(getString(R.string.app_name))
                    .setContentText(Constants.STRINGS.NOTIFICATION_MESSAGE)
                    .setSmallIcon(R.drawable.ic_action_info)
                    .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .setOngoing(true)
                    .setVibrate(new long[]{0, 50, 100, 50, 100, 50, 100, 400, 100, 300, 100, 350, 50, 200, 100, 100, 50, 600})
                    //brownie points if you know the song in the vibration pattern!
                    .setPriority(Notification.PRIORITY_MAX).build();

            //ID is arbitrary, it is stored in a global constant
            startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
            registerSensors();
        } else if (intent.getAction().equals(Constants.ACTION.STOP_SERVICE)) {
            //unregister the accelerometer sensor
            unregisterSensors();

            //close and flush the file writer. Flush ensures that the data is written to the file
            FileUtil.closeWriter(WRITER.ACCELEROMETER);
            FileUtil.closeWriter(WRITER.RSSI);

            //remove the service from the foreground
            stopForeground(true);

            //end the service
            stopSelf();
        }

        return START_STICKY;
    }

    /**
     * TODO: For communicating with UI we need Handler
     */
    private void registerSensors() {
        //Light Blue Bean Stuff

        BeanDiscoveryListener listener = new BeanDiscoveryListener() {
            @Override
            public void onBeanDiscovered(final Bean bean, int rssi) {
                Log.d(BEAN_TAG, String.format("Discovered bean %s", bean.getDevice().getAddress()));
                beans.add(bean);
                Log.d(BEAN_TAG, String.format("There are %d beans.", beans.size()));
                Log.d(BEAN_TAG, String.format("Bean %s has a signal strength of %s RSSI.", bean.getDevice().getAddress(), String.valueOf(rssi)));

                final BeanListener beanListener = new BeanListener() {
                    @Override
                    public void onConnected() {
                        Log.d(BEAN_TAG, String.format("Connected to bean %s.", bean.getDevice().getAddress()));
                        bean.readDeviceInfo(new Callback<DeviceInfo>() {
                            @Override
                            public void onResult(DeviceInfo deviceInfo) {
                                Log.d(BEAN_TAG, "Device Information: " + deviceInfo.toString());
                            }
                        });
                        bean.readBatteryLevel(new Callback<BatteryLevel>() {
                            @Override
                            public void onResult(BatteryLevel batteryLevel) {
                                Log.d(BEAN_TAG, String.format("Battery level for Bean %s: %s", bean.getDevice().getAddress(), batteryLevel.toString()));
                            }
                        });

                        final Handler h = new Handler();
                        final int delay = (int)(1000.0 / SAMPLING_RATE.ACCELEROMETER); // milliseconds

                        h.postDelayed(new Runnable() {
                            public void run() {
                                bean.readAcceleration(new Callback<Acceleration>() {
                                    @Override
                                    public void onResult(Acceleration acceleration) {
                                        long time = System.currentTimeMillis();
                                        double x = acceleration.x();
                                        double y = acceleration.y();
                                        double z = acceleration.z();
                                        String line = String.format("%d, %f, %f, %f", time, x, y, z);
                                        synchronized (WRITER.ACCELEROMETER) {
                                            FileUtil.writeToFile(line, WRITER.ACCELEROMETER);
                                        }
                                        Log.d(BEAN_TAG, String.format("Accel data for Bean %s: %s", bean.getDevice().getAddress(), acceleration.toString()));
                                    }
                                });

                                h.postDelayed(this, delay);
                            }
                        }, delay);

                        final Handler hRRSI = new Handler();
                        final int delayRRSI = (int)(1000.0 / SAMPLING_RATE.RSSI); // milliseconds

                        hRRSI.postDelayed(new Runnable() {
                            public void run() {
                                bean.readRemoteRssi();
                                hRRSI.postDelayed(this, delayRRSI);
                            }
                        }, delayRRSI);

                    }

                    @Override
                    public void onConnectionFailed() {
                        Log.d(BEAN_TAG, String.format("Connection to bean %s failed.", bean.getDevice().getAddress()));
                    }

                    @Override
                    public void onDisconnected() {
                        Log.d(BEAN_TAG, String.format("Disconnected from bean %s.", bean.getDevice().getAddress()));
                        beans.remove(bean);
                    }

                    @Override
                    public void onSerialMessageReceived(byte[] bytes) {

                    }

                    @Override
                    public void onScratchValueChanged(ScratchBank scratchBank, byte[] bytes) {

                    }

                    @Override
                    public void onError(BeanError beanError) {
                        Log.d(BEAN_TAG, String.format("Bean Error: %s", beanError.toString()));
                    }

                    @Override
                    public void onReadRemoteRssi(int r) {
                        long time = System.currentTimeMillis();
                        String line = String.format("%d, %d", time, r);
                        synchronized (WRITER.RSSI) {
                            FileUtil.writeToFile(line, WRITER.RSSI);
                        }
                        Log.d(BEAN_TAG, String.format("RSSI data for Bean %s: %s", bean.getDevice().getAddress(), String.valueOf(r)));
                    }
                };

                // Assuming you are in an Activity, use 'this' for the context
                Log.d(BEAN_TAG, "Connecting to bean" + bean.getDevice().getName() + "...");
                bean.connect(RecordingService.this, beanListener);
            }

            @Override
            public void onDiscoveryComplete() {
                Log.d(BEAN_TAG, String.format("Discovery Complete. %d devices found.", beans.size()));
                for (final Bean bean : beans) {
                    //System.out.println(bean.getDevice().getName());   // "Bean"              (example)
                    //System.out.println(bean.getDevice().getAddress());    // "B4:99:4C:1E:BC:75" (example)
                    Log.d(BEAN_TAG, String.format("Bean Name: %s Bean Address: %s", bean.getDevice().getName(), bean.getDevice().getAddress()));

                }
            }
        };

        if (BeanManager.getInstance().startDiscovery(listener)){
            Log.d(BEAN_TAG, "Listening for Bean...");
        }else{
            Log.d(BEAN_TAG, "Could not find Bean. Make sure Bluetooth is enabled.");
        }
    }

    public void unregisterSensors(){
        for (Bean bean : beans){
            bean.disconnect();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
