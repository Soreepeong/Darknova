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
		for (Entities.Entity e : ent.entities) {
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
		for (Entities.UrlEntity e : mUrlEntities)
			sb.replace(StringTools.charIndexFromCodePointIndex(t, e.indice_left), StringTools.charIndexFromCodePointIndex(t, e.indice_right), e._show_expanded ? e.expanded_url : e.display_url);
		return sb;
	}

	public static String includeUrlEntity(String s, Entities ent) {
		StringBuilder sb = new StringBuilder(s);
		ArrayList<Entities.UrlEntity> mUrlEntities = new ArrayList<>();
		for (Entities.Entity e : ent.entities)
			if (e instanceof Entities.UrlEntity)
				mUrlEntities.add((Entities.UrlEntity) e);
		Collections.sort(mUrlEntities, IndiceSorter);
		for (Entities.UrlEntity e : mUrlEntities)
			sb.replace(StringTools.charIndexFromCodePointIndex(s, e.indice_left), StringTools.charIndexFromCodePointIndex(s, e.indice_right), e._show_expanded ? e.expanded_url : e.display_url);
		return sb.toString();
	}

}
