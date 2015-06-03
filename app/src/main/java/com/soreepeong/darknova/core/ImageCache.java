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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.util.LruCache;
import android.util.SparseArray;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.services.ImageCacheProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Soreepeong on 2015-04-28.
 */
public class ImageCache {
	private static final int MAX_SIZE=4*1048576;
	private static final int DEFAULT_IMAGE_LOADERS = 4;
	private static final int DISK_MAX_SIZE=32*1048576;
	private static final int REVEAL_ANIMATION_LENGTH = 300;
	private static final String[] COLUMN_ID_ONLY=new String[]{"_id"};
	private static final String[] COLUMN_ID_SIZE=new String[]{"_id", "size"};
	private static final AccelerateDecelerateInterpolator REVEAL_ANIMATION_INTERPOLATOR = new AccelerateDecelerateInterpolator();
	private static ImageCache mCurrentCache;
	private static CacheLoader mCacheLoader;
	private static final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

	private final LinkedList<ImageLoader> mPendingImages;
	private final ConcurrentHashMap<String, ImageLoader> mLoaderMap;
	private final List<ImageLoader> mWorkingImageLoaders;
	private final ImageLoaderScheduler mScheduler;
	private final String mCachePath;
	private final Resources mResources;
	private final LruCache<String, WeakReference<BitmapDrawable>> mMemoryCache;
	private final WeakHashMap<WeakReference<BitmapDrawable>, Integer> mSizeCache;
	private final WeakHashMap<AutoApplyingDrawable, String> mUsedDrawables;
	private final ContentResolver mDb;

	public static ImageCache getCache(Context ctx, final OnImageCacheReadyListener listener) {
		synchronized (mMainThreadHandler){
			if(mCurrentCache != null){
				mMainThreadHandler.post(new Runnable() {
					@Override
					public void run() {
						listener.onImageCacheReady(mCurrentCache);
					}
				});
				return mCurrentCache;
			}
			if(mCacheLoader == null){
				mCacheLoader = new CacheLoader();
				mCacheLoader.mContext = ctx;
				mCacheLoader.start();
			}
			if(listener != null)
				mCacheLoader.mListeners.add(listener);
			return null;
		}
	}

	private ImageCache(ContentResolver resolver, String cachePath, Resources res) throws IOException {
		mPendingImages = new LinkedList<>();
		if(!cachePath.endsWith("/"))
			mCachePath = cachePath + "/";
		else
			mCachePath = cachePath;
		mDb = resolver;

		mResources = res;
		mWorkingImageLoaders = Collections.synchronizedList(new ArrayList<ImageLoader>());
		mLoaderMap = new ConcurrentHashMap<>();
		mScheduler = new ImageLoaderScheduler();
		mSizeCache = new WeakHashMap<>();
		mUsedDrawables = new WeakHashMap<>();
		mMemoryCache = new LruCache<String, WeakReference<BitmapDrawable>>(MAX_SIZE){
			@Override
			protected int sizeOf(String key, WeakReference<BitmapDrawable> bitmap) {
				return mSizeCache.get(bitmap);
			}
		};
		mScheduler.start();
	}

	public void truncateDiskCache(){
		ArrayList<String> removeList = new ArrayList<>();
		Cursor cursor = mDb.query(ImageCacheProvider.PROVIDER_URI, COLUMN_ID_SIZE, null, null,"created desc");
		if(cursor.moveToFirst()){
			long sizeSum = 0;
			do{
				sizeSum += cursor.getLong(1);
				if(sizeSum >= DISK_MAX_SIZE){
					new File(mCachePath + cursor.getString(0)).delete();
					removeList.add(cursor.getString(0));
				}
			}while(cursor.moveToNext());
		}
		cursor.close();
		if(!removeList.isEmpty())
			for(String id : removeList)
				mDb.delete(ImageCacheProvider.PROVIDER_URI, "_id=?", new String[]{id});
	}

	public String getCacheDir(){
		return mCachePath;
	}

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

	public BitmapDrawable getBitmapFromMemCache(String url) {
		WeakReference<BitmapDrawable> ref = mMemoryCache.get(url);
		if(ref == null)
			return null;
		return ref.get();
	}

