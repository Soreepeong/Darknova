package com.soreepeong.darknova.ui.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.soreepeong.darknova.R;

/**
 * @author Soreepeong
 */
public class ScalingLayout extends FrameLayout {

	private float mPropWidth, mPropHeight;
	private boolean mPropWidthBased;

	public ScalingLayout(Context context) {
		super(context);
	}

	public ScalingLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		readAttributes(context, attrs);
	}

	public ScalingLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		readAttributes(context, attrs);
	}

	@TargetApi(21)
	public ScalingLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		readAttributes(context, attrs);
	}

	private void readAttributes(Context context, AttributeSet attrs) {
		if (attrs == null)
			return;
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ScalingLayout);
		mPropHeight = a.getFloat(R.styleable.ScalingLayout_ratio_height, 1);
		mPropWidth = a.getFloat(R.styleable.ScalingLayout_ratio_width, 1);
		mPropWidthBased = a.getBoolean(R.styleable.ScalingLayout_ratio_width_based, false);
		a.recycle();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (mPropWidthBased)
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) (MeasureSpec.getSize(widthMeasureSpec) * mPropHeight / mPropWidth), MeasureSpec.getMode(widthMeasureSpec)));
		else
			super.onMeasure(MeasureSpec.makeMeasureSpec((int) (MeasureSpec.getSize(heightMeasureSpec) * mPropHeight / mPropWidth), MeasureSpec.getMode(heightMeasureSpec)), heightMeasureSpec);
	}
}
