package com.soreepeong.darknova.ui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.soreepeong.darknova.core.ThreadScheduler;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.tools.TimedStorage;

import java.util.regex.Pattern;

/**
 * Fragment that displays page, and some more
 * ONLY to be used in {@see MainActivity}
 *
 * @author Soreepeong
 */
public abstract class PageFragment extends Fragment{

	private static final SparseArray<TimedStorage<View>> mCachedPageViews = new SparseArray<>();
	private static final ThreadScheduler mRefreshScheduler = new ThreadScheduler(4, "Refresher");

	protected Page mPage;
	protected boolean mIsActive;
	protected String mQuickFilterString;
	protected Pattern mQuickFilterPattern;

	public static View obtainPageView(int layoutId, LayoutInflater inflater, ViewGroup container) {
		View res = null;
		if (mCachedPageViews.get(layoutId) != null)
			res = mCachedPageViews.get(layoutId).obtain();
		if (res == null)
			res = inflater.inflate(layoutId, container, false);
		return res;
	}

	public static void releasePageView(int layoutId, View v) {
		if (mCachedPageViews.get(layoutId) == null)
			mCachedPageViews.put(layoutId, new TimedStorage<View>());
		mCachedPageViews.get(layoutId).release(v);
	}

	protected static void addRefresher(Runnable t) {
		mRefreshScheduler.schedule(t);
	}

	protected static void removeRefresher(Runnable t) {
		mRefreshScheduler.cancel(t);
	}

	public void scrollToTop(){}
	public void onCompleteRefresh(){}
	public void onPageEnter(){}
	public void onPageLeave(){}

	public abstract boolean isSomethingSelected();
	public abstract void clearSelection();
	public abstract void loadBackground();

	protected void selectionChanged(){
		getActivity().invalidateOptionsMenu();
	}

	public void setQuickFilter(String input){
		mQuickFilterPattern = StringTools.getMaybePatternSearcher(input);
	}

	@Override
	public void setArguments(Bundle args) {
		super.setArguments(args);
		performArguments(args);
	}

	protected void performArguments(Bundle args){
		mPage = Page.getLoaded((Page) args.getParcelable("page"));
	}

	public Page getRepresentingPage(){
		return mPage;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null)
			performArguments(getArguments());
	}

	@Override
	public void onPause() {
		super.onPause();
		mIsActive = false;
	}

	@Override
	public void onResume() {
		super.onResume();
		mIsActive = true;
	}
}