	public void prepareBitmap(String url, OAuth auth, OnImageAvailableListener callback){
		boolean newObject;
		WeakReference<BitmapDrawable> ref = mMemoryCache.get(url);
		BitmapDrawable drawable = ref==null ? null : ref.get();
		if(drawable != null){
			if(callback != null)
				callback.onImageAvailable(url, drawable);
			return;
		}
		synchronized(mScheduler){
			ImageLoader loader = mLoaderMap.get(url);
			newObject = loader == null;
			if(newObject){
				loader = new ImageLoader(url, auth);
				mLoaderMap.put(url, loader);
				mPendingImages.addFirst(loader);
			}else if(mPendingImages.remove(loader)) // move to first, if exists
				mPendingImages.addFirst(loader);
			if(callback != null)
				loader.addListener(callback);
			mScheduler.notify();
		}
	}

	private final SparseArray<BitmapDrawable> mNullResourceBitmaps = new SparseArray<>();

	private BitmapDrawable getResourceDrawable(int resId){
		if(resId == 0)
			return null;
		if(mNullResourceBitmaps.get(resId) != null)
			return mNullResourceBitmaps.get(resId);
		mNullResourceBitmaps.put(resId, new BitmapDrawable(mResources, BitmapFactory.decodeResource(mResources, resId)));
		return getResourceDrawable(resId);
	}

	public void assignImageView(ImageView view, String url, OAuth auth){
		assignImageView(view, null, url, auth, null);
	}

	public void assignImageView(ImageView view, String url, OAuth auth, OnDrawablePreprocessListener preprocessor){
		assignImageView(view, null, url, auth, preprocessor);
	}

	public void assignImageView(ImageView view, Object nullDrawable, String url, OAuth auth, OnDrawablePreprocessListener preprocessor){
		if(view == null)
			return;
		if(view.getTag(R.id.IMAGEVIEW_URL) instanceof String){
			String prevUrl = (String) view.getTag(R.id.IMAGEVIEW_URL);
			if((url == null && prevUrl == null) || (url != null && url.equals(prevUrl)))
				return;
			synchronized (mScheduler){
				for(ImageLoader loader : mWorkingImageLoaders)
					if(loader.mUrl.equals(prevUrl)){
						synchronized (loader.mTargetViews){
							loader.mTargetViews.remove(view);
						}
					}
			}
		}
		view.clearAnimation();
		view.setTag(R.id.IMAGEVIEW_URL, url);
		view.setTag(R.id.IMAGEVIEW_PREPROCESSOR, preprocessor);
		if(url == null){
			if(nullDrawable == null)
				view.setImageDrawable(null);
			else if(nullDrawable instanceof Drawable)
				view.setImageDrawable((Drawable) nullDrawable);
			else
				view.setImageDrawable(getResourceDrawable((int) nullDrawable));
			return;
		}
		BitmapDrawable drawable = getBitmapFromMemCache(url);
		view.clearAnimation();
		if(drawable == null){
			if(nullDrawable == null)
				view.setImageDrawable(null);
			else if(nullDrawable instanceof Drawable)
				view.setImageDrawable((Drawable) nullDrawable);
			else
				view.setImageDrawable(getResourceDrawable((int) nullDrawable));
		}else
			view.setImageDrawable(preprocessor != null ? preprocessor.onDrawablePreprocess(drawable) : drawable);
		if(drawable == null){
			boolean newObject;
			synchronized(mScheduler){
				ImageLoader loader = mLoaderMap.get(url);
				newObject = loader == null;
				if(newObject){
					loader = new ImageLoader(url, auth);
					mLoaderMap.put(url, loader);
					mPendingImages.addFirst(loader);
				}else if(mPendingImages.remove(loader)) // move to first, if exists
					mPendingImages.addFirst(loader);
				loader.mTargetViews.add(view);
				mScheduler.notify();
			}
		}
	}

	private class ImageLoaderScheduler extends Thread{
		@Override
		public void interrupt() {
			super.interrupt();
		}

