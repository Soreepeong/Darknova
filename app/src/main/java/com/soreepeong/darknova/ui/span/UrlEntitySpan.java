package com.soreepeong.darknova.ui.span;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.soreepeong.darknova.core.HTTPRequest;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.ui.MediaPreviewActivity;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Soreepeong
 */
public class UrlEntitySpan extends TouchableSpan implements EntitySpan {
	private final Entities.UrlEntity mEntity;
	@SuppressWarnings("unused")
	private final Tweet mTweet;
	private long mExpandStartTime;

	public UrlEntitySpan(Entities.UrlEntity me, Tweet tweet) {
		super(0xFF909dff, 0, false, true, 0xFF909dff, 0x40FFFFFF, false, true);
		mEntity = me;
		mTweet = tweet;
	}

	@Override
	public Entities.Entity getEntity() {
		return mEntity;
	}

	@Override
	public void onClick(View v) {
		MediaPreviewActivity.previewLink(v.getContext(), mEntity);
	}

	@Override
	public boolean onLongClick(View v) {
		View rv = v;
		while (rv != null && !(rv instanceof RecyclerView)) {
			if (!(rv.getParent() instanceof View))
				rv = null;
			else
				rv = (View) rv.getParent();
		}
		if (mEntity._expanded_url == null)
			mEntity._expanded_url = mEntity.expanded_url;
		mEntity._show_expanded |= mEntity.expanded_url.endsWith(mEntity.display_url);
		if (!mEntity._show_expanded) {
			if (rv != null && ((RecyclerView) rv).getAdapter() != null)
				((RecyclerView) rv).getAdapter().notifyDataSetChanged();
			mEntity._show_expanded = true;
		}
		UrlExpander.expandUrl(mEntity, this, v, (RecyclerView) rv);
		return true;
	}

	public static class UrlTitleEntitySpan extends ForegroundColorSpan {

		public UrlTitleEntitySpan() {
			super(0xFFC0CdFF);
		}
	}

	private static class UrlExpander extends Thread implements Handler.Callback {
		private static final Pattern TITLE_EXTRACTOR = Pattern.compile("<title(?:\\s[^>]*)?>([^<]*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL | Pattern.MULTILINE);

		private static final WeakHashMap<Entities.UrlEntity, UrlExpander> mExpanderMap = new WeakHashMap<>();
		private static final Interpolator mFadeOutInterpolator = new DecelerateInterpolator();
		private static final Interpolator mFadeInInterpolator = new AccelerateInterpolator();
		private static final int BLINK_DURATION = 600;
		private static final int MIN_ALPHA_LEVEL = 127;
		private static final int TITLE_DISPLAY_LENGTH = 64;

		private static final int MESSAGE_UPDATE_COLOR = 1;
		private static final int MESSAGE_UPDATE_DONE = 2;

		private final ArrayList<View> mViewMap = new ArrayList<>();
		private final ArrayList<UrlEntitySpan> mSpanMap = new ArrayList<>();
		private final Entities.UrlEntity mEntity;
		private final Handler mHandler;
		private boolean mFinished;

		private UrlExpander(Entities.UrlEntity entity) {
			super();
			mEntity = entity;
			mHandler = new Handler(Looper.getMainLooper(), this);
			mHandler.sendEmptyMessage(MESSAGE_UPDATE_COLOR);
			start();
		}

		public static void expandUrl(Entities.UrlEntity entity, UrlEntitySpan span, View initiator, RecyclerView list) {
			if (Looper.myLooper() != Looper.getMainLooper())
				throw new RuntimeException("expandUrl can only be called from UI Thread");
			UrlExpander expander = mExpanderMap.get(entity);
			if (expander == null) {
				mExpanderMap.put(entity, expander = new UrlExpander(entity));
			}
			expander.mViewMap.add(initiator);
			expander.mViewMap.add(list);
			expander.mSpanMap.add(span);
			span.mExpandStartTime = System.currentTimeMillis();
		}

		@Override
		public void run() {
			HTTPRequest req = HTTPRequest.getRequest(mEntity._expanded_url, null, false, null, false);
			if (req != null)
				try {
					req.setMaxRedirects(1);
					req.submitRequest();
					Matcher m = TITLE_EXTRACTOR.matcher(req.getWholeData(8192));
					mEntity._expanded_url = req.getUrl();
					if (m.find()) {
						mEntity._page_title = Html.fromHtml(m.group(1)).toString().trim();
						if (mEntity._page_title.isEmpty())
							mEntity._page_title = null;
						else
							mEntity._page_title = StringTools.cutString(mEntity._page_title, TITLE_DISPLAY_LENGTH);
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					req.close();
				}
			mFinished = true;
			mHandler.sendEmptyMessage(MESSAGE_UPDATE_DONE);
		}

		@Override
		public boolean handleMessage(Message msg) {
			switch (msg.what) {
				case MESSAGE_UPDATE_COLOR: {
					for (UrlEntitySpan span : mSpanMap) {
						span.setAlphaOverride(!mFinished);
						int alphaLevel = (int) ((System.currentTimeMillis() - span.mExpandStartTime) % BLINK_DURATION);
						if (alphaLevel >= BLINK_DURATION >> 1) {
							alphaLevel = BLINK_DURATION - alphaLevel;
							alphaLevel = (int) ((255 - MIN_ALPHA_LEVEL) * mFadeOutInterpolator.getInterpolation(alphaLevel * 2f / BLINK_DURATION));
						} else
							alphaLevel = (int) ((255 - MIN_ALPHA_LEVEL) * mFadeInInterpolator.getInterpolation(alphaLevel * 2f / BLINK_DURATION));
						alphaLevel += MIN_ALPHA_LEVEL;
						span.setAlphaOverrideValue(mFinished ? 255 : alphaLevel);
					}
					for (View v : mViewMap) {
						if (!(v instanceof RecyclerView))
							v.invalidate();
					}
					if (!mFinished) {
						mHandler.sendEmptyMessageDelayed(MESSAGE_UPDATE_COLOR, 50);
					}
					return true;
				}
				case MESSAGE_UPDATE_DONE: {
					for (View v : mViewMap) {
						if (v instanceof RecyclerView && ((RecyclerView) v).getAdapter() != null)
							((RecyclerView) v).getAdapter().notifyDataSetChanged();
					}
					mExpanderMap.remove(mEntity);
					return true;
				}
			}
			return false;
		}
	}
}
