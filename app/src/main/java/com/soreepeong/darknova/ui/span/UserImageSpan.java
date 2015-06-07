package com.soreepeong.darknova.ui.span;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.twitter.Tweeter;

/**
 * @author Soreepeong
 */
public class UserImageSpan extends ReplacementSpan {
	private final TweeterDrawable mDrawable;
	private final Tweeter mTweeter;
	private int mLineHeight, mActualLineHeight;

	public UserImageSpan(Tweeter tweeter, ImageCache imageCache, int lineHeight) {
		mTweeter = tweeter;
		mLineHeight = lineHeight;
		mDrawable = new TweeterDrawable(imageCache);
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
		canvas.save();
		canvas.translate(x, y - mLineHeight);

		mDrawable.setSize(mActualLineHeight);
		mDrawable.setBounds(0, 0, mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight());
		mDrawable.setAlpha(255);
		mDrawable.draw(canvas);

		canvas.restore();
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
