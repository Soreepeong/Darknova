/**
 * ****************************************************************************
 * Copyright 2014 Soreepeong
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.soreepeong.darknova.ui.dragaction;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.soreepeong.darknova.R;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A parent layout which must contain every drag-related objects. DragInitiator
 * starts drag action, and DragReactor reacts to drag action.
 *
 * @author Soreepeong
 */
public class DragParentFrameLayout extends FrameLayout {

	/**
	 * Currently perceivable drag reactors.
	 */
	private final ArrayList<View> mPossibleDragReactors = new ArrayList<>();
	/**
	 * Currently active drag reactors.
	 */
	private final ArrayList<View> mActiveReactors = new ArrayList<>();
	private final HashMap<View, Long> mActiveReactorDownTime = new HashMap<>();
	/**
	 * Location of each views of DragReactors. Used in dispatchTouchEvent.
	 * Defined here for lesser GC.
	 */
	private final int[] myPosition = new int[2], viewPosition = new int[2];
	/**
	 * Drag Initiator. Null if not dragging.
	 */
	private DragInitiator mLastDragInitiator;
	/**
	 * Pointer ID which is currently dragging.
	 */
	private int mDragPointerId;
	private boolean mActuallyDragging;
	private int mCurrentTouches;

	public DragParentFrameLayout(Context context) {
		super(context);
	}

	public DragParentFrameLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public DragParentFrameLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	/**
	 * Traverse every views and find DragReactors with given Reactor Type.
	 *
	 * @param nReactorType    The reactor type.
	 * @param arrDragReactors ArrayList to append newly fount DragReactor.
	 * @param v               View to find from.
	 */
	private void findDragReactorsRecursive(int nReactorType, ArrayList<View> arrDragReactors, View v) {
		if (v == null)
			v = this;
		if (v.getId() == nReactorType || v.getId() == R.id.DRAG_ACTION_TYPE_ALL)
			nReactorType = R.id.DRAG_ACTION_TYPE_ALL;
		if (!(v instanceof ViewGroup)) {
			if (nReactorType == R.id.DRAG_ACTION_TYPE_ALL || ((v instanceof DragReactor) && ((DragReactor) v).getDragReactorActionType((DragReactor) v) == nReactorType))
				arrDragReactors.add(v);
			return;
		}
		ViewGroup parent = (ViewGroup) v;
		for (int i = parent.getChildCount() - 1; i >= 0; i--)
			findDragReactorsRecursive(nReactorType, arrDragReactors, parent.getChildAt(i));
	}

