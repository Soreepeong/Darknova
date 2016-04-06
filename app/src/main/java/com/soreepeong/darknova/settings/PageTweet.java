package com.soreepeong.darknova.settings;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;

import com.soreepeong.darknova.tools.FileTools;
import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.ui.fragments.PageFragment;
import com.soreepeong.darknova.ui.fragments.TimelineFragment;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Created by Soreepeong on 2016-04-02.
 */
public class PageTweet extends Page<Tweet> implements TwitterEngine.TwitterStreamCallback{
	@SuppressWarnings("unused")
	public static final Parcelable.Creator<Page> CREATOR = new Parcelable.Creator<Page>(){
		@Override
		public Page createFromParcel(Parcel in){
			Page p = null;
			if(in.readString().equals(PageTweet.class.getName())){
				p = new PageTweet(in);
				for(Page p2 : mPages)
					if(p2.equals(p))
						return p2;
			}
			return p;
		}

		@Override
		public Page[] newArray(int size){
			return new Page[size];
		}
	};
	public boolean mSavePending;

	protected PageTweet(Parcel in){
		super(in);
	}

	protected PageTweet(String name, int iconResId, List<PageElement> elements, Page parentPage){
		super(name, iconResId, elements, parentPage);
	}

	protected PageTweet(String key, SharedPreferences in){
		super(key, in);
	}

	@Override
	public void writeToPreferences(String key, SharedPreferences.Editor dest){
		super.writeToPreferences(key, dest);
		dest.putString(key + ".type", PageTweet.class.getName());
	}

	@Override
	public void writeToParcel(Parcel dest, int flags){
		dest.writeString(PageTweet.class.getName());
		super.writeToParcel(dest, flags);
	}

	@Override
	public void onStreamStart(TwitterEngine engine){
		if(mConnectedFragment != null)
			mConnectedFragment.onStreamStart(engine);
	}

	@Override
	public void onStreamConnected(TwitterEngine engine){
		if(mConnectedFragment != null)
			mConnectedFragment.onStreamConnected(engine);
	}

	@Override
	public void onStreamError(TwitterEngine engine, Throwable e){
		if(mConnectedFragment != null)
			mConnectedFragment.onStreamError(engine, e);
	}

