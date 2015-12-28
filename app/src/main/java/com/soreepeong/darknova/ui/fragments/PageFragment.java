package com.soreepeong.darknova.ui.fragments;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.soreepeong.darknova.DarknovaApplication;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.core.ThreadScheduler;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.PageElement;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.tools.TimedStorage;
import com.soreepeong.darknova.twitter.ObjectWithId;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.twitter.TwitterStreamServiceReceiver;
import com.soreepeong.darknova.ui.MainActivity;
import com.soreepeong.darknova.ui.MediaPreviewActivity;
import com.soreepeong.darknova.ui.span.TweetSpanner;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fragment that displays page, and some more
 * ONLY to be used in {@see MainActivity}
 *
 * @author Soreepeong
 */
public abstract class PageFragment<_T extends ObjectWithId> extends Fragment implements Handler.Callback, TwitterEngine.TwitterStreamCallback, SwipeRefreshLayout.OnRefreshListener {

	protected static final Interpolator mProgressInterpolator = new DecelerateInterpolator();
	protected static final RecyclerView.RecycledViewPool mRecyclerViewPool = new RecyclerView.RecycledViewPool();
	private static final SparseArray<TimedStorage<View>> mCachedPageViews = new SparseArray<>();
	private static final ThreadScheduler mRefreshScheduler = new ThreadScheduler(4, "Refresher");
	protected static int mColorAccent;

	static {
		mRecyclerViewPool.setMaxRecycledViews(R.layout.row_tweet, 32);
	}

	protected final ArrayList<Tweet> mSelectedList = new ArrayList<>();
	protected final WeakHashMap<_T, HashMap<PageElement, Byte>> mLoadMoreItems = new WeakHashMap<>();
	protected Handler mHandler;
	protected PageItemAdapter mAdapter;
	protected View mViewRoot;
	protected List<_T> mList;
	protected Page<_T> mPage;
	protected boolean mIsActive;
	protected String mQuickFilterString;
	protected Pattern mQuickFilterPattern;
	protected ArrayList<FilteredItem> mQuickFilteredList;
	protected SharedPreferences mPagePositionPreferences;
	protected String mQuickFilterOriginalString;
	protected ArrayList<Pattern> mQuickFilterUsers;
	protected ArrayList<String> mQuickFilterUsersExact;

	protected RecyclerView mViewList;
	protected LinearLayoutManager mListLayout;
	protected SwipeRefreshLayout mViewRefresher;
	protected ProgressBar mViewProgress;
	protected ImageView mViewEmptyIndicator;
	protected TextView mViewUnreadTweetCount;
	protected ImageCache mImageCache;

	protected boolean mIsPagePrepared;
	protected OnCreateBackground<_T, ? extends PageFragment<_T>> mBackgroundLoader;

	protected File mListCacheFile;

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

	public static PageFragment newInstance(String cacheDir, Page p) {
		return TimelineFragment.newInstance(cacheDir, p);
	}

	public void onCompleteRefresh() {
		if (Looper.getMainLooper() != Looper.myLooper())
			throw new RuntimeException("MUST be called from the UI Thread");
		mPage.mList = new WeakReference<>(mList = Collections.unmodifiableList(new ArrayList<_T>()));
		synchronized (mPage.mListPending) {
			mPage.mListPending.clear();
		}
		mAdapter.notifyDataSetChanged();
		applyEmptyIndicator();
		itemRead(mList.isEmpty() ? -1 : mList.get(0).getId(), true);
		onRefresh();
	}

	public void scrollToTop() {
		mPage.mIsListAtTop = true;
		mPage.mPageLastOffset = 0;
		if (mListLayout.findFirstVisibleItemPosition() < 8) {
			mListLayout.smoothScrollToPosition(mViewList, null, 0);
		} else {
			mListLayout.scrollToPosition(0);
			mViewList.smoothScrollBy(0, 0);
		}
		if (mList != null)
			itemRead(mList.isEmpty() ? -1 : mList.get(0).getId(), true);
	}

	public void onPageEnter(){}
	public void onPageLeave(){}

	public abstract boolean isSomethingSelected();
	public abstract void clearSelection();


