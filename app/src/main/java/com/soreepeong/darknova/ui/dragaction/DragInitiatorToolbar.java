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

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.soreepeong.darknova.R;

/**
 * FrameLayout with DragInitiator implemented.
 * 
 * @author Soreepeong
 * 
 */
public class DragInitiatorToolbar extends Toolbar{

	private final int mViewLocation[] = new int[2];
	/** DragInitiator associated to this button. */
	private DragInitiator mDragInitiator;
	/** Action Type of this button */
	private int mDragActionType = 0;

	public DragInitiatorToolbar(Context context){
		super(context);
		init(context, null);
	}

	public DragInitiatorToolbar(Context context, AttributeSet attrs){
		super(context, attrs);
		init(context, attrs);
	}

	public DragInitiatorToolbar(Context context, AttributeSet attrs, int defStyle){
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	/**
	 * Initialize variables.
	 */
	private void init(Context context, AttributeSet attrs){
		mDragInitiator = new DragInitiator(this);
		setFocusable(false);
		setFocusableInTouchMode(false);

		if(attrs == null)
			return;

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DragInitiator);
		for (int i = 0, i_ = a.getIndexCount(); i < i_; ++i){
			switch (a.getIndex(i)){
				case R.styleable.DragInitiator_action_type:
					mDragActionType = a.getResourceId(i, R.id.DRAG_ACTION_TYPE_ALL);
					break;
				case R.styleable.DragInitiator_action_on_leave:
					mDragInitiator.setActionOnCancel(a.getBoolean(i, false));
					break;
			}
		}
		a.recycle();
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if(mDragActionType == R.id.DRAG_ACTION_TYPE_ALL)
			return;
		ViewGroup vg = (ViewGroup) getParent();
		while(vg != null){
			if(vg.getId() == mDragActionType)
				break;
			if(!(vg.getParent() instanceof ViewGroup))
				break;
			View v = vg.findViewById(mDragActionType);
			if(v != null){
				mDragInitiator.setReactorContainer(v);
				if(isInEditMode())
					return;
				if (v.getVisibility() != View.VISIBLE)
					return;
				v.setVisibility(View.GONE);
				return;
			}
			vg = (ViewGroup) vg.getParent();
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		if(!isEnabled() || getVisibility() != View.VISIBLE)
			return false;
		if(event.getActionMasked() == MotionEvent.ACTION_DOWN){
			boolean mIsTitleBar = true; // Hack Warning
			for(int i = getChildCount() - 1; mIsTitleBar && i >=0; i--){
				if(!(getChildAt(i) instanceof TextView)){
					getChildAt(i).getLocationOnScreen(mViewLocation);
					mIsTitleBar = !(event.getRawX() >= mViewLocation[0] && event.getRawY() >= mViewLocation[1] && event.getRawX() < mViewLocation[0] + getChildAt(i).getWidth() && event.getRawY() < mViewLocation[1] + getChildAt(i).getHeight() && getChildAt(i).isEnabled());
				}
			}
			if(mIsTitleBar && !mDragInitiator.startDrag(mDragActionType, event)){
				event.setAction(MotionEvent.ACTION_CANCEL);
			}
			return super.dispatchTouchEvent(event);
		}
		return super.dispatchTouchEvent(event);
	}
}
