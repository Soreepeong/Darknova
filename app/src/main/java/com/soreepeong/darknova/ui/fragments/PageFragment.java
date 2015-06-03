package com.soreepeong.darknova.ui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.soreepeong.darknova.core.StringTools;
import com.soreepeong.darknova.core.TimedStorage;
import com.soreepeong.darknova.settings.Page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * Created by Soreepeong on 2015-05-29.
 */
public abstract class PageFragment extends Fragment{

	private static final SparseArray<TimedStorage<View>> mCachedPageViews = new SparseArray<>();

	public static View obtainPageView(int layoutId, LayoutInflater inflater, ViewGroup container){
		View res = null;
		if(mCachedPageViews.get(layoutId) != null)
			res = mCachedPageViews.get(layoutId).obtain();
		if(res == null)
			res = inflater.inflate(layoutId, container, false);
		return res;
	}

	public static void releasePageView(int layoutId, View v){
		if(mCachedPageViews.get(layoutId) == null)
			mCachedPageViews.put(layoutId, new TimedStorage<View>());
		mCachedPageViews.get(layoutId).release(v);
	}

	private static final ArrayList<Thread> mRunningRefreshers = new ArrayList<>(), mQueuedRefreshers = new ArrayList<>();
	private static final Thread mRefresherExecutor = new Thread(){
		@Override
		public void run() {
			try{
				while(true){
					synchronized (mQueuedRefreshers){
						while(mQueuedRefreshers.isEmpty() || mRunningRefreshers.size() >= 4)
							mQueuedRefreshers.wait();
						final Thread t = mQueuedRefreshers.remove(0);
						synchronized (mRunningRefreshers){
							mRunningRefreshers.add(t);
						}
						t.start();
					}
				}
			}catch(InterruptedException e){
				e.printStackTrace();
			}
		}
	};

	{
		if(!mRefresherExecutor.isAlive())
			mRefresherExecutor.start();
	}

	protected static void addRefresher(Thread t){
		synchronized(mQueuedRefreshers){
			mQueuedRefreshers.add(t);
			mQueuedRefreshers.notify();
		}
	}

	protected static void removeRefresher(Thread t){
		synchronized (mRunningRefreshers){
			mRunningRefreshers.remove(t);
		}
		synchronized(mQueuedRefreshers){
			mQueuedRefreshers.remove(t);
			mQueuedRefreshers.notify();
		}
	}


	protected Page mPage;
	protected boolean mIsActive;
	protected String mQuickFilterString;
	protected Pattern mQuickFilterPattern;

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
		mPage = args.getParcelable("page");
		if(!Page.pages.contains(mPage))
			Page.pages.add(mPage);
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
	public void onStart() {
		super.onStart();
		mIsActive = true;
	}

	@Override
	public void onResume() {
		super.onResume();
		mIsActive = true;
	}
}
