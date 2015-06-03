package com.soreepeong.darknova.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Soreepeong on 2015-05-31.
 */
public class NewTweet implements Parcelable {

	private static final ArrayList<NewTweet> mNewTweets = new ArrayList<>();
	private static long mLastNewTweetLoad;
	private static SharedPreferences mNewTweetPreference;

	private static void refreshNewTweets(){
		synchronized (mNewTweets){
			if(mNewTweetPreference.getLong("new_tweet.lastedit", 1) != mLastNewTweetLoad){
				mNewTweets.clear();
				for(int i = 0, i_ = mNewTweetPreference.getInt("new_tweet.length", 0); i<i_; i++)
					mNewTweets.add(new NewTweet("new_tweet."+i, mNewTweetPreference));
				mLastNewTweetLoad = mNewTweetPreference.getLong("new_tweet.lastedit", 1);
			}
		}
	}

	public static void loadNewTweets(Context context){
		if(mLastNewTweetLoad != 0)
			return;
		mNewTweetPreference = context.getSharedPreferences("NEW_TWEETS", Context.MODE_MULTI_PROCESS);
		mNewTweetPreference.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
				refreshNewTweets();
			}
		});
		refreshNewTweets();
	}

	public static void saveNewTweets(){
		synchronized (mNewTweets){
			SharedPreferences.Editor editor = mNewTweetPreference.edit();
			editor.putInt("new_tweet.length", mNewTweets.size());
			for(int i = 0, i_ = mNewTweets.size(); i<i_; i++)
				mNewTweets.get(i).writeToPreferences("new_tweet."+i, editor);
			mNewTweetPreference.getLong("new_tweet.lastedit", mLastNewTweetLoad = System.currentTimeMillis());
			editor.apply();
		}
	}

	public static final int TYPE_ONESHOT = 1;
	public static final int TYPE_SCHEDULED = 2; // startTime
	public static final int TYPE_PERIODIC = 3; // Interval, startTime, endTime
	public static final int TYPE_REPLY = 4; // startTime, endTime, Trigger, isRegex

	public int mType;
	public long mCreatedAt;
	public boolean mUserEnabled;
	public boolean mRemoveAfter;
	public int mInterval;
	public long mStartTime;
	public long mEndTime;
	public String mTriggerPattern;
	public boolean mUseRegex;
	public int mSelectionStart;
	public int mSelectionEnd;

	public String mText;
	public long mInReplyTo;
	public float mLatitude;
	public float mLongitude;
	public boolean mUseCoordinates;
	public final List<Long> mUserIdList = new ArrayList<>();
	public final List<String> mMediaTempPathList = new ArrayList<>();

	public void writeToPreferences(String key, SharedPreferences.Editor dest){
		dest.putInt(key + ".mType", mType);
		dest.putLong(key + ".mCreatedAt", mCreatedAt);
		dest.putBoolean(key + ".mUserEnabled", mUserEnabled);
		dest.putBoolean(key + ".mRemoveAfter", mRemoveAfter);
		dest.putInt(key + ".mInterval", mInterval);
		dest.putInt(key + ".mSelectionStart", mSelectionStart);
		dest.putInt(key + ".mSelectionEnd", mSelectionEnd);
		dest.putLong(key + ".mStartTime", mStartTime);
		dest.putLong(key + ".mEndTime", mEndTime);
		dest.putString(key + ".mTriggerPattern", mTriggerPattern);
		dest.putBoolean(key + ".mUseRegex", mUseRegex);
		dest.putString(key + ".mText", mText);
		dest.putLong(key + ".mInReplyTo", mInReplyTo);
		dest.putFloat(key + ".mLatitude", mLatitude);
		dest.putFloat(key + ".mLongitude", mLongitude);
		dest.putBoolean(key + ".mUseCoordinates", mUseCoordinates);
		dest.putInt(key + ".mUserIdList.length", mUserIdList.size());
		for(int i=0, i_=mUserIdList.size(); i<i_; i++)
			dest.putLong(key + ".mUserIdList." + i, mUserIdList.get(i));
		dest.putInt(key + ".mMediaTempPathList.length", mMediaTempPathList.size());
		for(int i=0, i_=mMediaTempPathList.size(); i<i_; i++)
			dest.putString(key+".mMediaTempPathList."+i, mMediaTempPathList.get(i));
	}

	public NewTweet(){}

	public NewTweet(String key, SharedPreferences in){
		mType = in.getInt(key + ".mType", 0);
		mCreatedAt = in.getLong(key + ".mCreatedAt", 0);
		mUserEnabled = in.getBoolean(key + ".mUserEnabled", false);
		mRemoveAfter = in.getBoolean(key + ".mRemoveAfter", false);
		mInterval = in.getInt(key + ".mInterval", 0);
		mSelectionStart = in.getInt(key + ".mSelectionStart", 0);
		mSelectionEnd = in.getInt(key + ".mSelectionEnd", 0);
		mStartTime = in.getLong(key + ".mStartTime", 0);
		mEndTime = in.getLong(key + ".mEndTime", 0);
		mTriggerPattern = in.getString(key + ".mTriggerPattern", null);
		mUseRegex = in.getBoolean(key + ".mUseRegex", false);
		mText = in.getString(key + ".mText", null);
		mInReplyTo = in.getLong(key + ".mInReplyTo", 0);
		mLatitude = in.getFloat(key + ".mLatitude", 0);
		mLongitude = in.getFloat(key + ".mLongitude", 0);
		mUseCoordinates = in.getBoolean(key + ".mUseCoordinates", false);
		for(int i=0, i_=in.getInt(key+".mUserIdList.length", 0); i<i_; i++)
			mUserIdList.add(in.getLong(key+".mUserIdList."+i, 0));
		for(int i=0, i_=in.getInt(key+".mMediaTempPathList.length", 0); i<i_; i++)
			mMediaTempPathList.add(in.getString(key+".mMediaTempPathList."+i, null));
	}

	protected NewTweet(Parcel in) {
		mType = in.readInt();
		mCreatedAt = in.readLong();
		mUserEnabled = in.readByte() != 0x00;
		mRemoveAfter = in.readByte() != 0x00;
		mInterval = in.readInt();
		mSelectionStart = in.readInt();
		mSelectionEnd = in.readInt();
		mStartTime = in.readLong();
		mEndTime = in.readLong();
		mTriggerPattern = in.readString();
		mUseRegex = in.readByte() != 0x00;
		mText = in.readString();
		mInReplyTo = in.readLong();
		mLatitude = in.readFloat();
		mLongitude = in.readFloat();
		mUseCoordinates = in.readByte() != 0x00;
		in.readList(mUserIdList, Long.class.getClassLoader());
		in.readList(mMediaTempPathList, String.class.getClassLoader());
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(mType);
		dest.writeLong(mCreatedAt);
		dest.writeByte((byte) (mUserEnabled ? 0x01 : 0x00));
		dest.writeByte((byte) (mRemoveAfter ? 0x01 : 0x00));
		dest.writeInt(mInterval);
		dest.writeInt(mSelectionStart);
		dest.writeInt(mSelectionEnd);
		dest.writeLong(mStartTime);
		dest.writeLong(mEndTime);
		dest.writeString(mTriggerPattern);
		dest.writeByte((byte) (mUseRegex ? 0x01 : 0x00));
		dest.writeString(mText);
		dest.writeLong(mInReplyTo);
		dest.writeFloat(mLatitude);
		dest.writeFloat(mLongitude);
		dest.writeByte((byte) (mUseCoordinates ? 0x01 : 0x00));
		dest.writeList(mUserIdList);
		dest.writeList(mMediaTempPathList);
	}

	@SuppressWarnings("unused")
	public static final Parcelable.Creator<NewTweet> CREATOR = new Parcelable.Creator<NewTweet>() {
		@Override
		public NewTweet createFromParcel(Parcel in) {
			return new NewTweet(in);
		}

		@Override
		public NewTweet[] newArray(int size) {
			return new NewTweet[size];
		}
	};
}
