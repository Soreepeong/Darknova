package com.soreepeong.darknova.ui.span;

import android.view.View;

import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.settings.PageElement;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.ui.MainActivity;

/**
 * @author Soreepeong
 */
public class UserEntitySpan extends TouchableSpan implements EntitySpan {
	private final Entities.MentionsEntity mEntity;

	public UserEntitySpan(Entities.MentionsEntity me) {
		super(TwitterEngine.get(me.id) != null ? 0xFFcfc2ff : 0xFF909dff, 0, false, true, TwitterEngine.get(me.id) != null ? 0xFFcfc2ff : 0xFF909dff, 0x40FFFFFF, false, true);
		mEntity = me;
	}

	@Override
	public Entities.Entity getEntity() {
		return mEntity;
	}

	@Override
	public void onClick(View v) {
		if (!(v.getContext() instanceof MainActivity))
			return;
		Page.templatePageUser(mEntity.id, mEntity.screen_name, (MainActivity) v.getContext(), PageElement.FUNCTION_USER_TIMELINE);
	}
}
