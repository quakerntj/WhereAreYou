package com.ntj.whereareyou;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;

public class MyGcmListenerService extends GcmListenerService {
	private static final String TAG = "WRU";

	private void showLocation(Bundle data) {
		String latStr = data.getString("latitude");
		String lonStr = data.getString("longitude");
		String label = "Here";
		String uriBegin = "geo:" + latStr + "," + lonStr;
		String query = latStr + "," + lonStr + "(" + label + ")";
		String encodedQuery = Uri.encode(query);
		String uriString = uriBegin + "?q=" + encodedQuery + "&z=16";

		Uri uri = Uri.parse(uriString);
		Intent intent = new Intent(android.content.Intent.ACTION_VIEW, uri);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		// TODO use pending intent
		Notification.Builder builder = new Notification.Builder(this);
		Notification n = builder.setContentTitle("Your target is Here")
				.setContentText(uriBegin)
				.build();
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(0);
		nm.notify(0, n);

		startActivity(intent);
	}

	public void onMessageReceived(String from, Bundle data) {
		Log.d(TAG, "onMessageReceived");
		String action = data.getString("action");
		String returnAddr = data.getString("return_address");
		if (action == null || returnAddr == null || returnAddr.isEmpty())
			return;
		Log.d(TAG, "action: " + action);

		if (action.equals("request location")) {
			Log.d(TAG, "start service");
			Intent intent = new Intent(this, LocationUpdateService.class);
			intent.putExtra("return_address", returnAddr);
			startService(intent);
		} else if (action.equals("report location")) {
			showLocation(data);
		} else if (action.equals("report starting")) {
			Notification.Builder builder = new Notification.Builder(this);
			Notification n = builder.setContentTitle("WRU").setContentText("Your target is locating").build();
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			nm.notify(0, n);
		}
	}
}