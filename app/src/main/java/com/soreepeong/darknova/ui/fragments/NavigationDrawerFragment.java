package com.soreepeong.darknova.ui.fragments;

import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.twitter.TwitterStreamServiceReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
public class NavigationDrawerFragment extends Fragment implements ImageCache.OnImageCacheReadyListener, TwitterEngine.OnUserlistChangedListener, TwitterStreamServiceReceiver.OnStreamTurnedListener, Page.OnPageListChangedListener, Handler.Callback {

	/**
	 * Remember the position of the selected item.
	 */
	private static final String STATE_SELECTED_PAGE = "selected_navigation_drawer_position";
	private static final String STATE_SELECTED_USER = "selected_navigation_drawer_user";

	/**
	 * Per the design guidelines, you should show the drawer on launch until the user manually
	 * expands it. This shared preference tracks this.
	 */
	private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";
	private static final int MESSAGE_SERVICE_TIMEOUT = 1;
	private static final int SERVICE_COMMUNICATION_TIMEOUT = 30000;
	private final ArrayList<TwitterEngine> mStreamPowerChangeWaitingEngines = new ArrayList<>();
	private final Handler mHandler = new Handler(this);
	/**
	 * A pointer to the current callbacks instance (the Activity).
	 */
	private NavigationDrawerCallbacks mCallbacks;
	/**
	 * Helper component that ties the action bar to the navigation drawer.
	 */
	private ActionBarDrawerToggle mActionBarDrawerToggle;
	private SharedPreferences mDefaultPreference;
	private DrawerLayout mDrawerLayout;
	private RecyclerView mDrawerList;
	private View mFragmentContainerView;
	private int mCurrentPage = 0;
	private boolean mFromSavedInstanceState;
	private boolean mUserLearnedDrawer;
	private TwitterEngine mCurrentUser;
	private long mCurrentUserId;
	private ImageCache mImageCache;
	private NavigationDrawerAdapter mPageAdapter;
	private RecyclerViewDragDropManager mRecyclerViewDragDropManager;

	private boolean mIsPageListing = true;
	private boolean mListEditMode = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Read in the flag indicating whether or not the user has demonstrated awareness of the
		// drawer. See PREF_USER_LEARNED_DRAWER for details.
		mDefaultPreference = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mUserLearnedDrawer = mDefaultPreference.getBoolean(PREF_USER_LEARNED_DRAWER, false);

		mCurrentPage = mDefaultPreference.getInt(STATE_SELECTED_PAGE, 0);
		mCurrentUserId = mDefaultPreference.getLong(STATE_SELECTED_USER, 0);

