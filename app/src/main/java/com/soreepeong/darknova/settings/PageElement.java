package com.soreepeong.darknova.settings;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.soreepeong.darknova.DarknovaApplication;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.TwitterEngine;

/**
 * @author Soreepeong
 */
public class PageElement implements Parcelable {
	public static final int FUNCTION_DEBUG = -1;

	/**
	 * Functions returning tweets
	 */
	public static final int FUNCTION_HOME_TIMELINE = 0;
	public static final int FUNCTION_MENTIONS = 1;
	public static final int FUNCTION_RETWEETS_OF_ME = 2;
	public static final int FUNCTION_SEARCH = 3;
	public static final int FUNCTION_LIST_TWEETS = 4;
	public static final int FUNCTION_USER_TIMELINE = 5;
	public static final int FUNCTION_USER_FAVORITES = 6;
	public static final int FUNCTION_TWEET_SINGLE = 7;

	/**
	 * Functions returning user IDs
	 */
	public static final int FUNCTION_BLOCKED_USERS = 9;
	public static final int FUNCTION_NO_RETWEET_USERS = 10;
	public static final int FUNCTION_LIST_MEMBERS = 11;
	public static final int FUNCTION_LIST_SUBSCRIBERS = 12;
	public static final int FUNCTION_USER_FOLLOWERS = 13;
	public static final int FUNCTION_USER_FRIENDS = 14;
	public static final int FUNCTION_USER_LIST_FOLLOWING = 15;
	public static final int FUNCTION_TWEET_RETWEETED_BY = 16;

	/**
	 * Functions returning lists
	 */
	public static final int FUNCTION_USER_LIST_MEMBERED = 17;

	/**
	 * Functions returning single user
	 */
	public static final int FUNCTION_USER_SINGLE = 18;

	/**
	 * Functions returning DMs
	 */
	public static final int FUNCTION_DM_RECEIVED = 19;
	public static final int FUNCTION_DM_SENT = 20;
	@SuppressWarnings("unused")
	public static final Creator<PageElement> CREATOR = new Creator<PageElement>() {
		@Override
		public PageElement createFromParcel(Parcel in) {
			return new PageElement(in);
		}

		@Override
		public PageElement[] newArray(int size) {
			return new PageElement[size];
		}
	};
	public final long twitterEngineId;
	public final int function;
	public final long id;
	public final String name;
	TwitterEngine mTwitterEngine;
	private String mUniqid;

	public PageElement(TwitterEngine twitterEngine, int function, long id, String name) {
		this.twitterEngineId = twitterEngine.getUserId();
		this.mTwitterEngine = twitterEngine;
		this.function = function;
		this.id = id;
		this.name = name;
	}

	protected PageElement(String key, SharedPreferences in) {
		twitterEngineId = in.getLong(key + ".twitterEngineId", 0);
		mTwitterEngine = TwitterEngine.get(twitterEngineId);
		function = in.getInt(key + ".function", 0);
		id = in.getLong(key + ".id", 0);
		name = in.getString(key + ".name", null);
	}

	protected PageElement(Parcel in) {
		twitterEngineId = in.readLong();
		mTwitterEngine = TwitterEngine.get(twitterEngineId);
		function = in.readInt();
		id = in.readLong();
		name = in.readString();
	}

	@Override
	public int hashCode() {
		return Long.valueOf(twitterEngineId).hashCode() ^ function ^ Long.valueOf(id).hashCode() ^ (name == null ? 0 : name.hashCode());
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof PageElement &&
				((PageElement) o).twitterEngineId == twitterEngineId &&
				((PageElement) o).function == function &&
				((PageElement) o).id == id &&
				((((PageElement) o).name != null && ((PageElement) o).name.equals(name)) ||
						(name == null && ((PageElement) o).name == null));
	}

	public String generateUniqid() {
		if (mUniqid != null)
			return mUniqid;
		mUniqid = twitterEngineId + "." + function + "." + id + "." + name;
		return mUniqid;
	}

	public boolean containsStream(TwitterEngine e) {
		return e != null && containsStream(e.getUserId());
	}

	public boolean containsStream(long user_id) {
		switch (function) {
			case FUNCTION_HOME_TIMELINE:
				return user_id == twitterEngineId;
			case FUNCTION_MENTIONS:
				return user_id == twitterEngineId;
			case FUNCTION_USER_TIMELINE:
				return user_id == id;
		}
		return false;
	}

	public boolean isHeaderView() {
		switch (function) {
			case FUNCTION_USER_SINGLE:
				return true;
			case FUNCTION_DEBUG:
				return true;
		}
		return false;
	}

	public int getHeaderType() {
		switch (function) {
			case FUNCTION_USER_SINGLE:
				return R.layout.row_header_userbig;
		}
		return -1;
	}

	public boolean isOnlyElement() {
		switch (function) {
			case FUNCTION_TWEET_SINGLE:
				return true;
			case FUNCTION_DEBUG:
				return true;
		}
		return false;
	}

	public boolean canHave(Tweet tweet) {
		switch (function) {
			case FUNCTION_HOME_TIMELINE:
				return (tweet.perUserInfo.get(twitterEngineId) != null) && (twitterEngineId == tweet.user.user_id || mTwitterEngine.getTweeter().follows(tweet.user));
			case FUNCTION_MENTIONS:
				if (tweet.entities != null)
					for (Entities.Entity e : tweet.entities.list) {
						if (e instanceof Entities.MentionsEntity)
							if (((Entities.MentionsEntity) e).id == twitterEngineId)
								return true;
					}
				return false;
			case FUNCTION_USER_TIMELINE:
				return tweet.user.user_id == id;
			case FUNCTION_USER_SINGLE:
				return false;
			case FUNCTION_SEARCH:
				return true;
		}
		return true;
	}

	public TwitterEngine getTwitterEngine() {
		if (mTwitterEngine == null && twitterEngineId != 0)
			mTwitterEngine = TwitterEngine.get(twitterEngineId);
		return mTwitterEngine;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(twitterEngineId);
		dest.writeInt(function);
		dest.writeLong(id);
		dest.writeString(name);
	}

	public void writeToPreferences(String key, SharedPreferences.Editor dest) {
		dest.putLong(key + ".twitterEngineId", twitterEngineId);
		dest.putInt(key + ".function", function);
		dest.putLong(key + ".id", id);
		dest.putString(key + ".name", name);
	}

	public ElementHeader createHeader(int viewType) {
		ElementHeader header = new ElementHeader();
		header.mId = DarknovaApplication.uniqid();
		header.mHeaderType = viewType;
		return header;
	}

	public class ElementHeader implements Comparable<ElementHeader> {

		private long mId;
		private int mHeaderType;

		public long getId() {
			return mId;
		}

		public int getHeaderType() {
			return mHeaderType;
		}

		@Override
		public int compareTo(@NonNull ElementHeader another) {
			return mId > another.mId ? 1 : (mId == another.mId ? 0 : -1);
		}

		public PageElement getElement() {
			return PageElement.this;
		}
	}
}
