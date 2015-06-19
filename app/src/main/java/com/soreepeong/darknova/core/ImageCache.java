package com.soreepeong.darknova.core;

import android.app.ActivityManager;
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
import android.media.MediaMetadataRetriever;
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
import com.soreepeong.darknova.tools.WeakValueHashMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
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
	private static final int REVEAL_ANIMATION_LENGTH = 300;
	private static final String[] COLUMN_ID_ONLY = new String[]{"_id"};
	private static final AccelerateDecelerateInterpolator REVEAL_ANIMATION_INTERPOLATOR = new AccelerateDecelerateInterpolator();
	private static final Pattern LOCAL_FILE_MATCHER = Pattern.compile("^(?:file://)?(/.*)$", Pattern.CASE_INSENSITIVE);
	private static final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
	private static final Map<DrawableWrapper, GifDrawable> mWrappedDrawableCallbacks = Collections.synchronizedMap(new WeakHashMap<DrawableWrapper, GifDrawable>());
	private static final Map<GifDrawable, String> mExistingWrappedDrawables = Collections.synchronizedMap(new WeakHashMap<GifDrawable, String>());
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
	private final ThreadScheduler mSchedulerLocal, mSchedulerRemote;
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
	private final LruCache<String, BitmapDrawable> mMemoryCache;
	private final WeakValueHashMap<String, BitmapDrawable> mUsedCache = new WeakValueHashMap<>();
	private final ConcurrentHashMap<String, ImageLoader> mLoaderMap = new ConcurrentHashMap<>();
	private final Map<BitmapDrawable, BitmapDrawableInformation> mBitmapInfo = Collections.synchronizedMap(new WeakHashMap<BitmapDrawable, BitmapDrawableInformation>());
	private final ContentResolver mResolver;
	private final SparseArray<BitmapDrawable> mNullResourceBitmaps = new SparseArray<>();
	private File mCacheFile;

	private ImageCache(Context context) throws IOException {
		mResolver = context.getContentResolver();
		mResources = context.getResources();
		mSchedulerLocal = new ThreadScheduler(2, "LocalImage");
		mSchedulerRemote = new ThreadScheduler(4, "RemoteImage");
		mMemoryCache = new LruCache<String, BitmapDrawable>(((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass() * 1048576 / 8) {
			@Override
			protected int sizeOf(String key, BitmapDrawable bitmap) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
					return bitmap.getBitmap().getAllocationByteCount();
				else
					return bitmap.getBitmap().getByteCount();
			}
		};
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

	/**
	 * Decode a Bitmap file
	 *
	 * @param sPath      File path
	 * @param nMaxWidth  Maximum decoded width
	 * @param nMaxHeight Maximum decoded height
	 * @param o          Options
	 * @return Decoded bitmap, or null if failed
	 * @throws IOException
	 */
	public static Bitmap decodeFile(String sPath, float nMaxWidth, float nMaxHeight, BitmapFactory.Options o) throws IOException {
		ExifInterface exif = new ExifInterface(sPath);
		if (o == null)
			o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		o.inDither = true;
		BitmapFactory.decodeFile(sPath, o);
		if (o.outMimeType == null)
			return null;
		int w = o.outWidth, h = o.outHeight;
		o.inSampleSize = (int) Math.max(1, Math.min(Math.ceil(o.outWidth / nMaxWidth / 2), Math.ceil(o.outHeight / nMaxHeight / 2)));

		while (o.inSampleSize < 256) {
			o.inDither = true;
			o.inJustDecodeBounds = false;
			try {
				Bitmap sourceBitmap = BitmapFactory.decodeFile(sPath, o);
				o.outWidth = w; o.outHeight = h;
				Matrix matrix = new Matrix();
				switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
					case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
						matrix.setScale(-1, 1);
						break;
					case ExifInterface.ORIENTATION_FLIP_VERTICAL:
						matrix.setScale(1, -1);
						break;
					case ExifInterface.ORIENTATION_ROTATE_90:
						o.outWidth = h; o.outHeight = w;
						matrix.setRotate(90);
						break;
					case ExifInterface.ORIENTATION_ROTATE_180:
						matrix.setRotate(180);
						break;
					case ExifInterface.ORIENTATION_ROTATE_270:
						o.outWidth = h; o.outHeight = w;
						matrix.setRotate(270);
						break;
					case ExifInterface.ORIENTATION_TRANSPOSE:
						o.outWidth = h; o.outHeight = w;
						matrix.setRotate(90);
						matrix.setScale(-1, 1);
						break;
					case ExifInterface.ORIENTATION_TRANSVERSE:
						o.outWidth = h; o.outHeight = w;
						matrix.setRotate(270);
						matrix.setScale(-1, 1);
						break;
					case ExifInterface.ORIENTATION_NORMAL:
					default:
						if (sourceBitmap.getWidth() <= nMaxWidth && sourceBitmap.getHeight() <= nMaxHeight)
							return sourceBitmap;
				}
				float scale = Math.max(nMaxWidth / (float) sourceBitmap.getWidth(), nMaxHeight / (float) sourceBitmap.getHeight());
				if (scale < 1)
					matrix.postScale(scale, scale);
				Bitmap newBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight(), matrix, true);
				if (sourceBitmap != newBitmap)
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
			mCacheFile = new File(mResolver.getType(ImageCacheProvider.PROVIDER_URI));
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
			mMemoryCache.put(url, bitmap);
			mUsedCache.put(url, bitmap);
		}
	}

	/**
	 * Get bitmap from the memory cache
	 *
	 * @param url URL
	 * @return BitmapDrawable; null if unavailable
	 */
	public BitmapDrawable getBitmapFromMemCache(String url) {
		BitmapDrawable d = mUsedCache.get(url);
		if (d != null)
			mMemoryCache.put(url, d);
		return d;
	}

	public void clearStorage(){
		mMemoryCache.evictAll();
		System.gc();
	}

	/**
	 * Prepare bitmap using given parameters
	 *
	 * @param url      URL
	 * @param auth     Authentication, null if unused
	 * @param minSize  Minimum size in pixels
	 * @param callback Callback to call after load
	 */
	public void prepareBitmap(String url, @Nullable OAuth auth, int minSize, OnImageAvailableListener callback) {
		boolean newObject;
		BitmapDrawable drawable = getBitmapFromMemCache(url);
		BitmapDrawableInformation info = mBitmapInfo.get(drawable);
		if (drawable != null && info.sizeLimit >= minSize) {
			if (callback != null) {
				callback.onImageAvailable(url, drawable, info.gif == null ? drawable : info.gif, info.width, info.height);
			}
			return;
		}
		ImageLoader loader;
		synchronized (mLoaderMap) {
			loader = mLoaderMap.get(url);
			newObject = loader == null;
			if (newObject) {
				loader = new ImageLoader(url, auth, minSize);
				mLoaderMap.put(url, loader);
			}
		}
		loader.updateRequiredSize(minSize);
		if (callback != null)
			loader.addListener(callback);
		mSchedulerLocal.schedule(loader);
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
			if (url != null && url.equals(prevUrl)) {
				BitmapDrawable drawable = getBitmapFromMemCache(url);
				BitmapDrawableInformation info = mBitmapInfo.get(drawable);
				if (drawable != null && listener != null)
					listener.onImageAvailable(url, drawable, info.gif != null ? info.gif : drawable, info.width, info.height);
				return;
			} else if ((url == null && prevUrl == null)) {
				return;
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
		BitmapDrawable drawable = getBitmapFromMemCache(url);
		BitmapDrawableInformation info = mBitmapInfo.get(drawable);
		if (drawable == null || info == null || info.sizeLimit < size) {
			boolean newObject;
			ImageLoader loader;
			synchronized (mLoaderMap) {
				loader = mLoaderMap.get(url);
				newObject = loader == null;
				if (newObject) {
					loader = new ImageLoader(url, auth, size);
					mLoaderMap.put(url, loader);
				}
			}
			loader.updateRequiredSize(size);
			loader.mTargetViews.add(view);
			if (listener != null)
				loader.mAvailableListener.add(listener);
			if (nullDrawable == null) {
				view.setImageDrawable(preprocessor == null ? new ImageLoadDrawable(loader) : preprocessor.onDrawablePreprocess(new ImageLoadDrawable(loader)));
			} else if (nullDrawable instanceof Drawable)
				view.setImageDrawable((Drawable) nullDrawable);
			else
				view.setImageDrawable(getResourceDrawable((int) nullDrawable));
			mSchedulerLocal.schedule(loader);
		} else {
			if (listener != null)
				listener.onImageAvailable(url, drawable, info.gif != null ? info.gif : drawable, info.width, info.height);
			if (info.gif != null)
				view.setImageDrawable(preprocessor != null ? preprocessor.onDrawablePreprocess(makeGifDrawableWrapper(info.gif)) : makeGifDrawableWrapper(info.gif));
			else
				view.setImageDrawable(preprocessor != null ? preprocessor.onDrawablePreprocess(drawable) : drawable);
		}
	}

	/**
	 * Show progress accordingly to load status
	 *
	 * @param view Indicating view
	 * @param url  URL
	 */
	public void assignStatusIndicator(final View view, final String url) {
		if (view == null)
			return;
		if (view.getTag(R.id.IMAGEVIEW_URL) instanceof String) {
			String prevUrl = (String) view.getTag(R.id.IMAGEVIEW_URL);
			if ((url == null && prevUrl == null) || (url != null && url.equals(prevUrl)))
				return;
		}
		view.clearAnimation();
		view.setTag(R.id.IMAGEVIEW_URL, url);
		if (url == null) {
			view.setVisibility(View.VISIBLE);
			return;
		}
		if (view.getMeasuredHeight() == 0)
			view.post(new Runnable() {
				@Override
				public void run() {
					String prevUrl = (String) view.getTag(R.id.IMAGEVIEW_URL);
					if (url.equals(prevUrl))
						assignStatusIndicatorAfter(view, url);
				}
			});
		else
			assignStatusIndicatorAfter(view, url);
	}

	public void assignStatusIndicatorAfter(View view, String url) {
		BitmapDrawable drawable = getBitmapFromMemCache(url);
		view.clearAnimation();
		view.setVisibility(drawable == null ? View.VISIBLE : View.GONE);
		if (drawable == null) {
			boolean newObject;
			ImageLoader loader;
			synchronized (mLoaderMap) {
				loader = mLoaderMap.get(url);
				newObject = loader == null;
				if (newObject)
					return;
			}
			loader.mTargetStatusIndicaters.add(view);
			mSchedulerLocal.schedule(loader);
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
		Cursor cursor = mResolver.query(ImageCacheProvider.PROVIDER_URI, COLUMN_ID_ONLY, "url=?", new String[]{url}, null);
		String res = null;
		if (cursor.moveToFirst()) {
			do {
				File f = new File(getCacheFile(), cursor.getString(0));
				if (f.exists())
					res = f.getAbsolutePath();
				else
					mResolver.delete(ImageCacheProvider.PROVIDER_URI, "_id=?", new String[]{cursor.getString(0)});
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
		values.put("url", url);
		return mResolver.insert(ImageCacheProvider.PROVIDER_URI, values).getPath();
	}

	/**
	 * Apply file size after load
	 *
	 * @param file file patht from makeTempPath
	 */
	public void applySize(File file) {
		ContentValues values = new ContentValues();
		values.put("size", file.length());
		mResolver.update(ImageCacheProvider.PROVIDER_URI, values, "_id=?", new String[]{file.getName()});
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
		AutoApplyingDrawable dr = new AutoApplyingDrawable(width, height);
		dr.updateUrlImmediate(url, auth, this);
		return dr;
	}

	public interface OnImageAvailableListener {
		int UNAVAILABLE_NETWORK_ERROR = 1;
		int UNAVAILABLE_FILESYSTEM_ERROR = 2;
		int UNAVAILABLE_MEMORY_ERROR = 3;

		void onImageAvailable(String url, BitmapDrawable bmp, Drawable d, int originalWidth, int originalHeight);

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
				mCurrentCache = new ImageCache(mContext);
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

	public static class AutoApplyingDrawable extends WaveProgressDrawable implements OnImageAvailableListener, Drawable.Callback {
		private final int mWidth, mHeight;
		private Drawable mDrawable;
		private String mUrl;
		private long mAnimationEndTime = 0;
		private boolean mIsLoading;
		private Rect mRect = new Rect();

		protected AutoApplyingDrawable(int width, int height) {
			mWidth = width;
			mHeight = height;
		}

		public static void refineRect(Rect rct, int bitmapWidth, int bitmapHeight) {
			if (bitmapWidth / bitmapHeight < (rct.right - rct.left) / (rct.bottom - rct.top)) {
				int newHeight = rct.bottom - rct.top;
				int newWidth = bitmapWidth * newHeight / bitmapHeight;
				int newLeft = rct.left + (rct.right - rct.left - newWidth) / 2;
				rct.left = newLeft;
				rct.right = newLeft + newWidth;
				rct.bottom = rct.top + newHeight;
			} else {
				int newWidth = rct.right - rct.left;
				int newHeight = bitmapHeight * newWidth / bitmapWidth;
				int newTop = rct.top + (rct.bottom - rct.top - newHeight) / 2;
				rct.right = rct.left + newWidth;
				rct.top = newTop;
				rct.bottom = newTop + newHeight;
			}
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

		public void updateUrlImmediate(String newurl, OAuth auth, ImageCache mCache) {
			if (mUrl == null ? newurl == null : mUrl.equals(newurl))
				return;
			BitmapDrawable drawable = mCache.getBitmapFromMemCache(newurl);
			BitmapDrawableInformation info = mCache.mBitmapInfo.get(drawable);
			if (drawable != null && info != null && info.sizeLimit >= Math.max(mWidth, mHeight)) {
				mUrl = newurl;
				setDrawable(info.gif == null ? drawable : info.gif, 0);
			} else
				updateUrl(newurl, auth, mCache);
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
				mRect.set(getBounds());
				refineRect(mRect, mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight());
				mDrawable.setBounds(mRect);
				if (mAnimationEndTime < now) {
					if (mDrawable.getOpacity() != myAlpha)
						mDrawable.setAlpha(myAlpha);
				} else {
					float interpolation = Math.max(0, 1 - (float) (mAnimationEndTime - now) / REVEAL_ANIMATION_LENGTH);
					if (interpolation > 1)
						interpolation = 1;
					else
						invalidateSelf();
					interpolation = REVEAL_ANIMATION_INTERPOLATOR.getInterpolation(interpolation);

					super.setAlpha((int) (myAlpha * (1 - interpolation)));
					super.draw(canvas);
					super.setAlpha(myAlpha);

					mDrawable.setAlpha((int) (myAlpha * interpolation));
				}
				mDrawable.draw(canvas);
			} else if (mIsLoading)
				super.draw(canvas);
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
		}

		private void setDrawable(Drawable d, long animationEndTime) {
			if (mDrawable != null)
				mDrawable.setCallback(null);
			mDrawable = d instanceof GifDrawable ? makeGifDrawableWrapper((GifDrawable) d) : d.getConstantState().newDrawable().mutate();
			mDrawable.setDither(true);
			mDrawable.setFilterBitmap(true);
			mDrawable.setCallback(this);
			mAnimationEndTime = animationEndTime;
			mIsLoading = false;
			invalidateSelf();
		}

		@Override
		public void onImageAvailable(String url, BitmapDrawable bmp, Drawable d, int originalWidth, int originalHeight) {
			setDrawable(d, System.currentTimeMillis() + REVEAL_ANIMATION_LENGTH);
		}

		@Override
		public void onImageUnavailable(String url, int reason) {
			mIsLoading = false;
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

	private static class ImageLoadDrawable extends WaveProgressDrawable implements Drawable.Callback {
		final ImageLoader mLoader;
		boolean replacing;
		Drawable replacement;
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
			replacement.setCallback(this);
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
				int prevAlpha = replacement.getOpacity();
				Rect prevBounds = replacement.getBounds();
				float interpolation = Math.max(0, 1 - (float) (mReplaceEndTime - now) / REVEAL_ANIMATION_LENGTH);
				if (interpolation > 1) {
					replacement.setAlpha(255);
					replacement.setCallback(null);
					replaceTarget.setImageDrawable(replacement);
					replaceTarget.setAlpha(1f);
					interpolation = 1;
				} else
					invalidateSelf();
				interpolation = REVEAL_ANIMATION_INTERPOLATOR.getInterpolation(interpolation);

				super.setAlpha((int) (myAlpha * (1 - interpolation)));
				super.draw(canvas);
				super.setAlpha(myAlpha);

				replacement.setAlpha((int) (255 * interpolation));
				replacement.setBounds(getBounds());
				replacement.draw(canvas);
				replacement.setAlpha(prevAlpha);
				replacement.setBounds(prevBounds);
			} else {
				super.draw(canvas);
			}
		}

		@Override
		public void invalidateDrawable(Drawable who) {
			if (who == replacement)
				invalidateSelf();
		}

		@Override
		public void scheduleDrawable(Drawable who, Runnable what, long when) {
			if (who == replacement)
				scheduleSelf(what, when);
		}

		@Override
		public void unscheduleDrawable(Drawable who, Runnable what) {
			if (who == replacement)
				unscheduleSelf(what);
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

	private class ImageLoader implements Runnable {
		final String mUrl;
		final OAuth mAuth;
		final CopyOnWriteArrayList<OnImageAvailableListener> mAvailableListener = new CopyOnWriteArrayList<>();
		final CopyOnWriteArrayList<ImageView> mTargetViews = new CopyOnWriteArrayList<>();
		final CopyOnWriteArrayList<View> mTargetStatusIndicaters = new CopyOnWriteArrayList<>();
		BitmapDrawable mBitmapDrawable;
		BitmapDrawableInformation mInfo;
		String mId = null;
		String mFilePath;
		int mLoadProgress;
		int mRequiredSize;
		boolean mUseNetwork;

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

		@Override
		public String toString() {
			return mUrl;
		}

		public void addListener(OnImageAvailableListener listener) {
			mAvailableListener.add(listener);
		}

		private int download() {
			if (!mUseNetwork)
				return -2;
			int error;
			FileOutputStream out = null;
			InputStream in = null;
			ContentValues values = new ContentValues();
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
						Uri inserted = mResolver.insert(ImageCacheProvider.PROVIDER_URI, values);
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
						mResolver.update(ImageCacheProvider.PROVIDER_URI, values, "_id=?", new String[]{mId});
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
			GifDrawable gif = null;
			BitmapFactory.Options o = new BitmapFactory.Options();
			File f = new File(mFilePath);
			if (!f.exists())
				return OnImageAvailableListener.UNAVAILABLE_FILESYSTEM_ERROR;
			try {
				b = decodeFile(mFilePath, mRequiredSize, mRequiredSize, o);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (OutOfMemoryError oom) {
				return OnImageAvailableListener.UNAVAILABLE_MEMORY_ERROR;
			}
			if (o.outMimeType != null && o.outMimeType.equals("image/gif")) {
				try {
					synchronized (mExistingWrappedDrawables) {
						for (GifDrawable dr : mExistingWrappedDrawables.keySet())
							if (mExistingWrappedDrawables.get(dr).equals(mFilePath))
								gif = dr;
						if (gif == null) {
							gif = new GifDrawable(mFilePath);
							gif.setCallback(mWrapperCallback);
							gif.setLoopCount(0);
							o.outWidth = gif.getIntrinsicWidth();
							o.outHeight = gif.getIntrinsicHeight();
							mExistingWrappedDrawables.put(gif, mFilePath);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else if (b == null) {
				b = ThumbnailUtils.createVideoThumbnail(mFilePath, MediaStore.Video.Thumbnails.MICRO_KIND);
				if (b != null) {
					MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
					metaRetriever.setDataSource(mFilePath);
					o.outHeight = Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
					o.outWidth = Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
				}
			}
			if (b != null) {
				mBitmapDrawable = new BitmapDrawable(mResources, b);
				mBitmapInfo.put(mBitmapDrawable, mInfo = new BitmapDrawableInformation(gif, mRequiredSize, o.outWidth, o.outHeight));
			}
			return 0;
		}

		@Override
		public synchronized void run() {
			try {
				boolean downloaded = false;
				int error = 0;
				Matcher m = LOCAL_FILE_MATCHER.matcher(mUrl);
				if (!m.matches()) {
					Cursor cursor = mResolver.query(ImageCacheProvider.PROVIDER_URI, COLUMN_ID_ONLY, "url=?", new String[]{mUrl}, null);
					mId = null;
					if (cursor.moveToFirst())
						do {
							File f = new File(getCacheFile(), cursor.getString(0));
							if (f.exists()) {
								mId = cursor.getString(0);
								mFilePath = f.getAbsolutePath();
							} else {
								mResolver.delete(ImageCacheProvider.PROVIDER_URI, "_id=?", new String[]{cursor.getString(0)});
							}
						} while (mId == null && cursor.moveToNext());
					if (mId == null) { // download
						downloaded = true;
						error = download();
						if (error == -2) {
							mUseNetwork = true;
							mSchedulerRemote.schedule(this);
							return;
						}
					}
					cursor.close();
					if (error == 0)
						error = read();
					if (error != 0 && !downloaded) {
						error = download();
						if (error == -2) {
							mUseNetwork = true;
							mSchedulerRemote.schedule(this);
							return;
						} else if (error == 0) {
							mFilePath = new File(getCacheFile(), mId).getAbsolutePath();
							error = read();
						}
					}
				} else {
					mFilePath = m.group(1);
					error = read();
				}
				if (error == 0)
					addBitmapToMemoryCache(mUrl, mBitmapDrawable);
				synchronized (mLoaderMap) {
					mLoaderMap.remove(mUrl);
				}
				if (Thread.interrupted())
					return;
				final int finalError = error;
				mMainThreadHandler.post(new Runnable() {
					@Override
					public void run() {
						for (OnImageAvailableListener listener : mAvailableListener)
							if (finalError == 0)
								listener.onImageAvailable(mUrl, mBitmapDrawable, mInfo.gif != null ? mInfo.gif : mBitmapDrawable, mInfo.width, mInfo.height);
							else
								listener.onImageUnavailable(mUrl, finalError);
						for (ImageView view : mTargetViews) {
							if (mUrl.equals(view.getTag(R.id.IMAGEVIEW_URL))) {
								if (finalError == 0) {
									Drawable newDrawable;
									newDrawable = mInfo.gif != null ? makeGifDrawableWrapper(mInfo.gif) : mBitmapDrawable.getConstantState().newDrawable().mutate();
									if (view.getTag(R.id.IMAGEVIEW_PREPROCESSOR) instanceof OnDrawablePreprocessListener)
										newDrawable = ((OnDrawablePreprocessListener) view.getTag(R.id.IMAGEVIEW_PREPROCESSOR)).onDrawablePreprocess(newDrawable);
									if (view.getDrawable() instanceof ImageLoadDrawable)
										((ImageLoadDrawable) view.getDrawable()).replace(view, newDrawable);
									else {
										view.setImageDrawable(newDrawable);
										if (view.getVisibility() == View.VISIBLE) {
											AlphaAnimation anim = new AlphaAnimation(0, 1);
											anim.setInterpolator(REVEAL_ANIMATION_INTERPOLATOR);
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

	private class BitmapDrawableInformation {
		final GifDrawable gif;
		final int sizeLimit;
		final int width;
		final int height;

		BitmapDrawableInformation(GifDrawable g, int limit, int w, int h) {
			gif = g;
			sizeLimit = limit;
			width = w;
			height = h;
		}
	}
}
