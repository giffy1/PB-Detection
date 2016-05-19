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
        String START_SERVICE = "edu.umass.cs.my-activities-toolkit.action.start-service";
        String NOTIFY = "edu.umass.cs.my-activities-toolkit.action.notify";
        String STOP_SERVICE = "edu.umass.cs.my-activities-toolkit.action.stop-service";
    }

    public interface NOTIFICATION_ID {
        /** Identifies the service to ensure that we have one single instance in the foreground */
        int FOREGROUND_SERVICE = 101;
    }

    //TODO: Should be in values.xml
    /** String constants such as notifications, prompts and titles that are displayed to the user */
    public interface STRINGS {
        String START_MESSAGE = "Accelerometer Started";
        String STOP_MESSAGE = "Accelerometer Stopped:\nData in Downloads/motion-data/ACCEL.csv";
        String TITLE = "My Activities Toolkit";
        String NOTIFICATION_MESSAGE = "Collecting sensor data...";
        String DATA_SUCCESSFULLY_DELETED = "Data successfully deleted.";
        String DELETE_DATA_CONFIRMATION_PROMPT = "Are you sure you want to delete the gestures data?";
        String DELETE_DATA_POSITIVE_MSG = "Yes, Delete Data";
        String DELETE_DATA_NEGATIVE_MSG = "No, I mis-clicked";
    }

    public interface PREFERENCES {
        interface SAMPLING_RATE {
            interface ACCELEROMETER {
                String KEY = "accelerometer-sampling-rate";
                int DEFAULT = 60;
            }
            interface RSSI {
                String KEY = "rssi-sampling-rate";
                int DEFAULT = 60;
            }
        }
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
            String KEY = "save-directory";
            String DEFAULT_DIRECTORY_NAME = "bluedroid";
            String DEFAULT = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DEFAULT_DIRECTORY_NAME).getAbsolutePath();
        }
    }

    /** Keys to identify key-value data sent to/from the sensor service */
    public interface KEYS {
        String ACTIVITIES = "activity";
    }

    /** Error/warning messages displayed to the user */
    public interface ERROR_MESSAGES {
        String ERROR_NO_ACCELEROMETER = "ERROR: No accelerometer available...";
        String ERROR_NO_SENSOR_MANAGER = "ERROR: Could not retrieve sensor manager...";
        String WARNING_SENSOR_NOT_SUPPORTED = "WARNING: Sensor not supported!";
        String WARNING_DATA_NOT_DELETED = "WARNING: Directory may not have been deleted!";
    }

    public interface KEY {
        String STATUS = "edu.umass.cs.mygestures.key.status";
        String ACCELEROMETER_READING = "edu.umass.cs.mygestures.key.accelerometer-reading";
    }

    public interface MESSAGE {
        int REGISTER_CLIENT = 0;
        int UNREGISTER_CLIENT = 1;
        int SENSOR_STARTED = 2;
        int SENSOR_STOPPED = 3;
        int STATUS = 4;
        int ACCELEROMETER_READING = 5;
    }

    /** Timestamp-relevant constants */
    public interface TIMESTAMPS {
        long NANOSECONDS_PER_MILLISECOND = 1000000;
    }
}
