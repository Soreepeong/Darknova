package com.soreepeong.darknova.twitter;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.soreepeong.darknova.tools.WeakValueHashMap;

/**
 * @author Soreepeong
 */
public class DirectMessage implements ObjectWithId {

	@SuppressWarnings("unused")
	public static final Parcelable.Creator<DirectMessage> CREATOR = new Parcelable.Creator<DirectMessage>() {
		@Override
		public DirectMessage createFromParcel(Parcel in) {
			return new DirectMessage(in);
		}

		@Override
		public DirectMessage[] newArray(int size) {
			return new DirectMessage[size];
		}
	};
	private static WeakValueHashMap<Long, DirectMessage> mMessages = new WeakValueHashMap<>();
	public InternalInformation info = new InternalInformation();
	public long id;
	public long created_at;
	public Entities entities;
	public Tweeter recipient;
	public Tweeter sender;
	public String text;

	protected DirectMessage() {
	}

	protected DirectMessage(Parcel in) {
		info = (InternalInformation) in.readValue(InternalInformation.class.getClassLoader());
		id = in.readLong();
		created_at = in.readLong();
		entities = (Entities) in.readValue(Entities.class.getClassLoader());
		recipient = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
		sender = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
		text = in.readString();
	}

	public static DirectMessage getTemporary() {
		return new DirectMessage();
	}

	public static DirectMessage getTweet(long id) {
		DirectMessage t = mMessages.get(id);
		if (t != null)
			return t;
		t = new DirectMessage();
		t.id = id;
		t.info.stub = true;
		mMessages.put(id, t);
		return t;
	}

	public static DirectMessage updateTweet(DirectMessage tweet) {
		DirectMessage t = mMessages.get(tweet.id);
		if (t == null) {
			t = tweet;
			mMessages.put(tweet.id, t);
		} else {
			synchronized (t) {
				if (!tweet.info.stub && tweet.info.lastUpdated > t.info.lastUpdated) {
					t.info = tweet.info;
					t.id = tweet.id;
					t.created_at = tweet.created_at;
					t.text = tweet.text;
					t.entities = tweet.entities;
				}
			}
		}
		return t;
	}

	@Override
	public long getId() {
		return id;
	}

	@Override
	public int compareTo(@NonNull ObjectWithId another) {
		return id < ((DirectMessage) another).id ? -1 : (id == ((DirectMessage) another).id ? 0 : 1);
	}

	@Override
	public int hashCode() {
		return (int) id;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof DirectMessage && id == ((DirectMessage) o).id;
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
		dest.writeValue(entities);
		dest.writeValue(recipient);
		dest.writeValue(sender);
		dest.writeString(text);
	}

}
