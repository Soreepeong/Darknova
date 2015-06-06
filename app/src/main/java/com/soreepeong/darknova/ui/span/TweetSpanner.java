package com.soreepeong.darknova.ui.span;

import android.text.SpannableStringBuilder;
import android.text.Spanned;

import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweeter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author Soreepeong
 */
public class TweetSpanner {

	private final static Comparator<Entities.Entity> IndiceSorter = new Comparator<Entities.Entity>() {
		@Override
		public int compare(Entities.Entity lhs, Entities.Entity rhs) {
			return rhs.indice_left - lhs.indice_left;
		}
	};

	public static SpannableStringBuilder get(String s, Entities ent, ImageCache cache, int lineHeight) {
		return make(SpannableStringBuilder.valueOf(s), ent, cache, lineHeight);
	}

	public static SpannableStringBuilder make(SpannableStringBuilder sb, Entities ent, ImageCache cache, int lineHeight) {
		String t = sb.toString();
		int prevEntityIndiceStart = -1;
		for (Entities.Entity e : ent.entities) {
			if (prevEntityIndiceStart == e.indice_left)
				continue;
			prevEntityIndiceStart = e.indice_left;
			Object span = null;
			if (e instanceof Entities.MediaEntity) {
				span = new MediaEntitySpan((Entities.MediaEntity) e);
			} else if (e instanceof Entities.UrlEntity) {
				span = new UrlEntitySpan((Entities.UrlEntity) e);
			} else if (e instanceof Entities.MentionsEntity) {
				span = new UserEntitySpan((Entities.MentionsEntity) e);
				sb.setSpan(new TweeterImageSpan(Tweeter.getTweeter(((Entities.MentionsEntity) e).id, ((Entities.MentionsEntity) e).screen_name), cache, lineHeight), StringTools.charIndexFromCodePointIndex(t, e.indice_left), StringTools.charIndexFromCodePointIndex(t, e.indice_left + 1), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
			} else if (e instanceof Entities.HashtagEntity) {
				span = new HashtagSpan((Entities.HashtagEntity) e);
			}
			if (span != null) {
				sb.setSpan(span, StringTools.charIndexFromCodePointIndex(t, e.indice_left), StringTools.charIndexFromCodePointIndex(t, e.indice_right), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
			}
		}
		ArrayList<Entities.UrlEntity> mUrlEntities = new ArrayList<>();
		for (Entities.Entity e : ent.entities)
			if (e instanceof Entities.UrlEntity)
				mUrlEntities.add((Entities.UrlEntity) e);
		Collections.sort(mUrlEntities, IndiceSorter);
		prevEntityIndiceStart = -1;
		for (Entities.UrlEntity e : mUrlEntities) {
			if (prevEntityIndiceStart == e.indice_left)
				continue;
			prevEntityIndiceStart = e.indice_left;
			String urlDisplay = e._show_expanded ? (e._expanded_url != null ? e._expanded_url : e.expanded_url) : e.display_url;
			String replacement = urlDisplay;
			if (e._page_title != null) {
				replacement += " (" + e._page_title + ")";
			}
			int left = StringTools.charIndexFromCodePointIndex(t, e.indice_left);
			int right = StringTools.charIndexFromCodePointIndex(t, e.indice_right);
			sb.replace(left, right, replacement);
			right = left + replacement.length();
			if (e._page_title != null)
				sb.setSpan(new UrlEntitySpan.UrlTitleEntitySpan(), left + urlDisplay.length() + 1, right, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
		}
		return sb;
	}

	public static String includeUrlEntity(String s, Entities ent) {
		int prevEntityIndiceStart = -1;
		StringBuilder sb = new StringBuilder(s);
		ArrayList<Entities.UrlEntity> mUrlEntities = new ArrayList<>();
		for (Entities.Entity e : ent.entities)
			if (e instanceof Entities.UrlEntity)
				mUrlEntities.add((Entities.UrlEntity) e);
		Collections.sort(mUrlEntities, IndiceSorter);
		prevEntityIndiceStart = -1;
		for (Entities.UrlEntity e : mUrlEntities) {
			if (prevEntityIndiceStart == e.indice_left)
				continue;
			prevEntityIndiceStart = e.indice_left;
			String urlDisplay = e._show_expanded ? (e._expanded_url != null ? e._expanded_url : e.expanded_url) : e.display_url;
			if (e._page_title != null)
				urlDisplay += " (" + e._page_title + ")";
			sb.replace(StringTools.charIndexFromCodePointIndex(s, e.indice_left), StringTools.charIndexFromCodePointIndex(s, e.indice_right), urlDisplay);
		}
		return sb.toString();
	}

}
