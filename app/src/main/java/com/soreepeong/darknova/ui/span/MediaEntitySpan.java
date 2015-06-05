package com.soreepeong.darknova.ui.span;

import android.view.View;

import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.ui.MediaPreviewActivity;

/**
 * @author Soreepeong
 */
public class MediaEntitySpan extends TouchableSpan {
	private final Entities.MediaEntity mEntity;

	public MediaEntitySpan(Entities.MediaEntity me) {
		super(0xFF17b9ff, 0, false, true, 0xFF17b9ff, 0x40FFFFFF, false, true);
		mEntity = me;
	}

	@Override
	public void onClick(View v) {
		MediaPreviewActivity.previewLink(v.getContext(), mEntity);
	}
}
