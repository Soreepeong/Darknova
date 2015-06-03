package com.soreepeong.darknova.settings;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.soreepeong.darknova.ui.LoginActivity;

/**
 * Created by Soreepeong on 2015-04-28.
 * https://udinic.wordpress.com/2013/04/24/write-your-own-android-authenticator/
 */
public class ArtwaveAuthenticator extends AbstractAccountAuthenticator{
	private Context mContext;

	public ArtwaveAuthenticator(Context context){
		super(context);
		mContext = context;
	}

	@Override
	public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
		return null;
	}

	@Override
	public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options) throws NetworkErrorException {
		final Intent intent = new Intent(mContext, LoginActivity.class);
		intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
		final Bundle bundle = new Bundle();
		bundle.putParcelable(AccountManager.KEY_INTENT, intent);
		return bundle;
	}

	@Override
	public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {
		return null;
	}

	@Override
	public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
		return null;
	}

	@Override
	public String getAuthTokenLabel(String authTokenType) {
		return null;
	}

	@Override
	public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
		return null;
	}

	@Override
	public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
		final Bundle result = new Bundle();
		result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
		return result;
	}
}