		if (savedInstanceState != null) {
			mCurrentPage = savedInstanceState.getInt(STATE_SELECTED_PAGE);
			mCurrentUserId = savedInstanceState.getLong(STATE_SELECTED_USER);
			mFromSavedInstanceState = true;
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {

		mImageCache = ImageCache.getCache(getActivity(), this);

		View view = inflater.inflate(R.layout.fragment_drawer, container, false);
		mDrawerList = (RecyclerView) view.findViewById(R.id.left_drawer);

		LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
		layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
		mDrawerList.setLayoutManager(layoutManager);
		mDrawerList.setHasFixedSize(true);

		mPageAdapter = new NavigationDrawerAdapter(new ArrayList<>(Page.pages), TwitterEngine.getAll());
		mRecyclerViewDragDropManager = new RecyclerViewDragDropManager();

		mDrawerList.setAdapter(mRecyclerViewDragDropManager.createWrappedAdapter(mPageAdapter));
		mCurrentPage = 0;
		mRecyclerViewDragDropManager.attachRecyclerView(mDrawerList);

		TwitterStreamServiceReceiver.addOnStreamTurnListener(this);

		TwitterEngine.addOnUserlistChangedListener(this);

		if (mCurrentUserId == 0) {
			mCurrentUserId = TwitterEngine.getAll().get(0).getUserId();
		}

		mPageAdapter.selectPage(mCurrentPage);
		mPageAdapter.selectUser(TwitterEngine.get(mCurrentUserId));

		Page.addOnPageChangedListener(this);

		return view;
	}

	@Override
	public void onPause() {
		mRecyclerViewDragDropManager.cancelDrag();
		super.onPause();
	}

	@Override
	public void onDestroyView() {
		mRecyclerViewDragDropManager.release();
		Page.removeOnPageChangedListener(this);
		for (Tweeter t : mPageAdapter.mListedUsers)
			t.removeOnChangeListener(mPageAdapter);
		TwitterEngine.removeOnUserlistChangedListener(this);
		TwitterStreamServiceReceiver.removeOnStreamTurnListener(this);
		mImageCache = null;
		super.onDestroyView();
	}

	public TwitterEngine getCurrentUser() {
		return mCurrentUser;
	}

	public int getCurrentPage() {
		return mCurrentPage;
	}

	public boolean isDrawerOpen() {
		return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
	}

	public ActionBarDrawerToggle getActionBarDrawerToggle() {
		return mActionBarDrawerToggle;
	}

	/**
	 * Users of this fragment must call this method to set up the navigation drawer interactions.
	 *
	 * @param fragmentId   The android:id of this fragment in its activity's layout.
	 * @param drawerLayout The DrawerLayout containing this fragment's UI.
	 * @param toolbar      The Toolbar of the activity.
	 */
	public void setup(int fragmentId, DrawerLayout drawerLayout, Toolbar toolbar) {
		mFragmentContainerView = (View) getActivity().findViewById(fragmentId).getParent();
		mDrawerLayout = drawerLayout;

		TypedArray ta = getActivity().obtainStyledAttributes(new int[]{R.attr.colorPrimary});
		mDrawerLayout.setStatusBarBackgroundColor(ta.getColor(0, 0));
		ta.recycle();

		mActionBarDrawerToggle = new ActionBarDrawerToggle(getActivity(), mDrawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
			@Override
			public void onDrawerClosed(View drawerView) {
				super.onDrawerClosed(drawerView);
				mIsPageListing = true;
				mListEditMode = false;
				mPageAdapter.notifyDataSetChanged();
				if (!isAdded()) return;

				getActivity().supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu()
			}

			@Override
			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
				if (!isAdded()) return;
				if (!mUserLearnedDrawer) {
					mUserLearnedDrawer = true;
					SharedPreferences sp = PreferenceManager
							.getDefaultSharedPreferences(getActivity());
					sp.edit().putBoolean(PREF_USER_LEARNED_DRAWER, true).apply();
				}
				getActivity().supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu()
			}
		};

		// If the user hasn't 'learned' about the drawer, open it to introduce them to the drawer,
		// per the navigation drawer design guidelines.
		if (!mUserLearnedDrawer && !mFromSavedInstanceState) {
			mDrawerLayout.openDrawer(mFragmentContainerView);
		}

		// Defer code dependent on restoration of previous instance state.
		mDrawerLayout.post(new Runnable() {
			@Override
			public void run() {
				mActionBarDrawerToggle.syncState();
			}
		});

		mDrawerLayout.setDrawerListener(mActionBarDrawerToggle);
	}

	public void onPageSelected(int position) {
		mPageAdapter.selectPage(position);
	}

	public void openDrawer() {
		mDrawerLayout.openDrawer(mFragmentContainerView);
	}

