package com.soreepeong.darknova.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.soreepeong.darknova.settings.ArtwaveAuthenticator;

/**
 * Created by Soreepeong on 2015-04-28.
 */
public class AuthenticationService extends Service{

	@Override
	public IBinder onBind(Intent intent) {
		ArtwaveAuthenticator authenticator = new ArtwaveAuthenticator(this);
		return authenticator.getIBinder();
	}
}