		@Override
		public void run() {
			try{
				while(!Thread.interrupted()){
					ImageLoader newLoader;
					synchronized(this){
						while(mWorkingImageLoaders.size() >= DEFAULT_IMAGE_LOADERS || mPendingImages.isEmpty())
							wait();
						newLoader = mPendingImages.removeFirst();
						mWorkingImageLoaders.add(newLoader);
					}
					newLoader.start();
				}
			}catch(InterruptedException ie){
				// finished
			}
		}
	}

	public String getCachedImagePath(String url){
		if(url == null)
			return null;
		Cursor cursor = mDb.query(ImageCacheProvider.PROVIDER_URI, COLUMN_ID_ONLY, "url=?", new String[]{url}, null);
		String res = null;
		if(cursor.moveToFirst())
			res = mCachePath + cursor.getLong(0);
		cursor.close();
		return res;
	}

	public String makeTempPath(String url){
		ContentValues values = new ContentValues();
		values.put("created", System.currentTimeMillis());
		values.put("url", url);
		return mCachePath + mDb.insert(ImageCacheProvider.PROVIDER_URI, values).getHost();
	}

	private class ImageLoader extends Thread{
		final String mUrl;
		final OAuth mAuth;
		final CopyOnWriteArrayList<OnImageAvailableListener> mAvailableListener = new CopyOnWriteArrayList<>();
		final CopyOnWriteArrayList<ImageView> mTargetViews = new CopyOnWriteArrayList<>();
		BitmapDrawable bmp;
		long id = -1;

		@Override
		public int hashCode() {
			return mUrl.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof ImageLoader && mUrl.equals(((ImageLoader)o).mUrl);
		}

		public ImageLoader(String url, OAuth auth){
			mUrl = url;
			mAuth = auth;
		}

		public void addListener(OnImageAvailableListener listener){
			mAvailableListener.add(listener);
		}

		private int download(){
			int error;
			int readBytes;
			byte[] buffer;
			FileOutputStream out = null;
			InputStream in = null;
			ContentValues values = new ContentValues();
			values.put("created", System.currentTimeMillis());
			values.put("url", mUrl);
			HTTPRequest request = HTTPRequest.getRequest(mUrl, mAuth, false, null, false);
			if(request == null)
				return -1;
			request.submitRequest();
			switch(error = request.getStatusCode()){
				case 0: error = OnImageAvailableListener.UNAVAILABLE_NETWORK_ERROR; break;
				case 200:
					error = 0;
					File f = null;
					try{
						in = request.getInputStream();
						id = Long.parseLong(mDb.insert(ImageCacheProvider.PROVIDER_URI, values).getHost());
						f = new File(mCachePath + id);
						out = new FileOutputStream(f);
						buffer = new byte[8192];
						while((readBytes = in.read(buffer, 0, buffer.length)) > 0 && !Thread.interrupted())
							out.write(buffer, 0, readBytes);
						mDb.update(ImageCacheProvider.PROVIDER_URI, values, "_id=?", new String[]{Long.toString(id)});
					}catch(Exception e){
						error = OnImageAvailableListener.UNAVAILABLE_NETWORK_ERROR;
						if(f != null)
							f.delete();
					}finally{
						StreamTools.close(in);
						StreamTools.close(out);
					}
					break;
			}
			request.close();
			return error;
		}

		int read(){
			try{
				bmp = new BitmapDrawable(mResources, decodeFile(mCachePath + id, 1280));
				return 0;
			}catch(OutOfMemoryError e){
				return OnImageAvailableListener.UNAVAILABLE_MEMORY_ERROR;
			}catch(IOException ioe){
				return OnImageAvailableListener.UNAVAILABLE_FILESYSTEM_ERROR;
			}
		}

