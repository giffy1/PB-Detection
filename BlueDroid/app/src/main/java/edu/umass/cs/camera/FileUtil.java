package edu.umass.cs.camera;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This class handles file input/output operations, such as saving the accelerometer/gyroscope
 * data and labels, opening/closing file readers/writers, and deleting the data storage location.
 */
class FileUtil {

    /** tag used for debugging purposes */
    private static final String TAG = FileUtil.class.getName();

    /** CSV extension */
    private static final String CSV_EXTENSION = ".csv";

    /**
     * Returns a file writer for a device
     * @param filename file name (without extension!)
     * @return the file writer for the particular filename
     */
    public static BufferedWriter getFileWriter(String filename, File directory){
        String fullFileName = filename + String.valueOf(System.currentTimeMillis()) + CSV_EXTENSION;

        BufferedWriter out = null;
        try{
            out = new BufferedWriter(new FileWriter(new File(directory,fullFileName)));
        }catch(IOException e){
            e.printStackTrace();
        }
        return out;
    }

    /**
     * Write the log to the specified file writer
     * @param s log to write
     * @param out file writer
     */
    public static void writeToFile(String s, final BufferedWriter out) {
        try{
            out.write(s+"\n");
        } catch(IOException e){
            e.printStackTrace();
        }
    }

    /**
     * Close and flush the given log writer. Flushing ensures that the data in the buffer is first save to the file
     * @param out file writer
     */
    public static void closeWriter(final BufferedWriter out) {
        try{
            out.flush();
            out.close();
        } catch(IOException | NullPointerException e){
            e.printStackTrace();
        }
    }

    /**
     * Deletes all the data from the given directory (be careful!!)
     * @return true if successfully deleted
     */
    public static boolean deleteData(File directory){
        boolean deleted = false;
        if(directory!=null){
            File files[] = directory.listFiles();
            if(files!=null){
                for(File file : files) {
                    if (!file.delete())
                        Log.d(TAG, "Deleting file failed: " + file.getName());
                }
            }
            deleted = directory.delete();
        }
        return deleted;
    }
}
