package com.soreepeong.darknova.twitter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Html;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ArrayTools;
import com.soreepeong.darknova.core.HTTPRequest;
import com.soreepeong.darknova.core.OAuth;
import com.soreepeong.darknova.core.StreamTools;
import com.soreepeong.darknova.core.StringTools;
import com.soreepeong.darknova.services.DarknovaService;

import org.acra.ACRA;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Soreepeong on 2015-04-24.
 */
public class TwitterEngine {
	protected static final String DEFAULT_API_KEY = "xAMJy6IMIBF6KY2RQAyvw";
	protected static final String DEFAULT_SECRET_API_KEY = "qq8aNTvEVlVn9NWJwZTJwCLrluGOXoX8dt7rI4A24";

	protected static final int MAX_STREAM_HOLD = 1048576 * 3; // 3MB

	protected static final SimpleDateFormat CREATED_AT_STRING = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy", Locale.ENGLISH);
	protected static final Pattern TWITTER_PROFILE_IMAGE_MATCHER = Pattern.compile("^(.*?)(?:_normal|_bigger|_mini|)(\\.[A-Za-z_0-9]+|)?$");

	/**
	 * Twitter API Base Paths
	 */
	protected static String API_BASE_PATH = "https://api.twitter.com/1.1/";
	protected static String UPLOAD_BASE_PATH = "https://upload.twitter.com/1.1/";
	protected static String STREAM_BASE_PATH = "https://userstream.twitter.com/1.1/";
	protected static String AUTH_REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
	protected static String AUTH_ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
	protected static String AUTH_AUTHORIZE_URL = "https://api.twitter.com/oauth/authorize";

	protected final OAuth auth;
	protected final JsonFactory JSON;

	protected long mUserId;
	protected String mScreenName;
	protected Tweeter mTweeter;

	public static final byte[] CRLF = new byte[]{'\r', '\n'};
	public static final int[] CRLF_FAILURE = ArrayTools.indexOfFailureFunction(CRLF);

	protected static SharedPreferences mTwitterConfiguration;
	protected static final ArrayList<OnUserlistChangedListener> mUserlistChangedListener = new ArrayList<>();
	protected static final CopyOnWriteArrayList<StreamableTwitterEngine> mTwitter = new CopyOnWriteArrayList<>();


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

	protected static final Runnable BROADCAST_USERLIST_CHANGE_RUNNABLE = new Runnable() {
		@Override
		public void run() {
			synchronized (mUserlistChangedListener) {
				List<StreamableTwitterEngine> mEngines = Collections.unmodifiableList(new ArrayList<>(mTwitter));
				for (OnUserlistChangedListener l : mUserlistChangedListener)
					l.onUserlistChanged(mEngines);
				TwitterStreamServiceReceiver.sendServiceMessage(Message.obtain(null, DarknovaService.MESSAGE_USERLIST_CHANGED));
			}
		}
	};

	public static void broadcastUserlistChange() {
		new Handler(Looper.getMainLooper()).post(BROADCAST_USERLIST_CHANGE_RUNNABLE);
	}

	public static StreamableTwitterEngine getTwitterEngine(Context context, long user_id) {
		synchronized (mTwitter) {
			prepare(context, false);
			for (StreamableTwitterEngine e : mTwitter)
				if (e.mUserId == user_id)
					return e;
			return null;
		}
	}

	public static ArrayList<StreamableTwitterEngine> getStreamableTwitterEngines(Context context) {
		synchronized (mTwitter) {
			prepare(context, false);
			return new ArrayList<>(mTwitter);
		}
	}

	public static ArrayList<TwitterEngine> getTwitterEngines(Context context) {
		synchronized (mTwitter) {
			prepare(context, false);
			ArrayList<TwitterEngine> engines = new ArrayList<>();
			for (StreamableTwitterEngine e : mTwitter)
				engines.add(e);
			return engines;
		}
	}

	public static void applyAccountInformationChanges(Context context) {
		synchronized (mTwitter) {
			AccountManager accountManager = AccountManager.get(context);
			for (Account acc : accountManager.getAccountsByType(context.getString(R.string.account_type))) {
				long user_id = Long.parseLong(accountManager.getUserData(acc, "user_id"));
				Tweeter tweeter = Tweeter.getTweeter(user_id, accountManager.getUserData(acc, "screen_name"));
				if (!tweeter.info.stub && !tweeter.screen_name.equals(accountManager.getUserData(acc, "screen_name"))) {
					accountManager.setUserData(acc, "screen_name", tweeter.screen_name);
				}
			}
		}
	}