		@Override
		public void run() {
			try{
				Cursor cursor = mDb.query(ImageCacheProvider.PROVIDER_URI, COLUMN_ID_ONLY, "url=?", new String[]{mUrl}, null);
				int error = 0;
				if(!cursor.moveToFirst()) // download
					error = download();
				else
					id = cursor.getLong(0);
				cursor.close();
				if(error == 0)
					error = read();
				if(error == 0)
					addBitmapToMemoryCache(mUrl, bmp);
				synchronized(mScheduler){
					mLoaderMap.remove(mUrl);
					mWorkingImageLoaders.remove(this);
					mScheduler.notify();
				}
				if(Thread.interrupted())
					return;
				final int finalError = error;
				mMainThreadHandler.post(new Runnable(){
					@Override
					public void run() {
						for(OnImageAvailableListener listener : mAvailableListener)
							if(finalError == 0)
								listener.onImageAvailable(mUrl, bmp);
							else
								listener.onImageUnavailable(mUrl, finalError);
						for(ImageView view : mTargetViews){
							if(mUrl.equals(view.getTag(R.id.IMAGEVIEW_URL))){
								if(finalError == 0){
									if(view.getTag(R.id.IMAGEVIEW_PREPROCESSOR) instanceof OnDrawablePreprocessListener)
										view.setImageDrawable(((OnDrawablePreprocessListener)view.getTag(R.id.IMAGEVIEW_PREPROCESSOR)).onDrawablePreprocess(bmp));
									else
										view.setImageDrawable(bmp);
									if(view.getVisibility() == View.VISIBLE){
										AlphaAnimation anim = new AlphaAnimation(0, 1);
										anim.setInterpolator(view.getContext(), android.R.anim.accelerate_decelerate_interpolator);
										anim.setDuration(REVEAL_ANIMATION_LENGTH);
										view.clearAnimation();
										view.startAnimation(anim);
									}
								}
							}
						}
						for(Iterator<AutoApplyingDrawable> iter = mUsedDrawables.keySet().iterator(); iter.hasNext(); ){
							AutoApplyingDrawable dr = iter.next();
							if(!dr.mUrl.equals(mUrl))
								continue;
							if(finalError == 0){
								dr.applyDrawable(bmp.getConstantState().newDrawable().mutate());
							}
							iter.remove();
						}
					}
				});
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public static Bitmap decodeFile(String sPath, int nSize) throws IOException{
		BitmapFactory.Options o = new BitmapFactory.Options();
		ExifInterface exif = new ExifInterface(sPath);
		o.inJustDecodeBounds = true;

		BitmapFactory.decodeFile(sPath, o);

		int scale = (int)Math.sqrt(o.outWidth * o.outHeight / nSize / nSize);

		while(scale < 256){
			if(scale == 0)
				scale = 1;

			o.inSampleSize = scale;
			o.inDither = true;
			o.inJustDecodeBounds = false;
			try{
				Bitmap sourceBitmap = BitmapFactory.decodeFile(sPath, o);
				Matrix matrix = new Matrix();
				switch(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)){
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
					case ExifInterface.ORIENTATION_TRANSPOSE: // 90 CW,
						// Horizontal
						// Flip
						matrix.setRotate(90);
						matrix.setScale(-1, 1);
						break;
					case ExifInterface.ORIENTATION_TRANSVERSE: // 90 CCW,
						// Horizontal
						// Flip
						matrix.setRotate(270);
						matrix.setScale(-1, 1);
						break;
					case ExifInterface.ORIENTATION_NORMAL:
					default:
						return sourceBitmap;
				}
				Bitmap newBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight(), matrix, true);
				sourceBitmap.recycle();
				return newBitmap;
			}catch(Error e){
				scale *= 2;
			}
		}
		throw new OutOfMemoryError();
	}

	private static class CacheLoader extends Thread{
		Context mContext;
		ArrayList<OnImageCacheReadyListener> mListeners = new ArrayList<>();

		@Override
		public void run() {
			try{
				if(mContext.getExternalCacheDir() != null)
					mCurrentCache = new ImageCache(mContext.getContentResolver(), mContext.getExternalCacheDir().getAbsolutePath(), mContext.getResources());
				else
					mCurrentCache = new ImageCache(mContext.getContentResolver(), mContext.getCacheDir().getAbsolutePath(), mContext.getResources());
				mMainThreadHandler.post(new Runnable() {
					@Override
					public void run() {
						synchronized(mMainThreadHandler){
							for(OnImageCacheReadyListener listener : mListeners)
								listener.onImageCacheReady(mCurrentCache);
						}
					}
				});
			}catch(IOException e){
				e.printStackTrace();
			}finally{
				mCacheLoader = null;
			}
		}
	}

