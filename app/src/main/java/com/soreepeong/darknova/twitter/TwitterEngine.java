package com.soreepeong.darknova.twitter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.Html;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.soreepeong.darknova.DarknovaApplication;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.HTTPRequest;
import com.soreepeong.darknova.core.OAuth;
import com.soreepeong.darknova.tools.ArrayTools;
import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.tools.StringTools;

import org.acra.ACRA;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Twitter APIs & account management
 *
 * @author Soreepeong
 */
public class TwitterEngine implements Comparable<TwitterEngine> {
	public static final int MAX_IMAGE_MEDIA_SIZE = 5 * 1048576;
	public static final int MAX_VIDEO_MEDIA_SIZE = 15 * 1048576;
	public static final byte[] CRLF = new byte[]{'\r', '\n'};
	public static final int[] CRLF_FAILURE = ArrayTools.computeFailure(CRLF);
	protected static final String DEFAULT_API_KEY = "xAMJy6IMIBF6KY2RQAyvw";
	protected static final String DEFAULT_SECRET_API_KEY = "qq8aNTvEVlVn9NWJwZTJwCLrluGOXoX8dt7rI4A24";
	protected static final int MAX_STREAM_HOLD = 1048576 * 3; // 3MB
	protected static final SimpleDateFormat CREATED_AT_STRING = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy", Locale.ENGLISH);
	protected static final Pattern TWITTER_PROFILE_IMAGE_MATCHER = Pattern.compile("^(.*?)(?:_normal|_bigger|_mini|)(\\.[A-Za-z_0-9]+|)?$");
	protected static final ArrayList<OnUserlistChangedListener> mUserlistChangedListener = new ArrayList<>();
	protected static final ArrayList<TwitterEngine> mTwitter = new ArrayList<>();
	protected static final JsonFactory JSON = new JsonFactory();
	/**
	 * Twitter API Base Paths
	 */
	protected static String API_BASE_PATH = "https://api.twitter.com/1.1/";
	protected static String UPLOAD_BASE_PATH = "https://upload.twitter.com/1.1/";
	protected static String STREAM_BASE_PATH = "https://userstream.twitter.com/1.1/";
	protected static String AUTH_REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
	protected static String AUTH_ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
	protected static String AUTH_AUTHORIZE_URL = "https://api.twitter.com/oauth/authorize";
	protected static SharedPreferences mTwitterConfiguration;
	protected static List<TwitterEngine> mOldEngines;
	protected final OAuth auth;
	protected final ArrayList<TwitterStreamCallback> mStreamCallbacks = new ArrayList<>();
	protected int mEngineIndex;
	protected Account mRespectiveAccount;
	protected long mUserId;
	protected String mScreenName;
	protected Tweeter mTweeter;
	private static OnAccountsUpdateListener mAccountUpdateListener = new OnAccountsUpdateListener() {
		@Override
		public void onAccountsUpdated(Account[] accounts) {
			reloadTwitterEngines();
		}
	};
	protected Handler mHandler = new Handler(Looper.getMainLooper());
	protected StreamThread mStreamThread;

	/**
	 * Initialize with default API Key.
	 */
	public TwitterEngine() {
		this(DEFAULT_API_KEY, DEFAULT_SECRET_API_KEY);
	}

	/**
	 * Initialize with given API Key.
	 *
	 * @param apiKey       API Key
	 * @param secretApiKey Secret API Key
	 */
	public TwitterEngine(String apiKey, String secretApiKey) {
		auth = new OAuth(apiKey, secretApiKey);
	}

	public static void addOnUserlistChangedListener(OnUserlistChangedListener l) {
		synchronized (mUserlistChangedListener) {
			if (!mUserlistChangedListener.contains(l))
				mUserlistChangedListener.add(l);
		}
	}

	public static void removeOnUserlistChangedListener(OnUserlistChangedListener l) {
		synchronized (mUserlistChangedListener) {
			mUserlistChangedListener.remove(l);
		}
	}

	public static TwitterEngine get(long user_id) {
		synchronized (mTwitter) {
			for (TwitterEngine e : mTwitter)
				if (e.mUserId == user_id)
					return e;
			return null;
		}
	}

	public static ArrayList<TwitterEngine> getStreamables() {
		synchronized (mTwitter) {
			return new ArrayList<>(mTwitter);
		}
	}

	public static ArrayList<TwitterEngine> getAll() {
		synchronized (mTwitter) {
			ArrayList<TwitterEngine> engines = new ArrayList<>();
			for (TwitterEngine e : mTwitter)
				engines.add(e);
			return engines;
		}
	}

	public static void applyAccountInformationChanges() {
		synchronized (mTwitter) {
			AccountManager accountManager = AccountManager.get(DarknovaApplication.mContext);
			for (TwitterEngine e : mTwitter) {
				Tweeter tweeter = e.getTweeter();
				if (!tweeter.info.stub && !tweeter.screen_name.equals(e.mRespectiveAccount.name)) {
					if (Build.VERSION.SDK_INT >= 21) {
						try {
							// BOILERPLATE - it works but throws errors
							accountManager.renameAccount(e.mRespectiveAccount, tweeter.screen_name, null, null);
						} catch (Exception ee) {
							ee.printStackTrace();
						}
					} else {
						remove(tweeter.user_id);
						e.mRespectiveAccount = new Account(tweeter.screen_name, DarknovaApplication.mContext.getString(R.string.account_type));
						accountManager.addAccountExplicitly(e.mRespectiveAccount, null, null);
						accountManager.setUserData(e.mRespectiveAccount, "oauth_token", e.auth.getToken());
						accountManager.setUserData(e.mRespectiveAccount, "oauth_token_secret", e.auth.getSecretToken());
						accountManager.setUserData(e.mRespectiveAccount, "user_id", Long.toString(e.mUserId));
						accountManager.setUserData(e.mRespectiveAccount, "_index", Integer.toString(e.mEngineIndex));
					}
				}
			}
		}
	}

	public static void reorder(Context context, int from, int to) {
		synchronized (mTwitter) {
			AccountManager accountManager = AccountManager.get(context);
			mTwitter.add(to, mTwitter.remove(from));
			int i = 0;
			for (TwitterEngine e : mTwitter) {
				if (e.mEngineIndex != i)
					accountManager.setUserData(e.mRespectiveAccount, "_index", Integer.toString(e.mEngineIndex = i));
				i++;
			}
			broadcastUserlistChanged();
		}
	}

