package com.soreepeong.darknova.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.text.Layout;
import android.text.Spannable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.TextView;

import com.soreepeong.darknova.ui.span.TouchableSpan;
import com.soreepeong.darknova.ui.span.UserImageSpan;

/**
 * http://stackoverflow.com/questions/8558732/listview-textview-with-linkmovementmethod-makes-list-item-unclickable
 */

public class ClickableSpanTextView extends TextView {

	private TouchableSpan mSpanTouchStart;
	private boolean mLongClicked;
	private final GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
		@Override
		public void onLongPress(MotionEvent e) {
			if (mSpanTouchStart != null)
				if (mSpanTouchStart.onLongClick(ClickableSpanTextView.this)) {
					performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
					e.setAction(MotionEvent.ACTION_CANCEL);
					mSpanTouchStart.onTouch(ClickableSpanTextView.this, e);
					mSpanTouchStart = null;
					mLongClicked = true;
				}
		}
	});

	public ClickableSpanTextView(Context context) {
		super(context);
	}

	public ClickableSpanTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ClickableSpanTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public boolean hasFocusable() {
		return false;
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		if (getText() instanceof Spannable) {
			Spannable sp = (Spannable) getText();
			for (UserImageSpan t : sp.getSpans(0, sp.length(), UserImageSpan.class))
				if (t.isPreparing())
					postInvalidateDelayed(500);
		}
		super.onDraw(canvas);
	}

	@Override
	public void setText(CharSequence text, BufferType type) {
		super.setText(text, type);
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		TouchableSpan spanBelow = getPressedTouchableSpan((int) event.getX(), (int) event.getY());
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				mSpanTouchStart = spanBelow;
				if (spanBelow != null) {
					spanBelow.onTouch(this, event);
					mLongClicked = false;
					gestureDetector.onTouchEvent(event);
					performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
					return true;
				}
				break;
			case MotionEvent.ACTION_UP:
				if (mLongClicked)
					return true;
				if (mSpanTouchStart != null) {
					gestureDetector.onTouchEvent(event);
					if (spanBelow != null) {
						spanBelow.onClick(this);
						spanBelow.onTouch(this, event);
					}
					return true;
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (mSpanTouchStart != null && mSpanTouchStart != spanBelow) {
					event.setAction(MotionEvent.ACTION_CANCEL);
					mSpanTouchStart.onTouch(this, event);
					mSpanTouchStart = null;
					gestureDetector.onTouchEvent(event);
					return true;
				}
				break;
			case MotionEvent.ACTION_CANCEL:
				if (mSpanTouchStart != null) {
					gestureDetector.onTouchEvent(event);
					mSpanTouchStart.onTouch(this, event);
					mSpanTouchStart = null;
				}
				break;
		}
		return false;
	}

	private TouchableSpan getPressedTouchableSpan(int x, int y) {
		CharSequence text = getText();
		Spannable spanText = Spannable.Factory.getInstance().newSpannable(text);

		x -= getTotalPaddingLeft();
		y -= getTotalPaddingTop();

		x += getScrollX();
		y += getScrollY();

		Layout layout = getLayout();
		int line = layout.getLineForVertical(y);
		int off = layout.getOffsetForHorizontal(line, x);

		TouchableSpan[] link = spanText.getSpans(off, off, TouchableSpan.class);
		if (link.length == 0)
			return null;
		return link[0];
	}
}
