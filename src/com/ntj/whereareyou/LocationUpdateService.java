package com.ntj.whereareyou;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
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
	private static final int MSG_REPORT_COARSE_LOCATION = 0x0013;

	private static final long STOP_SELF_DELAY = 300000;  // in millisecond, 5min

	private HandlerThread mThread = null;
	private Handler mHandler = null;
	private Object mLock = new Object();
	private long mStartTime = 0;
	private String mReturnAddress = null;
	private int mPrecise = 1;
	private int mTrack = 1;
	private Location mLocation = null;
	private Location mNetworkLocation = null;

	PowerManager.WakeLock mWakelock;

	class MyReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (mHandler != null)
				mHandler.sendEmptyMessage(MSG_STOP_SELF);
		}
	}
	private BroadcastReceiver mReceiver;

	private void doReportStart() {
		if (DEBUG) Log.d(TAG, "doReportStart");
		try {
			JSONObject jdata = new JSONObject();
			jdata.put("action", "report starting")
				.put("priority", 10)
				.put("reporter", Utility.getMyToken(this))
				.put("delay_while_idle", false);
			JSONObject root = new JSONObject();
			root.put("to", mReturnAddress);
			root.put("data", jdata);
			Utility.sendMessageAsync(this, root.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void doReportLocation(Location l) {
		if (DEBUG) Log.d(TAG, "doReportLocation");
		try {
			JSONObject jdata = new JSONObject();
			jdata.put("action", "report location")
				.put("latitude", l.getLatitude())
				.put("longitude", l.getLongitude())
				.put("provider", l.getProvider())
				.put("time", l.getTime())
				.put("reporter", Utility.getMyToken(this))
				.put("priority", 10)
				.put("delay_while_idle", false);
			JSONObject root = new JSONObject();
			root.put("to", mReturnAddress);
			root.put("data", jdata);
			Utility.sendMessageAsync(this, root.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		if (DEBUG) Log.d(TAG, "Message what: " + msg.what);
		int what = msg.what;
		LocationManager locationManager = (LocationManager)
				getSystemService(Context.LOCATION_SERVICE);
		switch (what) {
		case MSG_REPORT_COARSE_LOCATION:
			if (mNetworkLocation != null)
				doReportLocation(mNetworkLocation);
			mNetworkLocation = null;
			return true;
		case MSG_REPORT_LOCATION:
			if (DEBUG) Log.d(TAG, "Track left " + mTrack);
			if (mLocation != null)
				doReportLocation(mLocation);
			if (--mTrack <= 0) {
				locationManager.removeUpdates(this);
				// Let AsyncTask finish
				mHandler.sendEmptyMessageDelayed(MSG_STOP_SELF, 5000);
			}
			return true;
		case MSG_GET_LOCATION:
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 4, this);
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 4, this);
			mHandler.sendEmptyMessageDelayed(MSG_STOP_SELF, STOP_SELF_DELAY);
			return true;
		case MSG_STOP_SELF:
			locationManager.removeUpdates(this);
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
		String provider = location.getProvider();
		if (LocationManager.GPS_PROVIDER.equals(provider)) {
			mLocation = new Location(location);
			if (mStartTime == 0) {
				mStartTime = System.currentTimeMillis();
				Message msg = Message.obtain(mHandler, MSG_REPORT_LOCATION);
				// Improve the precision, collect location after 3 second.
				mHandler.sendMessageDelayed(msg, 3100);
	
				// When get first position, set self stop timer
				mHandler.sendEmptyMessageDelayed(MSG_STOP_SELF, mTrack * mPrecise * 1000 + 5000);
			} else {
				long current = System.currentTimeMillis();
				long elapse = current - mStartTime;
				if (elapse > (mPrecise * 1000)) {
					mStartTime = current;
					Message msg = Message.obtain(mHandler, MSG_REPORT_LOCATION);
					mHandler.removeMessages(MSG_REPORT_LOCATION);
					mHandler.sendMessage(msg);
				} else {
					Message msg = Message.obtain(mHandler, MSG_REPORT_LOCATION);
					mHandler.removeMessages(MSG_REPORT_LOCATION);
					mHandler.sendMessageDelayed(msg, mPrecise * 1000 - elapse);
				}
			}
		} else if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
			// only update network once
			//if (mNetworkLocation == null) {
				mNetworkLocation = new Location(location);
				Message msg = Message.obtain(mHandler, MSG_REPORT_COARSE_LOCATION);
				mHandler.sendMessage(msg);
			//}
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
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

		if (DEBUG) Log.d(TAG, "Service has received start id " + startId + ": " + intent);
		mReturnAddress = intent.getStringExtra("return_address");
		if (mReturnAddress == null || mReturnAddress.isEmpty()) {
			Log.e(TAG, "Lack return address");
			return START_NOT_STICKY;
		}

		mPrecise = intent.getIntExtra("precise", 10);
		mTrack = intent.getIntExtra("track", 1);

		if (mPrecise <= 0)
			mPrecise = 1;
		if (mPrecise >= 20)
			mPrecise = 20;
		if (mTrack <= 0)
			mTrack = 1;
		else if ((mTrack * mPrecise) > 240)
			mTrack = 240 / mPrecise;
		if (DEBUG) Log.d(TAG, "Precise " + mPrecise + ", Track " + mTrack);

		mReceiver = new MyReceiver();
		IntentFilter filter = new IntentFilter(Utility.ACTION_STOP_BACKGROUND);
		registerReceiver(mReceiver, filter);
		
		// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        synchronized (mLock) {
        	if (mThread != null)
        		return START_NOT_STICKY;
    		mThread = new HandlerThread("H1");
    		mThread.start();
    		mStartTime = 0;
    		if (DEBUG) Log.d(TAG, "thread started");
    		mHandler = new Handler(mThread.getLooper(), this);
    		mHandler.sendEmptyMessage(MSG_GET_LOCATION);

    		// Need keep wake until the location is updated.
    		mWakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WRU");
    		mWakelock.acquire();

    		doReportStart();
        }
        return START_NOT_STICKY;
    }

	@Override
	public void onDestroy() {
		if (DEBUG) Log.d(TAG, "onDestroy()");
		mNetworkLocation = null;
		mLocation = null;
        synchronized (mLock) {
        	if (mReceiver != null)
        		unregisterReceiver(mReceiver);
        	mReceiver = null;
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
