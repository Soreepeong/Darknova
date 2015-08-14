package com.soreepeong.darknova.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.PageElement;
import com.soreepeong.darknova.settings.TemplateTweet;
import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.twitter.TwitterEngine.OnUserlistChangedListener;
import com.soreepeong.darknova.ui.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service: Stream, Notification, new tweets
 *
 * @author Soreepeong
 */
public class DarknovaService extends Service implements TwitterEngine.TwitterStreamCallback, OnUserlistChangedListener, Handler.Callback, ImageCache.OnImageCacheReadyListener, ImageCache.OnImageAvailableListener {

	public static final int MESSAGE_STREAM_START = 101;
	public static final int MESSAGE_STREAM_STOP = 102;
	public static final int MESSAGE_STREAM_QUIT_REGISTER = 103;
	public static final int MESSAGE_STREAM_QUIT_CANCEL = 104;
	public static final int MESSAGE_STREAM_SET_CALLBACK = 105;
	public static final int MESSAGE_BREAK_CONNECTION = 106;

	public static final int MESSAGE_STREAM_CALLBACK_NEW_TWEET = 1001;
	public static final int MESSAGE_STREAM_CALLBACK_START = 1002;
	public static final int MESSAGE_STREAM_CALLBACK_CONNECTED = 1003;
	public static final int MESSAGE_STREAM_CALLBACK_ERROR = 1004;
	public static final int MESSAGE_STREAM_CALLBACK_TWEET_EVENT = 1005;
	public static final int MESSAGE_STREAM_CALLBACK_USER_EVENT = 1006;
	public static final int MESSAGE_STREAM_CALLBACK_STOP = 1007;
	public static final int MESSAGE_STREAM_CALLBACK_USE_STREAM_ON = 1101;
	public static final int MESSAGE_STREAM_CALLBACK_USE_STREAM_OFF = 1102;
	public static final int MESSAGE_STREAM_CALLBACK_PREPARED = 1103;

	public static final int MESSAGE_CLEAR_NOTIFICATION = 1;

