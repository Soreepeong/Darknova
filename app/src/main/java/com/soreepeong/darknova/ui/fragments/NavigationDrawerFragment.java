package com.soreepeong.darknova.ui.fragments;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.RotateAnimation;
import android.widget.CompoundButton;
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
import com.soreepeong.darknova.core.ResTools;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.twitter.Tweet;
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
public class NavigationDrawerFragment extends Fragment implements ImageCache.OnImageCacheReadyListener, TwitterEngine.OnUserlistChangedListener, TwitterStreamServiceReceiver.OnStreamTurnedListener, Page.OnPageListChangedListener, Handler.Callback{

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

	private final ArrayList<TwitterEngine> mStreamPowerChangeWaitingEngines = new ArrayList<>();
	private final Handler mHandler = new Handler(this);
	private static final int MESSAGE_SERVICE_TIMEOUT = 1;
	private static final int SERVICE_COMMUNICATION_TIMEOUT = 30000;

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

		mPageAdapter = new NavigationDrawerAdapter(new ArrayList<>(Page.pages), TwitterEngine.getTwitterEngines(getActivity()));
		mRecyclerViewDragDropManager = new RecyclerViewDragDropManager();

		mDrawerList.setAdapter(mRecyclerViewDragDropManager.createWrappedAdapter(mPageAdapter));
		mCurrentPage = 0;
		mRecyclerViewDragDropManager.attachRecyclerView(mDrawerList);

		TwitterStreamServiceReceiver.addOnStreamTurnListener(this);

		TwitterEngine.addOnUserlistChangedListener(this);

		if(mCurrentUserId==0){
			mCurrentUserId = TwitterEngine.getTwitterEngines(getActivity()).get(0).getUserId();
		}

