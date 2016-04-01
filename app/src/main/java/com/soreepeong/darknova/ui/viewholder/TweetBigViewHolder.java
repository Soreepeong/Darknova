package com.soreepeong.darknova.ui.viewholder;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.soreepeong.darknova.Darknova;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.PageElement;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.ui.MainActivity;
import com.soreepeong.darknova.ui.fragments.TimelineFragment;
import com.soreepeong.darknova.ui.span.TweetSpanner;
import com.soreepeong.darknova.ui.span.UserImageSpan;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 * @author Soreepeong
 */
public class TweetBigViewHolder extends CustomViewHolder<Tweet> implements View.OnLongClickListener{
	private final ImageView mImage, imgInfoFavorited, imgInfoProtected, imgInfoRetweeted, imgInfoReplied, imgInfoVerified;
	private final TextView mUserId, mUserName, mText, mDescription;
	private final Button mRetweets, mFavorites;
	private final ImageButton mReply, mRetweet, mFavorite, mShare;
	private final View mUser;
	private Tweet mLastBoundTweet;

	public TweetBigViewHolder(View v){
		super(v);
		mImage = (ImageView) itemView.findViewById(R.id.user_picture);
		imgInfoFavorited = (ImageView) v.findViewById(R.id.imgInfoFavorited);
		imgInfoProtected = (ImageView) v.findViewById(R.id.imgInfoProtected);
		imgInfoRetweeted = (ImageView) v.findViewById(R.id.imgInfoRetweeted);
		imgInfoReplied = (ImageView) v.findViewById(R.id.imgInfoReplied);
		imgInfoVerified = (ImageView) v.findViewById(R.id.imgInfoVerified);
		mUser = itemView.findViewById(R.id.user_area);
		mUserId = (TextView) itemView.findViewById(R.id.user_id);
		mUserName = (TextView) itemView.findViewById(R.id.user_name);
		mText = (TextView) itemView.findViewById(R.id.text);
		mDescription = (TextView) itemView.findViewById(R.id.description);
		mRetweets = (Button) itemView.findViewById(R.id.retweet_count);
		mFavorites = (Button) itemView.findViewById(R.id.favorite_count);
		mReply = (ImageButton) itemView.findViewById(R.id.reply);
		mRetweet = (ImageButton) itemView.findViewById(R.id.retweet);
		mFavorite = (ImageButton) itemView.findViewById(R.id.favorite);
		mShare = (ImageButton) itemView.findViewById(R.id.share);
		mRetweets.setOnClickListener(this);
		mFavorites.setOnClickListener(this);
		mReply.setOnClickListener(this);
		mReply.setOnLongClickListener(this);
		mRetweet.setOnClickListener(this);
		mRetweet.setOnLongClickListener(this);
		mFavorite.setOnClickListener(this);
		mShare.setOnClickListener(this);
		mUser.setOnClickListener(this);
	}

	@Override
	public void releaseMemory(){
		Darknova.img.assignImageView(mImage, null, null);
		mLastBoundTweet = null;
	}

