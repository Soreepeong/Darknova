package com.soreepeong.darknova.twitter;

import android.os.Parcel;
import android.os.Parcelable;

import com.soreepeong.darknova.core.WeakValueHashMap;

import java.util.HashMap;

/**
 * Created by Soreepeong on 2015-04-28.
 */
public class Tweet implements Parcelable, Comparable<Tweet>{

	private static WeakValueHashMap<Long, Tweet> mTweets = new WeakValueHashMap<>();

	public InternalInformation info = new InternalInformation();
	public long id;
	public long created_at;
	public String text;
	public int retweet_count;
	public int favourites_count;
	public String source;
	public boolean possibly_sensitive;
	public boolean removed;
	public Tweeter user;
	public Tweet retweeted_status;
	public Entities entities;
	public Tweet in_reply_to_status;
	public Tweeter in_reply_to_user;
	public HashMap<Long, ForUserInfo> perUserInfo;

	@Override
	public boolean equals(Object o) {
		return o instanceof Tweet &&  id == ((Tweet)o).id;
	}

	@Override
	public int compareTo(Tweet another) {
		return id < another.id ? -1 : (id == another.id ? 0 : 1);
	}

	@Override
	public int hashCode() {
		return (int) id;
	}

	public void addForUserInfo(long user_id, boolean favorited, boolean retweeted){
		ForUserInfo ui = new ForUserInfo();
		ui.favorited = favorited; ui.retweeted = retweeted;
		perUserInfo.put(user_id, ui);
	}

	public boolean favoriteExists(){
		for(ForUserInfo ui : perUserInfo.values())
			if(ui.favorited) return true;
		return false;
	}

	public boolean retweetExists(){
		for(ForUserInfo ui : perUserInfo.values())
			if(ui.retweeted) return true;
		return false;
	}


	public static class InternalInformation implements Parcelable{
		public long lastUpdated;
		public boolean stub;

		public InternalInformation(){}

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
	}

	public static Tweet getExistingTweet(long id){
		return mTweets.get(id);
	}

	public static Tweet getTweet(long id){
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
			if(tweet.info.stub == false && tweet.info.lastUpdated > t.info.lastUpdated){
				t.info = tweet.info;
				t.id = tweet.id;
				t.created_at = tweet.created_at;
				t.text = tweet.text;
				t.retweet_count = tweet.retweet_count;
				t.favourites_count = tweet.favourites_count;
				t.source = tweet.source;
				t.possibly_sensitive = tweet.possibly_sensitive;
				t.removed = tweet.removed;
				t.user = tweet.user;
				t.retweeted_status = tweet.retweeted_status;
				t.entities = tweet.entities;
				t.in_reply_to_status = tweet.in_reply_to_status;
				t.in_reply_to_user = tweet.in_reply_to_user;
				t.perUserInfo = tweet.perUserInfo;
			}
		}
		return t;
	}

	public static Tweet getTemporaryTweet(){
		return new Tweet();
	}

	private Tweet(){
		perUserInfo = new HashMap<>();
	}

	protected Tweet(Parcel in) {
		info = (InternalInformation) in.readValue(InternalInformation.class.getClassLoader());
		id = in.readLong();
		created_at = in.readLong();
		text = in.readString();
		retweet_count = in.readInt();
		favourites_count = in.readInt();
		source = in.readString();
		possibly_sensitive = in.readByte() != 0x00;
		removed = in.readByte() != 0x00;
		user = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
		retweeted_status = (Tweet) in.readValue(Tweet.class.getClassLoader());
		entities = (Entities) in.readValue(Entities.class.getClassLoader());
		in_reply_to_status = (Tweet) in.readValue(Tweet.class.getClassLoader());
		in_reply_to_user = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
		perUserInfo = (HashMap) in.readHashMap(ForUserInfo.class.getClassLoader());
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
		dest.writeInt(favourites_count);
		dest.writeString(source);
		dest.writeByte((byte) (possibly_sensitive ? 0x01 : 0x00));
		dest.writeByte((byte) (removed ? 0x01 : 0x00));
		dest.writeValue(user);
		dest.writeValue(retweeted_status);
		dest.writeValue(entities);
		dest.writeValue(in_reply_to_status);
		dest.writeValue(in_reply_to_user);
		dest.writeMap(perUserInfo);
	}

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

	public static class ForUserInfo implements Parcelable{
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
	}
}
