package com.soreepeong.darknova.tools;
import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;

import android.os.Environment;
import android.util.Log;

import com.soreepeong.darknova.BuildConfig;

// https://stackoverflow.com/questions/6131769/is-there-a-way-to-have-an-android-process-produce-a-heap-dump-on-an-outofmemorye

public class OutOfMemoryDumper implements Thread.UncaughtExceptionHandler {

	private static final String TAG = "OutOfMemoryDumper";
	private static final String FILE_PREFIX = "OOM-";
	private static final OutOfMemoryDumper instance = new OutOfMemoryDumper();

	private UncaughtExceptionHandler oldHandler;

	/**
	 * Call this method to initialize the OutOfMemoryDumper when your
	 * application is first launched.
	 */
	public static void initialize() {

		// Only works in DEBUG builds
		if (BuildConfig.DEBUG) {
			instance.setup();
		}
	}

	/**
	 * Keep the constructor private to ensure we only have one instance
	 */
	private OutOfMemoryDumper() {
	}

	private void setup() {

		// Checking if the dumper isn't already the default handler
		if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof OutOfMemoryDumper)) {

			// Keep the old default handler as we are going to use it later
			oldHandler = Thread.getDefaultUncaughtExceptionHandler();

			// Redirect uncaught exceptions to this class
			Thread.setDefaultUncaughtExceptionHandler(this);
		}
		Log.v(TAG, "OutOfMemoryDumper is ready");
	}

	@Override
	public void uncaughtException(Thread thread, Throwable ex) {

		Log.e(TAG, "Uncaught exception: " + ex);
		Log.e(TAG, "Caused by: " + ex.getCause());

		// Checking if the exception or the original cause for the exception is
		// an out of memory error
		if (ex.getClass().equals(OutOfMemoryError.class)
				|| (ex.getCause() != null && ex.getCause().getClass()
				.equals(OutOfMemoryError.class))) {

			// Checking if the external storage is mounted and available
			if (isExternalStorageWritable()) {
				try {

					// Building the path to the new file
					File f = Environment
							.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

					long time = System.currentTimeMillis();

					String dumpPath = f.getAbsolutePath() + "/" + FILE_PREFIX
							+ time + ".hprof";

					Log.i(TAG, "Dumping hprof data to: " + dumpPath);

					android.os.Debug.dumpHprofData(dumpPath);

				} catch (IOException ioException) {
					Log.e(TAG,"Failed to dump hprof data. " + ioException.toString());
					ioException.printStackTrace();
				}
			}
		}

		// Invoking the original default exception handler (if exists)
		if (oldHandler != null) {
			Log.v(TAG, "Invoking the original uncaught exception handler");
			oldHandler.uncaughtException(thread, ex);
		}
	}

	/**
	 * Checks if external storage is available for read and write
	 *
	 * @return true if the external storage is available
	 */
	private boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		Log.w(TAG,"The external storage isn't available. hprof data won't be dumped! (state=" + state + ")");
		return false;
	}
}