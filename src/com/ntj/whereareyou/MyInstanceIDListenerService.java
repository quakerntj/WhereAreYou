package com.ntj.whereareyou;

import java.io.IOException;

import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.android.gms.iid.InstanceIDListenerService;

public class MyInstanceIDListenerService extends InstanceIDListenerService {
	private static final String TAG = "WRU ";
	@Override
	public void onTokenRefresh() {
		Log.d(TAG, "onTokenRefresh");
		InstanceID iid = InstanceID.getInstance(this);
		String token;
		try {
			token = iid.getToken(getString(R.string.sender_id), GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
			Log.d(TAG, token);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
