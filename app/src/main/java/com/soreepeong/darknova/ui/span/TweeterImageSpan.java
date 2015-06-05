package com.soreepeong.darknova.ui.span;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.twitter.Tweeter;

/**
 * @author Soreepeong
 */
public class TweeterImageSpan extends ReplacementSpan {
	private final TweeterDrawable mDrawable;
	private int mLineHeight, mActualLineHeight;

	public TweeterImageSpan(Tweeter tweeter, ImageCache imageCache, int lineHeight) {
		mDrawable = new TweeterDrawable(tweeter, imageCache);
		mLineHeight = lineHeight;
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

	private static class TweeterDrawable extends ImageCache.AutoApplyingDrawable implements Tweeter.OnUserInformationChangedListener {
		public ImageCache mCache;

		public TweeterDrawable(Tweeter t, ImageCache cache) {
			mCache = cache;
			t.addOnChangeListener(this);
			updateUrl(t.getProfileImageUrl(), mCache);
		}

		@Override
		public void onUserInformationChanged(Tweeter tweeter) {
			updateUrl(tweeter.getProfileImageUrl(), mCache);
		}
	}
}
