package com.ntj.whereareyou;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;

public class MyGcmListenerService extends GcmListenerService {
	private static final String TAG = "WRU";

	private void showLocation(Bundle data) {
		String latStr = data.getString("latitude");
		String lonStr = data.getString("longitude");
		String provider = data.getString("provider");
		String timeStr = data.getString("time");
		
		String name;
		Target caller = Utility.getTargetByToken(this, data.getString("reporter"));
		if (caller == null)
			name = "Your target";
		else
			name = caller.mName;
		String uriBegin = "geo:" + latStr + "," + lonStr;
		String query = latStr + "," + lonStr + "(" + name + ")";
		String encodedQuery = Uri.encode(query);
		String uriString = uriBegin + "?q=" + encodedQuery + "&z=16";

		Uri uri = Uri.parse(uriString);
		Intent intent = new Intent(android.content.Intent.ACTION_VIEW, uri);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		// If coarse location, only update on the notification, wait for click. 
		if (!LocationManager.GPS_PROVIDER.equals(provider)) {
			long time;
			try {
				time = Long.valueOf(timeStr);
			} catch (NumberFormatException e) {
				time = System.currentTimeMillis(); 
			}
		
			PendingIntent pi = PendingIntent.getActivity(this, 1,
					intent, PendingIntent.FLAG_UPDATE_CURRENT);
			Notification n = new Notification.Builder(this).setContentTitle("Coarse Location is")
					.setContentText(latStr + "," + lonStr)
					.setSmallIcon(R.drawable.ic_launcher)
					.setDefaults(Notification.DEFAULT_SOUND)
					.setContentIntent(pi).build();
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			nm.cancel(1);
			nm.notify(1, n);
			return;
		}

		// Remove coarse position
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(1);
		startActivity(intent);
	}

	public void onMessageReceived(String from, Bundle data) {
		Log.d(TAG, "onMessageReceived(" + from + ")");
		String action = data.getString("action");
		if (action == null)
			return;
		Log.d(TAG, "action: " + action);

		if (action.equals("request location")) {
			String returnAddr = data.getString("return_address");
			if (returnAddr == null || returnAddr.isEmpty()) {
				Log.e(TAG, "Lack return address");
				return;
			}
			long time = Long.valueOf(data.getString("time"));
			long current = System.currentTimeMillis();

			Target caller = Utility.getTargetByToken(this, returnAddr);
			if (caller == null) {
				PendingIntent pi = PendingIntent.getActivity(this, 0,
						new Intent().setClass(this, WRUActivity.class)
							.putExtra("newtoken", returnAddr),
						PendingIntent.FLAG_UPDATE_CURRENT);
				String text = "An Stranger is looking for you.";
				Utility.onCallNotification(this, text, time, pi);
				return;
			}

			if (!caller.mAllow) {
				String text = caller.mName + "'s request is blocked.";
				Utility.onCallNotification(this, text, time);
				return;
			}

			if ((current - time) > Utility.TIME_EXPIRE) {
				String text = caller.mName + "'s request is expired.";
				Utility.onCallNotification(this, text, time);
				return;
			}

			Log.d(TAG, "start service");
			int precise, track;
			try {
				precise = Integer.valueOf(data.getString("precise"));
				track = Integer.valueOf(data.getString("track"));
			} catch (NumberFormatException e) {
				precise = 1;
				track = 1;
			}

			Intent intent = new Intent(this, LocationUpdateService.class);
			intent.putExtra("return_address", returnAddr)
				.putExtra("precise", precise)
				.putExtra("track", track)
				.putExtra("caller", caller.mName)
				.putExtra("time", time);
			startService(intent);
			PendingIntent pi = PendingIntent.getActivity(this, 0,
					new Intent().setClass(this, WRUActivity.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
			String text = caller.mName + " is looking for you.";
			Utility.onCallNotification(this, text, time, pi);
		} else if (action.equals("report location")) {
			showLocation(data);
		} else if (action.equals("report fail")) {
			Notification.Builder builder = new Notification.Builder(this);
			Notification n = builder.setContentTitle("Fail to locate")
					.setSmallIcon(R.drawable.ic_launcher)
					.setAutoCancel(true)
					.setContentText("Target didn't have GPS signal").build();
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			nm.cancel(0);
			nm.notify(0, n);
		} else if (action.equals("report starting")) {
			Notification.Builder builder = new Notification.Builder(this);
			String token = data.getString("caller");
			Target caller = Utility.getTargetByToken(this, token);
			String name;
			if (caller == null)
				name = "Target";
			else
				name = caller.mName;
			Notification n = builder.setContentTitle("Your request has been delivered")
					.setSmallIcon(R.drawable.ic_launcher)
					.setDefaults(Notification.DEFAULT_SOUND)
					.setAutoCancel(true)
					.setContentText(name + " is locating currently.").build();
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			nm.cancel(0);
			nm.notify(0, n);
		}
	}
}