	public void closeDrawer() {
		mDrawerLayout.closeDrawer(mFragmentContainerView);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mCallbacks = (NavigationDrawerCallbacks) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException("Activity must implement NavigationDrawerCallbacks.");
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mCallbacks = null;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(STATE_SELECTED_USER, mCurrentUserId);
		outState.putInt(STATE_SELECTED_PAGE, mCurrentPage);
		SharedPreferences.Editor editor = mDefaultPreference.edit();
		editor.putInt(STATE_SELECTED_PAGE, mCurrentPage);
		editor.putLong(STATE_SELECTED_USER, mCurrentUserId);
		editor.apply();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Forward the new configuration the drawer toggle component.
		mActionBarDrawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public void onPageListChanged() {
		mPageAdapter.updateUnderlyingData(Page.pages, null);
	}

	@Override
	public void onImageCacheReady(ImageCache cache) {
		mImageCache = cache;
		mPageAdapter.notifyDataSetChanged();
	}

	@Override
	public void onUserlistChanged(List<TwitterEngine.StreamableTwitterEngine> engines) {
		if (mPageAdapter != null && getActivity() != null) {
			ArrayList<TwitterEngine> newEngines = new ArrayList<>();
			newEngines.addAll(engines);
			if (!newEngines.contains(mCurrentUser))
				mCurrentUser = newEngines.get(0);
			mPageAdapter.updateUnderlyingData(null, newEngines);
		}
	}

	@Override
	public void onStreamTurnedOn(TwitterEngine e) {
		mStreamPowerChangeWaitingEngines.remove(e);
		if (!mIsPageListing)
			mPageAdapter.notifyItemChanged(1 + mPageAdapter.mListedUsers.indexOf(e.getTweeter()));
		mHandler.removeMessages(MESSAGE_SERVICE_TIMEOUT, e);
	}

	@Override
	public void onStreamTurnedOff(TwitterEngine e) {
		mStreamPowerChangeWaitingEngines.remove(e);
		if (!mIsPageListing)
			mPageAdapter.notifyItemChanged(1 + mPageAdapter.mListedUsers.indexOf(e.getTweeter()));
		mHandler.removeMessages(MESSAGE_SERVICE_TIMEOUT, e);
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MESSAGE_SERVICE_TIMEOUT: {
				if (!(msg.obj instanceof TwitterEngine))
					return true;
				TwitterEngine e = (TwitterEngine) msg.obj;
				if (mStreamPowerChangeWaitingEngines.contains(e)) {
					mStreamPowerChangeWaitingEngines.remove(e);
					if (msg.what == 1)
						TwitterStreamServiceReceiver.forceSetStatus((TwitterEngine.StreamableTwitterEngine) e, false);
					else if (msg.what == 2)
						TwitterStreamServiceReceiver.forceSetStatus((TwitterEngine.StreamableTwitterEngine) e, true);
					if (!mIsPageListing)
						mPageAdapter.notifyItemChanged(1 + mPageAdapter.mListedUsers.indexOf(e.getTweeter()));
				}
				return true;
			}
		}
		return false;
	}

	public interface NavigationDrawerCallbacks {
		void onNavigationDrawerItemSelected(int position);
	}

	public class NavigationDrawerAdapter extends RecyclerView.Adapter<NavigationDrawerAdapter.DrawerViewHolder> implements Tweeter.OnUserInformationChangedListener, DraggableItemAdapter<NavigationDrawerAdapter.DrawerViewHolder> {

		final ArrayList<Page> mListedPages;
		final ArrayList<Tweeter> mListedUsers;
		final SparseArray<BitmapDrawable> mSizedBitmapResources = new SparseArray<>();

		public int mPageBtnCount = 2;
		public int mUserBtnCount = 2;

		/*
		Header
		-
		Page list				User list
		Add page			Add user
		Manage pages	Manage users(Reorder)
		 */

		public NavigationDrawerAdapter(List<Page> data, ArrayList<TwitterEngine> users) {
			mListedPages = new ArrayList<>(data);
			mListedUsers = new ArrayList<>();
			for (TwitterEngine e : users) {
				e.getTweeter().addOnChangeListener(this);
				mListedUsers.add(e.getTweeter());
			}
			setHasStableIds(true);
		}

		public void updateUnderlyingData(List<Page> data, ArrayList<TwitterEngine> users) {
			if (data != null) {
				mListedPages.clear();
				mListedPages.addAll(data);
			}
			if (users != null) {
				for (Tweeter t : mListedUsers)
					t.removeOnChangeListener(this);
				mListedUsers.clear();
				for (TwitterEngine e : users) {
					e.getTweeter().addOnChangeListener(this);
					mListedUsers.add(e.getTweeter());
				}
			}
			notifyDataSetChanged();
		}

