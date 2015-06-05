package com.soreepeong.darknova.ui.span;

import android.view.View;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.ui.MainActivity;

/**
 * @author Soreepeong
 */
public class UserEntitySpan extends TouchableSpan {
	private final Entities.MentionsEntity mEntity;

	public UserEntitySpan(Entities.MentionsEntity me) {
		super(TwitterEngine.get(me.id) != null ? 0xFFcfc2ff : 0xFF909dff, 0, false, true, TwitterEngine.get(me.id) != null ? 0xFFcfc2ff : 0xFF909dff, 0x40FFFFFF, false, true);
		mEntity = me;
	}

	@Override
	public void onClick(View v) {
		if (!(v.getContext() instanceof MainActivity))
			return;
		MainActivity a = (MainActivity) v.getContext();
		Page.Builder builder = new Page.Builder(mEntity.screen_name, R.drawable.ic_eyes);
		TwitterEngine currentUser = a.getDrawerFragment().getCurrentUser();
		builder.e().add(new Page.Element(currentUser, Page.Element.FUNCTION_USER_SINGLE, mEntity.id, mEntity.screen_name));
		builder.e().add(new Page.Element(currentUser, Page.Element.FUNCTION_USER_TIMELINE, mEntity.id, mEntity.screen_name));
		builder.setParentPage(a.getCurrentPage().getRepresentingPage());
		Page p = builder.build();
		int index = Page.pages.indexOf(p);
		if (index == -1) {
			index = Page.pages.size();
			Page.addPage(p);
			Page.broadcastPageChange();
		}
		a.selectPage(index);
	}
}
