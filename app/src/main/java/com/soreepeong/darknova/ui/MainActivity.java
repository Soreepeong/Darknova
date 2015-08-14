package com.soreepeong.darknova.ui;

import android.accounts.AccountManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.services.DarknovaService;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.twitter.TwitterStreamServiceReceiver;
import com.soreepeong.darknova.ui.fragments.NavigationDrawerFragment;
import com.soreepeong.darknova.ui.fragments.PageFragment;
import com.soreepeong.darknova.ui.fragments.SearchSuggestionFragment;
import com.soreepeong.darknova.ui.fragments.SortableFragmentStatePagerAdapter;
import com.soreepeong.darknova.ui.fragments.TemplateTweetEditorFragment;

import java.util.ArrayList;

/**
 * Main Activity.
 * Consists of {@see NavigationDrawerFragment} on start side, {@see NewTemplateTweetFragment} on bottom, {@see PageFragment} on {@see ViewPager}, {@see SearchSuggestionFragment} on top
 *
 * @author Soreepeong
 */
public class MainActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener, NavigationDrawerFragment.NavigationDrawerCallbacks, View.OnClickListener, TemplateTweetEditorFragment.OnNewTweetVisibilityChangedListener, Page.OnPageListChangedListener, MultiContentFragmentActivity, View.OnLongClickListener {


	private ViewPager mPager;
	private TimelineFragmentPagerAdapter mPagerAdapter;
	private NavigationDrawerFragment mNavigationDrawerFragment;
	private TemplateTweetEditorFragment mTemplateTweetEditorFragment;
	private SearchSuggestionFragment mSuggestionFragment;
	private Toolbar mToolbar;
	private ActionBar mActionBar;
	private View mNewTweetOpener;
	private int mLastViewPagerPage;
	private Button mViewOpenDrawerDragTargetButton;

