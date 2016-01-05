package com.ntj.whereareyou;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.util.Log;
/**
 * Because the MyGcmListenerService is not sticky.  Listening for location update will fail.
 * Thus start this LocationUpdateService for background process.
 * @author quaker
 */
public class LocationUpdateService extends Service implements Handler.Callback, LocationListener {
	private static final String TAG = "WRU";
	private static final boolean DEBUG = false;

	private static final int MSG_GET_LOCATION = 0x0010;
	private static final int MSG_REPORT_LOCATION = 0x0011;
	private static final int MSG_STOP_SELF = 0x0012;

	private static final long WAIT_LOCATION_AFTER_DELAY = 5000;  // in millisecond
	private static final long STOP_SELF_DELAY = 300000;  // in millisecond

	private HandlerThread mThread = null;
	private Handler mHandler = null;
	private Object mLock = new Object();
	private Location mLocation = null;
	private long mStartTime = 0;
	private String mReturnAddress;

	PowerManager.WakeLock mWakelock;
	
	private void sendMessage(String data) {
		if (DEBUG) Log.d(TAG, "sendMessage");
		try {
			URL url = new URL("https://gcm-http.googleapis.com/gcm/send");
			HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setRequestProperty("Content-Type", "application/json");
			urlConnection.setRequestProperty("Authorization", "key=" + getString(R.string.api_key));
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
			if (DEBUG) Log.d(TAG, result);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void doReportLocation(Location l) {
		if (DEBUG) Log.d(TAG, "doReportLocation");
		new AsyncTask<Location, Integer, String>() {
			@Override
			protected String doInBackground(Location... params) {
				String msg = "Done";
				Location l = params[0];
				String data = "";
				if (l != null) {
					try {
						JSONObject root = new JSONObject();
						root.put("to", mReturnAddress);
						JSONObject jdata = new JSONObject();
						jdata.put("action", "report location");
						jdata.put("latitude", l.getLatitude());
						jdata.put("latitude", l.getLongitude());
						jdata.put("priority", 10);
						jdata.put("delay_while_idle", false);
	
						root.put("data", jdata);
						data = root.toString();
					} catch (JSONException e) {
						e.printStackTrace();
						stopSelf();
						return "Fail at JSON";
					}
				}
				sendMessage(data);
				stopSelf();
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				if (DEBUG) Log.d(TAG, msg);
			}
		}.execute(l, null, null);
	}

	@Override
	public boolean handleMessage(Message msg) {
		if (DEBUG) Log.d(TAG, "Message what: " + msg.what);
		int what = msg.what;
		LocationManager mLocationManager = (LocationManager)
				getSystemService(Context.LOCATION_SERVICE);
		switch (what) {
		case MSG_REPORT_LOCATION:
			mLocationManager.removeUpdates(this);
			doReportLocation(mLocation);
			return true;
		case MSG_GET_LOCATION:
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 4, this);
			mHandler.sendEmptyMessageDelayed(MSG_STOP_SELF, STOP_SELF_DELAY);
			return true;
		case MSG_STOP_SELF:
			mLocationManager.removeUpdates(this);
	        synchronized (mLock) {
	    		mHandler = null;
	    		if (mThread != null)
	    			mThread.quit();
				mThread = null;
	        }
			stopSelf();
			return true;
		}
		return false;
	}

	@Override
	public void onLocationChanged(Location location) {
		if (DEBUG) Log.d(TAG, location.toString());
		mLocation = location;
		if (mStartTime == 0) {
			// In order to get stable location, wait for a while.
			mStartTime = System.currentTimeMillis();
			Message msg = Message.obtain(mHandler, MSG_REPORT_LOCATION, 
					new Location(location));
			mHandler.sendMessageDelayed(msg, WAIT_LOCATION_AFTER_DELAY);
		}
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private void showNotification() {
		long [] pat = {0, 1000, 1000, 300, 200, 1000, 1500};
		new Intent();
		NotificationManager nm = (NotificationManager)
					getSystemService(Context.NOTIFICATION_SERVICE);

		Calendar cal = Calendar.getInstance();
		java.text.DateFormat df = DateFormat.getTimeFormat(this);
		Notification notification = new Notification.Builder(this).setContentTitle("Target is looking for you")
	        .setContentText("At " + df.format(cal.getTime()))
	        .setSmallIcon(R.drawable.ic_launcher)
	        .setVibrate(pat)
	        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
	        .setAutoCancel(true)
	        .build();

	    nm.notify(0, notification);
	}
	
	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

		if (DEBUG) Log.d(TAG, "Service has received start id " + startId + ": " + intent);
		mReturnAddress = intent.getStringExtra("return_address");
		if (mReturnAddress == null || mReturnAddress.isEmpty())
			return START_NOT_STICKY;

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        synchronized (mLock) {
        	if (mThread != null)
        		return START_STICKY;
    		mThread = new HandlerThread("H1");
    		mThread.start();
    		if (DEBUG) Log.d(TAG, "thread started");
    		mHandler = new Handler(mThread.getLooper(), this);
    		mHandler.sendEmptyMessage(MSG_GET_LOCATION);
    		mStartTime = 0;

    		// Need keep wake until the location is updated.
    		mWakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WRU");
    		mWakelock.acquire();

    		showNotification();
        }
        return START_STICKY;
    }

	@Override
	public void onDestroy() {
		if (DEBUG) Log.d(TAG, "onDestroy()");
        synchronized (mLock) {
    		mHandler = null;
    		if (mThread != null)
    			mThread.quit();
			mThread = null;
			if (mWakelock != null)
				mWakelock.release();
			mWakelock = null;
        }
	}
}
