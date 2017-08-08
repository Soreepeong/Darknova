package com.soreepeong.darknova.tools;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.AnimRes;
import android.support.annotation.AttrRes;
import android.support.annotation.LayoutRes;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

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
		a.setAnimationListener(new Animation.AnimationListener(){
			@Override
			public void onAnimationStart(Animation animation){

			}

			@Override
			public void onAnimationEnd(Animation animation){
				v.clearAnimation();
				v.setVisibility(View.GONE);
			}

			@Override
			public void onAnimationRepeat(Animation animation){

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

	public static boolean inflateResizedInternal(View v, float scale){
		if(!(v instanceof ViewGroup))
			return false;
		for(int i = ((ViewGroup) v).getChildCount()-1 ; i >= 0; i--){
			View c = ((ViewGroup) v).getChildAt(i);
			c.setPadding((int)(c.getPaddingLeft() * scale), (int)(c.getPaddingTop() * scale), (int)(c.getPaddingRight() * scale), (int)(c.getPaddingBottom() * scale));
			if(inflateResizedInternal(c, scale))
				continue;
			ViewGroup.LayoutParams lp = c.getLayoutParams();
			if(c instanceof TextView)
				((TextView) c).setTextSize(TypedValue.COMPLEX_UNIT_PX, ((TextView) c).getTextSize() * scale);
			if(lp.width != ViewGroup.LayoutParams.WRAP_CONTENT && lp.width != ViewGroup.LayoutParams.MATCH_PARENT)
				lp.width = (int) (lp.width * scale);
			if(lp.height != ViewGroup.LayoutParams.WRAP_CONTENT && lp.height != ViewGroup.LayoutParams.MATCH_PARENT)
				lp.height = (int) (lp.height * scale);
			if(lp instanceof ViewGroup.MarginLayoutParams){
				ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
				mlp.setMargins((int) (mlp.leftMargin * scale), (int) (mlp.topMargin * scale), (int) (mlp.rightMargin * scale), (int) (mlp.bottomMargin * scale));

				if(Build.VERSION.SDK_INT >= 17){
					mlp.setMarginStart((int) (mlp.getMarginStart() * scale));
					mlp.setMarginEnd((int) (mlp.getMarginEnd() * scale));
				}
			}
			c.setLayoutParams(lp);
		}
		return true;
	}

	public static View inflateResized(LayoutInflater inflater, @LayoutRes int res, ViewGroup parent, boolean attach, float scale){
		View v = inflater.inflate(res, parent, attach);
		inflateResizedInternal(v, scale);
		return v;
	}
}