	public static void remove(long who) {
		synchronized (mTwitter) {
			AccountManager accountManager = AccountManager.get(DarknovaApplication.mContext);
			for (TwitterEngine e : mTwitter) {
				if (e.getUserId() == who) {
					if (Build.VERSION.SDK_INT >= 22)
						accountManager.removeAccountExplicitly(e.mRespectiveAccount);
					else
						//noinspection deprecation
						accountManager.removeAccount(e.mRespectiveAccount, null, null);
					broadcastUserlistChanged();
					break;
				}
			}
		}
	}

	public static Account addAccount(TwitterEngine newEngine) {
		synchronized (mTwitter) {
			final Account account = new Account(newEngine.getScreenName(), DarknovaApplication.mContext.getString(R.string.account_type));
			final AccountManager am = AccountManager.get(DarknovaApplication.mContext);
			final Bundle info = new Bundle();
			info.putLong("user_id", newEngine.getUserId());
			info.putString("screen_name", newEngine.getScreenName());
			info.putString("oauth_token", newEngine.getAuth().getToken());
			info.putString("oauth_token_secret", newEngine.getAuth().getSecretToken());
			info.putString("consumer_key", newEngine.getAuth().getApiKey());
			info.putString("consumer_key_secret", newEngine.getAuth().getSecretApiKey());
			am.addAccountExplicitly(account, null, info);
			am.setUserData(account, "user_id", Long.toString(newEngine.getUserId()));
			am.setUserData(account, "screen_name", newEngine.getScreenName());
			am.setUserData(account, "oauth_token", newEngine.getAuth().getToken());
			am.setUserData(account, "oauth_token_secret", newEngine.getAuth().getSecretToken());
			am.setUserData(account, "consumer_key", newEngine.getAuth().getApiKey());
			am.setUserData(account, "consumer_key_secret", newEngine.getAuth().getSecretApiKey());
			return account;
		}
	}

	private static void reloadTwitterEngines() {
		int maxIndex = 0;
		synchronized (mTwitter) {
			mTwitter.clear();
			final AccountManager accountManager = AccountManager.get(DarknovaApplication.mContext);
			for (Account acc : accountManager.getAccountsByType(DarknovaApplication.mContext.getString(R.string.account_type))) {
				TwitterEngine engine = new TwitterEngine(accountManager.getUserData(acc, "consumer_key"), accountManager.getUserData(acc, "consumer_key_secret"));
				engine.setOauthToken(accountManager.getUserData(acc, "oauth_token"), accountManager.getUserData(acc, "oauth_token_secret"));
				engine.setUserInfo(Long.decode(accountManager.getUserData(acc, "user_id")), acc.name);
				String eIndex = accountManager.getUserData(acc, "_index");
				engine.mEngineIndex = eIndex == null ? -1 : Integer.parseInt(eIndex);
				engine.mRespectiveAccount = acc;
				maxIndex = Math.max(maxIndex, engine.mEngineIndex);
				mTwitter.add(engine);
			}
			for (TwitterEngine e : mTwitter)
				if (e.mEngineIndex == -1)
					accountManager.setUserData(e.mRespectiveAccount, "_index", Integer.toString(e.mEngineIndex = maxIndex++));
			Collections.sort(mTwitter);
		}
		broadcastUserlistChanged();

		if (System.currentTimeMillis() - mTwitterConfiguration.getLong("lastEditTime", 0) > 86400_000 && mTwitter.size() > 0)
			new Thread() {
				@Override
				public void run() {
					try {
						mTwitter.get(0).loadConfiguration();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
	}

	private static void broadcastUserlistChanged() {
		synchronized (mUserlistChangedListener) {
			List<TwitterEngine> mEngines = Collections.unmodifiableList(new ArrayList<>(mTwitter));
			if (mOldEngines != null && mOldEngines.equals(mEngines))
				return;
			for (OnUserlistChangedListener l : mUserlistChangedListener)
				l.onUserlistChanged(mEngines, mOldEngines);
			mOldEngines = mEngines;
		}
	}

	public static void prepare(Context context, String prefName) {
		synchronized (mTwitter) {
			mTwitterConfiguration = context.getSharedPreferences(prefName, Context.MODE_MULTI_PROCESS);
			final AccountManager accountManager = AccountManager.get(context);
			accountManager.addOnAccountsUpdatedListener(mAccountUpdateListener, null, false);
			reloadTwitterEngines();
		}
	}

	public static long getConfigurationLong(String key, long defaultValue) {
		return mTwitterConfiguration.getLong(key, defaultValue);
	}

	protected static String decodeText(String text, Entities entity) {
		final String[][] replacements = {{"&lt;", "<"}, {"&gt;", ">"}, {"&amp;", "&"}};
		int i = 0, last = 0;
		if (text == null) return null;
		StringBuilder sb = new StringBuilder();
		wholeLoop:
		while ((i = text.indexOf("&", i)) != -1) {
			sb.append(text.substring(last, i));
			for (String[] rep : replacements)
				if (text.startsWith(rep[0], i)) {
					sb.append(rep[1]);
					for (Entities.Entity e : entity.list) {
						if (e.indice_left > i) e.indice_left -= rep[0].length() - 1;
						if (e.indice_right > i) e.indice_right -= rep[0].length() - 1;
					}
					last = i += rep[0].length();
					continue wholeLoop;
				}
			sb.append("&");
			last = i = i + 1;
		}
		sb.append(text.substring(last));
		return sb.toString();
	}

	protected static long parseTwitterDate(String date) throws ParseException {
		synchronized (CREATED_AT_STRING) {
			return CREATED_AT_STRING.parse(date).getTime();
		}
	}

	public void setUserInfo(long user_id, String screen_name) {
		mScreenName = screen_name;
		mUserId = user_id;
		mTweeter = Tweeter.getTweeter(user_id, screen_name);
	}

	public Tweeter getTweeter() {
		if (mTweeter == null)
			mTweeter = Tweeter.getTweeter(mUserId, mScreenName);
		return mTweeter;
	}

	public long getUserId() {
		return mUserId;
	}

	public String getScreenName() {
		return mScreenName;
	}

	public OAuth getAuth() {
		return auth;
	}

	public void setOauthToken(String token, String secretToken) {
		auth.setToken(token, secretToken);
	}

	public String startNewLogin(String callbackUrl) throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(AUTH_REQUEST_TOKEN_URL, null, true, callbackUrl == null ? "" : ("oauth_callback=" + StringTools.UrlEncode(callbackUrl)));
		if (request == null) return null;
		try {
			request.addRequestHeader("Authorization", auth.getRequestTokenHeader(AUTH_REQUEST_TOKEN_URL, callbackUrl));
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("startNewLogin", request);
			for (String param : request.getWholeData().split("&")) {
				String key = param.substring(0, param.indexOf("="));
				String value = param.substring(key.length() + 1);
				switch (key) {
					case "oauth_token":
						auth.setToken(value);
						break;
					case "oauth_token_secret":
						auth.setSecretToken(value);
						break;
					case "error":
						throw new RequestException("startNewLogin", value);
				}
			}
			return AUTH_AUTHORIZE_URL + "?force_login=1&oauth_token=" + auth.getToken();
		} finally {
			request.close();
		}
	}

	public String authenticate(String token, String pin) throws RequestException {
		if (!auth.getToken().equals(token))
			throw new RequestException("authenticate", "oauth token wrong");
		HTTPRequest request = HTTPRequest.getRequest(AUTH_ACCESS_TOKEN_URL, null, true, "oauth_verifier=" + StringTools.UrlEncode(pin));
		if (request == null) return null;
		try {
			request.addRequestHeader("Authorization", auth.getAuthorizeHeader(AUTH_ACCESS_TOKEN_URL, pin));
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("startNewLogin", request);
			for (String param : request.getWholeData().split("&")) {
				String key = param.substring(0, param.indexOf("="));
				String value = param.substring(key.length() + 1);
				switch (key) {
					case "oauth_token":
						auth.setToken(value);
						break;
					case "oauth_token_secret":
						auth.setSecretToken(value);
						break;
					case "user_id":
						mUserId = Long.parseLong(value);
						break;
					case "screen_name":
						mScreenName = value;
						break;
					case "error":
						throw new RequestException("authenticate", value);
				}
			}
		} finally {
			request.close();
		}
		return mScreenName;
	}

	protected ArrayList<SavedSearch> parseSavedSearchArray(JsonParser parser) throws IOException, ParseException {
		ArrayList<SavedSearch> res = new ArrayList<>();
		while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_ARRAY) {
			if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
				res.add(parseSavedSearch(parser));
			}
		}
		return res;
	}

