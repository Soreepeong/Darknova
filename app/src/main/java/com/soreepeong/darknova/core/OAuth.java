package com.soreepeong.darknova.core;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;

import com.soreepeong.darknova.tools.StringTools;

import java.util.ArrayList;
import java.util.Collections;

/**
 * OAuth class.
 *
 * @author Soreepeong
 */
public class OAuth implements Parcelable{
	@SuppressWarnings("unused")
	public static final Parcelable.Creator<OAuth> CREATOR = new Parcelable.Creator<OAuth>() {
		@Override
		public OAuth createFromParcel(Parcel in) {
			return new OAuth(in);
		}

		@Override
		public OAuth[] newArray(int size) {
			return new OAuth[size];
		}
	};
	private String mApiKey;
	private String mSecretApiKey;
	private String mOauthToken;
	private String mOauthTokenSecret;

	/**
	 * Initialize with given API Key.
	 * @param apiKey API Key
	 * @param secretApiKey Secret API Key
	 */
	public OAuth(String apiKey, String secretApiKey){
		mApiKey=apiKey;
		mSecretApiKey=secretApiKey;
	}

	protected OAuth(Parcel in) {
		mApiKey = in.readString();
		mSecretApiKey = in.readString();
		mOauthToken = in.readString();
		mOauthTokenSecret = in.readString();
	}

	/**
	 * Get oAuth Token
	 * @return oAuth token
	 */
	public String getToken(){
		return mOauthToken;
	}

	/**
	 * Set the OAuth token.
	 * @param token oAuth token
	 */
	public void setToken(String token) {
		mOauthToken = token;
	}

	/**
	 * Get oAuth Secret Token
	 * @return oAuth Secret Token
	 */
	public String getSecretToken(){
		return mOauthTokenSecret;
	}

	/**
	 * Set the OAuth secret token.
	 * @param secretToken oAuth secret token
	 */
	public void setSecretToken(String secretToken) {
		mOauthTokenSecret = secretToken;
	}

	/**
	 * Get API Key
	 * @return API Key
	 */
	public String getApiKey(){
		return mApiKey;
	}

	/**
	 * Get secret API Key
	 * @return Secret API Key
	 */
	public String getSecretApiKey(){
		return mSecretApiKey;
	}

	/**
	 * Set the OAuth tokens.
	 * @param token oAuth token
	 * @param secretToken oAuth secret token
	 */
	public void setToken(String token, String secretToken){
		mOauthToken=token;
		mOauthTokenSecret=secretToken;
	}

	/**
	 * Generate the oAuth Base Parameter
	 * @param isPostRequest True if the request is a post request
	 * @param url URL of the request
	 * @param nonce nonce
	 * @param timestamp timestamp
	 * @param oAuthParameters oauth parameters
	 * @param parameters http parameters
	 * @return authorization header
	 */
	private String getBaseString(boolean isPostRequest, String url, String nonce, String timestamp, String[] oAuthParameters, String[] parameters){
		ArrayList<String> arrBaseItems = new ArrayList<>();
		StringBuilder sBaseString = new StringBuilder(isPostRequest?"POST&":"GET&");
		if(oAuthParameters != null){
			for(String param : oAuthParameters)
				arrBaseItems.add(StringTools.UrlEncode(param));
		}
		if(parameters != null){
			for(String param : parameters)
				arrBaseItems.add(StringTools.UrlEncode(param));
		}
		if(url.contains("?")){
			for(String param : url.substring(url.indexOf("?") + 1).split("&"))
				arrBaseItems.add(StringTools.UrlEncode(param));
			sBaseString.append(StringTools.UrlEncode(url.substring(0, url.indexOf("?")))).append("&");
		}else
			sBaseString.append(StringTools.UrlEncode(url)).append("&");
		arrBaseItems.add("oauth_consumer_key%3D" + StringTools.UrlEncode(mApiKey));
		arrBaseItems.add("oauth_nonce%3D" + StringTools.UrlEncode(nonce));
		arrBaseItems.add("oauth_signature_method%3DHMAC-SHA1");
		arrBaseItems.add("oauth_timestamp%3D" + timestamp);
		arrBaseItems.add("oauth_version%3D" + "1.0");
		if(mOauthToken != null)
			arrBaseItems.add("oauth_token%3D" + mOauthToken);

		Collections.sort(arrBaseItems);

		sBaseString.append(arrBaseItems.remove(0));
		for(String param : arrBaseItems)
			sBaseString.append("%26").append(param);
		return sBaseString.toString();
	}

