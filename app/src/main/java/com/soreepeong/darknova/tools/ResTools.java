package com.soreepeong.darknova.tools;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.AnimRes;
import android.support.annotation.AttrRes;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

/**
 * Various resource tool functions
 *
 * @author Soreepeong
 */
public class ResTools {

	private ResTools() {
	}

	/**
	 * Get drawable using themed context
	 *
	 * @param ctx    Themed context
	 * @param attrId Attribute ID
	 * @return Drawable
	 */
	public static Drawable getDrawableByAttribute(Context ctx, @AttrRes int attrId) {
		int[] textSizeAttr = new int[]{attrId};
		TypedArray a = ctx.obtainStyledAttributes(textSizeAttr);
		Drawable ret = a.getDrawable(0);
		a.recycle();
		return ret;
	}

	/**
	 * Get resource ID using themed context
	 *
	 * @param ctx    Themed context
	 * @param attrId Attribute ID
	 * @return Resource ID
	 */
	public static int getIdByAttribute(Context ctx, @AttrRes int attrId) {
		int[] textSizeAttr = new int[]{attrId};
		TypedArray a = ctx.obtainStyledAttributes(textSizeAttr);
		int ret = a.getResourceId(0, 0);
		a.recycle();
		return ret;
	}

	/**
	 * Get color using themed context
	 *
	 * @param ctx    Themed context
	 * @param attrId Attribute ID
	 * @return Color
	 */
	public static int getColorByAttribute(Context ctx, @AttrRes int attrId) {
		int[] textSizeAttr = new int[]{attrId};
		TypedArray a = ctx.obtainStyledAttributes(textSizeAttr);
		int ret = a.getColor(0, 0);
		a.recycle();
		return ret;
	}

	/**
	 * Hide view with animation
	 *  @param v               View to hide
	 * @param resId           Hide animation
	 * @param hideImmediately Set if the view needs to be gone immediately
	 */
	public static void hideWithAnimation(final View v, @AnimRes int resId, boolean hideImmediately) {
		if(v == null || v.getVisibility() != View.VISIBLE)
			return;
		Animation a = AnimationUtils.loadAnimation(v.getContext(), resId);
		a.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {

			}

			@Override
			public void onAnimationEnd(Animation animation) {
				v.clearAnimation();
				v.setVisibility(View.GONE);
			}

			@Override
			public void onAnimationRepeat(Animation animation) {

			}
		});
		v.startAnimation(a);
		v.setVisibility(hideImmediately ? View.GONE : View.INVISIBLE);
	}

	/**
	 * Show view with animation
	 *  @param v     View to hide
	 * @param resId Hide animation
	 */
	public static void showWithAnimation(final View v, @AnimRes int resId) {
		if(v == null || v.getVisibility() == View.VISIBLE)
			return;
		v.setVisibility(View.VISIBLE);
		v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), resId));
		v.clearAnimation();
	}
}
