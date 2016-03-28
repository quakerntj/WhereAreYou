package com.ntj.whereareyou;

import java.util.Calendar;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GcmListenerService;

public class MyGcmListenerService extends GcmListenerService {
	private static final String TAG = "WRU";
	private static final boolean DEBUG = false;

	private void showLocation(Bundle data) {
		String latStr = data.getString("latitude");
		String lonStr = data.getString("longitude");
		String provider = data.getString("provider");
		String timeStr = data.getString("time");

		String name;
		Target caller = Utility.getTargetByToken(this, data.getString("reporter"));
		if (caller == null)
			name = getString(R.string.code_your_target);
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
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(time);
			java.text.DateFormat df = DateFormat.getTimeFormat(this);
			Notification n = new Notification.Builder(this).setContentTitle(getString(R.string.code_coarse))
					.setContentText(latStr + "," + lonStr + "\nAt " + df.format(cal.getTime()))
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
		Utility.checkSelfAllPermissions(this);
		if (DEBUG) Log.d(TAG, "onMessageReceived(" + from + ")");
		String action = data.getString("action");
		if (action == null)
			return;
		if (DEBUG) Log.d(TAG, "action: " + action);


		if (action.equals("request location") || action.equals("request cancel")) {
			if (!Utility.hasPermission(Utility.PERMISSION_COARSE_LOCATION) &&
					!Utility.hasPermission(Utility.PERMISSION_FINE_LOCATION)) {
				Toast.makeText(this, "No permission", Toast.LENGTH_SHORT).show();
				return;
			}
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
				String text = getString(R.string.code_stranger);
				Utility.onCallNotification(this, text, time, pi);
				return;
			}

			if (!caller.mAllow) {
				String text = String.format(getString(R.string.code_blocked), caller.mName);
				Utility.onCallNotification(this, text, time);
				return;
			}

			if ((current - time) > Utility.TIME_EXPIRE) {
				String text = String.format(getString(R.string.code_expired), caller.mName);
				Utility.onCallNotification(this, text, time);
				return;
			}

			if (DEBUG) Log.d(TAG, "start service");
			int precise = 1, track = 1;
			boolean cancel = false;
			if (action.equals("request cancel"))
				cancel = true;
			if (!cancel) {
				try {
					precise = Integer.valueOf(data.getString("precise"));
					track = Integer.valueOf(data.getString("track"));
				} catch (NumberFormatException e) {
					precise = 1;
					track = 1;
				}
			}

			Intent intent = new Intent(this, LocationUpdateService.class);
			intent.putExtra("return_address", returnAddr)
				.putExtra("precise", precise)
				.putExtra("track", track)
				.putExtra("cancel", cancel)
				.putExtra("caller", caller.mName)
				.putExtra("time", time);
			startService(intent);
			PendingIntent pi = PendingIntent.getActivity(this, 0,
					new Intent().setClass(this, WRUActivity.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
			String text = String.format(getString(R.string.code_been_looking), caller.mName);
			Utility.onCallNotification(this, text, time, pi);
		} else if (action.equals("report location")) {
			showLocation(data);
		} else if (action.equals("report finish")) {
			int counts;
			String name;
			Target caller = Utility.getTargetByToken(this, data.getString("reporter"));
			if (caller == null)
				name = getString(R.string.code_your_target);
			else
				name = caller.mName;
 
			try {
				counts = Integer.valueOf(data.getString("report_counts"));
			} catch (NumberFormatException e) {
				counts = 0;
			}
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			Notification.Builder builder = new Notification.Builder(this);
			Notification n;
			if (counts <= 0) {
				n = builder.setContentTitle(String.format(getString(R.string.code_fail_to_locate), name))
					.setSmallIcon(R.drawable.ic_launcher)
					.setAutoCancel(true)
					.setContentText(getString(R.string.code_no_gps_signal)).build();
			} else {
				n = builder.setContentTitle(getString(R.string.code_finish_title))
					.setSmallIcon(R.drawable.ic_launcher)
					.setAutoCancel(true)
					.setContentText(String.format(getString(R.string.code_report_counts), name, counts)).build();
			}
			nm.cancel(0);
			nm.notify(0, n);
		} else if (action.equals("report starting")) {
			Notification.Builder builder = new Notification.Builder(this);
			String token = data.getString("caller");
			Target caller = Utility.getTargetByToken(this, token);
			String name;
			if (caller == null)
				name = getString(R.string.code_your_target);
			else
				name = caller.mName;
			Intent cancelIntent = new Intent(Utility.ACTION_STOP_LOCATE_TAGET);
			cancelIntent.putExtra("to", token);
			PendingIntent cancelPIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, PendingIntent.FLAG_ONE_SHOT);
			Notification n = builder.setContentTitle(getString(R.string.code_request_delivered))
					.setSmallIcon(R.drawable.ic_launcher)
					.setDefaults(Notification.DEFAULT_SOUND)
					.setAutoCancel(true)
					.setDeleteIntent(cancelPIntent)
					.setContentText(String.format(getString(R.string.code_locating), name)).build();
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			nm.cancel(0);
			nm.notify(0, n);
		}
	}
}