	/**
	 * Get Authorization header value to be used in an oAuth request
	 * @param isPostRequest True if the request is a post request
	 * @param url URL of the request
	 * @param oAuthParameters oauth parameters
	 * @param parameters Parameters of the request
	 * @return value of Authorization header
	 */
	public String getHeader(boolean isPostRequest, String url, String[] oAuthParameters, String[] parameters){
		ArrayList<String> arrBaseItems = new ArrayList<>();
		String sTimestamp = Long.toString(System.currentTimeMillis() / 1000);
		String sOauthNonce = StringTools.getSafeRandomString(32);
		StringBuilder sAuthorization = new StringBuilder("OAuth realm=\"API\", oauth_version=\"1.0\", oauth_signature_method=\"HMAC-SHA1\"");
		if(mOauthToken != null)
			sAuthorization.append(", oauth_token=\"").append(StringTools.UrlEncode(mOauthToken)).append("\"");
		sAuthorization.append(", oauth_consumer_key=\"").append(StringTools.UrlEncode(mApiKey)).append("\"");
		sAuthorization.append(", oauth_timestamp=\"").append(sTimestamp).append("\"");
		sAuthorization.append(", oauth_nonce=\"").append(sOauthNonce).append("\""); // provided that only alphanumeric characters are returned by random string generator
		if(oAuthParameters != null){
			for(String param : oAuthParameters){
				String key = param.substring(0, param.indexOf("="));
				String value = param.substring(key.length()+1);
				sAuthorization.append(", ").append(key).append("=\"").append(value).append("\"");
			}
		}
		sAuthorization.append(", oauth_signature=\"").append(StringTools.UrlEncode(
				Base64.encodeToString(StringTools.HmacSha1(
						getBaseString(isPostRequest, url, sOauthNonce, sTimestamp, oAuthParameters, parameters),
						mSecretApiKey + "&" + (mOauthTokenSecret==null?"":mOauthTokenSecret)
				), Base64.NO_WRAP)
			)).append("\"");
		return sAuthorization.toString();
	}

	/**
	 * Get Authorization header value to be used in an oAuth request
	 * @param isPostRequest True if the request is a post request
	 * @param url URL of the request
	 * @param parameters Parameters of the request
	 * @return value of Authorization header
	 */
	public String getHeader(boolean isPostRequest, String url, String[] parameters){
		return getHeader( isPostRequest, url, null, parameters);
	}

	/**
	 * Get Authorization header value for Request Token URL
	 * @param requestUrl request URL
	 * @param callbackUrl callback URL
	 * @return value of Authorization header
	 */
	public String getRequestTokenHeader(String requestUrl, String callbackUrl){
		return getHeader(true, requestUrl, callbackUrl==null?null:new String[]{"oauth_callback=" + StringTools.UrlEncode(callbackUrl)}, null);
	}

	/**
	 * Get Authorization header value for Authorize URL
	 * @param authorizeUrl Authorize URL
	 * @param pin PIN or Verifier
	 * @return value of Authorization header
	 */
	public String getAuthorizeHeader(String authorizeUrl, String pin){
		return getHeader(true, authorizeUrl, new String[]{"oauth_verifier=" + StringTools.UrlEncode(pin)}, null);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mApiKey);
		dest.writeString(mSecretApiKey);
		dest.writeString(mOauthToken);
		dest.writeString(mOauthTokenSecret);
	}

}
