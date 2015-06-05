package com.soreepeong.darknova.twitter;


import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;

import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.tools.WeakValueHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Twitter user
 *
 * @author Soreepeong
 */
public class Tweeter implements Parcelable {

	private static final WeakValueHashMap<Long, Tweeter> mUsersByUserId = new WeakValueHashMap<>();
	private static final WeakValueHashMap<String, Tweeter> mUsersByScreenName = new WeakValueHashMap<>();
	private static final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
	private static final int RESOLVE_DELAY = 500;
	private static final HashMap<Tweeter, TwitterEngine> mResolveRequiredUsers = new HashMap<>();
	private static AlwaysAvailableUsers mAlwaysAvailableUsers;
	final private ArrayList<WeakReference<OnUserInformationChangedListener>> mChangeListeners = new ArrayList<>();
	public InternalInformation info = new InternalInformation();
	public long user_id;
	public long created_at;
	public String screen_name;
	public String name;
	public String description;
	public String location;
	public String url;
	public String[] profile_image_url;
	public String[] profile_banner_url;
	public boolean _protected;
	public boolean verified;
	public boolean geo_enabled;
	public long statuses_count;
	public long favourites_count;
	public long friends_count;
	public long followers_count;
	public long listed_count;
	public Entities entities_url;
	public Entities entities_description;
	private static final Runnable mResolver = new Runnable() {
		@Override
		public void run() {
			if(mAlwaysAvailableUsers == null || !mAlwaysAvailableUsers.mLoaded){
				mMainThreadHandler.removeCallbacks(mResolver);
				mMainThreadHandler.postDelayed(mResolver, RESOLVE_DELAY);
				return;
			}
			final Map<Tweeter, TwitterEngine> toLoad;
			final HashMap<TwitterEngine, ArrayList<Tweeter>> byEngine = new HashMap<>();
			synchronized (mResolveRequiredUsers) {
				toLoad = Collections.unmodifiableMap(new HashMap<>(mResolveRequiredUsers));
				mResolveRequiredUsers.clear();
			}
			for (Tweeter t : toLoad.keySet()) {
				if (!t.info.stub && t.info.lastUpdated > System.currentTimeMillis() - 3600_000) // 60 min
					continue;
				if (byEngine.get(toLoad.get(t)) == null)
					byEngine.put(toLoad.get(t), new ArrayList<Tweeter>());
				byEngine.get(toLoad.get(t)).add(t);
			}
			for (final TwitterEngine engine : byEngine.keySet()) {
				final long[] ids = new long[byEngine.get(engine).size()];
				for (int i = byEngine.get(engine).size() - 1; i >= 0; i--) {
					ids[i] = byEngine.get(engine).get(i).user_id;
					android.util.Log.d("Tweeter-Resolver", engine.getScreenName() + ": " + byEngine.get(engine).get(i).screen_name);
				}
				new Thread() {
					@Override
					public void run() {
						try {
							engine.lookupUser(ids);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}.start();
			}
		}
	};
	public HashMap<Long, ForUserInfo> perUserInfo = new HashMap<>();
	private Runnable mChangeNotifierRunnable = new Runnable() {
		@Override
		public void run() {
			synchronized (mChangeListeners) {
				Iterator<WeakReference<OnUserInformationChangedListener>> iter = mChangeListeners.iterator();
				while (iter.hasNext()) {
					WeakReference<OnUserInformationChangedListener> ref = iter.next();
					OnUserInformationChangedListener dat = ref.get();
					if (dat == null)
						iter.remove();
					else
						dat.onUserInformationChanged(Tweeter.this);
				}
			}
		}
	};
	@SuppressWarnings("unused")
	public static final Parcelable.Creator<Tweeter> CREATOR = new Parcelable.Creator<Tweeter>() {
		@Override
		public Tweeter createFromParcel(Parcel in) {
			return updateTweeter(new Tweeter(in));
		}

		@Override
		public Tweeter[] newArray(int size) {
			return new Tweeter[size];
		}
	};

	private Tweeter() {
	}

	protected Tweeter(Parcel in) {
		info = (InternalInformation) in.readValue(InternalInformation.class.getClassLoader());
		user_id = in.readLong();
		created_at = in.readLong();
		screen_name = in.readString();
		name = in.readString();
		description = in.readString();
		location = in.readString();
		url = in.readString();
		profile_image_url = in.createStringArray();
		profile_banner_url = in.createStringArray();
		_protected = in.readByte() != 0x00;
		verified = in.readByte() != 0x00;
		geo_enabled = in.readByte() != 0x00;
		statuses_count = in.readLong();
		favourites_count = in.readLong();
		friends_count = in.readLong();
		followers_count = in.readLong();
		listed_count = in.readLong();
		entities_url = (Entities) in.readValue(Entities.class.getClassLoader());
		entities_description = (Entities) in.readValue(Entities.class.getClassLoader());
		perUserInfo = in.readHashMap(ForUserInfo.class.getClassLoader());
	}

	public static HashMap<String, Tweeter> getAvailableTweeters() {
		synchronized (mUsersByUserId) {
			return mUsersByScreenName.getMap();
		}
	}

	public static void initAlwaysAvailableUsers(Context context) {
		mAlwaysAvailableUsers = new AlwaysAvailableUsers(context);
	}

	public static void fillLoadQueuedData() {
		mMainThreadHandler.removeCallbacks(mResolver);
		mMainThreadHandler.postDelayed(mResolver, RESOLVE_DELAY);
	}

	@SuppressWarnings("all")
	public static Tweeter updateTweeter(Tweeter user) {
		synchronized (mUsersByUserId) {
			Tweeter t = mUsersByUserId.get(user.user_id);
			if (t == null) {
				t = user;
				mUsersByUserId.put(user.user_id, t);
				mUsersByScreenName.put(user.screen_name, t);
				mMainThreadHandler.post(t.mChangeNotifierRunnable);
			} else {
				if ((!user.info.stub || t.info.stub) && user.info.lastUpdated > t.info.lastUpdated) {
					boolean changed;
					changed = t.info != user.info;
					t.info = user.info;
					changed |= t.user_id != user.user_id;
					t.user_id = user.user_id;
					changed |= t.created_at != user.created_at;
					t.created_at = user.created_at;
					changed |= !t.screen_name.equals(user.screen_name);
					t.screen_name = user.screen_name;
					// diff: a!=b && (a==null || !a.equals(b))
					changed |= t.name != user.name && (t.name == null || !t.name.equals(user.name));
					t.name = user.name;
					if (user.description != null) {
						changed |= t.description != user.description && (t.description == null || !t.description.equals(user.description));
						t.description = user.description;
					}
					changed |= t.location != user.location && (t.location == null || !t.location.equals(user.location));
					t.location = user.location;
					changed |= t.url != user.url && (t.url == null || !t.url.equals(user.url));
					t.url = user.url;
					changed |= t.profile_image_url != user.profile_image_url;
					t.profile_image_url = user.profile_image_url;
					changed |= t.profile_banner_url != user.profile_banner_url;
					t.profile_banner_url = user.profile_banner_url;
					changed |= t._protected != user._protected;
					t._protected = user._protected;
					changed |= t.verified != user.verified;
					t.verified = user.verified;
					changed |= t.geo_enabled != user.geo_enabled;
					t.geo_enabled = user.geo_enabled;
					changed |= t.statuses_count != user.statuses_count;
					t.statuses_count = user.statuses_count;
					changed |= t.favourites_count != user.favourites_count;
					t.favourites_count = user.favourites_count;
					changed |= t.friends_count != user.friends_count;
					t.friends_count = user.friends_count;
					changed |= t.followers_count != user.followers_count;
					t.followers_count = user.followers_count;
					changed |= t.listed_count != user.listed_count;
					t.listed_count = user.listed_count;
					changed |= t.entities_url != user.entities_url;
					t.entities_url = user.entities_url;
					changed |= t.entities_description != user.entities_description;
					t.entities_description = user.entities_description;
					changed |= t.perUserInfo != user.perUserInfo;
					t.perUserInfo = user.perUserInfo;
					if (changed && !t.mChangeListeners.isEmpty())
						mMainThreadHandler.post(t.mChangeNotifierRunnable);
				}
			}
			return t;
		}
	}

	public static Tweeter getTweeter(long user_id, String screen_name) {
		synchronized (mUsersByUserId) {
			Tweeter user = mUsersByUserId.get(user_id);
			if (user != null) return user;
			user = mUsersByScreenName.get(screen_name.toLowerCase(Locale.ENGLISH));
			if (user != null) return user;
			user = new Tweeter();
			user.user_id = user_id;
			user.screen_name = screen_name;
			user.info.stub = true;
			mUsersByUserId.put(user_id, user);
			mUsersByScreenName.put(screen_name.toLowerCase(Locale.ENGLISH), user);
			return user;
		}
	}

	public static Tweeter getTemporaryTweeter() {
		return new Tweeter();
	}

	public void addToDataLoadQueue(TwitterEngine using) {
		synchronized (mResolveRequiredUsers) {
			if (mResolveRequiredUsers.get(this) != null) {
				if (mResolveRequiredUsers.get(this).getUserId() != user_id)
					mResolveRequiredUsers.put(this, using);
			} else
				mResolveRequiredUsers.put(this, using);
		}
	}

	public String getProfileImageUrl() {
		return profile_image_url == null ? null : profile_image_url[profile_image_url.length == 1 ? 0 : 2];
	}

	public String getProfileBannerUrl() {
		return profile_banner_url == null ? null : profile_banner_url[profile_banner_url.length * 3 / 4];
	}

	public void addOnChangeListener(OnUserInformationChangedListener listener) {
		synchronized (mChangeListeners) {
			Iterator<WeakReference<OnUserInformationChangedListener>> iter = mChangeListeners.iterator();
			boolean already = false;
			while (iter.hasNext()) {
				WeakReference<OnUserInformationChangedListener> ref = iter.next();
				OnUserInformationChangedListener l = ref.get();
				if (l == null)
					iter.remove();
				already |= l == listener;
			}
			if (!already)
				mChangeListeners.add(new WeakReference<>(listener));
		}
	}

	public void removeOnChangeListener(OnUserInformationChangedListener listener) {
		synchronized (mChangeListeners) {
			Iterator<WeakReference<OnUserInformationChangedListener>> iter = mChangeListeners.iterator();
			while (iter.hasNext()) {
				WeakReference<OnUserInformationChangedListener> ref = iter.next();
				if (ref.get() == null || ref.get().equals(listener))
					iter.remove();
			}
		}
	}

	public boolean follows(Tweeter other) {
		return other != null && other.perUserInfo.get(user_id) != null && other.perUserInfo.get(user_id).following;
	}

	public void addForUserInfo(long user_id, boolean following, boolean follow_request_sent) {
		ForUserInfo ui = new ForUserInfo();
		ui.following = following;
		ui.follow_request_sent = follow_request_sent;
		perUserInfo.put(user_id, ui);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Tweeter && user_id == ((Tweeter) o).user_id;
	}

	@Override
	public int hashCode() {
		return (int) user_id;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeValue(info);
		dest.writeLong(user_id);
		dest.writeLong(created_at);
		dest.writeString(screen_name);
		dest.writeString(name);
		dest.writeString(description);
		dest.writeString(location);
		dest.writeString(url);
		dest.writeStringArray(profile_image_url);
		dest.writeStringArray(profile_banner_url);
		dest.writeByte((byte) (_protected ? 0x01 : 0x00));
		dest.writeByte((byte) (verified ? 0x01 : 0x00));
		dest.writeByte((byte) (geo_enabled ? 0x01 : 0x00));
		dest.writeLong(statuses_count);
		dest.writeLong(favourites_count);
		dest.writeLong(friends_count);
		dest.writeLong(followers_count);
		dest.writeLong(listed_count);
		dest.writeValue(entities_url);
		dest.writeValue(entities_description);
		dest.writeMap(perUserInfo);
	}

	public interface OnUserInformationChangedListener {
		public void onUserInformationChanged(Tweeter tweeter);
	}

	public static class AlwaysAvailableUsers implements OnUserInformationChangedListener, Handler.Callback, TwitterEngine.OnUserlistChangedListener {
		private static final int LOAD = 0;
		private static final int SAVE = 1;
		private final ArrayList<Tweeter> mTweeterList = new ArrayList<>();
		private File mCachePath;
		private Handler mCacheSaver;
		private boolean mLoaded;

		protected AlwaysAvailableUsers(Context context) {
			mCachePath = new File(context.getCacheDir(), "always-available-users");
			HandlerThread mHandlerThread = new HandlerThread("always-available-users");
			mHandlerThread.start();
			TwitterEngine.addOnUserlistChangedListener(this);
			mCacheSaver = new Handler(mHandlerThread.getLooper(), this);
			mCacheSaver.sendEmptyMessage(LOAD);
		}

		@Override
		public void onUserInformationChanged(Tweeter tweeter) {
			mCacheSaver.removeMessages(SAVE);
			mCacheSaver.sendEmptyMessageDelayed(SAVE, 500);
			TwitterEngine.applyAccountInformationChanges();
		}

		@Override
		public boolean handleMessage(Message msg) {
			switch (msg.what) {
				case LOAD: {
					InputStream in = null;
					Parcel p = Parcel.obtain();
					try {
						in = new FileInputStream(mCachePath);
						byte b[] = new byte[(int) mCachePath.length()];
						if (in.read(b) != b.length)
							return true;
						p.unmarshall(b, 0, b.length);
						p.setDataPosition(0);
						p.readTypedList(mTweeterList, Tweeter.CREATOR);
						synchronized (mTweeterList) {
							HashSet<Tweeter> set = new HashSet<>(mTweeterList);
							mTweeterList.clear();
							mTweeterList.addAll(set);
							for(Tweeter t : mTweeterList)
								t.addOnChangeListener(this);
						}
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						mLoaded = true;
						p.recycle();
						StreamTools.close(in);
					}
					return true;
				}
				case SAVE: {
					OutputStream out = null;
					Parcel p = Parcel.obtain();
					try {
						out = new FileOutputStream(mCachePath);
						synchronized (mTweeterList) {
							p.writeTypedList(mTweeterList);
						}
						out.write(p.marshall());
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						p.recycle();
						StreamTools.close(out);
					}
					return true;
				}
			}
			return false;
		}

		@Override
		public void onUserlistChanged(List<TwitterEngine.StreamableTwitterEngine> engines) {
			Iterator<Tweeter> i = mTweeterList.iterator();
			a:
			while (i.hasNext()) {
				Tweeter t = i.next();
				for (TwitterEngine e : engines)
					if (e.getUserId() == t.user_id)
						continue a;
				i.remove();
				t.removeOnChangeListener(this);
			}
			a:
			for (TwitterEngine e : engines) {
				for (Tweeter t : mTweeterList)
					if (t.user_id == e.getUserId())
						continue a;
				Tweeter t = e.getTweeter();
				mTweeterList.add(t);
				t.addOnChangeListener(this);
			}
		}
	}

	public static class ForUserInfo implements Parcelable {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<ForUserInfo> CREATOR = new Parcelable.Creator<ForUserInfo>() {
			@Override
			public ForUserInfo createFromParcel(Parcel in) {
				return new ForUserInfo(in);
			}

			@Override
			public ForUserInfo[] newArray(int size) {
				return new ForUserInfo[size];
			}
		};
		public boolean following;
		public boolean follow_request_sent;

		public ForUserInfo() {
		}

		protected ForUserInfo(Parcel in) {
			following = in.readByte() != 0x00;
			follow_request_sent = in.readByte() != 0x00;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeByte((byte) (following ? 0x01 : 0x00));
			dest.writeByte((byte) (follow_request_sent ? 0x01 : 0x00));
		}
	}

	public static class InternalInformation implements Parcelable {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<InternalInformation> CREATOR = new Parcelable.Creator<InternalInformation>() {
			@Override
			public InternalInformation createFromParcel(Parcel in) {
				return new InternalInformation(in);
			}

			@Override
			public InternalInformation[] newArray(int size) {
				return new InternalInformation[size];
			}
		};
		long lastUpdated;
		boolean stub;

		public InternalInformation() {
		}

		protected InternalInformation(Parcel in) {
			lastUpdated = in.readLong();
			stub = in.readByte() != 0x00;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeLong(lastUpdated);
			dest.writeByte((byte) (stub ? 0x01 : 0x00));
		}
	}
}