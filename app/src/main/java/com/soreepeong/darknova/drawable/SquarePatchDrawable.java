package com.soreepeong.darknova.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;

/**
 * @author Soreepeong
 */
public class SquarePatchDrawable extends Drawable implements Drawable.Callback {

	private final ArrayList<Drawable> mDrawables = new ArrayList<>();
	private int size = 1;
	private int mAlpha = 255;
	private Rect mBounds = new Rect(), mTempBounds = new Rect();

	public void addDrawable(Drawable d) {
		d.setCallback(this);
		mDrawables.add(d);
		size = 1;
		while (size * size < mDrawables.size()) size++;
	}

	@Override
	public void draw(Canvas canvas) {
		int x = 0, y = 0;
		Paint p = new Paint();
		p.setColor(-1);
		mTempBounds.set(0, 0, mBounds.width() / size, mBounds.height() / size);
		for (Drawable d : mDrawables) {
			canvas.save();
			canvas.translate(-mBounds.left + mTempBounds.right * x, -mBounds.top + mTempBounds.bottom * y);
			d.setAlpha(mAlpha);
			canvas.drawRect(mTempBounds, p);
			d.setBounds(mTempBounds);
			d.draw(canvas);
			canvas.restore();
			x++;
			if (x >= size) {
				x = 0;
				y++;
			}
		}
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
		return mAlpha;
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		mBounds.set(bounds);
	}

	@Override
	public void invalidateDrawable(Drawable who) {
		if (mDrawables.contains(who) && getCallback() != null)
			getCallback().invalidateDrawable(this);
	}

	@Override
	public void scheduleDrawable(Drawable who, Runnable what, long when) {
		if (mDrawables.contains(who) && getCallback() != null)
			getCallback().scheduleDrawable(this, what, when);
	}

	@Override
	public void unscheduleDrawable(Drawable who, Runnable what) {
		if (mDrawables.contains(who) && getCallback() != null)
			getCallback().unscheduleDrawable(this, what);
	}
}
