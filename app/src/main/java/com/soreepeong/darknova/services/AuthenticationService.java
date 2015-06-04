package com.soreepeong.darknova.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.soreepeong.darknova.settings.DarknovaAuthenticator;

/**
 * Authentication service. Actual things are in {@see ArtwaveAuthenticator}
 */
public class AuthenticationService extends Service{

	@Override
	public IBinder onBind(Intent intent) {
		DarknovaAuthenticator authenticator = new DarknovaAuthenticator(this);
		return authenticator.getIBinder();
	}
}