	/**
	 * Check if dragging, and process if yes, or dispatch if no.
	 */
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		int nLastAction = ev.getActionMasked();
		int nCurrentPointerId = ev.getPointerId(ev.getActionIndex());
		mCurrentTouches = ev.getPointerCount();
		getLocationInWindow(myPosition);
		if (mLastDragInitiator != null && mActuallyDragging) {
			if (nCurrentPointerId == mDragPointerId) {
				for (View v : mPossibleDragReactors) {
					v.getLocationInWindow(viewPosition);
					/* Is "ev" in v AND v enabled? */
					boolean in = ev.getRawX() >= viewPosition[0] && ev.getRawY() >= viewPosition[1] && ev.getRawX() < viewPosition[0] + v.getWidth() && ev.getRawY() < viewPosition[1] + v.getHeight() && v.isEnabled();
					boolean already = mActiveReactors.contains(v);
					if (in && already) { // Move
						MotionEvent newEvent = MotionEvent.obtain(mActiveReactorDownTime.get(v), SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, ev.getX() - (viewPosition[0] - myPosition[0]), ev.getY() - (viewPosition[1] - myPosition[1]), 0);
						v.dispatchTouchEvent(newEvent);
						newEvent.recycle();
					} else if (in) { // New
						mActiveReactors.add(v);
						mActiveReactorDownTime.put(v, SystemClock.uptimeMillis());
						MotionEvent newEvent = MotionEvent.obtain(mActiveReactorDownTime.get(v), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, ev.getX() - (viewPosition[0] - myPosition[0]), ev.getY() - (viewPosition[1] - myPosition[1]), 0);
						v.dispatchTouchEvent(newEvent);
						newEvent.recycle();
					} else if (already) { // Remove
						mActiveReactors.remove(v);
						MotionEvent newEvent = MotionEvent.obtain(mActiveReactorDownTime.get(v), SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, ev.getX() - (viewPosition[0] - myPosition[0]), ev.getY() - (viewPosition[1] - myPosition[1]), 0);
						v.dispatchTouchEvent(newEvent);
						newEvent.recycle();
					}
				}
				switch (ev.getActionMasked()) {
					case MotionEvent.ACTION_POINTER_UP:
					case MotionEvent.ACTION_UP: {
						for (View v : mActiveReactors) {
							v.getLocationInWindow(viewPosition);
							MotionEvent newEvent = MotionEvent.obtain(mActiveReactorDownTime.get(v), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, ev.getX() - (viewPosition[0] - myPosition[0]), ev.getY() - (viewPosition[1] - myPosition[1]), 0);
							v.dispatchTouchEvent(newEvent);
							newEvent.recycle();
						}
						stopDrag();
						break;
					}
					case MotionEvent.ACTION_CANCEL: {
						for (View v : mActiveReactors) {
							v.getLocationInWindow(viewPosition);
							MotionEvent newEvent = MotionEvent.obtain(mActiveReactorDownTime.get(v), SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, ev.getX() - (viewPosition[0] - myPosition[0]), ev.getY() - (viewPosition[1] - myPosition[1]), 0);
							v.dispatchTouchEvent(newEvent);
							newEvent.recycle();
						}
						stopDrag();
						break;
					}
				}
			} else if (nCurrentPointerId != -1) {
				ev.setAction(MotionEvent.ACTION_CANCEL);
				super.dispatchTouchEvent(ev);
				return true;
			}
		} else {
			if (mLastDragInitiator != null) {
				switch (ev.getActionMasked()) {
					case MotionEvent.ACTION_POINTER_UP:
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						break;
					case MotionEvent.ACTION_DOWN: {
						if (mDragPointerId == -1) {
							mLastDragInitiator.getReactorContainer().setTag(R.id.DRAG_ACTION_CLICK_PERSIST, null);
						}
						break;
					}
					default: {
						if (mDragPointerId != -1) {
							boolean in = true;
							int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
							if(!mActuallyDragging && mLastDragInitiator.getActionOnCancel() && mLastDragInitiator.getReactorContainer() != null){
								View dragInitatorContainer=mLastDragInitiator.getContainer();
								dragInitatorContainer.getLocationInWindow(viewPosition);
								in = ev.getRawX() >= viewPosition[0] && ev.getRawY() >= viewPosition[1] && ev.getRawX() < viewPosition[0] + dragInitatorContainer.getWidth() && ev.getRawY() < viewPosition[1] + dragInitatorContainer.getHeight() && dragInitatorContainer.isEnabled();
							}
							if (!in || !mLastDragInitiator.getActionOnCancel() && Math.sqrt(Math.pow((mLastDragInitiator.getStartTouchPositionX() - ev.getX()), 2) + Math.pow((mLastDragInitiator.getStartTouchPositionY() - ev.getY()), 2)) >= slop){
								mActuallyDragging = true;
								ev.setAction(MotionEvent.ACTION_CANCEL);
								mLastDragInitiator.onDragStart();
								for (View v : mPossibleDragReactors) {
									if (v instanceof DragReactor) {
										DragReactor reactor = (DragReactor) v;
										reactor.onDragReactorReady(reactor, mLastDragInitiator);
									}
								}
							}
						}
					}
				}
				super.dispatchTouchEvent(ev);
				if (mDragPointerId == -1) {
					switch (ev.getActionMasked()) {
						case MotionEvent.ACTION_POINTER_UP:
						case MotionEvent.ACTION_UP:
						case MotionEvent.ACTION_CANCEL:
							mLastDragInitiator.onDragStop();
							mLastDragInitiator = null;
							break;
						case MotionEvent.ACTION_DOWN: {
							if (mLastDragInitiator.getReactorContainer().getTag(R.id.DRAG_ACTION_CLICK_PERSIST) == null) {
								mLastDragInitiator.onDragStop();
								mLastDragInitiator = null;
							}
						}
					}
				}
				if (nCurrentPointerId == mDragPointerId)
					ev.setAction(nLastAction);
				if (mLastDragInitiator != null && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP) && mDragPointerId != -1) {
					if (!mActuallyDragging && mLastDragInitiator.keepContainerOnClick() && mLastDragInitiator.getReactorContainer() != null && ev.getActionMasked() == MotionEvent.ACTION_UP) {
						mLastDragInitiator.onDragStart();
						mLastDragInitiator.getReactorContainer().setTag(R.id.DRAG_ACTION_CLICK_PERSIST, true);
					} else
						mLastDragInitiator = null;
					mActuallyDragging = false;
					mDragPointerId = -1;
				}
			} else {
				super.dispatchTouchEvent(ev);
			}
		}
		return true;
	}

	/**
	 * Start dragging action. Must be called from DragInitator.onTouchEvent
	 *
	 * @param nReactorType The reactor type.
	 * @param nPointerId   The pointer ID.
	 * @param dragFrom     DragInitiator which invoked dragging.
	 * @return true if drag started
	 */
	public boolean startDrag(int nReactorType, int nPointerId, DragInitiator dragFrom) {
		if (dragFrom == null)
			throw new NullPointerException();

		// If a part of screen is already touched, drag will not start
		// since it's illogical to cover the screen while another object
		// is currently processing the touch event.
		if (mCurrentTouches > 1)
			return false;

		if(mLastDragInitiator != null){
			mLastDragInitiator.onDragStop();
		}

		mLastDragInitiator = dragFrom;
		mLastDragInitiator.onDragPrepare();
		mPossibleDragReactors.clear();
		findDragReactorsRecursive(nReactorType, mPossibleDragReactors, mLastDragInitiator.getReactorContainer());
		mDragPointerId = nPointerId;

		performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

		return true;
	}

	/**
	 * Stop dragging action.
	 */
	public void stopDrag() {
		for (View v : mPossibleDragReactors) {
			if (v instanceof DragReactor) {
				DragReactor reactor = (DragReactor) v;
				reactor.onDragReactorStop(reactor);
			}
		}

		mLastDragInitiator.onDragStop();

		mActuallyDragging = false;
		mLastDragInitiator = null;
		mDragPointerId = -1;
		getLocationInWindow(myPosition);
		mActiveReactorDownTime.clear();
		mActiveReactors.clear();
	}

	/**
	 * Get drag initiator.
	 *
	 * @return DragInitiator that invoked current dragging action, or null if
	 * not dragging
	 */
	public DragInitiator getDragInitiator() {
		return mLastDragInitiator;
	}
}
