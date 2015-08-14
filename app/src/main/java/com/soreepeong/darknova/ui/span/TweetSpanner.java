package com.soreepeong.darknova.ui.span;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Entities;
import com.soreepeong.darknova.twitter.Tweet;
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

	public static SpannableStringBuilder make(Tweet tweet, ImageCache cache, int lineHeight, CharSequence seq) {
		SpannableStringBuilder sb = SpannableStringBuilder.valueOf(tweet.text);
		if (tweet.entities == null)
			return sb;
		int prevEntityIndiceStart = -1;
		EntitySpan[] previousSpans = null;
		UserImageSpan[] previousImageSpans = null;
		if (seq instanceof Spannable) {
			previousSpans = ((Spannable) seq).getSpans(0, seq.length(), EntitySpan.class);
			previousImageSpans = ((Spannable) seq).getSpans(0, seq.length(), UserImageSpan.class);
		}
		for (Entities.Entity e : tweet.entities.list) {
			if (prevEntityIndiceStart == e.indice_left)
				continue;
			prevEntityIndiceStart = e.indice_left;
			Object span = null;
			if (previousSpans != null){
				for(int i = 0; i < previousSpans.length; i++)
					if (previousSpans[i] != null && e.equals(previousSpans[i].getEntity())){
						span = previousSpans[i];
						previousSpans[i] = null;
						break;
					}
			}
			if (span == null) {
				if (e instanceof Entities.MediaEntity) {
					span = new MediaEntitySpan((Entities.MediaEntity) e, tweet);
				} else if (e instanceof Entities.UrlEntity) {
					span = new UrlEntitySpan((Entities.UrlEntity) e);
				} else if (e instanceof Entities.MentionsEntity) {
					span = new UserEntitySpan((Entities.MentionsEntity) e);
				} else if (e instanceof Entities.HashtagEntity) {
					span = new HashtagSpan((Entities.HashtagEntity) e);
				}
			}
			if (span != null) {
				sb.setSpan(span,
						StringTools.charIndexFromCodePointIndex(tweet.text, e.indice_left),
						StringTools.charIndexFromCodePointIndex(tweet.text, e.indice_right),
						Spanned.SPAN_INCLUSIVE_INCLUSIVE);
			}
			if (e instanceof Entities.MentionsEntity) {
				span = null;
				Tweeter u = Tweeter.getTweeter(((Entities.MentionsEntity) e).id,
						((Entities.MentionsEntity) e).screen_name);
				if (previousImageSpans != null)
					for(int i = 0; i < previousImageSpans.length; i++)
						if (previousImageSpans[i] != null && u.equals(previousImageSpans[i].getTweeter())){
							span = previousImageSpans[i];
							previousImageSpans[i] = null;
							break;
						}
				if (span == null)
					span = new UserImageSpan(
							u,
							cache,
							lineHeight);
				sb.setSpan(span,
						StringTools.charIndexFromCodePointIndex(tweet.text, e.indice_left),
						StringTools.charIndexFromCodePointIndex(tweet.text, e.indice_left + 1),
						Spanned.SPAN_INCLUSIVE_INCLUSIVE);
			}
		}
		ArrayList<Entities.UrlEntity> mUrlEntities = new ArrayList<>();
		for (Entities.Entity e : tweet.entities.list)
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
			int left = StringTools.charIndexFromCodePointIndex(tweet.text, e.indice_left);
			int right = StringTools.charIndexFromCodePointIndex(tweet.text, e.indice_right);
			sb.replace(left, right, replacement);
			right = left + replacement.length();
			if (e._page_title != null)
				sb.setSpan(new UrlEntitySpan.UrlTitleEntitySpan(), left + urlDisplay.length() + 1, right, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
		}
		return sb;
	}

	public static SpannableStringBuilder includeEntities(String text, Entities entities, ImageCache cache, int lineHeight, CharSequence seq) {
		SpannableStringBuilder sb = SpannableStringBuilder.valueOf(text);
		if (entities == null)
			return sb;
		int prevEntityIndiceStart = -1;
		EntitySpan[] previousSpans = null;
		UserImageSpan[] previousImageSpans = null;
		if (seq instanceof Spannable) {
			previousSpans = ((Spannable) seq).getSpans(0, seq.length(), EntitySpan.class);
			previousImageSpans = ((Spannable) seq).getSpans(0, seq.length(), UserImageSpan.class);
		}
		for (Entities.Entity e : entities.list) {
			if (prevEntityIndiceStart == e.indice_left)
				continue;
			prevEntityIndiceStart = e.indice_left;
			Object span = null;
			if (previousSpans != null){
				for(int i = 0; i < previousSpans.length; i++)
					if (previousSpans[i] != null && e.equals(previousSpans[i].getEntity())){
						span = previousSpans[i];
						previousSpans[i] = null;
						break;
					}
			}
			if (span == null) {
				if (e instanceof Entities.UrlEntity) {
					span = new UrlEntitySpan((Entities.UrlEntity) e);
				} else if (e instanceof Entities.MentionsEntity) {
					span = new UserEntitySpan((Entities.MentionsEntity) e);
				} else if (e instanceof Entities.HashtagEntity) {
					span = new HashtagSpan((Entities.HashtagEntity) e);
				}
			}
			if (span != null) {
				sb.setSpan(span,
						StringTools.charIndexFromCodePointIndex(text, e.indice_left),
						StringTools.charIndexFromCodePointIndex(text, e.indice_right),
						Spanned.SPAN_INCLUSIVE_INCLUSIVE);
			}
			if (e instanceof Entities.MentionsEntity) {
				span = null;
				Tweeter u = Tweeter.getTweeter(((Entities.MentionsEntity) e).id,
						((Entities.MentionsEntity) e).screen_name);
				if (previousImageSpans != null)
					for(int i = 0; i < previousImageSpans.length; i++)
						if (previousImageSpans[i] != null && u.equals(previousImageSpans[i].getTweeter())){
							span = previousImageSpans[i];
							previousImageSpans[i] = null;
							break;
						}
				if (span == null)
					span = new UserImageSpan(
							u,
							cache,
							lineHeight);
				sb.setSpan(span,
						StringTools.charIndexFromCodePointIndex(text, e.indice_left),
						StringTools.charIndexFromCodePointIndex(text, e.indice_left + 1),
						Spanned.SPAN_INCLUSIVE_INCLUSIVE);
			}
		}
		ArrayList<Entities.UrlEntity> mUrlEntities = new ArrayList<>();
		for (Entities.Entity e : entities.list)
			if (e instanceof Entities.UrlEntity)
				mUrlEntities.add((Entities.UrlEntity) e);
		Collections.sort(mUrlEntities, IndiceSorter);
		prevEntityIndiceStart = -1;
		for (Entities.UrlEntity e : mUrlEntities) {
			if (prevEntityIndiceStart == e.indice_left)
				continue;
			prevEntityIndiceStart = e.indice_left;
			String urlDisplay = e._show_expanded ? (e._expanded_url != null ? e._expanded_url : e.expanded_url) : e.display_url;
			if(urlDisplay == null) urlDisplay = e.display_url;
			if(urlDisplay == null) urlDisplay = e.expanded_url;
			if(urlDisplay == null) urlDisplay = e.url;
			String replacement = urlDisplay;
			if (e._page_title != null) {
				replacement += " (" + e._page_title + ")";
			}
			int left = StringTools.charIndexFromCodePointIndex(text, e.indice_left);
			int right = StringTools.charIndexFromCodePointIndex(text, e.indice_right);
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
		for (Entities.Entity e : ent.list)
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
