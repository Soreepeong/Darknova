package com.soreepeong.darknova.ui.viewholder;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.soreepeong.darknova.Darknova;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.PageElement;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.ui.MainActivity;
import com.soreepeong.darknova.ui.MediaPreviewActivity;
import com.soreepeong.darknova.ui.dragaction.DragInitiator;
import com.soreepeong.darknova.ui.dragaction.DragInitiatorButton;
import com.soreepeong.darknova.ui.dragaction.DragInitiatorCallbacks;
import com.soreepeong.darknova.ui.fragments.TimelineFragment;
import com.soreepeong.darknova.ui.span.TweetSpanner;
import com.soreepeong.darknova.ui.span.UserImageSpan;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Soreepeong
 */
public class TweetViewHolder extends CustomViewHolder<Tweet> implements DragInitiatorCallbacks, View.OnLongClickListener{

	private final ImageView imgUserPictureFull, imgUserPictureUp, imgUserPictureDown;
	private final TextView lblUserName, lblData, lblDescription,
			lblRetweetDescription;
	private final ImageView imgInfoFavorited, imgInfoProtected, imgInfoRetweeted, imgInfoReplied,
			imgInfoVerified, imgSelectedIndicator;
	private final LinearLayout divPreviews;
	private final ImageView[] arrPreviews;
	private final DragInitiatorButton dragInitiatorButton;
	private final View dragActionContainer;
	private final Button tweetActionReply, tweetActionRetweet, tweetActionFavorite;

	private Tweet mLastBoundTweet;
	private boolean mIsLongPress;

	@Override
	public String toString(){
		return "TVH: " + (mLastBoundTweet == null ? "null" : mLastBoundTweet.toString());
	}

	@Override
	public void releaseMemory(){
		Darknova.img.assignImageView(imgUserPictureDown, null, null);
		Darknova.img.assignImageView(imgUserPictureFull, null, null);
		Darknova.img.assignImageView(imgUserPictureUp, null, null);
		mLastBoundTweet = null;
	}

	public TweetViewHolder(View v){
		super(v);
		lblDescription = (TextView) v.findViewById(R.id.lblDescription);
		lblRetweetDescription = (TextView) v.findViewById(R.id.lblRetweetDescription);
		lblData = (TextView) v.findViewById(R.id.lblData);
		lblData.setMovementMethod(LinkMovementMethod.getInstance());
		lblUserName = (TextView) v.findViewById(R.id.user_name);
		lblUserName.setOnClickListener(this);
		imgUserPictureFull = (ImageView) v.findViewById(R.id.imgUserPictureFull);
		imgUserPictureUp = (ImageView) v.findViewById(R.id.imgUserPictureUp);
		imgUserPictureDown = (ImageView) v.findViewById(R.id.imgUserPictureDown);
		imgSelectedIndicator = (ImageView) v.findViewById(R.id.imgSelectedIndicator);
		imgInfoFavorited = (ImageView) v.findViewById(R.id.imgInfoFavorited);
		imgInfoProtected = (ImageView) v.findViewById(R.id.imgInfoProtected);
		imgInfoRetweeted = (ImageView) v.findViewById(R.id.imgInfoRetweeted);
		imgInfoReplied = (ImageView) v.findViewById(R.id.imgInfoReplied);
		imgInfoVerified = (ImageView) v.findViewById(R.id.imgInfoVerified);
		dragInitiatorButton = (DragInitiatorButton) v.findViewById(R.id.dragActionHandle);
		dragActionContainer = v.findViewById(R.id.drag_action_type_tweet);
		tweetActionReply = (Button) dragActionContainer.findViewById(R.id.tweetActionReply);
		tweetActionRetweet = (Button) dragActionContainer.findViewById(R.id.tweetActionRetweet);
		tweetActionFavorite = (Button) dragActionContainer.findViewById(R.id.tweetActionFavorite);
		divPreviews = (LinearLayout) v.findViewById(R.id.divPreviews);
		arrPreviews = new ImageView[divPreviews.getChildCount()];
		for(int i = 0; i < arrPreviews.length; i++){
			arrPreviews[i] = (ImageView) divPreviews.getChildAt(i);
			arrPreviews[i].setOnClickListener(this);
		}

		dragInitiatorButton.setDragInitiatorCallbacks(this);
		dragInitiatorButton.setOnClickListener(this);
		dragInitiatorButton.setOnLongClickListener(this);
		tweetActionReply.setOnClickListener(this);
		tweetActionRetweet.setOnClickListener(this);
		tweetActionFavorite.setOnClickListener(this);
		tweetActionReply.setOnLongClickListener(this);
		tweetActionRetweet.setOnLongClickListener(this);
		tweetActionFavorite.setOnLongClickListener(this);
	}

