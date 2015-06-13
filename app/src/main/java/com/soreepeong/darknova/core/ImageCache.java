package com.soreepeong.darknova.core;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.DimenRes;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.util.SparseArray;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.drawable.ClippedBitmapDrawable;
import com.soreepeong.darknova.drawable.ClippedDrawable;
import com.soreepeong.darknova.drawable.WaveProgressDrawable;
import com.soreepeong.darknova.services.ImageCacheProvider;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.tools.StreamTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.droidsonroids.gif.GifDrawable;

/**
 * Custom image cache that uses {@see ImageCacheProvider} for DB management.
 *
 * @author Soreepeong
 */
public class ImageCache {
	private static final int MAX_SIZE = 4 * 1048576;
	private static final int DEFAULT_IMAGE_LOADERS = 4;
	private static final int REVEAL_ANIMATION_LENGTH = 300;
	private static final String[] COLUMN_ID_ONLY = new String[]{"_id"};
	private static final AccelerateDecelerateInterpolator REVEAL_ANIMATION_INTERPOLATOR = new AccelerateDecelerateInterpolator();
	private static final Pattern LOCAL_FILE_MATCHER = Pattern.compile("^(?:file://)?(/.*)$", Pattern.CASE_INSENSITIVE);
	private static final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
	private static final WeakHashMap<DrawableWrapper, GifDrawable> mWrappedDrawableCallbacks = new WeakHashMap<>();
	private static final WeakHashMap<GifDrawable, String> mExistingWrappedDrawables = new WeakHashMap<>();
	private static final Drawable.Callback mWrapperCallback = new Drawable.Callback() {
		@Override
		public void invalidateDrawable(Drawable who) {
			synchronized (mWrappedDrawableCallbacks) {
				for (DrawableWrapper w : mWrappedDrawableCallbacks.keySet())
					w.invalidateDrawable(who);
			}
		}

		@Override
		public void scheduleDrawable(Drawable who, Runnable what, long when) {
			synchronized (mWrappedDrawableCallbacks) {
				for (DrawableWrapper w : mWrappedDrawableCallbacks.keySet())
					w.scheduleDrawable(who, what, when);
			}
		}

		@Override
		public void unscheduleDrawable(Drawable who, Runnable what) {
			synchronized (mWrappedDrawableCallbacks) {
				for (DrawableWrapper w : mWrappedDrawableCallbacks.keySet())
					w.unscheduleDrawable(who, what);
			}
		}
	};
	private static ImageCache mCurrentCache;
	private static CacheLoader mCacheLoader;
	private final LinkedList<ImageLoader> mPendingImages;
	private final ConcurrentHashMap<String, ImageLoader> mLoaderMap;
	private final List<ImageLoader> mWorkingImageLoaders;
	private final ImageLoaderScheduler mScheduler;
	private final Resources mResources;
	public final OnDrawablePreprocessListener CIRCULAR_IMAGE_PREPROCESSOR = new OnDrawablePreprocessListener() {
		@Override
		public Drawable onDrawablePreprocess(Drawable drawable) {
			if (drawable instanceof BitmapDrawable) {
				drawable = new ClippedBitmapDrawable(mResources, (BitmapDrawable) drawable);
				((ClippedBitmapDrawable) drawable).clip(ClippedBitmapDrawable.CLIP_TYPE_OVAL, 0);
			} else if (drawable instanceof WaveProgressDrawable) {
				((WaveProgressDrawable) drawable).clip(WaveProgressDrawable.CLIP_TYPE_OVAL, 0);
			} else {
				drawable = new ClippedDrawable(drawable);
				((ClippedDrawable) drawable).clip(ClippedBitmapDrawable.CLIP_TYPE_OVAL, 0);
			}
			return drawable;
		}
	};
	private final LruCache<String, WeakReference<BitmapDrawable>> mMemoryCache;
	private final WeakHashMap<WeakReference<BitmapDrawable>, Integer> mSizeCache;
	private final WeakHashMap<Bitmap, GifDrawable> mGifPaths;
	private final WeakHashMap<BitmapDrawable, Integer> mBitmapMaxSizes;
	private final ContentResolver mDb;
	private final SparseArray<BitmapDrawable> mNullResourceBitmaps = new SparseArray<>();
	private File mCacheFile;

