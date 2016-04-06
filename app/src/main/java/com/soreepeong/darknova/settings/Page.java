package com.soreepeong.darknova.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.Parcelable;

import com.soreepeong.darknova.Darknova;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ThreadScheduler;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.ObjectWithId;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.twitter.TwitterStreamServiceReceiver;
import com.soreepeong.darknova.ui.MainActivity;
import com.soreepeong.darknova.ui.fragments.PageFragment;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Page, rendered by PageFragment
 *
 * @author Soreepeong
 */
public abstract class Page<_T extends ObjectWithId> implements Parcelable{
	protected static final int MAX_BACKGROUND_NEW_ITEMS = 80;
	protected static final int MAX_MEMORY_HOLD = 200;
	protected static final int SAVE_LENGTH = 200;
	protected static final int SAVE_DELAY = 5000;
	protected static final Handler SAVE_HANDLER;

	static{
		HandlerThread t = new HandlerThread("SAVE_HANDLER");
		t.start();
		SAVE_HANDLER = new Handler(t.getLooper());
	}

	protected static final List<Page<? extends ObjectWithId>> mPages = Collections.synchronizedList(new ArrayList<Page<? extends ObjectWithId>>());
	private static final ArrayList<OnPageListChangedListener> mPagesListener = new ArrayList<>();
	private static final Comparator<PageElement> elementComparator = new Comparator<PageElement>() {
		@Override
		public int compare(PageElement lhs, PageElement rhs) {
			return lhs.generateUniqid().compareTo(rhs.generateUniqid());
		}
	};
	private static final Map<Page, String> mPagesOnMemory = Collections.synchronizedMap(new WeakHashMap<Page, String>());
	private static int mNonTemporaryPageLength = 0;
	private static SharedPreferences mPagePreferences;
	private static long mLastPageLoad;
	private final long mId;
	public final String name;
	public final int iconResId;
	public final List<PageElement> elements;
	private final Object mListEditLock = new Object();
	private final ArrayList<_T> mListPending = new ArrayList<>();
	private final List<WeakReference<Page>> mParentPage;
	private List<_T> mList;
	public final WeakHashMap<_T, HashMap<PageElement, Byte>> mLoadMoreItems = new WeakHashMap<>();
	public PageFragment<_T> mConnectedFragment;
	public boolean mIsListAtTop;
	public int mPageLastOffset;
	public long mPageLastItemId, mPageNewestSeenItemId;
	protected final File mListCacheFile;

	public boolean mStartedCollectingPending;

	private static final ThreadScheduler mRefreshScheduler = new ThreadScheduler(4, "Refresher");

	protected static void addRefresher(Runnable t){
		mRefreshScheduler.schedule(t);
	}

	protected static void removeRefresher(Runnable t){
		mRefreshScheduler.cancel(t);
	}

	protected Page(String name, int iconResId, List<PageElement> elements, Page parentPage) {
		this.name = name;
		this.iconResId = iconResId;
		this.elements = Collections.unmodifiableList(elements);
		long id;
		synchronized (mPages) {
			wholeLoop:
			while (true) {
				id = Darknova.uniqid();
				for (Page p : mPages)
					if (p.mId == id)
						continue wholeLoop;
				break;
			}
		}
		mId = id;
		mPagesOnMemory.put(this, name);
		mListCacheFile = new File(Darknova.mCacheDir, "list_cache_" + StringTools.UrlEncode(generateUniqid()));

		if (parentPage == null) {
			mParentPage = null;
			return;
		}
		ArrayList<WeakReference<Page>> parents = new ArrayList<>();
		while (true) {
			parents.add(new WeakReference<>(parentPage));
			int parentIndex = parentPage.getParentPageIndex();
			if (parentIndex == -1)
				break;
			parentPage = mPages.get(parentIndex);
		}
		mParentPage = parents;
	}

	protected Page(Parcel in) {
		mId = in.readLong();
		name = in.readString();
		iconResId = in.readInt();
		ArrayList<PageElement> newElements = new ArrayList<>();
		in.readList(newElements, PageElement.class.getClassLoader());
		for (Iterator<PageElement> i = newElements.iterator(); i.hasNext(); ) {
			if (i.next().mTwitterEngine == null)
				i.remove();
		}
		elements = Collections.unmodifiableList(newElements);
		mParentPage = null;
		mPagesOnMemory.put(this, name);
		mListCacheFile = new File(Darknova.mCacheDir, "list_cache_" + StringTools.UrlEncode(generateUniqid()));
	}

