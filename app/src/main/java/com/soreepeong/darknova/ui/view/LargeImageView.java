package com.soreepeong.darknova.ui.view;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.OverScroller;

import com.soreepeong.darknova.core.ImageCache;

import java.util.Arrays;

import pl.droidsonroids.gif.GifDrawable;

/**
 * Large image viewer with pinch-zoom, double tap-zoom, etc.
 *
 * @author Soreepeong
 */
public class LargeImageView extends View implements Runnable, Handler.Callback {

	public static final int MESSAGE_IMAGE_VIEW_ERROR = 8,
			MESSAGE_IMAGE_VIEW_CREATE_TO_VIEW = 9, MESSAGE_IMAGE_VIEW_APPLY_TO_VIEW = 10,
			MESSAGE_IMAGE_VIEW_REZOOM = 11, MESSAGE_IMAGE_VIEW_REPOSITION = 12,
			MESSAGE_IMAGE_VIEW_APPLY_TO_SINGLE_VIEW = 15, MESSAGE_IMAGE_VIEW_LOADED = 16,
			MESSAGE_IMAGE_VIEW_ROTATE = 17;

	private final int mAnimateDuration;

	private final GestureDetector mGesture;
	private final ViewConfiguration mConf;
	private final OverScroller mScrollerHorizontal, mScrollerVertical;
	private final Handler mHandler = new Handler(this);
	private final Interpolator mInterpolator = new AccelerateDecelerateInterpolator();
	private final EdgeEffectCompat mEdgeGlowTop, mEdgeGlowLeft, mEdgeGlowRight, mEdgeGlowBottom;

	private float mProportion = 1;
	private Thread mLoaderThread;
	private String mImgPath;
	private int mWidth, mHeight;
	private int mMaxX, mMidX, mMinX, mMaxY, mMidY, mMinY;
	private float mZoom = 0, mZoomMin = 0.5f;
	private float mDirection = 0; // in degrees
	private float mAnimateSourceDirection, mAnimateTargetDirection;
	private boolean mRotatedVertically;
	private OnClickListener mClickListener;
	private GifDrawable mGifDrawable;
	private Drawable mDrawable;
	private boolean mDisableRotation;
	private OnImageViewLoadFinishedListener mLoadListener;
	private boolean mPrepared; // if mLoadListener is notified of load completion

	private float mPreviousZoom;
	private int mPreviousMidX, mPreviousMidY;
	private float mPinchResizeTouchScale;
	private boolean mIsMoving, mIsZooming;
	private float mTouchAvgX, mTouchAvgY, nAutoZoomOldZoom;
	private long nAutoZoomStartTime;
	private int touchPointerCount;
	private float mZoomBaseY, mZoomBaseX, nDragZoomBegin;
	private boolean bZoomDragging, bIsDoubleTap;

	private OnViewerParamChangedListener mOnViewerParamChangedListener;

	public LargeImageView(Context context) {
		super(context);
		mGesture = new GestureDetector(getContext(), new SimpleGestureListener());
		mConf = ViewConfiguration.get(context);
		mScrollerHorizontal = new OverScroller(context);
		mScrollerVertical = new OverScroller(context);
		mEdgeGlowTop = new EdgeEffectCompat(context);
		mEdgeGlowRight = new EdgeEffectCompat(context);
		mEdgeGlowBottom = new EdgeEffectCompat(context);
		mEdgeGlowLeft = new EdgeEffectCompat(context);
		mAnimateDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
		setWillNotDraw(false);
	}

