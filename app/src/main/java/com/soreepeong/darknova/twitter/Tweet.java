package com.soreepeong.darknova.twitter;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.soreepeong.darknova.tools.WeakValueHashMap;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Twitter tweet
 *
 * @author Soreepeong
 */
public class Tweet implements ObjectWithId, Parcelable {

	private static WeakValueHashMap<Long, Tweet> mTweets = new WeakValueHashMap<>();
	public final ArrayList<Long> accessedBy = new ArrayList<>();
	public final HashMap<Long, ForUserInfo> perUserInfo = new HashMap<>();
	public InternalInformation info = new InternalInformation();
	public long id;
	public long created_at;
	public String text;
	public int retweet_count;
	public int favorite_count;
	public String source;
	public boolean possibly_sensitive;
	public boolean removed;
	public Tweeter user;
	public Tweet retweeted_status;
	public Entities entities;
	public Tweet in_reply_to_status;
	public Tweeter in_reply_to_user;
	@SuppressWarnings("unused")
	public static final Parcelable.Creator<Tweet> CREATOR = new Parcelable.Creator<Tweet>() {
		@Override
		public Tweet createFromParcel(Parcel in) {
			return updateTweet(new Tweet(in));
		}

		@Override
		public Tweet[] newArray(int size) {
			return new Tweet[size];
		}
	};

	private Tweet() {
	}

	protected Tweet(Parcel in) {
		info = (InternalInformation) in.readValue(InternalInformation.class.getClassLoader());
		id = in.readLong();
		created_at = in.readLong();
		text = in.readString();
		retweet_count = in.readInt();
		favorite_count = in.readInt();
		source = in.readString();
		possibly_sensitive = in.readByte() != 0x00;
		removed = in.readByte() != 0x00;
		user = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
		retweeted_status = (Tweet) in.readValue(Tweet.class.getClassLoader());
		entities = (Entities) in.readValue(Entities.class.getClassLoader());
		in_reply_to_status = Tweet.getTweet(in.readLong());
		in_reply_to_user = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
		in.readMap(perUserInfo, ForUserInfo.class.getClassLoader());
		in.readList(accessedBy, Long.class.getClassLoader());
	}

	public static Tweet getTweet(long id){
		if(id == 0)
			return null;
		Tweet t = mTweets.get(id);
		if(t != null)
			return t;
		t = new Tweet();
		t.id = id;
		t.info.stub = true;
		mTweets.put(id, t);
		return t;
	}

	public static Tweet updateTweet(Tweet tweet){
		Tweet t = mTweets.get(tweet.id);
		if(t == null){
			t = tweet;
			mTweets.put(tweet.id, t);
		}else{
			synchronized (t) {
				t.removed |= tweet.removed;
				if (!tweet.info.stub && tweet.info.lastUpdated > t.info.lastUpdated) {
					t.info = tweet.info;
					t.id = tweet.id;
					t.created_at = tweet.created_at;
					t.text = tweet.text;
					t.retweet_count = tweet.retweet_count;
					t.favorite_count = tweet.favorite_count;
					t.source = tweet.source;
					t.possibly_sensitive = tweet.possibly_sensitive;
					t.user = tweet.user;
					t.retweeted_status = tweet.retweeted_status;
					t.entities = tweet.entities;
					t.in_reply_to_status = tweet.in_reply_to_status;
					t.in_reply_to_user = tweet.in_reply_to_user;
					t.perUserInfo.putAll(tweet.perUserInfo);
					// <duplication removals>
					t.accessedBy.removeAll(tweet.accessedBy);
					t.accessedBy.addAll(tweet.accessedBy);
					// </duplication removals>
				}
			}
		}
		return t;
	}

	public static Tweet getTemporary() {
		return new Tweet();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Tweet && id == ((Tweet) o).id;
	}

	@Override
	public int compareTo(@NonNull ObjectWithId another) {
		return id < ((Tweet) another).id ? -1 : (id == ((Tweet) another).id ? 0 : 1);
	}

	@Override
	public int hashCode() {
		return (int) id;
	}

	public void addForUserInfo(long user_id, boolean favorited, boolean retweeted) {
		ForUserInfo ui = new ForUserInfo();
		ui.favorited = favorited;
		ui.retweeted = retweeted;
		perUserInfo.put(user_id, ui);
	}

	public boolean favoriteExists() {
		for (ForUserInfo ui : perUserInfo.values())
			if (ui.favorited) return true;
		return false;
	}

	public boolean retweetExists() {
		for (ForUserInfo ui : perUserInfo.values())
			if (ui.retweeted) return true;
		return false;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeValue(info);
		dest.writeLong(id);
		dest.writeLong(created_at);
		dest.writeString(text);
		dest.writeInt(retweet_count);
		dest.writeInt(favorite_count);
		dest.writeString(source);
		dest.writeByte((byte) (possibly_sensitive ? 0x01 : 0x00));
		dest.writeByte((byte) (removed ? 0x01 : 0x00));
		dest.writeValue(user);
		dest.writeValue(retweeted_status);
		dest.writeValue(entities);
		dest.writeLong(in_reply_to_status == null ? 0 : in_reply_to_status.id);
		dest.writeValue(in_reply_to_user);
		dest.writeMap(perUserInfo);
		dest.writeList(accessedBy);
	}

	@Override
	public long getId() {
		return id;
	}

	@Override
	public String toString() {
		return "@" + (user != null ? user.screen_name : "(null)") + ": " + text;
	}

	public static class ForUserInfo implements Parcelable{
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
		public boolean favorited;
		public boolean retweeted;

		public ForUserInfo(){}

		protected ForUserInfo(Parcel in) {
			favorited = in.readByte() != 0x00;
			retweeted = in.readByte() != 0x00;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeByte((byte) (favorited ? 0x01 : 0x00));
			dest.writeByte((byte) (retweeted ? 0x01 : 0x00));
		}
	}
}