	public void loadBackground() {
		if (mBackgroundLoader != null && mBackgroundLoader.getStatus() == AsyncTask.Status.PENDING)
			mBackgroundLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	protected void itemRead(long id, boolean forceUpdate) {
	}

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
		Page<_T> p = args.getParcelable("page");
		mPage = Page.getLoaded(p);
	}

	public Page getRepresentingPage(){
		return mPage;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mHandler = new Handler(this);
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null)
			performArguments(getArguments());
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return super.onCreateView(inflater, container, savedInstanceState);
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

	protected Object calculateListPositions() {
		if (getView() == null || !mIsActive)
			return null;
		if (mViewList.getChildCount() == 0 || mPage.mIsListAtTop) {
			mPage.mIsListAtTop = true;
			mPage.mPageLastOffset = 0;
			return null;
		}
		int first = mListLayout.findFirstVisibleItemPosition();
		View v = mListLayout.findViewByPosition(first);
		mPage.mPageLastOffset = v != null ? v.getTop() - mViewList.getPaddingTop() : 0;
		Object o = mAdapter.getItem(first);
		if (o instanceof ObjectWithId)
			mPage.mPageLastItemId = ((ObjectWithId) o).getId();
		else
			mPage.mPageLastItemId = -1;
		return o;
	}

	protected abstract _T createDummyObject(long id);

	protected void restorePositions() {
		if (!isPageReady())
			return;
		if (mPage.mIsListAtTop) {
			scrollToTop();
			return;
		}
		if (mPage.mPageLastItemId != -1) {
			int lastIndex = mList.indexOf(createDummyObject(mPage.mPageLastItemId));
			if (lastIndex < 0) lastIndex = -1 - lastIndex;
			lastIndex += mAdapter.mElementHeaders.size() + mAdapter.mNonElementHeaders.size();
			mListLayout.scrollToPositionWithOffset(lastIndex, mPage.mPageLastOffset);
		}
	}

	@Override
	public void onStreamStart(TwitterEngine engine) {

	}

	@Override
	public void onStreamConnected(TwitterEngine engine) {
		if (mPage.containsStream(engine))
			onRefresh();
	}

	@Override
	public void onStreamError(TwitterEngine engine, Throwable e) {
	}