	protected Page(String key, SharedPreferences in) {
		mId = in.getLong(key + ".id", Darknova.uniqid());
		name = in.getString(key + ".name", null);
		iconResId = in.getInt(key + ".iconResId", 0);
		ArrayList<PageElement> newElements = new ArrayList<>();
		for (int i = 0, i_ = in.getInt(key + ".elements.length", 0); i < i_; i++) {
			PageElement e = new PageElement(key + ".elements." + i, in);
			if (e.mTwitterEngine != null)
				newElements.add(e);
		}
		elements = Collections.unmodifiableList(newElements);
		mParentPage = null;
		mPagesOnMemory.put(this, name);
		mListCacheFile = new File(Darknova.mCacheDir, "list_cache_" + StringTools.UrlEncode(generateUniqid()));
	}

	public static void templatePageUser(long user_id, String screen_name, MainActivity mainActivity, int function) {
		templatePageUser(user_id, screen_name, mainActivity, function, -1);
	}

	public static void templatePageUser(long user_id, String screen_name, MainActivity mainActivity, int function, int replacing) {
		String fnName = function == PageElement.FUNCTION_USER_FAVORITES ? ": ☆" : "";
		Page.Builder builder = new Builder(screen_name + fnName, R.drawable.ic_eyes);
		TwitterEngine currentUser = mainActivity.getDrawerFragment().getCurrentUser();
		builder.e().add(new PageElement(currentUser, PageElement.FUNCTION_USER_SINGLE, user_id, screen_name));
		builder.e().add(new PageElement(currentUser, function, user_id, screen_name));
		builder.setParentPage(mainActivity.getCurrentPage().getRepresentingPage());
		mainActivity.selectPage(replacing >= 0 ? Page.replaceIfNoExist(replacing, builder.build()) : Page.addIfNoExist(builder.build()));
		mainActivity.getSearchSuggestionFragment().increaseSearchCountRecord(currentUser.getUserId(), Tweeter.getTweeter(user_id, screen_name));
	}

	public static void templatePageSearch(String search, MainActivity mainActivity) {
		Page.Builder builder = new Page.Builder(search, R.drawable.ic_bigeyed);
		TwitterEngine currentUser = mainActivity.getDrawerFragment().getCurrentUser();
		builder.e().add(new PageElement(currentUser, PageElement.FUNCTION_SEARCH, 0, search));
		builder.setParentPage(mainActivity.getCurrentPage().getRepresentingPage());
		mainActivity.selectPage(Page.addIfNoExist(builder.build()));
		mainActivity.getSearchSuggestionFragment().increaseSearchCountRecord(currentUser.getUserId(), search);
	}

	public static void templatePageTweet(Tweet t, MainActivity mainActivity) {
		if (t.retweeted_status != null) t = t.retweeted_status;
		Page.Builder builder = new Page.Builder(t.user.screen_name + ": " + (t.text == null ? "?" : t.text), R.drawable.ic_bigeyed);
		TwitterEngine currentUser = mainActivity.getDrawerFragment().getCurrentUser();
		builder.e().add(new PageElement(currentUser, PageElement.FUNCTION_TWEET_SINGLE, t.id, null));
		builder.setParentPage(mainActivity.getCurrentPage().getRepresentingPage());
		mainActivity.selectPage(Page.addIfNoExist(builder.build()));
	}

	public static void savePages() {
		synchronized (mPages) {
			SharedPreferences.Editor editor = mPagePreferences.edit();
			editor.putInt("mPages.length", mPages.size());
			for (int i = 0, i_ = Math.min(mNonTemporaryPageLength, mPages.size()); i < i_; i++)
				mPages.get(i).writeToPreferences("mPages." + i, editor);
			mPagePreferences.getLong("mPages.lastedit", mLastPageLoad = System.currentTimeMillis());
			editor.apply();
		}
	}

