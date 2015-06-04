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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.soreepeong.darknova.R;

/**
 * Button with DragInitiator implemented.
 * 
 * @author Soreepeong
 * 
 */
public class DragInitiatorButton extends Button{

	/** DragInitiator associated to this button. */
	private DragInitiator mDragInitiator;

	/** Action Type of this button */
	private int mDragActionType = 0;

	public DragInitiatorButton(Context context){
		super(context);
		init(context, null);
	}

	public DragInitiatorButton(Context context, AttributeSet attrs){
		super(context, attrs);
		init(context, attrs);
	}

	public DragInitiatorButton(Context context, AttributeSet attrs, int defStyle){
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

	/**
	 * Get action type of this button.
	 */
	public int getActionType() {
		return mDragActionType;
	}

	/**
	 * Set action type of this button.
	 *
	 * @param nAction
	 *            Action type.
	 */
	public void setActionType(int nAction){
		mDragActionType = nAction;
	}

	/**
	 * Set DragInitiatorCallbacks for this DragInitiator.
	 * 
	 * @param dragInitiatorCallbacks
	 *            DragInitiatorCallbacks to set.
	 */
	public void setDragInitiatorCallbacks(DragInitiatorCallbacks dragInitiatorCallbacks){
		mDragInitiator.setDragInitiatorCallbacks(dragInitiatorCallbacks);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		if(!isEnabled())
			return false;
		if(event.getActionMasked() == MotionEvent.ACTION_DOWN){
			if(!mDragInitiator.startDrag(mDragActionType, event))
				event.setAction(MotionEvent.ACTION_CANCEL);
			return super.dispatchTouchEvent(event);
		}
		return super.dispatchTouchEvent(event);
	}
}
