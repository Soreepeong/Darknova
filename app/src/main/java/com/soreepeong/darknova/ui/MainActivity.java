package com.soreepeong.darknova.ui;

import android.accounts.AccountManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
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
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.services.DarknovaService;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.twitter.TwitterStreamServiceReceiver;
import com.soreepeong.darknova.ui.fragments.NavigationDrawerFragment;
import com.soreepeong.darknova.ui.fragments.NewTemplateTweetFragment;
import com.soreepeong.darknova.ui.fragments.PageFragment;
import com.soreepeong.darknova.ui.fragments.SearchSuggestionFragment;
import com.soreepeong.darknova.ui.fragments.SortableFragmentStatePagerAdapter;
import com.soreepeong.darknova.ui.fragments.TimelineFragment;

import java.util.ArrayList;

/**
 * Main Activity.
 * Consists of {@see NavigationDrawerFragment} on start side, {@see NewTemplateTweetFragment} on bottom, {@see PageFragment} on {@see ViewPager}, {@see SearchSuggestionFragment} on top
 *
 * @author Soreepeong
 */
public class MainActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener, NavigationDrawerFragment.NavigationDrawerCallbacks, View.OnClickListener, NewTemplateTweetFragment.OnNewTweetVisibilityChangedListener, ImageCache.OnImageCacheReadyListener, Page.OnPageListChangedListener {


	private ViewPager mPager;
	private TimelineFragmentPagerAdapter mPagerAdapter;
	private ImageCache mImageCache;
	private NavigationDrawerFragment mNavigationDrawerFragment;
	private NewTemplateTweetFragment mNewTemplateTweetFragment;
	private SearchSuggestionFragment mSuggestionFragment;
	private Toolbar mToolbar;
	private ActionBar mActionBar;
	private DrawerLayout mDrawer;
	private FloatingActionButton mNewTweetOpener;
	private int mLastViewPagerPage;
	private Button mViewOpenDrawerDragTargetButton;

	private SparseArray<MenuItem> mMenuItems;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		ArrayList<TwitterEngine> users = TwitterEngine.getTwitterEngines(this);
		if (savedInstanceState != null) {
			for (int i = 0, length = savedInstanceState.getInt("temppage.length"); i < length; i++) {
				Page p = savedInstanceState.getParcelable("temppage." + i);
				if (Page.pages.contains(p))
					return;
				Page.addPage(p);
			}
		}

		super.onCreate(savedInstanceState);
		startService(new Intent(this, DarknovaService.class));
		setContentView(R.layout.activity_main);
		mImageCache = ImageCache.getCache(this, this);

		mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		mNewTweetOpener = (FloatingActionButton) findViewById(R.id.new_tweet_opener);
		mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
		mViewOpenDrawerDragTargetButton = (Button) findViewById(R.id.drag_action_open_drawer);

		setSupportActionBar(mToolbar);
		mActionBar = getSupportActionBar();

		mNewTweetOpener.setOnClickListener(this);
		mViewOpenDrawerDragTargetButton.setOnClickListener(this);
		mToolbar.setOnClickListener(this);

		mNavigationDrawerFragment = (NavigationDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_drawer);
		mNewTemplateTweetFragment = (NewTemplateTweetFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_new_tweet);
		mSuggestionFragment = (SearchSuggestionFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_search_suggestions);

		mNavigationDrawerFragment.setup(R.id.fragment_drawer, mDrawer, mToolbar);
		mNewTemplateTweetFragment.setOnNewTweetVisibilityChangedListener(this);
		if (mNewTemplateTweetFragment.isNewTweetVisible())
			mNewTweetOpener.setVisibility(View.GONE);

		if (TwitterStreamServiceReceiver.getBoundService() != null)
			TwitterStreamServiceReceiver.sendServiceMessage(Message.obtain(null, DarknovaService.MESSAGE_STREAM_QUIT_CANCEL));
		else
			TwitterStreamServiceReceiver.init(this);

		mPagerAdapter = new TimelineFragmentPagerAdapter(getSupportFragmentManager());
		mPager = (ViewPager) findViewById(R.id.pager);
		mPager.setAdapter(mPagerAdapter);
		mPager.setOnPageChangeListener(this);
		mPager.setOffscreenPageLimit(6);

