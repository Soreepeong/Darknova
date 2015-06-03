package com.soreepeong.darknova.core;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

/**
 * Created by Soreepeong on 2015-05-23.
 */
public class ResTools {
	public static Drawable getDrawableByAttribute(Context ctx, int attrId){
		int[] textSizeAttr = new int[] { attrId };
		TypedArray a = ctx.obtainStyledAttributes(textSizeAttr);
		Drawable ret=a.getDrawable(0);
		a.recycle();
		return ret;
	}
	public static int getIdByAttribute(Context ctx, int attrId){
		int[] textSizeAttr = new int[] { attrId };
		TypedArray a = ctx.obtainStyledAttributes(textSizeAttr);
		int ret = a.getResourceId(0, 0);
		a.recycle();
		return ret;
	}
	public static int getColorByAttribute(Context ctx, int attrId){
		int[] textSizeAttr = new int[] { attrId };
		TypedArray a = ctx.obtainStyledAttributes(textSizeAttr);
		int ret = a.getColor(0, 0);
		a.recycle();
		return ret;
	}
	public static void hideWithAnimation(Context ctx, final View v, int resId){
		Animation a = AnimationUtils.loadAnimation(ctx, resId);
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
		v.setVisibility(View.GONE);
	}
}
