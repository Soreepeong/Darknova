package com.soreepeong.darknova.ui.span;

/**
 * @author Soreepeong
 */
public interface SelfInvalidatingSpan {
	void setCallback(Callback callback);

	interface Callback {
		void invalidateSpan(SelfInvalidatingSpan who);

		void scheduleSpan(SelfInvalidatingSpan who, Runnable what, long when);

		void unscheduleSpan(SelfInvalidatingSpan who, Runnable what);
	}
}
