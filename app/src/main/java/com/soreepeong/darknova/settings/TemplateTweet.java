package com.soreepeong.darknova.settings;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;

import com.soreepeong.darknova.services.TemplateTweetProvider;
import com.soreepeong.darknova.twitter.TwitterEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tweet being made.
 *
 * @author Soreepeong
 */
public class TemplateTweet implements Parcelable {

	@SuppressWarnings("unused")
	public static final Parcelable.Creator<TemplateTweet> CREATOR = new Parcelable.Creator<TemplateTweet>() {
		@Override
		public TemplateTweet createFromParcel(Parcel in) {
			return new TemplateTweet(in);
		}

		@Override
		public TemplateTweet[] newArray(int size) {
			return new TemplateTweet[size];
		}
	};
	public final List<Long> mUserIdList = new ArrayList<>();
	public final List<TemplateTweetAttachment> mAttachments = new ArrayList<>();
	public final long id;
	public final long created_at;
	public int type;
	public boolean enabled;
	public boolean remove_after;
	public int interval;
	public long time_start;
	public long time_end;
	public String trigger_pattern;
	public boolean trigger_use_regex;
	public int selection_start;
	public int selection_end;
	public String text;
	public long in_reply_to_id;
	public float latitude;
	public float longitude;
	public boolean use_coordinates, autoresolve_coordinates;
	private boolean mIsPosting;

	public TemplateTweet(ContentResolver resolver, List<Long> userIdList) {
		if (userIdList != null)
			mUserIdList.addAll(userIdList);

		ContentValues cv = new ContentValues();
		cv.put("created_at", created_at = System.currentTimeMillis());
		cv.put("start_time", time_start = System.currentTimeMillis());
		cv.put("end_time", time_end = System.currentTimeMillis());
		cv.put("type", type = TemplateTweetProvider.TEMPLATE_TYPE_ONESHOT);
		cv.put("remove_after", remove_after = true);
		cv.put("enabled", enabled = false);
		id = Long.decode(resolver.insert(TemplateTweetProvider.URI_TEMPLATES, cv).getLastPathSegment());

		cv.clear();
		cv.put("template_id", id);
		for (long user_id : mUserIdList) {
			cv.put("user_id", user_id);
			resolver.insert(TemplateTweetProvider.URI_FROM_USERS, cv);
		}
	}

	public TemplateTweet(long id, ContentResolver resolver) {
		Cursor c = resolver.query(TemplateTweetProvider.URI_TEMPLATES, null, "id=?", new String[]{Long.toString(id)}, null);
		if (!c.moveToFirst())
			throw new RuntimeException("No such ID exists");
		this.id = id;
		type = c.getInt(c.getColumnIndex("type"));
		created_at = c.getLong(c.getColumnIndex("created_at"));
		enabled = c.getInt(c.getColumnIndex("enabled")) != 0;
		remove_after = c.getInt(c.getColumnIndex("remove_after")) != 0;
		interval = c.getInt(c.getColumnIndex("interval"));
		selection_start = c.getInt(c.getColumnIndex("selection_start"));
		selection_end = c.getInt(c.getColumnIndex("selection_end"));
		time_start = c.getLong(c.getColumnIndex("start_time"));
		time_end = c.getLong(c.getColumnIndex("end_time"));
		trigger_pattern = c.getString(c.getColumnIndex("trigger_pattern"));
		trigger_use_regex = c.getInt(c.getColumnIndex("use_regex")) != 0;
		text = c.getString(c.getColumnIndex("text"));
		in_reply_to_id = c.getLong(c.getColumnIndex("in_reply_to"));
		latitude = c.getFloat(c.getColumnIndex("latitude"));
		longitude = c.getFloat(c.getColumnIndex("longitude"));
		use_coordinates = c.getInt(c.getColumnIndex("use_coordinates")) != 0;
		autoresolve_coordinates = c.getInt(c.getColumnIndex("autoresolve_coordinates")) != 0;
		c.close();

		c = resolver.query(TemplateTweetProvider.URI_FROM_USERS, null, "template_id=?", new String[]{Long.toString(id)}, null);
		if (c.moveToFirst())
			do {
				mUserIdList.add(c.getLong(c.getColumnIndex("user_id")));
			} while (c.moveToNext());
		c.close();

		c = resolver.query(TemplateTweetProvider.URI_ATTACHMENTS, null, "template_id=?", new String[]{Long.toString(id)}, null);
		if (c.moveToFirst())
			do {
				mAttachments.add(new TemplateTweetAttachment(c, resolver));
			} while (c.moveToNext());
		c.close();
	}