	private void previewEntity(Entities entities){
		int i = 0;
		for(Entities.Entity entity : entities.list){
			if( i >= arrPreviews.length ) continue;
			if(entity instanceof Entities.MediaEntity){
				arrPreviews[i].setVisibility(View.VISIBLE);
				if(Darknova.img != null)
					Darknova.img.assignImageView(arrPreviews[i++], ((Entities.MediaEntity) entity).media_url, null);
			}
		}
		divPreviews.setVisibility(i == 0 ? View.GONE : View.VISIBLE);
		((FrameLayout.LayoutParams) dragActionContainer.getLayoutParams()).bottomMargin =
				((FrameLayout.LayoutParams) dragInitiatorButton.getLayoutParams()).bottomMargin =
						i == 0 ? 0 : divPreviews.getLayoutParams().height
								+ ((LinearLayout.LayoutParams) divPreviews.getLayoutParams()).topMargin
								+ ((FrameLayout.LayoutParams) ((View) (divPreviews.getParent())).getLayoutParams()).bottomMargin;

		for(; i < arrPreviews.length; i++)
			arrPreviews[i].setVisibility(View.GONE);
	}

	private String getDescriptionString(Tweet tweet){
		String sTime;
		sTime = StringTools.unixtimeToDisplayTime(tweet.created_at);
		String rtByFormat = null;
		if(tweet.retweeted_status == null && tweet.retweet_count > 0)
			rtByFormat = mFragment.getString(tweet.retweet_count == 1 ? R.string.tweet_description_retweet_user_count : R.string.tweet_description_retweet_user_counts).replace("${count}", Long.toString(tweet.retweet_count));
		else if(tweet.retweeted_status != null)
			rtByFormat = mFragment.getString(R.string.tweet_description_retweet_user).replace("${user}", tweet.user.screen_name);
		if(rtByFormat != null)
			return mFragment.getString(R.string.tweet_description_retweet).replace("${time}", sTime).replace("${via}", tweet.source).replace("${retweet}", rtByFormat);
		else if(tweet.in_reply_to_status != null)
			return mFragment.getString(R.string.tweet_description_reply).replace("${time}", sTime).replace("${via}", tweet.source).replace("${reply}", tweet.in_reply_to_user.screen_name);
		else
			return mFragment.getString(R.string.tweet_description).replace("${time}", sTime).replace("${via}", tweet.source);
	}

	public void updateSelectionStatus(Tweet tweet){
		itemView.setSelected(mFragment.mSelectedList.contains(tweet));
		imgSelectedIndicator.setVisibility(itemView.isSelected() ? View.VISIBLE : View.GONE);
	}

	@Override
	public void updateView(){
		int position = mAdapter.adapterPositionToListIndex(getLayoutPosition());
		if(position < 0 || position >= (mFragment.mQuickFilteredList != null ? mFragment.mQuickFilteredList.size() : mFragment.mPage.getList().size()))
			return;
		TimelineFragment.FilteredTweet filtered = mFragment.mQuickFilteredList != null ? (TimelineFragment.FilteredTweet) mFragment.mQuickFilteredList.get(position) : null;
		Tweet tweet = filtered != null ? filtered.mObject : mFragment.mPage.getList().get(position);
		if(tweet != mLastBoundTweet)
			return;
		if(filtered != null && filtered.spannedText != null){
			for(UserImageSpan s : filtered.spannedText.getSpans(0, filtered.spannedText.length(), UserImageSpan.class))
				s.setLineHeight(lblData.getLineHeight());
			lblData.setText(filtered.spannedText);
		}else
			lblData.setText(TweetSpanner.make(tweet.retweeted_status == null ? tweet : tweet.retweeted_status, Darknova.img, lblData.getLineHeight(), lblData.getText()));
		if(tweet.retweeted_status != null){
			lblDescription.setText(getDescriptionString(tweet.retweeted_status));
			lblRetweetDescription.setText(getDescriptionString(tweet));
			lblUserName.setText(filtered != null && filtered.spannedId != null ? TextUtils.concat(filtered.spannedId, "/", filtered.spannedName) : tweet.retweeted_status.user.screen_name + "/" + tweet.retweeted_status.user.name);
			// lblData.setText(filtered != null && filtered.spannedText != null ? filtered.spannedText : tweet.retweeted_status.text);
		}else{
			lblDescription.setText(getDescriptionString(tweet));
			lblUserName.setText(filtered != null && filtered.spannedId != null ? TextUtils.concat(filtered.spannedId, "/", filtered.spannedName) : tweet.user.screen_name + "/" + tweet.user.name);
			// lblData.setText(filtered != null && filtered.spannedText != null ? filtered.spannedText : tweet.text);
		}
		lblData.setPaintFlags((lblData.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG) | ((tweet.removed || (tweet.retweeted_status != null && tweet.retweeted_status.removed)) ? Paint.STRIKE_THRU_TEXT_FLAG : 0));
		updateSelectionStatus(tweet);
	}

