package com.ntj.whereareyou;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
/**
 * Because the MyGcmListenerService is not sticky.  Listening for location update will fail.
 * Thus start this LocationUpdateService for background process.
 * @author quaker
 */
public class LocationUpdateService extends Service implements Handler.Callback, LocationListener {
	private static final String TAG = "WRU";

	private static final int MSG_GET_LOCATION = 0x0010;
	private static final int MSG_REPORT_LOCATION = 0x0011;
	private static final int MSG_STOP_SELF = 0x0012;

	private static final long WAIT_LOCATION_AFTER_DELAY = 5000;  // in millisecond

	private HandlerThread mThread = null;
	private Handler mHandler = null;
	private Object mLock = new Object();
	private Location mLocation = null;
	private long mStartTime = 0;

	private String getTarget() {
		SharedPreferences sp = getSharedPreferences("WRU", Context.MODE_PRIVATE);
		return sp.getString("target", "");
	}

	private void sendMessage(Location l) {
		//Log.d(TAG, "sendMessage");
		try {
			URL url = new URL("https://gcm-http.googleapis.com/gcm/send");
			HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setRequestProperty("Content-Type", "application/json");
			urlConnection.setRequestProperty("Authorization", "key=" + getString(R.string.api_key));
			urlConnection.setRequestMethod("POST");

			String data = "";
			if (l != null) {
				StringBuilder sb = new StringBuilder("");
				sb.append("{\"to\":\"").append(getTarget());
				sb.append("\", \"data\":{\"action\":\"report location\",");
				sb.append("\"latitude\":\"").append(l.getLatitude()).append("\",");
				sb.append("\"longitude\":\"").append(l.getLongitude()).append("\"}");
				sb.append(",\"priority\":10,\"delay_while_idle\":false}");
				data = sb.toString();
			}

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
			//Log.d(TAG, result);
			stopSelf();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void doReportLocation(Location l) {
		//Log.d(TAG, "doReportLocation");
		new AsyncTask<Location, Integer, String>() {
			@Override
			protected String doInBackground(Location... params) {
				String msg = "";
				sendMessage(params[0]);
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				//Log.d(TAG, msg);
			}
		}.execute(l, null, null);
	}

	@Override
	public boolean handleMessage(Message msg) {
		//Log.d(TAG, "Message what: " + msg.what);
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
			mHandler.sendEmptyMessageDelayed(MSG_STOP_SELF, 360000);
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
		//Log.d(TAG, location.toString());
		mLocation = location;
		if (mStartTime == 0) {
			// In order to get stable location, wait for a while.
			long mStartTime = System.currentTimeMillis();
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

	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		//Log.d(TAG, "Service has received start id " + startId + ": " + intent);

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        synchronized (mLock) {
        	if (mThread != null)
        		return START_STICKY;
    		mThread = new HandlerThread("H1");
    		mThread.start();
    		//Log.d(TAG, "thread started");
    		mHandler = new Handler(mThread.getLooper(), this);
    		mHandler.sendEmptyMessage(MSG_GET_LOCATION);
    		mStartTime = 0;
		}
        return START_STICKY;
    }

	@Override
	public void onDestroy() {
		//Log.d(TAG, "onDestroy()");
        synchronized (mLock) {
    		mHandler = null;
    		if (mThread != null)
    			mThread.quit();
			mThread = null;
        }
	}
}