	public TemplateTweet(Cursor c, ContentResolver resolver) {
		id = c.getLong(c.getColumnIndex("_id"));
		type = c.getInt(c.getColumnIndex("type"));
		created_at = c.getLong(c.getColumnIndex("created_at"));
		enabled = c.getInt(c.getColumnIndex("enabled")) != 0;
		remove_after = c.getInt(c.getColumnIndex("remove_after")) != 0;
		interval = c.getInt(c.getColumnIndex("interval"));
		selection_start = c.getInt(c.getColumnIndex("selection_start"));
		selection_end = c.getInt(c.getColumnIndex("selection_end"));
		time_start = c.getLong(c.getColumnIndex("start_time"));
		time_end = c.getLong(c.getColumnIndex("end_time"));
		trigger_pattern = c.getString(c.getColumnIndex("trigger_pattern"));
		trigger_use_regex = c.getInt(c.getColumnIndex("use_regex")) != 0;
		text = c.getString(c.getColumnIndex("text"));
		in_reply_to_id = c.getLong(c.getColumnIndex("in_reply_to"));
		latitude = c.getFloat(c.getColumnIndex("latitude"));
		longitude = c.getFloat(c.getColumnIndex("longitude"));
		use_coordinates = c.getInt(c.getColumnIndex("use_coordinates")) != 0;
		autoresolve_coordinates = c.getInt(c.getColumnIndex("autoresolve_coordinates")) != 0;

		c = resolver.query(TemplateTweetProvider.URI_FROM_USERS, null, "template_id=?", new String[]{Long.toString(id)}, null);
		if (c.moveToFirst())
			do {
				mUserIdList.add(c.getLong(c.getColumnIndex("user_id")));
			} while (c.moveToNext());
		c.close();

		c = resolver.query(TemplateTweetProvider.URI_ATTACHMENTS, null, "template_id=?", new String[]{Long.toString(id)}, null);
		if (c.moveToFirst())
			do {
				mAttachments.add(new TemplateTweetAttachment(c, resolver));
			} while (c.moveToNext());
		c.close();
	}

	protected TemplateTweet(Parcel in) {
		id = in.readLong();
		type = in.readInt();
		created_at = in.readLong();
		enabled = in.readByte() != 0x00;
		remove_after = in.readByte() != 0x00;
		interval = in.readInt();
		selection_start = in.readInt();
		selection_end = in.readInt();
		time_start = in.readLong();
		time_end = in.readLong();
		trigger_pattern = in.readString();
		trigger_use_regex = in.readByte() != 0x00;
		text = in.readString();
		in_reply_to_id = in.readLong();
		latitude = in.readFloat();
		longitude = in.readFloat();
		use_coordinates = in.readByte() != 0x00;
		autoresolve_coordinates = in.readByte() != 0;
		in.readList(mUserIdList, Long.class.getClassLoader());
		in.readList(mAttachments, TemplateTweetAttachment.class.getClassLoader());
	}

	public void removeSelf(ContentResolver resolver) {
		resolver.delete(TemplateTweetProvider.URI_TEMPLATES, "_id=?", new String[]{Long.toString(id)});
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TemplateTweet && ((TemplateTweet) o).id == id;
	}