	public LargeImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mGesture = new GestureDetector(getContext(), new SimpleGestureListener());
		mConf = ViewConfiguration.get(context);
		mScrollerHorizontal = new OverScroller(context);
		mScrollerVertical = new OverScroller(context);
		mEdgeGlowTop = new EdgeEffectCompat(context);
		mEdgeGlowRight = new EdgeEffectCompat(context);
		mEdgeGlowBottom = new EdgeEffectCompat(context);
		mEdgeGlowLeft = new EdgeEffectCompat(context);
		mAnimateDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
		setWillNotDraw(false);
	}

	public LargeImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mGesture = new GestureDetector(getContext(), new SimpleGestureListener());
		mConf = ViewConfiguration.get(context);
		mScrollerHorizontal = new OverScroller(context);
		mScrollerVertical = new OverScroller(context);
		mEdgeGlowTop = new EdgeEffectCompat(context);
		mEdgeGlowRight = new EdgeEffectCompat(context);
		mEdgeGlowBottom = new EdgeEffectCompat(context);
		mEdgeGlowLeft = new EdgeEffectCompat(context);
		mAnimateDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
		setWillNotDraw(false);
	}

	public int getImageWidth() {
		return mRotatedVertically ? mHeight : mWidth;
	}

	public int getImageHeight() {
		return mRotatedVertically ? mWidth : mHeight;
	}

	/**
	 * Reset the view
	 */
	public void removeImage() {
		if (mLoaderThread != null) {
			mLoaderThread.interrupt();
			mLoaderThread = null;
		}
		mImgPath = null;
		mZoom = 0;
		mDirection = 0;
		mWidth = 0;
		mHeight = 0;
		mDrawable = null;
		mIsMoving = mIsZooming = false;
		mRotatedVertically = false;
		if (mGifDrawable != null)
			mGifDrawable.stop();
		mGifDrawable = null;
		mDisableRotation = true;
		mPrepared = false;
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_CREATE_TO_VIEW);
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_APPLY_TO_VIEW);
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_APPLY_TO_SINGLE_VIEW);
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_LOADED);
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_ROTATE);
	}

	/**
	 * Is any image loaded?
	 *
	 * @return If the view has image
	 */
	public boolean isLoaded() {
		return mPrepared;
	}

	/**
	 * Load the image from path
	 *
	 * @param sFilePath File path
	 */
	public void loadImage(String sFilePath) {
		mImgPath = sFilePath;
		mLoaderThread = new Thread(this);
		mLoaderThread.start();
	}

	/**
	 * Treat the view as loaded, using given width and height
	 *
	 * @param width  width in px
	 * @param height height in px
	 */
	public void loadEmptyArea(int width, int height) {
		mWidth = width;
		mHeight = height;
		mProportion = (float) getImageHeight() / (float) getImageWidth();
		mDirection = mAnimateSourceDirection = mAnimateTargetDirection = 0;
		if (mGifDrawable != null)
			mGifDrawable.stop();
		mGifDrawable = null;
		mDrawable = null;
		mImgPath = null;
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REZOOM);
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_LOADED);
		// mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_LOADED);
		prepareLayout();
		invalidate();
	}

	/**
	 * Rotate the image counterclockwise
	 */
	public void rotateLeft() {
		if (!mPrepared)
			return;
		if (mDisableRotation)
			return;
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_ROTATE);
		mAnimateSourceDirection = mDirection;
		mAnimateTargetDirection = (((int) (mAnimateTargetDirection / 90) * 90)) - 90;
		mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ROTATE, System.currentTimeMillis() + mAnimateDuration));
		mRotatedVertically = !mRotatedVertically;
		applyLayout(true);
		boolean b = mScrollerHorizontal.springBack(mMidX, 0, mMinX, mMaxX, 0, 0);
		b |= mScrollerVertical.springBack(0, mMidY, 0, 0, mMinY, mMaxY);
		if (b) {
			mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
			mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_REPOSITION);
		}
	}

	public int getDirection() {
		return (int) (mAnimateTargetDirection + 360) % 360;
	}

	public void setDirection(int direction) {
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_ROTATE);
		int prevDir = (int) mAnimateTargetDirection;
		mAnimateSourceDirection = mAnimateTargetDirection = mDirection = direction;
		if (prevDir % 180 != getDirection() % 180)
			mRotatedVertically = !mRotatedVertically;
		applyLayout(true);
	}

	public boolean isRotatedVertically() {
		return mRotatedVertically;
	}

	/**
	 * Rotate the image clockwise
	 */
	public void rotateRight() {
		if (!mPrepared)
			return;
		if (mDisableRotation)
			return;
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_ROTATE);
		mAnimateSourceDirection = mDirection;
		mAnimateTargetDirection = (((int) (mAnimateTargetDirection / 90) * 90)) + 90;
		mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ROTATE, Long.valueOf(System.currentTimeMillis() + mAnimateDuration)));
		mRotatedVertically = !mRotatedVertically;
		applyLayout(true);
		boolean b = mScrollerHorizontal.springBack(mMidX, 0, mMinX, mMaxX, 0, 0);
		b |= mScrollerVertical.springBack(0, mMidY, 0, 0, mMinY, mMaxY);
		if (b) {
			mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
			mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_REPOSITION);
		}
	}

	/**
	 * Reset the position only
	 */
	public void resetPosition(boolean justFitBounds) {
		if (!mPrepared)
			return;
		if (justFitBounds) {
			mZoom = Math.min(Math.min(getWidth() / (float) getImageWidth(), getHeight() / (float) getImageHeight()), mZoom);
		} else {
			if (getImageWidth() == 0 || getImageHeight() == 0 || mProportion == 0) { // Not loaded
			} else if (getWidth() == 0 || getHeight() == 0)
				mZoom = 0;
			else if (getWidth() >= getImageWidth() && getHeight() >= getImageHeight())
				mZoom = mZoomMin = 1;
			else if (getHeight() / getWidth() > mProportion)
				mZoomMin = mZoom = (float) getWidth() / getImageWidth();
			else
				mZoomMin = mZoom = (float) getHeight() / getImageHeight();
			if (mZoomMin > 0.25f)
				mZoomMin = 0.25f;
		}
		mMidX = getWidth() / 2;
		mMidY = getHeight() / 2;
		mMinX = (int) (getWidth() - (getImageWidth() * mZoom / 2));
		mMaxX = (int) (getImageWidth() * mZoom / 2);
		mMinY = (int) (getHeight() - (getImageHeight() * mZoom / 2));
		mMaxY = (int) (getImageHeight() * mZoom / 2);
		if (mMinX > mMaxX) mMinX = mMaxX = getWidth() / 2;
		if (mMinY > mMaxY) mMinY = mMaxY = getHeight() / 2;
		mScrollerHorizontal.forceFinished(true);
		mScrollerVertical.forceFinished(true);
		mEdgeGlowLeft.finish();
		mEdgeGlowRight.finish();
		mEdgeGlowTop.finish();
		mEdgeGlowBottom.finish();
		triggerOnImageParamChangedListener();
		invalidate();
	}

	private void prepareLayout() {
		if (mPrepared)
			return;
		mPrepared = true;
		float oldX = mMaxX != mMinX ? (mMidX - mMinX) / (float) (mMaxX - mMinX) : 0.5f;
		float oldY = mMaxY != mMinY ? (mMidY - mMinY) / (float) (mMaxY - mMinY) : 0.5f;
		if (getImageWidth() == 0 || getImageHeight() == 0 || mProportion == 0) { // Not loaded
			mZoomMin = 0;
		} else if (getWidth() == 0 || getHeight() == 0)
			mZoomMin = 0;
		else if (getWidth() >= getImageWidth() && getHeight() >= getImageHeight())
			mZoomMin = 1;
		else if (getHeight() / getWidth() > mProportion)
			mZoomMin = (float) getWidth() / getImageWidth();
		else
			mZoomMin = (float) getHeight() / getImageHeight();
		if (mZoom == 0)
			mZoom = mZoomMin;
		if (mZoomMin > 0.25f)
			mZoomMin = 0.25f;
		mMinX = (int) (getWidth() - (getImageWidth() * mZoom / 2));
		mMaxX = (int) (getImageWidth() * mZoom / 2);
		mMinY = (int) (getHeight() - (getImageHeight() * mZoom / 2));
		mMaxY = (int) (getImageHeight() * mZoom / 2);
		if (mMinX > mMaxX) mMinX = mMaxX = getWidth() / 2;
		if (mMinY > mMaxY) mMinY = mMaxY = getHeight() / 2;
		mMidX = (int) (mMinX + (mMaxX - mMinX) * oldX);
		mMidY = (int) (mMinY + (mMaxY - mMinY) * oldY);
		mScrollerHorizontal.forceFinished(true);
		mScrollerVertical.forceFinished(true);
		mEdgeGlowLeft.finish();
		mEdgeGlowRight.finish();
		mEdgeGlowTop.finish();
		mEdgeGlowBottom.finish();
		triggerOnImageParamChangedListener();
		postInvalidate();
	}

	private void applyLayout(boolean trap) {
		if (!mPrepared)
			return;
		if (mZoom == 0) { // not yet prepared
			mMidX = getWidth() / 2;
			mMidY = getHeight() / 2;
			if (getImageWidth() == 0 || getImageHeight() == 0 || mProportion == 0) { // Not loaded
			} else if (getWidth() == 0 || getHeight() == 0)
				mZoom = 0;
			else if (mZoomMin != 0) {
				mZoom = mZoomMin;
				mZoomMin = (float) getWidth() / getImageWidth();
			} else if (getWidth() >= getImageWidth() && getHeight() >= getImageHeight())
				mZoom = mZoomMin = 1;
			else if (getHeight() / getWidth() > mProportion)
				mZoomMin = mZoom = (float) getWidth() / getImageWidth();
			else
				mZoomMin = mZoom = (float) getHeight() / getImageHeight();
			if (mZoomMin > 0.25f)
				mZoomMin = 0.25f;
		}
		mMinX = (int) (getWidth() - (getImageWidth() * mZoom / 2));
		mMaxX = (int) (getImageWidth() * mZoom / 2);
		mMinY = (int) (getHeight() - (getImageHeight() * mZoom / 2));
		mMaxY = (int) (getImageHeight() * mZoom / 2);
		if (getWidth() >= (int) (getImageWidth() * mZoom)) {
			mMidX = mMinX = mMaxX = getWidth() / 2;
			mScrollerHorizontal.forceFinished(true);
			mEdgeGlowLeft.finish();
			mEdgeGlowRight.finish();
		}
		if (getHeight() >= (int) (getImageHeight() * mZoom)) {
			mMidY = mMinY = mMaxY = getHeight() / 2;
			mScrollerVertical.forceFinished(true);
			mEdgeGlowTop.finish();
			mEdgeGlowBottom.finish();
		}
		if (trap) {
			mMidX = Math.max(mMinX, Math.min(mMaxX, mMidX));
			mMidY = Math.max(mMinY, Math.min(mMaxY, mMidY));
		}
		triggerOnImageParamChangedListener();
		invalidate();
	}

	/**
	 * Set viewer paramaeters
	 *
	 * @param x    X of center
	 * @param y    Y of center
	 * @param zoom zoom
	 */
	public void setViewerParams(int x, int y, float zoom) {
		this.mZoom = zoom;
		mMinX = x;
		mMinY = y;
		applyLayout(true);
	}

	/**
	 * Notify every time view parameter is changed and applied
	 *
	 * @param l Listener
	 */
	public void setOnViewerParamChangedListener(OnViewerParamChangedListener l) {
		mOnViewerParamChangedListener = l;
	}

	public void triggerOnImageParamChangedListener() {
		if (mOnViewerParamChangedListener != null && mPrepared)
			mOnViewerParamChangedListener.onImageParamChanged(mMidX, mMinX, mMaxX, mMidY, mMinY, mMaxY, getImageWidth(), getImageHeight(), mZoom);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		applyLayout(true);
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		applyLayout(true);
	}

	private void smoothZoomTo(float newZoom) {
		if (mWidth == 0)
			return;
		newZoom = Math.min(Math.max(Math.max(getWidth() / (float) getImageWidth(), getHeight() / (float) getImageHeight()) * 4, 16), Math.max(0.25f, newZoom));
		if (mZoom != newZoom) {
			nAutoZoomOldZoom = mZoom;
			nAutoZoomStartTime = System.currentTimeMillis() + mAnimateDuration;
			mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REZOOM);
			mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_REZOOM, newZoom));
		}
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				// Begin transformation
				mPreviousMidX = mMidX;
				mPreviousMidY = mMidY;
				mPreviousZoom = mZoom;
				mIsMoving = true;
				mIsZooming = false;
				bZoomDragging = false;
				bIsDoubleTap = false;
				mTouchAvgX = event.getX();
				mTouchAvgY = event.getY();
				mZoomBaseX = event.getX();
				mZoomBaseY = event.getY();
				touchPointerCount = 1;
				mScrollerHorizontal.forceFinished(true);
				mScrollerVertical.forceFinished(true);
				mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
				break;
			case MotionEvent.ACTION_POINTER_DOWN:
				// Pinch-zoom
				if (mIsMoving) {
					touchPointerCount++;
					mIsMoving = true;
					mIsZooming = true;
					mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REZOOM);
					mTouchAvgX = mTouchAvgY = 0;
					for (int i = 0; i < touchPointerCount; i++) {
						mTouchAvgX += event.getX(i);
						mTouchAvgY += event.getY(i);
					}
					mTouchAvgX /= touchPointerCount;
					mTouchAvgY /= touchPointerCount;
					mPinchResizeTouchScale = 0;
					for (int i = 0; i < touchPointerCount; i++)
						mPinchResizeTouchScale += Math.sqrt(Math.pow(mTouchAvgX - event.getX(i), 2) + Math.pow(mTouchAvgY - event.getY(i), 2));
					mPinchResizeTouchScale /= touchPointerCount;
				}
				break;
			case MotionEvent.ACTION_CANCEL:
				// Cancelled, so revert
				mMidX = mPreviousMidX;
				mMidY = mPreviousMidY;
				mZoom = mPreviousZoom;
				bZoomDragging = bIsDoubleTap = false;
			case MotionEvent.ACTION_UP:
				touchPointerCount = 0;
				mIsMoving = false;
				mIsZooming = false;
				mScrollerHorizontal.forceFinished(true);
				mScrollerVertical.forceFinished(true);
				mEdgeGlowBottom.onRelease();
				mEdgeGlowRight.onRelease();
				mEdgeGlowTop.onRelease();
				mEdgeGlowLeft.onRelease();
				if (!bZoomDragging && bIsDoubleTap) {
					// double-tap zoom preset
					float[] arrZooms = new float[]{1, 4, (float) getWidth() / getImageWidth(), (float) getHeight() / getImageHeight()};
					int i;
					Arrays.sort(arrZooms);
					for (i = 0; i < arrZooms.length; i++)
						if (arrZooms[i] > mZoom) {
							smoothZoomTo(arrZooms[i]);
							break;
						}
					if (i >= arrZooms.length)
						smoothZoomTo(arrZooms[0]);
				} else {
					// reposition
					boolean b = mScrollerHorizontal.springBack(mMidX, 0, mMinX, mMaxX, 0, 0);
					b |= mScrollerVertical.springBack(0, mMidY, 0, 0, mMinY, mMaxY);
					if (b) {
						mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
						mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_REPOSITION);
					}
				}
				invalidate();
				break;
			case MotionEvent.ACTION_POINTER_UP:
				touchPointerCount--;
				if (touchPointerCount == 1) {
					mIsMoving = true;
					mIsZooming = false;
					for (int i = 0; i < event.getPointerCount(); i++) {
						if (i == event.getActionIndex())
							continue;
						mTouchAvgX = event.getX(i);
						mTouchAvgY = event.getY(i);
					}
					mZoomBaseX = mTouchAvgX;
					mZoomBaseY = mTouchAvgY;
					smoothZoomTo(mZoom);
				} else if (touchPointerCount > 1) {
					mTouchAvgX = mTouchAvgY = 0;
					for (int i = 0; i < event.getPointerCount(); i++) {
						if (i == event.getActionIndex())
							continue;
						mTouchAvgX += event.getX(i);
						mTouchAvgY += event.getY(i);
					}
					mTouchAvgX /= touchPointerCount;
					mTouchAvgY /= touchPointerCount;
					mPinchResizeTouchScale = 0;
					for (int i = 0; i < touchPointerCount; i++)
						mPinchResizeTouchScale += Math.sqrt(Math.pow(mTouchAvgX - event.getX(i), 2) + Math.pow(mTouchAvgY - event.getY(i), 2));
					mPinchResizeTouchScale /= touchPointerCount;
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (bIsDoubleTap) {
					if (Math.abs(event.getY() - mZoomBaseY) >= mConf.getScaledTouchSlop())
						bZoomDragging = true;
					if (bZoomDragging) {
						float newZoom = (float) (nDragZoomBegin * Math.pow(1 + (event.getY() - mZoomBaseY) / getImageHeight(), 2));
						if (newZoom < 0.01f)
							newZoom = 0.01f;
						mMidX = (int) (mZoomBaseX - ((mZoomBaseX - mMidX) / mZoom * newZoom));
						mMidY = (int) (mZoomBaseY - ((mZoomBaseY - mMidY) / mZoom * newZoom));
						mZoom = newZoom;
						triggerOnImageParamChangedListener();
					}
				} else if (mIsMoving && event.getPointerCount() == touchPointerCount) {
					float nowAvgX = 0, nowAvgY = 0, nowRscale;
					for (int i = 0; i < event.getPointerCount(); i++) {
						nowAvgX += event.getX(i);
						nowAvgY += event.getY(i);
					}
					nowAvgX /= event.getPointerCount();
					nowAvgY /= event.getPointerCount();
					mMidX += nowAvgX - mTouchAvgX;
					mMidY += nowAvgY - mTouchAvgY;
					mTouchAvgX = nowAvgX;
					mTouchAvgY = nowAvgY;
					if (mIsZooming) {
						nowRscale = 0;
						for (int i = 0; i < touchPointerCount; i++)
							nowRscale += Math.sqrt(Math.pow(nowAvgX - event.getX(i), 2) + Math.pow(nowAvgY - event.getY(i), 2));
						nowRscale /= touchPointerCount;
						float newZoom = mZoom * nowRscale / mPinchResizeTouchScale;
						if (newZoom < 0.01f)
							newZoom = 0.01f;
						mMidX = (int) (nowAvgX - (nowAvgX - mMidX) / mZoom * newZoom);
						mMidY = (int) (nowAvgY - (nowAvgY - mMidY) / mZoom * newZoom);
						mZoom = newZoom;
						mPinchResizeTouchScale = nowRscale;
					}
				}
				applyLayout(false);

				// width:  / (mRotatedVertically?getImageHeight():getImageWidth())
				int nDrawWidth = (int) Math.min(getWidth(), getImageWidth() * mZoom);
				int nDrawHeight = (int) Math.min(getHeight(), getImageHeight() * mZoom);


				if (mMidX > mMaxX)
					mEdgeGlowLeft.onPull((float) (mMidX - mMaxX) / getWidth(), 1 - (event.getY() - (getHeight() - nDrawHeight) / 2) / nDrawHeight);
				if (mMidX < mMinX)
					mEdgeGlowRight.onPull((float) (mMinX - mMidX) / getWidth(), (event.getY() - (getHeight() - nDrawHeight) / 2) / nDrawHeight);
				if (mMidY > mMaxY)
					mEdgeGlowTop.onPull((float) (mMidY - mMaxY) / getHeight(), (event.getX() - (getWidth() - nDrawWidth) / 2) / nDrawWidth);
				if (mMidY < mMinY)
					mEdgeGlowBottom.onPull((float) (mMinY - mMidY) / getHeight(), 1 - (event.getX() - (getWidth() - nDrawWidth) / 2) / nDrawWidth);
				mMidX = Math.max(mMinX, Math.min(mMaxX, mMidX));
				mMidY = Math.max(mMinY, Math.min(mMaxY, mMidY));
				break;
		}
		mGesture.onTouchEvent(event);
		return true;
	}

	@Override
	public void setOnClickListener(OnClickListener l) {
		mClickListener = l;
	}

	@Override
	public void run() {
		mDisableRotation = false;
		try {
			Thread.currentThread().setName("ImageLoader " + mImgPath);
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(mImgPath, o);
			if (mGifDrawable != null) {
				mGifDrawable.stop();
				mGifDrawable = null;
			}
			if (o.outMimeType.contains("image/gif"))
				try {
					mGifDrawable = new GifDrawable(mImgPath);
					mGifDrawable.start();
					mDrawable = mGifDrawable;
				} catch (Exception e) {
					e.printStackTrace();
				}
			if (mGifDrawable == null) {
				Canvas c = new Canvas();
				mDrawable = new BitmapDrawable(getResources(), ImageCache.decodeFile(mImgPath, c.getMaximumBitmapWidth(), c.getMaximumBitmapHeight()));
			}
			int oldWidth = getImageWidth();
			mWidth = o.outWidth;
			mHeight = o.outHeight;
			mZoom = getImageWidth() == 0 ? 1 : mZoom * oldWidth / getImageWidth();
			mProportion = (float) getImageHeight() / (float) getImageWidth();
			mPrepared = false;
			mHandler.removeMessages(MESSAGE_IMAGE_VIEW_LOADED);
			mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REZOOM);
			mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_LOADED);
		} catch (Error e) {
			mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ERROR, e));
		} catch (Exception e) {
			e.printStackTrace();
			mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ERROR, e));
		} finally {
			mDisableRotation = false;
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		boolean bContinue = mGifDrawable != null;
		if (mDrawable != null) {
			canvas.save();
			canvas.translate(mMidX, mMidY);
			canvas.rotate(mDirection);
			canvas.scale(mZoom, mZoom);
			if (!mRotatedVertically) {
				canvas.translate(-getImageWidth() / 2, -getImageHeight() / 2);
				mDrawable.setBounds(0, 0, getImageWidth(), getImageHeight());
			} else {
				canvas.translate(-getImageHeight() / 2, -getImageWidth() / 2);
				mDrawable.setBounds(0, 0, getImageHeight(), getImageWidth());
			}
			mDrawable.draw(canvas);
			canvas.restore();
		}

		int nDrawWidth = (int) Math.min(getWidth(), getImageWidth() * mZoom);
		int nDrawHeight = (int) Math.min(getHeight(), getImageHeight() * mZoom);

		if (!mEdgeGlowTop.isFinished()) {
			canvas.translate((getWidth() - nDrawWidth) / 2, 0);
			mEdgeGlowTop.setSize(nDrawWidth, nDrawHeight);
			bContinue |= mEdgeGlowTop.draw(canvas);
		}
		if (!mEdgeGlowBottom.isFinished()) {
			canvas.save();
			canvas.translate(-getWidth() - (getWidth() - nDrawWidth) / 2, getHeight());
			canvas.rotate(180, getWidth(), 0);
			mEdgeGlowBottom.setSize(nDrawWidth, nDrawHeight);
			bContinue |= mEdgeGlowBottom.draw(canvas);
			canvas.restore();
		}
		if (!mEdgeGlowLeft.isFinished()) {
			canvas.save();
			canvas.rotate(270);
			canvas.translate(-nDrawHeight - (getHeight() - nDrawHeight) / 2, 0);
			mEdgeGlowLeft.setSize(nDrawHeight, nDrawWidth);
			bContinue |= mEdgeGlowLeft.draw(canvas);
			canvas.restore();
		}
		if (!mEdgeGlowRight.isFinished()) {
			canvas.save();
			canvas.rotate(90);
			canvas.translate((getHeight() - nDrawHeight) / 2, -nDrawWidth - (getWidth() - nDrawWidth) / 2);
			mEdgeGlowRight.setSize(nDrawHeight, nDrawWidth);
			bContinue |= mEdgeGlowRight.draw(canvas);
			canvas.restore();
		}
		if (bContinue)
			postInvalidate();
	}

	@Override
	public boolean handleMessage(android.os.Message msg) {
		switch (msg.what) {
			case MESSAGE_IMAGE_VIEW_ROTATE: {
				float dist = (((Long) msg.obj) - System.currentTimeMillis()) / (float) mAnimateDuration;
				if (dist >= 0)
					mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ROTATE, msg.arg1, msg.arg2, msg.obj));
				else
					dist = 0;
				mDirection = mAnimateTargetDirection + (mAnimateSourceDirection - mAnimateTargetDirection) * mInterpolator.getInterpolation(dist);
				invalidate();
				return true;
			}
			case MESSAGE_IMAGE_VIEW_ERROR: {
				if (mLoadListener != null)
					mLoadListener.onImageViewLoadFailed(LargeImageView.this, (Throwable) msg.obj);
				return true;
			}
			case MESSAGE_IMAGE_VIEW_LOADED: {
				prepareLayout();
				if (mLoadListener != null)
					mLoadListener.onImageViewLoadFinished(LargeImageView.this);
				return true;
			}
			case MESSAGE_IMAGE_VIEW_REPOSITION: {
				if (mIsMoving)
					return true;
				boolean b = mScrollerHorizontal.computeScrollOffset();
				b |= mScrollerVertical.computeScrollOffset();
				if (b) {
					mMidX = mScrollerHorizontal.getCurrX();
					mMidY = mScrollerVertical.getCurrY();
					if (mScrollerHorizontal.isOverScrolled()) {
						if (mMidX > mMaxX)
							mEdgeGlowLeft.onAbsorb((int) mScrollerHorizontal.getCurrVelocity());
						if (mMidX < mMinX)
							mEdgeGlowRight.onAbsorb((int) mScrollerHorizontal.getCurrVelocity());
					}
					if (mScrollerVertical.isOverScrolled()) {
						if (mMidY > mMaxY)
							mEdgeGlowTop.onAbsorb((int) mScrollerVertical.getCurrVelocity());
						if (mMidY < mMinY)
							mEdgeGlowBottom.onAbsorb((int) mScrollerVertical.getCurrVelocity());
					}
					mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_REPOSITION);
				} else {
					mMidX = mScrollerHorizontal.getFinalX();
					mMidY = mScrollerVertical.getFinalY();
				}
				applyLayout(false);
				return true;
			}
			case MESSAGE_IMAGE_VIEW_REZOOM: {
				if (mIsZooming)
					return true;
				mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
				float dist = (nAutoZoomStartTime - System.currentTimeMillis()) / (float) mAnimateDuration;
				float destZoom = (Float) msg.obj;
				float newZoom = destZoom;
				if (dist >= 0) {
					newZoom += (nAutoZoomOldZoom - destZoom) * mInterpolator.getInterpolation(dist);
					mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_REZOOM, destZoom));
				}
				mMidX = (int) (mZoomBaseX - ((mZoomBaseX - mMidX) / mZoom * newZoom));
				mMidY = (int) (mZoomBaseY - ((mZoomBaseY - mMidY) / mZoom * newZoom));
				mZoom = newZoom;
				applyLayout(true);
				postInvalidate();
				return true;
			}
		}
		return false;
	}

	public void setOnImageViewLoadFinsihedListener(OnImageViewLoadFinishedListener l) {
		this.mLoadListener = l;
	}

	public interface OnImageViewLoadFinishedListener {
		void onImageViewLoadFinished(LargeImageView v);

		void onImageViewLoadFailed(LargeImageView v, Throwable exception);
	}

	public interface OnViewerParamChangedListener {
		void onImageParamChanged(int x, int minX, int maxX, int y, int minY, int maxY, int width, int height, float zoom);
	}

	private final class SimpleGestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			nDragZoomBegin = mZoom;
			bIsDoubleTap = true;
			return true;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			mScrollerVertical.forceFinished(true);
			mScrollerHorizontal.forceFinished(true);
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			if (mMidX == mMinX || mMidX == mMaxX)
				velocityX = 0;
			if (mMidY == mMinY || mMidY == mMaxY)
				velocityY = 0;
			if (Math.abs(velocityX) > mConf.getScaledMinimumFlingVelocity() || Math.abs(velocityY) > mConf.getScaledMinimumFlingVelocity()) {
				mScrollerHorizontal.forceFinished(true);
				mScrollerVertical.forceFinished(true);
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
					mScrollerHorizontal.fling(mMidX, 0, (int) velocityX, 0, mMinX, mMaxX, 0, 0, mConf.getScaledOverflingDistance(), 0);
					mScrollerVertical.fling(0, mMidY, 0, (int) velocityY, 0, 0, mMinY, mMaxY, 0, mConf.getScaledOverflingDistance());
				} else {
					mScrollerHorizontal.fling(mMidX, 0, (int) velocityX, 0, mMinX, mMaxX, 0, 0, 5, 0);
					mScrollerVertical.fling(0, mMidY, 0, (int) velocityY, 0, 0, mMinY, mMaxY, 0, 5);
				}
				mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
				mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_REPOSITION);
			}
			return true;
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			if (mClickListener != null)
				mClickListener.onClick(LargeImageView.this);
			return true;
		}
	}
}
