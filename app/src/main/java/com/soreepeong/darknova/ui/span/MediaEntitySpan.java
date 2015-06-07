package com.soreepeong.darknova.ui.span;

import android.view.View;

import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.ui.MediaPreviewActivity;

/**
 * @author Soreepeong
 */
public class MediaEntitySpan extends TouchableSpan implements EntitySpan {
	private final Entities.MediaEntity mEntity;
	private final Tweet mTweet;

	public MediaEntitySpan(Entities.MediaEntity me, Tweet tweet) {
		super(0xFF17b9ff, 0, false, true, 0xFF17b9ff, 0x40FFFFFF, false, true);
		mEntity = me;
		mTweet = tweet;
	}

	@Override
	public Entities.Entity getEntity() {
		return mEntity;
	}

	@Override
	public void onClick(View v) {
		if (mEntity.expanded_url.contains(Long.toString(mTweet.id)))
			MediaPreviewActivity.previewTweetImages(v.getContext(), mTweet, 0);
		else
			MediaPreviewActivity.previewLink(v.getContext(), mEntity);
	}
}