	@Override
	public void onStreamTweetEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, Tweet tweet, long created_at) {
	}

	@Override
	public void onStreamUserEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, long created_at) {
	}

	@Override
	public void onStreamStop(TwitterEngine engine) {
	}

	public void createAdapter() {
		mViewList.swapAdapter(mAdapter, false);
	}

	public void applyEmptyIndicator() {
		if (getView() == null)
			return;
		AlphaAnimation aa = null;
		boolean isEmpty = true;
		if (mIsPagePrepared) {
			if (mList.isEmpty()) {
				mAdapter.addNonElementHeader(R.layout.row_header_empty_page);
				mAdapter.removeNonElementHeader(R.layout.row_header_no_match);
			} else if (mQuickFilteredList != null && mQuickFilteredList.isEmpty()) {
				mAdapter.addNonElementHeader(R.layout.row_header_no_match);
				mAdapter.removeNonElementHeader(R.layout.row_header_empty_page);
			} else {
				mAdapter.removeNonElementHeader(R.layout.row_header_empty_page);
				mAdapter.removeNonElementHeader(R.layout.row_header_no_match);
				isEmpty = false;
			}
		}
		if (!isEmpty && mViewEmptyIndicator.getVisibility() == View.VISIBLE) {
			aa = new AlphaAnimation(1, 0);
			aa.setInterpolator(new AccelerateInterpolator());
			mViewEmptyIndicator.setVisibility(View.GONE);
		} else if (isEmpty && (mViewEmptyIndicator.getVisibility() != View.VISIBLE)) {
			aa = new AlphaAnimation(0, 1);
			aa.setInterpolator(new DecelerateInterpolator());
			mViewEmptyIndicator.setVisibility(View.VISIBLE);
		}
		if (aa == null)
			return;
		aa.setDuration(300);
		aa.setFillAfter(true);
		mViewEmptyIndicator.startAnimation(aa);
	}

	protected boolean isPageReady() {
		return mIsPagePrepared && mBackgroundLoader == null;
	}

	public static abstract class OnCreateBackground<_T extends ObjectWithId, _FRAGMENT_T extends PageFragment<_T>> extends AsyncTask<Object, Object, Object> {
		protected final Page<_T> mPage;
		protected final _FRAGMENT_T mFragment;
		protected final File mListCacheFile;
		protected final WeakHashMap<_T, HashMap<PageElement, Byte>> mLoadMoreItems = new WeakHashMap<>();
		protected boolean isNewlyLoadedPage = false;
		protected List<_T> mList;
		protected boolean requireRefresh = false;

		public OnCreateBackground(_FRAGMENT_T mFragment) {
			this.mFragment = mFragment;
			mListCacheFile = mFragment.mListCacheFile;
			mPage = mFragment.mPage;
			mList = mPage.mList == null ? null : mPage.mList.get();
		}

		public void postExecuteNow() {
			onPostExecute(null);
		}

		@Override
		protected Object doInBackground(Object... params) {
			if (mList == null) {
				mList = new ArrayList<>();
				isNewlyLoadedPage = true;
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
			mFragment.mIsPagePrepared = true;
			if (mFragment.getView() == null)
				return;
			mPage.mList = new WeakReference<>(mFragment.mList = mList);
			mFragment.mLoadMoreItems.clear();
			mFragment.mLoadMoreItems.putAll(mLoadMoreItems);
			mFragment.mBackgroundLoader = null;
			mFragment.mViewProgress.setIndeterminate(false);
			AlphaAnimation aa = new AlphaAnimation(1, 0);
			aa.setDuration(300);
			aa.setFillAfter(true);
			aa.setInterpolator(new AccelerateDecelerateInterpolator());
			mFragment.mViewProgress.setVisibility(View.GONE);
			mFragment.mViewProgress.startAnimation(aa);
			mFragment.itemRead(-1, true);
			mFragment.createAdapter();
			mPage.setFragment(mFragment);

			mFragment.mAdapter.mElementHeaders.clear();
			for (PageElement e : mPage.elements) {
				if (e.isHeaderView())
					mFragment.mAdapter.mElementHeaders.add(e.createHeader(e.getHeaderType()));
				TwitterEngine engine = e.getTwitterEngine();
				if (TwitterStreamServiceReceiver.isStreamOn(engine) && e.containsStream(engine))
					requireRefresh = true;
			}
			mFragment.restorePositions();
			if (isNewlyLoadedPage && requireRefresh)
				mFragment.onRefresh();
			mFragment.mAdapter.notifyDataSetChanged();
		}
	}

	protected static class NonElementHeader {
		private final int mLayoutId;
		private final long mId;

		public NonElementHeader(int layoutId) {
			mLayoutId = layoutId;
			mId = DarknovaApplication.uniqid();
		}

		public int getLayoutId() {
			return mLayoutId;
		}

		public long getId() {
			return mId;
		}
	}

	abstract static class CustomViewHolder<_T extends ObjectWithId> extends RecyclerView.ViewHolder implements View.OnClickListener {
		protected PageFragment<_T> mFragment;
		protected PageFragment<_T>.PageItemAdapter mAdapter;

		public CustomViewHolder(View v) {
			super(v);
			v.setOnClickListener(this);
			v.setClickable(true);
			v.setFocusable(true);
			v.setTag(R.id.VIEWHOLDER_ID, this);
		}

		public void setFragment(PageFragment<_T> frag) {
			mFragment = frag;
			mAdapter = frag.mAdapter;
		}

		public void updateView() {
		}

		public abstract void bindViewHolder(int position);
	}

	static class NonElementHeaderViewHolder extends CustomViewHolder {
		public NonElementHeaderViewHolder(View itemView) {
			super(itemView);
		}

		@Override
		public void onClick(View v) {
			NonElementHeader header = (NonElementHeader) mAdapter.getItem(getAdapterPosition());
			switch (header.getLayoutId()) {
				case R.layout.row_header_empty_page:
					mFragment.onRefresh();
					return;
				case R.layout.row_header_no_match:
					((MainActivity) mFragment.getActivity()).getSearchSuggestionFragment().setQuickFilter("");
			}
		}

		@Override
		public void bindViewHolder(int position) {

		}
	}

	static class UserHeaderViewHolder<_T extends ObjectWithId> extends CustomViewHolder<_T> {
		private final TextView mId, mName, mBio, mHomepage, mLocation;
		private final ImageView mUserImage, mUserBannerImage;
		private final Button mFollowers, mFriends, mTweets, mFavorites;
		private final View mIsProtected, mIsVerified;
		private Tweeter user;

		// TODO Design

		public UserHeaderViewHolder(View itemView) {
			super(itemView);
			mId = (TextView) itemView.findViewById(R.id.user_id);
			mName = (TextView) itemView.findViewById(R.id.user_name);
			mLocation = (TextView) itemView.findViewById(R.id.user_location);
			mHomepage = (TextView) itemView.findViewById(R.id.user_homepage);
			mBio = (TextView) itemView.findViewById(R.id.user_bio);
			mUserImage = (ImageView) itemView.findViewById(R.id.user_image);
			mUserBannerImage = (ImageView) itemView.findViewById(R.id.user_banner);
			mFollowers = (Button) itemView.findViewById(R.id.followers);
			mFriends = (Button) itemView.findViewById(R.id.friends);
			mTweets = (Button) itemView.findViewById(R.id.tweets);
			mFavorites = (Button) itemView.findViewById(R.id.favorites);
			mIsProtected = itemView.findViewById(R.id.imgInfoProtected);
			mIsVerified = itemView.findViewById(R.id.imgInfoVerified);
			mUserImage.setOnClickListener(this);
			mUserBannerImage.setOnClickListener(this);
			mTweets.setOnClickListener(this);
			mFavorites.setOnClickListener(this);
		}

		@Override
		public void onClick(View v) {
			if (v.equals(mUserImage)) {
				if (user.profile_image_url != null)
					MediaPreviewActivity.previewImage(v.getContext(), user.profile_image_url[user.profile_image_url.length - 1]);
			} else if (v.equals(mUserBannerImage)) {
				if (user.profile_banner_url != null)
					MediaPreviewActivity.previewImage(v.getContext(), user.profile_banner_url[user.profile_banner_url.length - 1]);
			} else if (v.equals(mTweets)) {
				Page.templatePageUser(user.user_id, user.screen_name, (MainActivity) mFragment.getActivity(), PageElement.FUNCTION_USER_TIMELINE, Page.indexOf(mFragment.mPage));
			} else if (v.equals(mFavorites)) {
				Page.templatePageUser(user.user_id, user.screen_name, (MainActivity) mFragment.getActivity(), PageElement.FUNCTION_USER_FAVORITES, Page.indexOf(mFragment.mPage));
			}
		}

		@Override
		public void bindViewHolder(int position) {
			PageElement.ElementHeader header = (PageElement.ElementHeader) mAdapter.getItem(position);
			user = Tweeter.getTweeter(header.getElement().id, header.getElement().name);
			mId.setText(user.screen_name);
			mName.setText(user.name);
			if (user.location == null || user.location.isEmpty())
				mLocation.setVisibility(View.GONE);
			else {
				mLocation.setVisibility(View.VISIBLE);
				mLocation.setText(user.location);
			}
			if (user.description == null || user.description.isEmpty())
				mBio.setVisibility(View.GONE);
			else {
				mBio.setVisibility(View.VISIBLE);
				mBio.setText(TweetSpanner.includeEntities(user.description, user.entities_description, mFragment.mImageCache, mHomepage.getLineHeight(), mBio.getText()));
			}
			if (user.url == null || user.url.isEmpty())
				mHomepage.setVisibility(View.GONE);
			else {
				mHomepage.setVisibility(View.VISIBLE);
				mHomepage.setText(user.url == null ? "" : TweetSpanner.includeEntities(user.url, user.entities_url, mFragment.mImageCache, mHomepage.getLineHeight(), mHomepage.getText()));
			}
			mFragment.mImageCache.assignImageView(mUserImage, user.getProfileImageUrl(), null);
			mFragment.mImageCache.assignImageView(mUserBannerImage, user.getProfileBannerUrl(), null);
			mFollowers.setText(user.followers_count + " followers");
			mFriends.setText(user.friends_count + " friends");
			mTweets.setText(user.statuses_count + "\ntweets");
			mFavorites.setText(user.favourites_count + "\nfavorites");
			mIsProtected.setVisibility(user._protected ? View.VISIBLE : View.GONE);
			mIsVerified.setVisibility(user.verified ? View.VISIBLE : View.GONE);
			for (PageElement e : mFragment.mPage.elements)
				if (e.function == PageElement.FUNCTION_USER_TIMELINE) {
					mTweets.setEnabled(false);
					mFavorites.setEnabled(true);
				} else if (e.function == PageElement.FUNCTION_USER_FAVORITES) {
					mTweets.setEnabled(true);
					mFavorites.setEnabled(false);
				}
		}
	}

	public abstract class FilteredItem implements Comparable<FilteredItem> {
		protected _T mObject;
		protected boolean isFilteredBySelection;

		FilteredItem(_T o) {
			mObject = o;
		}

		public void setSpanned(Spannable s, Matcher m) {
			if (m == null) {
				if (s.length() != 0)
					s.setSpan(new ForegroundColorSpan(mColorAccent), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				return;
			}
			do {
				for (int i = 1; i <= m.groupCount(); i++)
					if (m.end(i) - m.start(i) > 0)
						s.setSpan(new ForegroundColorSpan(mColorAccent), m.start(i), m.end(i), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			} while (m.find());
		}

		@Override
		public int compareTo(@NonNull FilteredItem another) {
			return mObject.compareTo(another.mObject);
		}

		@Override
		public int hashCode() {
			return mObject.hashCode();
		}

		@Override
		public String toString() {
			return mObject.toString();
		}
	}

	public abstract class PageItemAdapter extends RecyclerView.Adapter<CustomViewHolder<_T>> {

		public final ArrayList<NonElementHeader> mNonElementHeaders = new ArrayList<>();
		public final ArrayList<PageElement.ElementHeader> mElementHeaders = new ArrayList<>();

		public PageItemAdapter() {
			super();
			setHasStableIds(true);
		}

		public void addNonElementHeader(int layoutId) {
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			for (int i = mNonElementHeaders.size() - 1; i >= 0; i--)
				if (mNonElementHeaders.get(i).getLayoutId() == layoutId)
					return;
			NonElementHeader eh = new NonElementHeader(layoutId);
			mNonElementHeaders.add(eh);
			notifyItemInserted(elementHeaderLength + mNonElementHeaders.size() - 1);
		}

		public void removeNonElementHeader(int layoutId) {
			if (!isPageReady()) return;
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			for (int i = mNonElementHeaders.size() - 1; i >= 0; i--)
				if (mNonElementHeaders.get(i).getLayoutId() == layoutId) {
					mNonElementHeaders.remove(i);
					notifyItemRemoved(elementHeaderLength + i);
				}
		}

		public void notifyNonHeaderItemRemoved(int position) {
			if (!isPageReady()) return;
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			int headerLength = elementHeaderLength + mNonElementHeaders.size();
			notifyItemRemoved(position + headerLength);
		}

		public void notifyNonHeaderItemInserted(int position) {
			if (!isPageReady()) return;
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			int headerLength = elementHeaderLength + mNonElementHeaders.size();
			notifyItemInserted(position + headerLength);
		}

		public void notifyNonHeaderItemChanged(int position) {
			if (!isPageReady()) return;
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			int headerLength = elementHeaderLength + mNonElementHeaders.size();
			notifyItemChanged(position + headerLength);
		}

		public int adapterPositionToListIndex(int position) {
			if (!isPageReady()) return -1;
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			int headerLength = elementHeaderLength + mNonElementHeaders.size();
			if (position < elementHeaderLength)
				return position;
			if (position < elementHeaderLength + mNonElementHeaders.size())
				return position - elementHeaderLength;
			return position - headerLength;
		}

		public Object getItem(int position) {
			if (!isPageReady()) return null;
			if (position < 0)
				return null;
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			int headerLength = elementHeaderLength + mNonElementHeaders.size();
			if (position < elementHeaderLength)
				return mElementHeaders.get(position);
			if (position < elementHeaderLength + mNonElementHeaders.size())
				return mNonElementHeaders.get(position - elementHeaderLength);
			if (mQuickFilteredList != null)
				return mQuickFilteredList.get(position - headerLength);
			if (position - headerLength >= mList.size())
				return null;
			return mList.get(position - headerLength);
		}

		public int getItemPosition(Object item) {
			if (!isPageReady()) return -1;
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			int headerLength = elementHeaderLength + mNonElementHeaders.size();
			if (item instanceof Tweet) {
				int pos;
				if (mQuickFilteredList != null)
					pos = Collections.binarySearch(mQuickFilteredList, new FilteredItem((_T) item) {
					}, Collections.reverseOrder());
				else
					pos = Collections.binarySearch(mList, item, Collections.reverseOrder());
				if (pos == -1)
					return pos - headerLength;
				return pos + headerLength;
			} else if (item instanceof PageElement.ElementHeader)
				return mElementHeaders.indexOf(item);
			else if (item instanceof NonElementHeader)
				return mNonElementHeaders.indexOf(item) + elementHeaderLength;
			else { // FilteredItem
				FilteredItem itm;
				try {
					itm = (FilteredItem) item;
				} catch (Throwable e) {
					return -1;
				}
				int pos;
				if (mQuickFilteredList != null)
					pos = Collections.binarySearch(mQuickFilteredList, item, Collections.reverseOrder());
				else
					pos = Collections.binarySearch(mList, itm.mObject, Collections.reverseOrder());
				if (pos == -1)
					return pos - headerLength;
				return pos + headerLength;
			}
		}

		@Override
		public long getItemId(int position) {
			if (!isPageReady()) return -1;
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			int headerLength = elementHeaderLength + mNonElementHeaders.size();
			if (position < elementHeaderLength)
				return mElementHeaders.get(position).getId();
			if (position < elementHeaderLength + mNonElementHeaders.size())
				return mNonElementHeaders.get(position - elementHeaderLength).getId();
			if (mQuickFilteredList != null)
				return mQuickFilteredList.get(position - headerLength).mObject.getId();
			return mList.get(position - headerLength).getId();
		}

		@Override
		public int getItemViewType(int position) {
			if (!isPageReady() || position < 0) return -1;
			int elementHeaderLength = mQuickFilteredList != null ? 0 : mElementHeaders.size();
			if (position < elementHeaderLength)
				return mElementHeaders.get(position).getHeaderType();
			if (position < elementHeaderLength + mNonElementHeaders.size())
				return mNonElementHeaders.get(position - elementHeaderLength).getLayoutId();
			return R.layout.row_tweet;
		}

		@Override
		public int getItemCount() {
			if (!isPageReady()) return 0;
			int size = mNonElementHeaders.size();
			if (mQuickFilteredList != null)
				size += mQuickFilteredList.size();
			else if (mList != null)
				size += mElementHeaders.size() + mList.size();
			return size;
		}

		@Override
		public CustomViewHolder<_T> onCreateViewHolder(ViewGroup parent, int viewType) {
			final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			View v = inflater.inflate(viewType, parent, false);
			switch (viewType) {
				case R.layout.row_header_big_user:
					return new UserHeaderViewHolder(v);
			}
			return new NonElementHeaderViewHolder(v);
		}

		@Override
		public void onBindViewHolder(CustomViewHolder<_T> holder, int position) {
			holder.setFragment(PageFragment.this);
			holder.bindViewHolder(adapterPositionToListIndex(position));
		}

		public CustomViewHolder<_T> getViewHolder(int position) {
			if (!isPageReady()) return null;
			View o = mViewList.getChildAt(position - mListLayout.findFirstVisibleItemPosition());
			if (o == null) return null;
			return (CustomViewHolder<_T>) o.getTag(R.id.VIEWHOLDER_ID);
		}
	}
}
