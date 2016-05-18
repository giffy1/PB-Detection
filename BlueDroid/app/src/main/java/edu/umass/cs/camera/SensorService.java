package edu.umass.cs.camera;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The sensor service is responsible for handling the connection with the bean
 * and collecting relevant sensor information such as accelerometer, temperature
 * and signal strength (RSSI). It is an ongoing foreground service that will run
 * even when the application is not running. Note, however, that a process of the
 * application will continue to run, until explicitly stopped or killed by the
 * Android operating system. Data of interest is then written to disk.
 *
 * @author Sean Noran
 * @affiliation University of Massachusetts Amherst
 *
 * @see android.app.Service
 * @see <a href="http://developer.android.com/guide/components/services.html#Foreground">
 * Foreground Service</a>
 * @see BeanManager
 * @see FileUtil#writeToFile(String, BufferedWriter)
 * @see Constants
 */
public class SensorService extends Service {

    /** Used during debugging to identify logs by class */
    private static final String TAG = SensorService.class.getName();

    /** Messenger used by clients */
    private final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    /** List of bound clients/activities to this service */
    private ArrayList<Messenger> mClients = new ArrayList<>();

    /** indicates whether the sensor service is running or not */
    private static boolean isRunning = false;

    /**
     * Handler to handle incoming messages
     */
    private static class IncomingHandler extends Handler {
        private final WeakReference<SensorService> mService;

        IncomingHandler(SensorService service) {
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE.REGISTER_CLIENT:
                    mService.get().mClients.add(msg.replyTo);
                    break;
                case Constants.MESSAGE.UNREGISTER_CLIENT:
                    mService.get().mClients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Sends a status message to all clients, removing any inactive clients if necessary.
     * @param status the status message
     */
    private void sendStatusToClients(String status) {
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                // Send message value
                Bundle b = new Bundle();
                b.putString(Constants.KEY.STATUS, status);
                Message msg = Message.obtain(null, Constants.MESSAGE.STATUS);
                msg.setData(b);
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
    }

    /**
     * Sends a xyz accelerometer readings to listening clients, i.e. main UI
     * @param x acceleration along x axis
     * @param y acceleration along y axis
     * @param z acceleration along z axis
     */
    private void sendAccelerometerValuesToClients(double x, double y, double z) {
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                // Send message value
                Bundle b = new Bundle();
                b.putDoubleArray(Constants.KEY.ACCELEROMETER_READING, new double[]{x, y, z});
                Message msg = Message.obtain(null, Constants.MESSAGE.ACCELEROMETER_READING);
                msg.setData(b);
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    protected static boolean isRunning(){
        return isRunning;
    }

    private interface FILE_TAG {
        String ACCELEROMETER = "ACCEL";
        String RSSI = "RSSI";
    }

    private interface WRITER {
        BufferedWriter ACCELEROMETER = FileUtil.getFileWriter(FILE_TAG.ACCELEROMETER);
        BufferedWriter RSSI = FileUtil.getFileWriter(FILE_TAG.RSSI);
    }

    private interface SAMPLING_RATE {
        int ACCELEROMETER = 10;
        int RSSI = 60;
    }

    /** list of available LightBlue Bean sensors **/
    private final List<Bean> beans = new ArrayList<>();

    //Note: onDestroy() is not guaranteed to be called ever
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    //Called when passing in an intent via startService(), i.e. start/stop command
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(Constants.ACTION.START_SERVICE)) {
            registerSensors();
        } else if (intent.getAction().equals(Constants.ACTION.NOTIFY)) {

            isRunning = true;

            // create option to stop the service from the notification
            Intent stopIntent = new Intent(this, SensorService.class);
            stopIntent.setAction(Constants.ACTION.STOP_SERVICE);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, stopIntent, 0);

            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.lightblue_bean);

