package com.soreepeong.darknova.drawable;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * @author Soreepeong
 */
public class WaveProgressDrawable extends Drawable {
	public static final int CLIP_TYPE_NONE = 0;
	public static final int CLIP_TYPE_OVAL = 1;
	public static final int CLIP_TYPE_ROUND_RECT = 2;
	public static final int CLIP_TYPE_PATH = 3;
	protected static final int MAX_PERCENTAGE = 10000;
	protected static final int MAX_IN_PERCENTAGE = 1000;
	protected static final int CHANGE_TIME = 300;
	protected static final Interpolator mInterpolator = new DecelerateInterpolator();
	private static final int SLOW_FACTOR = 2;
	private static final int UPDATE_INTERVAL = 50;
	private static final int BITMAP_CREATE_DELAY = 1000;
	protected final Rect mBounds = new Rect();
	protected final RectF mBoundF = new RectF();
	protected final Paint mFinalPaint = new Paint();
	private final Rect mBitmapBounds = new Rect();
	private final Paint mFillPaint = new Paint();
	private final Paint mBackgroundPaint = new Paint();
	private final Paint mClipMaskPaint = new Paint();
	private final Runnable mInvalidator = new Runnable() {
		@Override
		public void run() {
			invalidateSelf();
		}
	};
	protected int mProgress, mTargetProgress;
	protected long mProgressChangeTimeTarget;
	private int mClipType, mClipParameter;
	private float mLoopWidth, mPower, mWaveHeight;
	private int mWaveHeightPeriod, mWaveLengthPeriod, mAlpha, mDuplicateDeltaPeriod;
	private Bitmap mWaveBitmap, mBackBufferBitmap;
	private Canvas mBackBufferCanvas;
	private long mBitmapCreateTime;

	public WaveProgressDrawable() {
		mFillPaint.setAntiAlias(true);
		mFillPaint.setColor(-1);
		mFillPaint.setDither(true);
		mFillPaint.setFilterBitmap(true);
		mFillPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
		mClipMaskPaint.setAntiAlias(true);
		mClipMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		mBackgroundPaint.setARGB(255, 0, 0, 0);
		mBackgroundPaint.setAntiAlias(true);

		mFinalPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
		mLoopWidth = 0.5f;
		mPower = 2.1f;
		mWaveHeightPeriod = 1200 * SLOW_FACTOR;
		mWaveLengthPeriod = 1600 * SLOW_FACTOR;
		mDuplicateDeltaPeriod = 500 * SLOW_FACTOR;
		mProgress = 50;
		mWaveHeight = 0.1f;
		mAlpha = 255;
		mBitmapCreateTime = System.currentTimeMillis() + BITMAP_CREATE_DELAY;
	}

