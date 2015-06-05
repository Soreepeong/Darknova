package com.soreepeong.darknova.ui.span;

import android.view.View;

import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.ui.MediaPreviewActivity;

/**
 * @author Soreepeong
 */
public class UrlEntitySpan extends TouchableSpan {
	private final Entities.UrlEntity mEntity;

	public UrlEntitySpan(Entities.UrlEntity me) {
		super(0xFF909dff, 0, false, true, 0xFF909dff, 0x40FFFFFF, false, true);
		mEntity = me;
	}

	@Override
	public void onClick(View v) {
		MediaPreviewActivity.previewLink(v.getContext(), mEntity);
	}

	@Override
	public boolean onLongClick(View v) {
		if (mEntity._show_expanded || mEntity.expanded_url.equals(mEntity.display_url)) {

		} else
			mEntity._show_expanded = true;
		v.invalidate();
		return true;
	}
}