		@Override
		public int getItemViewType(int position) {
			if (position == 0)
				return R.layout.row_drawer_header;
			if (position - 1 >= (mIsPageListing ? mListedPages.size() : mListedUsers.size()))
				return R.layout.row_drawer_button; // button
			return mIsPageListing ? R.layout.row_drawer_page : R.layout.row_drawer_account;
		}

		@Override
		public long getItemId(int position) {
			switch (getItemViewType(position)) {
				case R.layout.row_drawer_header:
					return -1;
				case R.layout.row_drawer_button:
					return -2 - position;
				case R.layout.row_drawer_page:
					return mListedPages.get(getItemActualPosition(position)).getId();
				case R.layout.row_drawer_account:
					return mListedUsers.get(getItemActualPosition(position)).user_id;
			}
			throw new RuntimeException("Bad layout ID");
		}

		private int getItemActualPosition(int position) {
			if (position == 0) return 0; // Header
			if (position - 1 >= (mIsPageListing ? mListedPages.size() : mListedUsers.size())) // Button
				return position - 1 - (mIsPageListing ? mListedPages.size() : mListedUsers.size());
			return position - 1;
		}

		@Override
		public int getItemCount() {
			return 1 + (mIsPageListing ? mListedPages.size() + mPageBtnCount : (mListedUsers.size()) + mUserBtnCount);
		}

