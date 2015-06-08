package com.soreepeong.darknova.drawable;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * @author Soreepeong
 */
public class WaveProgressDrawable extends Drawable {
	protected static final int MAX_PERCENTAGE = 10000;
	protected static final int MAX_IN_PERCENTAGE = 1000;
	protected static final int CHANGE_TIME = 300;
	protected static final Interpolator mInterpolator = new DecelerateInterpolator();
	final Rect mBounds = new Rect();
	final RectF mBoundF = new RectF();
	final Rect mBitmapBounds = new Rect();
	final Paint mFillPaint = new Paint();
	final Paint mBackgroundPaint = new Paint();
	final Paint mClipMaskPaint = new Paint();
	final Paint mFinalPaint = new Paint();
	protected int mProgress;
	protected long mProgressChangeTimeTarget;
	protected int mTargetProgress;
	float mLoopWidth;
	int mWaveHeightPeriod;
	int mWaveLengthPeriod;
	float mPower;
	float mWaveHeight;
	int mAlpha;
	int mDuplicateDeltaPeriod;
	boolean mClipOval;
	Bitmap mWaveBitmap;
	Bitmap mBackBufferBitmap;
	Canvas mBackBufferCanvas;

	public WaveProgressDrawable() {
		mFillPaint.setAntiAlias(true);
		mFillPaint.setColor(-1);
		mFillPaint.setDither(true);
		mFillPaint.setFilterBitmap(true);
		mFillPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
		mClipMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		mBackgroundPaint.setARGB(255, 0, 0, 0);
		mBackgroundPaint.setAntiAlias(true);
		mLoopWidth = 0.5f;
		mPower = 2.1f;
		mWaveHeightPeriod = 1200;
		mWaveLengthPeriod = 1600;
		mProgress = 50;
		mWaveHeight = 0.1f;
		mDuplicateDeltaPeriod = 500;
		mAlpha = 255;
	}

	public void setOval(boolean b) {
		mClipOval = b;
	}

	public int getPercentage() {
		return mTargetProgress;
	}

	public void setProgressPercentage(int percentage) {
		long now = System.currentTimeMillis();
		if (now < mProgressChangeTimeTarget)
			mProgress += (int) ((getPercentage() - mProgress) * mInterpolator.getInterpolation(1 - (float) (mProgressChangeTimeTarget - now) / CHANGE_TIME));
		mTargetProgress = percentage * MAX_PERCENTAGE / MAX_IN_PERCENTAGE;
		mProgressChangeTimeTarget = now + CHANGE_TIME;
		invalidateSelf();
	}

	private void prepareBitmap() {
		int waveLoopWidth = (int) (mLoopWidth * mBounds.width()) * 2;
		int waveHeight = (int) (mBounds.height() * mWaveHeight);
		if (mWaveBitmap != null && mWaveBitmap.getWidth() == mBounds.width() + waveLoopWidth && mWaveBitmap.getHeight() == waveHeight)
			return;
		mWaveBitmap = Bitmap.createBitmap(mBounds.width() + waveLoopWidth, waveHeight, Bitmap.Config.ARGB_8888);
		Path p = new Path();
		p.moveTo(0, waveHeight);
		p.lineTo(mWaveBitmap.getWidth(), waveHeight);
		p.lineTo(mWaveBitmap.getWidth(), waveHeight / 2);
		for (int i = mWaveBitmap.getWidth(); i >= 0; i -= waveLoopWidth) {
			p.quadTo(i - waveLoopWidth / 4, 0, i - waveLoopWidth / 2, waveHeight / 2);
			p.quadTo(i - waveLoopWidth * 3 / 4, waveHeight, i - waveLoopWidth, waveHeight / 2);
		}
		p.close();
		Canvas c = new Canvas(mWaveBitmap);
		Paint mPathDrawer = new Paint();
		mPathDrawer.setAntiAlias(true);
		mPathDrawer.setColor(-1);
		c.drawPath(p, mPathDrawer);
	}

	private void prepareBufferBitmap() {
		if (mBackBufferBitmap != null && mBackBufferBitmap.getHeight() == mBounds.height() && mBackBufferBitmap.getWidth() == mBounds.width())
			return;
		mBackBufferBitmap = Bitmap.createBitmap(mBounds.width(), mBounds.height(), Bitmap.Config.ARGB_8888);
		mBackBufferCanvas = new Canvas(mBackBufferBitmap);
	}

