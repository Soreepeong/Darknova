package com.soreepeong.darknova.ui;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.twitter.TwitterEngine;

/**
 * Login activity. Can be called only from AccountAuthenticator.
 *
 * @author Soreepeong
 */
public class LoginActivity extends AccountAuthenticatorActivity implements SwipeRefreshLayout.OnRefreshListener{
	private WebView mViewBrowser;
	private View mViewProgress;
	private TextView mViewProgressText;
	private SwipeRefreshLayout mViewSwiper;
	private TwitterEngine mTwitter;

	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		mTwitter = new TwitterEngine();
		setContentView(R.layout.activity_login);
		mViewBrowser = (WebView) findViewById(R.id.browser);
		mViewProgress = findViewById(R.id.progress);
		mViewProgressText = (TextView) findViewById(R.id.progressText);
		mViewSwiper = (SwipeRefreshLayout) findViewById(R.id.swipeRefresher);
		mViewSwiper.setOnRefreshListener(this);
		mViewBrowser.setWebViewClient(new LoginWebViewClient());
		new TwitterLoginTask().execute();
	}

	@Override
	public void onRefresh() {
		mViewBrowser.reload();
	}

	private void addAccount() {
		final Account account = new Account(mTwitter.getScreenName(), getString(R.string.account_type));
		final AccountManager am = AccountManager.get(this);
		final Bundle info = new Bundle();
		final Bundle accData = new Bundle();
		final Intent res = new Intent();
		info.putLong("user_id", mTwitter.getUserId());
		info.putString("screen_name", mTwitter.getScreenName());
		info.putString("oauth_token", mTwitter.getAuth().getToken());
		info.putString("oauth_token_secret", mTwitter.getAuth().getSecretToken());
		info.putString("consumer_key", mTwitter.getAuth().getApiKey());
		info.putString("consumer_key_secret", mTwitter.getAuth().getSecretApiKey());
		accData.putString(AccountManager.KEY_ACCOUNT_NAME, mTwitter.getScreenName());
		accData.putString(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.account_type));
		accData.putBundle(AccountManager.KEY_USERDATA, info);
		am.addAccountExplicitly(account, null, info);
		am.setUserData(account, "user_id", Long.toString(mTwitter.getUserId()));
		am.setUserData(account, "screen_name", mTwitter.getScreenName());
		am.setUserData(account, "oauth_token", mTwitter.getAuth().getToken());
		am.setUserData(account, "oauth_token_secret", mTwitter.getAuth().getSecretToken());
		am.setUserData(account, "consumer_key", mTwitter.getAuth().getApiKey());
		am.setUserData(account, "consumer_key_secret", mTwitter.getAuth().getSecretApiKey());
		res.putExtras(accData);
		setAccountAuthenticatorResult(accData);
		setResult(RESULT_OK, res);
		finish();
		TwitterEngine.prepare(this, true);
		TwitterEngine.broadcastUserlistChange();
	}

	private class TwitterLoginTask extends AsyncTask<Object, Object, String> {
		@Override
		protected String doInBackground(Object... params) {
			try{
				return mTwitter.startNewLogin("twitterclient://callback");
			}catch(TwitterEngine.RequestException exception){
				return null;
			}
		}

		@Override
		protected void onPostExecute(String url) {
			if(url == null){
				setResult(RESULT_CANCELED);
				finish();
			}
			mViewProgress.setVisibility(View.GONE);
			mViewSwiper.setVisibility(View.VISIBLE);
			mViewBrowser.loadUrl(url);
		}
	}

	private class TwitterVerifyTask extends AsyncTask<String, Object, String>{
		@Override
		protected String doInBackground(String... params) {
			try{
				return mTwitter.authenticate(params[0], params[1]);
			}catch(TwitterEngine.RequestException exception){
				return null;
			}
		}

		@Override
		protected void onPostExecute(String url) {
			if(url == null){
				setResult(RESULT_CANCELED);
				finish();
			}
			addAccount();
		}
	}

	private class LoginWebViewClient extends WebViewClient {
		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			super.onPageStarted(view, url, favicon);
			if(url.startsWith("twitterclient://callback?")){
				String token = null, verifier = null;
				for(String param :  url.substring(url.indexOf("?")+1).split("&")){
					String key = param.substring(0, param.indexOf("="));
					String value = param.substring(key.length() + 1);
					if(key.equals("oauth_token"))
						token = value;
					else if(key.equals("oauth_verifier"))
						verifier = value;
				}
				if(token != null && verifier != null){
					new TwitterVerifyTask().execute(token, verifier);
					mViewSwiper.setVisibility(View.GONE);
					mViewBrowser.stopLoading();
					mViewBrowser.loadUrl("about:blank");
					mViewProgressText.setText(R.string.login_signing);
					mViewProgress.setVisibility(View.VISIBLE);
				}
			}
			mViewSwiper.setRefreshing(true);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			mViewSwiper.setRefreshing(false);
		}
	}
}