	public static void reloadPages() {
		synchronized (mPages) {
			if (mPagePreferences.getLong("mPages.lastedit", 1) != mLastPageLoad) {
				ArrayList<Page<? extends ObjectWithId>> oldPages = new ArrayList<>();
				ArrayList<Page<? extends ObjectWithId>> tempPages = new ArrayList<>();
				oldPages.addAll(mPages.subList(0, mNonTemporaryPageLength));
				tempPages.addAll(mPages.subList(mNonTemporaryPageLength, mPages.size()));
				mPages.clear();
				mNonTemporaryPageLength = mPagePreferences.getInt("mPages.length", 0);
				for (int i = 0; i < mNonTemporaryPageLength; i++) {
					Page<? extends ObjectWithId> p;
					if(mPagePreferences.getString("mPages." + i + ".type", "").equals(PageTweet.class.getName()))
						p = new PageTweet("mPages." + i, mPagePreferences);
					else
						continue;
					if (oldPages.contains(p)) {
						p = oldPages.get(oldPages.indexOf(p));
						oldPages.remove(p);
					} else {
						for (PageElement e : p.elements)
							if(e.containsStream(e.twitterEngineId)){
								TwitterStreamServiceReceiver.addStreamCallback((TwitterEngine.TwitterStreamCallback) p);
								break;
							}

					}
					if (p.elements.size() == 0)
						continue;
					if (p.elements.get(0).function == PageElement.FUNCTION_DEBUG)
						continue;
					mPages.add(p);
				}
				if (mNonTemporaryPageLength > mPages.size())
					mNonTemporaryPageLength = mPages.size();
				for (Page<? extends ObjectWithId> p : oldPages)
					if(p instanceof TwitterEngine.TwitterStreamCallback)
						TwitterStreamServiceReceiver.removeStreamCallback((TwitterEngine.TwitterStreamCallback) p);
				for (Page<? extends ObjectWithId> p : tempPages)
					addIfNoExist(p);
			}
			mLastPageLoad = mPagePreferences.getLong("new_tweet.lastedit", 1);
		}
	}

	public static void createDefaultPages(TwitterEngine user) {
		Page.Builder p;
		p = new Page.Builder("Home", R.drawable.ic_launcher);
		p.e().add(new PageElement(user, PageElement.FUNCTION_HOME_TIMELINE, 0, null));
		addIfNoExist(p.build(), false);
		p = new Page.Builder("Mentions", R.drawable.ic_mention);
		p.e().add(new PageElement(user, PageElement.FUNCTION_MENTIONS, 0, null));
		addIfNoExist(p.build(), false);
	}