	@Override
	public void draw(Canvas canvas) {
		if (mBounds == null || mBounds.width() == 0 || mBounds.height() == 0)
			return;
		int targetProgress = getPercentage();
		long now = System.currentTimeMillis();
		prepareBitmap();
		prepareBufferBitmap();
		mBackBufferCanvas.save();
		mBackBufferCanvas.translate(-mBounds.left, -mBounds.top);
		if (mClipOval) {
			mBackBufferBitmap.eraseColor(Color.TRANSPARENT);
			mBackBufferCanvas.drawOval(mBoundF, mBackgroundPaint);
			mBackBufferCanvas.drawOval(mBoundF, mClipMaskPaint);
		} else {
			mBackBufferCanvas.drawRect(mBounds, mBackgroundPaint);
			mBackBufferCanvas.drawRect(mBounds, mClipMaskPaint);
		}
		if (mProgress == MAX_PERCENTAGE && targetProgress == MAX_PERCENTAGE) {
			mFillPaint.setAlpha(255);
			mBackBufferCanvas.drawRect(mBounds, mFillPaint);
		} else {
			int renderProgress;
			if (now > mProgressChangeTimeTarget)
				renderProgress = mProgress = targetProgress;
			else
				renderProgress = mProgress + (int) ((targetProgress - mProgress) * mInterpolator.getInterpolation(1 - (float) (mProgressChangeTimeTarget - now) / CHANGE_TIME));

			mFillPaint.setAlpha((int) (255 * (0.2f + 0.8f * renderProgress / MAX_PERCENTAGE)));
			float maxWaveHeight = renderProgress > MAX_PERCENTAGE - mWaveHeight * MAX_PERCENTAGE ? (MAX_PERCENTAGE - renderProgress) / (float) MAX_PERCENTAGE : mWaveHeight;
			int wavePosition = (int) (mBounds.height() * maxWaveHeight / 2) + renderProgress * mBounds.height() / MAX_PERCENTAGE;
			int waveHeight = (int) (mBounds.height() * maxWaveHeight * (0.4 + 0.6 * (1 - Math.pow(Math.abs(mWaveHeightPeriod / 2 - now % mWaveHeightPeriod) * 2. / mWaveHeightPeriod, mPower))));
			int waveLoopWidth = (int) (mLoopWidth * mBounds.width());
			int waveDx = (int) ((now % mWaveLengthPeriod) / (float) mWaveLengthPeriod * waveLoopWidth) * 2;
			double delta = Math.PI * 2 * (now % (Math.PI * 2)) / mDuplicateDeltaPeriod;
			int dsin = (int) (waveHeight / 2. * Math.sin(delta));
			int dcos = (int) (waveHeight / 2. * Math.cos(delta));

			mBitmapBounds.bottom = (mBitmapBounds.top = mBounds.bottom - wavePosition - waveHeight / 2 + dsin) + waveHeight;
			mBitmapBounds.right = (mBitmapBounds.left = mBounds.left - waveDx) + mWaveBitmap.getWidth();
			mBackBufferCanvas.drawBitmap(mWaveBitmap, null, mBitmapBounds, mFillPaint);
			mBackBufferCanvas.drawRect(mBounds.left, mBitmapBounds.bottom, mBounds.right, mBounds.bottom, mFillPaint);

			mBitmapBounds.bottom = (mBitmapBounds.top = mBounds.bottom - wavePosition - waveHeight / 2 + dcos) + waveHeight;
			mBitmapBounds.left = (mBitmapBounds.right = mBounds.right + (waveDx * 2) % (waveLoopWidth * 2)) - mWaveBitmap.getWidth();
			mBackBufferCanvas.drawBitmap(mWaveBitmap, null, mBitmapBounds, mFillPaint);
			mBackBufferCanvas.drawRect(mBounds.left, mBitmapBounds.bottom, mBounds.right, mBounds.bottom, mFillPaint);

			invalidateSelf();
		}
		mBackBufferCanvas.restore();
		mFinalPaint.setAlpha(mAlpha);
		canvas.drawBitmap(mBackBufferBitmap, mBounds.left, mBounds.top, mFinalPaint);
	}

	@Override
	public void setAlpha(int alpha) {
		mAlpha = alpha;
	}

	@Override
	public void setColorFilter(ColorFilter cf) {

	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		mBounds.set(bounds);
		mBoundF.set(bounds);
	}

	@Override
	public int getOpacity() {
		return mAlpha;
	}
}