		Page.addOnPageChangedListener(this);
		Page.broadcastPageChange();

		handleIntent(getIntent());

		if (users.isEmpty())
			AccountManager.get(this).addAccount(getString(R.string.account_type), "default", null, null, this, null, null);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			String query = intent.getStringExtra(SearchManager.QUERY);
			// TODO Search
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		onPageSelected(mNavigationDrawerFragment.getCurrentPage());
	}

	@Override
	protected void onResume() {
		try {
			super.onResume();
		} catch (Exception e) {
			Intent i = new Intent();
			i.setClass(getApplicationContext(), MainActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
			// Show toast to the user
			Toast.makeText(getApplicationContext(), "Data lost due to excess use of other apps", Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onDestroy() {
		Page.removeOnPageChangedListener(this);
		TwitterStreamServiceReceiver.sendServiceMessage(Message.obtain(null, DarknovaService.MESSAGE_STREAM_QUIT_REGISTER));
		super.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		for (int i = Page.mSavedPageLength; i < Page.pages.size(); i++) {
			outState.putParcelable("temppage." + (i - Page.mSavedPageLength), Page.pages.get(i));
		}
		outState.putInt("temppage.length", Page.pages.size() - Page.mSavedPageLength);
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
		if (mPagerAdapter.mFragments.get(currentItem) != null)
			selected = mPagerAdapter.mFragments.get(currentItem).isSomethingSelected();
		mMenuItems.get(R.id.action_clear_selection).setVisible(selected);
		mMenuItems.get(R.id.action_close_page).setVisible(!selected && mPager.getCurrentItem() >= Page.mSavedPageLength);
	}

	public PageFragment getCurrentPage() {
		return mPagerAdapter.mFragments.get(mPager.getCurrentItem());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_settings: {
				return true;
			}
			case R.id.action_clean: {
				if (Page.pages.size() > 0)
					mPagerAdapter.mFragments.get(mPager.getCurrentItem()).onCompleteRefresh();
				return true;
			}
			case R.id.action_clear_selection: {
				if (Page.pages.size() > 0)
					mPagerAdapter.mFragments.get(mPager.getCurrentItem()).clearSelection();
				return true;
			}
			case R.id.action_search: {
				return true;
			}
			case R.id.action_close_page: {
				int newIndex = Page.removePage(mPager.getCurrentItem()).getParentPageIndex();
				Page.broadcastPageChange();
				if (newIndex != -1)
					mPager.setCurrentItem(newIndex, true);
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
		if (mNavigationDrawerFragment.isDrawerOpen())
			mNavigationDrawerFragment.closeDrawer();
		else if (mNewTemplateTweetFragment.isNewTweetVisible())
			mNewTemplateTweetFragment.hideNewTweet();
		else
			super.onBackPressed();
	}

	public void showActionBar() {
		if (mToolbar.getVisibility() == View.VISIBLE) return;
		mNewTemplateTweetFragment.showWithActionBar();
		mToolbar.setVisibility(View.VISIBLE);
		mActionBar.show();
		mToolbar.clearAnimation();
		mToolbar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.actionbar_show));
		for (Page p : Page.pages)
			if (p.mConnectedFragment != null)
				p.mConnectedFragment.onActionBarShown(mToolbar);
	}

	public void hideActionBar() {
		if (mToolbar.getVisibility() != View.VISIBLE) return;
		mNewTemplateTweetFragment.hideWithActionBar();
		ResTools.hideWithAnimation(this, mToolbar, R.anim.actionbar_hide, true);
		for (Page p : Page.pages)
			if (p.mConnectedFragment != null)
				p.mConnectedFragment.onActionBarHidden(mToolbar);
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
		if (mPagerAdapter.mFragments.get(position) != null)
			mPagerAdapter.mFragments.get(position).loadBackground();
		if (positionOffset > 0)
			if (mPagerAdapter.mFragments.get(position + 1) != null)
				mPagerAdapter.mFragments.get(position + 1).loadBackground();
	}

	public void selectPage(int index) {
		onPageSelected(index);
	}

	@Override
	public void onPageSelected(int position) {
		mSuggestionFragment.hideAndIconify();
		if (mPagerAdapter.mFragments.get(mLastViewPagerPage) != null)
			mPagerAdapter.mFragments.get(mLastViewPagerPage).onPageLeave();
		mNavigationDrawerFragment.onPageSelected(position);
		mLastViewPagerPage = position;
		mActionBar.setTitle(mPagerAdapter.getPageTitle(position));
		mActionBar.setSubtitle(mPagerAdapter.getPageSubtitle(position));

		mPagerAdapter.requireEnterPage = position;
		if (mPagerAdapter.mFragments.get(position) != null)
			mPagerAdapter.enterPage(position);

		invalidateOptionsMenu();
	}

	public NewTemplateTweetFragment getNewTweetFragment() {
		return mNewTemplateTweetFragment;
	}

	@Override
	public void onPageScrollStateChanged(int state) {
		if (state == ViewPager.SCROLL_STATE_DRAGGING) {
			showActionBar();

		}
	}

	@Override
	public void onNavigationDrawerItemSelected(int position) {
		if (mPager != null) {
			mPager.setCurrentItem(position, true);
		}
	}

	@Override
	public void onClick(View v) {
		if (v.equals(mNewTweetOpener))
			mNewTemplateTweetFragment.showNewTweet();
		else if (v.equals(mToolbar) && Page.pages.size() > 0) {
			if (mPagerAdapter.mFragments.get(mPager.getCurrentItem()) != null)
				mPagerAdapter.mFragments.get(mPager.getCurrentItem()).scrollToTop();
		} else if (v.equals(mViewOpenDrawerDragTargetButton))
			mNavigationDrawerFragment.openDrawer();
	}

	@Override
	public void onNewTweetVisibilityChanged(boolean visible) {
		if (!visible) {
			if (mNewTweetOpener.getVisibility() == View.VISIBLE) return;
			mNewTweetOpener.startAnimation(AnimationUtils.loadAnimation(this, R.anim.newtweet_show));
			mNewTweetOpener.setVisibility(View.VISIBLE);
		} else {
			if (mNewTweetOpener.getVisibility() != View.VISIBLE) return;
			ResTools.hideWithAnimation(this, mNewTweetOpener, R.anim.newtweet_hide, true);
		}
	}

	@Override
	public void onImageCacheReady(ImageCache cache) {
		mImageCache = cache;
	}

	public class TimelineFragmentPagerAdapter extends SortableFragmentStatePagerAdapter {

		public final SparseArray<PageFragment> mFragments = new SparseArray<>();
		int requireEnterPage = -1;

		public TimelineFragmentPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		public void enterPage(int position) {
			if (requireEnterPage != position)
				return;
			mFragments.get(position).onPageEnter();
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			TimelineFragment fragment = (TimelineFragment) super.instantiateItem(container, position);
			mFragments.put(position, fragment);
			enterPage(position);
			return fragment;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			super.destroyItem(container, position, object);
			if (mFragments.get(position) != null)
				mFragments.get(position).onPageLeave();
			mFragments.remove(position);
		}

		@Override
		public Fragment getItem(int position) {
			return TimelineFragment.newInstance(getCacheDir().getAbsolutePath(), Page.pages.get(position));
		}

		@Override
		public int getItemPosition(Object object) {
			PageFragment f = (PageFragment) object;
			int pos = Page.pages.indexOf(f.getRepresentingPage());
			int oldPos = mFragments.indexOfValue(f);
			if (pos == oldPos)
				return pos;
			if (oldPos >= 0) mFragments.remove(oldPos);
			if (pos == -1)
				return PagerAdapter.POSITION_NONE;
			mFragments.put(pos, f);
			return pos;
		}

		@Override
		public long getItemId(int position) {
			return Page.pages.get(position).getId();
		}

		@Override
		public int getCount() {
			return Page.pages.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return Page.pages.get(position).name;
		}

		public String getPageSubtitle(int position) {
			StringBuilder sb = new StringBuilder("");
			ArrayList<TwitterEngine> mUsedEngines = new ArrayList<>();
			for (int i = 0; i < Page.pages.get(position).elements.size(); i++) {
				TwitterEngine e = Page.pages.get(position).elements.get(0).getTwitterEngine(MainActivity.this);
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
