package com.soreepeong.darknova.ui.fragments;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.PageElement;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.twitter.TwitterStreamServiceReceiver;
import com.soreepeong.darknova.ui.MainActivity;
import com.soreepeong.darknova.ui.MediaPreviewActivity;
import com.soreepeong.darknova.ui.dragaction.DragInitiator;
import com.soreepeong.darknova.ui.dragaction.DragInitiatorButton;
import com.soreepeong.darknova.ui.dragaction.DragInitiatorCallbacks;
import com.soreepeong.darknova.ui.span.TweetSpanner;
import com.soreepeong.darknova.ui.span.UserImageSpan;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fragment that displays timeline, and some more
 * ONLY to be used in {@see MainActivity}
 *
 * @author Soreepeong
 */
public class TimelineFragment extends PageFragment<Tweet> implements Handler.Callback, ImageCache.OnImageCacheReadyListener, View.OnClickListener {
	public static final byte LOAD_MORE_ITEM_AVAILABLE = 0;
	public static final byte LOAD_MORE_ITEM_LOADING = -1;
	private static final int MESSAGE_TIME_UPDATE = 1;
	private static final int MESSAGE_SAVE_LIST = 3;
	private static final int SAVE_DELAY = 5000;
	private static final int SAVE_LENGTH = 200;
	private static final int MAX_MEMORY_HOLD = 200;

	private ObjectAnimator mProgressAnimator;
	private View.OnLayoutChangeListener mListSizeChangeListener;
	private PageRefresher mUpdateRefresher;
	private Thread mListApplier;

	private boolean mSavePending;

	private FilterApplier mFilterApplier;

	public TimelineFragment() {
		super();
	}

	public static TimelineFragment newInstance(String cacheDir, Page p) {
		TimelineFragment fragment = new TimelineFragment();
		Bundle b = new Bundle();
		b.putParcelable("page", p);
		b.putString("cache-dir", cacheDir);
		fragment.setArguments(b);
		return fragment;
	}

	private void applyFilter(List<Tweet> oldList, String... args) {
		if (mFilterApplier != null)
			mFilterApplier.cancel(true);
		mFilterApplier = new FilterApplier(oldList, mList);
		mFilterApplier.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, args);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mPagePositionPreferences = getActivity().getSharedPreferences("page-position-" + mPage.generateUniqid(), 0);
		try {
			mPage.mPageLastItemId = mPagePositionPreferences.getLong("id", 0);
			mPage.mPageNewestSeenItemId = mPagePositionPreferences.getLong("seen_id", 0);
			mPage.mPageLastOffset = mPagePositionPreferences.getInt("lastoffset", 0);
			mPage.mIsListAtTop = mPagePositionPreferences.getBoolean("isattop", true);
			if (savedInstanceState == null || savedInstanceState.getParcelableArrayList("selected-tweets") == null)
				return;
			mSelectedList.clear();
			ArrayList<Tweet> selectedList = savedInstanceState.getParcelableArrayList("selected-tweets");
			mSelectedList.addAll(selectedList);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelableArrayList("selected-tweets", mSelectedList);
	}

	@Override
	public void onImageCacheReady(ImageCache cache) {
		mImageCache = cache;
		if (mAdapter != null)
			mAdapter.notifyDataSetChanged();
	}