	public void updateSelf(ContentResolver resolver) {
		ContentValues cv = new ContentValues();
		cv.put("type", type);
		cv.put("enabled", enabled);
		cv.put("remove_after", remove_after);
		cv.put("interval", interval);
		cv.put("selection_start", selection_start);
		cv.put("selection_end", selection_end);
		cv.put("start_time", time_start);
		cv.put("end_time", time_end);
		cv.put("trigger_pattern", trigger_pattern);
		cv.put("use_regex", trigger_use_regex);
		cv.put("text", text);
		cv.put("in_reply_to", in_reply_to_id);
		cv.put("latitude", latitude);
		cv.put("longitude", longitude);
		cv.put("use_coordinates", use_coordinates);
		cv.put("autoresolve_coordinates", autoresolve_coordinates);
		resolver.update(TemplateTweetProvider.URI_TEMPLATES, cv, "_id=?", new String[]{Long.toString(id)});
		resolver.delete(TemplateTweetProvider.URI_FROM_USERS, "template_id=?", new String[]{Long.toString(id)});

		cv.clear();
		cv.put("template_id", id);
		for (long user_id : mUserIdList) {
			cv.put("user_id", user_id);
			resolver.insert(TemplateTweetProvider.URI_FROM_USERS, cv);
		}
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(id);
		dest.writeInt(type);
		dest.writeLong(created_at);
		dest.writeByte((byte) (enabled ? 0x01 : 0x00));
		dest.writeByte((byte) (remove_after ? 0x01 : 0x00));
		dest.writeInt(interval);
		dest.writeInt(selection_start);
		dest.writeInt(selection_end);
		dest.writeLong(time_start);
		dest.writeLong(time_end);
		dest.writeString(trigger_pattern);
		dest.writeByte((byte) (trigger_use_regex ? 0x01 : 0x00));
		dest.writeString(text);
		dest.writeLong(in_reply_to_id);
		dest.writeFloat(latitude);
		dest.writeFloat(longitude);
		dest.writeByte((byte) (use_coordinates ? 0x01 : 0x00));
		dest.writeByte((byte) (autoresolve_coordinates ? 0x01 : 0x00));
		dest.writeList(mUserIdList);
		dest.writeList(mAttachments);
	}

	public void post(final OnTweetListener listener, Handler handler) {
		final int CHUNK_SIZE = 1048576;
		mIsPosting = true;
		try {
			for (long user_id : mUserIdList) {
				TwitterEngine e = TwitterEngine.get(user_id);
				if (e == null)
					throw new RuntimeException("No user exists");
				List<Long> mediaIds = Collections.synchronizedList(new ArrayList<Long>());
				for (TemplateTweetAttachment a : mAttachments) {
					if (a.mMediaId == 0) {
						if (a.media_type == TemplateTweetProvider.MEDIA_TYPE_VIDEO) {
							long length = a.mLocalFile.length();
							android.util.Log.d("AttachmentUploader", "INIT");
							a.mMediaId = e.uploadMediaChunkInit(length, "video/mp4", mUserIdList);
							android.util.Log.d("AttachmentUploader", "INIT " + a.mMediaId);
							for (int i = 0; i * CHUNK_SIZE < length; i++) {
								android.util.Log.d("AttachmentUploader", "Chunk " + i + ": START (" + i * CHUNK_SIZE + "~" + Math.min((i + 1) * CHUNK_SIZE, length) + ") " + (Math.min((i + 1) * CHUNK_SIZE, length) - i * CHUNK_SIZE));
								e.uploadMediaChunkAppend(a.mMediaId, i, a.mLocalFile, i * CHUNK_SIZE, Math.min((i + 1) * CHUNK_SIZE, length));
								android.util.Log.d("AttachmentUploader", "Chunk " + i + ": END");
							}
							a.mMediaId = e.uploadMediaFinalize(a.mMediaId);
							android.util.Log.d("AttachmentUploader", "FIN " + a.mMediaId);
						} else
							a.mMediaId = e.uploadMedia(a.mLocalFile, mUserIdList);
					}
					mediaIds.add(a.mMediaId);
				}
				e.postTweet(text, in_reply_to_id, latitude, longitude, use_coordinates, mediaIds.isEmpty() ? null : mediaIds);
			}
			handler.post(new Runnable() {
				@Override
				public void run() {
					listener.onTweetSucceed(TemplateTweet.this);
				}
			});
		} catch (final Throwable t) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					listener.onTweetFail(TemplateTweet.this, t);
				}
			});
		} finally {
			mIsPosting = false;
		}
	}

	public boolean isPosting() {
		return mIsPosting;
	}

	public interface OnTweetListener {
		void onTweetSucceed(TemplateTweet template);

		void onTweetFail(TemplateTweet template, Throwable why);
	}
}