	public static void prepare(Context context, boolean invalidate) {
		synchronized (mTwitter) {
			if(invalidate)
				mTwitter.clear();
			if (!mTwitter.isEmpty())
				return;
			mTwitterConfiguration = context.getSharedPreferences("TwitterEngine.configuration", Context.MODE_MULTI_PROCESS);
			mTwitter.clear();
			synchronized (mTwitter) {
				AccountManager accountManager = AccountManager.get(context);
				for (Account acc : accountManager.getAccountsByType(context.getString(R.string.account_type))) {
					StreamableTwitterEngine engine = new StreamableTwitterEngine(accountManager.getUserData(acc, "consumer_key"), accountManager.getUserData(acc, "consumer_key_secret"));
					engine.setOauthToken(accountManager.getUserData(acc, "oauth_token"), accountManager.getUserData(acc, "oauth_token_secret"));
					engine.setUserInfo(Long.decode(accountManager.getUserData(acc, "user_id")), accountManager.getUserData(acc, "screen_name"));
					mTwitter.add(engine);
				}
			}
			if (System.currentTimeMillis() - mTwitterConfiguration.getLong("lastEditTime", 0) > 86400_000)
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
			broadcastUserlistChange();
		}
	}

	/**
	 * Initialize with default API Key.
	 */
	public TwitterEngine() {
		this(DEFAULT_API_KEY, DEFAULT_SECRET_API_KEY);
	}