	private ImageCache(ContentResolver resolver, Resources res) throws IOException {
		mPendingImages = new LinkedList<>();
		mDb = resolver;

		mResources = res;
		mWorkingImageLoaders = Collections.synchronizedList(new ArrayList<ImageLoader>());
		mLoaderMap = new ConcurrentHashMap<>();
		mScheduler = new ImageLoaderScheduler();
		mSizeCache = new WeakHashMap<>();
		mGifPaths = new WeakHashMap<>();
		mBitmapMaxSizes = new WeakHashMap<>();
		mMemoryCache = new LruCache<String, WeakReference<BitmapDrawable>>(MAX_SIZE) {
			@Override
			protected int sizeOf(String key, WeakReference<BitmapDrawable> bitmap) {
				return mSizeCache.get(bitmap);
			}
		};
		mScheduler.start();
	}

	/**
	 * Get image cache instance
	 *
	 * @param ctx      Context to use in initialization
	 * @param listener Callback after initialization; posted to the handler of main thread if already available
	 * @return Instance of ImageCache. Null if not loaded yet
	 */
	public static ImageCache getCache(Context ctx, final OnImageCacheReadyListener listener) {
		synchronized (mMainThreadHandler) {
			if (mCurrentCache != null) {
				mMainThreadHandler.post(new Runnable() {
					@Override
					public void run() {
						listener.onImageCacheReady(mCurrentCache);
					}
				});
				return mCurrentCache;
			}
			if (mCacheLoader == null) {
				mCacheLoader = new CacheLoader();
				mCacheLoader.mContext = ctx;
				mCacheLoader.start();
			}
			if (listener != null)
				mCacheLoader.mListeners.add(listener);
			return null;
		}
	}

