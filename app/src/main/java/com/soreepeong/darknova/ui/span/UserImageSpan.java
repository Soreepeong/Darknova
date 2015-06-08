package com.soreepeong.darknova.ui.span;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.twitter.Tweeter;

import java.lang.ref.WeakReference;

/**
 * @author Soreepeong
 */
public class UserImageSpan extends ReplacementSpan implements SelfInvalidatingSpan, Drawable.Callback {
	private final TweeterDrawable mDrawable;
	private final Tweeter mTweeter;
	private int mLineHeight, mActualLineHeight;
	private WeakReference<Callback> mCallback;

	public UserImageSpan(Tweeter tweeter, ImageCache imageCache, int lineHeight) {
		mTweeter = tweeter;
		mLineHeight = lineHeight;
		mDrawable = new TweeterDrawable(imageCache);
		mDrawable.setCallback(this);
	}

	public Tweeter getTweeter() {
		return mTweeter;
	}

	public void setLineHeight(int h) {
		mLineHeight = h;
	}

	@Override
	public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
		return mActualLineHeight = (int) (mLineHeight + paint.getFontMetrics().descent);
	}

	public boolean isPreparing() {
		return mDrawable.isPreparing();
	}

	@Override
	public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
		if (mDrawable.getUrl() == null) {
			canvas.drawText(text, start, end, x, y, paint);
		} else {
			canvas.save();
			canvas.translate(x, y - mLineHeight);
			mDrawable.setSize(mActualLineHeight);
			mDrawable.setBounds(0, 0, mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight());
			mDrawable.setAlpha(255);
			mDrawable.draw(canvas);
			canvas.restore();
		}
	}

	@Override
	public void setCallback(Callback callback) {
		if (callback == null)
			mCallback = null;
		else
			mCallback = new WeakReference<>(callback);
	}

	@Override
	public void invalidateDrawable(Drawable who) {
		Callback cb = mCallback == null ? null : mCallback.get();
		if (cb != null)
			cb.invalidateSpan(this);
	}

	@Override
	public void scheduleDrawable(Drawable who, Runnable what, long when) {
		Callback cb = mCallback == null ? null : mCallback.get();
		if (cb != null)
			cb.scheduleSpan(this, what, when);
	}

	@Override
	public void unscheduleDrawable(Drawable who, Runnable what) {
		Callback cb = mCallback == null ? null : mCallback.get();
		if (cb != null)
			cb.unscheduleSpan(this, what);
	}

	private class TweeterDrawable extends ImageCache.AutoApplyingDrawable implements Tweeter.OnUserInformationChangedListener {
		public ImageCache mCache;

		public TweeterDrawable(ImageCache cache) {
			mCache = cache;
			mTweeter.addOnChangeListener(this);
			updateUrl(mTweeter.getProfileImageUrl(), mCache);
		}

		@Override
		public void onUserInformationChanged(Tweeter tweeter) {
			updateUrl(tweeter.getProfileImageUrl(), mCache);
		}
	}
}
