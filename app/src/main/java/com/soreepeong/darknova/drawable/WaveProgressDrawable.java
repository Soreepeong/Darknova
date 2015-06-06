package com.soreepeong.darknova.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * @author Soreepeong
 */
public class WaveProgressDrawable extends Drawable {
	protected static final int MAX_PERCENTAGE = 10000;
	protected static final int CHANGE_TIME = 300;
	protected static final Interpolator mInterpolator = new DecelerateInterpolator();
	final Path mBasePath = new Path();
	protected int mProgress;
	protected long mProgressChangeTimeTarget;
	protected int mTargetProgress;
	protected Paint mFillPaint = new Paint();
	Rect mBounds = new Rect();
	float mLoopWidth;
	int mWaveHeightPeriod;
	int mWaveLengthPeriod;
	float mPower;
	float mWaveHeight;
	int mAlpha;
	int mDuplicateDeltaPeriod;
	boolean mPerformanceMode;

	public WaveProgressDrawable() {
		mFillPaint.setAntiAlias(true);
		mFillPaint.setARGB(255, 255, 255, 255);
		mLoopWidth = 0.5f;
		mPower = 2.1f;
		mWaveHeightPeriod = 1200;
		mWaveLengthPeriod = 1600;
		mProgress = 50;
		mWaveHeight = 0.1f;
		mDuplicateDeltaPeriod = 500;
		mAlpha = 255;
	}

	public int getPercentage() {
		return mTargetProgress;
	}

	public void setProgressPercentage(int percentage) {
		long now = System.currentTimeMillis();
		if (now < mProgressChangeTimeTarget)
			mProgress += (int) ((getPercentage() - mProgress) * mInterpolator.getInterpolation(1 - (float) (mProgressChangeTimeTarget - now) / CHANGE_TIME));
		mTargetProgress = percentage * MAX_PERCENTAGE / 100;
		mProgressChangeTimeTarget = now + CHANGE_TIME;
		invalidateSelf();
	}

	@Override
	public void draw(Canvas canvas) {
		if (mBounds == null)
			return;
		int targetProgress = getPercentage();
		if (mProgress == MAX_PERCENTAGE && targetProgress == MAX_PERCENTAGE) {
			mFillPaint.setAlpha(mAlpha);
			canvas.drawRect(mBounds, mFillPaint);
			canvas.drawRect(mBounds, mFillPaint);
		} else {
			long now = System.currentTimeMillis();
			canvas.save();
			canvas.clipRect(mBounds.left, mBounds.top, mBounds.right, mBounds.bottom);

			int renderProgress;
			if (now > mProgressChangeTimeTarget)
				renderProgress = mProgress = targetProgress;
			else
				renderProgress = mProgress + (int) ((targetProgress - mProgress) * mInterpolator.getInterpolation(1 - (float) (mProgressChangeTimeTarget - now) / CHANGE_TIME));

			mFillPaint.setAlpha((int) (mAlpha * (0.2f + 0.8f * renderProgress / MAX_PERCENTAGE)));
			float maxWaveHeight = renderProgress > MAX_PERCENTAGE - mWaveHeight * MAX_PERCENTAGE ? (MAX_PERCENTAGE - renderProgress) / (float) MAX_PERCENTAGE : mWaveHeight;
			int wavePosition = (int) ((mBounds.bottom - mBounds.top) * maxWaveHeight / 2) + renderProgress * (mBounds.bottom - mBounds.top) / MAX_PERCENTAGE;
			int waveHeight = (int) ((mBounds.bottom - mBounds.top) * maxWaveHeight * (0.4 + 0.6 * (1 - Math.pow(Math.abs(mWaveHeightPeriod / 2 - now % mWaveHeightPeriod) * 2. / mWaveHeightPeriod, mPower))));
			int waveLoopWidth = (int) (mLoopWidth * (mBounds.right - mBounds.left));
			int waveDx = (int) ((now % mWaveLengthPeriod) / (float) mWaveLengthPeriod * waveLoopWidth);
			double delta = Math.PI * 2 * (now % (Math.PI * 2)) / mDuplicateDeltaPeriod;

			if (mPerformanceMode) {
				canvas.drawRect(mBounds.left, mBounds.bottom - wavePosition + (float) (waveHeight / 2. * Math.sin(delta)), mBounds.right, mBounds.bottom, mFillPaint);
				canvas.drawRect(mBounds.left, mBounds.bottom - wavePosition + (float) (waveHeight / 2. * Math.cos(delta)), mBounds.right, mBounds.bottom, mFillPaint);
			} else {
				mBasePath.rewind();
				mBasePath.moveTo(mBounds.left - waveDx * 2, mBounds.bottom + waveHeight);
				mBasePath.lineTo(mBounds.right + waveDx * 2, mBounds.bottom + waveHeight);
				mBasePath.lineTo(mBounds.right + waveDx, mBounds.bottom - wavePosition);
				for (int i = mBounds.right + waveDx; ; i -= waveLoopWidth) {
					mBasePath.quadTo(i - waveLoopWidth / 4, mBounds.bottom - wavePosition - waveHeight / 2, i - waveLoopWidth / 2, mBounds.bottom - wavePosition);
					mBasePath.quadTo(i - waveLoopWidth * 3 / 4, mBounds.bottom - wavePosition + waveHeight / 2, i - waveLoopWidth, mBounds.bottom - wavePosition);
					if (i < mBounds.left - waveDx * 2)
						break;
				}
				mBasePath.close();
				float dx = (float) (waveHeight / 2. * Math.sin(delta));
				canvas.translate(0, dx);
				canvas.drawPath(mBasePath, mFillPaint);
				canvas.translate(waveDx, -dx + (float) (waveHeight / 2. * Math.cos(delta)));
				canvas.drawPath(mBasePath, mFillPaint);
				if (System.currentTimeMillis() - now > 1)
					mPerformanceMode = true;
			}
			canvas.restore();
			invalidateSelf();
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
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		mBounds.set(bounds);
	}

	@Override
	public int getOpacity() {
		return mAlpha;
	}
}