	@Override
	public void onClick(View v){
		final Tweet t = mLastBoundTweet;
		if(v.equals(mRetweets)){
		}else if(v.equals(mFavorites)){
		}else if(v.equals(mReply)){
			HashMap<Tweeter, Boolean> map = new HashMap<>();
			String s = "@" + t.user.screen_name + " ";
			map.put(t.user, true);
			for(Entities.Entity ent : t.entities.list)
				if(ent instanceof Entities.MentionsEntity)
					if(map.put(((Entities.MentionsEntity) ent).tweeter, true) != null)
						s += "@" + ((Entities.MentionsEntity) ent).tweeter.screen_name + " ";
			((MainActivity) mFragment.getActivity()).getNewTweetFragment().setInReplyTo(t);
			((MainActivity) mFragment.getActivity()).getNewTweetFragment().insertText(s);
			((MainActivity) mFragment.getActivity()).getNewTweetFragment().showNewTweet();
		}else if(v.equals(mRetweet)){
			if(t.retweeted_status == null && t.user._protected){
				((MainActivity) mFragment.getActivity()).getNewTweetFragment().setInReplyTo(t);
				((MainActivity) mFragment.getActivity()).getNewTweetFragment().insertText("QT @" + (t.user._protected ? "***" : t.user.screen_name) + ": " + t.text);
				((MainActivity) mFragment.getActivity()).getNewTweetFragment().showNewTweet();
			}else{
				new AlertDialog.Builder(mFragment.getActivity())
						.setMessage("Retweet?")
						.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener(){
							@Override
							public void onClick(DialogInterface dialog, int which){
								final ArrayList<TwitterEngine> postFrom = ((MainActivity) mFragment.getActivity()).getNewTweetFragment().getPostFromList();
								final long id = t.id;
								new Thread(){
									@Override
									public void run(){
										try{
											for(TwitterEngine e : postFrom)
												e.postRetweet(id);
										}catch(Exception e){
											e.printStackTrace();
										}
									}
								}.start();
							}
						})
						.setNegativeButton(android.R.string.no, null)
						.show();
			}
		}else if(v.equals(mFavorite)){
			final ArrayList<TwitterEngine> postFrom = ((MainActivity) mFragment.getActivity()).getNewTweetFragment().getPostFromList();
			final long id = t.id;
			new Thread(){
				@Override
				public void run(){
					try{
						for(TwitterEngine e : postFrom)
							e.postFavorite(id);
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			}.start();
		}else if(v.equals(mShare)){
			Intent sharingIntent = new Intent(Intent.ACTION_SEND);
			sharingIntent.setType("text/plain");
			sharingIntent.putExtra(Intent.EXTRA_TEXT, t.toString());
			mFragment.startActivity(Intent.createChooser(sharingIntent, t.toString()));
		}else if(v.equals(mUser)){
			Page.templatePageUser(t.user.user_id, t.user.screen_name, (MainActivity) mFragment.getActivity(), PageElement.FUNCTION_USER_TIMELINE);
		}
	}

	private String getDescriptionString(Tweet tweet){
		String sTime;
		sTime = DateFormat.getLongDateFormat(mFragment.getActivity()).format(new Date(tweet.created_at));
		return mFragment.getString(R.string.tweet_description).replace("${time}", sTime).replace("${via}", tweet.source);
	}

	public void updateSelectionStatus(Tweet tweet){
		itemView.setSelected(mFragment.mSelectedList.contains(tweet));
	}

	@Override
	public void updateView(){
		int position = mAdapter.adapterPositionToListIndex(getLayoutPosition());
		if(position < 0 || position >= (mFragment.mQuickFilteredList != null ? mFragment.mQuickFilteredList.size() : mFragment.mList.size()))
			return;
		TimelineFragment.FilteredTweet filtered = mFragment.mQuickFilteredList != null ? (TimelineFragment.FilteredTweet) mFragment.mQuickFilteredList.get(position) : null;
		Tweet tweet = filtered != null ? filtered.mObject : mFragment.mList.get(position);
		if(tweet != mLastBoundTweet)
			return;
		if(tweet.info.stub){
			mText.setText("");
			mDescription.setText("Refreshing...");
			mUserName.setText("?");
			mUserId.setText("?");
			mText.setPaintFlags(mText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
			mRetweets.setVisibility(View.GONE);
			mFavorites.setVisibility(View.GONE);
			return;
		}
		if(filtered != null && filtered.spannedText != null){
			for(UserImageSpan s : filtered.spannedText.getSpans(0, filtered.spannedText.length(), UserImageSpan.class))
				s.setLineHeight(mText.getLineHeight());
			mText.setText(filtered.spannedText);
		}else
			mText.setText(TweetSpanner.make(tweet, Darknova.img, mText.getLineHeight(), mText.getText()));
		mDescription.setText(getDescriptionString(tweet));
		mUserName.setText(filtered != null && filtered.spannedName != null ? filtered.spannedName : tweet.user.name);
		mUserId.setText(filtered != null && filtered.spannedId != null ? filtered.spannedId : tweet.user.screen_name);
		updateSelectionStatus(tweet);
		mText.setPaintFlags((mText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG) | (tweet.removed ? Paint.STRIKE_THRU_TEXT_FLAG : 0));
		mRetweets.setVisibility(tweet.retweet_count > 0 ? View.VISIBLE : View.GONE);
		mFavorites.setVisibility(tweet.favorite_count > 0 ? View.VISIBLE : View.GONE);
		mRetweets.setText(tweet.retweet_count + " Retweets");
		mFavorites.setText(tweet.favorite_count + " Favorites");
	}

	public void bindViewHolder(int position){
		TimelineFragment.FilteredTweet filtered = mFragment.mQuickFilteredList != null ? (TimelineFragment.FilteredTweet) mFragment.mQuickFilteredList.get(position) : null;
		Tweet tweet = mFragment.mQuickFilteredList != null ? filtered.mObject : mFragment.mList.get(position);
		if(mLastBoundTweet == tweet){ // skip - already prepared
			updateView();
			return;
		}
		mLastBoundTweet = tweet;
		updateView();
		if(tweet.info.stub){
			imgInfoReplied.setVisibility(View.GONE);
			imgInfoFavorited.setVisibility(View.GONE);
			imgInfoRetweeted.setVisibility(View.GONE);
			imgInfoVerified.setVisibility(View.GONE);
			imgInfoProtected.setVisibility(View.GONE);
			Darknova.img.assignImageView(mImage, null, null);
			return;
		}
		imgInfoReplied.setVisibility(tweet.in_reply_to_status == null ? View.GONE : View.VISIBLE);
		imgInfoFavorited.setVisibility(tweet.favoriteExists() ? View.VISIBLE : View.GONE);
		imgInfoRetweeted.setVisibility(tweet.retweetExists() ? View.VISIBLE : View.GONE);
		imgInfoVerified.setVisibility(tweet.user.verified ? View.VISIBLE : View.GONE);
		imgInfoProtected.setVisibility(tweet.user._protected ? View.VISIBLE : View.GONE);
		Darknova.img.assignImageView(mImage, tweet.user.getProfileImageUrl(), null);
	}

	@Override
	public boolean onLongClick(View v){
		final Tweet t = mLastBoundTweet;
		if(v.equals(mRetweet)){
			((MainActivity) mFragment.getActivity()).getNewTweetFragment().setInReplyTo(t);
			((MainActivity) mFragment.getActivity()).getNewTweetFragment().insertText("QT @" + (t.user._protected ? "***" : t.user.screen_name) + ": " + t.text);
			((MainActivity) mFragment.getActivity()).getNewTweetFragment().showNewTweet();
			return true;
		}else if(v.equals(mReply)){
			((MainActivity) mFragment.getActivity()).getNewTweetFragment().setInReplyTo(t);
			((MainActivity) mFragment.getActivity()).getNewTweetFragment().insertText("@" + t.user.screen_name + " ");
			((MainActivity) mFragment.getActivity()).getNewTweetFragment().showNewTweet();
			return true;
		}
		return false;
	}
}