		@Override
		public DrawerViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
			switch (viewType) {
				case R.layout.row_drawer_header:
					return new HeaderViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(viewType, viewGroup, false));
				case R.layout.row_drawer_account:
					return new AccountViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(viewType, viewGroup, false));
				case R.layout.row_drawer_page:
					return new PageViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(viewType, viewGroup, false));
				case R.layout.row_drawer_button:
					return new ButtonViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(viewType, viewGroup, false));
			}
			return null;
		}

		@Override
		public void onBindViewHolder(final NavigationDrawerAdapter.DrawerViewHolder vh, int position) {
			vh.bindViewHolder(getItemActualPosition(position));
			Tweeter.fillLoadQueuedData();
		}

		private BitmapDrawable getResourceDrawable(int resId, int targetWidth, int targetHeight) {
			if (resId == 0)
				return null;
			if (mSizedBitmapResources.get(resId) != null)
				return mSizedBitmapResources.get(resId);
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeResource(getResources(), resId, options);
			options.inSampleSize = 1;
			while (options.outWidth / options.inSampleSize / 2 >= targetWidth && options.outHeight / options.inSampleSize / 2 >= targetHeight)
				options.inSampleSize <<= 1;
			options.inJustDecodeBounds = false;
			Bitmap bm = BitmapFactory.decodeResource(getResources(), resId, options);
			mSizedBitmapResources.put(resId, new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(bm, options.outWidth / options.inSampleSize, options.outHeight / options.inSampleSize, true)));
			bm.recycle();
			return getResourceDrawable(resId, targetWidth, targetHeight);
		}

		public void selectPage(int position) {
			if (position != mCurrentPage) {
				notifyItemChanged(mCurrentPage + 1);
				if (mCallbacks != null)
					mCallbacks.onNavigationDrawerItemSelected(position);
				mCurrentPage = position;
				notifyItemChanged(position + 1);
			}
		}

		public void selectUser(TwitterEngine user) {
			if (mCurrentUser != null) {
				mPageAdapter.notifyItemChanged(mPageAdapter.mListedUsers.indexOf(mCurrentUser.getTweeter()) + 1);
			}
			mCurrentUser = user;
			mCurrentUserId = user == null ? 0 : user.getUserId();
			if (mCurrentUser != null) {
				mPageAdapter.notifyItemChanged(mPageAdapter.mListedUsers.indexOf(mCurrentUser.getTweeter()) + 1);
				onUserInformationChanged(user.getTweeter());
			}
			mPageAdapter.notifyItemChanged(0);
		}

		@Override
		public boolean onCheckCanStartDrag(DrawerViewHolder holder, int position, int x, int y) {
			if (!mListEditMode) return false;
			if (holder instanceof AccountViewHolder) {
				AccountViewHolder vh = (AccountViewHolder) holder;
				return (x > vh.actionButton.getLeft() - vh.actionButton.getPaddingLeft() && x < vh.actionButton.getRight() + vh.actionButton.getPaddingRight() && y > vh.actionButton.getTop() - vh.actionButton.getPaddingTop() && y < vh.actionButton.getBottom() + vh.actionButton.getPaddingBottom());
			} else if (holder instanceof PageViewHolder) {
				PageViewHolder vh = (PageViewHolder) holder;
				return (x > vh.actionButton.getLeft() - vh.actionButton.getPaddingLeft() && x < vh.actionButton.getRight() + vh.actionButton.getPaddingRight() && y > vh.actionButton.getTop() - vh.actionButton.getPaddingTop() && y < vh.actionButton.getBottom() + vh.actionButton.getPaddingBottom());
			}
			return false;
		}

		@Override
		public ItemDraggableRange onGetItemDraggableRange(DrawerViewHolder vh, int position) {
			if (mIsPageListing) {
				if (getItemActualPosition(position) < Page.mSavedPageLength)
					return new ItemDraggableRange(1, Page.mSavedPageLength);
				else
					return new ItemDraggableRange(1 + Page.mSavedPageLength, mListedPages.size());
			} else
				return new ItemDraggableRange(1, mListedUsers.size());
		}

		public void moveItem(int from, int to) {
			if (mIsPageListing) {
				mListedPages.add(to, mListedPages.remove(from));
				Page.addPage(to, Page.removePage(from));
				Page.broadcastPageChange();
			} else {
				TwitterEngine.reorder(getActivity(), from, to);
			}
			notifyItemMoved(from + 1, to + 1);
		}

		@Override
		public void onMoveItem(int from, int to) {
			moveItem(from - 1, to - 1);
		}

		@Override
		public void onUserInformationChanged(Tweeter tweeter) {
			notifyDataSetChanged();
		}

		public class DrawerViewHolder extends AbstractDraggableItemViewHolder implements View.OnClickListener {
			private final ViewGroup mActionContainer;
			private ValueAnimator mContainerAnimator;
			private boolean mUseActionContainer;
			private boolean mActionContainerExpanded;

			public DrawerViewHolder(View itemView) {
				super(itemView);
				mActionContainer = (ViewGroup) itemView.findViewById(R.id.account_manage_container);
				if (mActionContainer != null) {
					for (int i = mActionContainer.getChildCount() - 1; i >= 0; i--)
						mActionContainer.getChildAt(i).setOnClickListener(this);
					mActionContainer.setVisibility(View.GONE);
				}
			}

			public void setSelected(boolean selected) {
				if (mUseActionContainer && selected)
					expand();
				else
					collapse();
				itemView.setSelected(selected);
			}

			protected void setUseActionContainer(boolean use) {
				if (itemView.isSelected()) {
					if (!use) {
						if (mContainerAnimator != null)
							mContainerAnimator.cancel();
						mActionContainerExpanded = false;
						mContainerAnimator = null;
						itemView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
						mActionContainer.setVisibility(View.GONE);
					} else if (!mUseActionContainer)
						expand();
				}
				mUseActionContainer = use;
			}

			public void bindViewHolder(int position) {
			}

			public void collapse() {
				if (mActionContainer == null)
					return;
				if (!mActionContainerExpanded)
					return;
				mActionContainerExpanded = false;
				if (mContainerAnimator != null)
					mContainerAnimator.cancel();
				FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mActionContainer.getLayoutParams();
				mContainerAnimator = ValueAnimator.ofInt(itemView.getHeight(), lp.topMargin);
				mContainerAnimator.setInterpolator(new AccelerateInterpolator());
				mContainerAnimator.addListener(new Animator.AnimatorListener() {
					@Override
					public void onAnimationStart(Animator animation) {

					}

					@Override
					public void onAnimationEnd(Animator animation) {
						mActionContainer.setVisibility(View.GONE);
					}

					@Override
					public void onAnimationCancel(Animator animation) {

					}

					@Override
					public void onAnimationRepeat(Animator animation) {

					}
				});
				mContainerAnimator.setDuration(300);
				mContainerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
					@Override
					public void onAnimationUpdate(ValueAnimator animation) {
						itemView.getLayoutParams().height = (Integer) animation.getAnimatedValue();
						itemView.requestLayout();
					}
				});
				mContainerAnimator.start();
			}

			public void expand() {
				if (mActionContainer == null)
					return;
				if (mActionContainerExpanded)
					return;
				mActionContainerExpanded = true;
				if (mContainerAnimator != null)
					mContainerAnimator.cancel();
				FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mActionContainer.getLayoutParams();
				mContainerAnimator = ValueAnimator.ofInt(itemView.getHeight(), lp.height + lp.topMargin);
				mContainerAnimator.setInterpolator(new DecelerateInterpolator());
				mActionContainer.setVisibility(View.VISIBLE);
				mContainerAnimator.setDuration(300);
				mContainerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
					@Override
					public void onAnimationUpdate(ValueAnimator animation) {
						itemView.getLayoutParams().height = (Integer) animation.getAnimatedValue();
						itemView.requestLayout();
					}
				});
				mContainerAnimator.start();
			}

			@Override
			public void onClick(View v) {
			}
		}

		public class HeaderViewHolder extends DrawerViewHolder {
			public final ImageView mViewAvatar, mViewAccountBackground;
			public final TextView mViewName, mViewScreenName;
			public final View mViewShowAccountList;

			public HeaderViewHolder(View itemView) {
				super(itemView);

				mViewAccountBackground = (ImageView) itemView.findViewById(R.id.imgAccountBackground);
				mViewAvatar = (ImageView) itemView.findViewById(R.id.imgAvatar);
				mViewName = (TextView) itemView.findViewById(R.id.txtUsername);
				mViewScreenName = (TextView) itemView.findViewById(R.id.txtScreenName);
				mViewShowAccountList = itemView.findViewById(R.id.show_account_list);

				itemView.setOnClickListener(this);
				if (mViewShowAccountList != null)
					((View) mViewShowAccountList.getParent()).setOnClickListener(this);
			}

			@Override
			public void onClick(View v) {
				if (v.equals(mViewShowAccountList.getParent())) {
					mListEditMode = false;
					mIsPageListing = !mIsPageListing;
					notifyDataSetChanged();
					mViewShowAccountList.setRotation(mIsPageListing ? 0 : 180);

					RotateAnimation ani = new RotateAnimation(-180, 0, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
					ani.setDuration(300);
					ani.setInterpolator(getActivity(), android.R.anim.accelerate_decelerate_interpolator);
					mViewShowAccountList.startAnimation(ani);
				}
			}

			@Override
			public void bindViewHolder(int position) {
				if (mCurrentUser != null) {
					Tweeter user = mCurrentUser.getTweeter();
					if (mImageCache != null) {
						mImageCache.assignImageView(mViewAvatar, user.getProfileImageUrl(), null, mImageCache.CIRCULAR_IMAGE_PREPROCESSOR);
						mImageCache.assignImageView(mViewAccountBackground, R.drawable.wallpaper, user.getProfileBannerUrl(), null, null);
					}
					mViewName.setText(user.name);
					mViewScreenName.setText(user.screen_name);
				}
			}
		}

		public class PageViewHolder extends DrawerViewHolder implements View.OnCreateContextMenuListener {
			public final TextView textView;
			public final ImageView imageView, imageViewUser;
			public final ImageButton actionButton;

			public PageViewHolder(View itemView) {
				super(itemView);
				textView = (TextView) itemView.findViewById(R.id.item_name);
				imageView = (ImageView) itemView.findViewById(R.id.item_image);
				imageViewUser = (ImageView) itemView.findViewById(R.id.item_image_user);
				actionButton = (ImageButton) itemView.findViewById(R.id.action_button);

				itemView.setOnClickListener(this);
				itemView.setOnCreateContextMenuListener(this);
				actionButton.setOnClickListener(this);
			}

			@Override
			public void onClick(View v) {
				int position = getItemActualPosition(getAdapterPosition());
				if (position < 0 || position >= mListedPages.size())
					return;
				if (v.equals(actionButton)) {
					int newIndex = Page.removePage(position).getParentPageIndex();
					Page.broadcastPageChange();
					if (newIndex != -1)
						selectPage(newIndex);
				} else if (mListEditMode) {
					if (position >= Page.mSavedPageLength) {
						moveItem(position, Page.mSavedPageLength);
						Page.mSavedPageLength++;
						Page.broadcastPageChange();
					}
				} else {
					selectPage(position);
					if (isDrawerOpen())
						mDrawerLayout.closeDrawer(mFragmentContainerView);
				}
			}

			@Override
			public void bindViewHolder(int position) {
				textView.setText(mListedPages.get(position).name);
				actionButton.setVisibility(mListEditMode || position >= Page.mSavedPageLength ? View.VISIBLE : View.GONE);
				if (mListEditMode) {
					actionButton.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_navigation_menu));
					actionButton.setContentDescription(getString(R.string.action_reorder_page));
					actionButton.setEnabled(false);
				} else if (position >= Page.mSavedPageLength) {
					actionButton.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_content_clear));
					actionButton.setContentDescription(getString(R.string.action_close_page));
					actionButton.setEnabled(true);
				}
				setSelected(mCurrentPage == position);
				if (mImageCache != null) {
					long twitterEngineId = mListedPages.get(position).elements.get(0).twitterEngineId;
					long id = mListedPages.get(position).elements.get(0).id;
					for (int i = mListedPages.get(position).elements.size() - 1; i > 0; i--) {
						if (twitterEngineId != mListedPages.get(position).elements.get(i).twitterEngineId)
							twitterEngineId = 0;
						if (id != mListedPages.get(position).elements.get(i).id)
							id = 0;
					}
					if (twitterEngineId != 0) {
						mImageCache.assignImageView(imageViewUser, mListedPages.get(position).elements.get(0).getTwitterEngine(getActivity()).getTweeter().getProfileImageUrl(), null, mImageCache.CIRCULAR_IMAGE_PREPROCESSOR);
						mListedPages.get(position).elements.get(0).getTwitterEngine(getActivity()).getTweeter().addToDataLoadQueue(mListedPages.get(position).elements.get(0).getTwitterEngine(getActivity()));
					} else
						mImageCache.assignImageView(imageViewUser, null, null);
					if (id != 0) {
						mImageCache.assignImageView(imageView, Tweeter.getTweeter(id, mListedPages.get(position).elements.get(0).name).getProfileImageUrl(), null, mImageCache.CIRCULAR_IMAGE_PREPROCESSOR);
						Tweeter.getTweeter(id, mListedPages.get(position).elements.get(0).name).addToDataLoadQueue(mListedPages.get(position).elements.get(0).getTwitterEngine(getActivity()));
					} else {
						mImageCache.assignImageView(imageView, null, null);
						imageView.setImageDrawable(getResourceDrawable(mListedPages.get(position).iconResId, imageView.getLayoutParams().width, imageView.getLayoutParams().height));
					}
				} else {
					imageViewUser.setImageDrawable(null);
					imageView.setImageDrawable(null);
				}
			}

			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

			}
		}

		public class AccountViewHolder extends DrawerViewHolder implements CompoundButton.OnCheckedChangeListener, View.OnLongClickListener {
			public final TextView textView;
			public final Switch useStream;
			public final ImageView imageView, imageViewUser;
			public final ImageButton actionButton;

			public AccountViewHolder(View itemView) {
				super(itemView);
				textView = (TextView) itemView.findViewById(R.id.item_name);
				useStream = (Switch) itemView.findViewById(R.id.use_stream);
				imageView = (ImageView) itemView.findViewById(R.id.item_image);
				imageViewUser = (ImageView) itemView.findViewById(R.id.item_image_user);
				actionButton = (ImageButton) itemView.findViewById(R.id.action_button);

				itemView.setOnClickListener(this);
				useStream.setOnCheckedChangeListener(this);
				useStream.setOnLongClickListener(this);
				actionButton.setOnClickListener(this);
			}

			@Override
			public void bindViewHolder(int position) {
				actionButton.setVisibility(mListEditMode ? View.VISIBLE : View.GONE);
				useStream.setVisibility(mListEditMode ? View.GONE : View.VISIBLE);
				TwitterEngine engine = TwitterEngine.get(mListedUsers.get(position).user_id);
				useStream.setEnabled(!mStreamPowerChangeWaitingEngines.contains(engine));
				useStream.setOnCheckedChangeListener(null);
				useStream.setChecked(TwitterStreamServiceReceiver.isStreamOn(engine));
				useStream.setOnCheckedChangeListener(this);
				textView.setText(mListedUsers.get(position).screen_name);
				setUseActionContainer(mListEditMode);
				setSelected(mListedUsers.get(position).user_id == mCurrentUserId);

				if (mImageCache != null)
					mImageCache.assignImageView(imageView, mListedUsers.get(position).getProfileImageUrl(), null, mImageCache.CIRCULAR_IMAGE_PREPROCESSOR);
			}

			@Override
			public void onClick(View v) {
				final int position = getItemActualPosition(getAdapterPosition());
				if (position < 0 || position >= mListedUsers.size())
					return;
				switch (v.getId()) {
					case R.id.account_setting: {
						return;
					}
					case R.id.account_logout: {
						new AlertDialog.Builder(getActivity())
								.setMessage(StringTools.fillStringResFormat(getActivity(), R.string.drawer_menu_account_logout_confirm, "screen_name", mListedUsers.get(position).screen_name))
								.setPositiveButton(android.R.string.yes, new AlertDialog.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										TwitterEngine.remove(mListedUsers.get(position).user_id);
									}
								})
								.setNegativeButton(android.R.string.no, null)
								.show();
						return;
					}
				}
				selectUser(TwitterEngine.get(mListedUsers.get(position).user_id));
			}

			@Override
			public void onCheckedChanged(CompoundButton v, boolean isChecked) {
				int usr = getAdapterPosition() - 1;
				TwitterEngine.StreamableTwitterEngine engine = TwitterEngine.get(mListedUsers.get(usr).user_id);
				TwitterStreamServiceReceiver.turnStreamOn(engine, isChecked);
				mHandler.sendMessageDelayed(Message.obtain(mHandler, MESSAGE_SERVICE_TIMEOUT, isChecked ? 2 : 1, 0, engine), SERVICE_COMMUNICATION_TIMEOUT);
				mStreamPowerChangeWaitingEngines.add(engine);
				v.setEnabled(false);
			}

			@Override
			public boolean onLongClick(View v) {
				Toast.makeText(getActivity(), useStream.isChecked() ? R.string.drawer_stream_btn_off : R.string.drawer_stream_btn_on, Toast.LENGTH_SHORT).show();
				return false;
			}
		}

		public class ButtonViewHolder extends DrawerViewHolder {
			public TextView textView;
			public ImageView imageView;

			public ButtonViewHolder(View itemView) {
				super(itemView);
				textView = (TextView) itemView.findViewById(R.id.item_name);
				imageView = (ImageView) itemView.findViewById(R.id.item_image);
				itemView.setOnClickListener(this);
			}

			@Override
			public void onClick(View v) {
				int position = getItemActualPosition(getAdapterPosition());
				switch (position) {
					case 0: { // Add
						if (mIsPageListing) {

						} else {
							AccountManager.get(getActivity()).addAccount(getString(R.string.account_type), null, null, null, getActivity(), null, null);
						}
						return;
					}
					case 1: { // Manage
						mListEditMode = !mListEditMode;
						notifyDataSetChanged();
						return;
					}
				}
			}

			@Override
			public void bindViewHolder(int position) {
				switch (position) {
					case 0: { // Add
						textView.setText(mIsPageListing ? R.string.drawer_add_page : R.string.drawer_add_account);
						imageView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_content_add));
						return;
					}
					case 1: { // Manage
						textView.setText(mIsPageListing ? R.string.drawer_manage_pages : R.string.drawer_manage_accounts);
						imageView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_action_settings));
						return;
					}
				}
			}
		}
	}
}