	@Override
	public void onStreamTweetEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, Tweet tweet, long created_at){
		if(mConnectedFragment != null)
			mConnectedFragment.onStreamTweetEvent(engine, event, source, target, tweet, created_at);
	}

	@Override
	public void onStreamUserEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, long created_at){
		if(mConnectedFragment != null)
			mConnectedFragment.onStreamUserEvent(engine, event, source, target, created_at);
	}

	@Override
	public void onStreamStop(TwitterEngine engine){
		if(mConnectedFragment != null)
			mConnectedFragment.onStreamStop(engine);
	}

	@Override
	public void onNewTweetReceived(Tweet tweet){
		if(!canHave(tweet))
			return;
		addListPending(tweet);
		if(mConnectedFragment != null)
			mConnectedFragment.onNewTweetReceived(tweet);
	}

	public boolean applyPending(){
		synchronized(getListLock()){
			final List<Tweet> listOld = getList();
			if(listOld == null)
				return false;
			final List<Tweet> listNew = new ArrayList<>(listOld);
			final List<Tweet> listPending = getListPending();
			if(listPending == null)
				return false;
			for(int i = listPending.size() - 1; i >= 0 && !Thread.interrupted(); i--){
				Tweet tweet = listPending.get(i); // newest tweets first
				int index = Collections.binarySearch(listNew, tweet, Collections.reverseOrder());
				if(index < 0){
					index = -index - 1;
					listNew.add(index, tweet);
				}
			}
			if(mIsListAtTop && listNew.size() > MAX_MEMORY_HOLD)
				listNew.subList(MAX_MEMORY_HOLD, listNew.size()).clear();
			final List<Tweet> applyingList = Collections.unmodifiableList(listNew);
			if(mConnectedFragment == null || !mConnectedFragment.updateListUi(applyingList))
				updateList(applyingList);
			registerDelayedSave();
			return true;
		}
	}

	public void registerDelayedSave(){
		mSavePending = true;
		SAVE_HANDLER.postDelayed(mListSaverRunnable, SAVE_DELAY);
	}

	public void registerSaveAndClear(){
		android.util.Log.d("Darknova", "registerSaveAndClear: " + indexOf(this));
		mSavePending = true;
		SAVE_HANDLER.post(mListSaverRunnable);
		SAVE_HANDLER.post(mClearRunnable);
	}

	@Override
	public void setFragment(PageFragment<Tweet> frag){
		if(frag != null)
			SAVE_HANDLER.removeCallbacks(mClearRunnable);
		super.setFragment(frag);
	}

	private final Runnable mClearRunnable = new Runnable(){
		@Override
		public void run(){
			unloadList();
		}
	};

	private final Runnable mListSaverRunnable = new Runnable(){
		@Override
		public void run(){
			SAVE_HANDLER.removeCallbacks(mListSaverRunnable);
			final List<Tweet> list = getList();
			if(!mSavePending || list == null || list.isEmpty())
				return;
			TwitterEngine.applyAccountInformationChanges();
			final ArrayList<Tweet> res = new ArrayList<>(list.size() > SAVE_LENGTH ? list.subList(0, SAVE_LENGTH) : list);
			final HashMap<Tweet, HashMap<PageElement, Byte>> map = new HashMap<>();
			for(Tweet t : mLoadMoreItems.keySet())
				map.put(t, new HashMap<>(mLoadMoreItems.get(t)));
			mSavePending = false;
			new Thread(){
				@Override
				public void run(){
					Parcel p = Parcel.obtain();
					FileOutputStream out = null;
					try{
						out = new FileOutputStream(mListCacheFile);
						p.writeList(res);
						p.writeInt(map.size());
						for(Tweet t : map.keySet()){
							p.writeParcelable(t, 0);
							p.writeInt(map.get(t).size());
							for(PageElement e : map.get(t).keySet()){
								p.writeParcelable(e, 0);
								p.writeByte(map.get(t).get(e));
							}
						}
						out.write(p.marshall());
						out.flush();
					}catch(Exception e){
						e.printStackTrace();
					}finally{
						StreamTools.close(out);
						p.recycle();
					}
				}
			}.start();
		}
	};

	public List<Tweet> loadListCache(){
		if(!mListCacheFile.exists())
			return null;
		byte b[] = FileTools.readFile(mListCacheFile, 1048576 * 5);
		if(b == null)
			return null;
		List<Tweet> list = new ArrayList<>();
		Parcel p = Parcel.obtain();
		try{
			p.unmarshall(b, 0, b.length);
			p.setDataPosition(0);
			if(p.dataSize() > 0){
				p.readList(list, Tweet.class.getClassLoader());
				for(Iterator<Tweet> i = list.iterator(); i.hasNext(); ){
					if(i.next() == null)
						i.remove();
				}
				for(int i = 0, size = p.readInt(); i < size; i++){
					HashMap<PageElement, Byte> map = new HashMap<>();
					Tweet key = p.readParcelable(Tweet.class.getClassLoader());
					for(int j = 0, sizej = p.readInt(); j < sizej; j++){
						PageElement e = p.readParcelable(PageElement.class.getClassLoader());
						map.put(e, p.readByte());
					}
					mLoadMoreItems.put(key, map);
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			p.recycle();
		}
		return list;
	}

	private PageRefresher mUpdateRefresher;

	public void refresh(Tweet initiator){
		if(mUpdateRefresher != null)
			return;
		mUpdateRefresher = new PageRefresher(initiator);
		mUpdateRefresher.execute(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public void applyRefreshStatus(){
		if(mConnectedFragment != null && mUpdateRefresher != null){
			mConnectedFragment.showRefreshProgress(mUpdateRefresher.mMaxProgress);
			mConnectedFragment.setRefreshProgress(mUpdateRefresher.mProgress);
		}
	}

	public class PageRefresher extends AsyncTask<Object, Object, Object> implements TwitterEngine.TweetCallback{
		private final HashMap<PageElement, PageElementRefresher> mElements = new HashMap<>();
		private final Tweet mInitiator;
		private final int mLoadCount = 200;
		private volatile int mProgress, mMaxProgress;
		private long mLastPublishTime;

		public PageRefresher(Tweet initiator){
			super();
			mInitiator = initiator;
			if(initiator == null){ // refresh button
				for(PageElement e : elements){
					mMaxProgress += 200;
					mElements.put(e, new PageElementRefresher(e, 0, 0));
				}
			}else{
				synchronized(mLoadMoreItems){
					for(PageElement e : mLoadMoreItems.get(initiator).keySet()){
						if(mLoadMoreItems.get(initiator).put(e, TimelineFragment.LOAD_MORE_ITEM_LOADING) == TimelineFragment.LOAD_MORE_ITEM_AVAILABLE){
							mElements.put(e, new PageElementRefresher(e, 0, initiator.id));
							mMaxProgress += 200;
						}
					}
				}
			}
		}

		@Override
		protected Object doInBackground(Object... params){
			try{
				for(PageElementRefresher refresher : mElements.values())
					addRefresher(refresher);
				for(PageElementRefresher refresher : mElements.values())
					refresher.finished.acquire();
			}catch(InterruptedException e){
				e.printStackTrace();
				for(PageElementRefresher refresher : mElements.values())
					removeRefresher(refresher);
			}
			applyPending();
			return null;
		}

		@Override
		protected void onPreExecute(){
			if(mConnectedFragment != null)
				mConnectedFragment.showRefreshProgress(mMaxProgress);
		}

		@Override
		protected void onProgressUpdate(Object... values){
			if(mConnectedFragment != null)
				mConnectedFragment.setRefreshProgress(mProgress);
		}

		@Override
		protected void onPostExecute(Object o){
			if(mConnectedFragment != null)
				mConnectedFragment.hideRefreshProgress();
			if(mInitiator != null && mLoadMoreItems.containsKey(mInitiator) && mLoadMoreItems.get(mInitiator).isEmpty())
				synchronized(mLoadMoreItems){
					mLoadMoreItems.remove(mInitiator);
				}
			mUpdateRefresher = null;
		}

		@Override
		public void onNewTweetReceived(Tweet tweet){
			mProgress++;
			addListPending(tweet);
			if(System.currentTimeMillis() - mLastPublishTime >= 750){
				applyPending();
				publishProgress();
				mLastPublishTime = System.currentTimeMillis();
			}
		}

		private class PageElementRefresher implements Runnable{
			private final PageElement mElement;
			private final long since_id, max_id;
			public Exception exception;
			public Semaphore finished = new Semaphore(1);

			public PageElementRefresher(PageElement element, long since_id, long max_id){
				super();
				mElement = element;
				this.since_id = since_id;
				this.max_id = max_id;
				finished.drainPermits();
			}

			@Override
			public void run(){
				ArrayList<Tweet> res = null;
				try{
					switch(mElement.function){
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
						case PageElement.FUNCTION_USER_FAVORITES:
							res = mElement.getTwitterEngine().getUserFavorites(mElement.id, mLoadCount, since_id, max_id, true, PageRefresher.this);
							break;
						case PageElement.FUNCTION_TWEET_SINGLE:
							Tweet t = Tweet.getTweet(elements.get(0).id);
							int i = 0;
							while(!Thread.interrupted() && i < 10){
								if(t.retweeted_status != null) t = t.retweeted_status;
								if(t.info.stub){
									t = mElement.getTwitterEngine().getTweet(t.id);
									i++;
									if(t == null)
										break;
								}
								onNewTweetReceived(t);
								t = t.in_reply_to_status;
								if(t == null)
									break;
							}
							break;
					}
					mProgress += mLoadCount - (res == null ? 0 : res.size());
					publishProgress();
					if(res == null || res.isEmpty())
						return;
					Tweet last = res.get(res.size() - 1);
					synchronized(mLoadMoreItems){
						HashMap<PageElement, Byte> updaters = mLoadMoreItems.get(last);
						if(updaters == null)
							mLoadMoreItems.put(last, updaters = new HashMap<>());
						updaters.put(mElement, TimelineFragment.LOAD_MORE_ITEM_AVAILABLE);
					}
				}catch(TwitterEngine.RequestException e){
					e.printStackTrace();
					exception = e;
				}finally{
					finished.release();
				}
			}
		}
	}
}
