package com.soreepeong.darknova.settings;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.soreepeong.darknova.DarknovaApplication;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.twitter.TwitterStreamServiceReceiver;
import com.soreepeong.darknova.ui.fragments.TimelineFragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Page, rendered by PageFragment
 *
 * @author Soreepeong
 */
public class Page implements Parcelable, TwitterEngine.TwitterStreamCallback{
	public static final ArrayList<Page> pages = new ArrayList<>();
	public static final ArrayList<OnPageListChangedListener> mPagesListener = new ArrayList<>();
	@SuppressWarnings("unused")
	public static final Parcelable.Creator<Page> CREATOR = new Parcelable.Creator<Page>() {
		@Override
		public Page createFromParcel(Parcel in) {
			return new Page(in);
		}

		@Override
		public Page[] newArray(int size) {
			return new Page[size];
		}
	};
	private static final Comparator<Element> elementComparator = new Comparator<Element>() {
		@Override
		public int compare(Element lhs, Element rhs) {
			return lhs.generateUniqid().compareTo(rhs.generateUniqid());
		}
	};
	public static int mSavedPageLength = 0;
	public final String name;
	public final int iconResId;
	public final List<Element> elements;
	public final Object mListEditLock = new Object();
	public final ArrayList<Tweet> mListPending = new ArrayList<>();
	private final long mId;
	private final List<WeakReference<Page>> mParentPage;
	public WeakReference<List<Tweet>> mList;
	public TimelineFragment mConnectedFragment;
	public boolean mIsListAtTop;
	public int mPageLastItemPosition, mPageLastOffset;
	public long mPageLastItemId;
	private Thread mList_holdRemover;

