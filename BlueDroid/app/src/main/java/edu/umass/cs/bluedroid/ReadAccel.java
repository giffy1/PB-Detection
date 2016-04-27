package edu.umass.cs.bluedroid;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
import com.punchthrough.bean.sdk.Bean;
import com.punchthrough.bean.sdk.BeanDiscoveryListener;
import com.punchthrough.bean.sdk.BeanListener;
import com.punchthrough.bean.sdk.BeanManager;
import com.punchthrough.bean.sdk.message.Acceleration;
import com.punchthrough.bean.sdk.message.BeanError;
import com.punchthrough.bean.sdk.message.Callback;
import com.punchthrough.bean.sdk.message.DeviceInfo;
import com.punchthrough.bean.sdk.message.ScratchBank;

import java.util.ArrayList;
import java.util.List;

public class ReadAccel extends AppCompatActivity {

    final String BEAN_TAG = "BEAN";
    final String BAND_TAG = "BAND";

    BandInfo[] pairedBands;
    BandClient bandClient;

    TextView feedback;
    Button attach, read_button, write_button;

    Boolean attached;

    BandPendingResult<ConnectionState> pendingResult;

    Activity mActivity = this;

    ArrayList<BandAccelerometerEvent> accelReadings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_accel);

        TextView feedback = (TextView) findViewById(R.id.feedback);

        accelReadings = new ArrayList<BandAccelerometerEvent>();

        attached = false;

        attach = (Button) findViewById(R.id.band_attach);
        attach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attachBand();
                attached = true;
            }
        });

        read_button = (Button) findViewById(R.id.read_button);
        read_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (attached) {
                    //feedback.setText("Reading data..");
                    readData();
                } else {
                    //feedback.setText("Band not attached yet");
                }
            }
        });

        //Light Blue Bean Stuff

        final List<Bean> beans = new ArrayList<>();

        BeanDiscoveryListener listener = new BeanDiscoveryListener() {
            @Override
            public void onBeanDiscovered(Bean bean, int rssi) {
                beans.add(bean);
                Log.v(BEAN_TAG, "Discovered bean!");
                Log.v(BEAN_TAG, "Size: " + beans.size());
            }

            @Override
            public void onDiscoveryComplete() {
                for (Bean bean : beans) {
                    //System.out.println(bean.getDevice().getName());   // "Bean"              (example)
                    //System.out.println(bean.getDevice().getAddress());    // "B4:99:4C:1E:BC:75" (example)
                    Log.v(BEAN_TAG, "Bean Name: " + bean.getDevice().getName() + " Bean Address: " + bean.getDevice().getAddress());
                }

            }
        };

        Log.v(BEAN_TAG, "Scan started: " + BeanManager.getInstance().startDiscovery(listener));

        if (beans.size() > 0) {
            // Assume we have a reference to the 'beans' ArrayList from above.
            final Bean bean = beans.get(0);

            bean.readAcceleration(new Callback<Acceleration>() {
                @Override
                public void onResult(Acceleration acceleration) {
                    Log.v(BEAN_TAG, "Accel: " + acceleration.toString());
                }
            });


            BeanListener beanListener = new BeanListener() {
                @Override
                public void onConnected() {
                    Log.v(BEAN_TAG, "connected to Bean!");
                    bean.readDeviceInfo(new Callback<DeviceInfo>() {
                        @Override
                        public void onResult(DeviceInfo deviceInfo) {
                            Log.v(BEAN_TAG, "Device Information: " + deviceInfo.toString());
                        }
                    });
                }

                @Override
                public void onConnectionFailed() {

                }

                @Override
                public void onDisconnected() {

                }

                @Override
                public void onSerialMessageReceived(byte[] bytes) {

                }

                @Override
                public void onScratchValueChanged(ScratchBank scratchBank, byte[] bytes) {

                }

                @Override
                public void onError(BeanError beanError) {

                }

                @Override
                public void onReadRemoteRssi(int r) {

                }
            };

            // Assuming you are in an Activity, use 'this' for the context
            bean.connect(mActivity, beanListener);

        }
    }

    public boolean attachBand() {
        pairedBands = BandClientManager.getInstance().getPairedBands();
        bandClient = BandClientManager.getInstance().create(this, pairedBands[0]);

        //feedback.setText("Band Attached");

        //Need to place this in an Async Task
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
        if(bandClient.getSensorManager().getCurrentHeartRateConsent() !=
                UserConsent.GRANTED) {
            // user hasn’t consented, request consent
            // the calling class is an Activity and implements
            // HeartRateConsentListener
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
            } catch(InterruptedException ex) {
                // handle InterruptedException
            } catch(BandException ex) {
                // handle BandException
            }
            return null;
        }

        protected void onProgressUpdate() {

        }

        protected void onPostExecute() {

        }
    }

}