	public static void initialize(Context context, String prefName) {
		mPagePreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
		mPagePreferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
				reloadPages();
			}
		});
		TwitterEngine.addOnUserlistChangedListener(new TwitterEngine.OnUserlistChangedListener(){
			@Override
			public void onUserlistChanged(List<TwitterEngine> engines, List<TwitterEngine> oldEngines){
				ArrayList<TwitterEngine> newEngines = new ArrayList<>(engines);
				ArrayList<TwitterEngine> removedEngines = new ArrayList<>(oldEngines);
				newEngines.removeAll(oldEngines);
				removedEngines.removeAll(engines);
				for(int i = 0; i < mPages.size(); i++){
					Page<? extends ObjectWithId> p = mPages.get(i);
					ArrayList<PageElement> newElements = new ArrayList<>();
					newElements.addAll(p.elements);
					for(PageElement e : p.elements)
						if(removedEngines.contains(e.mTwitterEngine))
							newElements.remove(e);
					if(newElements.isEmpty())
						remove(i--);
					else if(newElements.size() != p.elements.size()){
						Builder newPage = new Builder(p.name, p.iconResId);
						newPage.e().addAll(newElements);
						newPage.mParentPage = mPages.get(p.getParentPageIndex());
						mPages.set(i, newPage.build());
					}
				}
				for(TwitterEngine e : newEngines)
					createDefaultPages(e);
				reloadPages();
			}
		});
		reloadPages();
	}

	public static ArrayList<Page<? extends ObjectWithId>> getPageList(){
		ArrayList<Page<? extends ObjectWithId>> res = new ArrayList<>();
		res.addAll(mPages);
		return res;
	}

	public static int indexOf(Page p) {
		return mPages.indexOf(p);
	}

	public static int getCountNonTemporary() {
		return mNonTemporaryPageLength;
	}

	public static void setCountNonTemporary(int newSize) {
		mNonTemporaryPageLength = newSize;
		savePages();
		broadcastPageChange();
	}

	@SuppressWarnings("unchecked")
	public static <_T extends ObjectWithId> Page<_T> getLoaded(Page<_T> p) {
		synchronized (mPages) {
			int i = mPages.indexOf(p);
			if (i == -1)
				return null;
			return (Page<_T>) Page.mPages.get(i);
		}
	}

	public static void addOnPageChangedListener(OnPageListChangedListener p) {
		synchronized (mPagesListener) {
			mPagesListener.add(p);
		}
	}

	public static void removeOnPageChangedListener(OnPageListChangedListener p) {
		synchronized (mPagesListener) {
			mPagesListener.remove(p);
		}
	}

	public static int addIfNoExist(Page<? extends ObjectWithId> p) {
		return addIfNoExist(p, true);
	}

	public static int addIfNoExist(Page<? extends ObjectWithId> p, boolean isTemporaryPage) {
		synchronized (mPages) {
			int i = mPages.indexOf(p);
			if (i != -1) {
				if (!isTemporaryPage && mNonTemporaryPageLength <= i) {
					mPages.add(mNonTemporaryPageLength, mPages.remove(i));
					mNonTemporaryPageLength++;
					savePages();
					broadcastPageChange();
				}
				return i;
			}
			if(p instanceof TwitterEngine.TwitterStreamCallback){
				for(PageElement e : p.elements){
					if(e.containsStream(e.twitterEngineId)){
						TwitterStreamServiceReceiver.addStreamCallback((TwitterEngine.TwitterStreamCallback) p);
						break;
					}
				}
			}
			i = isTemporaryPage ? mPages.size() : mNonTemporaryPageLength;
			mPages.add(i, p);
			if (!isTemporaryPage) {
				mNonTemporaryPageLength++;
				savePages();
			}
			broadcastPageChange();
			return i;
		}
	}

	public static int replaceIfNoExist(int index, Page<? extends ObjectWithId> p) {
		synchronized (mPages) {
			int i = mPages.indexOf(p);
			if (i != -1)
				return i;
			if(p instanceof TwitterEngine.TwitterStreamCallback){
				for(PageElement e : p.elements){
					if(e.containsStream(e.twitterEngineId)){
						TwitterStreamServiceReceiver.addStreamCallback((TwitterEngine.TwitterStreamCallback) p);
						break;
					}
				}
			}
			if(mPages.get(index) instanceof TwitterEngine.TwitterStreamCallback)
				TwitterStreamServiceReceiver.removeStreamCallback((TwitterEngine.TwitterStreamCallback) mPages.get(index));
			mPages.set(index, p);
			if (index < mNonTemporaryPageLength) {
				savePages();
			}
			broadcastPageChange();
			return index;
		}
	}

	public static int size() {
		return mPages.size();
	}

	public static Page<? extends ObjectWithId> get(int index) {
		return mPages.get(index);
	}

	public static void broadcastPageChange() {
		synchronized (mPagesListener) {
			for (OnPageListChangedListener l : mPagesListener)
				l.onPageListChanged();
		}
	}

	public static Page remove(int index) {
		synchronized (mPages) {
			if (mPages.size() > index && index >= 0) {
				Page p = mPages.get(index);
				if(p instanceof TwitterEngine.TwitterStreamCallback)
					TwitterStreamServiceReceiver.removeStreamCallback((TwitterEngine.TwitterStreamCallback) p);
				mPages.remove(p);
				if (index < mNonTemporaryPageLength) {
					mNonTemporaryPageLength--;
					savePages();
				}
				broadcastPageChange();
				return p;
			}
			return null;
		}
	}

	public static void reorder(int from, int to) {
		boolean isTempPageReorder = false;
		synchronized (mPages) {
			if (to >= mNonTemporaryPageLength && from >= mNonTemporaryPageLength)
				isTempPageReorder = true;
			else if (to < mNonTemporaryPageLength && from >= mNonTemporaryPageLength)
				mNonTemporaryPageLength++;
			else if (from < mNonTemporaryPageLength && to >= mNonTemporaryPageLength)
				mNonTemporaryPageLength--;
			mPages.add(to, mPages.remove(from));
			if (!isTempPageReorder)
				savePages();
			broadcastPageChange();
		}
	}

	public static void clearMemory(){
		synchronized(mPages){
			for(final Page p : mPages)
				if(p.isListLoaded() && p.mConnectedFragment == null)
					if(p instanceof PageTweet)
						((PageTweet) p).registerSaveAndClear();
		}
	}

	public abstract boolean applyPending();

	public List<_T> getListPending(){
		synchronized(mListPending){
			if(mListPending.isEmpty())
				return null;
			List<_T> res = Collections.unmodifiableList(new ArrayList<>(mListPending));
			mListPending.clear();
			return res;
		}
	}

	public void addListPending(_T t){
		synchronized(mListPending){
			int i = Collections.binarySearch(mListPending, t);
			if(i >= 0)
				return;
			mListPending.add(-i - 1, t);
			if(mListPending.size() > MAX_BACKGROUND_NEW_ITEMS){
				if(mList != null)
					new Thread(){
						@Override
						public void run(){
							applyPending();
						}
					}.start();
				else
					mListPending.subList(MAX_BACKGROUND_NEW_ITEMS, mListPending.size()).clear();
			}
		}
	}

	public Object getListLock(){
		return mListEditLock;
	}

	public List<_T> getList(){
		return mList;
	}

	public void unloadList(){
		mList = null;
	}

	public void updateList(List<_T> list){
		if(mConnectedFragment != null && mConnectedFragment.isPageReady()){
			try{
				mConnectedFragment.calculateListPositions();
				List<_T> prevList = mList;
				if((mList = list) == null)
					mList = Collections.emptyList();
				mConnectedFragment.onDataSetUpdated(prevList);
			}catch(Exception e){
				if((mList = list) == null)
					mList = Collections.emptyList();
				e.printStackTrace();
			}
		}else{
			if((mList = list) == null)
				mList = Collections.emptyList();
		}
	}

	public boolean isListLoaded(){
		return mList != null;
	}

	public void setFragment(PageFragment<_T> frag){
		android.util.Log.d("Page", mId + " (" + name + ") " + (frag == null ? "disconnect" : "connect"));
		mConnectedFragment = frag;
	}

	public boolean canHave(Tweet tweet) {
		if(tweet == null) return false;
		for (PageElement e : elements)
			if (e.canHave(tweet))
				return true;
		return false;
	}

	public boolean containsStream(TwitterEngine engine) {
		for (PageElement e : elements)
			if (e.containsStream(engine.getUserId()))
				return true;
		return false;
	}

	public int getParentPageIndex() {
		if (mParentPage != null) {
			for (WeakReference<Page> p : mParentPage) {
				Page page = p.get();
				if (page == null) continue;
				int pos = mPages.indexOf(page);
				if (pos == -1) continue;
				return pos;
			}
		}
		return -1;
	}

	public String generateUniqid() {
		StringBuilder sb = new StringBuilder();
		for (PageElement e : elements) {
			if (sb.length() > 0)
				sb.append(":");
			sb.append(e.generateUniqid());
		}
		return StringTools.UrlEncode(sb.toString());
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Page))
			return false;
		Page<? extends ObjectWithId> p2 = (Page<? extends ObjectWithId>) o;
		if (p2.mId == mId)
			return true;
		if (p2.elements.size() != elements.size())
			return false;
		ArrayList<PageElement> e = new ArrayList<>(p2.elements);
		e.removeAll(elements);
		return e.isEmpty();
	}

	public long getId() {
		return mId;
	}

	@Override
	public int hashCode() {
		return elements.hashCode();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(mId);
		dest.writeString(name);
		dest.writeInt(iconResId);
		dest.writeList(elements);
	}

	public void writeToPreferences(String key, SharedPreferences.Editor dest) {
		dest.putLong(key + ".id", mId);
		dest.putString(key + ".name", name);
		dest.putInt(key + ".iconResId", iconResId);
		dest.putInt(key + ".elements.length", elements.size());
		for (int i = 0, i_ = elements.size(); i < i_; i++)
			elements.get(i).writeToPreferences(key + ".elements." + i, dest);
	}

	public interface OnPageListChangedListener {
		void onPageListChanged();
	}

	public static class Builder{
		String mName;
		int mResId;
		ArrayList<PageElement> mElements = new ArrayList<>();
		Page mParentPage;

		public Builder(String name, int resId) {
			mName = name;
			mResId = resId;
		}

		public Builder(String name) {
			mName = name;
			mResId = R.drawable.ic_launcher;
		}

		public Builder() {
			mName = "untitled page";
			mResId = R.drawable.ic_launcher;
		}

		public Builder setName(String name) {
			mName = name;
			return this;
		}

		public Builder setIconResId(int resId) {
			mResId = resId;
			return this;
		}

		public Builder setParentPage(Page page) {
			mParentPage = page;
			return this;
		}

		public ArrayList<PageElement> e() {
			return mElements;
		}

		public Page<? extends ObjectWithId> build(){
			String pageType;
			if (mElements == null)
				throw new RuntimeException("already built");
			if (mElements.size() == 0)
				throw new RuntimeException("no element");
			pageType = mElements.get(0).type();
			if(mElements.size() > 1){
				for(PageElement e : mElements){
					if(e.isOnlyElement()){
						throw new RuntimeException("only one element allowed");
					}else if(pageType != null && !e.type().equals(pageType)){
						throw new RuntimeException("only one type of page allowed");
					}
					pageType = e.type();
				}
			}
			Collections.sort(mElements, elementComparator);
			if(pageType == null || pageType.equals(PageTweet.class.getName()))
				return new PageTweet(mName, mResId, mElements, mParentPage);
			return null;
		}
	}
}