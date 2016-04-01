package com.soreepeong.darknova.extractors;

import java.util.regex.Pattern;

/**
 * Image extractor for various media provider sites.
 *
 * TODO work in progress.
 */
public class ImageExtractor {
	public static final Pattern mTwitterPicPattern = Pattern.compile("^https?://(?:[^/]*\\.)?twitter\\.com/([a-z0-9_]{1,15})/status/([0-9]+)/photo/([0-9]+)$", Pattern.CASE_INSENSITIVE);
	public static final Pattern mRedirectingTwitterPicPattern = Pattern.compile("^https?://pic\\.twitter\\.com/([a-z0-9_]+)$", Pattern.CASE_INSENSITIVE);
	public static final Pattern mFileNameReplacer = Pattern.compile("\\\\/\\:\\*\\?\"<>\\|");
	public static final Pattern mFileNameGetter = Pattern.compile("^https?://(?:[^/]*)(?:/[^?]+)?/(.*?)(\\.(?:jpe?g|gif|png|webp|bmp)*?)([?:].*)?$", Pattern.CASE_INSENSITIVE);
	// https://twitter.com/wintail/status/605389019711553536/photo/1

	public static boolean isProcessableLink(String url){
		return false;
	}
}
