package com.soreepeong.darknova.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;

public class ClippedBitmapDrawable extends BitmapDrawable {
	public static final int CLIP_TYPE_NONE = 0;
	public static final int CLIP_TYPE_OVAL = 1;
	public static final int CLIP_TYPE_ROUND_RECT = 2;
	private final Paint mPaint;
	private final RectF mRectF = new RectF();
	private int mClipType, mClipParameter;

	public ClippedBitmapDrawable(Resources res, Bitmap bitmap) {
		super(res, bitmap);
		mPaint = getPaint();
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
	}

	public ClippedBitmapDrawable(Resources res, BitmapDrawable bitmap) {
		super(res, bitmap.getBitmap());
		mPaint = getPaint();
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setShader(new BitmapShader(getBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
	}

	public void clip(int clipType, int clipParam) {
		mClipType = clipType;
		mClipParameter = clipParam;
	}

	@Override
	public void draw(Canvas canvas) {
		switch (mClipType) {
			case CLIP_TYPE_NONE:
				canvas.drawRect(mRectF, mPaint);
				break;
			case CLIP_TYPE_OVAL:
				canvas.drawOval(mRectF, mPaint);
				break;
			case CLIP_TYPE_ROUND_RECT:
				canvas.drawRoundRect(mRectF, mClipParameter, mClipParameter, mPaint);
				break;
		}
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		mRectF.set(bounds);
	}
}