	public void clip(int clipType, int clipParam) {
		mClipType = clipType;
		mClipParameter = clipParam;
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

	protected Canvas prepareBufferCanvas(boolean createIfNonExist) {
		if (mBackBufferBitmap != null && mBackBufferBitmap.getHeight() == mBounds.height() && mBackBufferBitmap.getWidth() == mBounds.width())
			return mBackBufferCanvas;
		if (!createIfNonExist)
			return null;
		mBackBufferBitmap = Bitmap.createBitmap(mBounds.width(), mBounds.height(), Bitmap.Config.ARGB_8888);
		mBackBufferCanvas = new Canvas(mBackBufferBitmap);
		return mBackBufferCanvas;
	}

	protected Bitmap prepareBufferBitmap(boolean createIfNonExist) {
		if (mBackBufferBitmap != null && mBackBufferBitmap.getHeight() == mBounds.height() && mBackBufferBitmap.getWidth() == mBounds.width())
			return mBackBufferBitmap;
		if (!createIfNonExist)
			return null;
		mBackBufferBitmap = Bitmap.createBitmap(mBounds.width(), mBounds.height(), Bitmap.Config.ARGB_8888);
		mBackBufferCanvas = new Canvas(mBackBufferBitmap);
		return mBackBufferBitmap;
	}

	@Override
	public void draw(Canvas canvas) {
		long now = System.currentTimeMillis();
		if (mBounds == null || mBounds.width() == 0 || mBounds.height() == 0)
			return;
		mBackgroundPaint.setAlpha(mAlpha);
		switch (mClipType) {
			case CLIP_TYPE_NONE:
				canvas.drawRect(mBounds, mBackgroundPaint);
				break;
			case CLIP_TYPE_OVAL:
				canvas.drawOval(mBoundF, mBackgroundPaint);
				break;
			case CLIP_TYPE_ROUND_RECT:
				canvas.drawRoundRect(mBoundF, mClipParameter, mClipParameter, mBackgroundPaint);
				break;
		}
		if (mBackBufferBitmap == null) {
			if (mBitmapCreateTime < now)
				prepareBufferCanvas(true);
			else {
				unscheduleSelf(mInvalidator);
				scheduleSelf(mInvalidator, SystemClock.uptimeMillis() + (mBitmapCreateTime - now) + 5);
				return;
			}
		}
		Canvas backCanvas = prepareBufferCanvas(true);
		int targetProgress = getPercentage();
		prepareBitmap();
		backCanvas.save();
		backCanvas.translate(-mBounds.left, -mBounds.top);
		mBackBufferBitmap.eraseColor(0xFF000000);
		switch (mClipType) {
			case CLIP_TYPE_NONE:
				backCanvas.drawRect(mBounds, mClipMaskPaint);
				break;
			case CLIP_TYPE_OVAL:
				backCanvas.drawOval(mBoundF, mClipMaskPaint);
				break;
			case CLIP_TYPE_ROUND_RECT:
				backCanvas.drawRoundRect(mBoundF, mClipParameter, mClipParameter, mClipMaskPaint);
				break;
		}
		if (mProgress == MAX_PERCENTAGE && targetProgress == MAX_PERCENTAGE) {
			mFillPaint.setAlpha(255);
			backCanvas.drawRect(mBounds, mFillPaint);
		} else {
			int renderProgress;
			if (now > mProgressChangeTimeTarget)
				renderProgress = mProgress = targetProgress;
			else
				renderProgress = mProgress + (int) ((targetProgress - mProgress) * mInterpolator.getInterpolation(1 - (float) (mProgressChangeTimeTarget - now) / CHANGE_TIME));

			float maxWaveHeight = renderProgress > MAX_PERCENTAGE - mWaveHeight * MAX_PERCENTAGE ? (MAX_PERCENTAGE - renderProgress) / (float) MAX_PERCENTAGE : mWaveHeight;
			int wavePosition = (int) (mBounds.height() * maxWaveHeight / 2) + renderProgress * mBounds.height() / MAX_PERCENTAGE;
			int waveHeight = (int) (mBounds.height() * maxWaveHeight * (0.4 + 0.6 * (1 - Math.pow(Math.abs(mWaveHeightPeriod / 2 - now % mWaveHeightPeriod) * 2. / mWaveHeightPeriod, mPower))));
			int waveLoopWidth = (int) (mLoopWidth * mBounds.width());
			int waveDx = (int) ((now % mWaveLengthPeriod) / (float) mWaveLengthPeriod * waveLoopWidth) * 2;
			double delta = Math.PI * 2 * (now % (Math.PI * 2)) / mDuplicateDeltaPeriod;
			int dsin = (int) (waveHeight / 2. * Math.sin(delta));
			int dcos = (int) (waveHeight / 2. * Math.cos(delta));

			mFillPaint.setAlpha((int) (255 * (0.2f + 0.8f * renderProgress / MAX_PERCENTAGE)));

			mBitmapBounds.bottom = (mBitmapBounds.top = mBounds.bottom - wavePosition - waveHeight / 2 + dsin) + waveHeight;
			mBitmapBounds.right = (mBitmapBounds.left = mBounds.left - waveDx) + mWaveBitmap.getWidth();
			backCanvas.drawBitmap(mWaveBitmap, null, mBitmapBounds, mFillPaint);
			backCanvas.drawRect(mBounds.left, mBitmapBounds.bottom, mBounds.right, mBounds.bottom, mFillPaint);

			mBitmapBounds.bottom = (mBitmapBounds.top = mBounds.bottom - wavePosition - waveHeight / 2 + dcos) + waveHeight;
			mBitmapBounds.left = (mBitmapBounds.right = mBounds.right + (waveDx * 2) % (waveLoopWidth * 2)) - mWaveBitmap.getWidth();
			backCanvas.drawBitmap(mWaveBitmap, null, mBitmapBounds, mFillPaint);
			backCanvas.drawRect(mBounds.left, mBitmapBounds.bottom, mBounds.right, mBounds.bottom, mFillPaint);

			if (renderProgress != targetProgress)
				invalidateSelf();
			else {
				unscheduleSelf(mInvalidator);
				scheduleSelf(mInvalidator, SystemClock.uptimeMillis() + UPDATE_INTERVAL);
			}
		}
		backCanvas.restore();
		mFinalPaint.setAlpha(mAlpha);
		canvas.drawBitmap(prepareBufferBitmap(false), mBounds.left, mBounds.top, mFinalPaint);
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
