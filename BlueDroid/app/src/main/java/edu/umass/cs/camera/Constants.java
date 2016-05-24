package edu.umass.cs.camera;

import android.os.Environment;

import java.io.File;

/**
 * The Constants class stores various constants that are used across various classes, including
 * identifiers for intent actions used when the main UI communicates with the sensor service; the
 * list of activity labels; the error and warning messages displayed to the user; and so on.
 *
 * @author snoran
 *
 */
class Constants {

    /** Intent actions used to communicate between the main UI and the sensor service
     * @see SensorService
     * @see MainActivity
     * @see android.content.Intent */
    public interface ACTION {
        String START_SERVICE = "edu.umass.cs.bluedroid.action.start-service";
        String NOTIFY = "edu.umass.cs.bluedroid.action.notify";
        String STOP_SERVICE = "edu.umass.cs.bluedroid.action.stop-service";
        String MINIMIZE_VIDEO = "edu.umass.cs.bluedroid.action.minimize-video";
        String MAXIMIZE_VIDEO = "edu.umass.cs.bluedroid.action.maximize-video";
    }

    public interface NOTIFICATION_ID {
        /** Identifies the service to ensure that we have one single instance in the foreground */
        int SENSOR_SERVICE = 101;
        int VIDEO_SERVICE = 102;
    }

    public interface PREFERENCES {
        interface FILE_NAME {
            interface ACCELEROMETER {
                String KEY = "accelerometer-file-name";
                String DEFAULT = "accelerometer";
            }

            interface RSSI {
                String KEY = "rssi";
                String DEFAULT = "";
            }
        }

        interface SAVE_DIRECTORY {
            String DEFAULT_DIRECTORY_NAME = "bluedroid";
            String DEFAULT = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DEFAULT_DIRECTORY_NAME).getAbsolutePath();
        }
    }

    public interface KEY {
        String STATUS = "edu.umass.cs.bluedroid.key.status";
        String ACCELEROMETER_READING = "edu.umass.cs.bluedroid.key.accelerometer-reading";
        String BATTERY_LEVEL = "edu.umass.cs.bluedroid.key.battery-level";
        String SURFACE_WIDTH = "edu.umass.cs.bluedroid.key.surface-width";
        String SURFACE_HEIGHT = "edu.umass.cs.bluedroid.key.surface-height";
        String SURFACE_X = "edu.umass.cs.bluedroid.key.surface-x";
        String SURFACE_Y = "edu.umass.cs.bluedroid.key.surface-y";
    }

    public interface MESSAGE {
        int REGISTER_CLIENT = 0;
        int UNREGISTER_CLIENT = 1;
        int SENSOR_STARTED = 2;
        int SENSOR_STOPPED = 3;
        int STATUS = 4;
        int ACCELEROMETER_READING = 5;
        int BATTERY_LEVEL = 6;
    }
}
