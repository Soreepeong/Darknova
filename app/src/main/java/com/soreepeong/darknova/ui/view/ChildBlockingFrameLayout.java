package com.soreepeong.darknova.ui.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * @author Soreepeong
 */
public class ChildBlockingFrameLayout extends FrameLayout {
	public ChildBlockingFrameLayout(Context context) {
		super(context);
	}

	public ChildBlockingFrameLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ChildBlockingFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@TargetApi(21)
	public ChildBlockingFrameLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return true;
	}
}
