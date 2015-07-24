package com.soreepeong.darknova.settings;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import com.soreepeong.darknova.DarknovaApplication;
import com.soreepeong.darknova.services.TemplateTweetProvider;
import com.soreepeong.darknova.tools.FileTools;
import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.twitter.TwitterEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import pl.droidsonroids.gif.GifDrawable;

/**
 * Attachment for Template Tweet.
 * MP4 Parser: https://github.com/sannies/mp4parser
 *
 * @author Soreepeong
 */
public class TemplateTweetAttachment implements Parcelable {
	@SuppressWarnings("unused")
	public static final Parcelable.Creator<TemplateTweetAttachment> CREATOR = new Parcelable.Creator<TemplateTweetAttachment>() {
		@Override
		public TemplateTweetAttachment createFromParcel(Parcel in) {
			return new TemplateTweetAttachment(in);
		}

		@Override
		public TemplateTweetAttachment[] newArray(int size) {
			return new TemplateTweetAttachment[size];
		}
	};
	private static final Handler mMainHandler = new Handler(Looper.getMainLooper());
	public final File mLocalFile;
	public final Uri original_url;
	public final long template_id;
	public final long id;
	public boolean mLocalFileExists;
	public int media_type;
	public long mMediaId;
	private Thread mResolver;

	// _id, template_id, original_url, media_type

	public TemplateTweetAttachment(Uri unresolved, ContentResolver resolver, TemplateTweet template) {
		ContentValues cv = new ContentValues();
		cv.put("template_id", template_id = template.id);
		cv.put("original_url", (original_url = unresolved).toString());
		id = Long.decode(resolver.insert(TemplateTweetProvider.URI_ATTACHMENTS, cv).getLastPathSegment());
		mLocalFile = new File(resolver.getType(TemplateTweetProvider.URI_ATTACHMENTS) + "/" + id);
	}

	public TemplateTweetAttachment(Cursor cursor, ContentResolver resolver) {
		original_url = Uri.parse(cursor.getString(cursor.getColumnIndex("original_url")));
		id = cursor.getLong(cursor.getColumnIndex("_id"));
		template_id = cursor.getLong(cursor.getColumnIndex("template_id"));
		media_type = cursor.getInt(cursor.getColumnIndex("media_type"));
		mLocalFile = new File(resolver.getType(TemplateTweetProvider.URI_ATTACHMENTS) + "/" + id);
		mLocalFileExists = mLocalFile.exists();
	}

	protected TemplateTweetAttachment(Parcel in) {
		original_url = Uri.parse(in.readString());
		id = in.readLong();
		template_id = in.readLong();
		media_type = in.readInt();
		mLocalFile = new File(in.readString());
		mLocalFileExists = mLocalFile.exists();
	}

	public void updateSelf(ContentResolver resolver) {
		ContentValues cv = new ContentValues();
		cv.put("media_type", media_type);
		resolver.update(TemplateTweetProvider.URI_ATTACHMENTS, cv, "_id=?", new String[]{Long.toString(id)});
	}

	public void removeSelf(ContentResolver resolver) {
		resolver.delete(TemplateTweetProvider.URI_ATTACHMENTS, "_id=?", new String[]{Long.toString(id)});
	}

	public void resolve(final Context context, final AttachmentResolveResult resolveResult) {
		if (mResolver != null || (mLocalFile.exists() && mLocalFile.length() != 0)) {
			android.util.Log.wtf("mLocalFile", "exists?");
			return;
		}
		mResolver = new Thread() {
			@Override
			public void run() {
				InputStream in = null;
				OutputStream out = null;
				try {
					in = context.getContentResolver().openInputStream(original_url);
					out = new FileOutputStream(mLocalFile);
					StreamTools.passthroughStreams(in, out);
					media_type = 0;
					if (null != ThumbnailUtils.createVideoThumbnail(mLocalFile.getAbsolutePath(), MediaStore.Images.Thumbnails.MICRO_KIND))
						media_type = TemplateTweetProvider.MEDIA_TYPE_VIDEO;
					else {
						GifDrawable g = null;
						try {
							g = new GifDrawable(mLocalFile.getAbsolutePath());
							if (g.getNumberOfFrames() > 1)
								media_type = TemplateTweetProvider.MEDIA_TYPE_GIF;
						} catch (Exception e) {
							if (g != null)
								g.recycle();
						}
					}
					if (media_type == 0)
						media_type = TemplateTweetProvider.MEDIA_TYPE_IMAGE;
					if (resolveResult != null)
						mMainHandler.post(new Runnable() {
							@Override
							public void run() {
								resolveResult.onTypeResolved(TemplateTweetAttachment.this);
							}
						});
					if (media_type == TemplateTweetProvider.MEDIA_TYPE_VIDEO) {
						if (mLocalFile.length() > TwitterEngine.MAX_VIDEO_MEDIA_SIZE) {
							DarknovaApplication.showToast("Video file too big (>15MB)");
							throw new IOException("File too big");
						}
					} else if (media_type == TemplateTweetProvider.MEDIA_TYPE_GIF) {
						if (mLocalFile.length() > TwitterEngine.MAX_IMAGE_MEDIA_SIZE) {
							DarknovaApplication.showToast("Animated GIF file too big (>5MB)");
							throw new IOException("File too big");
						}
					} else {
						long maxMediaSize = TwitterEngine.getConfigurationLong("photo_size_limit", 3145728);
						if (mLocalFile.length() > maxMediaSize) {
							FileTools.resizeImage(mLocalFile, maxMediaSize);
						}
					}
					mLocalFileExists = true;
					updateSelf(context.getContentResolver());
				} catch (final Throwable e) {
					if (!mLocalFile.delete())
						mLocalFile.deleteOnExit();
					if (resolveResult != null)
						mMainHandler.post(new Runnable() {
							@Override
							public void run() {
								resolveResult.onResolveFailed(TemplateTweetAttachment.this, e);
							}
						});
					e.printStackTrace();
				} finally {
					StreamTools.close(in);
					StreamTools.close(out);
					if (resolveResult != null)
						if (mLocalFile.exists())
							mMainHandler.post(new Runnable() {
								@Override
								public void run() {
									resolveResult.onResolved(TemplateTweetAttachment.this);
								}
							});
				}
			}
		};
		mResolver.start();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(original_url.toString());
		dest.writeLong(id);
		dest.writeLong(template_id);
		dest.writeInt(media_type);
		dest.writeString(mLocalFile.getAbsolutePath());
	}

	public interface AttachmentResolveResult {
		void onResolved(TemplateTweetAttachment attachment);

		void onTypeResolved(TemplateTweetAttachment attachment);

		void onResolveFailed(TemplateTweetAttachment attachment, Throwable e);
	}
}
