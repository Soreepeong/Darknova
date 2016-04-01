package com.soreepeong.darknova.ui.viewholder;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.soreepeong.darknova.Darknova;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.PageElement;
import com.soreepeong.darknova.twitter.ObjectWithId;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.ui.MainActivity;
import com.soreepeong.darknova.ui.MediaPreviewActivity;
import com.soreepeong.darknova.ui.span.TweetSpanner;

/**
 * @author Soreepeong
 */
public class UserHeaderViewHolder<_T extends ObjectWithId> extends CustomViewHolder<_T>{
	private final TextView mId, mName, mBio, mHomepage, mLocation;
	private final ImageView mUserImage, mUserBannerImage;
	private final Button mFollowers, mFriends, mTweets, mFavorites;
	private final View mIsProtected, mIsVerified;
	private Tweeter user;

	// TODO Design

	public UserHeaderViewHolder(View itemView){
		super(itemView);
		mId = (TextView) itemView.findViewById(R.id.user_id);
		mName = (TextView) itemView.findViewById(R.id.user_name);
		mLocation = (TextView) itemView.findViewById(R.id.user_location);
		mHomepage = (TextView) itemView.findViewById(R.id.user_homepage);
		mBio = (TextView) itemView.findViewById(R.id.user_bio);
		mUserImage = (ImageView) itemView.findViewById(R.id.user_image);
		mUserBannerImage = (ImageView) itemView.findViewById(R.id.user_banner);
		mFollowers = (Button) itemView.findViewById(R.id.followers);
		mFriends = (Button) itemView.findViewById(R.id.friends);
		mTweets = (Button) itemView.findViewById(R.id.tweets);
		mFavorites = (Button) itemView.findViewById(R.id.favorites);
		mIsProtected = itemView.findViewById(R.id.imgInfoProtected);
		mIsVerified = itemView.findViewById(R.id.imgInfoVerified);
		mUserImage.setOnClickListener(this);
		mUserBannerImage.setOnClickListener(this);
		mTweets.setOnClickListener(this);
		mFavorites.setOnClickListener(this);
	}

	@Override
	public void releaseMemory(){
		Darknova.img.assignImageView(mUserBannerImage, null, null);
		Darknova.img.assignImageView(mUserImage, null, null);
		user = null;
	}

	@Override
	public void onClick(View v){
		if(v.equals(mUserImage)){
			if(user.profile_image_url != null)
				MediaPreviewActivity.previewImage(v.getContext(), user.profile_image_url[user.profile_image_url.length - 1], true);
		}else if(v.equals(mUserBannerImage)){
			if(user.profile_banner_url != null)
				MediaPreviewActivity.previewImage(v.getContext(), user.profile_banner_url[user.profile_banner_url.length - 1], true);
		}else if(v.equals(mTweets)){
			Page.templatePageUser(user.user_id, user.screen_name, (MainActivity) mFragment.getActivity(), PageElement.FUNCTION_USER_TIMELINE, Page.indexOf(mFragment.mPage));
		}else if(v.equals(mFavorites)){
			Page.templatePageUser(user.user_id, user.screen_name, (MainActivity) mFragment.getActivity(), PageElement.FUNCTION_USER_FAVORITES, Page.indexOf(mFragment.mPage));
		}
	}

	@Override
	public void bindViewHolder(int position){
		PageElement.ElementHeader header = (PageElement.ElementHeader) mAdapter.getItem(position);
		user = Tweeter.getTweeter(header.getElement().id, header.getElement().name);
		mId.setText(user.screen_name);
		mName.setText(user.name);
		if(user.location == null || user.location.isEmpty())
			mLocation.setVisibility(View.GONE);
		else{
			mLocation.setVisibility(View.VISIBLE);
			mLocation.setText(user.location);
		}
		if(user.description == null || user.description.isEmpty())
			mBio.setVisibility(View.GONE);
		else{
			mBio.setVisibility(View.VISIBLE);
			mBio.setText(TweetSpanner.includeEntities(user.description, user.entities_description, Darknova.img, mHomepage.getLineHeight(), mBio.getText()));
		}
		if(user.url == null || user.url.isEmpty())
			mHomepage.setVisibility(View.GONE);
		else{
			mHomepage.setVisibility(View.VISIBLE);
			mHomepage.setText(user.url == null ? "" : TweetSpanner.includeEntities(user.url, user.entities_url, Darknova.img, mHomepage.getLineHeight(), mHomepage.getText()));
		}
		Darknova.img.assignImageView(mUserImage, user.getProfileImageUrl(), null);
		Darknova.img.assignImageView(mUserBannerImage, user.getProfileBannerUrl(), null);
		mFollowers.setText(user.followers_count + " followers");
		mFriends.setText(user.friends_count + " friends");
		mTweets.setText(user.statuses_count + "\ntweets");
		mFavorites.setText(user.favourites_count + "\nfavorites");
		mIsProtected.setVisibility(user._protected ? View.VISIBLE : View.GONE);
		mIsVerified.setVisibility(user.verified ? View.VISIBLE : View.GONE);
		for(PageElement e : mFragment.mPage.elements)
			if(e.function == PageElement.FUNCTION_USER_TIMELINE){
				mTweets.setEnabled(false);
				mFavorites.setEnabled(true);
			}else if(e.function == PageElement.FUNCTION_USER_FAVORITES){
				mTweets.setEnabled(true);
				mFavorites.setEnabled(false);
			}
	}
}