	public static Bitmap decodeFile(String sPath, float nMaxWidth, float nMaxHeight) throws IOException {
		BitmapFactory.Options o = new BitmapFactory.Options();
		ExifInterface exif = new ExifInterface(sPath);
		o.inJustDecodeBounds = true;
		o.inSampleSize = 1;
		BitmapFactory.decodeFile(sPath, o);
		o.inSampleSize = (int) Math.max(Math.ceil(o.outWidth / nMaxWidth / 2), o.inSampleSize);
		o.inSampleSize = (int) Math.max(Math.ceil(o.outHeight / nMaxHeight / 2), o.inSampleSize);

		while (o.inSampleSize < 256) {
			o.inDither = true;
			o.inJustDecodeBounds = false;
			try {
				Bitmap sourceBitmap = BitmapFactory.decodeFile(sPath, o);
				Matrix matrix = new Matrix();
				switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
					case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
						matrix.setScale(-1, 1);
						break;
					case ExifInterface.ORIENTATION_FLIP_VERTICAL:
						matrix.setScale(1, -1);
						break;
					case ExifInterface.ORIENTATION_ROTATE_90:
						matrix.setRotate(90);
						break;
					case ExifInterface.ORIENTATION_ROTATE_180:
						matrix.setRotate(180);
						break;
					case ExifInterface.ORIENTATION_ROTATE_270:
						matrix.setRotate(270);
						break;
					case ExifInterface.ORIENTATION_TRANSPOSE:
						matrix.setRotate(90);
						matrix.setScale(-1, 1);
						break;
					case ExifInterface.ORIENTATION_TRANSVERSE:
						matrix.setRotate(270);
						matrix.setScale(-1, 1);
						break;
					case ExifInterface.ORIENTATION_NORMAL:
					default:
						if (sourceBitmap.getWidth() <= nMaxWidth && sourceBitmap.getHeight() <= nMaxHeight)
							return sourceBitmap;
				}
				float scale = Math.min(nMaxWidth / (float) sourceBitmap.getWidth(), nMaxHeight / (float) sourceBitmap.getHeight());
				if (scale < 1)
					matrix.postScale(scale, scale);
				Bitmap newBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight(), matrix, true);
				sourceBitmap.recycle();
				return newBitmap;
			} catch (Error e) {
				o.inSampleSize *= 2;
			}
		}
		throw new OutOfMemoryError();
	}

	private static DrawableWrapper makeGifDrawableWrapper(GifDrawable dr) {
		DrawableWrapper wr = new DrawableWrapper(dr);
		synchronized (mWrappedDrawableCallbacks) {
			mWrappedDrawableCallbacks.put(wr, dr);
		}
		dr.setVisible(true, false);
		dr.start();
		return wr;
	}

	/**
	 * Get cache directory file
	 *
	 * @return Cache directory file
	 */
	public File getCacheFile() {
		if (mCacheFile == null)
			mCacheFile = new File(mDb.getType(ImageCacheProvider.PROVIDER_URI));
		return mCacheFile;
	}

	/**
	 * Add bitmap to memory cache
	 *
	 * @param url    Bitmap's original URL
	 * @param bitmap BitmapDrawable
	 */
	public void addBitmapToMemoryCache(String url, BitmapDrawable bitmap) {
		if (getBitmapFromMemCache(url) == null) {
			int size;
			WeakReference<BitmapDrawable> ref = new WeakReference<>(bitmap);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
				size = bitmap.getBitmap().getAllocationByteCount();
			else
				size = bitmap.getBitmap().getByteCount();
			mSizeCache.put(ref, size);
			mMemoryCache.put(url, ref);
			bitmap.getBitmap(); // weakreference
		}
	}

	/**
	 * Get bitmap from the memory cache
	 *
	 * @param url URL
	 * @return BitmapDrawable; null if unavailable
	 */
	public BitmapDrawable getBitmapFromMemCache(String url) {
		WeakReference<BitmapDrawable> ref = mMemoryCache.get(url);
		if (ref == null)
			return null;
		return ref.get();
	}

	/**
	 * Prepare bitmap using given parameters
	 *
	 * @param url      URL
	 * @param auth     Authentication, null if unused
	 *                 @param minSize Minimum size in pixels
	 * @param callback Callback to call after load
	 */
	public void prepareBitmap(String url, @Nullable OAuth auth, int minSize, OnImageAvailableListener callback) {
		boolean newObject;
		WeakReference<BitmapDrawable> ref = mMemoryCache.get(url);
		BitmapDrawable drawable = ref == null ? null : ref.get();
		if (drawable != null && mBitmapMaxSizes.get(drawable) != null && mBitmapMaxSizes.get(drawable) >= minSize) {
			if (callback != null)
				callback.onImageAvailable(url, drawable);
			return;
		}
		synchronized (mScheduler) {
			ImageLoader loader = mLoaderMap.get(url);
			newObject = loader == null;
			if (newObject) {
				loader = new ImageLoader(url, auth, minSize);
				mLoaderMap.put(url, loader);
				mPendingImages.addFirst(loader);
			} else if (mPendingImages.remove(loader)) { // move to first, if exists
				loader.updateRequiredSize(minSize);
				mPendingImages.addFirst(loader);
			}
			if (callback != null)
				loader.addListener(callback);
			mScheduler.notify();
		}
	}

	private BitmapDrawable getResourceDrawable(int resId) {
		if (resId == 0)
			return null;
		if (mNullResourceBitmaps.get(resId) != null)
			return mNullResourceBitmaps.get(resId);
		mNullResourceBitmaps.put(resId, new BitmapDrawable(mResources, BitmapFactory.decodeResource(mResources, resId)));
		return getResourceDrawable(resId);
	}

	/**
	 * Set source of ImageView to url.
	 *
	 * @param view ImageView
	 * @param url  URL
	 * @param auth Authentication, null if unused
	 */
	public void assignImageView(ImageView view, String url, OAuth auth) {
		assignImageView(view, null, url, auth, null, null);
	}

	/**
	 * Set source of ImageView to url.
	 *
	 * @param view         ImageView
	 * @param url          URL
	 * @param auth         Authentication, null if unused
	 * @param preprocessor Image preprocessor.
	 */
	public void assignImageView(ImageView view, String url, OAuth auth, @Nullable OnDrawablePreprocessListener preprocessor) {
		assignImageView(view, null, url, auth, preprocessor, null);
	}

	/**
	 * Set source of ImageView to url.
	 *
	 * @param view         ImageView
	 * @param nullDrawable Drawable to show if there's nothing to show on view
	 * @param url          URL
	 * @param auth         Authentication, null if unused
	 * @param preprocessor Image preprocessor.
	 */
	public void assignImageView(final ImageView view, final Object nullDrawable, final String url, @Nullable final OAuth auth, @Nullable final OnDrawablePreprocessListener preprocessor, final OnImageAvailableListener listener) {
		if (view == null)
			return;
		if (view.getTag(R.id.IMAGEVIEW_URL) instanceof String) {
			String prevUrl = (String) view.getTag(R.id.IMAGEVIEW_URL);
			if ((url == null && prevUrl == null) || (url != null && url.equals(prevUrl)))
				return;
			synchronized (mScheduler) {
				for (ImageLoader loader : mWorkingImageLoaders)
					if (loader.mUrl.equals(prevUrl)) {
						synchronized (loader.mTargetViews) {
							loader.mTargetViews.remove(view);
						}
					}
			}
		}
		view.setTag(R.id.IMAGEVIEW_URL, url);
		view.setTag(R.id.IMAGEVIEW_PREPROCESSOR, preprocessor);
		view.clearAnimation();
		if (url == null) {
			if (nullDrawable == null)
				view.setImageDrawable(null);
			else if (nullDrawable instanceof Drawable)
				view.setImageDrawable((Drawable) nullDrawable);
			else
				view.setImageDrawable(getResourceDrawable((int) nullDrawable));
			return;
		}
		if (view.getMeasuredHeight() == 0)
			view.post(new Runnable() {
				@Override
				public void run() {
					String prevUrl = (String) view.getTag(R.id.IMAGEVIEW_URL);
					if (url.equals(prevUrl))
						assignImageViewAfter(view, nullDrawable, url, auth, preprocessor, listener);
				}
			});
		else
			assignImageViewAfter(view, nullDrawable, url, auth, preprocessor, listener);
	}

	private void assignImageViewAfter(ImageView view, Object nullDrawable, String url, @Nullable OAuth auth, @Nullable OnDrawablePreprocessListener preprocessor, OnImageAvailableListener listener) {
		int size = Math.max(view.getMeasuredWidth(), view.getMeasuredHeight());
		Drawable drawable = getBitmapFromMemCache(url);
		if (drawable == null) {
			if (nullDrawable == null) {
				view.setImageDrawable(null);
			} else if (nullDrawable instanceof Drawable)
				view.setImageDrawable((Drawable) nullDrawable);
			else
				view.setImageDrawable(getResourceDrawable((int) nullDrawable));
		} else {
			if (mBitmapMaxSizes.get(drawable) == null || mBitmapMaxSizes.get(drawable) < size)
				drawable = null;
			if (drawable != null) {
				GifDrawable gif = mGifPaths.get(((BitmapDrawable) drawable).getBitmap());
				if (gif != null)
					drawable = makeGifDrawableWrapper(gif);
				view.setImageDrawable(preprocessor != null ? preprocessor.onDrawablePreprocess(drawable) : drawable);
			}
		}
		if (drawable == null) {
			boolean newObject;
			synchronized (mScheduler) {
				ImageLoader loader = mLoaderMap.get(url);
				newObject = loader == null;
				if (newObject) {
					loader = new ImageLoader(url, auth, size);
					mLoaderMap.put(url, loader);
					mPendingImages.addFirst(loader);
				} else if (mPendingImages.remove(loader)) { // move to first, if exists
					loader.updateRequiredSize(size);
					mPendingImages.addFirst(loader);
				}
				loader.mTargetViews.add(view);
				if (listener != null)
					loader.mAvailableListener.add(listener);
				if (nullDrawable == null)
					view.setImageDrawable(preprocessor == null ? new ImageLoadDrawable(loader) : preprocessor.onDrawablePreprocess(new ImageLoadDrawable(loader)));
				mScheduler.notify();
			}
		}
	}

	/**
	 * Show progress accordingly to load status
	 *
	 * @param view Indicating view
	 * @param url  URL
	 * @param auth auth
	 */
	public void assignStatusIndicator(View view, String url, @Nullable OAuth auth) {
		if (view == null)
			return;
		if (view.getTag(R.id.IMAGEVIEW_URL) instanceof String) {
			String prevUrl = (String) view.getTag(R.id.IMAGEVIEW_URL);
			if ((url == null && prevUrl == null) || (url != null && url.equals(prevUrl)))
				return;
			synchronized (mScheduler) {
				for (ImageLoader loader : mWorkingImageLoaders)
					if (loader.mUrl.equals(prevUrl)) {
						synchronized (loader.mTargetStatusIndicaters) {
							loader.mTargetStatusIndicaters.remove(view);
						}
					}
			}
		}
		view.clearAnimation();
		view.setTag(R.id.IMAGEVIEW_URL, url);
		if (url == null) {
			view.setVisibility(View.VISIBLE);
			return;
		}
		BitmapDrawable drawable = getBitmapFromMemCache(url);
		view.clearAnimation();
		view.setVisibility(drawable == null ? View.VISIBLE : View.GONE);
		if (drawable == null) {
			boolean newObject;
			synchronized (mScheduler) {
				ImageLoader loader = mLoaderMap.get(url);
				newObject = loader == null;
				if (newObject) {
					return;
				} else if (mPendingImages.remove(loader)) // move to first, if exists
					mPendingImages.addFirst(loader);
				loader.mTargetStatusIndicaters.add(view);
				mScheduler.notify();
			}
		}
	}

	/**
	 * Get cached image path
	 *
	 * @param url URL
	 * @return File path
	 */
	@Nullable
	public String getCachedImagePath(String url) {
		if (url == null)
			return null;
		Cursor cursor = mDb.query(ImageCacheProvider.PROVIDER_URI, COLUMN_ID_ONLY, "url=?", new String[]{url}, null);
		String res = null;
		if (cursor.moveToFirst()) {
			do {
				File f = new File(getCacheFile(), cursor.getString(0));
				if (f.exists())
					res = f.getAbsolutePath();
				else
					mDb.delete(ImageCacheProvider.PROVIDER_URI, "_id=?", new String[]{cursor.getString(0)});
			} while (res == null && cursor.moveToNext());
		}
		cursor.close();
		return res;
	}

	/**
	 * Generate temporary path for URL
	 *
	 * @param url URL
	 * @return file path
	 */
	public String makeTempPath(String url) {
		ContentValues values = new ContentValues();
		values.put("created", System.currentTimeMillis());
		values.put("url", url);
		return mDb.insert(ImageCacheProvider.PROVIDER_URI, values).getPath();
	}

	/**
	 * Apply file size after load
	 *
	 * @param file file patht from makeTempPath
	 */
	public void applySize(File file) {
		ContentValues values = new ContentValues();
		values.put("size", file.length());
		mDb.update(ImageCacheProvider.PROVIDER_URI, values, "_id=?", new String[]{file.getName()});
	}

	/**
	 * Get drawable of size {@param dimenRes}
	 *
	 * @param url      URL
	 * @param dimenRes Dimension resource
	 * @param auth     Authentication; null if unused
	 * @return Drawable
	 */
	public Drawable getDrawable(String url, @DimenRes int dimenRes, OAuth auth) {
		int p = mResources.getDimensionPixelSize(dimenRes);
		return getDrawable(url, p, p, auth);
	}

	/**
	 * Get drawable of size {@param width}x{@param height}
	 *
	 * @param url    URL
	 * @param width  Width
	 * @param height Height
	 * @param auth   Authentication; null if unused
	 * @return Drawable
	 */
	public Drawable getDrawable(String url, int width, int height, OAuth auth) {
		if (url == null)
			return null;
		BitmapDrawable bd = getBitmapFromMemCache(url);
		if (bd != null && mBitmapMaxSizes.get(bd) != null && mBitmapMaxSizes.get(bd) > width && mBitmapMaxSizes.get(bd) > height)
			return bd.getConstantState().newDrawable().mutate();
		AutoApplyingDrawable dr = new AutoApplyingDrawable(width, height);
		dr.mUrl = url;
		dr.updateUrl(url, auth, this);
		return dr;
	}

	public interface OnImageAvailableListener {
		int UNAVAILABLE_NETWORK_ERROR = 1;
		int UNAVAILABLE_FILESYSTEM_ERROR = 2;
		int UNAVAILABLE_MEMORY_ERROR = 3;

		void onImageAvailable(String url, BitmapDrawable bmp);

		void onImageUnavailable(String url, int reason);
	}

	public interface OnImageCacheReadyListener {
		void onImageCacheReady(ImageCache cache);
	}

	public interface OnDrawablePreprocessListener {
		Drawable onDrawablePreprocess(Drawable drawable);
	}

	private static class CacheLoader extends Thread {
		Context mContext;
		ArrayList<OnImageCacheReadyListener> mListeners = new ArrayList<>();

		@Override
		public void run() {
			try {
				mCurrentCache = new ImageCache(mContext.getContentResolver(), mContext.getResources());
				mMainThreadHandler.post(new Runnable() {
					@Override
					public void run() {
						synchronized (mMainThreadHandler) {
							for (OnImageCacheReadyListener listener : mListeners)
								listener.onImageCacheReady(mCurrentCache);
						}
					}
				});
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				mCacheLoader = null;
			}
		}
	}

	public static class AutoApplyingDrawable extends WaveProgressDrawable implements OnImageAvailableListener {
		private final int mWidth, mHeight;
		private Drawable mDrawable;
		private String mUrl;
		private long mAnimationEndTime = 0;
		private boolean mIsLoading;

		protected AutoApplyingDrawable(int width, int height) {
			mWidth = width;
			mHeight = height;
		}

		public boolean isPreparing() {
			return mDrawable == null;
		}

		public String getUrl() {
			return mUrl;
		}

		public void updateUrl(String newurl, OAuth auth, ImageCache mCache) {
			if (mUrl == null ? newurl == null : mUrl.equals(newurl))
				return;
			mUrl = newurl;
			invalidateSelf();
			if (mUrl == null)
				return;
			mDrawable = null;
			mIsLoading = true;
			mCache.prepareBitmap(mUrl, auth, Math.max(mWidth, mHeight), this);
		}

		@Override
		public int getIntrinsicHeight() {
			return mHeight;
		}

		@Override
		public int getIntrinsicWidth() {
			return mWidth;
		}

		@Override
		public void draw(Canvas canvas) {
			if (mDrawable != null) {
				long now = System.currentTimeMillis();
				int myAlpha = getOpacity();
				int prevAlpha = mDrawable.getOpacity();
				Rect prevBounds = mDrawable.getBounds();
				mDrawable.setBounds(getBounds());
				if (mAnimationEndTime < now) {
					mDrawable.setAlpha(myAlpha);
				} else {
					float interpolation = Math.max(0, 1 - (float) (mAnimationEndTime - now) / REVEAL_ANIMATION_LENGTH);
					if (interpolation > 1) {
						interpolation = 1;
					} else
						invalidateSelf();
					interpolation = REVEAL_ANIMATION_INTERPOLATOR.getInterpolation(interpolation);

					super.setAlpha((int) (myAlpha * (1 - interpolation)));
					super.draw(canvas);
					super.setAlpha(myAlpha);

					mDrawable.setAlpha((int) (myAlpha * interpolation));
				}
				mDrawable.draw(canvas);
				mDrawable.setAlpha(prevAlpha);
				mDrawable.setBounds(prevBounds);
			} else if (mIsLoading)
				super.draw(canvas);
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
		}

		@Override
		public void onImageAvailable(String url, BitmapDrawable bmp) {
			mDrawable = bmp.getConstantState().newDrawable().mutate();
			mDrawable.setDither(true);
			mDrawable.setFilterBitmap(true);
			mAnimationEndTime = System.currentTimeMillis() + REVEAL_ANIMATION_LENGTH;
			mIsLoading = false;
			invalidateSelf();
		}

		@Override
		public void onImageUnavailable(String url, int reason) {
			mIsLoading = false;
		}
	}

	private static class ImageLoadDrawable extends WaveProgressDrawable {
		final ImageLoader mLoader;
		boolean replacing;
		Drawable replacement, replacingDrawable;
		ImageView replaceTarget;
		long mReplaceEndTime;

		protected ImageLoadDrawable(ImageLoader loader) {
			mLoader = loader;
			setAlpha(50);
		}

		@Override
		public int getPercentage() {
			int p = replacing ? MAX_PERCENTAGE : mLoader.mLoadProgress * MAX_PERCENTAGE / MAX_IN_PERCENTAGE;
			if (mTargetProgress != p) {
				long now = System.currentTimeMillis();
				if (now < mProgressChangeTimeTarget)
					mProgress += (int) ((mTargetProgress - mProgress) * mInterpolator.getInterpolation(1 - (float) (mProgressChangeTimeTarget - now) / CHANGE_TIME));
				mTargetProgress = p;
				mProgressChangeTimeTarget = now + CHANGE_TIME;
			}
			return p;
		}

		public void replace(ImageView v, Drawable result) {
			if (replacing)
				return;
			replacing = true;
			replacement = result;
			replacingDrawable = result;
			replaceTarget = v;
			mReplaceEndTime = System.currentTimeMillis() + REVEAL_ANIMATION_LENGTH;
			v.setImageDrawable(null);
			v.setImageDrawable(this);
		}

		@Override
		public int getIntrinsicHeight() {
			return replacement == null ? -1 : replacement.getIntrinsicHeight();
		}

		@Override
		public int getIntrinsicWidth() {
			return replacement == null ? -1 : replacement.getIntrinsicWidth();
		}

		@Override
		public void draw(Canvas canvas) {
			if (replacing && replaceTarget != null) {
				long now = System.currentTimeMillis();
				int myAlpha = getOpacity();
				int prevAlpha = replacingDrawable.getOpacity();
				Rect prevBounds = replacingDrawable.getBounds();
				float interpolation = Math.max(0, 1 - (float) (mReplaceEndTime - now) / REVEAL_ANIMATION_LENGTH);
				if (interpolation > 1) {
					replacement.setAlpha(255);
					replaceTarget.setImageDrawable(replacement);
					replaceTarget.setAlpha(1f);
					interpolation = 1;
				} else
					invalidateSelf();
				interpolation = REVEAL_ANIMATION_INTERPOLATOR.getInterpolation(interpolation);

				super.setAlpha((int) (myAlpha * (1 - interpolation)));
				super.draw(canvas);
				super.setAlpha(myAlpha);

				replacingDrawable.setAlpha((int) (255 * interpolation));
				replacingDrawable.setBounds(getBounds());
				replacingDrawable.draw(canvas);
				replacingDrawable.setAlpha(prevAlpha);
				replacingDrawable.setBounds(prevBounds);
			} else {
				super.draw(canvas);
			}
		}
	}

	private static class DrawableWrapper extends Drawable implements Drawable.Callback {
		private final Drawable mDrawable;
		private int mAlpha = 255;

		DrawableWrapper(Drawable dr) {
			mDrawable = dr;
		}

		@Override
		public void draw(Canvas canvas) {
			mDrawable.setAlpha(mAlpha);
			mDrawable.setBounds(getBounds());
			mDrawable.draw(canvas);
		}

		@Override
		public void setAlpha(int alpha) {
			mAlpha = alpha;
		}

		@Override
		public void setColorFilter(ColorFilter cf) {

		}

		@Override
		public int getIntrinsicHeight() {
			return mDrawable.getIntrinsicHeight();
		}

		@Override
		public int getIntrinsicWidth() {
			return mDrawable.getIntrinsicWidth();
		}

		@Override
		public int getOpacity() {
			return mAlpha;
		}

		@Override
		public void invalidateDrawable(Drawable who) {
			if (who == mDrawable)
				invalidateSelf();
		}

		@Override
		public void scheduleDrawable(Drawable who, Runnable what, long when) {
			if (who == mDrawable)
				scheduleSelf(what, when);
		}

		@Override
		public void unscheduleDrawable(Drawable who, Runnable what) {
			if (who == mDrawable)
				unscheduleSelf(what);
		}
	}

	private class ImageLoaderScheduler extends Thread {

		@Override
		public void run() {
			try {
				while (!Thread.interrupted()) {
					ImageLoader newLoader;
					synchronized (this) {
						while (mWorkingImageLoaders.size() >= DEFAULT_IMAGE_LOADERS || mPendingImages.isEmpty())
							wait();
						newLoader = mPendingImages.removeFirst();
						newLoader.setPriority(Thread.MIN_PRIORITY);
						mWorkingImageLoaders.add(newLoader);
					}
					newLoader.start();
				}
			} catch (InterruptedException ie) {
				// finished
			}
		}
	}

	private class ImageLoader extends Thread {
		final String mUrl;
		final OAuth mAuth;
		final CopyOnWriteArrayList<OnImageAvailableListener> mAvailableListener = new CopyOnWriteArrayList<>();
		final CopyOnWriteArrayList<ImageView> mTargetViews = new CopyOnWriteArrayList<>();
		final CopyOnWriteArrayList<View> mTargetStatusIndicaters = new CopyOnWriteArrayList<>();
		BitmapDrawable mBitmapDrawable;
		GifDrawable mGifDrawable;
		boolean mIsGif;
		String mId = null;
		String mFilePath;
		int mLoadProgress;
		int mRequiredSize;

		public ImageLoader(String url, OAuth auth, int requiredSize) {
			mUrl = url;
			mAuth = auth;
			mRequiredSize = requiredSize;
		}

		public void updateRequiredSize(int size) {
			if (mRequiredSize < size)
				mRequiredSize = size;
		}

		@Override
		public int hashCode() {
			return mUrl.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof ImageLoader && mUrl.equals(((ImageLoader) o).mUrl);
		}

		public void addListener(OnImageAvailableListener listener) {
			mAvailableListener.add(listener);
		}

		private int download() {
			int error;
			FileOutputStream out = null;
			InputStream in = null;
			ContentValues values = new ContentValues();
			values.put("created", System.currentTimeMillis());
			values.put("url", mUrl);
			HTTPRequest request = HTTPRequest.getRequest(mUrl, mAuth, false, null);
			if (request == null)
				return -1;
			request.submitRequest();
			switch (error = request.getStatusCode()) {
				case 0:
					error = OnImageAvailableListener.UNAVAILABLE_NETWORK_ERROR;
					break;
				case 200:
					error = 0;
					File f = null;
					try {
						long size = request.getInputLength(), current = 0;
						in = request.getInputStream();
						Uri inserted = mDb.insert(ImageCacheProvider.PROVIDER_URI, values);
						mId = inserted.getLastPathSegment();
						f = new File(inserted.getPath());
						mFilePath = f.getAbsolutePath();
						out = new FileOutputStream(f);
						int bytesRead;
						byte buffer[] = new byte[8192];
						while (!Thread.interrupted() && (bytesRead = in.read(buffer)) > 0) {
							out.write(buffer, 0, bytesRead);
							current += bytesRead;
							mLoadProgress = size == 0 ? 30 : (int) (1000 * current / size);
						}
						mLoadProgress = 1000;
						values.clear();
						values.put("size", size);
						mDb.update(ImageCacheProvider.PROVIDER_URI, values, "_id=?", new String[]{mId});
					} catch (Exception e) {
						error = OnImageAvailableListener.UNAVAILABLE_NETWORK_ERROR;
						if (f != null && !f.delete())
							f.deleteOnExit();
					} finally {
						StreamTools.close(in);
						StreamTools.close(out);
					}
					break;
			}
			request.close();
			return error;
		}

		int read() {
			Bitmap b = null;
			File f = new File(mFilePath);
			if (!f.exists())
				return OnImageAvailableListener.UNAVAILABLE_FILESYSTEM_ERROR;
			try {
				synchronized (mExistingWrappedDrawables) {
					for (GifDrawable dr : mExistingWrappedDrawables.keySet())
						if (mExistingWrappedDrawables.get(dr).equals(mFilePath))
							mGifDrawable = dr;
					if (mGifDrawable == null) {
						mGifDrawable = new GifDrawable(mFilePath);
						mGifDrawable.setCallback(mWrapperCallback);
						mGifDrawable.setLoopCount(0);
						mExistingWrappedDrawables.put(mGifDrawable, mFilePath);
					}
				}
				mIsGif = true;
			} catch (IOException e) {
				mIsGif = false;
			}
			try {
				b = decodeFile(mFilePath, mRequiredSize, mRequiredSize);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (OutOfMemoryError oom) {
				return OnImageAvailableListener.UNAVAILABLE_MEMORY_ERROR;
			}
			if (b == null)
				b = ThumbnailUtils.createVideoThumbnail(mFilePath, MediaStore.Video.Thumbnails.MICRO_KIND);
			if (b != null) {
				mBitmapDrawable = new BitmapDrawable(mResources, b);
				mBitmapMaxSizes.put(mBitmapDrawable, mRequiredSize);
			}
			if (mIsGif)
				mGifPaths.put(b, mGifDrawable);
			return 0;
		}

		@Override
		public void run() {
			try {
				boolean downloaded = false;
				int error = 0;
				Matcher m = LOCAL_FILE_MATCHER.matcher(mUrl);
				if (!m.matches()) {
					Cursor cursor = mDb.query(ImageCacheProvider.PROVIDER_URI, COLUMN_ID_ONLY, "url=?", new String[]{mUrl}, null);
					mId = null;
					if (cursor.moveToFirst())
						do {
							File f = new File(getCacheFile(), cursor.getString(0));
							if (f.exists()) {
								mId = cursor.getString(0);
								mFilePath = f.getAbsolutePath();
							} else {
								mDb.delete(ImageCacheProvider.PROVIDER_URI, "_id=?", new String[]{cursor.getString(0)});
							}
						} while (mId == null && cursor.moveToNext());
					if (mId == null) { // download
						downloaded = true;
						error = download();
					}
					cursor.close();
					if (error == 0)
						error = read();
					if (error != 0 && !downloaded && (error = download()) == 0) {
						mFilePath = new File(getCacheFile(), mId).getAbsolutePath();
						error = read();
					}
				} else {
					mFilePath = m.group(1);
					error = read();
				}
				if (error == 0)
					addBitmapToMemoryCache(mUrl, mBitmapDrawable);
				synchronized (mScheduler) {
					mLoaderMap.remove(mUrl);
					mWorkingImageLoaders.remove(this);
					mScheduler.notify();
				}
				if (Thread.interrupted())
					return;
				final int finalError = error;
				mMainThreadHandler.post(new Runnable() {
					@Override
					public void run() {
						for (OnImageAvailableListener listener : mAvailableListener)
							if (finalError == 0)
								listener.onImageAvailable(mUrl, mBitmapDrawable);
							else
								listener.onImageUnavailable(mUrl, finalError);
						for (ImageView view : mTargetViews) {
							if (mUrl.equals(view.getTag(R.id.IMAGEVIEW_URL))) {
								if (finalError == 0) {
									Drawable newDrawable = null;
									if (mIsGif)
										newDrawable = makeGifDrawableWrapper(mGifDrawable);
									if (newDrawable == null)
										newDrawable = mBitmapDrawable;
									if (view.getTag(R.id.IMAGEVIEW_PREPROCESSOR) instanceof OnDrawablePreprocessListener)
										newDrawable = ((OnDrawablePreprocessListener) view.getTag(R.id.IMAGEVIEW_PREPROCESSOR)).onDrawablePreprocess(newDrawable);
									if (view.getDrawable() instanceof ImageLoadDrawable)
										((ImageLoadDrawable) view.getDrawable()).replace(view, newDrawable);
									else {
										view.setImageDrawable(mBitmapDrawable);
										if (view.getVisibility() == View.VISIBLE) {
											AlphaAnimation anim = new AlphaAnimation(0, 1);
											anim.setInterpolator(view.getContext(), android.R.anim.accelerate_decelerate_interpolator);
											anim.setDuration(REVEAL_ANIMATION_LENGTH);
											view.clearAnimation();
											view.startAnimation(anim);
										}
									}
								} else {
									view.setImageDrawable(null);
								}
							}
						}
						for (View view : mTargetStatusIndicaters) {
							if (mUrl.equals(view.getTag(R.id.IMAGEVIEW_URL))) {
								ResTools.hideWithAnimation(view, android.R.anim.fade_out, true);
							}
						}
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