	/**
	 * Initialize with given API Key.
	 *
	 * @param apiKey
	 * @param secretApiKey
	 */
	public TwitterEngine(String apiKey, String secretApiKey) {
		auth = new OAuth(apiKey, secretApiKey);
		JSON = new JsonFactory();

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
		HTTPRequest request = HTTPRequest.getRequest(AUTH_REQUEST_TOKEN_URL, null, true, callbackUrl == null ? "" : ("oauth_callback=" + StringTools.UrlEncode(callbackUrl)), false); 		if(request == null) 			return null;
		try {
			request.addParameter("Authorization", auth.getRequestTokenHeader(AUTH_REQUEST_TOKEN_URL, callbackUrl));
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
		HTTPRequest request = HTTPRequest.getRequest(AUTH_ACCESS_TOKEN_URL, null, true, "oauth_verifier=" + StringTools.UrlEncode(pin), false); 		if(request == null) 			return null;
		try {
			request.addParameter("Authorization", auth.getAuthorizeHeader(AUTH_ACCESS_TOKEN_URL, pin));
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

	protected long parseTwitterDate(String date) throws ParseException {
		synchronized (CREATED_AT_STRING) {
			return CREATED_AT_STRING.parse(date).getTime();
		}
	}

	protected Tweeter parseUser(JsonParser parser) throws IOException, ParseException {
		return parseUser(parser, null);
	}

	protected Tweeter parseUser(JsonParser parser, ArrayList<Long> friends) throws IOException, ParseException {
		Tweeter res = Tweeter.getTemporaryTweeter();
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
		res.description = decodeText(res.url, res.entities_description);
		return Tweeter.updateTweeter(res);
	}

	protected Tweet parseTweet(JsonParser parser) throws IOException, ParseException {
		return parseTweet(parser, null);
	}

	protected Tweet parseTweet(JsonParser parser, ArrayList<Long> friends) throws IOException, ParseException {
		Tweet res = Tweet.getTemporaryTweet();
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
		if (e1 == null) e1 = e2;
		else if (e2 != null) {
			for (Entities.Entity e : e2.entities)
				e1.entities.remove(e);
			e1.entities.addAll(e2.entities);
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
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + url, auth, post != null, post, false); 		if(request == null) 			return null;
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
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + url, auth, post != null, post, false); 		if(request == null) 			return null;
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

	public ArrayList<Tweet> getSearchTweets(String q, int count, long since_id, long max_id, boolean exclude_replies, boolean include_entities, TweetCallback callback) throws RequestException {
		return getTweetArrayRequest("search/tweets.json?q=" + StringTools.UrlEncode(q) + "&exclude_replies=" + (exclude_replies ? "t" : "f") + "&include_entities=" + (include_entities ? "t" : "f") +
				(count > 0 ? "&count=" + count : "") + (since_id > 0 ? "&since_id=" + since_id : "") + (max_id > 0 ? "&max_id=" + max_id : ""), null, callback);
	}

	public ArrayList<Tweet> getTweets(String path, int count, long since_id, long max_id, boolean exclude_replies, boolean include_entities, TweetCallback callback) throws RequestException {
		return getTweetArrayRequest(path + ".json?exclude_replies=" + (exclude_replies ? "t" : "f") + "&include_entities=" + (include_entities ? "t" : "f") +
				(count > 0 ? "&count=" + count : "") + (since_id > 0 ? "&since_id=" + since_id : "") + (max_id > 0 ? "&max_id=" + max_id : ""), null, callback);
	}

	public ArrayList<Tweeter> lookupUser(long... ids) throws RequestException{
		if(ids.length > 1){
			StringBuilder query = new StringBuilder("user_id=");
			query.append(ids[0]);
			for(int i = ids.length - 1; i >= 1; i--){
				query.append(",").append(ids[i]);
			}
			return getTweeterArrayRequest("users/lookup.json", query.toString());
		}else{
			HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "users/show.json?user_id="+ids[0], auth, false, null, false); 		if(request == null) 			return null;
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

	public ArrayList<Tweeter> lookupUser(String... screen_names) throws RequestException{
		if(screen_names.length > 1){
			StringBuilder query = new StringBuilder("screen_name=");
			query.append(screen_names[0]);
			for(int i = screen_names.length - 1; i >= 1; i--){
				query.append(",").append(screen_names[i]);
			}
			return getTweeterArrayRequest("users/lookup.json", query.toString());
		}else{
			HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "users/show.json?screen_name="+screen_names[0], auth, false, null, false); 		if(request == null) 			return null;
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

	public ArrayList<Tweeter> searchUser(String q, int count, int page) throws RequestException{
		return getTweeterArrayRequest("users/search.json?q="+StringTools.UrlEncode(q)+"&count="+count+"&page="+page, null);
	}

	public Tweet postTweet(String status, long in_reply_to_status_id, long latitude, long longitude, boolean display_coordinates, List<Long> media_ids) throws RequestException {
		String post = "status=" + StringTools.UrlEncode(status) + "&in_reply_to_status_id=" + in_reply_to_status_id + "&latitude=" + latitude + "&longitude=" + longitude + "&display_coordinates=" + (display_coordinates ? "t" : "f");
		if (media_ids != null && !media_ids.isEmpty()) {
			post += "&media_ids=" + media_ids.remove(0);
			while (!media_ids.isEmpty())
				post += "," + media_ids.remove(0);
		}
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "statuses/update.json", auth, true, post, false); 		if(request == null) 			return null;
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
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "statuses/retweet/" + id + ".json", auth, true, null, false); 		if(request == null) 			return null;
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

	private void parseConfiguration(JsonParser parser, String path, SharedPreferences.Editor editor) throws IOException {
		if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
			while (parser.nextToken() != JsonToken.END_OBJECT) {
				String key = parser.getCurrentName();
				parser.nextToken();
				parseConfiguration(parser, path + "." + key, editor);
			}
		} else if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
			int i = 0;
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				parseConfiguration(parser, path + "." + i++, editor);
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
		HTTPRequest request = HTTPRequest.getRequest(API_BASE_PATH + "help/configuration.json", auth, false, null, false); 		if(request == null) 			return;
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

	public interface OnUserlistChangedListener {
		public void onUserlistChanged(List<StreamableTwitterEngine> engines);
	}

	public interface TweetCallback {
		public void onNewTweetReceived(Tweet tweet);
	}

	public interface TwitterStreamCallback extends TweetCallback {
		public void onStreamStart(StreamableTwitterEngine engine);

		public void onStreamConnected(StreamableTwitterEngine engine);

		public void onStreamError(StreamableTwitterEngine engine, Exception e);

		public void onStreamTweetEvent(StreamableTwitterEngine engine, String event, Tweeter source, Tweeter target, Tweet tweet, long created_at);

		// public void onListStreamEvent(TwitterEngine engine, String event, Tweeter source, Tweeter target, List list, long created_at);
		public void onStreamUserEvent(StreamableTwitterEngine engine, String event, Tweeter source, Tweeter target);

		public void onStreamStop(StreamableTwitterEngine engine);
	}

	public class RequestException extends Exception {
		public RequestException(String functionName, Exception exception) {
			exception.printStackTrace();
		}

		public RequestException(String functionName, String description) {
			android.util.Log.d("TwitterEngine " + functionName, description);
		}

		public RequestException(String functionName, HTTPRequest request) {
			try {
				android.util.Log.d("TwitterEngine " + functionName, request.getWholeData());
			} catch (Exception e) {
				e.printStackTrace();
			}
			;
			if (request.getLastError() != null)
				request.getLastError().printStackTrace();
		}
	}

	public static class StreamableTwitterEngine extends TwitterEngine {

		protected Thread mStreamThread;
		protected HTTPRequest mStreamConnection;
		protected long mStreamLastActivityTime;
		protected final ArrayList<TwitterStreamCallback> mStreamCallbacks = new ArrayList<>();
		protected Handler mHandler = new Handler(Looper.getMainLooper());
		protected final ArrayList<Long> mStreamFriendsList = new ArrayList<>();
		protected final TwitterStreamCallback mStreamCallback = new TwitterStreamCallback() {

			@Override
			public void onStreamConnected(final StreamableTwitterEngine engine) {
				mHandler.post(new Runnable() {
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
			public void onStreamStart(final StreamableTwitterEngine engine) {
				mHandler.post(new Runnable() {
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
			public void onStreamError(final StreamableTwitterEngine engine, final Exception e) {
				mHandler.post(new Runnable() {
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
			public void onStreamTweetEvent(final StreamableTwitterEngine engine, final String event, final Tweeter source, final Tweeter target, final Tweet tweet, final long created_at) {
				mHandler.post(new Runnable() {
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
			public void onStreamUserEvent(final StreamableTwitterEngine engine, final String event, final Tweeter source, final Tweeter target) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						synchronized (mStreamCallbacks) {
							for (TwitterStreamCallback c : mStreamCallbacks)
								c.onStreamUserEvent(engine, event, source, target);
						}
					}
				});
			}

			@Override
			public void onStreamStop(final StreamableTwitterEngine engine) {
				mHandler.post(new Runnable() {
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
				synchronized (mStreamCallbacks) {
					for (TwitterStreamCallback c : mStreamCallbacks)
						c.onNewTweetReceived(tweet);
				}
			}
		};

		/**
		 * Initialize with given API Key.
		 *
		 * @param apiKey
		 * @param secretApiKey
		 */
		public StreamableTwitterEngine(String apiKey, String secretApiKey) {
			super(apiKey, secretApiKey);
		}

		public long getLastActivityTime() {
			return mStreamLastActivityTime;
		}

		// TODO
		protected void parseStreamMessage(String line) {
			mStreamLastActivityTime = System.currentTimeMillis();
			if (line.isEmpty()) return;
			try {
				JsonParser parser = JSON.createParser(line);
				parser.nextToken();
				Tweet t = parseTweet(parser, mStreamFriendsList);
				if (t.id == 0) {
					if ("event".equals(t.source)) {
						parser = JSON.createParser(line);
						parser.nextToken();
						Tweeter source = null, target = null;
						final String event = t.source;
						long created_at;
						while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
							String key = parser.getCurrentName();
							parser.nextToken();
							if (parser.getCurrentToken() == JsonToken.VALUE_NULL) continue;
							switch (key) {
								case "source":
									source = parseUser(parser);
									break;
								case "target":
									target = parseUser(parser);
									break;
								case "event":
									break;
								case "created_at":
									created_at = parseTwitterDate(parser.getText());
									break;
								case "target_object":
									switch (event) {
										case "favorite":
										case "unfavorite":
											t = parseTweet(parser, mStreamFriendsList);
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

						switch (t.text) {
							case "user_update":
								break;
							case "block":
								break;
							case "unblock":
								break;
							case "favorite":
								break;
							case "unfavorite":
								break;
							case "follow":
								if (getTweeter().equals(source))
									mStreamFriendsList.add(target.user_id);
								break;
							case "unfollow":
								if (getTweeter().equals(source))
									mStreamFriendsList.remove(target.user_id);
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
							// undefined
							default:
								break;
						}
					} else {
						switch (t.text) {
							case "delete": {
								t = Tweet.getExistingTweet(new JSONObject(line).getJSONObject("delete").getJSONObject("status").getLong("id"));
								if (t != null) {
									t.removed = true;
									t.info.lastUpdated = System.currentTimeMillis();
								}
								break;
							}
							case "scrub_geo":
								break;
							case "status_withheld":
								break;
							case "user_withheld":
								break;
							case "disconnect":
								break;
							case "warning":
								break;
							case "friends":
								JSONArray arr = new JSONObject(line).getJSONArray("friends");
								mStreamFriendsList.clear();
								for (int i = 0; i < arr.length(); i++)
									mStreamFriendsList.add(arr.getLong(i));
								break;
							// undefined
							default:
								break;
						}
					}
				} else {
					// is a tweet
					mStreamCallback.onNewTweetReceived(t);
				}
			} catch (Exception e) {
				e.printStackTrace();
				ACRA.log.e("STREAM_PARSER", "stream line parse fail: " + line, e);
			}
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
				if (use && !mStreamCallbacks.isEmpty() && mStreamThread == null) {
					mStreamThread = new Thread(mStreamRunner);
					mStreamThread.start();
				} else if (!use && mStreamThread != null) {
					HTTPRequest con = mStreamConnection;
					con.close();
					mStreamThread.interrupt();
					mStreamConnection = null;
					mStreamThread = null;
				}
			}
		}

		public void breakStreamAndRetryConnect() {
			HTTPRequest con = mStreamConnection;
			if (con != null) {
				con.close();
			}
		}

		public boolean isStreamUsed() {
			return mStreamThread != null;
		}

		protected Runnable mStreamRunner = new Runnable() {
			@Override
			public void run() {
				Thread.currentThread().setName("StreamThread for " + getScreenName());
				int errorWaitTime = 0;
				mStreamCallback.onStreamStart(StreamableTwitterEngine.this);
				try {
					while (!Thread.interrupted()) {
						Thread.sleep(errorWaitTime);
						HTTPRequest conn = mStreamConnection = HTTPRequest.getRequest(STREAM_BASE_PATH + "user.json", auth, false, null, true);
						InputStream in = null;
						try {
							int statusCode;
							if(conn == null){
								statusCode = 0;
							}else{
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
								if (Thread.interrupted())
									break;
								mStreamCallback.onStreamError(StreamableTwitterEngine.this, new RequestException("StreamRunner", conn));
								continue;
							}
							errorWaitTime = 0;
							in = conn.getInputStream(false);
							mStreamLastActivityTime = System.currentTimeMillis();
							mStreamCallback.onStreamConnected(StreamableTwitterEngine.this);
							byte[] buffer = new byte[8192];
							int searchFrom = 0;
							ByteBuffer data = ByteBuffer.allocate(buffer.length);
							while (!Thread.interrupted()) {
								int read = in.read(buffer, 0, Math.max(1, Math.min(buffer.length, in.available())));
								if (data.remaining() < read) {
									ByteBuffer tmp = ByteBuffer.allocate(data.capacity() * 2);
									tmp.put(data.array(), 0, data.position());
									data = tmp;
								}
								data.put(buffer, 0, read);
								while (!mStreamCallbacks.isEmpty()) {
									int pos = ArrayTools.indexOf(data.array(), searchFrom, data.position(), CRLF, CRLF_FAILURE);
									if (pos == -1) {
										searchFrom = Math.max(0, data.position() - CRLF.length + 1);
										break;
									}
									parseStreamMessage(new String(data.array(), 0, pos));
									data.limit(data.position());
									data.position(pos + CRLF.length);
									data.compact();
									searchFrom = 0;
								}
								if (data.position() > MAX_STREAM_HOLD)
									Thread.currentThread().interrupt();
							}
						} catch (Exception e) {
							errorWaitTime = 250;
							if (Thread.interrupted())
								break;
							e.printStackTrace();
							mStreamCallback.onStreamError(StreamableTwitterEngine.this, e);
						} finally {
							StreamTools.close(in);
							conn.close();
							if (mStreamConnection == conn)
								mStreamConnection = null;
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					mStreamFriendsList.clear();
					if (mStreamThread == Thread.currentThread())
						mStreamThread = null;
					mStreamCallback.onStreamStop(StreamableTwitterEngine.this);
				}
			}
		};
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
					for (Entities.Entity e : entity.entities) {
						if (e.indice_left > i) e.indice_left -= rep[1].length() - 1;
						if (e.indice_right > i) e.indice_right -= rep[1].length() - 1;
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
}
