package com.soreepeong.darknova.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import static android.graphics.PixelFormat.TRANSLUCENT;

public class ClippedDrawable extends Drawable implements Drawable.Callback {
	public static final int CLIP_TYPE_NONE = 0;
	public static final int CLIP_TYPE_OVAL = 1;
	public static final int CLIP_TYPE_ROUND_RECT = 2;
	private final Drawable mDrawable;
	private final RectF mRectF = new RectF();
	private final Path mClipPath = new Path();
	private int mClipType, mClipParameter;
	private int mAlpha = 255;

	public ClippedDrawable(Drawable original) {
		super();
		mDrawable = original;
		mDrawable.setCallback(this);
	}

	public void clip(int clipType, int clipParam) {
		mClipType = clipType;
		mClipParameter = clipParam;
	}

	@Override
	public void draw(Canvas canvas) {
		canvas.save();
		switch (mClipType) {
			case CLIP_TYPE_NONE:
				break;
			case CLIP_TYPE_OVAL:
				mClipPath.rewind();
				mClipPath.addOval(mRectF, Path.Direction.CCW);
				canvas.clipPath(mClipPath);
				break;
			case CLIP_TYPE_ROUND_RECT:
				mClipPath.rewind();
				mClipPath.addRoundRect(mRectF, mClipParameter, mClipParameter, Path.Direction.CCW);
				canvas.clipPath(mClipPath);
				break;
		}
		mDrawable.setBounds(getBounds());
		mDrawable.setAlpha(mAlpha);
		mDrawable.draw(canvas);
		canvas.restore();
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
	public void setAlpha(int alpha) {
		mAlpha = alpha;
	}

	@Override
	public void setColorFilter(ColorFilter cf) {

	}

	@Override
	public int getOpacity() {
		return TRANSLUCENT;
	}

	@Override
	public int getAlpha() {
		return mAlpha;
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		mRectF.set(bounds);
	}

	@Override
	public void invalidateDrawable(Drawable who) {
		if (who != mDrawable) return;
		invalidateSelf();
	}

	@Override
	public void scheduleDrawable(Drawable who, Runnable what, long when) {
		if (who != mDrawable) return;
		scheduleSelf(what, when);
	}

	@Override
	public void unscheduleDrawable(Drawable who, Runnable what) {
		if (who != mDrawable) return;
		unscheduleSelf(what);
	}
}