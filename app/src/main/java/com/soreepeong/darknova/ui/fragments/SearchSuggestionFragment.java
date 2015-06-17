package com.soreepeong.darknova.ui.fragments;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.SavedSearch;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.ui.MainActivity;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fragment that shows search suggestions
 *
 * @author Soreepeong
 */
public class SearchSuggestionFragment extends Fragment implements SearchView.OnQueryTextListener, View.OnFocusChangeListener, ImageCache.OnImageCacheReadyListener, MenuItemCompat.OnActionExpandListener, View.OnClickListener {
	private static final int LIST_COUNT = 15;
	private static final ArrayList<SavedSearch> mSavedSearches = new ArrayList<>();
	private static File mSearchHistoryFile;
	private final ArrayList<UserSuggestion> mUserList = new ArrayList<>();
	private final ArrayList<SearchSuggestion> mSearchList = new ArrayList<>();
	private SharedPreferences mSearchPreferences;
	private int mColorAccent;
	private boolean mQuickSearchMode;
	private String mInputText = "";
	private View mViewFragment;
	private RecyclerView mViewUserList, mViewSearchList;
	private SwipeRefreshLayout mViewUserRefresher, mViewSearchRefresher;
	private ImageButton mViewQuickSearch;
	private SuggestedUserAdapter mUserAdapter;
	private SuggestedSearchAdapter mSearchAdapter;
	private ImageCache mImageCache;
	private SearchView mSearchView;
	private MenuItem mMenuItem;
	private Pattern mSearchOptimizedPattern;
	private ListCreator mTaskListCreator;
	private UserSearcher mTaskUserSearcher;
	private Thread mHistorySaveThread;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mViewFragment = inflater.inflate(R.layout.fragment_search_suggestion, container, false);
		mViewUserList = (RecyclerView) mViewFragment.findViewById(R.id.user_suggestion);
		mViewSearchList = (RecyclerView) mViewFragment.findViewById(R.id.search_suggestion);
		mViewQuickSearch = (ImageButton) inflater.inflate(R.layout.activity_main_search_btn, container, false);
		mViewUserRefresher = (SwipeRefreshLayout) mViewFragment.findViewById(R.id.user_suggestion_refresher);
		mViewSearchRefresher = (SwipeRefreshLayout) mViewFragment.findViewById(R.id.search_suggestion_refresher);
		mViewUserList.setAdapter(mUserAdapter = new SuggestedUserAdapter());
		mViewSearchList.setAdapter(mSearchAdapter = new SuggestedSearchAdapter());
		mViewUserList.setLayoutManager(new LinearLayoutManager(mViewUserList.getContext()));
		mViewSearchList.setLayoutManager(new LinearLayoutManager(mViewSearchList.getContext()));
		mViewUserRefresher.setOnRefreshListener(mUserAdapter);
		mViewSearchRefresher.setOnRefreshListener(mSearchAdapter);
		mImageCache = ImageCache.getCache(getActivity(), this);
		mColorAccent = ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent);
		mSearchPreferences = getActivity().getSharedPreferences("SearchLoader", 0);
		if (mSearchHistoryFile == null) {
			mSearchHistoryFile = new File(getActivity().getCacheDir(), "search-history");
			new SearchLoader().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
			new Thread() {
				@Override
				public void run() {
					InputStream in = null;
					Parcel p = Parcel.obtain();
					try {
						in = new FileInputStream(mSearchHistoryFile);
						byte[] b = new byte[(int) mSearchHistoryFile.length()];
						if (b.length != in.read(b))
							return;
						p.unmarshall(b, 0, b.length);
						p.setDataPosition(0);
						UserSearchHistory.prepare(p);
						TweetSearchHistory.prepare(p);
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						p.recycle();
						StreamTools.close(in);
					}
				}
			}.start();
		}

		return mViewFragment;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mViewFragment = null;
		mViewUserList = null;
		mViewSearchList = null;
	}

	private void saveHistory() {
		if (mHistorySaveThread != null)
			return;
		mHistorySaveThread = new Thread() {
			@Override
			public void run() {
				OutputStream out = null;
				Parcel p = Parcel.obtain();
				try {
					out = new FileOutputStream(mSearchHistoryFile);
					UserSearchHistory.save(p);
					TweetSearchHistory.save(p);
					out.write(p.marshall());
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					p.recycle();
					StreamTools.close(out);
				}
			}
		};
		mHistorySaveThread.start();
	}

	@Override
	public void onClick(View v) {
		if (v.equals(mViewQuickSearch)) {
			setQuickSearchMode(!mQuickSearchMode);
			if (mQuickSearchMode) {
				PageFragment page = ((MainActivity) getActivity()).getCurrentPage();
				page.setQuickFilter(mSearchView.getQuery().toString());
			}
		}
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		onClick(mViewQuickSearch);
		return false;
	}

	public void setQuickFilter(String text) {
		if (!MenuItemCompat.isActionViewExpanded(mMenuItem))
			MenuItemCompat.expandActionView(mMenuItem);
		setQuickSearchMode(true);
		mSearchView.setQuery(text, false);
		mSearchView.requestFocus();
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		mInputText = newText;
		if (!mQuickSearchMode) {
			mSearchOptimizedPattern = StringTools.getMaybePatternSearcher(newText);
			if (mTaskListCreator != null)
				mTaskListCreator.cancel(true);
			mTaskListCreator = new ListCreator();
			mTaskListCreator.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, newText);
		} else {
			PageFragment page = ((MainActivity) getActivity()).getCurrentPage();
			page.setQuickFilter(newText);
		}
		return true;
	}

	@Override
	public boolean onMenuItemActionExpand(MenuItem item) {
		mSearchList.clear();
		mUserList.clear();
		mSearchAdapter.notifyDataSetChanged();
		mUserAdapter.notifyDataSetChanged();
		setQuickSearchMode(false);
		onQueryTextChange("");
		return true;
	}

	@Override
	public boolean onMenuItemActionCollapse(MenuItem item) {
		setQuickSearchMode(false);
		hide();
		return true;
	}

	public void setSearchUi(SearchView v, MenuItem item) {
		mSearchView = v;
		mMenuItem = item;
		MenuItemCompat.setOnActionExpandListener(mMenuItem, this);
		mSearchView.setOnQueryTextListener(this);
		mSearchView.setOnQueryTextFocusChangeListener(this);
		mSearchView.setSubmitButtonEnabled(true);
		int submit_area_id = mSearchView.getContext().getResources().getIdentifier("submit_area", "id", mSearchView.getContext().getPackageName());
		LinearLayout ll = (LinearLayout) mSearchView.findViewById(submit_area_id);
		int search_go_btn_id = mSearchView.getContext().getResources().getIdentifier("search_go_btn", "id", mSearchView.getContext().getPackageName());
		ImageView submitImage = (ImageView) ll.findViewById(search_go_btn_id);
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, 0);
		LinearLayout.LayoutParams old = (LinearLayout.LayoutParams) submitImage.getLayoutParams();
		submitImage.setLayoutParams(layoutParams);
		for (int i = 0; i < ll.getChildCount(); i++) {
			if (ll.getChildAt(i).equals(submitImage)) {
				mViewQuickSearch.setLayoutParams(old);
				if (mViewQuickSearch.getParent() != null)
					((ViewGroup) mViewQuickSearch.getParent()).removeView(mViewQuickSearch);
				ll.addView(mViewQuickSearch, i);
				break;
			}
		}
		mViewQuickSearch.setOnClickListener(this);

	}

	public void hideAndIconify() {
		if (mSearchView != null && !mSearchView.isIconified()) {
			mSearchView.setIconified(true);
			mMenuItem.collapseActionView();
			hide();
		}
	}

	public void setQuickSearchMode(boolean mode) {
		PageFragment page = ((MainActivity) getActivity()).getCurrentPage();
		if (mode && page == null)
			mode = false;
		mQuickSearchMode = mode;
		if (!mQuickSearchMode && !mSearchView.isIconified())
			show();
		else
			hide();
		if (!mode && page != null) {
			page.setQuickFilter(null);
		}
	}

	public void show() {
		if (mViewFragment.getVisibility() == View.VISIBLE)
			return;
		mViewFragment.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.show_downward));
		mViewFragment.setVisibility(View.VISIBLE);
	}

	public void hide() {
		if (mViewFragment.getVisibility() != View.VISIBLE)
			return;
		ResTools.hideWithAnimation(mViewFragment, R.anim.hide_upward, true);
	}

	private ArrayList<UserSuggestion> makeUserSuggestions(String user) {
		ArrayList<UserSuggestion> res = new ArrayList<>();
		String text = user.toLowerCase();
		String textallowed = StringTools.DISALLOWED_USERNAME_CHARACTERS.matcher(user).replaceAll("_");
		if (text.length() >= 1) {
			HashMap<String, Tweeter> map = Tweeter.getAvailableTweeters();
			Iterator<Tweeter> i = map.values().iterator();
			while (i.hasNext()) {
				Tweeter t = i.next();
				if (t.screen_name.toLowerCase().startsWith(text.toLowerCase()) || (t.name != null && t.name.toLowerCase().startsWith(text.toLowerCase())))
					res.add(new UserSuggestion(t, t.screen_name, t.name, text, mSearchOptimizedPattern));
				else if (!textallowed.startsWith("_") && (t.screen_name.toLowerCase().startsWith(textallowed.toLowerCase()) || (t.name != null && t.name.toLowerCase().startsWith(textallowed.toLowerCase()))))
					res.add(new UserSuggestion(t, t.screen_name, t.name, textallowed, mSearchOptimizedPattern));
				else
					continue;
				i.remove();
			}
			Collections.sort(res);
			if (res.size() < LIST_COUNT) {
				int div = res.size();
				i = map.values().iterator();
				while (i.hasNext()) {
					Tweeter t = i.next();
					if (t.screen_name.toLowerCase().contains(text.toLowerCase()) || (t.name != null && t.name.toLowerCase().contains(text.toLowerCase())))
						res.add(new UserSuggestion(t, t.screen_name, t.name, text, mSearchOptimizedPattern));
					else if (!textallowed.startsWith("_") && (t.screen_name.toLowerCase().contains(textallowed.toLowerCase()) || (t.name != null && t.name.toLowerCase().contains(textallowed.toLowerCase()))))
						res.add(new UserSuggestion(t, t.screen_name, t.name, textallowed, mSearchOptimizedPattern));
					else
						continue;
					i.remove();
				}
				Collections.sort(res.subList(div, res.size()));
				if (res.size() < LIST_COUNT) {
					div = res.size();
					i = map.values().iterator();
					while (i.hasNext()) {
						Tweeter t = i.next();
						String unspace = t.name == null ? null : StringTools.toHalfChar(t.name);
						Matcher m = t.name == null ? null : mSearchOptimizedPattern.matcher(StringTools.unHanja(unspace));
						if (m != null && !m.find())
							m = null;
						Matcher m2 = t.screen_name != null ? mSearchOptimizedPattern.matcher(t.screen_name) : null;
						if (m2 != null && !m2.find()) m2 = null;
						if (m != null || m2 != null)
							res.add(new UserSuggestion(t, t.screen_name, t.name, text, m2, m));
						else
							continue;
						i.remove();
					}
					Collections.sort(res.subList(div, res.size()));
					if (res.size() < LIST_COUNT) {
						div = res.size();
						i = map.values().iterator();
						while (i.hasNext() && res.size() < LIST_COUNT) {
							Tweeter t = i.next();
							res.add(new UserSuggestion(t, t.screen_name, t.name, text, null));
							i.remove();
						}
						Collections.sort(res.subList(div, res.size()));
					}
				}
			}
			if ((res.size() == 0 || !res.get(0).user.screen_name.equalsIgnoreCase(textallowed)))
				res.add(0, new UserSuggestion(null, textallowed, user, textallowed, mSearchOptimizedPattern));
			else
				res.add(1, new UserSuggestion(null, user, user, textallowed, mSearchOptimizedPattern));
			if (res.size() >= LIST_COUNT)
				res.subList(LIST_COUNT, res.size()).clear();
		} else {
			synchronized (UserSearchHistory.mList) {
				for (int i = 0, i_ = Math.min(LIST_COUNT, UserSearchHistory.mList.size()); i < i_; i++) {
					Tweeter t = UserSearchHistory.mList.get(i).tweeter;
					res.add(new UserSuggestion(t, t.screen_name, t.name, null, null));
				}
			}
		}
		return res;
	}

	public ArrayList<SearchSuggestion> makeSearchSuggestions(String text) {
		ArrayList<SearchSuggestion> res = new ArrayList<>();
		ArrayList<SavedSearch> list = new ArrayList<>(mSavedSearches);
		text = text.trim();
		if (!text.isEmpty()) {
			Iterator<SavedSearch> i = list.iterator();
			while (i.hasNext()) {
				SavedSearch t = i.next();
				if (t.query.toLowerCase().startsWith(text.toLowerCase()))
					res.add(new SearchSuggestion(t.query, text, null));
				else
					continue;
				i.remove();
			}
			Collections.sort(res);
			if (res.size() < LIST_COUNT) {
				int div = res.size();
				i = list.iterator();
				while (i.hasNext()) {
					SavedSearch t = i.next();
					if (t.query.toLowerCase().contains(text.toLowerCase()))
						res.add(new SearchSuggestion(t.query, text, null));
					else
						continue;
					i.remove();
				}
				Collections.sort(res.subList(div, res.size()));
				if (res.size() < LIST_COUNT) {
					div = res.size();
					i = list.iterator();
					while (i.hasNext()) {
						SavedSearch t = i.next();
						String unspace = t.query == null ? null : StringTools.toHalfChar(t.query);
						Matcher m = t.query == null ? null : mSearchOptimizedPattern.matcher(StringTools.unHanja(unspace));
						if (m != null && !m.find())
							m = null;
						if (m != null)
							res.add(new SearchSuggestion(t.query, text, m));
						else
							continue;
						i.remove();
					}
					Collections.sort(res.subList(div, res.size()));
					if (res.size() < LIST_COUNT) {
						div = res.size();
						i = list.iterator();
						while (i.hasNext() && res.size() < LIST_COUNT) {
							SavedSearch t = i.next();
							res.add(new SearchSuggestion(t.query, text, null));
							i.remove();
						}
						Collections.sort(res.subList(div, res.size()));
					}
				}
			}
			if (res.size() == 0 || !res.get(0).text.equalsIgnoreCase(text))
				res.add(0, new SearchSuggestion(text, text, null));
			if (res.size() >= LIST_COUNT)
				res.subList(LIST_COUNT, res.size()).clear();
		} else {
			synchronized (TweetSearchHistory.mList) {
				for (int i = 0, i_ = Math.min(5, TweetSearchHistory.mList.size()); i < i_; i++)
					res.add(new SearchSuggestion(TweetSearchHistory.mList.get(i).keyword, null, null));
			}
			for (SavedSearch s : list) {
				SearchSuggestion g = new SearchSuggestion(s.query, null, null);
				if (!res.contains(g))
					res.add(g);
			}
		}
		return res;
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus) {
		if (mQuickSearchMode)
			return;
		if (hasFocus)
			show();
		else
			hide();
	}

	@Override
	public void onImageCacheReady(ImageCache cache) {
		mImageCache = cache;
		mUserAdapter.notifyDataSetChanged();
	}

	private void openUser(long user_id, String screen_name) {
		Page.templatePageUser(user_id, screen_name, (MainActivity) getActivity());
		mSearchView.setIconified(true);
		MenuItemCompat.collapseActionView(mMenuItem);
		hide();
	}

	public static class UserSearchHistory implements Parcelable {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<UserSearchHistory> CREATOR = new Parcelable.Creator<UserSearchHistory>() {
			@Override
			public UserSearchHistory createFromParcel(Parcel in) {
				return new UserSearchHistory(in);
			}

			@Override
			public UserSearchHistory[] newArray(int size) {
				return new UserSearchHistory[size];
			}
		};
		private static final ArrayList<UserSearchHistory> mList = new ArrayList<>();
		private static final HashMap<Tweeter, UserSearchHistory> mMap = new HashMap<>();
		public final Tweeter tweeter;
		public long lastSearch;
		public int searchCount;

		protected UserSearchHistory(Tweeter t) {
			tweeter = t;
			lastSearch = System.currentTimeMillis();
			searchCount++;
		}

		protected UserSearchHistory(Parcel in) {
			tweeter = (Tweeter) in.readValue(Tweeter.class.getClassLoader());
			lastSearch = in.readLong();
			searchCount = in.readInt();
		}

		public static void prepare(Parcel p) {
			synchronized (mList) {
				mList.clear();
				mMap.clear();
				p.readTypedList(mList, CREATOR);
				for (UserSearchHistory h : mList)
					mMap.put(h.tweeter, h);
			}
		}

		public static void save(Parcel p) {
			synchronized (mList) {
				p.writeTypedList(mList);
			}
		}

		public static void increase(Tweeter t) {
			synchronized (mList) {
				if (mMap.get(t) != null)
					mMap.get(t).increaseCount();
				else {
					UserSearchHistory h = new UserSearchHistory(t);
					mList.add(h);
					mMap.put(t, h);
				}
				while (mList.size() > 30) {
					mMap.remove(mList.remove(mList.size() - 1).tweeter);
				}
			}
		}

		public void increaseCount() {
			lastSearch = System.currentTimeMillis();
			searchCount++;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeValue(tweeter);
			dest.writeLong(lastSearch);
			dest.writeInt(searchCount);
		}
	}

	public static class TweetSearchHistory implements Parcelable, Comparable<TweetSearchHistory> {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<TweetSearchHistory> CREATOR = new Parcelable.Creator<TweetSearchHistory>() {
			@Override
			public TweetSearchHistory createFromParcel(Parcel in) {
				return new TweetSearchHistory(in);
			}

			@Override
			public TweetSearchHistory[] newArray(int size) {
				return new TweetSearchHistory[size];
			}
		};
		private static final ArrayList<TweetSearchHistory> mList = new ArrayList<>();
		private static final HashMap<String, TweetSearchHistory> mMap = new HashMap<>();
		public final String keyword;
		public long lastSearch;
		public int searchCount;

		protected TweetSearchHistory(String t) {
			keyword = t;
			lastSearch = System.currentTimeMillis();
			searchCount++;
		}

		protected TweetSearchHistory(Parcel in) {
			keyword = in.readString();
			lastSearch = in.readLong();
			searchCount = in.readInt();
		}

		public static void prepare(Parcel p) {
			synchronized (mList) {
				mList.clear();
				mMap.clear();
				p.readTypedList(mList, CREATOR);
				for (TweetSearchHistory h : mList)
					mMap.put(h.keyword.toLowerCase(), h);
			}
		}

		public static void save(Parcel p) {
			synchronized (mList) {
				p.writeTypedList(mList);
			}
		}

		public static void increase(String text) {
			synchronized (mList) {
				String t = text.toLowerCase();
				if (mMap.get(t) != null)
					mMap.get(t).increaseCount();
				else {
					TweetSearchHistory h = new TweetSearchHistory(text);
					mList.add(h);
					mMap.put(t, h);
				}
				Collections.sort(mList, Collections.reverseOrder());
				while (mList.size() > 30) {
					mMap.remove(mList.remove(mList.size() - 1).keyword);
				}
			}
		}

		protected void increaseCount() {
			lastSearch = System.currentTimeMillis();
			searchCount++;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(keyword);
			dest.writeLong(lastSearch);
			dest.writeInt(searchCount);
		}

		@Override
		public int compareTo(@NonNull TweetSearchHistory another) {
			if (lastSearch > another.lastSearch)
				return 1;
			if (lastSearch == another.lastSearch)
				return 0;
			return -1;
		}
	}

	private class ListCreator extends AsyncTask<String, Object, String> {
		ArrayList<UserSuggestion> userSuggestions;
		ArrayList<SearchSuggestion> searchSuggestions;

		@Override
		protected String doInBackground(String... params) {
			userSuggestions = makeUserSuggestions(params[0]);
			searchSuggestions = makeSearchSuggestions(params[0]);
			return params[0];
		}

		@Override
		protected void onPostExecute(String o) {
			if (!mInputText.equals(o))
				return;
			mUserList.clear();
			mSearchList.clear();
			mUserList.addAll(userSuggestions);
			mSearchList.addAll(searchSuggestions);
			mUserAdapter.notifyDataSetChanged();
			mSearchAdapter.notifyDataSetChanged();
		}
	}

	private class SearchLoader extends AsyncTask<Void, TwitterEngine.RequestException, ArrayList<SavedSearch>> {
		private boolean firstLoad;

		public SearchLoader() {
			super();
		}

		@Override
		protected ArrayList<SavedSearch> doInBackground(Void... params) {
			try {
				firstLoad = params != null && params.length == 1;
				if (mSavedSearches.isEmpty()) {
					ArrayList<SavedSearch> ret = new ArrayList<>();
					for (int i = 0, i_ = mSearchPreferences.getInt("SavedSearch.length", 0); i < i_; i++)
						ret.add(new SavedSearch("SavedSearch." + i, mSearchPreferences));
					if (!ret.isEmpty())
						return ret;
				}
				if (firstLoad)
					return null;
				return ((MainActivity) getActivity()).getDrawerFragment().getCurrentUser().getSavedSearches();
			} catch (TwitterEngine.RequestException e) {
				e.printStackTrace();
				publishProgress(e);
				return null;
			}
		}

		@Override
		protected void onProgressUpdate(TwitterEngine.RequestException... values) {
		}

		@Override
		protected void onPostExecute(ArrayList<SavedSearch> ret) {
			if (firstLoad) {
				if (ret == null)
					return;
				mSavedSearches.clear();
				mSavedSearches.addAll(ret);
				return;
			}
			mViewSearchRefresher.setRefreshing(false);
			if (ret == null) {
				return;
			}
			if (!ret.equals(mSavedSearches)) {
				SharedPreferences.Editor edit = mSearchPreferences.edit();
				edit.putInt("SavedSearch.length", ret.size());
				for (int i = 0; i < ret.size(); i++)
					ret.get(i).writeToPreferences("SavedSearch." + i, edit);
				edit.apply();
			}
			mSavedSearches.clear();
			mSavedSearches.addAll(ret);
			if (mViewFragment.getVisibility() != View.VISIBLE)
				return;
			if (mTaskListCreator != null)
				mTaskListCreator.cancel(true);
			mTaskListCreator = new ListCreator();
			mTaskListCreator.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mInputText);
		}
	}

	private class UserSearcher extends AsyncTask<String, Tweeter, ArrayList<Tweeter>> {
		String query;
		ArrayList<Tweeter> u;

		@Override
		protected ArrayList<Tweeter> doInBackground(String... params) {
			query = params[0];
			TwitterEngine currentUser = ((MainActivity) getActivity()).getDrawerFragment().getCurrentUser();
			try {
				if (params.length >= 2 && !Tweeter.getAvailableTweeters().containsKey(params[1].toLowerCase())) {
					u = currentUser.lookupUser(params[1]);
					if (u != null && !u.isEmpty()) {
						publishProgress(u.toArray(new Tweeter[u.size()]));
						return null;
					}
				}
				return currentUser.searchUser(query, 100, 0);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		protected void onProgressUpdate(Tweeter... values) {
			for (Tweeter u : values) {
				mUserList.add(0, new UserSuggestion(u, u.screen_name, u.name, query, mSearchOptimizedPattern));
			}
			mUserAdapter.notifyDataSetChanged();
		}

		@Override
		protected void onPostExecute(ArrayList<Tweeter> tweeters) {
			if (!query.equals(mInputText))
				return;
			if (u != null && !u.isEmpty()) {
				UserSearchHistory.increase(u.get(0));
				saveHistory();
				openUser(u.get(0).user_id, u.get(0).screen_name);
				return;
			}
			if (mTaskListCreator != null)
				mTaskListCreator.cancel(true);
			mTaskListCreator = new ListCreator();
			mTaskListCreator.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, query);
		}
	}

	public class SuggestedUserAdapter extends RecyclerView.Adapter<SuggestedUserAdapter.ViewHolder> implements SwipeRefreshLayout.OnRefreshListener {

		public SuggestedUserAdapter() {
			setHasStableIds(true);
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_suggestion_user, parent, false));
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			holder.bindViewHolder(position);
		}

		@Override
		public long getItemId(int position) {
			if (mUserList.get(position).user == null)
				return 0;
			return mUserList.get(position).user.user_id;
		}

		@Override
		public int getItemCount() {
			return mUserList.size();
		}

		@Override
		public void onRefresh() {
			mViewUserRefresher.setRefreshing(false);
		}

		public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
			ImageView imageView;
			TextView userId, userName;
			ProgressBar progress;

			public ViewHolder(View v) {
				super(v);
				imageView = (ImageView) v.findViewById(R.id.item_image);
				progress = (ProgressBar) v.findViewById(R.id.progress);
				userId = (TextView) v.findViewById(R.id.user_id);
				userName = (TextView) v.findViewById(R.id.user_name);
				v.setOnClickListener(this);
			}

			public void bindViewHolder(int position) {
				if (mUserList.get(position).user == null) {
					if (mUserList.get(position).inProgress) {
						progress.setVisibility(View.VISIBLE);
						imageView.setVisibility(View.GONE);
					} else {
						progress.setVisibility(View.GONE);
						imageView.setVisibility(View.VISIBLE);
						mImageCache.assignImageView(imageView, null, null);
						imageView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_action_search));
					}
				} else {
					progress.setVisibility(View.GONE);
					imageView.setVisibility(View.VISIBLE);
					mImageCache.assignImageView(imageView, mUserList.get(position).user.getProfileImageUrl(), null);
				}
				userId.setText(mUserList.get(position).displayedUserId);
				userName.setText(mUserList.get(position).displayedUserName == null ? "" : mUserList.get(position).displayedUserName);
			}

			@Override
			public void onClick(View v) {
				int position = getAdapterPosition();
				if (position < 0 || position >= getItemCount())
					return;
				UserSuggestion s = mUserList.get(position);
				if (s.user == null) {
					s.inProgress = true;
					mUserAdapter.notifyDataSetChanged();
					if (mTaskUserSearcher != null)
						mTaskUserSearcher.cancel(true);
					mTaskUserSearcher = new UserSearcher();
					if (position == 0)
						mTaskUserSearcher.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, s.name, s.distanceString);
					else
						mTaskUserSearcher.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, s.name);
					return;
				}
				UserSearchHistory.increase(s.user);
				saveHistory();
				openUser(s.user.user_id, s.user.screen_name);
			}
		}
	}

	public class SuggestedSearchAdapter extends RecyclerView.Adapter<SuggestedSearchAdapter.ViewHolder> implements SwipeRefreshLayout.OnRefreshListener {

		public SuggestedSearchAdapter() {
			setHasStableIds(true);
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			return new ViewHolder(getActivity().getLayoutInflater().inflate(R.layout.row_suggestion_search, parent, false));
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			holder.bindViewHolder(position);
		}

		@Override
		public long getItemId(int position) {
			return mSearchList.get(position).text.hashCode();
		}

		@Override
		public int getItemCount() {
			return mSearchList.size();
		}

		@Override
		public void onRefresh() {
			new SearchLoader().execute();
		}

		public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
			TextView textView;

			public ViewHolder(View v) {
				super(v);
				textView = (TextView) v.findViewById(R.id.item_name);
				v.setOnClickListener(this);
			}

			public void bindViewHolder(int position) {
				textView.setText(mSearchList.get(position).displayText);
			}

			@Override
			public void onClick(View v) {
				int position = getAdapterPosition();
				if (position < 0 || position >= getItemCount())
					return;
				SearchSuggestion s = mSearchList.get(position);
				TweetSearchHistory.increase(s.text);
				saveHistory();
				Page.templatePageSearch(s.text, (MainActivity) getActivity());
				mSearchView.setIconified(true);
				MenuItemCompat.collapseActionView(mMenuItem);
				hide();
			}
		}
	}

	public class UserSuggestion implements Comparable<UserSuggestion> {

		final Tweeter user;
		final String id, name;
		final Spannable displayedUserId, displayedUserName;
		final String distanceString;
		final int distance;
		boolean inProgress;

		UserSuggestion(Tweeter user, String id, String name, String distanceFind, Pattern finder) {
			Matcher idMatch = finder == null ? null : finder.matcher(id);
			Matcher nameMatch = finder == null || name == null ? null : finder.matcher(name);
			if (idMatch != null && !idMatch.find()) idMatch = null;
			if (nameMatch != null && !nameMatch.find()) nameMatch = null;
			this.user = user;
			this.id = id;
			this.name = name;
			int dist = distanceFind == null ? 0 : StringUtils.getLevenshteinDistance(distanceFind, id);
			distanceString = distanceFind;
			displayedUserId = SpannableStringBuilder.valueOf(id);
			if (idMatch != null)
				do {
					for (int i = 1; i <= idMatch.groupCount(); i++)
						if (idMatch.end(i) - idMatch.start(i) > 0)
							displayedUserId.setSpan(new ForegroundColorSpan(ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent)), idMatch.start(i), idMatch.end(i), 0);
				} while (idMatch.find());
			if (name != null) {
				dist = distanceFind == null ? 0 : Math.min(dist, StringUtils.getLevenshteinDistance(distanceFind, name));
				displayedUserName = SpannableStringBuilder.valueOf(name);
				if (nameMatch != null)
					do {
						for (int i = 1; i <= nameMatch.groupCount(); i++)
							if (nameMatch.end(i) - nameMatch.start(i) > 0)
								displayedUserName.setSpan(new ForegroundColorSpan(ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent)), nameMatch.start(i), nameMatch.end(i), 0);
					} while (nameMatch.find());
			} else
				displayedUserName = null;
			this.distance = dist;
		}

		UserSuggestion(Tweeter user, String id, String name, String distanceFind, Matcher idMatch, Matcher nameMatch) {
			this.user = user;
			this.id = id;
			this.name = name;
			int dist = distanceFind == null ? 0 : StringUtils.getLevenshteinDistance(distanceFind, id);
			displayedUserId = SpannableStringBuilder.valueOf(id);
			distanceString = distanceFind;
			if (idMatch != null)
				do {
					for (int i = 1; i <= idMatch.groupCount(); i++)
						if (idMatch.end(i) - idMatch.start(i) > 0)
							displayedUserId.setSpan(new ForegroundColorSpan(ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent)), idMatch.start(i), idMatch.end(i), 0);
				} while (idMatch.find());
			if (name != null) {
				dist = distanceFind == null ? 0 : Math.min(dist, StringUtils.getLevenshteinDistance(distanceFind, name));
				displayedUserName = SpannableStringBuilder.valueOf(name);
				if (nameMatch != null)
					do {
						for (int i = 1; i <= nameMatch.groupCount(); i++)
							if (nameMatch.end(i) - nameMatch.start(i) > 0)
								displayedUserName.setSpan(new ForegroundColorSpan(ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent)), nameMatch.start(i), nameMatch.end(i), 0);
					} while (nameMatch.find());
			} else
				displayedUserName = null;
			this.distance = dist;
		}

		@Override
		public int compareTo(@NonNull UserSuggestion another) {
			int res;
			if ((res = distance - another.distance) != 0)
				return res;
			if ((res = id.compareTo(another.id)) != 0)
				return res;
			if ((res = name.compareTo(another.name)) != 0)
				return res;
			return 0;
		}

		@Override
		public int hashCode() {
			return user == null ? id.hashCode() : user.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof UserSuggestion && ((user == null && ((UserSuggestion) o).user == null) || (user != null && user.equals(((UserSuggestion) o).user)));
		}
	}

	public class SearchSuggestion implements Comparable<SearchSuggestion> {
		final String text;
		final Spannable displayText;
		final int distance;

		SearchSuggestion(String text, String distanceString, Matcher matcher) {
			this.text = text;
			displayText = SpannableStringBuilder.valueOf(text);
			if (distanceString != null) {
				this.distance = StringUtils.getLevenshteinDistance(distanceString.toLowerCase(), text.toLowerCase());
				int sPos = text.toLowerCase().indexOf(distanceString.toLowerCase());
				if (sPos != -1 && distanceString.length() > 0)
					displayText.setSpan(new ForegroundColorSpan(mColorAccent), sPos, sPos + distanceString.length(), 0);
			} else
				distance = 0;
			if (matcher != null)
				do {
					for (int i = 1; i <= matcher.groupCount(); i++)
						if (matcher.end(i) - matcher.start(i) > 0)
							displayText.setSpan(new ForegroundColorSpan(ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent)), matcher.start(i), matcher.end(i), 0);
				} while (matcher.find());
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof SearchSuggestion && ((SearchSuggestion) o).text.equals(text);
		}

		@Override
		public int hashCode() {
			return text.hashCode();
		}

		@Override
		public int compareTo(@NonNull SearchSuggestion another) {
			int res = distance - another.distance;
			if (res != 0)
				return res;
			res = text.compareTo(another.text);
			if (res != 0)
				return res;
			return 0;
		}
	}

}
