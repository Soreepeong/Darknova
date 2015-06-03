package com.soreepeong.darknova.ui.dragaction;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import com.soreepeong.darknova.R;

/**
 * Created by Soreepeong on 2015-05-31.
 */
public class DragActionOutsideDetectFrameLayout extends FrameLayout{

	public DragActionOutsideDetectFrameLayout(Context context){
		super(context);
	}

	public DragActionOutsideDetectFrameLayout(Context context, AttributeSet attrs){
		super(context, attrs);
	}

	public DragActionOutsideDetectFrameLayout(Context context, AttributeSet attrs, int defStyleAttr){
		super(context, attrs, defStyleAttr);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		setTag(R.id.DRAG_ACTION_CLICK_PERSIST, true);
		return super.dispatchTouchEvent(ev);
	}
}