	private boolean mDataRestored;
	private SparseArray<MenuItem> mMenuItems;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			for (int i = 0, length = savedInstanceState.getInt("temppage.length"); i < length; i++)
				Page.addIfNoExist((Page) savedInstanceState.getParcelable("temppage." + i));
		}

		super.onCreate(savedInstanceState);
		startService(new Intent(this, DarknovaService.class));
		setContentView(R.layout.activity_main);

		mNewTweetOpener = findViewById(R.id.new_tweet_opener);
		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		mViewOpenDrawerDragTargetButton = (Button) findViewById(R.id.drag_action_open_drawer);

		setSupportActionBar(mToolbar);
		mActionBar = getSupportActionBar();

		mNewTweetOpener.setOnClickListener(this);
		mNewTweetOpener.setOnLongClickListener(this);
		mViewOpenDrawerDragTargetButton.setOnClickListener(this);
		mToolbar.setOnClickListener(this);


		mPagerAdapter = new TimelineFragmentPagerAdapter(getSupportFragmentManager());
		mPager = (ViewPager) findViewById(R.id.pager);
		mPager.setAdapter(mPagerAdapter);
		mPager.addOnPageChangeListener(this);
		mPager.setOffscreenPageLimit(2);

		mNavigationDrawerFragment = (NavigationDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_drawer);
		mTemplateTweetEditorFragment = (TemplateTweetEditorFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_template_tweet);
		mSuggestionFragment = (SearchSuggestionFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_search_suggestions);

		mTemplateTweetEditorFragment.setOnNewTweetVisibilityChangedListener(this);
		if (mTemplateTweetEditorFragment.isNewTweetVisible())
			mNewTweetOpener.setVisibility(View.GONE);

		if (TwitterStreamServiceReceiver.getBoundService() != null)
			TwitterStreamServiceReceiver.sendServiceMessage(Message.obtain(null, DarknovaService.MESSAGE_STREAM_QUIT_CANCEL));
		else
			TwitterStreamServiceReceiver.init(this);

		Page.addOnPageChangedListener(this);

		handleIntent(getIntent());

		if (TwitterEngine.getAll().isEmpty())
			AccountManager.get(this).addAccount(getString(R.string.account_type), "default", null, null, this, null, null);
	}

	/**
	 *
	 */
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		mDataRestored = true;
		super.onPostCreate(savedInstanceState);
		mNavigationDrawerFragment.setup(R.id.fragment_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), mToolbar);
		if (Page.size() > 0)
			onPageSelected(mNavigationDrawerFragment.getCurrentPage());
	}

	@Override
	protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		mDataRestored = true;
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
		// TODO Handle Twitter links
	}

	@Override
	protected void onDestroy() {
		Page.removeOnPageChangedListener(this);
		TwitterStreamServiceReceiver.sendServiceMessage(Message.obtain(null, DarknovaService.MESSAGE_STREAM_QUIT_REGISTER));
		super.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		for (int i = Page.getCountNonTemporary(); i < Page.size(); i++) {
			outState.putParcelable("temppage." + (i - Page.getCountNonTemporary()), Page.get(i));
		}
		outState.putInt("temppage.length", Page.size() - Page.getCountNonTemporary());
		super.onSaveInstanceState(outState);
	}

	public NavigationDrawerFragment getDrawerFragment() {
		return mNavigationDrawerFragment;
	}

	@Override
	public void onPageListChanged() {
		mPagerAdapter.notifyDataSetChanged();
		invalidateOptionsMenu();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main_toolbar, menu);
		mMenuItems = new SparseArray<>();
		for (int i = menu.size() - 1; i >= 0; i--)
			mMenuItems.put(menu.getItem(i).getItemId(), menu.getItem(i));
		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
		searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		mSuggestionFragment.setSearchUi(searchView, menu.findItem(R.id.action_search));
		invalidateOptionsMenu();
		return true;
	}

	@Override
	public void invalidateOptionsMenu() {
		if (mMenuItems == null) {
			super.invalidateOptionsMenu();
			return;
		}
		int currentItem = mPager.getCurrentItem();
		boolean selected = false;
		if (getFragmentAt(currentItem) != null)
			selected = getFragmentAt(currentItem).isSomethingSelected();
		mMenuItems.get(R.id.action_clear_selection).setVisible(selected);
		mMenuItems.get(R.id.action_close_page).setVisible(!selected && mPager.getCurrentItem() >= Page.getCountNonTemporary() && Page.size() > 0);
	}

	public PageFragment getCurrentPage() {
		return getFragmentAt(mPager.getCurrentItem());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_settings: {
				return true;
			}
			case R.id.action_clean: {
				if (Page.size() > 0)
					getCurrentPage().onCompleteRefresh();
				return true;
			}
			case R.id.action_clear_selection: {
				if (Page.size() > 0)
					getCurrentPage().clearSelection();
				return true;
			}
			case R.id.action_search: {
				return true;
			}
			case R.id.action_close_page: {
				Page rmItem = Page.remove(mPager.getCurrentItem());
				if (rmItem != null) {
					int newIndex = rmItem.getParentPageIndex();
					if (newIndex != -1)
						selectPage(newIndex);
				}
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	public SearchSuggestionFragment getSearchSuggestionFragment() {
		return mSuggestionFragment;
	}

	@Override
	public void onBackPressed() {
		boolean selected = false;
		int currentItem = mPager.getCurrentItem();
		if (getFragmentAt(currentItem) != null)
			selected = getFragmentAt(currentItem).isSomethingSelected();
		if (mNavigationDrawerFragment.isDrawerOpen())
			mNavigationDrawerFragment.closeDrawer();
		else if (mTemplateTweetEditorFragment.switchViews(null))
			return;
		else if (mTemplateTweetEditorFragment.isNewTweetVisible())
			mTemplateTweetEditorFragment.hideNewTweet();
		else if (selected)
			getCurrentPage().clearSelection();
		else if (currentItem >= Page.getCountNonTemporary() && Page.size() > 0) {
			Page rmItem = Page.remove(currentItem);
			if (rmItem != null) {
				int newIndex = rmItem.getParentPageIndex();
				if (newIndex != -1)
					selectPage(newIndex);
			}
		} else
			super.onBackPressed();
	}

	@Override
	public void hideContents() {
		if (mPager.getVisibility() == View.GONE) return;
		mPager.setVisibility(View.GONE);
		mPager.setAdapter(null);
	}

	@Override
	public void showContents() {
		if (mPager.getVisibility() == View.VISIBLE) return;
		mPager.setVisibility(View.VISIBLE);
		mPager.setAdapter(mPagerAdapter);
		mPager.setCurrentItem(mNavigationDrawerFragment.getCurrentPage(), false);
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
		if (getFragmentAt(position) != null)
			getFragmentAt(position).loadBackground();
		if (positionOffset > 0)
			if (getFragmentAt(position + 1) != null)
				getFragmentAt(position + 1).loadBackground();
	}

	public void selectPage(int index) {
		mPager.setCurrentItem(index, false);
		onPageSelected(index);
	}

	@Override
	public void onPageSelected(int position) {
		mSuggestionFragment.hideAndIconify();
		boolean changed = mLastViewPagerPage != position;
		if (changed && getFragmentAt(mLastViewPagerPage) != null)
			getFragmentAt(mLastViewPagerPage).onPageLeave();
		mNavigationDrawerFragment.onPageSelected(position);
		mLastViewPagerPage = position;
		mActionBar.setTitle(mPagerAdapter.getPageTitle(position));
		mActionBar.setSubtitle(mPagerAdapter.getPageSubtitle(position));

		if(changed)
			getFragmentAt(position).onPageEnter();

		invalidateOptionsMenu();
	}

	public TemplateTweetEditorFragment getNewTweetFragment() {
		return mTemplateTweetEditorFragment;
	}

	@Override
	public void onPageScrollStateChanged(int state) {
	}

	@Override
	public void onNavigationDrawerItemSelected(int position) {
		mPager.setCurrentItem(position, true);
	}

	@Override
	public void onNavigationDrawerItemInitialized(int position) {
		mPager.setCurrentItem(position, false);
	}

	@Override
	public void onClick(View v) {
		if (v.equals(mNewTweetOpener))
			startActivity(new Intent(this, TemplateTweetActivity.class));
		else if (v.equals(mToolbar) && Page.size() > 0) {
			if (getFragmentAt(mPager.getCurrentItem()) != null)
				getFragmentAt(mPager.getCurrentItem()).scrollToTop();
		} else if (v.equals(mViewOpenDrawerDragTargetButton)) {
			mNavigationDrawerFragment.openDrawer();
		}
	}

	public PageFragment getFragmentAt(int position) {
		if (!mDataRestored)
			throw new RuntimeException("this function must be called AFTER onPostCreate");
		if (position >= Page.size())
			return null;
		return (PageFragment) mPagerAdapter.instantiateItem(mPager, position);
	}

	@Override
	public void onNewTweetVisibilityChanged(boolean visible) {
		if (!visible) {
			if (mNewTweetOpener.getVisibility() == View.VISIBLE) return;
			mNewTweetOpener.startAnimation(AnimationUtils.loadAnimation(this, R.anim.show_upward));
			mNewTweetOpener.setVisibility(View.VISIBLE);
		} else {
			if (mNewTweetOpener.getVisibility() != View.VISIBLE) return;
			ResTools.hideWithAnimation(mNewTweetOpener, R.anim.hide_downward, true);
		}
	}

	@Override
	public boolean onLongClick(View v) {
		if (v.equals(mNewTweetOpener)) {
			mTemplateTweetEditorFragment.showNewTweet();
			return true;
		}
		return false;
	}

	public class TimelineFragmentPagerAdapter extends SortableFragmentStatePagerAdapter {

		public TimelineFragmentPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			return PageFragment.newInstance(getCacheDir().getAbsolutePath(), Page.get(position));
		}

		@Override
		public int getItemPosition(Object object) {
			PageFragment f = (PageFragment) object;
			int pos = Page.indexOf(f.getRepresentingPage());
			if (pos == -1)
				return PagerAdapter.POSITION_NONE;
			return pos;
		}

		@Override
		public long getItemId(int position) {
			return Page.get(position).getId();
		}

		@Override
		public int getCount() {
			return Page.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return Page.get(position).name;
		}

		public String getPageSubtitle(int position) {
			StringBuilder sb = new StringBuilder("");
			ArrayList<TwitterEngine> mUsedEngines = new ArrayList<>();
			for (int i = 0; i < Page.get(position).elements.size(); i++) {
				TwitterEngine e = Page.get(position).elements.get(0).getTwitterEngine();
				if (mUsedEngines.contains(e)) continue;
				mUsedEngines.add(e);
				if (sb.length() > 0)
					sb.append(", ");
				sb.append(e.getScreenName());
			}
			return sb.toString();
		}
	}
}
