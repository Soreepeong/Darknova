package com.soreepeong.darknova.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.core.OAuth;
import com.soreepeong.darknova.extractors.ImageExtractor;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.ui.fragments.MediaFragment;
import com.soreepeong.darknova.ui.view.SwipeableViewPager;

import java.util.ArrayList;
import java.util.regex.Matcher;

/**
 * Created by Soreepeong on 2015-06-02.
 */
public class MediaPreviewActivity extends AppCompatActivity implements ImageCache.OnImageCacheReadyListener, View.OnClickListener, Handler.Callback, SwipeableViewPager.OnPageChangeListener {

	private static final int MESSAGE_HIDE_ACTIONBAR = 1;
	private static final int HIDE_DELAY = 3000;
	public String mInitiatorUrl;
	private Handler mHandler = new Handler(this);
	private Toolbar mViewActionbarToolbar;
	private ActionBar mActionBar;
	private ImageCache mImageCache;
	private RecyclerView mViewImageList;
	private SwipeableViewPager mViewPager;
	private View mViewRoot;
	private MediaFragmentAdapter mPagerAdapter;
	private ImageAdapter mAdapter;
	private PaddedLayoutManager mLayoutManager;
	private ArrayList<Image> mImageList;
	private int mLastViewPagerPage;

	private int mBackgroundColorIndex;
	private int[] mBackgroundColors = new int[]{0xA0000000, 0xFFFFFFFF, 0xFF000000};

	public static void previewTweetImages(Context context, OAuth auth, Tweet t, int selectedIndex) {
		ArrayList<Image> images = new ArrayList<>();
		for (Entities.Entity e : t.entities.entities) {
			if (e instanceof Entities.MediaEntity)
				images.add(new Image(t, auth, (Entities.MediaEntity) e));
		}
		if (images.size() > 0) {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(images.get(0).mSourceUrl), context, MediaPreviewActivity.class);
			intent.putExtra("pictures", images);
			intent.putExtra("index", selectedIndex);
			context.startActivity(intent);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!getIntent().getAction().equals(Intent.ACTION_VIEW) || !extractAction(getIntent())) {
			finish();
			return;
		}
		setContentView(R.layout.activity_mediapreview);
		mViewRoot = findViewById(R.id.root);

		mImageCache = ImageCache.getCache(this, this);

		mViewActionbarToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
		mViewImageList = (RecyclerView) findViewById(R.id.images_list);
		mViewPager = (SwipeableViewPager) findViewById(R.id.pager);

		setSupportActionBar(mViewActionbarToolbar);
		mActionBar = getSupportActionBar();

