package com.soreepeong.darknova.ui.fragments;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.ui.MainActivity;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fragment that shows search suggestions
 *
 * @author Soreepeong
 */
public class SearchSuggestionFragment extends Fragment implements SearchView.OnQueryTextListener, View.OnFocusChangeListener, ImageCache.OnImageCacheReadyListener, AbsListView.OnItemClickListener, MenuItemCompat.OnActionExpandListener, View.OnClickListener{
	private static final int LIST_COUNT = 15;
	public static Comparator<UserSuggestion> compareUserSuggestionsAlphabetically = new Comparator<UserSuggestion>() {
		@Override
		public int compare(UserSuggestion lhs, UserSuggestion rhs) {
			int res;
			if ((res = lhs.id.compareTo(rhs.id)) != 0)
				return res;
			if ((res = lhs.name.compareTo(rhs.name)) != 0)
				return res;
			return 0;
		}
	};
	int mColorAccent;
	boolean mQuickSearchMode;
	String mInputText = "";
	private View mViewFragment;
	private ListView mViewUserList, mViewSearchList;
	private ImageButton mViewQuickSearch;
	private SuggestedUserAdapter mUserAdapter;
	private SuggestedSearchAdapter mSearchAdapter;
	private ArrayList<UserSuggestion> mUserList;
	private ArrayList<SearchSuggestion> mSearchList;
	private ImageCache mImageCache;
	private SearchView mSearchView;
	private MenuItem mMenuItem;
	private Pattern mSearchOptimizedPattern;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mViewFragment = inflater.inflate(R.layout.fragment_search_suggestion, container, false);
		mViewUserList = (ListView) mViewFragment.findViewById(R.id.user_suggestion);
		mViewSearchList = (ListView) mViewFragment.findViewById(R.id.search_suggestion);
		mViewQuickSearch = (ImageButton) inflater.inflate(R.layout.activity_main_search_btn, container, false);
		mUserList = new ArrayList<>();
		mSearchList = new ArrayList<>();
		mViewUserList.setAdapter(mUserAdapter = new SuggestedUserAdapter());
		mViewSearchList.setAdapter(mSearchAdapter = new SuggestedSearchAdapter());
		mViewUserList.setOnItemClickListener(this);
		mViewSearchList.setOnItemClickListener(this);
		mImageCache = ImageCache.getCache(getActivity(), this);
		mColorAccent = ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent);

		return mViewFragment;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mViewFragment = null;
		mViewUserList = null;
		mViewSearchList = null;
	}

	@Override
	public void onClick(View v) {
		if(v.equals(mViewQuickSearch)){
			setQuickSearchMode(!mQuickSearchMode);
			if(mQuickSearchMode){
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

	public void setQuickFilter(String text){
		if(!MenuItemCompat.isActionViewExpanded(mMenuItem))
			MenuItemCompat.expandActionView(mMenuItem);
		setQuickSearchMode(true);
		mSearchView.setQuery(text, false);
		mSearchView.requestFocus();
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		mInputText = newText;
		if(!mQuickSearchMode){
			mSearchOptimizedPattern = StringTools.getMaybePatternSearcher(newText);
			new ListCreator().execute(newText);
		}else{
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
		for(int i = 0; i < ll.getChildCount(); i++){
			if(ll.getChildAt(i).equals(submitImage)){
				mViewQuickSearch.setLayoutParams(old);
				if(mViewQuickSearch.getParent() != null)
					((ViewGroup)mViewQuickSearch.getParent()).removeView(mViewQuickSearch);
				ll.addView(mViewQuickSearch, i);
				break;
			}
		}
		mViewQuickSearch.setOnClickListener(this);

	}

	public void hideAndIconify(){
		if(mSearchView != null && !mSearchView.isIconified()){
			mSearchView.setIconified(true);
			mMenuItem.collapseActionView();
			hide();
		}
	}

	public void setQuickSearchMode(boolean mode){
		PageFragment page = ((MainActivity) getActivity()).getCurrentPage();
		if(mode && page == null)
			mode = false;
		mQuickSearchMode = mode;
		if(!mQuickSearchMode && !mSearchView.isIconified())
			show();
		else
			hide();
		if(!mode && page != null){
			page.setQuickFilter(null);
		}
	}

	public void show() {
		if(mViewFragment.getVisibility() == View.VISIBLE)
			return;
		mViewFragment.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.actionbar_show));
		mViewFragment.setVisibility(View.VISIBLE);
	}

	public void hide() {
		if (mViewFragment.getVisibility() != View.VISIBLE)
			return;
		ResTools.hideWithAnimation(getActivity(), mViewFragment, R.anim.newtweet_hide, true);
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
				if(t.screen_name.toLowerCase().startsWith(text.toLowerCase()) || (t.name != null && t.name.toLowerCase().startsWith(text.toLowerCase())))
					res.add(new UserSuggestion(t, t.screen_name, t.name, text, mSearchOptimizedPattern));
				else if(!textallowed.startsWith("_") && (t.screen_name.toLowerCase().startsWith(textallowed.toLowerCase()) || (t.name != null && t.name.toLowerCase().startsWith(textallowed.toLowerCase()))))
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
					if(t.screen_name.toLowerCase().contains(text.toLowerCase()) || (t.name != null && t.name.toLowerCase().contains(text.toLowerCase())))
						res.add(new UserSuggestion(t, t.screen_name, t.name, text, mSearchOptimizedPattern));
					else if(!textallowed.startsWith("_") && (t.screen_name.toLowerCase().contains(textallowed.toLowerCase()) || (t.name != null && t.name.toLowerCase().contains(textallowed.toLowerCase()))))
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
						if(m != null && !m.find())
							m = null;
						Matcher m2 = t.screen_name != null ? mSearchOptimizedPattern.matcher(t.screen_name) : null;
						if(m2 != null && !m2.find()) m2 = null;
						if(m != null || m2 != null)
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
		}else{
			// Users in input box
			// History x3
			// Bookmarked users
		}
		return res;
	}

	public ArrayList<SearchSuggestion> makeSearchSuggestions(String text) {
		ArrayList<SearchSuggestion> res = new ArrayList<>();
		text = text.trim();
		if(!text.isEmpty())
			res.add(new SearchSuggestion(text, text));
		// History x3
		// Bookmarked searches
		return res;
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus) {
		if(mQuickSearchMode)
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

	private void openUser(long user_id, String screen_name){
		Page.Builder builder = new Page.Builder(screen_name, R.drawable.ic_eyes);
		TwitterEngine currentUser = ((MainActivity) getActivity()).getDrawerFragment().getCurrentUser();
		builder.e().add(new Page.Element(currentUser, Page.Element.FUNCTION_USER_SINGLE, user_id, screen_name));
		builder.e().add(new Page.Element(currentUser, Page.Element.FUNCTION_USER_TIMELINE, user_id, screen_name));
		builder.setParentPage(((MainActivity) getActivity()).getCurrentPage().getRepresentingPage());
		Page p = builder.build();
		int index = Page.pages.indexOf(p);
		if(index == -1){
			index = Page.pages.size();
			Page.addPage(p);
			Page.broadcastPageChange();
		}
		((MainActivity) getActivity()).selectPage(index);
		mSearchView.setIconified(true);
		MenuItemCompat.collapseActionView(mMenuItem);
		hide();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (mViewUserList.equals(parent)) {
			UserSuggestion s = mUserList.get(position);
			if(s.user == null){
				s.inProgress = true;
				mUserAdapter.notifyDataSetChanged();
				if (position == 0)
					new UserSearcher().execute(s.name, s.distanceString);
				else
					new UserSearcher().execute(s.name);
				return;
			}
			openUser(s.user.user_id, s.user.screen_name);
		} else if (mViewSearchList.equals(parent)) {
			SearchSuggestion s = mSearchList.get(position);
			Page.Builder builder = new Page.Builder(s.text, R.drawable.ic_bigeyed);
			TwitterEngine currentUser = ((MainActivity) getActivity()).getDrawerFragment().getCurrentUser();
			builder.e().add(new Page.Element(currentUser, Page.Element.FUNCTION_SEARCH, 0, s.text));
			builder.setParentPage(((MainActivity) getActivity()).getCurrentPage().getRepresentingPage());
			Page p = builder.build();
			int index = Page.pages.indexOf(p);
			if(index == -1){
				index = Page.pages.size();
				Page.addPage(p);
				Page.broadcastPageChange();
			}
			((MainActivity) getActivity()).selectPage(index);
			mSearchView.setIconified(true);
			MenuItemCompat.collapseActionView(mMenuItem);
			hide();
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
			mUserList = userSuggestions;
			mSearchList = searchSuggestions;
			mUserAdapter.notifyDataSetChanged();
			mSearchAdapter.notifyDataSetChanged();
		}
	}

	private class UserSearcher extends AsyncTask<String, Tweeter, ArrayList<Tweeter>>{
		String query;
		ArrayList<Tweeter> u;

		@Override
		protected ArrayList<Tweeter> doInBackground(String... params) {
			query = params[0];
			TwitterEngine currentUser = ((MainActivity) getActivity()).getDrawerFragment().getCurrentUser();
			try{
				if (params.length >= 2 && !Tweeter.getAvailableTweeters().containsKey(params[1].toLowerCase())) {
					u = currentUser.lookupUser(params[1]);
					if(u != null && !u.isEmpty()){
						publishProgress(u.toArray(new Tweeter[u.size()]));
						return null;
					}
				}
				return currentUser.searchUser(query, 100, 0);
			}catch(Exception e){
				e.printStackTrace();
				return null;
			}
		}

		@Override
		protected void onProgressUpdate(Tweeter... values) {
			for(Tweeter u : values){
				mUserList.add(0, new UserSuggestion(u, u.screen_name, u.name, query, mSearchOptimizedPattern));
			}
			mUserAdapter.notifyDataSetChanged();
		}

		@Override
		protected void onPostExecute(ArrayList<Tweeter> tweeters) {
			if(!query.equals(mInputText))
				return;
			if(u != null && !u.isEmpty()){
				openUser(u.get(0).user_id, u.get(0).screen_name);
				return;
			}
			new ListCreator().execute(query);
			super.onPostExecute(tweeters);
		}
	}

	public class SuggestedUserAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return mUserList.size();
		}

		@Override
		public Object getItem(int position) {
			return mUserList.get(position);
		}

		@Override
		public long getItemId(int position) {
			if(mUserList.get(position).user == null)
				return 0;
			return mUserList.get(position).user.user_id;
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public View getView(int position, View v, ViewGroup parent) {
			ViewHolder vh;
			if (v == null)
				vh = new ViewHolder(v = getActivity().getLayoutInflater().inflate(R.layout.row_suggestion_user, parent, false));
			else
				vh = (ViewHolder) v.getTag();
			if (mUserList.get(position).user == null) {
				if(mUserList.get(position).inProgress){
					vh.progress.setVisibility(View.VISIBLE);
					vh.imageView.setVisibility(View.GONE);
				}else{
					vh.progress.setVisibility(View.GONE);
					vh.imageView.setVisibility(View.VISIBLE);
					mImageCache.assignImageView(vh.imageView, null, null);
					vh.imageView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_action_search));
				}
			} else {
				vh.progress.setVisibility(View.GONE);
				vh.imageView.setVisibility(View.VISIBLE);
				mImageCache.assignImageView(vh.imageView, mUserList.get(position).user.getProfileImageUrl(), null);
			}
			vh.userId.setText(mUserList.get(position).displayedUserId);
			vh.userName.setText(mUserList.get(position).displayedUserName == null ? "" : mUserList.get(position).displayedUserName);
			return v;
		}

		private class ViewHolder {
			ImageView imageView;
			TextView userId, userName;
			ProgressBar progress;

			public ViewHolder(View v) {
				imageView = (ImageView) v.findViewById(R.id.item_image);
				progress = (ProgressBar) v.findViewById(R.id.progress);
				userId = (TextView) v.findViewById(R.id.user_id);
				userName = (TextView) v.findViewById(R.id.user_name);
				v.setTag(this);
			}
		}
	}

	public class SuggestedSearchAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return mSearchList.size();
		}

		@Override
		public Object getItem(int position) {
			return mSearchList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return mSearchList.get(position).text.hashCode();
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public View getView(int position, View v, ViewGroup parent) {
			ViewHolder vh;
			if (v == null)
				vh = new ViewHolder(v = getActivity().getLayoutInflater().inflate(R.layout.row_suggestion_search, parent, false));
			else
				vh = (ViewHolder) v.getTag();
			vh.textView.setText(mSearchList.get(position).displayText);
			return v;
		}

		private class ViewHolder {
			TextView textView;

			public ViewHolder(View v) {
				textView = (TextView) v.findViewById(R.id.item_name);
				v.setTag(this);
			}
		}
	}

	public class UserSuggestion implements Comparable<UserSuggestion> {

		Tweeter user;
		String id, name;
		Spannable displayedUserId, displayedUserName;
		String distanceString;
		int distance;
		boolean inProgress;

		UserSuggestion(Tweeter user, String id, String name, String distanceFind, Pattern finder) {
			Matcher idMatch = finder==null ? null : finder.matcher(id);
			Matcher nameMatch = finder==null || name==null ? null : finder.matcher(name);
			if(idMatch != null && !idMatch.find()) idMatch = null;
			if(nameMatch != null && !nameMatch.find()) nameMatch = null;
			this.user = user;
			this.id=id; this.name=name;
			this.distance = StringUtils.getLevenshteinDistance(distanceFind, id);
			distanceString = distanceFind;
			displayedUserId = SpannableStringBuilder.valueOf(id);
			if(idMatch != null)
				do{
					for(int i = 1; i <= idMatch.groupCount(); i++)
						if(idMatch.end(i) - idMatch.start(i) > 0)
							displayedUserId.setSpan(new ForegroundColorSpan(ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent)), idMatch.start(i), idMatch.end(i), 0);
				}while(idMatch.find());
			if(name != null){
				this.distance = Math.min(this.distance, StringUtils.getLevenshteinDistance(distanceFind, name));
				displayedUserName = SpannableStringBuilder.valueOf(name);
				if(nameMatch != null)
					do{
						for(int i = 1; i <= nameMatch.groupCount(); i++)
							if(nameMatch.end(i) - nameMatch.start(i) > 0)
								displayedUserName.setSpan(new ForegroundColorSpan(ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent)), nameMatch.start(i), nameMatch.end(i), 0);
					}while(nameMatch.find());
			}
		}

		UserSuggestion(Tweeter user, String id, String name, String distanceFind, Matcher idMatch, Matcher nameMatch) {
			this.user = user;
			this.id=id; this.name=name;
			this.distance = StringUtils.getLevenshteinDistance(distanceFind, id);
			displayedUserId = SpannableStringBuilder.valueOf(id);
			distanceString = distanceFind;
			if(idMatch != null)
				do{
					for(int i = 1; i <= idMatch.groupCount(); i++)
						if(idMatch.end(i) - idMatch.start(i) > 0)
							displayedUserId.setSpan(new ForegroundColorSpan(ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent)), idMatch.start(i), idMatch.end(i), 0);
				}while(idMatch.find());
			if(name != null){
				this.distance = Math.min(this.distance, StringUtils.getLevenshteinDistance(distanceFind, name));
				displayedUserName = SpannableStringBuilder.valueOf(name);
				if(nameMatch != null)
					do{
						for(int i = 1; i <= nameMatch.groupCount(); i++)
							if(nameMatch.end(i) - nameMatch.start(i) > 0)
								displayedUserName.setSpan(new ForegroundColorSpan(ResTools.getColorByAttribute(getActivity(), R.attr.colorAccent)), nameMatch.start(i), nameMatch.end(i), 0);
					}while(nameMatch.find());
			}
		}

		@Override
		public int compareTo(@NonNull UserSuggestion another) {
			int res;
			if((res = distance - another.distance) != 0)
				return res;
			if((res = id.compareTo(another.id)) != 0)
				return res;
			if((res = name.compareTo(another.name)) != 0)
				return res;
			return 0;
		}
	}

	public class SearchSuggestion implements Comparable<SearchSuggestion>{

		String text;
		Spannable displayText;
		int distance;

		SearchSuggestion(String text, String distanceString) {
			this.text = text;
			this.distance = StringUtils.getLevenshteinDistance(distanceString.toLowerCase(), text.toLowerCase());
			displayText = SpannableStringBuilder.valueOf(text);
			int sPos = text.toLowerCase().indexOf(distanceString.toLowerCase());
			if(sPos != -1 && distanceString.length() > 0)
				displayText.setSpan(new ForegroundColorSpan(mColorAccent), sPos, sPos + distanceString.length(), 0);
		}

		@Override
		public int compareTo(@NonNull SearchSuggestion another) {
			int res = distance - another.distance;
			if(res != 0)
				return res;
			res = text.compareTo(another.text);
			if(res != 0)
				return res;
			return 0;
		}
	}
}
