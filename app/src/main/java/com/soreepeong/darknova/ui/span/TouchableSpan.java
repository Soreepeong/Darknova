package com.soreepeong.darknova.ui.span;

import android.support.annotation.NonNull;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;

public abstract class TouchableSpan extends ClickableSpan implements OnLongClickListener {

	private TextStyle mNormal, mPressed, mCurrent;

	private int mAlphaOverrideValue;
	private boolean mAlphaOverride;

	public TouchableSpan(int nColor, int nBackgroundColor, boolean bBold, boolean bUnderline, int nPressColor, int nPressBackgroundColor, boolean bPressBold, boolean bPressUnderline) {
		super();
		mCurrent = mNormal = new TextStyle(nColor, nBackgroundColor, bBold, bUnderline);
		mPressed = new TextStyle(nPressColor, nPressBackgroundColor, bPressBold, bPressUnderline);
	}

	@Override
	public boolean onLongClick(View v) {
		return false;
	}

	public void setAlphaOverride(boolean bOverride) {
		mAlphaOverride = bOverride;
	}

	public void setAlphaOverrideValue(int value) {
		mAlphaOverrideValue = value;
	}

	public void onTouch(View widget, MotionEvent me) {
		switch (me.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				mCurrent = mPressed;
				widget.invalidate();
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				mCurrent = mNormal;
				widget.invalidate();
				break;
		}
	}

	@Override
	public void updateDrawState(@NonNull TextPaint ds) {
		ds.setUnderlineText(mCurrent.mUnderline);
		ds.setColor(mAlphaOverride ? ((mCurrent.mColor & 0x00FFFFFF) | (mAlphaOverrideValue << 24)) : mCurrent.mColor);
		ds.setFakeBoldText(mCurrent.mBold);
		ds.bgColor = mAlphaOverride ? ((mCurrent.mBackgroundColor & 0x00FFFFFF) | (Math.min(mCurrent.mBackgroundColor >> 24, mAlphaOverrideValue) << 24)) : mCurrent.mBackgroundColor;
	}

	private class TextStyle {
		public int mColor, mBackgroundColor;
		public boolean mBold, mUnderline;

		public TextStyle(int nColor, int nBackgroundColor, boolean bBold, boolean bUnderline) {
			mColor = nColor;
			mBackgroundColor = nBackgroundColor;
			mBold = bBold;
			mUnderline = bUnderline;
		}
	}
}
