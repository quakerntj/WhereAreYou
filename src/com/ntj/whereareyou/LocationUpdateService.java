package com.ntj.whereareyou;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
	private static final boolean DEBUG = true;

	private static final int MSG_GET_LOCATION = 0x0010;
	private static final int MSG_REPORT_LOCATION = 0x0011;
	private static final int MSG_STOP_SELF = 0x0012;

	private static final long WAIT_LOCATION_AFTER_DELAY = 1000;  // in millisecond
	private static final long STOP_SELF_DELAY = 300000;  // in millisecond, 5min

	private HandlerThread mThread = null;
	private Handler mHandler = null;
	private Object mLock = new Object();
	private long mStartTime = 0;
	private String mReturnAddress = null;
	private int mPrecise = 1;
	private int mTrack = 1;
	private String mCaller = "Target";
	private Location mLocation = null;

	PowerManager.WakeLock mWakelock;
	
	private void doReportStart() {
		if (DEBUG) Log.d(TAG, "doReportLocation");
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
				.put("reporter", Utility.getMyToken(this))
				.put("priority", 10)
				.put("delay_while_idle", false);
			JSONObject root = new JSONObject();
			root.put("to", mReturnAddress);
			root.put("data", jdata);
			Utility.sendMessageAsync(this, root.toString());
		} catch (JSONException e) {
			e.printStackTrace();
			stopSelf();
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		if (DEBUG) Log.d(TAG, "Message what: " + msg.what);
		int what = msg.what;
		LocationManager locationManager = (LocationManager)
				getSystemService(Context.LOCATION_SERVICE);
		switch (what) {
		case MSG_REPORT_LOCATION:
			if (DEBUG) Log.d(TAG, "Track left " + mTrack);
			if (mLocation != null)
				doReportLocation(mLocation);
			if (--mTrack <= 0) {
				locationManager.removeUpdates(this);
				stopSelf();
			}
			return true;
		case MSG_GET_LOCATION:
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 4, this);
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
		if (location == null) {
			Log.d(TAG, "Location is null");
			return;
		}
		if (mStartTime == 0) {
			mStartTime = System.currentTimeMillis();
			Message msg = Message.obtain(mHandler, MSG_REPORT_LOCATION);
			mHandler.sendMessage(msg);
			mHandler.sendEmptyMessageDelayed(MSG_STOP_SELF, mTrack * mPrecise * 1000);
		} else {
			mLocation = new Location(location);
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
			Log.d(TAG, "Lack return address");
			return START_NOT_STICKY;
		}

		mPrecise = intent.getIntExtra("precise", 10);
		mTrack = intent.getIntExtra("track", 1);
		mCaller = intent.getStringExtra("caller");

		if (mPrecise <= 0)
			mPrecise = 1;
		if (mPrecise >= 20)
			mPrecise = 20;
		if (mTrack <= 0)
			mTrack = 1;
		else if ((mTrack * mPrecise) > 240)
			mTrack = 240 / mPrecise;
		if (DEBUG) Log.d(TAG, "Precise " + mPrecise + ", Track " + mTrack);

		// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        synchronized (mLock) {
        	if (mThread != null)
        		return START_STICKY;
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