	protected SavedSearch parseSavedSearch(JsonParser parser) throws IOException, ParseException {
		SavedSearch res = new SavedSearch();
		String key;
		while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
			key = parser.getCurrentName();
			parser.nextToken();
			if (parser.getCurrentToken() == JsonToken.VALUE_NULL) continue;
			if (key.endsWith("_str")) StreamTools.consumeJsonValue(parser);
			else switch (key) {
				case "created_at":
					res.created_at = parseTwitterDate(parser.getValueAsString());
					break;
				case "id":
					res.id = parser.getLongValue();
					break;
				case "name":
					res.name = parser.getText();
					break;
				case "query":
					res.query = parser.getText();
					break;
				default:
					StreamTools.consumeJsonValue(parser);
			}
			if (parser.getCurrentToken() == JsonToken.START_OBJECT || parser.getCurrentToken() == JsonToken.START_ARRAY) {
				res.id = 0;
				break;
			}
		}
		return res;
	}

	protected Tweeter parseUser(JsonParser parser) throws IOException, ParseException {
		return parseUser(parser, null);
	}

	protected Tweeter parseUser(JsonParser parser, ArrayList<Long> friends) throws IOException, ParseException {
		Tweeter res = Tweeter.getTemporary();
		String key;
		boolean following = false, follow_request_sent = false;
		while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
			key = parser.getCurrentName();
			parser.nextToken();
			if (parser.getCurrentToken() == JsonToken.VALUE_NULL) continue;
			if (key.endsWith("_str")) StreamTools.consumeJsonValue(parser);
			else switch (key) {
				case "created_at":
					res.created_at = parseTwitterDate(parser.getValueAsString());
					break;
				case "id":
					res.user_id = parser.getLongValue();
					break;
				case "name":
					res.name = parser.getText();
					break;
				case "description":
					res.description = parser.getText();
					break;
				case "screen_name":
					res.screen_name = parser.getText();
					break;
				case "url":
					res.url = parser.getText();
					break;
				case "profile_banner_url": {
					String url = parser.getText();
					res.profile_banner_url = new String[]{
							url + "/mobile",
							url + "/web",
							url + "/mobile_retina",
							url + "/web_retina",
							url + "/1500x500",
							url
					};
					break;
				}
				case "profile_image_url": {
					Matcher mat = TWITTER_PROFILE_IMAGE_MATCHER.matcher(parser.getText());
					if (mat.matches()) {
						res.profile_image_url = new String[]{mat.replaceAll("$1_mini$2"), parser.getText(), mat.replaceAll("$1_bigger$2"), mat.replaceAll("$1$2")};
					} else {
						res.profile_image_url = new String[]{parser.getText()};
					}
					break;
				}
				case "location":
					res.location = parser.getText();
					break;
				case "statuses_count":
					res.statuses_count = parser.getLongValue();
					break;
				case "favourites_count":
					res.favourites_count = parser.getLongValue();
					break;
				case "friends_count":
					res.friends_count = parser.getLongValue();
					break;
				case "followers_count":
					res.followers_count = parser.getLongValue();
					break;
				case "listed_count":
					res.listed_count = parser.getLongValue();
					break;
				case "protected":
					res._protected = parser.getBooleanValue();
					break;
				case "verified":
					res.verified = parser.getBooleanValue();
					break;
				case "geo_enabled":
					res.geo_enabled = parser.getBooleanValue();
					break;
				case "following":
					following = parser.getBooleanValue();
					break;
				case "follow_request_sent":
					follow_request_sent = parser.getBooleanValue();
					break;
				case "entities": {
					while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
						key = parser.getCurrentName();
						parser.nextToken();
						switch (key) {
							case "url":
								res.entities_url = new Entities(parser);
								break;
							case "description":
								res.entities_description = new Entities(parser);
								break;
							default:
								StreamTools.consumeJsonValue(parser);
						}
					}
					break;
				}
				default:
					StreamTools.consumeJsonValue(parser);
			}
		}
		res.addForUserInfo(mUserId, (friends != null && friends.contains(res.user_id)) || following, follow_request_sent);
		res.info.lastUpdated = System.currentTimeMillis();
		res.info.stub = false;
		res.url = decodeText(res.url, res.entities_url);
		res.description = decodeText(res.description, res.entities_description);
		return Tweeter.updateTweeter(res);
	}

	protected Tweet parseTweet(JsonParser parser) throws IOException, ParseException {
		return parseTweet(parser, null);
	}

	protected Tweet parseTweet(JsonParser parser, ArrayList<Long> friends) throws IOException, ParseException {
		Tweet res = Tweet.getTemporary();
		Entities e1 = null, e2 = null;
		String key, unused_message = null;
		long in_reply_to_user_id = 0;
		String in_reply_to_screen_name = null;
		boolean favorited = false, retweeted = false;
		while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
			key = parser.getCurrentName();
			parser.nextToken();
			if (parser.getCurrentToken() == JsonToken.VALUE_NULL) continue;
			if (key.endsWith("_str")) StreamTools.consumeJsonValue(parser);
			else switch (key) {
				case "created_at":
					res.created_at = parseTwitterDate(parser.getValueAsString());
					break;
				case "id":
					res.id = parser.getLongValue();
					break;
				case "text":
					res.text = parser.getText();
					break;
				case "retweet_count":
					res.retweet_count = parser.getIntValue();
					break;
				case "favourites_count":
					res.favourites_count = parser.getIntValue();
					break;
				case "source":
					res.source = Html.fromHtml(parser.getText()).toString();
					break;
				case "possibly_sensitive":
					res.possibly_sensitive = parser.getBooleanValue();
					break;
				case "favorited":
					favorited = parser.getBooleanValue();
					break;
				case "retweeted":
					retweeted = parser.getBooleanValue();
					break;
				case "retweeted_status":
					res.retweeted_status = parseTweet(parser, friends);
					break;
				case "in_reply_to_status_id":
					res.in_reply_to_status = Tweet.getTweet(parser.getLongValue());
					break;
				case "in_reply_to_user_id":
					in_reply_to_user_id = parser.getLongValue();
					break;
				case "in_reply_to_screen_name":
					in_reply_to_screen_name = parser.getText();
					break;
				case "user":
					res.user = parseUser(parser, friends);
					break;
				case "entities":
					e1 = new Entities(parser);
					break;
				case "extended_entities":
					e2 = new Entities(parser);
					break;
				case "event":
					res.source = "event";
					res.text = parser.getText();
					break;
				default:
					unused_message = key;
					StreamTools.consumeJsonValue(parser);
			}
			if (parser.getCurrentToken() == JsonToken.START_OBJECT || parser.getCurrentToken() == JsonToken.START_ARRAY) {
				res.id = 0;
				break;
			}
		}
		if (res.id == 0) {
			if (!"event".equals(res.source))
				res.text = unused_message;
			return res;
		}
		res.addForUserInfo(mUserId, favorited, retweeted);
		if (in_reply_to_screen_name != null && in_reply_to_user_id != 0)
			res.in_reply_to_user = Tweeter.getTweeter(in_reply_to_user_id, in_reply_to_screen_name);
		res.info.lastUpdated = System.currentTimeMillis();
		res.info.stub = false;
		res.accessedBy.add(mUserId);
		if (e1 == null) e1 = e2;
		else if (e2 != null) {
			for (Entities.Entity e : e2.list)
				e1.list.remove(e);
			e1.list.addAll(e2.list);
		}
		res.entities = e1;
		if (e1 != null)
			res.text = decodeText(res.text, res.entities);
		return Tweet.updateTweet(res);
	}

	protected ArrayList<Tweet> parseTweetArray(JsonParser parser, TweetCallback callback) throws IOException, ParseException {
		ArrayList<Tweet> res = new ArrayList<>();
		while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_ARRAY) {
			if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
				Tweet tweet = parseTweet(parser);
				if (callback != null)
					callback.onNewTweetReceived(tweet);
				res.add(tweet);
			}
		}
		return res;
	}

	protected ArrayList<Tweeter> parseUserArray(JsonParser parser) throws IOException, ParseException {
		ArrayList<Tweeter> res = new ArrayList<>();
		while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_ARRAY) {
			if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
				res.add(parseUser(parser));
			}
		}
		return res;
	}

	public ArrayList<Tweet> getTweetArrayRequest(String url, String post, TweetCallback callback) throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + url, auth, post != null, post);
		if (request == null) return null;
		InputStream in = null;
		try {
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("getTweetArrayRequest", request);
			JsonParser parser = JSON.createParser(in = request.getInputStream());
			parser.nextToken();
			return parseTweetArray(parser, callback);
		} catch (ParseException | IOException e) {
			throw new RequestException("getTweetArrayRequest", e);
		} finally {
			StreamTools.close(in);
			request.close();
		}
	}

	public ArrayList<Tweeter> getTweeterArrayRequest(String url, String post) throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + url, auth, post != null, post);
		if (request == null) return null;
		InputStream in = null;
		try {
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("getTweeterArrayRequest", request);
			JsonParser parser = JSON.createParser(in = request.getInputStream());
			parser.nextToken();
			return parseUserArray(parser);
		} catch (ParseException | IOException e) {
			throw new RequestException("getTweeterArrayRequest", e);
		} finally {
			StreamTools.close(in);
			request.close();
		}
	}

	public ArrayList<Tweet> getUserHome(long user_id, int count, long since_id, long max_id, boolean exclude_replies, boolean include_entities, TweetCallback callback) throws RequestException {
		return getTweetArrayRequest("statuses/user_timeline.json?user_id=" + user_id + "&exclude_replies=" + (exclude_replies ? "t" : "f") + "&include_entities=" + (include_entities ? "t" : "f") +
				(count > 0 ? "&count=" + count : "") + (since_id > 0 ? "&since_id=" + since_id : "") + (max_id > 0 ? "&max_id=" + max_id : ""), null, callback);
	}

	public ArrayList<Tweet> getUserFavorites(long user_id, int count, long since_id, long max_id, boolean include_entities, TweetCallback callback) throws RequestException {
		return getTweetArrayRequest("favorites/list.json?user_id=" + user_id + "&include_entities=" + (include_entities ? "t" : "f") +
				(count > 0 ? "&count=" + count : "") + (since_id > 0 ? "&since_id=" + since_id : "") + (max_id > 0 ? "&max_id=" + max_id : ""), null, callback);
	}

	public ArrayList<Tweet> getSearchTweets(String q, int count, long since_id, long max_id, boolean exclude_replies, boolean include_entities, TweetCallback callback) throws RequestException {
		return getTweetArrayRequest("search/tweets.json?q=" + StringTools.UrlEncode(q) + "&exclude_replies=" + (exclude_replies ? "t" : "f") + "&include_entities=" + (include_entities ? "t" : "f") +
				(count > 0 ? "&count=" + count : "") + (since_id > 0 ? "&since_id=" + since_id : "") + (max_id > 0 ? "&max_id=" + max_id : ""), null, callback);
	}

	public ArrayList<Tweet> getTweets(String path, int count, long since_id, long max_id, boolean exclude_replies, boolean include_entities, TweetCallback callback) throws RequestException {
		return getTweetArrayRequest(path + ".json?exclude_replies=" + (exclude_replies ? "t" : "f") + "&include_entities=" + (include_entities ? "t" : "f") +
				(count > 0 ? "&count=" + count : "") + (since_id > 0 ? "&since_id=" + since_id : "") + (max_id > 0 ? "&max_id=" + max_id : ""), null, callback);
	}

	public ArrayList<Tweeter> lookupUser(long... ids) throws RequestException {
		if (ids.length > 1) {
			StringBuilder query = new StringBuilder("user_id=");
			query.append(ids[0]);
			for (int i = ids.length - 1; i >= 1; i--) {
				query.append("%2C").append(ids[i]);
			}
			return getTweeterArrayRequest("users/lookup.json", query.toString());
		} else {
			HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "users/show.json?user_id=" + ids[0], auth, false, null);
			if (request == null) return null;
			InputStream in = null;
			try {
				request.submitRequest();
				if (request.getStatusCode() != 200)
					throw new RequestException("lookupUser", request);
				JsonParser parser = JSON.createParser(in = request.getInputStream());
				parser.nextToken();
				ArrayList<Tweeter> mList = new ArrayList<>();
				mList.add(parseUser(parser));
				return mList;
			} catch (ParseException | IOException e) {
				throw new RequestException("lookupUser", e);
			} finally {
				StreamTools.close(in);
				request.close();
			}
		}
	}

	public ArrayList<Tweeter> lookupUser(String... screen_names) throws RequestException {
		if (screen_names.length > 1) {
			StringBuilder query = new StringBuilder("screen_name=");
			query.append(screen_names[0]);
			for (int i = screen_names.length - 1; i >= 1; i--) {
				query.append("%2C").append(screen_names[i]);
			}
			return getTweeterArrayRequest("users/lookup.json", query.toString());
		} else {
			HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "users/show.json?screen_name=" + screen_names[0], auth, false, null);
			if (request == null) return null;
			InputStream in = null;
			try {
				request.submitRequest();
				if (request.getStatusCode() != 200)
					throw new RequestException("lookupUser", request);
				JsonParser parser = JSON.createParser(in = request.getInputStream());
				parser.nextToken();
				ArrayList<Tweeter> mList = new ArrayList<>();
				mList.add(parseUser(parser));
				return mList;
			} catch (ParseException | IOException e) {
				throw new RequestException("lookupUser", e);
			} finally {
				StreamTools.close(in);
				request.close();
			}
		}
	}

	public long uploadMedia(File localFile, List<Long> additionalUsers) throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(UPLOAD_BASE_PATH + "media/upload.json", auth, true, null);
		if (request == null) throw new RequestException("uploadMedia", "Nulled");
		try {
			if (additionalUsers != null && !additionalUsers.isEmpty()) {
				StringBuilder additional = new StringBuilder();
				for (long l : additionalUsers) {
					if (additional.length() != 0)
						additional.append(",");
					additional.append(l);
				}
				request.addMultipartParameter("additional_owners", additional.toString());
			}
			request.addMultipartFileParameter("media", localFile);
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("uploadMedia", request);
			JSONObject parsed = new JSONObject(request.getWholeData());
			return parsed.getLong("media_id");
		} catch (JSONException e) {
			throw new RequestException("uploadMedia", e);
		} finally {
			request.close();
		}
	}

	public long uploadMediaChunkInit(long length, String mediaType, List<Long> additionalUsers) throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(UPLOAD_BASE_PATH + "media/upload.json", auth, true, null);
		if (request == null) throw new RequestException("uploadMediaChunkInit", "Nulled");
		try {
			if (additionalUsers != null && !additionalUsers.isEmpty()) {
				StringBuilder additional = new StringBuilder();
				for (long l : additionalUsers) {
					if (additional.length() != 0)
						additional.append(",");
					additional.append(l);
				}
				request.addMultipartParameter("additional_owners", additional.toString());
			}
			request.addMultipartParameter("command", "INIT");
			request.addMultipartParameter("total_bytes", Long.toString(length));
			request.addMultipartParameter("media_type", mediaType);
			request.submitRequest();
			if (request.getStatusCode() / 100 != 2)
				throw new RequestException("uploadMediaChunkInit", request);
			JSONObject parsed = new JSONObject(request.getWholeData());
			return parsed.getLong("media_id");
		} catch (JSONException e) {
			throw new RequestException("uploadMediaChunkInit", e);
		} finally {
			request.close();
		}
	}

	public void uploadMediaChunkAppend(long media_id, int segment_index, File localFile, long from, long to) throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(UPLOAD_BASE_PATH + "media/upload.json", auth, true, null);
		if (request == null) throw new RequestException("uploadMediaChunkAppend", "Nulled");
		try {
			request.addMultipartParameter("media_id", Long.toString(media_id));
			request.addMultipartParameter("segment_index", Integer.toString(segment_index));
			request.addMultipartParameter("command", "APPEND");
			request.addMultipartFileParameter("media", localFile, from, to);
			request.submitRequest();
			if (request.getStatusCode() / 100 != 2)
				throw new RequestException("uploadMediaChunkAppend", request);
		} finally {
			request.close();
		}
	}

	public long uploadMediaFinalize(long media_id) throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(UPLOAD_BASE_PATH + "media/upload.json", auth, true, null);
		if (request == null) throw new RequestException("uploadMediaFinalize", "Nulled");
		try {
			request.addMultipartParameter("media_id", Long.toString(media_id));
			request.addMultipartParameter("command", "FINALIZE");
			request.submitRequest();
			if (request.getStatusCode() / 100 != 2)
				throw new RequestException("uploadMediaChunkInit", request);
			JSONObject parsed = new JSONObject(request.getWholeData());
			return parsed.getLong("media_id");
		} catch (JSONException e) {
			throw new RequestException("uploadMediaFinalize", e);
		} finally {
			request.close();
		}
	}

	public ArrayList<Tweeter> searchUser(String q, int count, int page) throws RequestException {
		return getTweeterArrayRequest("users/search.json?q=" + StringTools.UrlEncode(q) + "&count=" + count + "&page=" + page, null);
	}

	public Tweet postTweet(String status, long in_reply_to_status_id, float latitude, float longitude, boolean display_coordinates, List<Long> media_ids) throws RequestException {
		String post = "status=" + StringTools.UrlEncode(status) + "&in_reply_to_status_id=" + in_reply_to_status_id + "&latitude=" + latitude + "&longitude=" + longitude + "&display_coordinates=" + (display_coordinates ? "t" : "f");
		if (media_ids != null && !media_ids.isEmpty()) {
			post += "&media_ids=" + media_ids.remove(0);
			while (!media_ids.isEmpty())
				post += "%2C" + media_ids.remove(0);
		}
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "statuses/update.json", auth, true, post);
		if (request == null) return null;
		InputStream in = null;
		try {
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("postTweet", request);
			JsonParser parser = JSON.createParser(in = request.getInputStream());
			parser.nextToken();
			return parseTweet(parser);
		} catch (ParseException | IOException e) {
			throw new RequestException("postTweet", e);
		} finally {
			StreamTools.close(in);
			request.close();
		}
	}

	public Tweet postRetweet(long id) throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "statuses/retweet/" + id + ".json", auth, true, null);
		if (request == null) return null;
		InputStream in = null;
		try {
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("postRetweet", request);
			JsonParser parser = JSON.createParser(in = request.getInputStream());
			parser.nextToken();
			return parseTweet(parser);
		} catch (ParseException | IOException e) {
			throw new RequestException("postRetweet", e);
		} finally {
			StreamTools.close(in);
			request.close();
		}
	}

	public Tweet postFavorite(long id) throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "favorites/create.json", auth, true, "id=" + id);
		if (request == null) return null;
		InputStream in = null;
		try {
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("postFavorite", request);
			JsonParser parser = JSON.createParser(in = request.getInputStream());
			parser.nextToken();
			return parseTweet(parser);
		} catch (ParseException | IOException e) {
			throw new RequestException("postFavorite", e);
		} finally {
			StreamTools.close(in);
			request.close();
		}
	}

	private void parseConfiguration(JsonParser parser, String path, SharedPreferences.Editor editor) throws IOException {
		if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
			while (parser.nextToken() != JsonToken.END_OBJECT) {
				String key = parser.getCurrentName();
				parser.nextToken();
				parseConfiguration(parser, path.isEmpty() ? key : path + "." + key, editor);
			}
		} else if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
			int i = 0;
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				parseConfiguration(parser, (path.isEmpty() ? "" : path + ".") + i, editor);
				i++;
			}
		} else {
			try {
				editor.putInt(path, Integer.parseInt(parser.getText()));
			} catch (Exception e) {
				editor.putString(path, parser.getText());
			}
		}
	}

	public void loadConfiguration() throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "help/configuration.json", auth, false, null);
		if (request == null) return;
		InputStream in = null;
		try {
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("loadConfiguration", request);
			in = request.getInputStream();
			JsonParser parser = JSON.createParser(in = request.getInputStream());
			SharedPreferences.Editor editor = mTwitterConfiguration.edit();
			parseConfiguration(parser, "", editor);
			editor.putLong("lastEditTime", System.currentTimeMillis());
			editor.apply();
		} catch (IOException e) {
			throw new RequestException("loadConfiguration", e);
		} finally {
			StreamTools.close(in);
			request.close();
		}
	}

	public ArrayList<SavedSearch> getSavedSearches() throws RequestException {
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "saved_searches/list.json", auth, false, null);
		if (request == null) return null;
		InputStream in = null;
		try {
			request.submitRequest();
			if (request.getStatusCode() != 200)
				throw new RequestException("getSavedSearches", request);
			in = request.getInputStream();
			JsonParser parser = JSON.createParser(in = request.getInputStream());
			return parseSavedSearchArray(parser);
		} catch (Throwable e) {
			throw new RequestException("getSavedSearches", e);
		} finally {
			StreamTools.close(in);
			request.close();
		}
	}

	@Override
	public int compareTo(@NonNull TwitterEngine another) {
		int eq = mEngineIndex - another.mEngineIndex;
		if (eq == 0)
			eq = mUserId > another.mUserId ? 1 : mUserId == another.mUserId ? 0 : -1;
		return eq;
	}

	@Override
	public boolean equals(Object o) {
		return o != null && o instanceof TwitterEngine && (((TwitterEngine) o).mUserId == mUserId) && ((TwitterEngine) o).auth.equals(auth);
	}

	public void addStreamCallback(TwitterStreamCallback callback) {
		synchronized (mStreamCallbacks) {
			if (!mStreamCallbacks.contains(callback))
				mStreamCallbacks.add(callback);
		}
	}

	public void removeStreamCallback(TwitterStreamCallback callback) {
		synchronized (mStreamCallbacks) {
			mStreamCallbacks.remove(callback);
		}
	}

	public void setUseStream(boolean use) {
		synchronized (mStreamCallbacks) {
			if (use && !mStreamCallbacks.isEmpty() && (mStreamThread == null || !mStreamThread.isAlive())) {
				mStreamThread = new StreamThread(this, mStreamCallbacks);
				mStreamThread.start();
			} else if (!use && (mStreamThread != null && mStreamThread.isAlive())) {
				mStreamThread.stopStream();
			}
		}
	}

	public void breakStreamAndRetryConnect() {
		if (mStreamThread != null)
			mStreamThread.interrupt();
	}

	public long getLastActivityTime() {
		StreamThread t = mStreamThread;
		return t != null ? t.getLastActivityTime() : 0;
	}

	public boolean isStreamUsed() {
		return mStreamThread != null && mStreamThread.isAlive();
	}

	public interface OnUserlistChangedListener {
		void onUserlistChanged(List<TwitterEngine> engines, List<TwitterEngine> oldEngines);
	}

	public interface TweetCallback {
		void onNewTweetReceived(Tweet tweet);
	}

	public interface TwitterStreamCallback extends TweetCallback {
		void onStreamStart(TwitterEngine engine);

		void onStreamConnected(TwitterEngine engine);

		void onStreamError(TwitterEngine engine, Throwable e);

		void onStreamTweetEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, Tweet tweet, long created_at);

		// void onListStreamEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, List list, long created_at);
		void onStreamUserEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, long created_at);

		void onStreamStop(TwitterEngine engine);
	}

	static class StreamThread extends Thread {
		private final ArrayList<Long> mStreamFriendsList = new ArrayList<>();
		private final TwitterEngine mEngine;
		private final TwitterStreamCallback mCallback;
		private final List<TwitterStreamCallback> mStreamCallbacks;
		private boolean mStopRequested;
		private boolean mStopCallbacked = false;
		private long mStreamLastActivityTime;

		public StreamThread(TwitterEngine e, List<TwitterStreamCallback> callbacks) {
			super();
			mEngine = e;
			mStreamCallbacks = callbacks;
			mCallback = new TwitterStreamCallback() {

				@Override
				public void onStreamConnected(final TwitterEngine engine) {
					if (mStopCallbacked)
						return;
					mEngine.mHandler.post(new Runnable() {
						@Override
						public void run() {
							synchronized (mStreamCallbacks) {
								for (TwitterStreamCallback c : mStreamCallbacks)
									c.onStreamConnected(engine);
							}
						}
					});
				}

				@Override
				public void onStreamStart(final TwitterEngine engine) {
					if (mStopCallbacked)
						return;
					mEngine.mHandler.post(new Runnable() {
						@Override
						public void run() {
							synchronized (mStreamCallbacks) {
								for (TwitterStreamCallback c : mStreamCallbacks)
									c.onStreamStart(engine);
							}
						}
					});
				}

				@Override
				public void onStreamError(final TwitterEngine engine, final Throwable e) {
					if (mStopCallbacked)
						return;
					mEngine.mHandler.post(new Runnable() {
						@Override
						public void run() {
							synchronized (mStreamCallbacks) {
								for (TwitterStreamCallback c : mStreamCallbacks)
									c.onStreamError(engine, e);
							}
						}
					});
				}

				@Override
				public void onStreamTweetEvent(final TwitterEngine engine, final String event, final Tweeter source, final Tweeter target, final Tweet tweet, final long created_at) {
					if (mStopCallbacked)
						return;
					mEngine.mHandler.post(new Runnable() {
						@Override
						public void run() {
							synchronized (mStreamCallbacks) {
								for (TwitterStreamCallback c : mStreamCallbacks)
									c.onStreamTweetEvent(engine, event, source, target, tweet, created_at);
							}
						}
					});
				}

				@Override
				public void onStreamUserEvent(final TwitterEngine engine, final String event, final Tweeter source, final Tweeter target, final long created_at) {
					if (mStopCallbacked)
						return;
					mEngine.mHandler.post(new Runnable() {
						@Override
						public void run() {
							synchronized (mStreamCallbacks) {
								for (TwitterStreamCallback c : mStreamCallbacks)
									c.onStreamUserEvent(engine, event, source, target, created_at);
							}
						}
					});
				}

				@Override
				public void onStreamStop(final TwitterEngine engine) {
					if (mStopCallbacked)
						return;
					mEngine.mHandler.post(new Runnable() {
						@Override
						public void run() {
							synchronized (mStreamCallbacks) {
								for (TwitterStreamCallback c : mStreamCallbacks)
									c.onStreamStop(engine);
							}
						}
					});
				}

				@Override
				public void onNewTweetReceived(Tweet tweet) {
					if (mStopCallbacked)
						return;
					synchronized (mStreamCallbacks) {
						for (TwitterStreamCallback c : mStreamCallbacks)
							c.onNewTweetReceived(tweet);
					}
				}
			};
		}

		public void stopStream() {
			mStopRequested = true;
			interrupt();
		}

		@Override
		public void interrupt() {
			if (mStopRequested && !mStopCallbacked) {
				mCallback.onStreamStop(mEngine);
				mStopCallbacked = true;
			}
			super.interrupt();
		}

		@Override
		public void run() {
			Thread.currentThread().setName("StreamThread for " + mEngine.getScreenName());
			int errorWaitTime = 0;
			mCallback.onStreamStart(mEngine);
			try {
				while (!mStopRequested) {
					Thread.sleep(errorWaitTime);
					try {
						final HTTPRequest conn = HTTPRequest.getRequest(STREAM_BASE_PATH + "user.json", mEngine.auth, false, null);
						int statusCode;
						if (conn == null) {
							statusCode = 0;
						} else {
							conn.setConnectTimeout(10000);
							conn.setReadTimeout(90000);
							conn.submitRequest();
							statusCode = conn.getStatusCode();
						}
						if (statusCode != 200) {
							if (statusCode == 0) { // Network error
								if (errorWaitTime < 16000)
									errorWaitTime += 250;
							} else if (statusCode == 420) { // Rate Limit
								if (errorWaitTime < 60000)
									errorWaitTime += 60000;
								else
									errorWaitTime *= 2;
								if (errorWaitTime > 600000)
									errorWaitTime = 600000;
							} else {
								if (errorWaitTime < 5000)
									errorWaitTime += 5000;
								else
									errorWaitTime *= 2;
								if (errorWaitTime > 320000)
									errorWaitTime = 320000;
							}
							if (!Thread.interrupted())
								mCallback.onStreamError(mEngine, new RequestException("StreamRunner", conn));
							continue;
						}
						errorWaitTime = 0;
						final InputStream in = conn.getInputStream(false);
						try {
							mStreamLastActivityTime = System.currentTimeMillis();
							mCallback.onStreamConnected(mEngine);
							final byte[] buffer = new byte[8192];
							int searchFrom = 0;
							ByteBuffer data = ByteBuffer.allocate(buffer.length);
							while (!mStopRequested) {
								int read = in.read(buffer, 0, Math.max(1, Math.min(buffer.length, in.available())));
								if (Thread.interrupted())
									break;
								if (data.remaining() < read) {
									ByteBuffer tmp = ByteBuffer.allocate(data.capacity() * 2);
									tmp.put(data.array(), 0, data.position());
									data = tmp;
								}
								data.put(buffer, 0, read);
								while (true) {
									int pos = ArrayTools.indexOf(data.array(), searchFrom, data.position(), CRLF, CRLF_FAILURE);
									if (pos == -1) {
										searchFrom = Math.max(0, data.position() - CRLF.length + 1);
										break;
									}
									if (mStopRequested)
										return;
									parseStreamMessage(new String(data.array(), 0, pos));
									data.limit(data.position());
									data.position(pos + CRLF.length);
									data.compact();
									searchFrom = 0;
								}
								if (data.position() > MAX_STREAM_HOLD)
									stopStream();
							}
						} finally {
							conn.close();
							StreamTools.close(in);
						}
						errorWaitTime = 0; // interrupted
					} catch (Throwable e) {
						errorWaitTime = 250;
						e.printStackTrace();
						mCallback.onStreamError(mEngine, e);
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				if (!mStopCallbacked) {
					mCallback.onStreamStop(mEngine);
					mStopCallbacked = true;
				}
			}
		}

		// TODO
		protected void parseStreamMessage(String line) {
			mStreamLastActivityTime = System.currentTimeMillis();
			if (line.isEmpty()) return;
			try {
				if (line.startsWith("{\"delete\":{")) {
					Tweet removed = Tweet.getTweet(new JSONObject(line).getJSONObject("delete").getJSONObject("status").getLong("id"));
					removed.removed = true;
					removed.info.lastUpdated = System.currentTimeMillis();
					mCallback.onStreamTweetEvent(mEngine, "delete", null, null, removed, 0);
				} else if (line.startsWith("{\"friends\":[")) {
					JSONArray arr = new JSONObject(line).getJSONArray("friends");
					mStreamFriendsList.clear();
					for (int i = 0; i < arr.length(); i++)
						mStreamFriendsList.add(arr.getLong(i));
				} else if (line.startsWith("{\"scrub_geo\":{")) {
				} else if (line.startsWith("{\"limit\":{")) {
				} else if (line.startsWith("{\"status_withheld\":{")) {
				} else if (line.startsWith("{\"user_withheld\":{")) {
				} else if (line.startsWith("{\"warning\":{")) {
				} else if (line.startsWith("{\"disconnect\":{")) {
				} else if (line.contains("\"event\":\"")) {
					JsonParser parser = JSON.createParser(line);
					parser.nextToken();
					Tweeter source = null, target = null;
					Tweet target_object = null;
					String event = null;
					long created_at = -1;
					while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
						String key = parser.getCurrentName();
						parser.nextToken();
						if (parser.getCurrentToken() == JsonToken.VALUE_NULL) continue;
						switch (key) {
							case "source":
								source = mEngine.parseUser(parser);
								break;
							case "target":
								target = mEngine.parseUser(parser);
								break;
							case "event":
								event = parser.getText();
								break;
							case "created_at":
								created_at = parseTwitterDate(parser.getText());
								break;
							case "target_object":
								if (event == null)
									if ((event = new JSONObject(line).getString("event")) == null)
										break;
								switch (event) {
									case "favorite":
									case "unfavorite":
										target_object = mEngine.parseTweet(parser, mStreamFriendsList);
										break;
									case "list_created":
									case "list_destroyed":
									case "list_updated":
									case "list_member_added":
									case "list_member_removed":
									case "list_user_subscribed":
									case "list_user_unsubscribed":
										StreamTools.consumeJsonValue(parser);
										break;
									default:
										StreamTools.consumeJsonValue(parser);
										break;
								}
								break;
							default:
								StreamTools.consumeJsonValue(parser);
						}
					}
					if (event == null)
						return;
					switch (event) {
						case "user_update": // target = source
							if (source == null)
								return;
							mCallback.onStreamUserEvent(mEngine, event, source, null, created_at);
							break;
						case "block":
							if (target == null || source == null)
								return;
							mCallback.onStreamUserEvent(mEngine, event, source, target, created_at);
							break;
						case "unblock":
							if (target == null || source == null)
								return;
							mCallback.onStreamUserEvent(mEngine, event, source, target, created_at);
							break;
						case "favorite":
							mCallback.onStreamTweetEvent(mEngine, event, source, target, target_object, created_at);
							break;
						case "unfavorite":
							mCallback.onStreamTweetEvent(mEngine, event, source, target, target_object, created_at);
							break;
						case "quoted_tweet":
							mCallback.onStreamTweetEvent(mEngine, event, source, target, target_object, created_at);
							break;
						case "follow":
							if (target == null || source == null)
								return;
							if (mEngine.getTweeter().equals(source))
								mStreamFriendsList.add(target.user_id);
							mCallback.onStreamUserEvent(mEngine, event, source, target, created_at);
							break;
						case "unfollow":
							if (target == null || source == null)
								return;
							if (mEngine.getTweeter().equals(source))
								mStreamFriendsList.remove(target.user_id);
							mCallback.onStreamUserEvent(mEngine, event, source, target, created_at);
							break;
						case "list_created":
							break;
						case "list_destroyed":
							break;
						case "list_updated":
							break;
						case "list_member_added":
							break;
						case "list_member_removed":
							break;
						case "list_user_subscribed":
							break;
						case "list_user_unsubscribed":
							break;
					}
				} else if (line.contains("\"recipient\":{") && line.contains("\"sender\":{")) {
				} else {
					JsonParser parser = JSON.createParser(line);
					parser.nextToken();
					Tweet t = mEngine.parseTweet(parser, mStreamFriendsList);
					if (t.id == 0)
						return;
					mCallback.onNewTweetReceived(t);
				}
			} catch (Exception e) {
				e.printStackTrace();
				ACRA.log.e("STREAM_PARSER", "stream line parse fail: " + line, e);
			}
		}

		public long getLastActivityTime() {
			return mStreamLastActivityTime;
		}

	}

	public static class RequestException extends Exception {

		public RequestException(String functionName, Throwable exception) {
			android.util.Log.d("TwitterEngine", functionName, exception);
		}

		public RequestException(String functionName, String description) {
			android.util.Log.d("TwitterEngine", functionName + " - " + description);
		}

		public RequestException(String functionName, HTTPRequest request) {
			try {
				android.util.Log.d("TwitterEngine", functionName + " - " + request.getWholeData(), request.getLastError());
			} catch (Exception e) {
				android.util.Log.d("TwitterEngine " + functionName, "StatusCode: " + request.getStatusCode(), request.getLastError());
			}
		}
	}
}
