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
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.soreepeong.darknova.Darknova;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.PageElement;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.tools.TimedStorage;
import com.soreepeong.darknova.twitter.ObjectWithId;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.twitter.TwitterStreamServiceReceiver;
import com.soreepeong.darknova.ui.viewholder.CustomViewHolder;
import com.soreepeong.darknova.ui.viewholder.NonElementHeaderViewHolder;
import com.soreepeong.darknova.ui.viewholder.UserHeaderViewHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
	protected static final RecyclerView.RecycledViewPool mRecyclerViewPool = new RecyclerView.RecycledViewPool() {
		@Override
		public void putRecycledView(RecyclerView.ViewHolder scrap) {
			if (scrap instanceof CustomViewHolder)
				((CustomViewHolder) scrap).releaseMemory();
			super.putRecycledView(scrap);
		}
	};
	private static final SparseArray<TimedStorage<View>> mCachedPageViews = new SparseArray<>();
	protected static int mColorAccent;

	static {
		mRecyclerViewPool.setMaxRecycledViews(R.layout.row_tweet, 48);
		mRecyclerViewPool.setMaxRecycledViews(R.layout.row_header_big_tweet, 3);
	}

	public final ArrayList<Tweet> mSelectedList = new ArrayList<>();
	public Page<_T> mPage;
	public ArrayList<FilteredItem> mQuickFilteredList;
	private Handler mHandler;
	protected PageItemAdapter mAdapter;
	protected View mViewRoot;
	protected boolean mIsActive;
	protected String mQuickFilterString;
	protected Pattern mQuickFilterPattern;
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

	protected boolean mIsPagePrepared;
	protected OnCreateBackground<_T, ? extends PageFragment<_T>> mBackgroundLoader;

	public static View obtainPageView(int layoutId, LayoutInflater inflater, ViewGroup container) {
		View res = null;
		if (mCachedPageViews.get(layoutId) != null)
			res = mCachedPageViews.get(layoutId).obtain();
		if(res == null){
			res = inflater.inflate(layoutId, container, false);
			android.util.Log.d("Darknova", "obtainPageView: Create");
		}
		return res;
	}

	public static void releasePageView(int layoutId, View v) {
		if (mCachedPageViews.get(layoutId) == null)
			mCachedPageViews.put(layoutId, new TimedStorage<View>());
		mCachedPageViews.get(layoutId).release(v);
	}

	public static PageFragment newInstance(String cacheDir, Page p) {
		return TimelineFragment.newInstance(cacheDir, p);
	}

	public PageItemAdapter getAdapter() {
		return mAdapter;
	}

	public void showRefreshProgress(int max){
	}

	public void setRefreshProgress(int progress){
	}

	public void hideRefreshProgress(){
	}


	public void onCompleteRefresh() {
		if (Looper.getMainLooper() != Looper.myLooper())
			throw new RuntimeException("MUST be called from the UI Thread");
		mPage.updateList(null);
		mPage.getListPending();
		mAdapter.notifyDataSetChanged();
		applyEmptyIndicator();
		itemRead();
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
		itemRead();
	}

	public void onPageEnter(){}
	public void onPageLeave(){}

	public abstract boolean isSomethingSelected();
	public abstract void clearSelection();


	public void loadBackground() {
		if (mBackgroundLoader != null && mBackgroundLoader.getStatus() == AsyncTask.Status.PENDING)
			mBackgroundLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	protected void itemRead(){
		List<_T> l = mPage == null ? null : mPage.getList();
		if(l == null || l.isEmpty())
			itemRead(-1, true);
		else
			itemRead(l.get(0).getId(), true);
	}

	protected void itemRead(long id, boolean forceUpdate) {
	}

	public void selectionChanged() {
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

	public void calculateListPositions(){
		if (getView() == null || !mIsActive)
			return;
		if (mViewList.getChildCount() == 0 || mPage.mIsListAtTop) {
			mPage.mIsListAtTop = true;
			mPage.mPageLastOffset = 0;
			return;
		}
		int first = mListLayout.findFirstVisibleItemPosition();
		View v = mListLayout.findViewByPosition(first);
		mPage.mPageLastOffset = v != null ? v.getTop() - mViewList.getPaddingTop() : 0;
		Object o = mAdapter.getItem(first);
		if (o instanceof ObjectWithId)
			mPage.mPageLastItemId = ((ObjectWithId) o).getId();
		else
			mPage.mPageLastItemId = -1;
		return;
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
			int lastIndex = mPage.getList().indexOf(createDummyObject(mPage.mPageLastItemId));
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
		mViewList.swapAdapter(mAdapter, true);
	}

	public void applyEmptyIndicator() {
		if (getView() == null)
			return;
		AlphaAnimation aa = null;
		boolean isEmpty = true;
		if (mIsPagePrepared) {
			if(mPage.getList().isEmpty()){
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
			aa.setAnimationListener(new Animation.AnimationListener(){
				@Override
				public void onAnimationEnd(Animation animation){
					if(mViewEmptyIndicator != null)
						mViewEmptyIndicator.setVisibility(View.GONE);
				}

				@Override
				public void onAnimationStart(Animation animation){
				}

				@Override
				public void onAnimationRepeat(Animation animation){
				}
			});
		} else if (isEmpty && (mViewEmptyIndicator.getVisibility() != View.VISIBLE)) {
			aa = new AlphaAnimation(0, 1);
			aa.setInterpolator(new DecelerateInterpolator());
			aa.setAnimationListener(new Animation.AnimationListener(){
				@Override
				public void onAnimationEnd(Animation animation){
					if(mViewEmptyIndicator != null)
						mViewEmptyIndicator.setVisibility(View.VISIBLE);
				}

				@Override
				public void onAnimationStart(Animation animation){
				}

				@Override
				public void onAnimationRepeat(Animation animation){
				}
			});
		}
		if (aa == null)
			return;
		aa.setDuration(300);
		mViewEmptyIndicator.startAnimation(aa);
	}

	public boolean isPageReady(){
		return mIsPagePrepared && mBackgroundLoader == null;
	}

	public void onDataSetUpdated(List<_T> listOld){
	}

	public static abstract class OnCreateBackground<_T extends ObjectWithId, _FRAGMENT_T extends PageFragment<_T>> extends AsyncTask<Object, Object, Object> {
		protected final Page<_T> mPage;
		protected final _FRAGMENT_T mFragment;
		protected final WeakHashMap<_T, HashMap<PageElement, Byte>> mLoadMoreItems = new WeakHashMap<>();
		protected boolean isNewlyLoadedPage = false;
		protected List<_T> mList;
		protected boolean requireRefresh = false;

		public OnCreateBackground(_FRAGMENT_T mFragment) {
			this.mFragment = mFragment;
			mPage = mFragment.mPage;
			mList = mPage.getList();
		}

		public void postExecuteNow() {
			onPostExecute(null);
		}

		@Override
		protected Object doInBackground(Object... params) {
			if (mList == null) {
				mList = new ArrayList<>();
				isNewlyLoadedPage = true;
			}else
				mList = new ArrayList<>(mList);
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
			mPage.updateList(mList);
			mPage.mLoadMoreItems.clear();
			mPage.mLoadMoreItems.putAll(mLoadMoreItems);
			mFragment.mBackgroundLoader = null;
			mFragment.mViewProgress.setIndeterminate(false);
			AlphaAnimation aa = new AlphaAnimation(1, 0);
			aa.setDuration(300);
			aa.setFillAfter(true);
			aa.setInterpolator(new AccelerateDecelerateInterpolator());
			mFragment.mViewProgress.setVisibility(View.GONE);
			mFragment.mViewProgress.startAnimation(aa);
			mFragment.itemRead();
			mFragment.createAdapter();
			mPage.setFragment(mFragment);

			mFragment.mAdapter.mElementHeaders.clear();
			for(PageElement e : mPage.elements){
				if(e.isHeaderView())
					mFragment.mAdapter.mElementHeaders.add(e.createHeader(e.getHeaderType()));
				TwitterEngine engine = e.getTwitterEngine();
				if(TwitterStreamServiceReceiver.isStreamOn(engine) && e.containsStream(engine))
					requireRefresh = true;
			}
			mFragment.restorePositions();
			if(isNewlyLoadedPage && requireRefresh)
				mFragment.onRefresh();
			mFragment.mAdapter.notifyDataSetChanged();
		}
	}

	private final Object UI_LIST_UPDATE_LOCK = new Object();

	public void cancelPendingActions(){
		mHandler.removeCallbacksAndMessages(null);
		synchronized(UI_LIST_UPDATE_LOCK){
			UI_LIST_UPDATE_LOCK.notify();
		}
	}

	public void addPendingAction(int code, int delay){
		mHandler.removeMessages(code);
		mHandler.sendEmptyMessageDelayed(code, delay);
	}

	public void addPendingAction(Runnable run){
		mHandler.post(run);
	}

	public boolean updateListUi(final List<_T> newList){
		final AtomicBoolean s = new AtomicBoolean(false);
		try{
			synchronized(UI_LIST_UPDATE_LOCK){
				mHandler.post(new Runnable(){
					@Override
					public void run(){
						synchronized(UI_LIST_UPDATE_LOCK){
							if(s.get()) return;
							mPage.updateList(newList);
							s.set(true);
							UI_LIST_UPDATE_LOCK.notify();
						}
					}
				});
				UI_LIST_UPDATE_LOCK.wait();
				if(s.get())
					return true;
			}
		}catch(InterruptedException e){
			e.printStackTrace();
		}finally{
			s.set(true);
		}
		return false;
	}

	public static class NonElementHeader {
		private final int mLayoutId;
		private final long mId;

		public NonElementHeader(int layoutId) {
			mLayoutId = layoutId;
			mId = Darknova.uniqid();
		}

		public int getLayoutId() {
			return mLayoutId;
		}

		public long getId() {
			return mId;
		}
	}

	public abstract class FilteredItem implements Comparable<FilteredItem> {
		public _T mObject;
		public boolean isFilteredBySelection;

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
			if(position - headerLength >= mPage.getList().size())
				return null;
			return mPage.getList().get(position - headerLength);
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
					pos = Collections.binarySearch(mPage.getList(), item, Collections.reverseOrder());
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
					pos = Collections.binarySearch(mPage.getList(), itm.mObject, Collections.reverseOrder());
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
			return mPage.getList().get(position - headerLength).getId();
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
			else if(mPage.getList() != null)
				size += mElementHeaders.size() + mPage.getList().size();
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
