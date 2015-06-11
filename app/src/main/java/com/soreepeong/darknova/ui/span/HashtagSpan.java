package com.soreepeong.darknova.ui.span;

import android.view.View;

import com.soreepeong.darknova.settings.Page;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.ui.MainActivity;

/**
 * @author Soreepeong
 */
public class HashtagSpan extends TouchableSpan implements EntitySpan {
	private final Entities.HashtagEntity mEntity;

	public HashtagSpan(Entities.HashtagEntity me) {
		super(0xFF909dff, 0, false, true, 0xFF909dff, 0x40FFFFFF, false, true);
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
		String search = mEntity.text;
		if (mEntity instanceof Entities.SymbolEntity)
			search = "$" + search;
		else
			search = "#" + search;
		Page.templatePageSearch(search, (MainActivity) v.getContext());
	}
}