		mAdapter = new ImageAdapter();
		mLayoutManager = new PaddedLayoutManager(this);
		mViewImageList.setLayoutManager(mLayoutManager);
		mViewImageList.setAdapter(mAdapter);
		mViewImageList.setVisibility(mImageList.size() > 1 ? View.VISIBLE : View.GONE);
		mViewPager.setAdapter(mPagerAdapter = new MediaFragmentAdapter(getSupportFragmentManager()));
		mViewPager.setOnPageChangeListener(this);
		int position = getIntent().getIntExtra("index", 0);
		mViewPager.setCurrentItem(position, false);
		if (mImageList.get(position).mRelatedTweet != null && !mImageList.get(position).mRelatedTweet.info.stub) {
			mActionBar.setTitle(mImageList.get(position).mRelatedTweet.user.screen_name);
			mActionBar.setSubtitle(mImageList.get(position).mRelatedTweet.text);
		} else {
			mActionBar.setTitle(mImageList.get(position).mSourceUrl);
			mActionBar.setSubtitle(mImageList.get(position).mOriginalUrl);
		}
		mViewActionbarToolbar.setOnClickListener(this);
	}

	private boolean extractAction(Intent intent) {
		mInitiatorUrl = intent.getDataString();
		mImageList = intent.getParcelableArrayListExtra("pictures");
		if (mImageList == null) {
			mImageList = new ArrayList<>();
			Matcher twitterPicMatcher = ImageExtractor.mTwitterPicPattern.matcher(mInitiatorUrl);
			if (twitterPicMatcher.matches()) {
				try {
					mImageList.add(new Image(null, null, null, null, mInitiatorUrl, null, Tweet.getTweet(Long.parseLong(twitterPicMatcher.group(2))), true));
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (ImageExtractor.mRedirectingTwitterPicPattern.matcher(mInitiatorUrl).matches()) {
				mImageList.add(new Image(null, null, null, null, mInitiatorUrl, null, null, true));
			} else {
				if (intent.getType() != null) {
					if (intent.getType().startsWith("image/"))
						mImageList.add(new Image(mInitiatorUrl, intent.getType(), null, mInitiatorUrl, mInitiatorUrl, null, null, false));
				} else if (ImageExtractor.mFileNameGetter.matcher(mInitiatorUrl).matches())
					mImageList.add(new Image(mInitiatorUrl, "image/*", null, mInitiatorUrl, mInitiatorUrl, null, null, false));
			}
		}
		return !mImageList.isEmpty();
	}

	@Override
	public void onImageCacheReady(ImageCache cache) {
		mImageCache = cache;
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_mediapreview_toolbar, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.change_background: {
				mBackgroundColorIndex = (mBackgroundColorIndex + 1) % mBackgroundColors.length;
				mViewRoot.setBackgroundColor(mBackgroundColors[mBackgroundColorIndex]);
				return true;
			}
		}
		if (mPagerAdapter.mFragments.get(mViewPager.getCurrentItem()) != null)
			mPagerAdapter.mFragments.get(mViewPager.getCurrentItem()).onOptionsItemSelected(item.getItemId());
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		mHandler.removeMessages(MESSAGE_HIDE_ACTIONBAR);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		return super.onMenuOpened(featureId, menu);
	}

	public void setAtRight(Image initiator, boolean atRight, boolean canScrollHorizontally) {
		if (initiator != null && initiator != mImageList.get(mViewPager.getCurrentItem()))
			return;
		mViewPager.setUseRightDrag((atRight && mViewPager.getCurrentItem() != mImageList.size() - 1) || !canScrollHorizontally);
	}

	public void setAtLeft(Image initiator, boolean atLeft, boolean canScrollHorizontally) {
		if (initiator != null && initiator != mImageList.get(mViewPager.getCurrentItem()))
			return;
		mViewPager.setUseLeftDrag((atLeft && mViewPager.getCurrentItem() != 0) || !canScrollHorizontally);
	}

	public void showActionBar(Image initiator) {
		if (initiator != null && initiator != mImageList.get(mViewPager.getCurrentItem()))
			return;
		mHandler.removeMessages(MESSAGE_HIDE_ACTIONBAR);
		if (mViewActionbarToolbar.getVisibility() == View.VISIBLE) return;
		mViewActionbarToolbar.setVisibility(View.VISIBLE);
		mActionBar.show();
		mViewActionbarToolbar.clearAnimation();
		mViewActionbarToolbar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.actionbar_show));
		if (mImageList.size() < 2) return;
		mViewImageList.clearAnimation();
		mViewImageList.setVisibility(View.VISIBLE);
		mViewImageList.startAnimation(AnimationUtils.loadAnimation(this, R.anim.newtweet_show));
	}

	public void hideActionBar(Image initiator) {
		if (initiator != null && initiator != mImageList.get(mViewPager.getCurrentItem()))
			return;
		mHandler.removeMessages(MESSAGE_HIDE_ACTIONBAR);
		if (mViewActionbarToolbar.getVisibility() != View.VISIBLE) return;
		mActionBar.hide();
		ResTools.hideWithAnimation(this, mViewActionbarToolbar, R.anim.actionbar_hide, true);
		if (mImageList.size() < 2) return;
		ResTools.hideWithAnimation(this, mViewImageList, R.anim.newtweet_hide, true);
	}

	public void hideActionBarDelayed(Image initiator) {
		if (initiator != null && initiator != mImageList.get(mViewPager.getCurrentItem()))
			return;
		mHandler.removeMessages(MESSAGE_HIDE_ACTIONBAR);
		mHandler.sendEmptyMessageDelayed(MESSAGE_HIDE_ACTIONBAR, HIDE_DELAY);
	}

	public void toggleActionBarVisibility(Image initiator) {
		if (initiator != null && initiator != mImageList.get(mViewPager.getCurrentItem()))
			return;
		if (mActionBar.isShowing())
			hideActionBar(initiator);
		else
			showActionBar(initiator);
	}

	@Override
	public void onClick(View v) {
		if (v.equals(mViewActionbarToolbar)) {
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MESSAGE_HIDE_ACTIONBAR:
				hideActionBar(null);
				return true;
		}
		return false;
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

	}

	@Override
	public void onPageSelected(int position) {
		if (mPagerAdapter.mFragments.get(mLastViewPagerPage) != null)
			mPagerAdapter.mFragments.get(mLastViewPagerPage).onPageLeave();
		int lastPosition = mLastViewPagerPage;
		mLastViewPagerPage = position;

		mPagerAdapter.requireEnterPage = position;
		if (mPagerAdapter.mFragments.get(position) != null)
			mPagerAdapter.enterPage(position);

		mAdapter.notifyItemChanged(lastPosition);
		mAdapter.notifyItemChanged(mLastViewPagerPage);

		if (mImageList.get(position).mRelatedTweet != null && !mImageList.get(position).mRelatedTweet.info.stub) {
			mActionBar.setTitle(mImageList.get(position).mRelatedTweet.user.screen_name);
			mActionBar.setSubtitle(mImageList.get(position).mRelatedTweet.text);
		} else {
			mActionBar.setTitle(mImageList.get(position).mSourceUrl);
			mActionBar.setSubtitle(mImageList.get(position).mOriginalUrl);
		}

		mLayoutManager.scrollToPosition(position);

		invalidateOptionsMenu();
	}

	@Override
	public void onPageScrollStateChanged(int state) {
		if (state == ViewPager.SCROLL_STATE_IDLE) {
			for (int i = Math.max(0, mViewPager.getCurrentItem() - 1), i_ = Math.min(mViewPager.getCurrentItem() + 1, mImageList.size() - 1); i <= i_; i++) {
				if (mPagerAdapter.mFragments.get(i) != null && i != mViewPager.getCurrentItem())
					mPagerAdapter.mFragments.get(i).resetPosition();
			}
		}
	}

	public static class Image implements Parcelable {
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<Image> CREATOR = new Parcelable.Creator<Image>() {
			@Override
			public Image createFromParcel(Parcel in) {
				return new Image(in);
			}

			@Override
			public Image[] newArray(int size) {
				return new Image[size];
			}
		};
		public String mOriginalUrl;
		public String mOriginalContentType;
		public String mThumbnailUrl;
		public String mResizedUrl;
		public String mSourceUrl;
		public OAuth mAuthInfo;
		public Tweet mRelatedTweet;
		public boolean mRequireExpansion;

		public Image(Tweet tweet, OAuth auth, Entities.MediaEntity entity) {
			if (entity.variants == null) {
				mOriginalUrl = entity.media_url + ":orig";
				mResizedUrl = entity.media_url;
				mOriginalContentType = "image/*";
			} else {
				mResizedUrl = entity.media_url;
				mOriginalUrl = entity.variants.get(0).url;
				mOriginalContentType = entity.variants.get(0).contentType;
			}
			mSourceUrl = entity.expanded_url;
			mAuthInfo = auth;
			mRelatedTweet = tweet;
			mThumbnailUrl = entity.media_url; // + ":thumb";
		}

		public Image(String orig, String cType, String resized, String thumb, String source, OAuth auth, Tweet related, boolean needExpand) {
			mOriginalUrl = orig;
			mOriginalContentType = cType;
			mResizedUrl = resized;
			mThumbnailUrl = thumb;
			mSourceUrl = source;
			mAuthInfo = auth;
			mRelatedTweet = related;
			mRequireExpansion = needExpand;
		}

		protected Image(Parcel in) {
			mOriginalUrl = in.readString();
			mOriginalContentType = in.readString();
			mThumbnailUrl = in.readString();
			mResizedUrl = in.readString();
			mSourceUrl = in.readString();
			mAuthInfo = (OAuth) in.readValue(OAuth.class.getClassLoader());
			mRelatedTweet = (Tweet) in.readValue(Tweet.class.getClassLoader());
			mRequireExpansion = in.readByte() != 0x00;
		}

		@Override
		public boolean equals(Object o) {
			return super.equals(o);
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(mOriginalUrl);
			dest.writeString(mOriginalContentType);
			dest.writeString(mThumbnailUrl);
			dest.writeString(mResizedUrl);
			dest.writeString(mSourceUrl);
			dest.writeValue(mAuthInfo);
			dest.writeValue(mRelatedTweet);
			dest.writeByte((byte) (mRequireExpansion ? 0x01 : 0x00));
		}
	}

	private class PaddedLayoutManager extends LinearLayoutManager {
		PaddedLayoutManager(Context context) {
			super(context);
			setOrientation(HORIZONTAL);
		}

		@Override
		public int getPaddingLeft() {
			return (getWidth() - getResources().getDimensionPixelSize(R.dimen.col_thumbnail_size)) / 2;
		}

		@Override
		public int getPaddingRight() {
			return getPaddingLeft();
		}
	}

	public class MediaFragmentAdapter extends FragmentPagerAdapter {

		public final SparseArray<MediaFragment> mFragments = new SparseArray<>();
		int requireEnterPage = -1;

		public MediaFragmentAdapter(FragmentManager fm) {
			super(fm);
		}

		public void enterPage(int position) {
			if (requireEnterPage != position)
				return;
			mFragments.get(position).onPageEnter();
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			MediaFragment fragment = (MediaFragment) super.instantiateItem(container, position);
			mFragments.put(position, fragment);
			enterPage(position);
			return fragment;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			super.destroyItem(container, position, object);
			mFragments.get(position).onPageLeave();
			mFragments.remove(position);
		}

		@Override
		public Fragment getItem(int position) {
			return MediaFragment.newInstance(mImageList.get(position));
		}

		@Override
		public int getItemPosition(Object object) {
			MediaFragment f = (MediaFragment) object;
			int pos = mImageList.indexOf(f.mImage);
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
		public int getCount() {
			return mImageList.size();
		}
	}

	public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

		@Override
		public int getItemViewType(int position) {
			return R.layout.col_thumbnail;
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			return new ViewHolder(getLayoutInflater().inflate(viewType, parent, false));
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			holder.bindViewHolder(position);
		}

		@Override
		public int getItemCount() {
			return mImageList.size();
		}

		public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
			ImageView mViewThumbnail;

			public ViewHolder(View itemView) {
				super(itemView);
				mViewThumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
				mViewThumbnail.setOnClickListener(this);
			}

			public void bindViewHolder(int position) {
				Image img = mImageList.get(position);
				String url = img.mThumbnailUrl;
				if (mImageCache == null)
					mViewThumbnail.setImageDrawable(null);
				else if (url != null)
					mImageCache.assignImageView(mViewThumbnail, url, img.mAuthInfo);
				else
					mImageCache.assignImageView(mViewThumbnail, null, null);
			}

			@Override
			public void onClick(View v) {
				mViewPager.setCurrentItem(getAdapterPosition());
			}
		}
	}
}
