package com.soreepeong.darknova.ui.view;

import java.util.ArrayList;
import java.util.Collections;

import pl.droidsonroids.gif.GifDrawable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.OverScroller;

public class LargeImageView extends View implements Runnable, Handler.Callback{

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
	private int mMidX = 0, mMidY = 0;
	private float zoom = 0, minZoom = 0.5f;
	private float direction = 0, mAnimateSourceDirection, mAnimateTargetDirection;
	private boolean bVertical;
	private OnClickListener clkListener;
	private GifDrawable mGif;
	private Drawable mDrawable;
	private boolean bNoRotate;
	private OnImageViewLoadFinishedListener mLoadListener;
	private boolean bLoadedCalled;
	private int nMaxX, nMinX, nMaxY, nMinY;

	private OnImageParamChangedListener mOnImageParamChangedListener;

	public LargeImageView(Context context){
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

	public LargeImageView(Context context, AttributeSet attrs){
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

	public LargeImageView(Context context, AttributeSet attrs, int defStyle){
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

	public void removeImage(){
		if(mLoaderThread != null){
			mLoaderThread.interrupt();
			mLoaderThread = null;
		}
		mImgPath = null;
		zoom = 0;
		direction = 0;
		mDrawable = null;
		bMoving = bZooming = false;
		bVertical = false;
		if(mGif != null)
			mGif.stop();
		mGif = null;
		bNoRotate = true;
		bLoadedCalled = false;
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_CREATE_TO_VIEW);
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_APPLY_TO_VIEW);
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_APPLY_TO_SINGLE_VIEW);
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_LOADED);
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_ROTATE);
	}

	public boolean hasImage(){
		return mImgPath != null;
	}

	public void loadImage(String sFilePath){
		mImgPath = sFilePath;
		mLoaderThread = new Thread(this);
		mLoaderThread.start();
	}

	public void loadEmptyArea(int width, int height){
		mWidth = width;
		mHeight = height;
		mProportion = (float) mHeight / (float) mWidth;
		if(!bLoadedCalled && mLoadListener != null)
			mLoadListener.OnImageViewLoadFinished(LargeImageView.this);
		bLoadedCalled = true;
		applyLayout(true);
	}

	public void rotateLeft(){
		if(bNoRotate)
			return;
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_ROTATE);
		mAnimateSourceDirection = direction;
		mAnimateTargetDirection = (((int)(mAnimateTargetDirection / 90) * 90)) - 90;
		mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ROTATE, Long.valueOf(System.currentTimeMillis() + mAnimateDuration)));
		int a = mWidth;
		mWidth = mHeight;
		mHeight = a;
		bVertical = !bVertical;
		applyLayout(true);
		boolean b = mScrollerHorizontal.springBack(mMidX, 0, nMinX, nMaxX, 0, 0);
		b |= mScrollerVertical.springBack(0, mMidY, 0, 0, nMinY, nMaxY);
		if(b){
			mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
			mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_REPOSITION);
		}
	}

	public void rotateRight(){
		if(bNoRotate)
			return;
		mHandler.removeMessages(MESSAGE_IMAGE_VIEW_ROTATE);
		mAnimateSourceDirection = direction;
		mAnimateTargetDirection = (((int)(mAnimateTargetDirection / 90) * 90)) + 90;
		mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ROTATE, Long.valueOf(System.currentTimeMillis() + mAnimateDuration)));
		int a = mWidth;
		mWidth = mHeight;
		mHeight = a;
		bVertical = !bVertical;
		applyLayout(true);
		boolean b = mScrollerHorizontal.springBack(mMidX, 0, nMinX, nMaxX, 0, 0);
		b |= mScrollerVertical.springBack(0, mMidY, 0, 0, nMinY, nMaxY);
		if(b){
			mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
			mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_REPOSITION);
		}
	}

	public void resetPosition(){
		zoom = 0;
		applyLayout(true);
	}

	private void applyLayout(boolean trap){
		if(zoom == 0){ // not yet prepared
			mMidX = getWidth() / 2;
			mMidY = getHeight() / 2;
			if(mWidth == 0 || mHeight == 0 || mProportion == 0){ // Not loaded
			}else if(getWidth() == 0 || getHeight() == 0)
				zoom = 0;
			else if(getWidth() >= mWidth && getHeight() >= mHeight)
				zoom = minZoom = 1;
			else if(getHeight() / getWidth() > mProportion)
				minZoom = zoom = (float)getWidth() / mWidth;
			else
				minZoom = zoom = (float)getHeight() / mHeight;
			if(minZoom > 0.25f)
				minZoom = 0.25f;
		}
		nMinX = (int)(getWidth() - (mWidth * zoom / 2));
		nMaxX = (int)(mWidth * zoom / 2);
		nMinY = (int)(getHeight() - (mHeight * zoom / 2));
		nMaxY = (int)(mHeight * zoom / 2);
		if(getWidth() >= (int)(mWidth * zoom)){
			mMidX = nMinX = nMaxX = getWidth() / 2;
			mScrollerHorizontal.forceFinished(true);
			mEdgeGlowLeft.finish();
			mEdgeGlowRight.finish();
		}
		if(getHeight() >= (int)(mHeight * zoom)){
			mMidY = nMinY = nMaxY = getHeight() / 2;
			mScrollerVertical.forceFinished(true);
			mEdgeGlowTop.finish();
			mEdgeGlowBottom.finish();
		}
		if(trap){
			mMidX = Math.max(nMinX, Math.min(nMaxX, mMidX));
			mMidY = Math.max(nMinY, Math.min(nMaxY, mMidY));
		}
		triggerOnImageParamChangedListener();
		invalidate();
	}

	public void setImageParams(int x, int y, float zoom){
		this.zoom =zoom;
		nMinX = x;
		nMinY = y;
		applyLayout(true);
	}

	public void setOnImageParamChangedListener(OnImageParamChangedListener l){
		mOnImageParamChangedListener = l;
	}

	public void triggerOnImageParamChangedListener(){
		if(mOnImageParamChangedListener != null)
			mOnImageParamChangedListener.onImageParamChanged(mMidX, nMinX, nMaxX, mMidY, nMinY, nMaxY, mWidth, mHeight, zoom);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
		applyLayout(true);
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	private float touchRscale;
	private boolean bMoving, bZooming;
	private float touchAvgX, touchAvgY, nAutoZoomOldZoom;
	private long nAutoZoomStartTime;
	private int touchPointerCount;
	private float nZoomBaseY, nZoomBaseX, nDragZoomBegin;
	private boolean bZoomDragging, bIsDoubleTap;

	private void rezoom(){
		float newZoom, maxZoom;
		newZoom = zoom;
		if(zoom < 0.25f)
			newZoom = 0.25f;
		maxZoom = Math.max(Math.max(8, (float)getWidth() / mWidth), (float)getHeight() / mHeight);
		if(zoom > maxZoom)
			newZoom = maxZoom;
		if(zoom != newZoom){
			nAutoZoomOldZoom = zoom;
			nAutoZoomStartTime = System.currentTimeMillis() + mAnimateDuration;
			mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REZOOM);
			mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_REZOOM, newZoom));
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event){
		int i;
		float nowAvgX = 0, nowAvgY = 0, nowRscale = 0, newZoom;
		switch(event.getActionMasked()){
			case MotionEvent.ACTION_DOWN:
				bMoving = true;
				bZooming = false;
				bZoomDragging = false;
				bIsDoubleTap = false;
				touchAvgX = event.getX();
				touchAvgY = event.getY();
				nZoomBaseX = event.getX();
				nZoomBaseY = event.getY();
				touchPointerCount = 1;
				mScrollerHorizontal.forceFinished(true);
				mScrollerVertical.forceFinished(true);
				mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
				break;
			case MotionEvent.ACTION_POINTER_DOWN:
				if(bMoving){
					touchPointerCount++;
					bMoving = true;
					bZooming = true;
					mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REZOOM);
					touchAvgX = touchAvgY = 0;
					for(i = 0; i < touchPointerCount; i++){
						touchAvgX += event.getX(i);
						touchAvgY += event.getY(i);
					}
					touchAvgX /= touchPointerCount;
					touchAvgY /= touchPointerCount;
					touchRscale = 0;
					for(i = 0; i < touchPointerCount; i++)
						touchRscale += Math.sqrt(Math.pow(touchAvgX - event.getX(i), 2) + Math.pow(touchAvgY - event.getY(i), 2));
					touchRscale /= touchPointerCount;
				}
				break;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				touchPointerCount = 0;
				bMoving = false;
				bZooming = false;
				mScrollerHorizontal.forceFinished(true);
				mScrollerVertical.forceFinished(true);
				mEdgeGlowBottom.onRelease();
				mEdgeGlowRight.onRelease();
				mEdgeGlowTop.onRelease();
				mEdgeGlowLeft.onRelease();
				if(!bZoomDragging && bIsDoubleTap){
					ArrayList<Float> arrZooms = new ArrayList<Float>();
					arrZooms.add(Float.valueOf(1));
					arrZooms.add(Float.valueOf(4));
					arrZooms.add(Float.valueOf((float)getWidth() / mWidth)); // Width
					// fill
					arrZooms.add(Float.valueOf((float)getHeight() / mHeight)); // Height
					// fill
					Collections.sort(arrZooms);
					if(arrZooms.get(0) > zoom)
						newZoom = arrZooms.get(0);
					else if(arrZooms.get(1) > zoom)
						newZoom = arrZooms.get(1);
					else if(arrZooms.get(2) > zoom)
						newZoom = arrZooms.get(2);
					else if(arrZooms.get(3) > zoom)
						newZoom = arrZooms.get(3);
					else
						newZoom = arrZooms.get(0);
					nAutoZoomOldZoom = zoom;
					nAutoZoomStartTime = System.currentTimeMillis() + mAnimateDuration;
					mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REZOOM);
					mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_REZOOM, Float.valueOf(newZoom)));
				}else{
					boolean b = mScrollerHorizontal.springBack(mMidX, 0, nMinX, nMaxX, 0, 0);
					b |= mScrollerVertical.springBack(0, mMidY, 0, 0, nMinY, nMaxY);
					if(b){
						mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
						mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_REPOSITION);
					}
				}
				rezoom();
				invalidate();
				break;
			case MotionEvent.ACTION_POINTER_UP:
				touchPointerCount--;
				if(touchPointerCount == 1){
					bMoving = true;
					bZooming = false;
					for(i = 0; i < event.getPointerCount(); i++){
						if(i == event.getActionIndex())
							continue;
						touchAvgX = event.getX(i);
						touchAvgY = event.getY(i);
					}
					nZoomBaseX = touchAvgX;
					nZoomBaseY = touchAvgY;
					rezoom();
				}else if(touchPointerCount > 1){
					touchAvgX = touchAvgY = 0;
					for(i = 0; i < event.getPointerCount(); i++){
						if(i == event.getActionIndex())
							continue;
						touchAvgX += event.getX(i);
						touchAvgY += event.getY(i);
					}
					touchAvgX /= touchPointerCount;
					touchAvgY /= touchPointerCount;
					touchRscale = 0;
					for(i = 0; i < touchPointerCount; i++)
						touchRscale += Math.sqrt(Math.pow(touchAvgX - event.getX(i), 2) + Math.pow(touchAvgY - event.getY(i), 2));
					touchRscale /= touchPointerCount;
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if(bIsDoubleTap){
					if(Math.abs(event.getY() - nZoomBaseY) >= mConf.getScaledTouchSlop())
						bZoomDragging = true;
					if(bZoomDragging){
						newZoom = (float)(nDragZoomBegin * Math.pow(1 + (event.getY()-nZoomBaseY) / mHeight, 2));
						if(newZoom < 0.01f)
							newZoom = 0.01f;
						mMidX = (int)(nZoomBaseX - ((nZoomBaseX - mMidX) / zoom * newZoom));
						mMidY = (int)(nZoomBaseY - ((nZoomBaseY - mMidY) / zoom * newZoom));
						zoom = newZoom;
						triggerOnImageParamChangedListener();
					}
				}else if(bMoving && event.getPointerCount() == touchPointerCount){
					for(i = 0; i < event.getPointerCount(); i++){
						nowAvgX += event.getX(i);
						nowAvgY += event.getY(i);
					}
					nowAvgX /= event.getPointerCount();
					nowAvgY /= event.getPointerCount();
					mMidX += nowAvgX - touchAvgX;
					mMidY += nowAvgY - touchAvgY;
					touchAvgX = nowAvgX;
					touchAvgY = nowAvgY;
					if(bZooming){
						nowRscale = 0;
						for(i = 0; i < touchPointerCount; i++)
							nowRscale += Math.sqrt(Math.pow(nowAvgX - event.getX(i), 2) + Math.pow(nowAvgY - event.getY(i), 2));
						nowRscale /= touchPointerCount;
						newZoom = zoom * nowRscale / touchRscale;
						if(newZoom < 0.01f)
							newZoom = 0.01f;
						mMidX = (int)(nowAvgX - (nowAvgX - mMidX) / zoom * newZoom);
						mMidY = (int)(nowAvgY - (nowAvgY - mMidY) / zoom * newZoom);
						zoom = newZoom;
						touchRscale = nowRscale;
					}
				}
				applyLayout(false);

				// width:  / (bVertical?mHeight:mWidth)
				int nDrawWidth = (int)Math.min(getWidth(), mWidth * zoom);
				int nDrawHeight = (int)Math.min(getHeight(), mHeight * zoom);


				if(mMidX > nMaxX)
					mEdgeGlowLeft.onPull((float)(mMidX - nMaxX) / getWidth(), 1 - (event.getY() - (getHeight() - nDrawHeight) / 2) / nDrawHeight );
				if(mMidX < nMinX)
					mEdgeGlowRight.onPull((float)(nMinX - mMidX) / getWidth(), (event.getY() - (getHeight() - nDrawHeight) / 2) / nDrawHeight );
				if(mMidY > nMaxY)
					mEdgeGlowTop.onPull((float)(mMidY - nMaxY) / getHeight(), (event.getX() - (getWidth() - nDrawWidth) / 2) / nDrawWidth );
				if(mMidY < nMinY)
					mEdgeGlowBottom.onPull((float)(nMinY - mMidY) / getHeight(), 1- (event.getX() - (getWidth() - nDrawWidth) / 2) / nDrawWidth );
				mMidX = Math.max(nMinX, Math.min(nMaxX, mMidX));
				mMidY = Math.max(nMinY, Math.min(nMaxY, mMidY));
				break;
		}
		mGesture.onTouchEvent(event);
		return true;
	}

	@SuppressLint("NewApi")
	private final class SimpleGestureListener extends GestureDetector.SimpleOnGestureListener{
		@Override
		public boolean onDoubleTap(MotionEvent e){
			nDragZoomBegin = zoom;
			bIsDoubleTap = true;
			return true;
		}

		@Override
		public boolean onDown(MotionEvent e){
			mScrollerVertical.forceFinished(true);
			mScrollerHorizontal.forceFinished(true);
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
			if(mMidX == nMinX || mMidX == nMaxX)
				velocityX = 0;
			if(mMidY == nMinY || mMidY == nMaxY)
				velocityY = 0;
			if(Math.abs(velocityX) > mConf.getScaledMinimumFlingVelocity() || Math.abs(velocityY) > mConf.getScaledMinimumFlingVelocity()){
				mScrollerHorizontal.forceFinished(true);
				mScrollerVertical.forceFinished(true);
				if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD){
					mScrollerHorizontal.fling(mMidX, 0, (int)velocityX, 0, nMinX, nMaxX, 0, 0, mConf.getScaledOverflingDistance(), 0);
					mScrollerVertical.fling(0, mMidY, 0, (int)velocityY, 0, 0, nMinY, nMaxY, 0, mConf.getScaledOverflingDistance());
				}else{
					mScrollerHorizontal.fling(mMidX, 0, (int)velocityX, 0, nMinX, nMaxX, 0, 0, 5, 0);
					mScrollerVertical.fling(0, mMidY, 0, (int)velocityY, 0, 0, nMinY, nMaxY, 0, 5);
				}
				mHandler.removeMessages(MESSAGE_IMAGE_VIEW_REPOSITION);
				mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_REPOSITION);
			}
			return true;
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e){
			if(clkListener != null)
				clkListener.onClick(LargeImageView.this);
			return true;
		}
	}

	@Override
	public void setOnClickListener(OnClickListener l){
		clkListener = l;
	}

	@Override
	public void run(){
		bNoRotate = true;
		try{
			Thread.currentThread().setName("ImageLoader " + mImgPath);
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(mImgPath, o);
			mWidth = o.outWidth;
			mHeight = o.outHeight;
			mProportion = (float) mHeight / (float) mWidth;
			if(mGif != null){
				mGif.stop();
				mGif = null;
			}
			if(o.outMimeType.contains("image/gif"))
				try{
					mGif = new GifDrawable(mImgPath);
					mGif.start();
					mDrawable = mGif;
				}catch(Exception e){
					e.printStackTrace();
				}
			if(mGif == null)
				mDrawable = new BitmapDrawable(getResources(), mImgPath);
			mHandler.sendEmptyMessage(MESSAGE_IMAGE_VIEW_LOADED);
		}catch(Error e){
			mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ERROR, e));
		}catch(Exception e){
			e.printStackTrace();
			mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ERROR, e));
		}finally{
			bNoRotate = false;
		}
	}

	@Override
	protected void onDraw(Canvas canvas){
		boolean bContinue = mGif != null;
		if(mDrawable != null){
			canvas.save();
			canvas.translate(mMidX, mMidY);
			canvas.rotate(direction);
			canvas.scale(zoom, zoom);
			if(!bVertical){
				canvas.translate(-mWidth / 2, -mHeight / 2);
				mDrawable.setBounds(0, 0, mWidth, mHeight);
			}else{
				canvas.translate(-mHeight / 2, -mWidth / 2);
				mDrawable.setBounds(0, 0, mHeight, mWidth);
			}
			mDrawable.draw(canvas);
			canvas.restore();
		}

		int nDrawWidth = (int)Math.min(getWidth(), mWidth * zoom);
		int nDrawHeight = (int)Math.min(getHeight(), mHeight * zoom);

		if(!mEdgeGlowTop.isFinished()){
			canvas.translate((getWidth() - nDrawWidth) / 2, 0);
			mEdgeGlowTop.setSize(nDrawWidth, nDrawHeight);
			bContinue |= mEdgeGlowTop.draw(canvas);
		}
		if(!mEdgeGlowBottom.isFinished()){
			canvas.save();
			canvas.translate(-getWidth() - (getWidth() - nDrawWidth) / 2, getHeight());
			canvas.rotate(180, getWidth(), 0);
			mEdgeGlowBottom.setSize(nDrawWidth, nDrawHeight);
			bContinue |= mEdgeGlowBottom.draw(canvas);
			canvas.restore();
		}
		if(!mEdgeGlowLeft.isFinished()){
			canvas.save();
			canvas.rotate(270);
			canvas.translate(-nDrawHeight - (getHeight() - nDrawHeight) / 2, 0);
			mEdgeGlowLeft.setSize(nDrawHeight, nDrawWidth);
			bContinue |= mEdgeGlowLeft.draw(canvas);
			canvas.restore();
		}
		if(!mEdgeGlowRight.isFinished()){
			canvas.save();
			canvas.rotate(90);
			canvas.translate((getHeight() - nDrawHeight) / 2, -nDrawWidth - (getWidth() - nDrawWidth) / 2);
			mEdgeGlowRight.setSize(nDrawHeight, nDrawWidth);
			bContinue |= mEdgeGlowRight.draw(canvas);
			canvas.restore();
		}
		if(bContinue)
			postInvalidate();
	}

	@Override
	public boolean handleMessage(android.os.Message msg){
		if(msg.what == MESSAGE_IMAGE_VIEW_ROTATE){
			float dist = (((Long)msg.obj) - System.currentTimeMillis()) / (float)mAnimateDuration;
			if(dist >= 0)
				mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_ROTATE, msg.arg1, msg.arg2, msg.obj));
			else
				dist = 0;
			direction = mAnimateTargetDirection + (mAnimateSourceDirection - mAnimateTargetDirection) * mInterpolator.getInterpolation(dist);
			invalidate();
		}else if(msg.what == MESSAGE_IMAGE_VIEW_ERROR){
			if(mLoadListener != null)
				mLoadListener.OnImageViewLoadFailed(LargeImageView.this, (Exception) msg.obj);
			return true;
		}else if(msg.what == MESSAGE_IMAGE_VIEW_LOADED){
			if(!bLoadedCalled && mLoadListener != null)
				mLoadListener.OnImageViewLoadFinished(LargeImageView.this);
			bLoadedCalled = true;
			applyLayout(true);
			return true;
		}else if(msg.what == MESSAGE_IMAGE_VIEW_REPOSITION){
			if(bMoving)
				return true;
			boolean b = mScrollerHorizontal.computeScrollOffset();
			b |= mScrollerVertical.computeScrollOffset();
			if(b){
				mMidX = mScrollerHorizontal.getCurrX();
				mMidY = mScrollerVertical.getCurrY();
				if(mScrollerHorizontal.isOverScrolled()){
					if(mMidX > nMaxX)
						mEdgeGlowLeft.onAbsorb((int)mScrollerHorizontal.getCurrVelocity());
					if(mMidX < nMinX)
						mEdgeGlowRight.onAbsorb((int)mScrollerHorizontal.getCurrVelocity());
				}
				if(mScrollerVertical.isOverScrolled()){
					if(mMidY > nMaxY)
						mEdgeGlowTop.onAbsorb((int)mScrollerVertical.getCurrVelocity());
					if(mMidY < nMinY)
						mEdgeGlowBottom.onAbsorb((int)mScrollerVertical.getCurrVelocity());
				}
				mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_REPOSITION));
			}else{
				mMidX = mScrollerHorizontal.getFinalX();
				mMidY = mScrollerVertical.getFinalY();
			}
			applyLayout(false);
			return true;
		}else if(msg.what == MESSAGE_IMAGE_VIEW_REZOOM){
			if(bZooming)
				return true;
			float dist = (nAutoZoomStartTime - System.currentTimeMillis()) / (float)mAnimateDuration;
			float destZoom = (Float)msg.obj;
			float newZoom = destZoom;
			if(dist >= 0){
				newZoom += (nAutoZoomOldZoom - destZoom) * mInterpolator.getInterpolation(dist);
				mHandler.sendMessage(Message.obtain(mHandler, MESSAGE_IMAGE_VIEW_REZOOM, destZoom));
			}
			mMidX = (int)(nZoomBaseX - ((nZoomBaseX - mMidX) / zoom * newZoom));
			mMidY = (int)(nZoomBaseY - ((nZoomBaseY - mMidY) / zoom * newZoom));
			zoom = newZoom;
			applyLayout(true);
			return true;
		}
		return false;
	}

	public void setOnImageViewLoadFinsihedListener(OnImageViewLoadFinishedListener l){
		this.mLoadListener = l;
	}

	public static interface OnImageViewLoadFinishedListener{
		public void OnImageViewLoadFinished(LargeImageView v);

		public void OnImageViewLoadFailed(LargeImageView v, Exception exception);
	}

	public interface OnImageParamChangedListener{
		public void onImageParamChanged(int x, int minX, int maxX, int y, int minY, int maxY, int width, int height, float zoom);
	}
}
