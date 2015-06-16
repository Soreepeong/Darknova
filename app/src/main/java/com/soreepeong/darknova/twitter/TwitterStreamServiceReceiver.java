package com.soreepeong.darknova.twitter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.soreepeong.darknova.services.DarknovaService;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Twitter Stream Service Receiver
 *
 * @author Soreepeong
 */
public class TwitterStreamServiceReceiver {
	private static final HashSet<TwitterEngine.TwitterStreamCallback> mStreamCallbacks = new HashSet<>();
	private static final Handler mHandler = new Handler(Looper.getMainLooper());

	private static final ArrayList<TwitterEngine> mStreamOnUsers = new ArrayList<>();
	private static final ArrayList<OnStreamTurnedListener> mStreamTurnListeners = new ArrayList<>();
	private static final ArrayList<OnServiceInterfaceReadyListener> mServiceReadyListeners = new ArrayList<>();
	private static final TwitterEngine.TwitterStreamCallback mStreamCallback = new TwitterEngine.TwitterStreamCallback() {

		@Override
		public void onStreamConnected(final TwitterEngine engine) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					synchronized (mStreamCallbacks) {
						for (TwitterEngine.TwitterStreamCallback c : mStreamCallbacks)
							c.onStreamConnected(engine);
					}
				}
			});
		}

		@Override
		public void onStreamStart(final TwitterEngine engine) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					synchronized (mStreamCallbacks) {
						for (TwitterEngine.TwitterStreamCallback c : mStreamCallbacks)
							c.onStreamStart(engine);
					}
				}
			});
		}

		@Override
		public void onStreamError(final TwitterEngine engine, final Throwable e) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					synchronized (mStreamCallbacks) {
						for (TwitterEngine.TwitterStreamCallback c : mStreamCallbacks)
							c.onStreamError(engine, e);
					}
				}
			});
		}

		@Override
		public void onStreamTweetEvent(final TwitterEngine engine, final String event, final Tweeter source, final Tweeter target, final Tweet tweet, final long created_at) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					synchronized (mStreamCallbacks) {
						for (TwitterEngine.TwitterStreamCallback c : mStreamCallbacks)
							c.onStreamTweetEvent(engine, event, source, target, tweet, created_at);
					}
				}
			});
		}

		@Override
		public void onStreamUserEvent(final TwitterEngine engine, final String event, final Tweeter source, final Tweeter target, final long created_at) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					synchronized (mStreamCallbacks) {
						for (TwitterEngine.TwitterStreamCallback c : mStreamCallbacks)
							c.onStreamUserEvent(engine, event, source, target, created_at);
					}
				}
			});
		}

		@Override
		public void onStreamStop(final TwitterEngine engine) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					synchronized (mStreamCallbacks) {
						for (TwitterEngine.TwitterStreamCallback c : mStreamCallbacks)
							c.onStreamStop(engine);
					}
				}
			});
		}

		@Override
		public void onNewTweetReceived(Tweet tweet) {
			synchronized (mStreamCallbacks) {
				for (TwitterEngine.TwitterStreamCallback c : mStreamCallbacks)
					c.onNewTweetReceived(tweet);
			}
		}
	};
	private static Messenger mBoundService;
	private static Context mContext;
	private static ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mBoundService = new Messenger(service);

			Message msg = Message.obtain(null, DarknovaService.MESSAGE_STREAM_SET_CALLBACK);
			msg.replyTo = new Messenger(new Handler() {
				@Override
				public void handleMessage(Message msg) {
					Bundle b = msg.getData();
					b.setClassLoader(mContext.getClassLoader());
					TwitterEngine e = TwitterEngine.get(b.getLong("user_id"));
					switch (msg.what) {
						case DarknovaService.MESSAGE_STREAM_CALLBACK_PREPARED: {
							synchronized (mServiceReadyListeners) {
								mServiceReadyListeners.notifyAll();
								for (OnServiceInterfaceReadyListener l : mServiceReadyListeners)
									l.onServiceInterfaceReady();
							}
							break;
						}
						case DarknovaService.MESSAGE_STREAM_CALLBACK_USE_STREAM_ON:
							if (!mStreamOnUsers.contains(e)) {
								mStreamOnUsers.add(e);
								synchronized (mStreamTurnListeners) {
									for (OnStreamTurnedListener l : mStreamTurnListeners)
										l.onStreamTurnedOn(e);
								}
							}
							break;
						case DarknovaService.MESSAGE_STREAM_CALLBACK_USE_STREAM_OFF:
							if (mStreamOnUsers.remove(e))
								synchronized (mStreamTurnListeners) {
									for (OnStreamTurnedListener l : mStreamTurnListeners)
										l.onStreamTurnedOff(e);
								}
							break;
						case DarknovaService.MESSAGE_STREAM_CALLBACK_NEW_TWEET:
							mStreamCallback.onNewTweetReceived((Tweet) b.getParcelable("tweet"));
							break;
						case DarknovaService.MESSAGE_STREAM_CALLBACK_START:
							mStreamCallback.onStreamStart(e);
							break;
						case DarknovaService.MESSAGE_STREAM_CALLBACK_CONNECTED:
							mStreamCallback.onStreamConnected(e);
							break;
						case DarknovaService.MESSAGE_STREAM_CALLBACK_ERROR:
							mStreamCallback.onStreamError(e, (Throwable) b.getSerializable("e"));
							break;
						case DarknovaService.MESSAGE_STREAM_CALLBACK_TWEET_EVENT:
							mStreamCallback.onStreamTweetEvent(e, b.getString("event"), (Tweeter) b.getParcelable("source"), (Tweeter) b.getParcelable("target"), (Tweet) b.getParcelable("tweet"), b.getLong("created_at"));
							break;
						case DarknovaService.MESSAGE_STREAM_CALLBACK_USER_EVENT:
							mStreamCallback.onStreamUserEvent(e, b.getString("event"), (Tweeter) b.getParcelable("source"), (Tweeter) b.getParcelable("target"), b.getLong("created_at"));
							break;
						case DarknovaService.MESSAGE_STREAM_CALLBACK_STOP:
							mStreamCallback.onStreamStop(e);
							break;
					}
				}
			});
			try {
				mBoundService.send(msg);
				mBoundService.send(Message.obtain(null, DarknovaService.MESSAGE_STREAM_QUIT_CANCEL));
			} catch (RemoteException re) {
				re.printStackTrace();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBoundService = null;
		}
	};

	public static void addOnServiceReadyListener(OnServiceInterfaceReadyListener l) {
		synchronized (mServiceReadyListeners) {
			mServiceReadyListeners.add(l);
		}
	}

	public static void removeOnServiceReadyListener(OnServiceInterfaceReadyListener l) {
		synchronized (mServiceReadyListeners) {
			mServiceReadyListeners.remove(l);
		}
	}

	public static void waitForService() throws InterruptedException {
		synchronized (mServiceReadyListeners) {
			if (mBoundService == null)
				mServiceReadyListeners.wait();
		}
	}

	public static void addOnStreamTurnListener(OnStreamTurnedListener l) {
		synchronized (mStreamTurnListeners) {
			mStreamTurnListeners.add(l);
		}
	}

	public static void removeOnStreamTurnListener(OnStreamTurnedListener l) {
		synchronized (mStreamTurnListeners) {
			mStreamTurnListeners.remove(l);
		}
	}

	public static void forceSetStatus(TwitterEngine e, boolean b) {
		if (b && !mStreamOnUsers.contains(e))
			mStreamOnUsers.add(e);
		else if (!b && mStreamOnUsers.contains(e))
			mStreamOnUsers.remove(e);
	}

	public static boolean isStreamOn(TwitterEngine e) {
		return mStreamOnUsers.contains(e);
	}

	public static void turnStreamOn(TwitterEngine e, boolean state) {
		if (state) {
			sendServiceMessage(Message.obtain(null, DarknovaService.MESSAGE_STREAM_START, (int) (e.getUserId() >> 32), (int) e.getUserId()));
		} else {
			sendServiceMessage(Message.obtain(null, DarknovaService.MESSAGE_STREAM_STOP, (int) (e.getUserId() >> 32), (int) e.getUserId()));
		}
	}

	public static void sendServiceMessage(Message m) {
		if (mBoundService == null)
			return;
		try {
			mBoundService.send(m);
		} catch (RemoteException re) {
			re.printStackTrace();
		}
	}

	public static void addStreamCallback(TwitterEngine.TwitterStreamCallback callback) {
		synchronized (mStreamCallbacks) {
			if (!mStreamCallbacks.contains(callback))
				mStreamCallbacks.add(callback);
		}
	}

	public static void removeStreamCallback(TwitterEngine.TwitterStreamCallback callback) {
		synchronized (mStreamCallbacks) {
			mStreamCallbacks.remove(callback);
		}
	}

	public static Messenger getBoundService() {
		return mBoundService;
	}

	public static void init(Context ctx) {
		mContext = ctx.getApplicationContext();
		mContext.bindService(new Intent(mContext, DarknovaService.class), mConnection, Context.BIND_AUTO_CREATE);
	}

	public interface OnStreamTurnedListener {
		void onStreamTurnedOn(TwitterEngine e);

		void onStreamTurnedOff(TwitterEngine e);
	}

	public interface OnServiceInterfaceReadyListener {
		void onServiceInterfaceReady();
	}
}