	public Drawable getDrawable(String url, int dimenRes, OAuth auth){
		int p = mResources.getDimensionPixelSize(dimenRes);
		return getDrawable(url, p, p, auth);
	}

	public Drawable getDrawable(String url, int width, int height, OAuth auth){
		if(url == null)
			return null;
		BitmapDrawable bd = getBitmapFromMemCache(url);
		if(bd != null)
			return bd.getConstantState().newDrawable().mutate();
		AutoApplyingDrawable dr = new AutoApplyingDrawable();
		dr.mUrl = url; dr.mWidth = width; dr.mHeight = height;
		mUsedDrawables.put(dr, url);
		prepareBitmap(url, auth, null);
		return dr;
	}

	public class AutoApplyingDrawable extends Drawable implements Runnable{
		int mWidth, mHeight;
		Drawable mDrawable;
		String mUrl;
		int mAlpha;
		ColorFilter mCf;
		boolean mFilterBitmap, mDither;
		long mAnimationEndTime = 0;

		protected AutoApplyingDrawable(){
			mAlpha = 255;
			mDither = true;
			mFilterBitmap = true;
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
		public void setDither(boolean dither) {
			mDither = dither;
			if(mDrawable != null)
				mDrawable.setDither(dither);
		}

		@Override
		public void setFilterBitmap(boolean filter) {
			mFilterBitmap = filter;
			if(mDrawable != null)
				mDrawable.setFilterBitmap(filter);
		}

		@Override
		public void draw(Canvas canvas) {
			if (mDrawable != null){
				int prevAlpha = mDrawable.getOpacity();
				Rect prevBounds = mDrawable.getBounds();

				float state = mAnimationEndTime - System.currentTimeMillis();
				state = 1 - (state / REVEAL_ANIMATION_LENGTH);
				if(state < 0) state = 0;
				if(state > 1){
					state = 1;
				}else
					mMainThreadHandler.post(this);
				mDrawable.setAlpha((int)(mAlpha * REVEAL_ANIMATION_INTERPOLATOR.getInterpolation(state)));
				mDrawable.setBounds(getBounds());
				mDrawable.draw(canvas);

				mDrawable.setAlpha(prevAlpha);
				mDrawable.setBounds(prevBounds);
			}
		}

		@Override
		public void setAlpha(int alpha) {
			mAlpha = alpha;
			if(mDrawable != null)
				mDrawable.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
			mCf = cf;
			if(mDrawable != null)
				mDrawable.setColorFilter(cf);
		}

		@Override
		public int getOpacity() {
			return mAlpha;
		}

		protected void applyDrawable(Drawable bmp){
			mDrawable = bmp;
			mDrawable.setAlpha(mAlpha);
			mDrawable.setDither(mDither);
			mDrawable.setFilterBitmap(mFilterBitmap);
			if(mCf != null)
				mDrawable.setColorFilter(mCf);
			mAnimationEndTime = System.currentTimeMillis() + REVEAL_ANIMATION_LENGTH;
			invalidateSelf();
		}

		@Override
		public void run() {
			invalidateSelf();
		}
	}

	public interface OnImageAvailableListener{
		public static final int UNAVAILABLE_NETWORK_ERROR = 1;
		public static final int UNAVAILABLE_FILESYSTEM_ERROR = 2;
		public static final int UNAVAILABLE_MEMORY_ERROR = 3;
		public void onImageAvailable(String url, BitmapDrawable bmp);
		public void onImageUnavailable(String url, int reason);
	}

	public interface OnImageCacheReadyListener{
		public void onImageCacheReady(ImageCache cache);
	}

	public interface OnDrawablePreprocessListener{
		public Drawable onDrawablePreprocess(BitmapDrawable bitmapDrawable);
	}

	public final OnDrawablePreprocessListener CIRCULAR_IMAGE_PREPROCESSOR=new OnDrawablePreprocessListener() {
		@Override
		public Drawable onDrawablePreprocess(BitmapDrawable bitmapDrawable) {
			return new RoundBitmapDrawable(mResources, bitmapDrawable);
		}
	};


}
