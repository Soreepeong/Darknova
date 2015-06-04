package com.soreepeong.darknova.services;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.ui.MainActivity;

import java.io.File;

/**
 * Notification broadcast receiver.
 *
 * @author Soreepeong
 */
public class NotificationBroadcastReceiver extends BroadcastReceiver {

	public final static String CLEAR_NOTIFICATIONS = "com.soreepeong.darknova.services.NotificationBroadcastReceiver.CLEAR_NOTIFICATIONS";
	public final static String STOP_STREAMING = "com.soreepeong.darknova.services.NotificationBroadcastReceiver.STOP_STREAMING";
	public final static String KEEP_STREAMING = "com.soreepeong.darknova.services.NotificationBroadcastReceiver.KEEP_STREAMING";

	@Override
	public void onReceive(Context context, Intent intent) {
		IBinder b = peekService(context, new Intent(context, DarknovaService.class));
		NotificationManager mNotification = ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));

		switch (intent.getAction()) {
			case ConnectivityManager.CONNECTIVITY_ACTION: {
				if (b != null) {
					try {
						new Messenger(b).send(Message.obtain(null, DarknovaService.MESSAGE_BREAK_CONNECTION));
					} catch (RemoteException re) {
						re.printStackTrace();
					}
				}
				break;
			}
			case CLEAR_NOTIFICATIONS: {
				mNotification.cancel(R.id.NOTIFICATION_ACTIVITIES);
				if (b != null) {
					try {
						new Messenger(b).send(Message.obtain(null, DarknovaService.MESSAGE_CLEAR_NOTIFICATION));
					} catch (RemoteException re) {
						re.printStackTrace();
					}
				} else {
					new File(context.getCacheDir(), DarknovaService.NOTIFICATION_LIST_FILE).delete();
				}
				break;
			}
			case STOP_STREAMING: {
				if (b != null) {
					try {
						new Messenger(b).send(Message.obtain(null, DarknovaService.MESSAGE_ACTUAL_STREAM_QUIT));
					} catch (RemoteException re) {
						re.printStackTrace();
					}
				} else {
					mNotification.cancel(R.id.NOTIFICATION_STREAM_INDICATOR);
				}
				break;
			}
			case KEEP_STREAMING: {
				if (b != null) {
					try {
						new Messenger(b).send(Message.obtain(null, DarknovaService.MESSAGE_STREAM_QUIT_CANCEL));
					} catch (RemoteException re) {
						re.printStackTrace();
					}
				}
				break;
			}
		}
		if ("MainActivity".equals(intent.getStringExtra("show"))) {
			Intent intentShowMainActivity = new Intent(context, MainActivity.class);
			intentShowMainActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			intentShowMainActivity.putExtras(intent);
			context.startActivity(intentShowMainActivity);
		}
	}
}
