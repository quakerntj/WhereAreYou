package com.ntj.whereareyou;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;

import javax.net.ssl.HttpsURLConnection;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.RingtoneManager;
import android.os.AsyncTask;
import android.text.format.DateFormat;
import android.util.Log;

public class Utility {
	public static final String TAG = "WRU";
	public static final String SP_WRU = "WRU";
	public static final String SP_WRU_ME = "ME";
	public static final String SP_PRECISE_CHECKED = "precise";
	public static final String SP_TRACK_CHECKED = "track";
	public static final String SP_MY_TOKEN = "my_token";
	public static final String SP_TARGET_NUMBERS = "target_numbers";
	public static final String SP_TARGET_TOKEN = "target_token_";
	public static final String SP_TARGET_NAME = "target_name_";
	public static final String SP_TARGET_ALLOW = "target_allow_";
	public static final long TIME_EXPIRE = 3 * 60 * 60 * 1000;  // Request expired after 3 hours
	public static final String ACTION_STOP_BACKGROUND = "com.ntj.whereareyou.STOP_BACKGROUND";

	abstract static class AsyncTaskCallback {
		abstract void onPostExecute();
	}

	public static void sendMessageAsync(Context context, String data) {
		sendMessageAsync(context, data, null);
	}
	public static void sendMessageAsync(Context context, String data, final AsyncTaskCallback callback) {
		new AsyncTask<Object, Integer, Void>() {
			@Override
			protected Void doInBackground(Object... params) {
				sendMessage((Context) params[0], (String) params[1]);
				return null;
			}
			@Override
			protected void onPostExecute(Void result) {
				if (callback != null)
					callback.onPostExecute();
			}
		}.execute(context, data, null);
	}

	public static void sendMessage(Context context, String data) {
		try {
			URL url = new URL("https://gcm-http.googleapis.com/gcm/send");
			HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setRequestProperty("Content-Type", "application/json");
			urlConnection.setRequestProperty("Authorization", "key=" + context.getString(R.string.api_key));
			urlConnection.setRequestMethod("POST");

			//Write 
			OutputStream os = urlConnection.getOutputStream();
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
			writer.write(data);
			writer.close();
			os.close();

			//Read
			BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(),"UTF-8"));

			String line = null; 
			StringBuilder sb = new StringBuilder();

			while ((line = br.readLine()) != null) {
				sb.append(line);
			}

			br.close();
			String result = sb.toString();
			Log.d(TAG, result);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String getMyToken(Context context) {
		SharedPreferences sp = context.getSharedPreferences(SP_WRU_ME, Context.MODE_PRIVATE);
		String token = sp.getString(SP_MY_TOKEN, "");
		return token;
	}

	public static void putMyToken(Context context, String token) {
		SharedPreferences sp = context.getSharedPreferences(SP_WRU_ME, Context.MODE_PRIVATE);
		sp.edit().putString(Utility.SP_MY_TOKEN, token).commit();
	}

	public static void updateTarget(Context context, ArrayList<Target> list) {
		SharedPreferences sp = context.getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
		update(sp, list);
	}

	private static void update(SharedPreferences sp, ArrayList<Target> list) {
		final int N = list.size();
		Editor e = sp.edit();
		e.clear();
		e.putInt(SP_TARGET_NUMBERS, N);
		for (int i = 0; i < N; i++) {
			Target p = list.get(i);
			e.putString(SP_TARGET_NAME + i, p.mName);
			e.putString(SP_TARGET_TOKEN + i, p.mToken);
			e.putBoolean(SP_TARGET_ALLOW + i, p.mAllow);
		}
		e.commit();
	}
	
	public static void addTarget(Context context, String newName, String newToken) {
		SharedPreferences sp = context.getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
		ArrayList<Target> list = new ArrayList<Target>();
		final int N = sp.getInt(SP_TARGET_NUMBERS, 0);
		for (int i = 0; i < N; i++) {
			String name = sp.getString(SP_TARGET_NAME + i, "");
			String token = sp.getString(SP_TARGET_TOKEN + i, "");
			boolean allow = sp.getBoolean(SP_TARGET_ALLOW + i, true);
			if (token.isEmpty())
				continue;
			Target p = new Target(name, token, allow);
			list.add(p);
		}
		// Do add here
		Target p = new Target(newName, newToken);
		list.add(p);
		
		update(sp, list);
	}

	public static void removeTarget(Context context, String delToken) {
		if (delToken == null || delToken.isEmpty())
			return;
		SharedPreferences sp = context.getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
		ArrayList<Target> list = new ArrayList<Target>();
		final int N = sp.getInt(SP_TARGET_NUMBERS, 0);
		for (int i = 0; i < N; i++) {
			String name = sp.getString(SP_TARGET_NAME + i, "");
			String token = sp.getString(SP_TARGET_TOKEN + i, "");
			boolean allow = sp.getBoolean(SP_TARGET_ALLOW + i, true);
			if (token.isEmpty())
				continue;
			
			// Do remove here
			if (delToken.equals(token))
				continue;
			Target p = new Target(name, token, allow);
			list.add(p);
		}
		
		update(sp, list);
	}

	public static Target getTargetByToken(Context context, String token) {
		if (token == null)
			return null;

		SharedPreferences sp = context.getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
		final int num = sp.getInt(SP_TARGET_NUMBERS, 0);

		for (int i = 0; i < num; i++) {
			String t = sp.getString(SP_TARGET_TOKEN + i, "");
			if (!token.equals(t))
				continue;
			String name = sp.getString(SP_TARGET_NAME + i, "");
			boolean allow = sp.getBoolean(SP_TARGET_ALLOW + i, true);
			return new Target(name, token, allow);
		}
		return null;
	}

	public static ArrayList<Target> getTargets(Context context) {
		SharedPreferences sp = context.getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
		ArrayList<Target> list = new ArrayList<Target>();
		final int num = sp.getInt(SP_TARGET_NUMBERS, 0);

		for (int i = 0; i < num; i++) {
			String name = sp.getString(SP_TARGET_NAME + i, "");
			String token = sp.getString(SP_TARGET_TOKEN + i, "");
			boolean allow = sp.getBoolean(SP_TARGET_ALLOW + i, true);

			if (token.isEmpty())
				continue;
			Target p = new Target(name, token, allow);
			list.add(p);
		}
		return list;
	}


	public static void onCallNotification(Context context, String text, long time) {
		onCallNotification(context, text, time, null);
	}
	public static void onCallNotification(Context context, String text, long time, PendingIntent pi) {
		long [] pat = {0, 1000, 1000, 300, 200, 1000, 1500};
		new Intent();
		NotificationManager nm = (NotificationManager)
				context.getSystemService(Context.NOTIFICATION_SERVICE);

		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(time);
		java.text.DateFormat df = DateFormat.getTimeFormat(context);
		Builder builder = new Notification.Builder(context)
			.setContentTitle("At " + df.format(cal.getTime()))
	        .setContentText(text)
	        .setSmallIcon(R.drawable.ic_launcher)
	        .setVibrate(pat)
	        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
	        .setContentIntent(pi)
	        .setAutoCancel(true);
		Notification notification = builder.build();

	    nm.notify(0, notification);
	}
}
