package com.soreepeong.darknova.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import com.soreepeong.darknova.DarknovaApplication;
import com.soreepeong.darknova.tools.FileTools;
import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.TwitterEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.WeakHashMap;

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
	private static final WeakHashMap<TemplateTweetAttachment, File> mFileMap = new WeakHashMap<>();
	private static File mAttachmentStorage;
	public final long mRuntimeId = DarknovaApplication.uniqid();
	public Uri mUnresolvedUrl;
	public boolean mTypeResolved;
	public String mLocalPath;
	public boolean mIsImageMedia;
	public long media_id;
	private Thread mResolver;

	public TemplateTweetAttachment(Uri unresolved) {
		mUnresolvedUrl = unresolved;
	}

	public TemplateTweetAttachment(String localPath, boolean isImageMedia) {
		mIsImageMedia = isImageMedia;
		mLocalPath = localPath;
		if (localPath != null)
			mFileMap.put(this, new File(localPath));
	}

	protected TemplateTweetAttachment(String key, SharedPreferences in) {
		mLocalPath = in.getString(key + ".mLocalPath", null);
		mIsImageMedia = in.getBoolean(key + ".mIsImageMedia", false);
		media_id = in.getLong(key + ".media_id", 0);
		String unresolved = in.getString(key + ".mUnresolvedUrl", null);
		mUnresolvedUrl = unresolved == null || unresolved.isEmpty() ? null : Uri.parse(unresolved);
		if (mLocalPath != null)
			mFileMap.put(this, new File(mLocalPath));
	}

	protected TemplateTweetAttachment(Parcel in) {
		if (in.readByte() == 1) {
			mLocalPath = in.readString();
			mFileMap.put(this, new File(mLocalPath));
		}
		mIsImageMedia = in.readByte() != 0x00;
		media_id = in.readLong();
		if (mLocalPath != null)
			mFileMap.put(this, new File(mLocalPath));
		if (in.readByte() == 1)
			mUnresolvedUrl = Uri.parse(in.readString());
	}

	public static void initialize(File mStorage) {
		mAttachmentStorage = mStorage;
		mAttachmentStorage.mkdirs();
		clearUnusedFiles();
	}

	public static void removeUnreferencedFile(File f, TemplateTweetAttachment ignore) {
		if (f == null)
			return;
		synchronized (mFileMap) {
			if (mFileMap.containsValue(f)) {
				if (ignore == null)
					return;
				File f2 = mFileMap.remove(ignore);
				if (mFileMap.containsValue(f)) {
					mFileMap.put(ignore, f2);
					return;
				}
			} else if (f.getName().endsWith("_resizing")) {
				File f_orig = new File(f.getAbsolutePath().substring(0, f.getAbsolutePath().length() - 9));
				if (mFileMap.containsValue(f_orig)) {
					if (ignore == null)
						return;
					File f2 = mFileMap.remove(ignore);
					if (mFileMap.containsValue(f_orig)) {
						mFileMap.put(ignore, f2);
						return;
					}
				}
			}
		}
		if (!f.delete())
			f.deleteOnExit();
		android.util.Log.d("Darknova", "Unused attachment removal: " + f.getName());
	}

	public static void clearUnusedFiles() {
		new Thread() {
			@Override
			public void run() {
				File[] files = mAttachmentStorage.listFiles();
				if (files == null)
					return;
				for (File f : files)
					removeUnreferencedFile(f, null);
			}
		}.start();
	}

	private static File generateRandomAttachmentFileName() {
		while (true) {
			File f = new File(mAttachmentStorage, StringTools.getSafeRandomString(32));
			if (!f.exists())
				return f;
		}
	}

	public void resolve(final Context context, final AttachmentResolveResult resolveResult) {
		if (mUnresolvedUrl == null || mResolver != null)
			return;
		mResolver = new Thread() {
			@Override
			public void run() {
				InputStream in = null;
				OutputStream out = null;
				File saveTo = generateRandomAttachmentFileName();
				try {
					synchronized (mFileMap) {
						mFileMap.put(TemplateTweetAttachment.this, saveTo);
					}
					in = context.getContentResolver().openInputStream(mUnresolvedUrl);
					out = new FileOutputStream(saveTo);
					StreamTools.passthroughStreams(in, out);
					saveTo.getAbsolutePath();
					boolean isGif = false;
					boolean isMovie;
					GifDrawable gifTester = null;
					Bitmap bitmapTester;
					bitmapTester = ThumbnailUtils.createVideoThumbnail(saveTo.getAbsolutePath(), MediaStore.Images.Thumbnails.MICRO_KIND);
					isMovie = bitmapTester != null;
					if (bitmapTester != null) bitmapTester.recycle();
					try {
						gifTester = new GifDrawable(saveTo.getAbsolutePath());
						isGif = gifTester.getNumberOfFrames() > 1;
					} catch (Exception e) {
						e.printStackTrace();
						if (gifTester != null) gifTester.recycle();
					}
					mIsImageMedia = !isGif && !isMovie;
					mTypeResolved = true;
					mMainHandler.post(new Runnable() {
						@Override
						public void run() {
							resolveResult.onTypeResolved(TemplateTweetAttachment.this);
						}
					});
					if (isMovie) {
						if (saveTo.length() > TwitterEngine.MAX_VIDEO_MEDIA_SIZE) {
							DarknovaApplication.showToast("Video file too big (>15MB)");
							throw new IOException("File too big");
						}
					} else if (isGif) {
						if (saveTo.length() > TwitterEngine.MAX_IMAGE_MEDIA_SIZE) {
							DarknovaApplication.showToast("Animated GIF file too big (>5MB)");
							throw new IOException("File too big");
						}
					} else {
						if (saveTo.length() > TwitterEngine.MAX_IMAGE_MEDIA_SIZE) {
							FileTools.resizeImage(saveTo, TwitterEngine.MAX_IMAGE_MEDIA_SIZE);
						}
					}
					mLocalPath = saveTo.getAbsolutePath();
					mUnresolvedUrl = null;
				} catch (final IOException e) {
					synchronized (mFileMap) {
						mFileMap.remove(TemplateTweetAttachment.this);
						if (!saveTo.delete())
							saveTo.deleteOnExit();
					}
					mMainHandler.post(new Runnable() {
						@Override
						public void run() {
							resolveResult.onResolveFailed(TemplateTweetAttachment.this, e);
						}
					});
					e.printStackTrace();
				} catch (final Error e) {
					synchronized (mFileMap) {
						mFileMap.remove(TemplateTweetAttachment.this);
						if (!saveTo.delete())
							saveTo.deleteOnExit();
					}
					mMainHandler.post(new Runnable() {
						@Override
						public void run() {
							resolveResult.onResolveFailed(TemplateTweetAttachment.this, new Exception(e));
						}
					});
					e.printStackTrace();
				} finally {
					StreamTools.close(in);
					StreamTools.close(out);
					if (mLocalPath != null) {
						mMainHandler.post(new Runnable() {
							@Override
							public void run() {
								resolveResult.onResolved(TemplateTweetAttachment.this);
							}
						});
					}
				}
			}
		};
		mResolver.start();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	public void writeToPreferences(String key, SharedPreferences.Editor dest) {
		if (mLocalPath == null)
			dest.remove(key + ".mLocalPath");
		else
			dest.putString(key + ".mLocalPath", mLocalPath);
		dest.putBoolean(key + ".mIsImageMedia", mIsImageMedia);
		dest.putLong(key + ".media_id", media_id);
		if (mUnresolvedUrl == null)
			dest.remove(key + ".mUnresolvedUrl");
		else
			dest.putString(key + ".mUnresolvedUrl", mUnresolvedUrl.toString());
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		if (mLocalPath != null)
			dest.writeString(mLocalPath);
		dest.writeByte((byte) (mIsImageMedia ? 0x01 : 0x00));
		dest.writeLong(media_id);
		dest.writeByte((byte) (mUnresolvedUrl == null ? 0 : 1));
		if (mUnresolvedUrl != null)
			dest.writeString(mUnresolvedUrl.toString());
	}

	public interface AttachmentResolveResult {
		void onResolved(TemplateTweetAttachment attachment);

		void onTypeResolved(TemplateTweetAttachment attachment);

		void onResolveFailed(TemplateTweetAttachment attachment, Exception e);
	}
}
