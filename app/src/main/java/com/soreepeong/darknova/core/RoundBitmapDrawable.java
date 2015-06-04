package com.soreepeong.darknova.core;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;

/**
 * Round bitmap drawable
 */
public class RoundBitmapDrawable extends BitmapDrawable {
	private final BitmapDrawable mBitmap;
	private final Paint mPaint;
	private final RectF mRectF = new RectF();

	public RoundBitmapDrawable(Resources res, Bitmap bitmap) {
		super(res, bitmap);
		mBitmap = this;
		mPaint = getPaint();
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
	}

	public RoundBitmapDrawable(Resources res, BitmapDrawable bitmap) {
		super(res, bitmap.getBitmap());
		mBitmap = bitmap;
		mPaint = getPaint();
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setShader(new BitmapShader(getBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
	}

	@Override
	public void draw(Canvas canvas) {
		canvas.drawOval(mRectF, mPaint);
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		mRectF.set(bounds);
	}
}