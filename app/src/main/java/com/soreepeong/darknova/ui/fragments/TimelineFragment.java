package com.soreepeong.darknova.ui.fragments;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.soreepeong.darknova.Darknova;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.PageElement;
import com.soreepeong.darknova.settings.PageTweet;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.TwitterStreamServiceReceiver;
import com.soreepeong.darknova.ui.MainActivity;
import com.soreepeong.darknova.ui.span.TweetSpanner;
import com.soreepeong.darknova.ui.viewholder.CustomViewHolder;
import com.soreepeong.darknova.ui.viewholder.TweetBigViewHolder;
import com.soreepeong.darknova.ui.viewholder.TweetViewHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

	private ObjectAnimator mProgressAnimator;

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
		mFilterApplier = new FilterApplier(oldList, mPage.getList());
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
			if(selectedList != null)
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
		mSelectedList.clear();
		mIsPagePrepared = false;

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
		mViewList.setRecycledViewPool(mRecyclerViewPool);
		mViewRefresher = (SwipeRefreshLayout) mViewRoot.findViewById(R.id.swipeRefresher);
		mViewProgress = (ProgressBar) mViewRoot.findViewById(R.id.progress_horizontal);
		mViewEmptyIndicator = (ImageView) mViewRoot.findViewById(R.id.empty_image);
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

					if (dy <= 0) // scrolling up?
						processVisibleItems(activity, false);
				}
			}
		});

		TwitterStreamServiceReceiver.addStreamCallback(this);
		ImageCache.getCache(getActivity(), this);
		if (mBackgroundLoader == null)
			mBackgroundLoader = new OnCreateBackground(this);
		else if (mIsPagePrepared)
			mBackgroundLoader.postExecuteNow();

		return mViewRoot;
	}

	private void processVisibleItems(MainActivity activity, boolean forceRenew) {
		if (activity == null) return;
		List<Tweet> list = mPage.getList();
		if(list != null && !list.isEmpty() && mQuickFilteredList == null)
			for (int i = mListLayout.findFirstVisibleItemPosition(); i <= mListLayout.findLastVisibleItemPosition(); i++) {
				if (mAdapter.getItemViewType(i) != R.layout.row_tweet)
					continue;
				int position = mAdapter.adapterPositionToListIndex(i);
				Tweet tweet = mQuickFilteredList == null ? list.get(position) : mQuickFilteredList.get(position).mObject;
				View v = mListLayout.findViewByPosition(i);
				if (v != null && v.getTop() >= 0)
					itemRead(tweet.id, forceRenew);
				if(mPage.mLoadMoreItems.containsKey(tweet))
					loadOlderThan(list.get(position));
			}
	}

	@Override
	protected void itemRead(long id, boolean forceUpdate) {
		List<Tweet> list = mPage.getList();
		if (id != -1) {
			if(list.isEmpty())
				mPage.mPageNewestSeenItemId = -1;
			else if (mPage.mPageNewestSeenItemId < id)
				mPage.mPageNewestSeenItemId = id;
			else if (!forceUpdate)
				return;
		}
		int unread = mPage.mPageNewestSeenItemId == 0 ? -1 : Collections.binarySearch(list, Tweet.getTweet(mPage.mPageNewestSeenItemId), Collections.reverseOrder());
		if (unread < 0) unread = -1 - unread;
		unread = Math.min(unread, list.size());
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
		mViewList.swapAdapter(null, false);
		mViewList.setRecycledViewPool(null);
		mViewRefresher.setOnRefreshListener(null);
		mViewRefresher.setRefreshing(false);
		mViewRefresher.setEnabled(true);
		mViewProgress.setVisibility(View.GONE);

		mIsPagePrepared = false;

		cancelPendingActions();

		mQuickFilterOriginalString = null;
		mQuickFilterUsers = null;
		mQuickFilterUsersExact = null;
		mQuickFilteredList = null;
		mViewList = null;
		mViewRefresher = null;
		mViewProgress = null;
		mViewEmptyIndicator = null;
		mViewUnreadTweetCount = null;
		mListLayout = null;
		mProgressAnimator = null;
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
	}

	@Override
	public void onPageLeave() {
		super.onPageLeave();
		if(mIsPagePrepared){
			calculateListPositions();
			SharedPreferences.Editor edit = mPagePositionPreferences.edit();
			edit.putInt("lastoffset", mPage.mPageLastOffset);
			edit.putLong("seen_id", mPage.mPageNewestSeenItemId);
			edit.putBoolean("isattop", mPage.mIsListAtTop);
			edit.putLong("id", mPage.mPageLastItemId);
			edit.apply();
		}
		mIsActive = false;
		setQuickFilter(null);
	}

	@Override
	public void onPause() {
		calculateListPositions();
		if(mIsPagePrepared){
			SharedPreferences.Editor edit = mPagePositionPreferences.edit();
			edit.putInt("lastoffset", mPage.mPageLastOffset);
			edit.putLong("seen_id", mPage.mPageNewestSeenItemId);
			edit.putBoolean("isattop", mPage.mIsListAtTop);
			edit.putLong("id", mPage.mPageLastItemId);
			edit.apply();
		}
		cancelPendingActions();
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
		restorePositions();
	}

	@Override
	public void onDetach() {
		super.onDetach();
		cancelPendingActions();
	}

	private int getRemainingUpdateTime(long time) {
		int d = (int) ((System.currentTimeMillis() - time) / 1000);
		if (d < 0) d = -d;
		if (d < 60) return 1;
		if (d < 3600) return (d + 59) / 60 * 60 - d;
		return (d + 3599) / 3600 * 3600 - d;
	}

	private void registerTimeUpdate() {
		if(isPageReady() && mIsActive && !mPage.getList().isEmpty()){
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
					final CustomViewHolder<Tweet> viewHolder = mAdapter.getViewHolder(i);
					if (viewHolder == null)
						continue;
					viewHolder.updateView();
				}
				if (delay < 1) delay = 1;
			} else
				delay = 1;
			addPendingAction(MESSAGE_TIME_UPDATE, 1000 * delay);
		}
	}

	@Override
	protected Tweet createDummyObject(long id) {
		return Tweet.getTweet(id);
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MESSAGE_TIME_UPDATE: {
				registerTimeUpdate();
				return true;
			}
		}
		return false;
	}

	public void onNewTweetReceived(Tweet tweet) {
		new Thread(){
			@Override
			public void run(){
				mPage.applyPending();
			}
		}.start();
	}

	@Override
	public void onDataSetUpdated(List<Tweet> listOld){
		if(mQuickFilteredList == null){
			mAdapter.notifyDataSetChanged();
			restorePositions();
			registerTimeUpdate();
			applyEmptyIndicator();
			addPendingAction(new Runnable(){
				@Override
				public void run() {
					processVisibleItems((MainActivity) getActivity(), true);
				}
			});
		}
		if(mQuickFilterPattern != null)
			applyFilter(listOld, mQuickFilterString);
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
			for(Tweet t : mPage.getList())
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

	public void showRefreshProgress(int max){
		if(mViewProgress != null){
			mViewProgress.setVisibility(View.VISIBLE);
			mViewProgress.setMax(max * 10);
			mViewProgress.setProgress(0);
			if(mProgressAnimator != null){
				mProgressAnimator.cancel();
				mProgressAnimator = null;
			}
			mViewProgress.clearAnimation();
			mViewRefresher.setRefreshing(true);
			mViewProgress.setIndeterminate(true);
		}
	}

	public void setRefreshProgress(int progress){
		if(mViewProgress != null){
			mViewProgress.setIndeterminate(false);
			mProgressAnimator = ObjectAnimator.ofInt(mViewProgress, "progress", progress * 10);
			mProgressAnimator.setDuration(300);
			mProgressAnimator.setInterpolator(PageFragment.mProgressInterpolator);
			mProgressAnimator.start();
		}
	}

	public void hideRefreshProgress(){
		if(mViewProgress != null){
			mProgressAnimator = ObjectAnimator.ofInt(mViewProgress, "progress", mViewProgress.getMax());
			mProgressAnimator.setDuration(300);
			mProgressAnimator.setInterpolator(PageFragment.mProgressInterpolator);
			mProgressAnimator.addListener(new Animator.AnimatorListener(){
				@Override
				public void onAnimationStart(Animator animation){
				}

				@Override
				public void onAnimationCancel(Animator animation){
				}

				@Override
				public void onAnimationRepeat(Animator animation){
				}

				@Override
				public void onAnimationEnd(Animator animation){
					if(mViewProgress == null)
						return;
					AlphaAnimation aa = new AlphaAnimation(1, 0);
					aa.setDuration(300);
					aa.setAnimationListener(new Animation.AnimationListener(){
						@Override
						public void onAnimationStart(Animation animation){
						}

						@Override
						public void onAnimationRepeat(Animation animation){
						}

						@Override
						public void onAnimationEnd(Animation animation){
							mViewProgress.clearAnimation();
							mViewProgress.setVisibility(View.GONE);
						}
					});
					mViewProgress.startAnimation(aa);
				}
			});
			mProgressAnimator.start();
			mViewRefresher.setRefreshing(false);
		}
	}

	private void loadNewerThan() {
		((PageTweet) mPage).refresh(null);
	}

	private void loadOlderThan(final Tweet tweet) {
		boolean noexecute = true;
		synchronized(mPage.mLoadMoreItems){
			if(mPage.mLoadMoreItems.get(tweet) == null) return;
			for(Byte b : mPage.mLoadMoreItems.get(tweet).values())
				if (b == LOAD_MORE_ITEM_AVAILABLE)
					noexecute = false;
		}
		if (!noexecute)
			((PageTweet) mPage).refresh(tweet);
	}

	public boolean isSomethingSelected() {
		return !mSelectedList.isEmpty();
	}

	@Override
	public void clearSelection() {
		if(!mSelectedList.isEmpty()){
			if(mQuickFilteredList != null){
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
	public void selectionChanged(){
		super.selectionChanged();
		if (mQuickFilterOriginalString != null && (mSelectedList.isEmpty() || mQuickFilterString == null))
			setQuickFilter(mQuickFilterOriginalString);
	}

	@Override
	public void onClick(View v) {
		List<Tweet> list = mPage.getList();
		if (v.equals(mViewUnreadTweetCount)) {
			int unread = Collections.binarySearch(list, Tweet.getTweet(mPage.mPageNewestSeenItemId), Collections.reverseOrder());
			if (unread < 0) unread = -1 - unread;
			unread = Math.min(unread, list.size());
			if (mListLayout.findFirstVisibleItemPosition() - unread < 8) {
				mListLayout.smoothScrollToPosition(mViewList, null, unread);
			} else {
				mListLayout.scrollToPosition(unread);
				mViewList.smoothScrollBy(0, 0);
			}
		}
	}

	private static class OnCreateBackground extends PageFragment.OnCreateBackground<Tweet, TimelineFragment> {

		public OnCreateBackground(TimelineFragment mFragment) {
			super(mFragment);
		}

		@Override
		protected Object doInBackground(Object... params) {
			super.doInBackground(params);
			if(isNewlyLoadedPage){
				List<Tweet> t = ((PageTweet) mPage).loadListCache();
				if(t != null)
					mList.addAll(t);
			}
			if(mPage.elements.size() == 1 && mPage.elements.get(0).function == PageElement.FUNCTION_TWEET_SINGLE){
				Tweet t = Tweet.getTweet(mPage.elements.get(0).id);
				if(t == null)
					return null;
				if (t.info.stub)
					requireRefresh = true;
				while(true){
					if(t.retweeted_status != null) t = t.retweeted_status;
					int index = Collections.binarySearch(mList, t, Collections.reverseOrder());
					if (index < 0) {
						index = -index - 1;
						mList.add(index, t);
					}
					t = t.in_reply_to_status;
					if(t == null || t.info.stub)
						break;
				}
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
			mFragment.applyEmptyIndicator();
			mFragment.registerTimeUpdate();
			((PageTweet) mPage).applyRefreshStatus();
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
				filtered.spannedText = TweetSpanner.make(t.retweeted_status == null ? t : t.retweeted_status, Darknova.img, -1, null);
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

	public class TweetAdapter extends PageItemAdapter {

		@Override
		public CustomViewHolder<Tweet> onCreateViewHolder(ViewGroup parent, int viewType) {
			final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			switch (viewType) {
				case R.layout.row_tweet:
					return new TweetViewHolder(inflater.inflate(viewType, parent, false));
				case R.layout.row_header_big_tweet:
					return new TweetBigViewHolder(inflater.inflate(viewType, parent, false));
			}
			return super.onCreateViewHolder(parent, viewType);
		}

		@Override
		public int getItemViewType(int position) {
			int type = super.getItemViewType(position);
			if(type == R.layout.row_tweet){
				long itm = mPage.getList().get(adapterPositionToListIndex(position)).id;
				for (PageElement e : mPage.elements)
					if (e.id == itm && e.function == PageElement.FUNCTION_TWEET_SINGLE)
						return R.layout.row_header_big_tweet;
			}
			return type;
		}
	}

	public class FilteredTweet extends FilteredItem{
		public SpannableStringBuilder spannedId;
		public SpannableStringBuilder spannedName;
		public SpannableStringBuilder spannedText;

		FilteredTweet(Tweet tweet) {
			super(tweet);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof FilteredTweet && mObject.equals(((FilteredTweet) o).mObject);
		}
	}
}