	public void bindViewHolder(int position){
		TimelineFragment.FilteredTweet filtered = mFragment.mQuickFilteredList != null ? (TimelineFragment.FilteredTweet) mFragment.mQuickFilteredList.get(position) : null;
		if(mFragment.mPage.getList() == null)
			return;
		Tweet tweet = mFragment.mQuickFilteredList != null ? filtered.mObject : mFragment.mPage.getList().get(position);
		if(mLastBoundTweet == tweet){ // skip - already prepared
			updateView();
			return;
		}
		mLastBoundTweet = tweet;
		updateView();
		if(tweet.retweeted_status == null){
			imgUserPictureUp.setVisibility(View.GONE);
			imgUserPictureDown.setVisibility(View.GONE);
			imgUserPictureFull.setVisibility(View.VISIBLE);
			lblRetweetDescription.setVisibility(View.GONE);
			imgInfoReplied.setVisibility(tweet.in_reply_to_status == null ? View.GONE : View.VISIBLE);
			imgInfoFavorited.setVisibility(tweet.favoriteExists() ? View.VISIBLE : View.GONE);
			imgInfoRetweeted.setVisibility(tweet.retweetExists() ? View.VISIBLE : View.GONE);
			imgInfoVerified.setVisibility(tweet.user.verified ? View.VISIBLE : View.GONE);
			imgInfoProtected.setVisibility(tweet.user._protected ? View.VISIBLE : View.GONE);
			Darknova.img.assignImageView(imgUserPictureDown, null, null);
			Darknova.img.assignImageView(imgUserPictureUp, null, null);
			Darknova.img.assignImageView(imgUserPictureFull, tweet.user.getProfileImageUrl(), null);
			previewEntity(tweet.entities);
		}else{
			imgUserPictureUp.setVisibility(View.VISIBLE);
			imgUserPictureDown.setVisibility(View.VISIBLE);
			imgUserPictureFull.setVisibility(View.GONE);
			lblRetweetDescription.setVisibility(View.VISIBLE);
			imgInfoReplied.setVisibility(tweet.retweeted_status.in_reply_to_status == null ? View.GONE : View.VISIBLE);
			imgInfoFavorited.setVisibility(tweet.retweeted_status.favoriteExists() ? View.VISIBLE : View.GONE);
			imgInfoRetweeted.setVisibility(tweet.retweeted_status.retweetExists() ? View.VISIBLE : View.GONE);
			imgInfoVerified.setVisibility(tweet.retweeted_status.user.verified ? View.VISIBLE : View.GONE);
			imgInfoProtected.setVisibility(tweet.retweeted_status.user._protected ? View.VISIBLE : View.GONE);
			Darknova.img.assignImageView(imgUserPictureDown, tweet.user.getProfileImageUrl(), null);
			Darknova.img.assignImageView(imgUserPictureUp, tweet.retweeted_status.user.getProfileImageUrl(), null);
			Darknova.img.assignImageView(imgUserPictureFull, null, null);
			previewEntity(tweet.retweeted_status.entities);
		}
	}

