package com.soreepeong.darknova;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.widget.Toast;

import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

/**
 * Global constant variables initialization
 *
 * @author Soreepeong
 */
@ReportsCrashes(
		formUri = "http://soreepeong.com/darknova/error-report.php",
		mode = ReportingInteractionMode.TOAST,
		resToastText = R.string.crash_toast_text)
public class DarknovaApplication extends Application implements Handler.Callback {
	public static final boolean USE_DEBUG_FEATURES = false;

	private static final int MESSAGE_SHOW_TOAST = 1;
	public static Context mContext;
	private static volatile long mRuntimeUniqueIdCounter = 0;
	private static Handler mHandler;

	public static long uniqid(){
		return mRuntimeUniqueIdCounter++;
	}

	public static void showToast(@NonNull String msg) {
		mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_SHOW_TOAST, msg));
	}

	public static void showToast(@StringRes int resId) {
		mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_SHOW_TOAST, resId, 0));
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mContext = this;
		ACRA.init(this);
		mHandler = new Handler(this);
		StringTools.ARRAY_RELATIVE_TIME_STRINGS = getString(R.string.times).split("/");
		StringTools.ARRAY_RELATIVE_DURATION_STRINGS = getString(R.string.durations).split("/");
		StringTools.ARRAY_FILE_SIZES = getString(R.string.filesizes).split("/");
		StringTools.loadHanjaArray(getResources());

		// For preparing image cache earlier
		ImageCache.getCache(this, null);

		// BEFORE TwitterEngine: addOnUserlistChangedListener from AlwaysAvailableUsers on preparation
		Tweeter.initializeAlwaysAvailableUsers(this);

		// No dependency
		TwitterEngine.prepare(this, "TwitterEngine");

		// AFTER TwitterEngine
		Page.initialize(this, "Page");
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MESSAGE_SHOW_TOAST: {
				if (msg.obj != null)
					Toast.makeText(this, msg.obj.toString(), Toast.LENGTH_LONG).show();
				else
					Toast.makeText(this, msg.arg1, Toast.LENGTH_LONG).show();
				break;
			}
		}
		return false;
	}
}