		mPageAdapter.selectPage(mCurrentPage);
		mPageAdapter.selectUser(TwitterEngine.getTwitterEngine(getActivity(), mCurrentUserId));

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
		for(Tweeter t : mPageAdapter.mListedUsers)
			t.removeOnChangeListener(mPageAdapter);
		TwitterEngine.removeOnUserlistChangedListener(this);
		TwitterStreamServiceReceiver.removeOnStreamTurnListener(this);
		mImageCache = null;
		super.onDestroyView();
	}

	public TwitterEngine getCurrentUser(){
		return mCurrentUser;
	}

	public int getCurrentPage(){
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
		if(mPageAdapter != null && getActivity() != null){
			ArrayList<TwitterEngine> newEngines = new ArrayList<>();
			newEngines.addAll(engines);
			mPageAdapter.updateUnderlyingData(null, newEngines);
		}
	}

	@Override
	public void onStreamTurnedOn(TwitterEngine e) {
		mStreamPowerChangeWaitingEngines.remove(e);
		if(!mIsPageListing)
			mPageAdapter.notifyItemChanged(1 + mPageAdapter.mListedUsers.indexOf(e.getTweeter()));
		mHandler.removeMessages(MESSAGE_SERVICE_TIMEOUT, e);
	}

	@Override
	public void onStreamTurnedOff(TwitterEngine e) {
		mStreamPowerChangeWaitingEngines.remove(e);
		if(!mIsPageListing)
			mPageAdapter.notifyItemChanged(1 + mPageAdapter.mListedUsers.indexOf(e.getTweeter()));
		mHandler.removeMessages(MESSAGE_SERVICE_TIMEOUT, e);
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch(msg.what){
			case MESSAGE_SERVICE_TIMEOUT:{
				if(!(msg.obj instanceof TwitterEngine))
					return true;
				TwitterEngine e = (TwitterEngine) msg.obj;
				if(mStreamPowerChangeWaitingEngines.contains(e)){
					mStreamPowerChangeWaitingEngines.remove(e);
					if(msg.what == 1)
						TwitterStreamServiceReceiver.forceSetStatus((TwitterEngine.StreamableTwitterEngine) e, false);
					else if(msg.what == 2)
						TwitterStreamServiceReceiver.forceSetStatus((TwitterEngine.StreamableTwitterEngine) e, true);
					if(!mIsPageListing)
						mPageAdapter.notifyItemChanged(1 + mPageAdapter.mListedUsers.indexOf(e.getTweeter()));
				}
				return true;
			}
		}
		return false;
	}

	public class NavigationDrawerAdapter extends RecyclerView.Adapter<NavigationDrawerAdapter.ViewHolder> implements Tweeter.OnUserInformationChangedListener, DraggableItemAdapter<NavigationDrawerAdapter.ViewHolder> {

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

		public NavigationDrawerAdapter(List<Page> data, ArrayList<TwitterEngine> users){
			mListedPages = new ArrayList<>(data);
			mListedUsers = new ArrayList<>();
			for(TwitterEngine e : users){
				e.getTweeter().addOnChangeListener(this);
				mListedUsers.add(e.getTweeter());
			}
			setHasStableIds(true);
		}

		public void updateUnderlyingData(List<Page> data, ArrayList<TwitterEngine> users){
			if(data != null){
				mListedPages.clear();
				mListedPages.addAll(data);
			}
			if(users != null){
				for(Tweeter t : mListedUsers)
					t.removeOnChangeListener(this);
				mListedUsers.clear();
				for(TwitterEngine e : users){
					e.getTweeter().addOnChangeListener(this);
					mListedUsers.add(e.getTweeter());
				}
			}
			notifyDataSetChanged();
		}

		@Override
		public int getItemViewType(int position) {
			if (position == 0)
				return 0;
			if(position-1 >= (mIsPageListing? mListedPages.size(): mListedUsers.size()))
				return 3; // button
			return mIsPageListing ? 1 : 2;
		}

		@Override
		public long getItemId(int position) {
			if(position == 0) return -1;
			if(getItemViewType(position) == 3) return -2-position;
			position=getItemActualPosition(position);
			return (mIsPageListing? mListedPages.get(position).getId(): mListedUsers.get(position).user_id);
		}

		private int getItemActualPosition(int position){
			if(position == 0) return 0; // Header
			if(position - 1 >= (mIsPageListing? mListedPages.size(): mListedUsers.size())) // Button
				return position - 1 - (mIsPageListing? mListedPages.size(): mListedUsers.size());
			return position - 1;
		}

		@Override
		public int getItemCount() {
			return 1 + (mIsPageListing ? mListedPages.size() + mPageBtnCount : (mListedUsers.size()) + mUserBtnCount);
		}

		@Override
		public NavigationDrawerAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
			switch(viewType){
				case 0:
					return new ViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.row_drawer_header, viewGroup, false));
				case 1:
					return new ViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.row_drawer_page, viewGroup, false));
				case 2:
					return new ViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.row_drawer_account, viewGroup, false));
				case 3:
					return new ViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.row_drawer_button, viewGroup, false));
			}
			return null;
		}

		@Override
		public void onBindViewHolder(NavigationDrawerAdapter.ViewHolder vh, int position) {
			int itemType=getItemViewType(position);
			position= getItemActualPosition(position);
			switch(itemType){
				case 0:{
					if(mCurrentUser != null){
						Tweeter user = mCurrentUser.getTweeter();
						if(mImageCache != null){
							mImageCache.assignImageView(vh.mViewAvatar, user.getProfileImageUrl(), null, mImageCache.CIRCULAR_IMAGE_PREPROCESSOR);
							mImageCache.assignImageView(vh.mViewAccountBackground, R.drawable.wallpaper, user.getProfileBannerUrl(), null, null);
						}
						vh.mViewName.setText(user.name);
						vh.mViewScreenName.setText(user.screen_name);
					}
					return;
				}
				case 1: {
					vh.textView.setText(mListedPages.get(position).name);
					vh.actionButton.setVisibility(mListEditMode || position >= Page.mSavedPageLength ? View.VISIBLE : View.GONE);
					if(mListEditMode){
						vh.actionButton.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_navigation_menu));
						vh.actionButton.setContentDescription(getString(R.string.action_reorder_page));
						vh.actionButton.setEnabled(false);
					}else if(position >= Page.mSavedPageLength){
						vh.actionButton.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_content_clear));
						vh.actionButton.setContentDescription(getString(R.string.action_close_page));
						vh.actionButton.setEnabled(true);
					}
					vh.itemView.setSelected(mCurrentPage == position);
					if(mImageCache != null){
						long twitterEngineId = mListedPages.get(position).elements.get(0).twitterEngineId;
						long id = mListedPages.get(position).elements.get(0).id;
						for(int i = mListedPages.get(position).elements.size()-1; i>0; i--){
							if(twitterEngineId != mListedPages.get(position).elements.get(i).twitterEngineId)
								twitterEngineId = 0;
							if(id != mListedPages.get(position).elements.get(i).id)
								id = 0;
						}
						if(twitterEngineId != 0){
							mImageCache.assignImageView(vh.imageViewUser, mListedPages.get(position).elements.get(0).getTwitterEngine(getActivity()).getTweeter().getProfileImageUrl(), null, mImageCache.CIRCULAR_IMAGE_PREPROCESSOR);
							mListedPages.get(position).elements.get(0).getTwitterEngine(getActivity()).getTweeter().addToDataLoadQueue(mListedPages.get(position).elements.get(0).getTwitterEngine(getActivity()));
						}else
							mImageCache.assignImageView(vh.imageViewUser, null, null);
						if(id != 0){
							mImageCache.assignImageView(vh.imageView, Tweeter.getTweeter(id, mListedPages.get(position).elements.get(0).name).getProfileImageUrl(), null, mImageCache.CIRCULAR_IMAGE_PREPROCESSOR);
							Tweeter.getTweeter(id, mListedPages.get(position).elements.get(0).name).addToDataLoadQueue(mListedPages.get(position).elements.get(0).getTwitterEngine(getActivity()));
						}else{
							mImageCache.assignImageView(vh.imageView, null, null);
							vh.imageView.setImageDrawable(getResourceDrawable(mListedPages.get(position).iconResId, vh.imageView.getLayoutParams().width, vh.imageView.getLayoutParams().height));
						}
					}else{
						vh.imageViewUser.setImageDrawable(null);
						vh.imageView.setImageDrawable(null);
					}
					return;
				}
				case 2:{
					vh.actionButton.setVisibility(mListEditMode ? View.VISIBLE : View.GONE);
					vh.useStream.setVisibility(mListEditMode ? View.GONE : View.VISIBLE);
					TwitterEngine engine = TwitterEngine.getTwitterEngine(getActivity(), mListedUsers.get(position).user_id);
					vh.useStream.setEnabled(!mStreamPowerChangeWaitingEngines.contains(engine));
					vh.useStream.setOnCheckedChangeListener(null);
					vh.useStream.setChecked(TwitterStreamServiceReceiver.isStreamOn(engine));
					vh.useStream.setOnCheckedChangeListener(vh);
					vh.textView.setText(mListedUsers.get(position).screen_name);
					vh.itemView.setSelected(mListedUsers.get(position).user_id == mCurrentUserId);

					if(mImageCache != null){
						mImageCache.assignImageView(vh.imageView, mListedUsers.get(position).getProfileImageUrl(), null, mImageCache.CIRCULAR_IMAGE_PREPROCESSOR);
					}
					return;
				}
				case 3:{
					switch(position){
						case 0:{ // Add
							vh.textView.setText(mIsPageListing?R.string.drawer_add_page:R.string.drawer_add_account);
							vh.imageView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_content_add));
							return;
						}
						case 1:{ // Manage
							vh.textView.setText(mIsPageListing?R.string.drawer_manage_pages:R.string.drawer_manage_accounts);
							vh.imageView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_action_settings));
							return;
						}
					}
					return;
				}
			}
			Tweeter.fillLoadQueuedData();
		}

		private BitmapDrawable getResourceDrawable(int resId, int targetWidth, int targetHeight){
			if(resId == 0)
				return null;
			if(mSizedBitmapResources.get(resId) != null)
				return mSizedBitmapResources.get(resId);
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeResource(getResources(), resId, options);
			options.inSampleSize = 1;
			while(options.outWidth / options.inSampleSize / 2 >= targetWidth && options.outHeight / options.inSampleSize / 2 >= targetHeight)
				options.inSampleSize <<= 1;
			options.inJustDecodeBounds = false;
			Bitmap bm = BitmapFactory.decodeResource(getResources(), resId, options);
			mSizedBitmapResources.put(resId, new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(bm, options.outWidth / options.inSampleSize, options.outHeight / options.inSampleSize, true)));
			bm.recycle();
			return getResourceDrawable(resId, targetWidth, targetHeight);
		}

		public void selectPage(int position) {
			if(position != mCurrentPage){
				notifyItemChanged(mCurrentPage +1);
				if (mCallbacks != null)
					mCallbacks.onNavigationDrawerItemSelected(position);
				mCurrentPage = position;
				notifyItemChanged(position+1);
			}
		}

		public void selectUser(TwitterEngine user) {
			if (mCurrentUser != null){
				mPageAdapter.notifyItemChanged(mPageAdapter.mListedUsers.indexOf(mCurrentUser.getTweeter())+1);
			}
			mCurrentUser = user;
			mCurrentUserId = user==null ? 0 : user.getUserId();
			if (mCurrentUser != null) {
				mPageAdapter.notifyItemChanged(mPageAdapter.mListedUsers.indexOf(mCurrentUser.getTweeter()) + 1);
				onUserInformationChanged(user.getTweeter());
			}
			mPageAdapter.notifyItemChanged(0);
		}

		@Override
		public boolean onCheckCanStartDrag(ViewHolder vh, int position, int x, int y) {
			if(!mListEditMode) return false;
			if(position == 0 || getItemViewType(position) == 3)
				return false;
			return (x > vh.actionButton.getLeft() - vh.actionButton.getPaddingLeft() && x < vh.actionButton.getRight() + vh.actionButton.getPaddingRight() && y > vh.actionButton.getTop() - vh.actionButton.getPaddingTop() && y < vh.actionButton.getBottom() + vh.actionButton.getPaddingBottom());
		}

		@Override
		public ItemDraggableRange onGetItemDraggableRange(ViewHolder vh, int position) {
			if(mIsPageListing){
				if(getItemActualPosition(position) < Page.mSavedPageLength)
					return new ItemDraggableRange(1, Page.mSavedPageLength);
				else
					return new ItemDraggableRange(1 + Page.mSavedPageLength, mListedPages.size());
			}else
				return new ItemDraggableRange(1,mListedUsers.size());
		}

		public void moveItem(int from, int to){
			mListedPages.add(to, mListedPages.remove(from));
			Page.addPage(to, Page.removePage(from));
			Page.broadcastPageChange();
			notifyItemMoved(from+1, to+1);
		}

		@Override
		public void onMoveItem(int from, int to) {
			moveItem(from-1, to-1);
		}

		@Override
		public void onUserInformationChanged(Tweeter tweeter) {
			notifyDataSetChanged();
		}

		public class ViewHolder extends AbstractDraggableItemViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, View.OnLongClickListener{
			public TextView textView;
			public Switch useStream;
			public ImageView imageView,  imageViewUser;
			public ImageButton actionButton;

			public ImageView mViewAvatar, mViewAccountBackground;
			public TextView mViewName, mViewScreenName;
			public View mViewShowAccountList;

			public ViewHolder(View itemView) {
				super(itemView);
				textView = (TextView) itemView.findViewById(R.id.item_name);
				useStream = (Switch) itemView.findViewById(R.id.use_stream);
				imageView = (ImageView) itemView.findViewById(R.id.item_image);
				imageViewUser = (ImageView) itemView.findViewById(R.id.item_image_user);
				actionButton = (ImageButton) itemView.findViewById(R.id.action_button);

				mViewAccountBackground = (ImageView) itemView.findViewById(R.id.imgAccountBackground);
				mViewAvatar = (ImageView) itemView.findViewById(R.id.imgAvatar);
				mViewName = (TextView) itemView.findViewById(R.id.txtUsername);
				mViewScreenName = (TextView) itemView.findViewById(R.id.txtScreenName);
				mViewShowAccountList = itemView.findViewById(R.id.show_account_list);


				itemView.setOnClickListener(this);
				if(useStream!=null){
					useStream.setOnCheckedChangeListener(this);
					useStream.setOnLongClickListener(this);
				}
				if(actionButton != null){
					actionButton.setOnClickListener(this);
				}
				if(mViewShowAccountList!=null)
					mViewShowAccountList.setOnClickListener(this);
			}

			@Override
			public void onClick(View v) {
				int position = getItemActualPosition(getAdapterPosition());
				int viewType = getItemViewType();
				switch(viewType){
					case 0:{ // Nav Header
						if(v.equals(mViewShowAccountList)){
							mListEditMode = false;
							mIsPageListing = !mIsPageListing;
							mViewShowAccountList.setRotation(mIsPageListing ? 0 : 180);

							int newSize, oldSize;
							if(mIsPageListing){
								newSize = mPageAdapter.mListedPages.size() + mPageAdapter.mPageBtnCount;
								oldSize = mPageAdapter.mListedUsers.size() + mPageAdapter.mUserBtnCount;
							}else{
								oldSize = mPageAdapter.mListedPages.size() + mPageAdapter.mPageBtnCount;
								newSize = mPageAdapter.mListedUsers.size() + mPageAdapter.mUserBtnCount;
							}
							int i = 0;
							for(; i <= Math.min(oldSize, newSize); i++) mPageAdapter.notifyItemChanged(i+1);
							for(; i <= newSize; i++) mPageAdapter.notifyItemInserted(i+1);
							for(; i <= oldSize; i++) mPageAdapter.notifyItemRemoved(i+1);
							RotateAnimation ani = new RotateAnimation(-180, 0, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
							ani.setDuration(300);
							ani.setInterpolator(getActivity(), android.R.anim.accelerate_decelerate_interpolator);
							mViewShowAccountList.startAnimation(ani);
						}
						return;
					}
					case 1:{ // Page
						if(v.equals(actionButton)){
							int newIndex = Page.removePage(position).getParentPageIndex();
							Page.broadcastPageChange();
							if(newIndex != -1)
								selectPage(newIndex);
						}else if(mListEditMode){
							if(position >= Page.mSavedPageLength){
								moveItem(position, Page.mSavedPageLength);
								Page.mSavedPageLength++;
								Page.broadcastPageChange();
							}
						}else{
							selectPage(position);
							if (isDrawerOpen())
								mDrawerLayout.closeDrawer(mFragmentContainerView);
						}
						return;
					}
					case 2:{ //User
						if(!mListEditMode){
							selectUser(TwitterEngine.getTwitterEngine(getActivity(), mListedUsers.get(position).user_id));
						}
						return;
					}
					case 3:{ // Button
						switch(position){
							case 0:{ // Add
								if(mIsPageListing){

								}else{
									AccountManager.get(getActivity()).addAccount(getString(R.string.account_type), null, null, null, getActivity(), null, null);
								}
								return;
							}
							case 1:{ // Manage
								mListEditMode =!mListEditMode;
								notifyDataSetChanged();
								return;
							}
						}
					}
				}
			}

			@Override
			public void onCheckedChanged(CompoundButton v, boolean isChecked) {
				int usr = getAdapterPosition() - 1;
				TwitterEngine.StreamableTwitterEngine engine = TwitterEngine.getTwitterEngine(getActivity(), mListedUsers.get(usr).user_id);
				TwitterStreamServiceReceiver.turnStreamOn(engine, isChecked);
				mHandler.sendMessageDelayed(Message.obtain(mHandler, MESSAGE_SERVICE_TIMEOUT, isChecked?2:1, 0, engine), SERVICE_COMMUNICATION_TIMEOUT);
				mStreamPowerChangeWaitingEngines.add(engine);
				v.setEnabled(false);
			}

			@Override
			public boolean onLongClick(View v) {
				Toast.makeText(getActivity(), useStream.isChecked()?R.string.drawer_stream_btn_off:R.string.drawer_stream_btn_on, Toast.LENGTH_SHORT).show();
				return false;
			}
		}
	}


	public interface NavigationDrawerCallbacks {
		void onNavigationDrawerItemSelected(int position);
	}
}