	@Override
	public void onClick(View v){
		int position = getAdapterPosition();
		final Tweet t = mFragment.mQuickFilteredList == null ? (Tweet) mAdapter.getItem(position) : ((TimelineFragment.FilteredTweet) mAdapter.getItem(position)).mObject;
		if(t == null)
			return;
		if(v.equals(itemView)){
			Page.templatePageTweet(t, (MainActivity) mFragment.getActivity());
		}else if(v.equals(tweetActionRetweet)){
			if(mIsLongPress || (t.retweeted_status == null && t.user._protected)){
				((MainActivity) mFragment.getActivity()).getNewTweetFragment().setInReplyTo(t);
				final Tweet t2 = t.retweeted_status == null ? t : t.retweeted_status;
				((MainActivity) mFragment.getActivity()).getNewTweetFragment().insertText("QT @" + (t2.user._protected ? "***" : t2.user.screen_name) + ": " + t2.text);
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
		}else if(v.equals(tweetActionReply)){
			if(mIsLongPress){
				((MainActivity) mFragment.getActivity()).getNewTweetFragment().setInReplyTo(t);
				((MainActivity) mFragment.getActivity()).getNewTweetFragment().insertText("@" + t.user.screen_name + " ");
				((MainActivity) mFragment.getActivity()).getNewTweetFragment().showNewTweet();
			}else{
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
			}
		}else if(v.equals(tweetActionFavorite)){
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
		}else if(v.equals(dragInitiatorButton)){
			if(!mFragment.mSelectedList.contains(t)){
				mFragment.mSelectedList.add(t);
				updateSelectionStatus(t);
			}else{
				mFragment.mSelectedList.remove(t);
				int actualItemPosition = mAdapter.adapterPositionToListIndex(position);
				if(mFragment.mQuickFilteredList != null && mFragment.mQuickFilteredList.get(actualItemPosition).isFilteredBySelection){
					mFragment.mQuickFilteredList.remove(actualItemPosition);
					mAdapter.notifyNonHeaderItemRemoved(position);
				}else{
					updateSelectionStatus(t);
				}
			}
			mFragment.selectionChanged();
			mFragment.applyEmptyIndicator();
		}else if(v.equals(itemView)){
			if(!mFragment.mSelectedList.isEmpty()){
				if(!mFragment.mSelectedList.contains(t)){
					mFragment.mSelectedList.add(t);
					updateSelectionStatus(t);
				}else{
					mFragment.mSelectedList.remove(t);
					int actualItemPosition = mAdapter.adapterPositionToListIndex(position);
					if(mFragment.mQuickFilteredList != null && mFragment.mQuickFilteredList.get(actualItemPosition).isFilteredBySelection){
						mFragment.mQuickFilteredList.remove(actualItemPosition);
						mAdapter.notifyNonHeaderItemRemoved(position);
					}else{
						updateSelectionStatus(t);
					}
				}
				mFragment.selectionChanged();
				mFragment.applyEmptyIndicator();
			}
		}else if(v.equals(lblUserName)){
			Tweeter u = t.retweeted_status == null ? t.user : t.retweeted_status.user;
			Page.templatePageUser(u.user_id, u.screen_name, (MainActivity) mFragment.getActivity(), PageElement.FUNCTION_USER_TIMELINE);
		}else{
			for(int i = arrPreviews.length - 1; i >= 0; i--)
				if(arrPreviews[i].equals(v)){
					MediaPreviewActivity.previewTweetImages(mFragment.getActivity(), t.retweeted_status == null ? t : t.retweeted_status, i);
				}
		}
	}

	@Override
	public boolean onLongClick(View v){
		int itemPosition = getAdapterPosition();
		final Tweet t = mFragment.mQuickFilteredList == null ? (Tweet) mAdapter.getItem(itemPosition) : ((TimelineFragment.FilteredTweet) mAdapter.getItem(itemPosition)).mObject;
		if(v.equals(tweetActionRetweet)){
			if(t.user._protected)
				return true;
			mIsLongPress = true;
			v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
			tweetActionRetweet.setText(R.string.tweet_action_quote);
		}else if(v.equals(tweetActionReply)){
			mIsLongPress = true;
			v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
		}else if(v.equals(tweetActionFavorite)){
			return true;
		}else if(v.equals(dragInitiatorButton)){
			new AlertDialog.Builder(mFragment.getActivity())
					.setMessage("Remove?")
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
											e.postRemove(id);
									}catch(Exception e){
										e.printStackTrace();
									}
								}
							}.start();
						}
					})
					.setNegativeButton(android.R.string.no, null)
					.show();
			return true;
		}
		return false;
	}

	@Override
	public void onDragPrepare(DragInitiator dragInitiator){
		int itemPosition = getAdapterPosition();
		final Tweet t = mFragment.mQuickFilteredList == null ? (Tweet) mAdapter.getItem(itemPosition) : ((TimelineFragment.FilteredTweet) mAdapter.getItem(itemPosition)).mObject;
		if(t == null)
			return;
		dragInitiator.setKeepContainerOnClick(!mFragment.mSelectedList.contains(t));
		mIsLongPress = false;
		tweetActionRetweet.setText(t.user._protected && t.retweeted_status == null ? R.string.tweet_action_quote : R.string.tweet_action_retweet);
	}

	@Override
	public void onDragStart(DragInitiator dragInitiator){

	}

	@Override
	public void onDragEnd(DragInitiator dragInitiator){

	}
}