            // notify the user that the foreground service has started
            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.app_name))
                    .setTicker(getString(R.string.app_name))
                    .setContentText(getString(R.string.msg_service_started))
                    .setSmallIcon(R.drawable.lightblue_bean)
                    .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .setOngoing(true)
                    .setVibrate(new long[]{0, 50, 150, 200})
                    .setPriority(Notification.PRIORITY_MAX)
                    .addAction(android.R.drawable.ic_delete, getString(R.string.stop_service), pendingIntent).build();

            startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);

        } else if (intent.getAction().equals(Constants.ACTION.STOP_SERVICE)) {
            isRunning = false;

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
     * Register available Light Blue Bean sensors over BLE
     */
    private void registerSensors() {
        BeanDiscoveryListener listener = new BeanDiscoveryListener() {
            @Override
            public void onBeanDiscovered(final Bean bean, int rssi) {
                beans.add(bean);
                sendStatusToClients(String.format("Discovered bean %s", bean.getDevice().getAddress()));
                sendStatusToClients(String.format("There are now %d beans.", beans.size()));
                sendStatusToClients(String.format("Bean %s has a signal strength of %s RSSI.", bean.getDevice().getAddress(), String.valueOf(rssi)));

                final BeanListener beanListener = new BeanListener() {
                    @Override
                    public void onConnected() {
                        sendStatusToClients(String.format("Connected to bean %s.", bean.getDevice().getAddress()));
                        bean.readDeviceInfo(new Callback<DeviceInfo>() {
                            @Override
                            public void onResult(DeviceInfo deviceInfo) {
                                sendStatusToClients("Device Information: " + deviceInfo.toString());
                            }
                        });
                        bean.readBatteryLevel(new Callback<BatteryLevel>() {
                            @Override
                            public void onResult(BatteryLevel batteryLevel) {
                                sendStatusToClients(String.format("Battery level for Bean %s: %s", bean.getDevice().getAddress(), batteryLevel.toString()));
                            }
                        });

                        HandlerThread hThread = new HandlerThread("HandlerThread");
                        hThread.start();

                        final Handler handlerAccelerometer = new Handler(hThread.getLooper());
                        final int delayAccelerometer = (int)(1000.0 / SAMPLING_RATE.ACCELEROMETER); // milliseconds
                        Runnable readAccelerometerTask = new Runnable() {
                            @Override
                            public void run() {
                                bean.readAcceleration(new Callback<Acceleration>() {
                                    @Override
                                    public void onResult(Acceleration acceleration) {
                                        long time = System.currentTimeMillis();
                                        double x = acceleration.x();
                                        double y = acceleration.y();
                                        double z = acceleration.z();
                                        sendAccelerometerValuesToClients(x, y, z);
                                        String line = String.format("%d, %f, %f, %f", time, x, y, z);
                                        synchronized (WRITER.ACCELEROMETER) {
                                            FileUtil.writeToFile(line, WRITER.ACCELEROMETER);
                                        }
                                    }
                                });

                                handlerAccelerometer.postDelayed(this, delayAccelerometer);
                            }
                        };
                        handlerAccelerometer.postDelayed(readAccelerometerTask, delayAccelerometer);

                        final Handler handlerRSSI = new Handler(hThread.getLooper());
                        final int delayRSSI = (int)(1000.0 / SAMPLING_RATE.RSSI); // milliseconds
                        Runnable readRSSITask = new Runnable() {
                            @Override
                            public void run() {
                                bean.readRemoteRssi();
                                handlerRSSI.postDelayed(this, delayRSSI);
                            }
                        };
                        handlerAccelerometer.postDelayed(readRSSITask, delayRSSI);

                        //show notification
                        Intent notifyIntent = new Intent(SensorService.this, SensorService.class);
                        //identify the intent by the STOP_SERVICE action, defined in the Constants class
                        notifyIntent.setAction(Constants.ACTION.NOTIFY);
                        //send intent to the sensor service
                        startService(notifyIntent);

                    }

                    @Override
                    public void onConnectionFailed() {
                        sendStatusToClients(String.format("Connection to bean %s failed.", bean.getDevice().getAddress()));
                    }

                    @Override
                    public void onDisconnected() {
                        sendStatusToClients(String.format("Disconnected from bean %s.", bean.getDevice().getAddress()));
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
                        sendStatusToClients(String.format("Bean Error: %s", beanError.toString()));
                    }

                    @Override
                    public void onReadRemoteRssi(int r) {
                        long time = System.currentTimeMillis();
                        String line = String.format("%d, %d", time, r);
                        synchronized (WRITER.RSSI) {
                            FileUtil.writeToFile(line, WRITER.RSSI);
                        }
                        sendStatusToClients(String.format("RSSI data for Bean %s: %s", bean.getDevice().getAddress(), String.valueOf(r)));
                    }
                };

                // Assuming you are in an Activity, use 'this' for the context
                sendStatusToClients("Connecting to bean" + bean.getDevice().getName() + "...");
                bean.connect(SensorService.this, beanListener);
            }

            @Override
            public void onDiscoveryComplete() {
                sendStatusToClients(String.format("Discovery Complete. %d devices found.", beans.size()));
                for (final Bean bean : beans) {
                    //System.out.println(bean.getDevice().getName());   // "Bean"              (example)
                    //System.out.println(bean.getDevice().getAddress());    // "B4:99:4C:1E:BC:75" (example)
                    sendStatusToClients(String.format("Bean Name: %s Bean Address: %s", bean.getDevice().getName(), bean.getDevice().getAddress()));
                }
            }
        };

        if (BeanManager.getInstance().startDiscovery(listener)){
            sendStatusToClients("Listening for Bean...");
        }else{
            sendStatusToClients("Could not find Bean. Make sure Bluetooth is enabled.");
        }
    }

    /**
     * Disconnect from all available LightBlue Bean sensors.
     */
    public void unregisterSensors(){
        for (Bean bean : beans){
            bean.disconnect();
        }
    }
}