	public static final int MESSAGE_UPDATE_NOTIFICATION = 10000;
	public static final int MESSAGE_ACTUAL_STREAM_QUIT = 10001;
	public static final int MESSAGE_ACTUAL_STREAM_BREAK = 10002;
	public static final int MESSAGE_USER_REMOVED_STREAM_STOP = 10003;
	public static final int MESSAGE_USER_REMOVED_STREAM_STOP_WAIT_DURATION = 1000;
	public static final String NOTIFICATION_LIST_FILE = "notification-list";
	private static final int DELAY_QUIT_TIME = 5 * 60000; // 5 min.
	private final Handler mHandler = new Handler(this);
	private final Messenger mMessenger = new Messenger(mHandler);
	private final ArrayList<TwitterEngine> mActiveStreams = new ArrayList<>();
	private final ArrayList<TwitterEngine> mRemovalPendingStreamingUsers = new ArrayList<>();
	private final ArrayList<ActivityNotification> mActivities = new ArrayList<>();
	private final Object mCallbackModifyLock = new Object();
	private final ArrayList<TemplateTweet> mTemplateOneshot = new ArrayList<>();
	private final ContentObserver mTemplateTweetObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			if (selfChange)
				return;
			ContentResolver resolver = getContentResolver();
			synchronized (mTemplateOneshot) {
				Cursor c = resolver.query(TemplateTweetProvider.URI_TEMPLATES, null, "enabled!=0 AND type=" + TemplateTweetProvider.TEMPLATE_TYPE_ONESHOT, null, "_id ASC");
				if (c.moveToFirst()) {
					wholeLoop:
					do {
						final TemplateTweet t = TemplateTweet.obtain(c, resolver);
						if (t.mUserIdList.isEmpty())
							continue;
						if (mTemplateOneshot.contains(t))
							continue;
						for (long user_id : t.mUserIdList)
							if (TwitterEngine.get(user_id) == null)
								continue wholeLoop;
						mTemplateOneshot.add(t);
						new Thread() {
							@Override
							public void run() {
								t.post(new TemplateTweet.OnTweetListener() {
									@Override
									public void onTweetSucceed(TemplateTweet template) {
										synchronized (mTemplateOneshot) {
											mTemplateOneshot.remove(t);
											if (t.remove_after) {
												t.removeSelf(getContentResolver());
											} else {
												template.enabled = false;
												t.updateSelf(getContentResolver());
											}
										}
									}

									@Override
									public void onTweetFail(TemplateTweet template, Throwable why) {
										synchronized (mTemplateOneshot) {
											mTemplateOneshot.remove(t);
											template.enabled = false;
											t.updateSelf(getContentResolver());
											// TODO Notify why
										}
									}
								}, mHandler);
							}
						}.start();
					} while (c.moveToNext());
				}
				c.close();
			}
		}
	};
	private Messenger mCallback;
	private Page mNotifyChecker;
	private ImageCache mImageCache;
	private File mNotificationListCacheFile;
	private boolean mIsForegroundService;
	private boolean mIsWaitingForQuit;

	@Override
	public void onCreate() {
		super.onCreate();
		Page.Builder builder = new Page.Builder();
		for (TwitterEngine e : TwitterEngine.getStreamables()) {
			e.addStreamCallback(this);
			builder.e().add(new PageElement(e, PageElement.FUNCTION_MENTIONS, 0, null));
		}
		mNotifyChecker = builder.e().isEmpty() ? null : builder.build();
		TwitterEngine.addOnUserlistChangedListener(this);
		mImageCache = ImageCache.getCache(this, this);

		mNotificationListCacheFile = new File(getCacheDir(), NOTIFICATION_LIST_FILE);
		loadActivityNotifications();

		getContentResolver().registerContentObserver(TemplateTweetProvider.URI_BASE, true, mTemplateTweetObserver);
		mTemplateTweetObserver.onChange(false);

		loadStreamState();
	}

	@Override
	public void onUserlistChanged(List<TwitterEngine> engines, List<TwitterEngine> oldEngines) {
		Page.Builder builder = new Page.Builder();
		synchronized (mRemovalPendingStreamingUsers) {
			for (TwitterEngine e : engines) {
				e.addStreamCallback(this);
				builder.e().add(new PageElement(e, PageElement.FUNCTION_MENTIONS, 0, null));
				if (mRemovalPendingStreamingUsers.contains(e))
					mHandler.removeMessages(MESSAGE_USER_REMOVED_STREAM_STOP, mRemovalPendingStreamingUsers.remove(mRemovalPendingStreamingUsers.indexOf(e)));
			}
			for (TwitterEngine e : new ArrayList<>(mActiveStreams)) {
				if (!engines.contains(e) && !mRemovalPendingStreamingUsers.contains(e)) {
					mHandler.sendMessageDelayed(Message.obtain(mHandler, MESSAGE_USER_REMOVED_STREAM_STOP, e), MESSAGE_USER_REMOVED_STREAM_STOP_WAIT_DURATION);
					mRemovalPendingStreamingUsers.add(e);
				}
			}
		}
		if (builder.e().isEmpty())
			mNotifyChecker = null;
		else
			mNotifyChecker = builder.build();
	}

	private void loadActivityNotifications() {
		new Thread() {
			@Override
			public void run() {
				InputStream in = null;
				byte[] b;
				Parcel p = null;
				try {
					b = new byte[(int) mNotificationListCacheFile.length()];
					in = new FileInputStream(mNotificationListCacheFile);
					p = Parcel.obtain();
					p.unmarshall(b, 0, in.read(b));
					p.setDataPosition(0);
					synchronized (mActivities) {
						p.readList(mActivities, ActivityNotification.class.getClassLoader());
					}
					showNotification();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					StreamTools.close(in);
					if (p != null)
						p.recycle();
				}
			}
		}.start();
	}

	private void applyActivityNotificationChange() {
		new Thread() {
			@Override
			public void run() {
				OutputStream out = null;
				Parcel p = null;
				try {
					out = new FileOutputStream(mNotificationListCacheFile);
					p = Parcel.obtain();
					synchronized (mActivities) {
						p.writeList(mActivities);
					}
					out.write(p.marshall());
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					StreamTools.close(out);
					if (p != null)
						p.recycle();
				}
			}
		}.start();
	}

	public void addMentionNotification(Tweet tweet) {
		if (tweet.retweeted_status != null)
			return; // don't notify retweets
		synchronized (mActivities) {
			mActivities.add(new ActivityNotification(ActivityNotification.NEW_MENTION, tweet, tweet.user, null));
		}
		applyActivityNotificationChange();
		showNotification();
	}

	public void clearActivityNotifications() {
		synchronized (mActivities) {
			mActivities.clear();
			applyActivityNotificationChange();
		}
	}

	public void addActiveStream(TwitterEngine e) {
		synchronized (mActiveStreams) {
			if (!mActiveStreams.contains(e)) {
				mActiveStreams.add(e);
				synchronized (mCallbackModifyLock) {
					if (mCallback != null) {
						Message msgs = Message.obtain(null, MESSAGE_STREAM_CALLBACK_USE_STREAM_ON);
						Bundle b = new Bundle();
						b.putLong("user_id", e.getUserId());
						msgs.setData(b);
						try {
							mCallback.send(msgs);
						} catch (RemoteException re) {
							re.printStackTrace();
						}
					}
				}
			}
		}
	}

	public void removeActiveStream(TwitterEngine e) {
		synchronized (mActiveStreams) {
			if (mActiveStreams.remove(e)) {
				Message msgs = Message.obtain(null, MESSAGE_STREAM_CALLBACK_USE_STREAM_OFF);
				synchronized (mCallbackModifyLock) {
					if (mCallback != null) {
						Bundle b = new Bundle();
						b.putLong("user_id", e.getUserId());
						msgs.setData(b);
						try {
							mCallback.send(msgs);
						} catch (RemoteException re) {
							re.printStackTrace();
							mCallback = null;
						}
					}
				}
			}
		}
		saveStreamState();
	}

	private void saveStreamState(){
		SharedPreferences.Editor edit = getSharedPreferences("stream-state", MODE_PRIVATE).edit();
		synchronized (mActiveStreams) {
			edit.clear();
			if(!mIsWaitingForQuit)
				for(TwitterEngine e : mActiveStreams){
					if(e.isStreamUsed())
						edit.putBoolean("u" + e.getUserId(), true);
				}
		}
		edit.apply();
	}

	private void loadStreamState(){
		SharedPreferences state = getSharedPreferences("stream-state", MODE_PRIVATE);
		for(TwitterEngine e : TwitterEngine.getAll())
			if(state.getBoolean("u" + e.getUserId(), false))
				e.setUseStream(true);
	}

	@Override
	public boolean handleMessage(final Message msg) {
		switch (msg.what) {
			case MESSAGE_USER_REMOVED_STREAM_STOP: {
				synchronized (mRemovalPendingStreamingUsers) {
					if (mRemovalPendingStreamingUsers.remove(msg.obj)) {
						((TwitterEngine) msg.obj).setUseStream(false);
						removeActiveStream((TwitterEngine) msg.obj);
					}
				}
				return true;
			}
			case MESSAGE_BREAK_CONNECTION: {
				if (!mActiveStreams.isEmpty()) {
					mHandler.removeMessages(MESSAGE_ACTUAL_STREAM_BREAK);
					mHandler.sendMessageDelayed(Message.obtain(mHandler, MESSAGE_ACTUAL_STREAM_BREAK, (int) (msg.getWhen() >> 32), (int) msg.getWhen()), 5000); // change after 5s of inactivity
				}
				return true;
			}
			case MESSAGE_ACTUAL_STREAM_BREAK: {
				long breakTime = ((long) msg.arg1 << 32) | msg.arg2;
				synchronized (mActiveStreams) {
					for (TwitterEngine e : mActiveStreams)
						if (breakTime > e.getLastActivityTime())
							e.breakStreamAndRetryConnect();
				}
				return true;
			}
			case MESSAGE_STREAM_SET_CALLBACK: {
				synchronized (mCallbackModifyLock) {
					mCallback = msg.replyTo;
					for (TwitterEngine e : mActiveStreams) {
						Message msgs = Message.obtain(null, MESSAGE_STREAM_CALLBACK_USE_STREAM_ON);
						Bundle b = new Bundle();
						b.putLong("user_id", e.getUserId());
						msgs.setData(b);
						try {
							mCallback.send(msgs);
						} catch (RemoteException re) {
							re.printStackTrace();
							mCallback = null;
						}
					}
					try {
						mCallback.send(Message.obtain(null, MESSAGE_STREAM_CALLBACK_PREPARED));
					} catch (RemoteException re) {
						re.printStackTrace();
						mCallback = null;
					}
				}
				return true;
			}
			case MESSAGE_STREAM_START: {
				long user_id = ((msg.arg1 & 0xffffffffL) << 32) | (msg.arg2 & 0xffffffffL);
				TwitterEngine e = TwitterEngine.get(user_id);
				if (e != null) {
					e.setUseStream(true);
				}
				return true;
			}
			case MESSAGE_STREAM_STOP: {
				long user_id = ((msg.arg1 & 0xffffffffL) << 32) | (msg.arg2 & 0xffffffffL);
				TwitterEngine e = TwitterEngine.get(user_id);
				if (e != null) {
					e.setUseStream(false);
				}
				return true;
			}
			case MESSAGE_CLEAR_NOTIFICATION: {
				clearActivityNotifications();
				return true;
			}
			case MESSAGE_STREAM_QUIT_REGISTER: {
				boolean ongoingStreamExists = false;
				for (TwitterEngine e : mActiveStreams)
					ongoingStreamExists |= e.isStreamUsed();
				if (ongoingStreamExists && !mHandler.hasMessages(MESSAGE_ACTUAL_STREAM_QUIT)) {
					mHandler.sendEmptyMessageDelayed(MESSAGE_ACTUAL_STREAM_QUIT, DELAY_QUIT_TIME);
					mIsWaitingForQuit = true;
					saveStreamState();
					showStreamTicker(getString(R.string.stream_quit_ongoing_notification));
				}
				applyStreamIndicator(null);
				return true;
			}
			case MESSAGE_STREAM_QUIT_CANCEL: {
				mHandler.removeMessages(MESSAGE_ACTUAL_STREAM_QUIT);
				mIsWaitingForQuit = false;
				saveStreamState();
				applyStreamIndicator(null);
				return true;
			}
			case MESSAGE_ACTUAL_STREAM_QUIT: {
				mHandler.removeMessages(MESSAGE_ACTUAL_STREAM_QUIT);
				stopForeground(true);
				mIsForegroundService = true;
				synchronized (mActiveStreams) {
					for (TwitterEngine e : mActiveStreams)
						e.setUseStream(false);
				}
				return true;
			}
			case MESSAGE_UPDATE_NOTIFICATION: {
				synchronized (mActivities) {
					NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					if (mActivities.isEmpty()) {
						mNotificationManager.cancel(R.id.NOTIFICATION_ACTIVITIES);
						return true;
					}
					NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
					Intent intentShowMainActivity = new Intent(NotificationBroadcastReceiver.CLEAR_NOTIFICATIONS);
					intentShowMainActivity.putExtra("show", "MainActivity");
					intentShowMainActivity.putExtra("from", "notification");
					intentShowMainActivity.putExtra("activities", mActivities);

					StringBuilder tickers = new StringBuilder();
					for (ActivityNotification noti : mActivities)
						if (!noti.isTickerShowed())
							tickers.append(noti.getTicker(this)).append("\n");
					if (!tickers.toString().trim().isEmpty()) {
						mBuilder.setTicker(tickers.toString().trim());
						applyActivityNotificationChange();
					}

					if (mActivities.size() == 1) {
						ActivityNotification noti = mActivities.get(0);
						if (noti.setLargeIcon(mBuilder, mImageCache, this))
							mBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), noti.getStatusBarIcon()));
						mBuilder.setContentText(noti.getTicker(this));
						mBuilder.setStyle(noti.getStyle(mBuilder, this));
						mBuilder.setContentTitle(noti.getNotificationTitle(this));
						mBuilder.setSmallIcon(noti.getStatusBarIcon());
					} else {
						NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle(mBuilder);
						String summary = StringTools.fillStringResFormat(this, R.string.notification_new_activities_summary, "count", "" + mActivities.size());
						inboxStyle.setBigContentTitle(summary);
						inboxStyle.setSummaryText(mActivities.size() > 7 ? StringTools.fillStringResFormat(this, R.string.notification_new_activities_more, "count", "" + (mActivities.size() - 7)) : "");
						for (int i = mActivities.size() - 1, j = 0; i >= 0 && j < 7; i--, j++)
							inboxStyle.addLine(mActivities.get(i).getTicker(this));

						mBuilder.setStyle(inboxStyle);
						mBuilder.setContentTitle(getString(R.string.notification_new_activities));
						mBuilder.setContentText(summary);
						mBuilder.setSmallIcon(R.drawable.ic_mention); // TODO "ic_activity"
					}

					mBuilder.setAutoCancel(true);

					mBuilder.setDeleteIntent(PendingIntent.getBroadcast(this, 0, new Intent(NotificationBroadcastReceiver.CLEAR_NOTIFICATIONS, null), 0));
					mBuilder.setContentIntent(PendingIntent.getBroadcast(this, 0, intentShowMainActivity, 0));


					mNotificationManager.notify(R.id.NOTIFICATION_ACTIVITIES, mBuilder.build());

				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void onImageAvailable(String url, BitmapDrawable bmp, Drawable d, int originalWidth, int originalHeight) {
		showNotification();
	}

	@Override
	public void onImageUnavailable(String url, int reason) {

	}

	@Override
	public IBinder onBind(Intent intent) {
		mHandler.sendEmptyMessage(MESSAGE_STREAM_QUIT_CANCEL);
		return mMessenger.getBinder();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		mHandler.sendEmptyMessage(MESSAGE_STREAM_QUIT_REGISTER);
		mCallback = null;
		android.util.Log.e("darknova", "unbound");
		return false;
	}

	@Override
	public void onDestroy() {
		synchronized (mActiveStreams) {
			for (TwitterEngine e : mActiveStreams) {
				e.setUseStream(false);
			}
			mActiveStreams.clear();
		}
		getContentResolver().unregisterContentObserver(mTemplateTweetObserver);
		applyStreamIndicator(StringTools.fillStringResFormat(this, R.string.stream_stopped_all));
		super.onDestroy();
	}

	public void showNotification() {
		mHandler.sendEmptyMessage(MESSAGE_UPDATE_NOTIFICATION);
	}

	public void showStreamTicker(String ticker) {
		NotificationManager mNotificationManager =
				(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
						.setSmallIcon(R.drawable.ic_wing)
						.setContentTitle(StringTools.fillStringResFormat(this, R.string.stream_notification_title, "app_name", getString(R.string.app_name)))
						.setContentText(ticker).setTicker(ticker);
		mNotificationManager.notify(R.id.NOTIFICATION_STREAM_ONESHOT_TICKER, mBuilder.build());
		mNotificationManager.cancel(R.id.NOTIFICATION_STREAM_ONESHOT_TICKER);
	}

	public void applyStreamIndicator(String ticker) {
		NotificationManager mNotificationManager =
				(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		synchronized (mActiveStreams) {
			if (mActiveStreams.isEmpty()) {
				if (mIsForegroundService) {
					mIsForegroundService = false;
					stopForeground(true);
				}
				if (ticker != null) {
					NotificationCompat.Builder mBuilder =
							new NotificationCompat.Builder(this)
									.setSmallIcon(R.drawable.ic_wing)
									.setContentTitle(StringTools.fillStringResFormat(this, R.string.stream_notification_title, "app_name", getString(R.string.app_name)))
									.setContentText(ticker).setTicker(ticker);
					mNotificationManager.notify(R.id.NOTIFICATION_STREAM_INDICATOR, mBuilder.build());
				}
				mNotificationManager.cancel(R.id.NOTIFICATION_STREAM_INDICATOR);
			} else {
				StringBuilder sb = new StringBuilder(mActiveStreams.get(0).getScreenName());
				for (int i = 1; i < mActiveStreams.size(); i++) {
					sb.append(", ").append(mActiveStreams.get(i).getScreenName());
				}
				NotificationCompat.Builder mBuilder =
						new NotificationCompat.Builder(this)
								.setSmallIcon(R.drawable.ic_wing)
								.setContentTitle(StringTools.fillStringResFormat(this, R.string.stream_notification_title, "app_name", getString(R.string.app_name)))
								.setContentText(sb.toString());
				if (ticker != null)
					mBuilder.setTicker(ticker);
				mBuilder.setAutoCancel(false);
				mBuilder.setOngoing(true);
				Intent intentShowMainActivity = new Intent(this, MainActivity.class);
				intentShowMainActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				intentShowMainActivity.putExtra("from", "notification");
				intentShowMainActivity.putExtra("activities", mActivities);
				mBuilder.setContentIntent(PendingIntent.getActivity(this, 0, intentShowMainActivity, PendingIntent.FLAG_UPDATE_CURRENT));
				if (mIsWaitingForQuit)
					mBuilder.addAction(R.drawable.ic_check_black_36dp, getString(R.string.notification_keep_streaming_btn), PendingIntent.getBroadcast(this, 0, new Intent(NotificationBroadcastReceiver.KEEP_STREAMING, null), 0));
				mBuilder.addAction(R.drawable.ic_clear_black_36dp, getString(R.string.notification_stop_streaming_btn), PendingIntent.getBroadcast(this, 0, new Intent(NotificationBroadcastReceiver.STOP_STREAMING, null), 0));
				Notification built = mBuilder.build();
				mNotificationManager.notify(R.id.NOTIFICATION_STREAM_INDICATOR, built);
				if (!mIsForegroundService) {
					mIsForegroundService = true;
					startForeground(R.id.NOTIFICATION_STREAM_INDICATOR, built);
				}
			}
		}
	}

	public void sendCallbackMessages(TwitterEngine e, int what, Bundle bundle) {
		mHandler.removeMessages(MESSAGE_ACTUAL_STREAM_BREAK);
		synchronized (mCallbackModifyLock) {
			if (mCallback != null) {
				Message msg = Message.obtain(null, what);
				if (bundle == null)
					bundle = new Bundle();
				if (e != null)
					bundle.putLong("user_id", e.getUserId());
				msg.setData(bundle);
				try {
					mCallback.send(msg);
				} catch (RemoteException re) {
					msg.recycle();
					re.printStackTrace();
					mCallback = null;
				}
			}
		}
	}

	@Override
	public void onStreamStart(TwitterEngine engine) {
		addActiveStream(engine);
		applyStreamIndicator(null);
		sendCallbackMessages(engine, MESSAGE_STREAM_CALLBACK_START, null);
	}

	@Override
	public void onStreamConnected(TwitterEngine engine) {
		applyStreamIndicator(StringTools.fillStringResFormat(this, R.string.stream_connected, "user", engine.getScreenName()));
		sendCallbackMessages(engine, MESSAGE_STREAM_CALLBACK_CONNECTED, null);
	}

	@Override
	public void onStreamError(TwitterEngine engine, Throwable e) {
		applyStreamIndicator(StringTools.fillStringResFormat(this, R.string.stream_error, "user", engine.getScreenName(), "error", e.getMessage()));
		Bundle b = new Bundle();
		b.putSerializable("e", e);
		sendCallbackMessages(engine, MESSAGE_STREAM_CALLBACK_ERROR, null);
	}

	@Override
	public void onStreamTweetEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, Tweet tweet, long created_at) {
		Bundle b = new Bundle();
		b.putString("event", event);
		b.putParcelable("source", source);
		b.putParcelable("target", target);
		b.putParcelable("tweet", tweet);
		b.putLong("created_at", created_at);
		sendCallbackMessages(engine, MESSAGE_STREAM_CALLBACK_TWEET_EVENT, b);
	}

	@Override
	public void onStreamUserEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, long created_at) {
		Bundle b = new Bundle();
		b.putString("event", event);
		b.putParcelable("source", source);
		b.putParcelable("target", target);
		sendCallbackMessages(engine, MESSAGE_STREAM_CALLBACK_USER_EVENT, b);
	}

	@Override
	public void onStreamStop(TwitterEngine engine) {
		sendCallbackMessages(engine, MESSAGE_STREAM_CALLBACK_STOP, null);
		removeActiveStream(engine);
		applyStreamIndicator(StringTools.fillStringResFormat(this, R.string.stream_stopped, "user", engine.getScreenName()));
	}

	@Override
	public void onNewTweetReceived(Tweet tweet) {
		if (mNotifyChecker != null && mNotifyChecker.canHave(tweet)) {
			addMentionNotification(tweet);
		}
		Bundle b = new Bundle();
		b.putParcelable("tweet", tweet);
		sendCallbackMessages(null, MESSAGE_STREAM_CALLBACK_NEW_TWEET, b);
	}

	@Override
	public void onImageCacheReady(ImageCache cache) {
		mImageCache = cache;
		showNotification();
	}

	private static class ActivityNotification implements ImageCache.OnImageAvailableListener, Parcelable {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<ActivityNotification> CREATOR = new Parcelable.Creator<ActivityNotification>() {
			@Override
			public ActivityNotification createFromParcel(Parcel in) {
				return new ActivityNotification(in);
			}

			@Override
			public ActivityNotification[] newArray(int size) {
				return new ActivityNotification[size];
			}
		};
		private static final Pattern SPACE_UNDUPLICATOR = Pattern.compile("\\s+");
		private static final int NEW_TWEET = 0;
		private static final int NEW_MENTION = 1;
		private static final int NEW_DM = 2;
		private static final int NEW_PAGE_ITEM = 3;
		private final Tweet mTarget;
		private final Tweeter mTargetUser;
		private final Tweeter mSourceUser;
		private final int mNotificationType;
		private boolean mTickerShown;
		private Bitmap mBitmap;

		public ActivityNotification(int type, Tweet targetTweet, Tweeter sourceUser, Tweeter targetUser) {
			mNotificationType = type;
			mTarget = targetTweet;
			mSourceUser = sourceUser;
			mTargetUser = targetUser;
		}

		protected ActivityNotification(Parcel in) {
			mTarget = (Tweet) in.readValue(Tweet.class.getClassLoader());
			mTargetUser = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
			mSourceUser = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
			mNotificationType = in.readInt();
			mTickerShown = in.readByte() != 0x00;
		}

		public String getNotificationTitle(Context context) {
			if (mNotificationType == NEW_MENTION)
				return context.getString(R.string.notification_new_mention);
			return "unknown";
		}

		public int getStatusBarIcon() {
			return R.drawable.ic_mention;
		}

		public String toString(Context context) {
			switch (mNotificationType) {
				case NEW_MENTION:
					return StringTools.fillStringResFormat(context, R.string.notification_new_mention_description, "user", "@" + mSourceUser.screen_name, "text", SPACE_UNDUPLICATOR.matcher(mTarget.text).replaceAll(" "));
			}
			return "unknown notification";
		}

		public String getTicker(Context context) {
			mTickerShown = true;
			return toString(context);
		}

		public boolean setLargeIcon(NotificationCompat.Builder builder, ImageCache imageCache, ImageCache.OnImageAvailableListener listener) {
			if (mBitmap != null) {
				builder.setLargeIcon(mBitmap);
				return false;
			}
			if (imageCache == null)
				return true;
			BitmapDrawable drw = imageCache.getBitmapFromMemCache(mTarget.user.getProfileImageUrl());
			if (drw != null) {
				builder.setLargeIcon(mBitmap = drw.getBitmap());
				return false;
			}
			// TODO image size
			imageCache.prepareBitmap(mSourceUser.getProfileImageUrl(), null, 384, this);
			imageCache.prepareBitmap(mSourceUser.getProfileImageUrl(), null, 384, listener);
			return true;
		}

		public boolean isTickerShowed() {
			return mTickerShown;
		}

		public NotificationCompat.Style getStyle(NotificationCompat.Builder builder, Context context) {
			switch (mNotificationType) {
				case NEW_MENTION: {
					NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle(builder);
					bigStyle.bigText(SPACE_UNDUPLICATOR.matcher(mTarget.text).replaceAll(" "));
					bigStyle.setSummaryText(StringTools.fillStringResFormat(context, R.string.notification_new_mention_big_summary, "user_id", mSourceUser.screen_name, "user_name", mSourceUser.name));
					bigStyle.setBigContentTitle(context.getString(R.string.notification_new_mention));
					return bigStyle;
				}
			}
			return null;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeValue(mTarget);
			dest.writeValue(mTargetUser);
			dest.writeValue(mSourceUser);
			dest.writeInt(mNotificationType);
			dest.writeByte((byte) (mTickerShown ? 0x01 : 0x00));
		}

		@Override
		public void onImageAvailable(String url, BitmapDrawable bmp, Drawable d, int originalWidth, int originalHeight) {
			mBitmap = bmp.getBitmap();
		}

		@Override
		public void onImageUnavailable(String url, int reason) {

		}
	}
}