	protected Page(String name, int iconResId, List<Element> elements, Page parentPage) {
		this.name = name;
		this.iconResId = iconResId;
		this.elements = Collections.unmodifiableList(elements);
		mId = DarknovaApplication.uniqid();
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
			parentPage = pages.get(parentIndex);
		}
		mParentPage = parents;
	}

	protected Page(Parcel in) {
		name = in.readString();
		iconResId = in.readInt();
		ArrayList<Element> newElements = new ArrayList<>();
		in.readList(newElements, Element.class.getClassLoader());
		elements = Collections.unmodifiableList(newElements);
		mId = DarknovaApplication.uniqid();
		mParentPage = null;
	}

	public static void addOnPageChangedListener(OnPageListChangedListener p){
		synchronized (mPagesListener){
			mPagesListener.add(p);
		}
	}

	public static void removeOnPageChangedListener(OnPageListChangedListener p){
		synchronized (mPagesListener){
			mPagesListener.remove(p);
		}
	}

	public static void addPage(int index, Page p){
		boolean useStream = false;
		for(Element e : p.elements){
			useStream |= e.containsStream(e.twitterEngineId);
		}
		if(useStream)
			TwitterStreamServiceReceiver.addStreamCallback(null, p);
		pages.add(index, p);
	}

	public static void broadcastPageChange(){
		synchronized (mPagesListener){
			for(OnPageListChangedListener l : mPagesListener)
				l.onPageListChanged();
		}
	}

	public static void addPage(Page p){
		addPage(pages.size(), p);
	}

	public static void removePage(Page p){
		TwitterStreamServiceReceiver.removeStreamCallback(p);
		pages.remove(p);
	}

	public static Page removePage(int index){
		Page p = null;
		if(pages.size() > index && index >= 0)
			removePage(p = pages.get(index));
		return p;
	}

	public void setFragment(TimelineFragment frag){
		if(mList_holdRemover != null){
			mList_holdRemover.interrupt();
			mList_holdRemover = null;
		}
		if(frag == null){ // delay gc
			final List<Tweet> mList_hold = mList != null && mList.get() != null ? mList.get() : null;
			if(mList_hold != null){
				mList_holdRemover = new Thread(){
					@Override
					public void run() {
						try{
							Thread.sleep(60000);
							android.util.Log.d("darknova", "page gone: " + name + " size " + mList_hold.size());
						}catch(InterruptedException e){
							android.util.Log.d("darknova", "page held: " + name);
						}
					}
				};
				mList_holdRemover.start();
			}
		}
		android.util.Log.d("Darknova Page", mId + " (" + name + ") " + (frag==null?"disconnect":"connect"));
		mConnectedFragment = frag;
	}

	@Override
	public void onStreamStart(TwitterEngine.StreamableTwitterEngine engine) {
		if(mConnectedFragment!=null)
			mConnectedFragment.onStreamStart(engine);
	}

	@Override
	public void onStreamConnected(TwitterEngine.StreamableTwitterEngine engine) {
		if(mConnectedFragment!=null)
			mConnectedFragment.onStreamConnected(engine);
	}

	@Override
	public void onStreamError(TwitterEngine.StreamableTwitterEngine engine, Exception e) {
		if(mConnectedFragment!=null)
			mConnectedFragment.onStreamError(engine, e);
	}

	@Override
	public void onStreamTweetEvent(TwitterEngine.StreamableTwitterEngine engine, String event, Tweeter source, Tweeter target, Tweet tweet, long created_at) {
		if(mConnectedFragment!=null)
			mConnectedFragment.onStreamTweetEvent(engine, event, source, target, tweet, created_at);
	}

	@Override
	public void onStreamUserEvent(TwitterEngine.StreamableTwitterEngine engine, String event, Tweeter source, Tweeter target) {
		if(mConnectedFragment!=null)
			mConnectedFragment.onStreamUserEvent(engine, event, source, target);
	}

	@Override
	public void onStreamStop(TwitterEngine.StreamableTwitterEngine engine) {
		if(mConnectedFragment!=null)
			mConnectedFragment.onStreamStop(engine);
	}

	public boolean addNewTweet(Tweet tweet, boolean streamInitated){
		synchronized (mListPending){
			if(streamInitated && !canHave(tweet))
				return false;
			int index = Collections.binarySearch(mListPending, tweet); // old tweets on front
			if(index < 0){
				index = -index - 1;
				mListPending.add(index, tweet);
			}
		}
		return true;
	}

	@Override
	public void onNewTweetReceived(Tweet tweet) {
		if(!addNewTweet(tweet, true))
			return;
		if(mConnectedFragment!=null)
			mConnectedFragment.onNewTweetReceived(tweet);
	}

	public boolean canHave(Tweet tweet) {
		for (Element e : elements)
			if (e.canHave(tweet))
				return true;
		return false;
	}

	public boolean containsStream(TwitterEngine.StreamableTwitterEngine engine) {
		for (Element e : elements)
			if (e.containsStream(engine.getUserId()))
				return true;
		return false;
	}

	public int getParentPageIndex() {
		if (mParentPage != null) {
			for (WeakReference<Page> p : mParentPage) {
				Page page = p.get();
				if (page == null) continue;
				int pos = pages.indexOf(page);
				if (pos == -1) continue;
				return pos;
			}
		}
		return -1;
	}

	public String generateUniqid() {
		StringBuilder sb = new StringBuilder();
		for (Element e : elements) {
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
		Page p2 = (Page) o;
		if (p2.elements.size() != elements.size())
			return false;
		ArrayList<Element> e = new ArrayList<>(p2.elements);
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
		dest.writeString(name);
		dest.writeInt(iconResId);
		dest.writeList(elements);
	}

	public interface OnPageListChangedListener {
		void onPageListChanged();
	}

	public static class Element implements Parcelable {
		/** Functions returning tweets */
		public static final int FUNCTION_HOME_TIMELINE = 0;
		public static final int FUNCTION_MENTIONS = 1;
		public static final int FUNCTION_RETWEETS_OF_ME = 2;
		public static final int FUNCTION_SEARCH = 3;
		public static final int FUNCTION_LIST_TWEETS = 4;
		public static final int FUNCTION_USER_TIMELINE = 5;
		public static final int FUNCTION_USER_FAVORITES = 6;
		public static final int FUNCTION_TWEET_SINGLE = 7;

		/** Functions returning user IDs */
		public static final int FUNCTION_BLOCKED_USERS = 9;
		public static final int FUNCTION_NO_RETWEET_USERS = 10;
		public static final int FUNCTION_LIST_MEMBERS = 11;
		public static final int FUNCTION_LIST_SUBSCRIBERS = 12;
		public static final int FUNCTION_USER_FOLLOWERS = 13;
		public static final int FUNCTION_USER_FRIENDS = 14;
		public static final int FUNCTION_USER_LIST_FOLLOWING = 15;
		public static final int FUNCTION_TWEET_RETWEETED_BY = 16;

		/** Functions returning lists */
		public static final int FUNCTION_USER_LIST_MEMBERED = 17;

		/** Functions returning single user */
		public static final int FUNCTION_USER_SINGLE = 18;

		/** Functions returning DMs */
		public static final int FUNCTION_DM_RECEIVED = 19;
		public static final int FUNCTION_DM_SENT = 20;
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<Element> CREATOR = new Parcelable.Creator<Element>() {
			@Override
			public Element createFromParcel(Parcel in) {
				return new Element(in);
			}

			@Override
			public Element[] newArray(int size) {
				return new Element[size];
			}
		};
		public final long twitterEngineId;
		public final int function;
		public final long id;
		public final String name;
		private TwitterEngine mTwitterEngine;
		private String mUniqid;

		public Element(TwitterEngine twitterEngine, int function, long id, String name){
			this.twitterEngineId = twitterEngine.getUserId();
			this.mTwitterEngine = twitterEngine;
			this.function = function;
			this.id = id;
			this.name = name;
		}

		protected Element(Parcel in) {
			twitterEngineId = in.readLong();
			mTwitterEngine = TwitterEngine.get(twitterEngineId);
			function = in.readInt();
			id = in.readLong();
			name = in.readString();
		}

		@Override
		public int hashCode() {
			return Long.valueOf(twitterEngineId).hashCode() ^ function ^ Long.valueOf(id).hashCode() ^ (name==null?0:name.hashCode());
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Element &&
					((Element)o).twitterEngineId == twitterEngineId &&
					((Element)o).function == function &&
					((Element)o).id == id &&
					((((Element)o).name != null && ((Element)o).name.equals(name)) ||
							(name == null && ((Element)o).name == null));
		}

		public String generateUniqid(){
			if(mUniqid != null)
				return mUniqid;
			mUniqid = twitterEngineId + "." + function + "." + id + "." + name;
			return mUniqid;
		}

		public boolean containsStream(TwitterEngine e){
			return e != null && containsStream(e.getUserId());
		}

		public boolean containsStream(long user_id){
			switch(function){
				case FUNCTION_HOME_TIMELINE:
					return user_id == twitterEngineId;
				case FUNCTION_MENTIONS:
					return user_id == twitterEngineId;
				case FUNCTION_USER_TIMELINE:
					return user_id == id;
			}
			return false;
		}

		public boolean isHeaderView(){
			switch(function){
				case FUNCTION_USER_SINGLE:
					return true;
			}
			return false;
		}

		public boolean isOnlyElement(){
			switch(function){
				case FUNCTION_TWEET_SINGLE:
					return true;
			}
			return false;
		}

		public boolean canHave(Tweet tweet){
			switch(function){
				case FUNCTION_HOME_TIMELINE:
					return twitterEngineId == tweet.user.user_id || mTwitterEngine.getTweeter().follows(tweet.user);
				case FUNCTION_MENTIONS:
					if(tweet.entities != null)
						for(Entities.Entity e : tweet.entities.entities){
							if(e instanceof Entities.MentionsEntity)
								if(((Entities.MentionsEntity)e).id == twitterEngineId)
									return true;
						}
					return false;
				case FUNCTION_USER_TIMELINE:
					return tweet.user.user_id == id;
				case FUNCTION_USER_SINGLE:
					return false;
				case FUNCTION_SEARCH:
					return true;
			}
			return true;
		}

		public TwitterEngine getTwitterEngine(Context context){
			if(mTwitterEngine == null && twitterEngineId != 0)
				mTwitterEngine = TwitterEngine.get(twitterEngineId);
			return mTwitterEngine;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeLong(mTwitterEngine.getUserId());
			dest.writeInt(function);
			dest.writeLong(id);
			dest.writeString(name);
		}

		public ElementHeader createHeader(int viewType){
			ElementHeader header = new ElementHeader();
			header.mId = DarknovaApplication.uniqid();
			header.mHeaderType = viewType;
			return header;
		}

		public class ElementHeader implements Comparable<ElementHeader>{

			private long mId;
			private int mHeaderType;

			public long getId(){
				return mId;
			}

			public int getHeaderType(){
				return mHeaderType;
			}

			@Override
			public int compareTo(@NonNull ElementHeader another) {
				return mId > another.mId ? 1 : (mId == another.mId ? 0 : -1);
			}

			public Element getElement(){
				return Element.this;
			}
		}
	}

	public static class Builder{
		String mName;
		int mResId;
		ArrayList<Element> mElements = new ArrayList<>();
		Page mParentPage;
		public Builder(String name, int resId){
			mName = name;
			mResId = resId;
		}
		public Builder(String name){
			mName = name;
			mResId = R.drawable.ic_launcher;
		}
		public Builder(){
			mName = "untitled page";
			mResId = R.drawable.ic_launcher;
		}
		public Builder setName(String name){
			mName = name;
			return this;
		}
		public Builder setIconResId(int resId){
			mResId=resId;
			return this;
		}
		public Builder setParentPage(Page page){
			mParentPage = page;
			return this;
		}
		public ArrayList<Element> e(){
			return mElements;
		}
		public Page build(){
			if(mElements == null)
				throw new RuntimeException("already built");
			if(mElements.size() == 0)
				throw new RuntimeException("no element");
			if(mElements.size()>1)
				for(Element e : mElements)
					if(e.isOnlyElement())
						throw new RuntimeException("only one element allowed");
			Collections.sort(mElements, elementComparator);
			Page p = new Page(mName, mResId, mElements, mParentPage);
			mElements = null;
			return p;
		}
	}
}