package com.soreepeong.darknova;

import android.app.Application;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.widget.Toast;

import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.TemplateTweet;
import com.soreepeong.darknova.settings.TemplateTweetAttachment;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import java.io.File;
import java.util.ArrayList;

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
	private static final int MESSAGE_SHOW_TOAST = 1;
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
		mHandler = new Handler(this);

		ACRA.init(this);

		StringTools.ARRAY_RELATIVE_TIME_STRINGS = getString(R.string.times).split("/");
		StringTools.ARRAY_RELATIVE_DURATION_STRINGS = getString(R.string.durations).split("/");
		StringTools.ARRAY_FILE_SIZES = getString(R.string.filesizes).split("/");
		StringTools.initHanjaArray(getResources());

		ImageCache.getCache(this, null);
		Tweeter.initAlwaysAvailableUsers(this);

		ArrayList<TwitterEngine> users = TwitterEngine.getTwitterEngines(this);
		if (!users.isEmpty()) {
				// TODO Load saved pages
				for (TwitterEngine user : users) {
					Page.Builder p;
					p = new Page.Builder("Home", R.drawable.ic_launcher);
					p.e().add(new Page.Element(user, Page.Element.FUNCTION_HOME_TIMELINE, 0, null));
					Page.addPage(p.build());
					p = new Page.Builder("Mentions", R.drawable.ic_mention);
					p.e().add(new Page.Element(user, Page.Element.FUNCTION_MENTIONS, 0, null));
					Page.addPage(p.build());
				}
				Page.mSavedPageLength = Page.pages.size();
		}
		TemplateTweet.initialize(this);
		TemplateTweetAttachment.initialize(new File(getFilesDir(), "attachment"));
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
