/*******************************************************************************
 * Copyright 2014 Soreepeong
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.soreepeong.darknova.ui.dragaction;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.AlphaAnimation;

/**
 * Helper for views which initiates drag action
 * 
 * Any view which intends to start drag must call this function.
 * 
 * @author Soreepeong
 */
public class DragInitiator{
	/** The view which this drag initiator handles. */
	private View mViewInitator;

	private View mViewReactor;

	private boolean mActionOnCancel;

	/** The drag parent. */
	private DragParentFrameLayout mViewDragParent;

	/** Drag initiator callbacks. */
	private DragInitiatorCallbacks dragInitiatorCallbacks;

	/** Starting touch position. */
	private float mStartTouchPositionX;
	private float mStartTouchPositionY;

	private boolean mKeepContainerOnClick;

	/**
	 * Construct a new drag initiator for a view.
	 * 
	 * @param container
	 *            The view which the drag initiator handles.
	 */
	public DragInitiator(View container){
		this.mViewInitator = container;
	}

	/**
	 * Return container.
	 * 
	 * @returns Container view.
	 */
	public View getContainer(){
		return mViewInitator;
	}

	/**
	 * Clear parent associated to this view.
	 */
	public void resetParent(){
		if(mViewDragParent.getDragInitiator() == this)
			mViewDragParent.stopDrag();
		mViewDragParent = null;
	}

	/**
	 * Set DragParentLayout associated with this DragInitiator.
	 * 
	 * @param vwParent
	 *            DragParentLayout associated with this DragInitiator.
	 */
	public void setParent(DragParentFrameLayout vwParent){
		this.mViewDragParent = vwParent;
	}

	/**
	 * Find drag parent.
	 * 
	 * @return The DragParentLayout which is associated with this DragInitiator,
	 *         or null if neither set nor found
	 */
	public View getParent(){
		if(mViewDragParent != null)
			return mViewDragParent;
		View vwFinder = mViewInitator;
		while(!(vwFinder instanceof DragParentFrameLayout)){
			ViewParent vwParentTemp = vwFinder.getParent();
			if(vwParentTemp == null || !(vwParentTemp instanceof View))
				return null;
			vwFinder = (View)vwParentTemp;
		}
		mViewDragParent = (DragParentFrameLayout)vwFinder;
		return vwFinder;
	}

	public boolean keepContainerOnClick(){
		return mKeepContainerOnClick;
	}

	public void setKeepContainerOnClick(boolean b){
		mKeepContainerOnClick = b;
	}

	public void setReactorContainer(View v){
		mViewReactor = v;
	}

	public View getReactorContainer(){
		return mViewReactor;
	}

	public void setActionOnCancel(boolean b){
		mActionOnCancel = b;
	}

	public boolean getActionOnCancel(){
		return mActionOnCancel;
	}

	/**
	 * Set DragInitiatorCallbacks for this DragInitiator.
	 * 
	 * @param dragInitiatorCallbacks
	 *            DragInitiatorCallbacks to set.
	 */
	public void setDragInitiatorCallbacks(DragInitiatorCallbacks dragInitiatorCallbacks){
		this.dragInitiatorCallbacks = dragInitiatorCallbacks;
	}

	/**
	 * Start dragging. Must be called from onTouchEvent.
	 * 
	 * @param nActionType
	 *            Type of action associated with this DragInitiator
	 * @param event
	 *            MotionEvent from onTouchEvent
	 * @throws RuntimeException
	 *             If DragParentLayout cannot be found, the method will throw an
	 *             error.
	 */
	public boolean startDrag(int nActionType, MotionEvent event){
		if(getParent() == null)
			throw new RuntimeException("Associated DragParentLayout not found");
		mStartTouchPositionX = event.getRawX();
		mStartTouchPositionY = event.getRawY();
		return mViewDragParent.startDrag(nActionType, event.getPointerId(event.getActionIndex()), this);
	}

	/**
	 * Called back by DragParentLayout when drag has quit.
	 */
	public void onDragStop(){
		if(dragInitiatorCallbacks != null)
			dragInitiatorCallbacks.onDragEnd(this);
		if(mViewReactor != null){
			AlphaAnimation aa = new AlphaAnimation(1, 0);
			aa.setDuration(300);
			aa.setInterpolator(mViewInitator.getContext(), android.R.anim.accelerate_interpolator);
			mViewReactor.startAnimation(aa);
			mViewReactor.setVisibility(View.GONE);
		}
	}

	/**
	 * Called back by DragParentLayout when drag has started.
	 */
	public void onDragStart(){
		if(dragInitiatorCallbacks != null)
			dragInitiatorCallbacks.onDragStart(this);
		if(mViewReactor != null){
			AlphaAnimation aa = new AlphaAnimation(0, 1);
			aa.setDuration(100);
			aa.setInterpolator(mViewInitator.getContext(), android.R.anim.decelerate_interpolator);
			mViewReactor.startAnimation(aa);
			mViewReactor.setVisibility(View.VISIBLE);
		}
	}

	public void onDragPrepare(){
		if(dragInitiatorCallbacks != null)
			dragInitiatorCallbacks.onDragPrepare(this);
	}

	/**
	 * Get starting touch position's X value
	 * 
	 * @return X value
	 */
	public float getStartTouchPositionX(){
		return mStartTouchPositionX;
	}

	/**
	 * Get starting touch position's Y value
	 * 
	 * @return Y value
	 */
	public float getStartTouchPositionY(){
		return mStartTouchPositionY;
	}
}
