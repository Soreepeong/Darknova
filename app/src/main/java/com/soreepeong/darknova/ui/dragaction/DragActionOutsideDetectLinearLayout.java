package com.soreepeong.darknova.ui.dragaction;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.soreepeong.darknova.R;

/**
 * Created by Soreepeong on 2015-05-31.
 */
public class DragActionOutsideDetectLinearLayout extends LinearLayout{

	public DragActionOutsideDetectLinearLayout(Context context){
		super(context);
	}

	public DragActionOutsideDetectLinearLayout(Context context, AttributeSet attrs){
		super(context, attrs);
	}

	public DragActionOutsideDetectLinearLayout(Context context, AttributeSet attrs, int defStyleAttr){
		super(context, attrs, defStyleAttr);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		setTag(R.id.DRAG_ACTION_CLICK_PERSIST, true);
		return super.dispatchTouchEvent(ev);
	}
}