	@Override
	protected void performArguments(Bundle args) {
		if (mAdapter != null)
			throw new RuntimeException("performArguments must be called before attachment");
		super.performArguments(args);
		if (mPage == null)
			return;
		mQuickFilterOriginalString = null;
		mList = null;
		mSelectedList.clear();
		mLoadMoreItems.clear();
		mIsPagePrepared = false;

		String cacheDir = args.getString("cache-dir");
		String uniqid = StringTools.UrlEncode(mPage.generateUniqid());
		mListCacheFile = new File(cacheDir, "list_cache_" + uniqid);
		mBackgroundLoader = new OnCreateBackground(this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {

		if (container == null)
			return null;

		mViewRoot = obtainPageView(R.layout.fragment_timeline, inflater, container);
		super.onCreateView(inflater, container, savedInstanceState);

		mViewList = (RecyclerView) mViewRoot.findViewById(R.id.list);
		mViewRefresher = (SwipeRefreshLayout) mViewRoot.findViewById(R.id.swipeRefresher);
		mViewProgress = (ProgressBar) mViewRoot.findViewById(R.id.progress_horizontal);
		mViewEmptyIndicator = (ImageView) mViewRoot.findViewById(R.id.empty_image);
		mViewUnderActionbar = (FrameLayout) mViewRoot.findViewById(R.id.under_actionbar);
		mViewUnreadTweetCount = (TextView) mViewRoot.findViewById(R.id.unread);
		mColorAccent = ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent);

		mViewRefresher.setOnRefreshListener(this);
		mViewUnreadTweetCount.setOnClickListener(this);
		mListLayout = new LinearLayoutManager(getActivity());
		mListLayout.setOrientation(LinearLayoutManager.VERTICAL);
		mViewList.setLayoutManager(mListLayout);
		mViewList.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				MainActivity activity = (MainActivity) getActivity();
				ActionBar bar = activity != null ? activity.getSupportActionBar() : null;
				if (bar != null) {
					int firstVisibleItem = mListLayout.findFirstVisibleItemPosition();
					View v = mListLayout.findViewByPosition(firstVisibleItem);
					int topRowVerticalPosition = v == null ? 0 : v.getTop() - mViewList.getPaddingTop();
					mViewRefresher.setEnabled(mPage.mIsListAtTop = (firstVisibleItem == 0 && topRowVerticalPosition >= 0));
					float touchSlop = ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop();

					if (dy <= 0) // scrolling up?
						processVisibleItems(activity, false);
					if ((firstVisibleItem == 0 && mViewList.getChildAt(0).getTop() > -bar.getHeight()) || (mListLayout.findFirstCompletelyVisibleItemPosition() == 0 && mListLayout.findLastCompletelyVisibleItemPosition() == mListLayout.getItemCount()) || dy < -touchSlop) {
						activity.showActionBar();
					} else if (dy > touchSlop) {
						activity.hideActionBar();
					}
				}
			}
		});
		mViewList.addOnLayoutChangeListener(mListSizeChangeListener = new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
				v.post(new Runnable() {
					@Override
					public void run() {
						MainActivity activity = (MainActivity) getActivity();
						if (activity != null && mListLayout != null) {
							if (mListLayout.findFirstCompletelyVisibleItemPosition() == 0 && mListLayout.findLastCompletelyVisibleItemPosition() == mListLayout.getItemCount()) {
								activity.showActionBar();
							}
						}
					}
				});
			}
		});
		// Run after measurement
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				MainActivity activity = (MainActivity) getActivity();
				ActionBar bar = activity != null ? activity.getSupportActionBar() : null;
				if (bar == null)
					return;

				int defaultEnd = getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material); //(int) (getResources().getDisplayMetrics().density * 64);
				mViewRefresher.setProgressViewOffset(false, bar.getHeight() - mViewRefresher.getProgressCircleDiameter(), bar.getHeight() - mViewRefresher.getProgressCircleDiameter() + defaultEnd);

				// actionbar always shown at the time of inflation
				mViewUnderActionbar.setPadding(0, getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material), 0, 0);
			}
		});

		TwitterStreamServiceReceiver.addStreamCallback(this);
		mImageCache = ImageCache.getCache(getActivity(), this);
		if (mBackgroundLoader == null)
			mBackgroundLoader = new OnCreateBackground(this);
		else if (mIsPagePrepared)
			mBackgroundLoader.postExecuteNow();

		return mViewRoot;
	}

	private void processVisibleItems(MainActivity activity, boolean forceRenew) {
		if (activity == null) return;
		int visiblePaddingTop = activity.isActionBarVisible() ? mViewList.getPaddingTop() : 0;
		if (mList != null && !mList.isEmpty() && mQuickFilteredList == null)
			for (int i = mListLayout.findFirstVisibleItemPosition(); i <= mListLayout.findLastVisibleItemPosition(); i++) {
				if (mAdapter.getItemViewType(i) != R.layout.row_tweet)
					continue;
				int position = mAdapter.adapterPositionToListIndex(i);
				Tweet tweet = mQuickFilteredList == null ? mList.get(position) : mQuickFilteredList.get(position).mObject;
				View v = mListLayout.findViewByPosition(i);
				if (v != null && v.getTop() >= visiblePaddingTop)
					itemRead(tweet.id, forceRenew);
				if (mLoadMoreItems.containsKey(tweet))
					loadOlderThan(mList.get(position));
			}
	}

	@Override
	protected void itemRead(long id, boolean forceUpdate) {
		if (id != -1) {
			if (mList.isEmpty())
				mPage.mPageNewestSeenItemId = -1;
			else if (mPage.mPageNewestSeenItemId < id)
				mPage.mPageNewestSeenItemId = id;
			else if (!forceUpdate)
				return;
		}
		int unread = Collections.binarySearch(mList, Tweet.getTweet(mPage.mPageNewestSeenItemId), Collections.reverseOrder());
		if (unread < 0) unread = -1 - unread;
		unread = Math.min(unread, mList.size());
		mViewUnreadTweetCount.setText(getString(R.string.page_unread).replace("%", Integer.toString(unread)));
		if (unread > 0 && mPage.mPageNewestSeenItemId != -1) {
			ResTools.showWithAnimation(mViewUnreadTweetCount, R.anim.show_downward);
		} else
			ResTools.hideWithAnimation(mViewUnreadTweetCount, R.anim.hide_upward, true);
	}

	@Override
	public void onDestroyView() {
		TwitterStreamServiceReceiver.removeStreamCallback(this);

		mViewList.clearOnScrollListeners();
		mViewList.removeOnLayoutChangeListener(mListSizeChangeListener);
		mViewRefresher.setOnRefreshListener(null);

		if (mSavePending) {
			saveListImmediately();
			android.util.Log.d("Darknova", "MESSAGE_SAVE_LIST onDestroyView");
		}

		mIsPagePrepared = false;

		mHandler.removeCallbacksAndMessages(null);

		mQuickFilterOriginalString = null;
		mList = null;
		mQuickFilterUsers = null;
		mQuickFilterUsersExact = null;
		mQuickFilteredList = null;
		mViewList = null;
		mViewRefresher = null;
		mViewProgress = null;
		mViewEmptyIndicator = null;
		mViewUnderActionbar = null;
		mViewUnreadTweetCount = null;
		mListLayout = null;
		mProgressAnimator = null;
		mUpdateRefresher = null;
		mListApplier = null;
		mBackgroundLoader = null;

		if (null != mViewRoot.getParent())
			((ViewGroup) mViewRoot.getParent()).removeView(mViewRoot);
		releasePageView(R.layout.fragment_timeline, mViewRoot);
		mViewRoot = null;

		mPage.setFragment(null);
		super.onDestroyView();
	}

	@Override
	public void onPageEnter() {
		super.onPageEnter();
		mIsActive = true;
		registerTimeUpdate();
		applyTweetChanges();
	}

	@Override
	public void onPageLeave() {
		super.onPageLeave();
		mIsActive = false;
		setQuickFilter(null);
	}

	@Override
	public void onPause() {
		calculateListPositions();
		SharedPreferences.Editor edit = mPagePositionPreferences.edit();
		edit.putInt("lastoffset", mPage.mPageLastOffset);
		edit.putLong("seen_id", mPage.mPageNewestSeenItemId);
		edit.putBoolean("isattop", mPage.mIsListAtTop);
		edit.putLong("id", mPage.mPageLastItemId);
		edit.apply();
		mHandler.removeCallbacksAndMessages(null);
		Thread listApplier = mListApplier;
		if (listApplier != null) {
			synchronized (mPage.mListEditLock) {
				listApplier.interrupt();
			}
		}
		super.onPause();
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();
		registerTimeUpdate();
		applyTweetChanges();
		restorePositions();
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mHandler.removeCallbacksAndMessages(null);
		if (mListApplier != null) {
			synchronized (mPage.mListEditLock) {
				mListApplier.interrupt();
			}
		}
	}

	private int getRemainingUpdateTime(long time) {
		int d = (int) ((System.currentTimeMillis() - time) / 1000);
		if (d < 0) d = -d;
		if (d < 60) return 1;
		if (d < 3600) return (d + 59) / 60 * 60 - d;
		return (d + 3599) / 3600 * 3600 - d;
	}

	private void registerTimeUpdate() {
		if (isPageReady() && mIsActive && !mList.isEmpty()) {
			int delay = 9999;
			if (mListLayout.findFirstVisibleItemPosition() != -1) {
				for (int i = mListLayout.findFirstVisibleItemPosition(), i_ = mListLayout.findLastVisibleItemPosition(); i <= i_; i++) {
					final Object item = mAdapter.getItem(i);
					final Tweet t;
					if (item == null)
						break;
					else if (item instanceof Tweet)
						t = (Tweet) item;
					else if (item instanceof FilteredTweet)
						t = ((FilteredTweet) item).mObject;
					else
						continue;
					delay = Math.min(getRemainingUpdateTime(t.created_at), delay);
					if (t.retweeted_status != null)
						delay = Math.min(getRemainingUpdateTime(t.retweeted_status.created_at), delay);
					final TweetAdapter.CustomViewHolder viewHolder = mAdapter.getViewHolder(i);
					if (viewHolder == null)
						continue;
					viewHolder.updateView();
				}
				if (delay < 1) delay = 1;
			} else
				delay = 1;
			mHandler.removeMessages(MESSAGE_TIME_UPDATE);
			mHandler.sendEmptyMessageDelayed(MESSAGE_TIME_UPDATE, 1000 * delay);
		}
	}

	@Override
	protected Tweet createDummyObject(long id) {
		return Tweet.getTweet(id);
	}

	private void saveListImmediately() {
		mHandler.removeMessages(MESSAGE_SAVE_LIST);
		if (!mIsPagePrepared || mList == null || mList.isEmpty())
			return;
		TwitterEngine.applyAccountInformationChanges();
		final ArrayList<Tweet> res = new ArrayList<>(mList.size() > SAVE_LENGTH ? mList.subList(0, SAVE_LENGTH) : mList);
		final HashMap<Tweet, HashMap<PageElement, Byte>> map = new HashMap<>();
		synchronized (mLoadMoreItems) {
			for (Tweet t : mLoadMoreItems.keySet()) {
				map.put(t, new HashMap<>(mLoadMoreItems.get(t)));
			}
		}
		mSavePending = false;
		new Thread() {
			@Override
			public void run() {
				Parcel p = Parcel.obtain();
				FileOutputStream out = null;
				try {
					out = new FileOutputStream(mListCacheFile);
					p.writeList(res);
					p.writeInt(map.size());
					for (Tweet t : map.keySet()) {
						p.writeParcelable(t, 0);
						p.writeInt(map.get(t).size());
						for (PageElement e : map.get(t).keySet()) {
							p.writeParcelable(e, 0);
							p.writeByte(map.get(t).get(e));
						}
					}
					out.write(p.marshall());
					out.flush();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					StreamTools.close(out);
					p.recycle();
				}
			}
		}.start();
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MESSAGE_TIME_UPDATE: {
				registerTimeUpdate();
				return true;
			}
			case MESSAGE_SAVE_LIST: {
				saveListImmediately();
				return true;
			}
		}
		return false;
	}

	public void onNewTweetReceived(Tweet tweet) {
		applyTweetChanges();
	}

	private void applyTweetChanges() {
		if (mPage == null || mPage.mListPending.isEmpty() || mViewRoot == null || mListApplier != null || mList == null || !mIsActive)
			return;
		if (Looper.getMainLooper().equals(Looper.myLooper())) {
			new Thread() {
				public void run() {
					applyTweetChanges();
				}
			}.start();
			return;
		}
		final Object mUpdaterLock = new Object();
		synchronized (mPage.mListEditLock) {
			synchronized (mUpdaterLock) {
				final List<Tweet> previousList = mList;
				final List<Tweet> newList = new ArrayList<>(previousList);
				final List<Tweet> mTweetsToAdd;
				synchronized (mPage.mListPending) {
					mTweetsToAdd = Collections.unmodifiableList(new ArrayList<>(mPage.mListPending));
					mPage.mListPending.clear();
				}
				mListApplier = Thread.currentThread();
				for (int i = mTweetsToAdd.size() - 1; i >= 0 && !Thread.interrupted(); i--) {
					Tweet tweet = mTweetsToAdd.get(i); // newest tweets first
					int index = Collections.binarySearch(newList, tweet, Collections.reverseOrder());
					if (index < 0) {
						index = -index - 1;
						newList.add(index, tweet);
					}
				}
				if (mPage.mIsListAtTop && newList.size() > MAX_MEMORY_HOLD)
					newList.subList(MAX_MEMORY_HOLD, newList.size()).clear();
				final List<Tweet> applyingList = Collections.unmodifiableList(newList);
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						synchronized (mUpdaterLock) {
							if (isPageReady()) {
								calculateListPositions();
								mPage.mList = new WeakReference<>(mList = applyingList);
								if (mQuickFilteredList == null) {
									mAdapter.notifyDataSetChanged();
									restorePositions();
									registerTimeUpdate();
									applyEmptyIndicator();
									mHandler.post(new Runnable() {
										@Override
										public void run() {
											processVisibleItems((MainActivity) getActivity(), true);
										}
									});
								}
								if (mQuickFilterPattern != null)
									applyFilter(previousList, mQuickFilterString);
							} else
								mPage.mList = new WeakReference<>(mList = applyingList);
							mUpdaterLock.notify();
						}
					}
				});
				boolean succeed = false;
				try {
					mUpdaterLock.wait(8000);
					if (mList != applyingList) // Timeout
						return;
					succeed = true;
				} catch (InterruptedException ie) {
					return;
				} finally {
					mListApplier = null;
					if (!succeed) {
						synchronized (mPage.mListPending) {
							mPage.mListPending.addAll(mTweetsToAdd);
							Collections.sort(mPage.mListPending);
						}
					}
				}
			}
		}
		mSavePending = true;
		mHandler.sendEmptyMessageDelayed(MESSAGE_SAVE_LIST, SAVE_DELAY);
	}

	@Override
	public void setQuickFilter(String input) {
		mQuickFilterOriginalString = input;
		if (input != null && input.trim().isEmpty() && !mSelectedList.isEmpty())
			input = StringTools.MAYBE_NEVER_MATCH;
		if (input == null || input.trim().isEmpty()) {
			mQuickFilterString = null;
			if (mQuickFilterPattern == null)
				return;
			calculateListPositions();

			mQuickFilteredList = null;
			mQuickFilterUsers = null;
			mQuickFilterPattern = null;
			mQuickFilterUsersExact = null;
			mAdapter.notifyDataSetChanged();

			restorePositions();
			applyEmptyIndicator();
			return;
		}
		mQuickFilterString = input;
		if (mQuickFilteredList == null) {
			mQuickFilteredList = new ArrayList<>();
			for (Tweet t : mList)
				mQuickFilteredList.add(new FilteredTweet(t));
		}
		applyFilter(null, input, input);
	}

	@Override
	public void createAdapter() {
		if (mAdapter == null)
			mAdapter = new TweetAdapter();
		super.createAdapter();
	}

	@Override
	public void onRefresh() {
		loadNewerThan();
	}

	private void loadNewerThan() {
		if (mUpdateRefresher != null)
			return;
		mUpdateRefresher = new PageRefresher(null);
		mUpdateRefresher.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private void loadOlderThan(final Tweet tweet) {
		boolean noexecute = true;
		synchronized (mLoadMoreItems) {
			if (mLoadMoreItems.get(tweet) == null) return;
			for (Byte b : mLoadMoreItems.get(tweet).values())
				if (b == LOAD_MORE_ITEM_AVAILABLE)
					noexecute = false;
		}
		if (!noexecute)
			new PageRefresher(tweet).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public boolean isSomethingSelected() {
		return !mSelectedList.isEmpty();
	}

	@Override
	public void clearSelection() {
		if (!mSelectedList.isEmpty()) {
			if (mQuickFilteredList != null) {
				for (Tweet selTweet : mSelectedList) {
					int i = Collections.binarySearch(mQuickFilteredList, new FilteredTweet(selTweet), Collections.reverseOrder());
					if (mQuickFilteredList.get(i).isFilteredBySelection) {
						mQuickFilteredList.remove(i);
						mAdapter.notifyNonHeaderItemRemoved(i);
					}
				}
				mSelectedList.clear();
			} else {
				mSelectedList.clear();
				mAdapter.notifyDataSetChanged();
			}
			applyEmptyIndicator();
			selectionChanged();
		}
	}

	@Override
	protected void selectionChanged() {
		super.selectionChanged();
		if (mQuickFilterOriginalString != null && (mSelectedList.isEmpty() || mQuickFilterString == null))
			setQuickFilter(mQuickFilterOriginalString);
	}

	@Override
	public void onClick(View v) {
		if (v.equals(mViewUnreadTweetCount)) {
			int unread = Collections.binarySearch(mList, Tweet.getTweet(mPage.mPageNewestSeenItemId), Collections.reverseOrder());
			if (unread < 0) unread = -1 - unread;
			unread = Math.min(unread, mList.size());
			if (unread > 0)
				if (mListLayout.isSmoothScrolling() || mListLayout.findFirstVisibleItemPosition() == unread)
					mListLayout.scrollToPositionWithOffset(unread, 0);
				else
					mListLayout.smoothScrollToPosition(mViewList, null, unread);
		}
	}

	private static class OnCreateBackground extends PageFragment.OnCreateBackground<Tweet, TimelineFragment> {

		public OnCreateBackground(TimelineFragment mFragment) {
			super(mFragment);
		}

		@Override
		protected Object doInBackground(Object... params) {
			super.doInBackground(params);
			if (isNewlyLoadedPage) {
				loadListCache();
			}
			try {
				TwitterStreamServiceReceiver.waitForService();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Object o) {
			if (mFragment.mBackgroundLoader != this)
				return;
			if (isCancelled()) {
				mFragment.mBackgroundLoader = null;
				return;
			}
			super.onPostExecute(o);

			mFragment.applyTweetChanges();
			mFragment.applyEmptyIndicator();
			mFragment.registerTimeUpdate();
		}

		private void loadListCache() {
			InputStream in = null;
			if (!mListCacheFile.exists())
				return;
			Parcel p = Parcel.obtain();
			try {
				in = new FileInputStream(mListCacheFile);
				byte[] b = new byte[(int) mListCacheFile.length()];
				if (b.length != in.read(b, 0, b.length))
					return;
				p.unmarshall(b, 0, b.length);
				p.setDataPosition(0);
				if (p.dataSize() > 0) {
					p.readList(mList, Tweet.class.getClassLoader());
					for (int i = 0, size = p.readInt(); i < size; i++) {
						HashMap<PageElement, Byte> map = new HashMap<>();
						Tweet key = p.readParcelable(Tweet.class.getClassLoader());
						for (int j = 0, sizej = p.readInt(); j < sizej; j++) {
							PageElement e = p.readParcelable(PageElement.class.getClassLoader());
							map.put(e, p.readByte());
						}
						mLoadMoreItems.put(key, map);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				StreamTools.close(in);
				p.recycle();
			}
		}
	}

	private class FilterApplier extends AsyncTask<String, Object, String> {
		private ArrayList<Pattern> mQuickFilterUsers;
		private ArrayList<String> mQuickFilterUsersExact;
		private List<Tweet> mOriginalList;
		private Pattern mQuickFilterPattern;
		private String mInput;
		private ArrayList<FilteredItem> mPrevQuickFilterList = new ArrayList<>();
		private ArrayList<Tweet> newTweets;

		FilterApplier(List<Tweet> mList, List<Tweet> mNewList) {
			mOriginalList = mList;
			mPrevQuickFilterList = new ArrayList<>(mQuickFilteredList);
			newTweets = new ArrayList<>(mNewList);
			newTweets.addAll(mSelectedList);
		}

		private FilteredTweet checkAndAdd(Tweet t) {
			boolean userIdExists = false, userNameExists = false, textExists;
			boolean userExactId = false, userExactName = false, emptyUser;
			Matcher matchedUserId = null, matchedUserName = null, matchedText;
			emptyUser = (mQuickFilterUsersExact.isEmpty() && mQuickFilterUsers.isEmpty());
			if (!emptyUser) {
				for (String user : mQuickFilterUsersExact) {
					if (t.retweeted_status == null) {
						userExactId |= user.equals(t.user.screen_name);
						userExactName |= user.equals(t.user.name);
					} else {
						userExactId |= user.equals(t.retweeted_status.user.screen_name);
						userExactName |= user.equals(t.retweeted_status.user.name);
					}
				}
				userIdExists = userExactId;
				userNameExists = userExactName;
				if (!userIdExists)
					for (Pattern user : mQuickFilterUsers) {
						if (t.retweeted_status == null)
							matchedUserId = user.matcher(t.user.screen_name);
						else
							matchedUserId = user.matcher(t.retweeted_status.user.screen_name);
						if (matchedUserId.find())
							break;
						else
							matchedUserId = null;
					}
				userIdExists |= matchedUserId != null;
				if (!userNameExists && (t.user.name != null && (t.retweeted_status == null || t.retweeted_status.user.name != null)))
					for (Pattern user : mQuickFilterUsers) {
						if (t.retweeted_status == null)
							matchedUserName = user.matcher(StringTools.unHanja(t.user.name));
						else
							matchedUserName = user.matcher(StringTools.unHanja(t.retweeted_status.user.name));
						if (matchedUserName.find())
							break;
						else
							matchedUserName = null;
					}
				userNameExists |= matchedUserName != null;
			}
			String theText = TweetSpanner.includeUrlEntity(t.retweeted_status != null ? t.retweeted_status.text : t.text, t.retweeted_status != null ? t.retweeted_status.entities : t.entities);
			if (!(matchedText = mQuickFilterPattern.matcher(StringTools.unHanja(theText))).find())
				matchedText = null;
			textExists = matchedText != null;
			if ((userIdExists || userNameExists || emptyUser) && textExists) {
				FilteredTweet filtered = new FilteredTweet(t);
				filtered.spannedId = SpannableStringBuilder.valueOf(t.retweeted_status != null ? t.retweeted_status.user.screen_name : t.user.screen_name);
				if (userIdExists)
					filtered.setSpanned(filtered.spannedId, matchedUserId);
				filtered.spannedName = SpannableStringBuilder.valueOf(t.retweeted_status != null ? t.retweeted_status.user.name : t.user.name);
				if (userNameExists)
					filtered.setSpanned(filtered.spannedName, matchedUserName);
				filtered.spannedText = TweetSpanner.make(t.retweeted_status == null ? t : t.retweeted_status, mImageCache, -1, null);
				filtered.setSpanned(filtered.spannedText, matchedText);
				return filtered;
			}
			return null;
		}

		@Override
		protected String doInBackground(String... params) {
			mInput = params[0];
			if (params.length > 1) {
				StringBuilder filterText = new StringBuilder();
				mQuickFilterUsersExact = new ArrayList<>();
				mQuickFilterUsers = new ArrayList<>();
				for (String part : mInput.split("\\s+")) {
					if (part.length() > 5 && part.toLowerCase().startsWith("from:"))
						mQuickFilterUsersExact.add(part.substring(5).toLowerCase());
					else if (part.length() > 5 && part.toLowerCase().startsWith("user:"))
						mQuickFilterUsers.add(StringTools.getMaybePatternSearcher(part.substring(5)));
					else if (part.length() > 2 && part.toLowerCase().startsWith("f:"))
						mQuickFilterUsersExact.add(part.substring(2).toLowerCase());
					else if (part.length() > 2 && part.toLowerCase().startsWith("u:"))
						mQuickFilterUsers.add(StringTools.getMaybePatternSearcher(part.substring(2)));
					else if (part.length() > 0)
						filterText.append(" ").append(part);
				}
				mQuickFilterPattern = StringTools.getMaybePatternSearcher(filterText.toString(), false);
			} else {
				mQuickFilterUsersExact = TimelineFragment.this.mQuickFilterUsersExact;
				mQuickFilterUsers = TimelineFragment.this.mQuickFilterUsers;
				mQuickFilterPattern = TimelineFragment.this.mQuickFilterPattern;
			}

			if (params.length == 1)
				newTweets.removeAll(mOriginalList);
			else
				for (FilteredItem ft : mPrevQuickFilterList)
					if (!newTweets.contains(ft.mObject))
						newTweets.add(ft.mObject);

			if (newTweets.size() > 5) {
				FilteredTweet hollow = new FilteredTweet(null);
				for (Tweet t : newTweets) {
					FilteredTweet ft = checkAndAdd(t);
					if (ft == null && mSelectedList.contains(t)) {
						ft = new FilteredTweet(t);
						ft.isFilteredBySelection = true;
					}
					if (ft != null) {
						int index = Collections.binarySearch(mPrevQuickFilterList, ft, Collections.reverseOrder());
						if (index < 0) {
							index = -index - 1;
							mPrevQuickFilterList.add(index, ft);
						} else {
							mPrevQuickFilterList.set(index, ft);
						}
					} else {
						hollow.mObject = t;
						mPrevQuickFilterList.remove(hollow);
					}
					if (!mInput.equals(mQuickFilterString))
						return mInput;
				}
			} else {
				mPrevQuickFilterList = null;
				for (Tweet t : newTweets) {
					FilteredTweet ft = checkAndAdd(t);
					publishProgress(ft, t);
					if (!mInput.equals(mQuickFilterString))
						return mInput;
				}
			}

			return mInput;
		}

		@Override
		protected void onPostExecute(String s) {
			if (!mInput.equals(mQuickFilterString))
				return;
			if (mPrevQuickFilterList != null) {
				calculateListPositions();

				TimelineFragment.this.mQuickFilterPattern = mQuickFilterPattern;
				TimelineFragment.this.mQuickFilterUsersExact = mQuickFilterUsersExact;
				TimelineFragment.this.mQuickFilterUsers = mQuickFilterUsers;
				mQuickFilteredList = mPrevQuickFilterList;
				mAdapter.notifyDataSetChanged();

				restorePositions();
			}
			applyEmptyIndicator();
			processVisibleItems((MainActivity) getActivity(), true);
		}

		@Override
		protected void onProgressUpdate(Object... values) {
			if (!mInput.equals(mQuickFilterString))
				return;

			TimelineFragment.this.mQuickFilterPattern = mQuickFilterPattern;
			TimelineFragment.this.mQuickFilterUsersExact = mQuickFilterUsersExact;
			TimelineFragment.this.mQuickFilterUsers = mQuickFilterUsers;


			if (TimelineFragment.this.mQuickFilteredList == null)
				TimelineFragment.this.mQuickFilteredList = new ArrayList<>();
			FilteredTweet ft = (FilteredTweet) values[0];
			Tweet t = (Tweet) values[1];

			if (ft != null) {
				int index = Collections.binarySearch(TimelineFragment.this.mQuickFilteredList, ft, Collections.reverseOrder());
				if (index < 0) {
					index = -index - 1;
					TimelineFragment.this.mQuickFilteredList.add(index, ft);
					mAdapter.notifyNonHeaderItemInserted(index);
				} else {
					TimelineFragment.this.mQuickFilteredList.set(index, ft);
					mAdapter.notifyNonHeaderItemChanged(index);
				}
			} else {
				int pos = TimelineFragment.this.mQuickFilteredList.indexOf(new FilteredTweet(t));
				if (pos != -1) {
					TimelineFragment.this.mQuickFilteredList.remove(pos);
					mAdapter.notifyNonHeaderItemRemoved(pos);
				}
			}

			if (mPage.mIsListAtTop)
				scrollToTop();
			applyEmptyIndicator();
		}
	}

	private class PageRefresher extends AsyncTask<Object, Object, Object> implements TwitterEngine.TweetCallback {
		private final HashMap<PageElement, PageElementRefresher> mElements = new HashMap<>();
		private final Tweet mInitator;
		private final int mLoadCount = 200;
		private volatile int mProgress, mMaxProgress;
		private int mLastProgress;
		private long mLastPublishTime;

		public PageRefresher(Tweet initator) {
			super();
			mInitator = initator;
			if (initator == null) { // refresh button
				for (PageElement e : mPage.elements) {
					mMaxProgress += 200;
					mElements.put(e, new PageElementRefresher(e, 0, 0));
				}
			} else {
				synchronized (mLoadMoreItems) {
					for (PageElement e : mLoadMoreItems.get(initator).keySet()) {
						if (mLoadMoreItems.get(initator).put(e, LOAD_MORE_ITEM_LOADING) == LOAD_MORE_ITEM_AVAILABLE) {
							mElements.put(e, new PageElementRefresher(e, 0, initator.id));
							mMaxProgress += 200;
						}
					}
				}
			}
		}

		@Override
		protected Object doInBackground(Object... params) {
			try {
				for (PageElementRefresher refresher : mElements.values())
					addRefresher(refresher);
				for (PageElementRefresher refresher : mElements.values())
					refresher.finished.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
				for (PageElementRefresher refresher : mElements.values())
					removeRefresher(refresher);
			}
			applyTweetChanges();
			return null;
		}

		@Override
		protected void onPreExecute() {
			if (mInitator == null && mViewProgress != null) {
				mViewProgress.setVisibility(View.VISIBLE);
				mViewProgress.setMax(mMaxProgress * 10);
				mViewProgress.setProgress(0);
				if (mProgressAnimator != null) {
					mProgressAnimator.cancel();
					mProgressAnimator = null;
				}
				mViewProgress.clearAnimation();
				mViewRefresher.setRefreshing(true);
				mViewProgress.setIndeterminate(true);
			}
		}

		@Override
		protected void onProgressUpdate(Object... values) {
			mLastProgress = mProgress;
			if (mInitator == null && mViewProgress != null) {
				mViewProgress.setIndeterminate(false);
				mProgressAnimator = ObjectAnimator.ofInt(mViewProgress, "progress", mLastProgress * 10);
				mProgressAnimator.setDuration(300);
				mProgressAnimator.setInterpolator(mProgressInterpolator);
				mProgressAnimator.start();
			}
		}

		@Override
		protected void onPostExecute(Object o) {
			if (mInitator == null) {
				if (mViewProgress != null) {
					mProgressAnimator = ObjectAnimator.ofInt(mViewProgress, "progress", mMaxProgress * 10);
					mProgressAnimator.setDuration(300);
					mProgressAnimator.setInterpolator(mProgressInterpolator);
					mProgressAnimator.addListener(new Animator.AnimatorListener() {
						@Override
						public void onAnimationStart(Animator animation) {
						}

						@Override
						public void onAnimationEnd(Animator animation) {
							AlphaAnimation aa = new AlphaAnimation(1, 0);
							aa.setDuration(300);
							aa.setFillAfter(true);
							mViewProgress.setVisibility(View.GONE);
							mViewProgress.startAnimation(aa);
						}

						@Override
						public void onAnimationCancel(Animator animation) {
						}

						@Override
						public void onAnimationRepeat(Animator animation) {
						}
					});
					mProgressAnimator.start();
					mViewRefresher.setRefreshing(false);
				}
			} else if (mLoadMoreItems.containsKey(mInitator) && mLoadMoreItems.get(mInitator).isEmpty())
				synchronized (mLoadMoreItems) {
					mLoadMoreItems.remove(mInitator);
				}
			if (mUpdateRefresher == this)
				mUpdateRefresher = null;
		}

		@Override
		public void onNewTweetReceived(Tweet tweet) {
			mProgress++;
			mPage.addNewTweet(tweet, false);
			if (mProgress > 10 && System.currentTimeMillis() - mLastPublishTime >= 750) {
				applyTweetChanges();
				publishProgress();
				mLastPublishTime = System.currentTimeMillis();
			}
		}

		private class PageElementRefresher implements Runnable {
			private final PageElement mElement;
			private final long since_id, max_id;
			public Exception exception;
			public Semaphore finished = new Semaphore(1);

			public PageElementRefresher(PageElement element, long since_id, long max_id) {
				super();
				mElement = element;
				this.since_id = since_id;
				this.max_id = max_id;
				finished.drainPermits();
			}

			@Override
			public void run() {
				ArrayList<Tweet> res = null;
				try {
					switch (mElement.function) {
						case PageElement.FUNCTION_HOME_TIMELINE:
							res = mElement.getTwitterEngine().getTweets("statuses/home_timeline", mLoadCount, since_id, max_id, false, true, PageRefresher.this);
							break;
						case PageElement.FUNCTION_MENTIONS:
							res = mElement.getTwitterEngine().getTweets("statuses/mentions_timeline", mLoadCount, since_id, max_id, false, true, PageRefresher.this);
							break;
						case PageElement.FUNCTION_SEARCH:
							res = mElement.getTwitterEngine().getSearchTweets(mElement.name, mLoadCount, since_id, max_id, false, true, PageRefresher.this);
							break;
						case PageElement.FUNCTION_USER_TIMELINE:
							res = mElement.getTwitterEngine().getUserHome(mElement.id, mLoadCount, since_id, max_id, false, true, PageRefresher.this);
							break;
					}
					mProgress += mLoadCount - (res == null ? 0 : res.size());
					publishProgress();
					if (res == null || res.isEmpty())
						return;
					Tweet last = res.get(res.size() - 1);
					synchronized (mLoadMoreItems) {
						HashMap<PageElement, Byte> updaters = mLoadMoreItems.get(last);
						if (updaters == null)
							mLoadMoreItems.put(last, updaters = new HashMap<>());
						updaters.put(mElement, LOAD_MORE_ITEM_AVAILABLE);
					}
				} catch (TwitterEngine.RequestException e) {
					e.printStackTrace();
					exception = e;
				} finally {
					finished.release();
				}
			}
		}
	}

	public class TweetAdapter extends PageItemAdapter {

		@Override
		public CustomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			switch (viewType) {
				case R.layout.row_tweet:
					return new TweetViewHolder(inflater.inflate(viewType, parent, false));
			}
			return super.onCreateViewHolder(parent, viewType);
		}

		class TweetViewHolder extends CustomViewHolder implements DragInitiatorCallbacks, View.OnLongClickListener {

			private final ImageView imgUserPictureFull, imgUserPictureUp, imgUserPictureDown;
			private final TextView lblUserName, lblData, lblDescription,
					lblRetweetDescription;
			private final ImageView imgInfoFavorited, imgInfoProtected, imgInfoRetweeted, imgInfoReplied,
					imgInfoVerified, imgSelectedIndicator;
			private final LinearLayout divPreviews;
			private final ImageView[] arrPreviews;
			private final DragInitiatorButton dragInitiatorButton;
			private final View dragActionContainer;
			private final Button tweetActionReply, tweetActionRetweet, tweetActionFavorite;

			private Tweet mLastBoundTweet;

			public TweetViewHolder(View v) {
				super(v);
				lblDescription = (TextView) v.findViewById(R.id.lblDescription);
				lblRetweetDescription = (TextView) v.findViewById(R.id.lblRetweetDescription);
				lblData = (TextView) v.findViewById(R.id.lblData);
				lblData.setMovementMethod(LinkMovementMethod.getInstance());
				lblUserName = (TextView) v.findViewById(R.id.lblUserName);
				lblUserName.setOnClickListener(this);
				imgUserPictureFull = (ImageView) v.findViewById(R.id.imgUserPictureFull);
				imgUserPictureUp = (ImageView) v.findViewById(R.id.imgUserPictureUp);
				imgUserPictureDown = (ImageView) v.findViewById(R.id.imgUserPictureDown);
				imgSelectedIndicator = (ImageView) v.findViewById(R.id.imgSelectedIndicator);
				imgInfoFavorited = (ImageView) v.findViewById(R.id.imgInfoFavorited);
				imgInfoProtected = (ImageView) v.findViewById(R.id.imgInfoProtected);
				imgInfoRetweeted = (ImageView) v.findViewById(R.id.imgInfoRetweeted);
				imgInfoReplied = (ImageView) v.findViewById(R.id.imgInfoReplied);
				imgInfoVerified = (ImageView) v.findViewById(R.id.imgInfoVerified);
				dragInitiatorButton = (DragInitiatorButton) v.findViewById(R.id.dragActionHandle);
				dragActionContainer = v.findViewById(R.id.drag_action_type_tweet);
				tweetActionReply = (Button) dragActionContainer.findViewById(R.id.tweetActionReply);
				tweetActionRetweet = (Button) dragActionContainer.findViewById(R.id.tweetActionRetweet);
				tweetActionFavorite = (Button) dragActionContainer.findViewById(R.id.tweetActionFavorite);
				divPreviews = (LinearLayout) v.findViewById(R.id.divPreviews);
				arrPreviews = new ImageView[divPreviews.getChildCount()];
				for (int i = 0; i < arrPreviews.length; i++) {
					arrPreviews[i] = (ImageView) divPreviews.getChildAt(i);
					arrPreviews[i].setOnClickListener(this);
				}

				dragInitiatorButton.setDragInitiatorCallbacks(this);
				dragInitiatorButton.setOnClickListener(this);
				tweetActionReply.setOnClickListener(this);
				tweetActionRetweet.setOnClickListener(this);
				tweetActionFavorite.setOnClickListener(this);
				tweetActionReply.setOnLongClickListener(this);
				tweetActionRetweet.setOnLongClickListener(this);
				tweetActionFavorite.setOnLongClickListener(this);
			}

			private void previewEntity(Entities entities) {
				int i = 0;
				for (Entities.Entity entity : entities.list) {
					if (entity instanceof Entities.MediaEntity) {
						arrPreviews[i].setVisibility(View.VISIBLE);
						if (mImageCache != null)
							mImageCache.assignImageView(arrPreviews[i++], ((Entities.MediaEntity) entity).media_url, null);
					}
				}
				divPreviews.setVisibility(i == 0 ? View.GONE : View.VISIBLE);
				((FrameLayout.LayoutParams) dragActionContainer.getLayoutParams()).bottomMargin =
						((FrameLayout.LayoutParams) dragInitiatorButton.getLayoutParams()).bottomMargin =
								i == 0 ? 0 : divPreviews.getLayoutParams().height
										+ ((LinearLayout.LayoutParams) divPreviews.getLayoutParams()).topMargin
										+ ((FrameLayout.LayoutParams) ((View) (divPreviews.getParent())).getLayoutParams()).bottomMargin;

				for (; i < arrPreviews.length; i++)
					arrPreviews[i].setVisibility(View.GONE);
			}

			private String getDescriptionString(Tweet tweet) {
				String sTime;
				sTime = StringTools.unixtimeToDisplayTime(tweet.created_at);
				String rtByFormat = null;
				if (tweet.retweeted_status == null && tweet.retweet_count > 0)
					rtByFormat = getString(tweet.retweet_count == 1 ? R.string.tweet_description_retweet_user_count : R.string.tweet_description_retweet_user_counts).replace("${count}", Long.toString(tweet.retweet_count));
				else if (tweet.retweeted_status != null)
					rtByFormat = getString(R.string.tweet_description_retweet_user).replace("${user}", tweet.user.screen_name);
				if (rtByFormat != null)
					return getString(R.string.tweet_description_retweet).replace("${time}", sTime).replace("${via}", tweet.source).replace("${retweet}", rtByFormat);
				else if (tweet.in_reply_to_status != null)
					return getString(R.string.tweet_description_reply).replace("${time}", sTime).replace("${via}", tweet.source).replace("${reply}", tweet.in_reply_to_user.screen_name);
				else
					return getString(R.string.tweet_description).replace("${time}", sTime).replace("${via}", tweet.source);
			}

			public void updateSelectionStatus(Tweet tweet) {
				itemView.setSelected(mSelectedList.contains(tweet));
				imgSelectedIndicator.setVisibility(itemView.isSelected() ? View.VISIBLE : View.GONE);
			}

			@Override
			public void updateView() {
				int position = adapterPositionToListIndex(getLayoutPosition());
				if (position < 0 || position >= (mQuickFilteredList != null ? mQuickFilteredList.size() : mList.size()))
					return;
				FilteredTweet filtered = mQuickFilteredList != null ? (FilteredTweet) mQuickFilteredList.get(position) : null;
				Tweet tweet = filtered != null ? filtered.mObject : mList.get(position);
				if (tweet != mLastBoundTweet)
					return;
				if (filtered != null && filtered.spannedText != null) {
					for (UserImageSpan s : filtered.spannedText.getSpans(0, filtered.spannedText.length(), UserImageSpan.class))
						s.setLineHeight(lblData.getLineHeight());
					lblData.setText(filtered.spannedText);
				} else
					lblData.setText(TweetSpanner.make(tweet.retweeted_status == null ? tweet : tweet.retweeted_status, mImageCache, lblData.getLineHeight(), lblData.getText()));
				if (tweet.retweeted_status != null) {
					lblDescription.setText(getDescriptionString(tweet.retweeted_status));
					lblRetweetDescription.setText(getDescriptionString(tweet));
					lblUserName.setText(filtered != null && filtered.spannedId != null ? TextUtils.concat(filtered.spannedId, "/", filtered.spannedName) : tweet.retweeted_status.user.screen_name + "/" + tweet.retweeted_status.user.name);
					// lblData.setText(filtered != null && filtered.spannedText != null ? filtered.spannedText : tweet.retweeted_status.text);
				} else {
					lblDescription.setText(getDescriptionString(tweet));
					lblUserName.setText(filtered != null && filtered.spannedId != null ? TextUtils.concat(filtered.spannedId, "/", filtered.spannedName) : tweet.user.screen_name + "/" + tweet.user.name);
					// lblData.setText(filtered != null && filtered.spannedText != null ? filtered.spannedText : tweet.text);
				}
				updateSelectionStatus(tweet);
			}

			public void bindViewHolder(int position) {
				FilteredTweet filtered = mQuickFilteredList != null ? (FilteredTweet) mQuickFilteredList.get(position) : null;
				Tweet tweet = mQuickFilteredList != null ? filtered.mObject : mList.get(position);
				if (mLastBoundTweet == tweet) { // skip - already prepared
					updateView();
					return;
				}
				mLastBoundTweet = tweet;
				updateView();
				if (tweet.retweeted_status == null) {
					imgUserPictureUp.setVisibility(View.GONE);
					imgUserPictureDown.setVisibility(View.GONE);
					imgUserPictureFull.setVisibility(View.VISIBLE);
					lblRetweetDescription.setVisibility(View.GONE);
					imgInfoReplied.setVisibility(tweet.in_reply_to_status == null ? View.GONE : View.VISIBLE);
					imgInfoFavorited.setVisibility(tweet.favoriteExists() ? View.VISIBLE : View.GONE);
					imgInfoRetweeted.setVisibility(tweet.retweetExists() ? View.VISIBLE : View.GONE);
					imgInfoVerified.setVisibility(tweet.user.verified ? View.VISIBLE : View.GONE);
					imgInfoProtected.setVisibility(tweet.user._protected ? View.VISIBLE : View.GONE);
					mImageCache.assignImageView(imgUserPictureDown, null, null);
					mImageCache.assignImageView(imgUserPictureUp, null, null);
					mImageCache.assignImageView(imgUserPictureFull, tweet.user.getProfileImageUrl(), null);
					previewEntity(tweet.entities);
				} else {
					imgUserPictureUp.setVisibility(View.VISIBLE);
					imgUserPictureDown.setVisibility(View.VISIBLE);
					imgUserPictureFull.setVisibility(View.GONE);
					lblRetweetDescription.setVisibility(View.VISIBLE);
					imgInfoReplied.setVisibility(tweet.retweeted_status.in_reply_to_status == null ? View.GONE : View.VISIBLE);
					imgInfoFavorited.setVisibility(tweet.retweeted_status.favoriteExists() ? View.VISIBLE : View.GONE);
					imgInfoRetweeted.setVisibility(tweet.retweeted_status.retweetExists() ? View.VISIBLE : View.GONE);
					imgInfoVerified.setVisibility(tweet.retweeted_status.user.verified ? View.VISIBLE : View.GONE);
					imgInfoProtected.setVisibility(tweet.retweeted_status.user._protected ? View.VISIBLE : View.GONE);
					mImageCache.assignImageView(imgUserPictureDown, tweet.user.getProfileImageUrl(), null);
					mImageCache.assignImageView(imgUserPictureUp, tweet.retweeted_status.user.getProfileImageUrl(), null);
					mImageCache.assignImageView(imgUserPictureFull, null, null);
					previewEntity(tweet.retweeted_status.entities);
				}
			}

			@Override
			public void onClick(View v) {
				int position = getAdapterPosition();
				final Tweet t = mQuickFilteredList == null ? (Tweet) getItem(position) : ((FilteredTweet) getItem(position)).mObject;
				if (t == null)
					return;
				if (v.equals(tweetActionRetweet)) {
					new AlertDialog.Builder(getActivity())
							.setMessage("Retweet?")
							.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									final ArrayList<TwitterEngine> postFrom = ((MainActivity) getActivity()).getNewTweetFragment().getPostFromList();
									final long id = t.id;
									new Thread() {
										@Override
										public void run() {
											try {
												for (TwitterEngine e : postFrom)
													e.postRetweet(id);
											} catch (Exception e) {
												e.printStackTrace();
											}
										}
									}.start();
								}
							})
							.setNegativeButton(android.R.string.no, null)
							.show();
				} else if (v.equals(tweetActionReply)) {
					((MainActivity) getActivity()).showActionBar();
					((MainActivity) getActivity()).getNewTweetFragment().setInReplyTo(t);
					((MainActivity) getActivity()).getNewTweetFragment().insertText("@" + t.user.screen_name + " ");
					((MainActivity) getActivity()).getNewTweetFragment().showNewTweet();
				} else if (v.equals(dragInitiatorButton)) {
					if (!mSelectedList.contains(t)) {
						mSelectedList.add(t);
						updateSelectionStatus(t);
					} else {
						mSelectedList.remove(t);
						int actualItemPosition = adapterPositionToListIndex(position);
						if (mQuickFilteredList != null && mQuickFilteredList.get(actualItemPosition).isFilteredBySelection) {
							mQuickFilteredList.remove(actualItemPosition);
							notifyNonHeaderItemRemoved(position);
						} else {
							updateSelectionStatus(t);
						}
					}
					selectionChanged();
					applyEmptyIndicator();
				} else if (v.equals(itemView)) {
					if (!mSelectedList.isEmpty()) {
						if (!mSelectedList.contains(t)) {
							mSelectedList.add(t);
							updateSelectionStatus(t);
						} else {
							mSelectedList.remove(t);
							int actualItemPosition = adapterPositionToListIndex(position);
							if (mQuickFilteredList != null && mQuickFilteredList.get(actualItemPosition).isFilteredBySelection) {
								mQuickFilteredList.remove(actualItemPosition);
								notifyNonHeaderItemRemoved(position);
							} else {
								updateSelectionStatus(t);
							}
						}
						selectionChanged();
						applyEmptyIndicator();
					}
				} else if (v.equals(lblUserName)) {
					Tweeter u = t.retweeted_status == null ? t.user : t.retweeted_status.user;
					Page.templatePageUser(u.user_id, u.screen_name, (MainActivity) getActivity());
				} else {
					for (int i = arrPreviews.length - 1; i >= 0; i--)
						if (arrPreviews[i].equals(v)) {
							MediaPreviewActivity.previewTweetImages(getActivity(), t.retweeted_status == null ? t : t.retweeted_status, i);
						}
				}
			}

			@Override
			public boolean onLongClick(View v) {
				int itemPosition = getAdapterPosition();
				final Tweet t = mQuickFilteredList == null ? (Tweet) getItem(itemPosition) : ((FilteredTweet) getItem(itemPosition)).mObject;
				if (v.equals(tweetActionRetweet)) {
					return true;
				} else if (v.equals(tweetActionReply)) {
					return true;
				}
				return false;
			}

			@Override
			public void onDragPrepare(DragInitiator dragInitiator) {
				int itemPosition = getAdapterPosition();
				final Tweet t = mQuickFilteredList == null ? (Tweet) getItem(itemPosition) : ((FilteredTweet) getItem(itemPosition)).mObject;
				dragInitiator.setKeepContainerOnClick(!mSelectedList.contains(t));
			}

			@Override
			public void onDragStart(DragInitiator dragInitiator) {

			}

			@Override
			public void onDragEnd(DragInitiator dragInitiator) {

			}
		}
	}


	private class FilteredTweet extends FilteredItem {
		SpannableStringBuilder spannedId;
		SpannableStringBuilder spannedName;
		SpannableStringBuilder spannedText;

		FilteredTweet(Tweet tweet) {
			super(tweet);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof FilteredTweet && mObject.equals(((FilteredTweet) o).mObject);
		}
	}
}
