package com.soreepeong.darknova;

import android.app.Application;

import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.core.StringTools;
import com.soreepeong.darknova.settings.NewTweet;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;

import org.acra.*;
import org.acra.annotation.*;

import java.util.ArrayList;

/**
 * Created by Soreepeong on 2015-05-31.
 */
@ReportsCrashes(
		formUri = "http://soreepeong.com/darknova/error-report.php",
		mode = ReportingInteractionMode.TOAST,
		resToastText = R.string.crash_toast_text)
public class DarknovaApplication extends Application{
	private static volatile long mRuntimeUniqueIdCounter = 0;

	public static long uniqid(){
		return mRuntimeUniqueIdCounter++;
	}

	@Override
	public void onCreate() {
		super.onCreate();

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

		NewTweet.loadNewTweets(this);
	}
}
