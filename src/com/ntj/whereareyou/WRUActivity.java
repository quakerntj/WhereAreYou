package com.ntj.whereareyou;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

public class WRUActivity extends Activity {
	private static final String TAG = "WRU";
	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
	private static final int MSG_SHOW_TOKEN = 0x0010;
	private static final int MSG_LOAD_HISTORY = 0x0011;
	private static final int MSG_COPY_TO_CLIPBOARD = 0X0012;
	private static final int MSG_UPDATE_HISTORY = 0x0013;

	private boolean mTokenOkay = false;
	private AtomicInteger msgId = new AtomicInteger();
	private GoogleCloudMessaging mGCM;

	private String getToken() {
		SharedPreferences sp = getSharedPreferences("WRU", Context.MODE_PRIVATE);
		String token = sp.getString("token", "");
		if (token.isEmpty()) {
			SharedPreferences dsp = getPreferences(Context.MODE_PRIVATE);
			token = dsp.getString("token", "");
			sp.edit().putString("token", token).commit();
		};
		return token;
	}
	
	private String getTarget() {
		SharedPreferences sp = getSharedPreferences("WRU", Context.MODE_PRIVATE);
		String token = sp.getString("target", "");
		if (token.isEmpty()) {
			SharedPreferences dsp = getPreferences(Context.MODE_PRIVATE);
			token = dsp.getString("target", "");
			sp.edit().putString("target", token).commit();
		};
		return token;
	}

	private class MyHandler extends Handler {
		private WeakReference<WRUActivity> mWeakContext;

		MyHandler(WRUActivity context) {
			super();
			mWeakContext = new WeakReference<WRUActivity>(context);
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			WRUActivity activity = mWeakContext.get();
			if (activity == null)
				return;

			if (msg.what == MSG_SHOW_TOKEN) {
				final String token = (String) msg.obj;
				Button code = (Button) activity.findViewById(R.id.btnGetToken);
				code.setText("Token is:\n\n" + token + 
						"\n\nThis code is already copied to your clipboard." + 
						"  You can paste it in other text input.");

				ClipboardManager cb = (ClipboardManager)
						activity.getSystemService(Context.CLIPBOARD_SERVICE);
				cb.setPrimaryClip(ClipData.newPlainText(
						ClipDescription.MIMETYPE_TEXT_PLAIN, token));

				SharedPreferences sp = getSharedPreferences("WRU", Context.MODE_PRIVATE);
				sp.edit().putString("token", token).commit();
				mTokenOkay = true;
				
				code.setEnabled(true);
			} else if (msg.what == MSG_COPY_TO_CLIPBOARD) {
			} else if (msg.what == MSG_UPDATE_HISTORY) {
			} else if (msg.what == MSG_LOAD_HISTORY) {
			}
		}
	};

	private static Handler mHandler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wru);
		mHandler = new MyHandler(this);
		mGCM = GoogleCloudMessaging.getInstance(this);
		findViewById(R.id.btnGetToken).setEnabled(false);
	}

	private boolean checkPlayServices() {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, this,
						PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else {
				Log.i(TAG, "This device is not supported.");
				finish();
			}
			return false;
		}
		return true;
	}

	private void emailAccess(String token) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		String subject = "How to use GCMTest";
		String body = "Dear User,\n\nThis mail will show you how to use this in your desktop or other device.\n" +
				"At first, your token is in the following line:\n\n" + token + "\n\n";
		Uri data = Uri.parse("mailto:?subject=" + subject + "&body=" + body);
		intent.setData(data);
		startActivity(Intent.createChooser(intent, "Choose how to deliver this \"INFORMATION\" mail"));
	}

	public void onGetToken(View v) {
		String token = getToken();
		if (mTokenOkay) {
			Log.i(TAG, "onGetToken = " + token);
			emailAccess(token);
		} else {
			if (!token.isEmpty())
				Message.obtain(mHandler, MSG_SHOW_TOKEN, token).sendToTarget();
		}
	}
	
	public void doGetToken() {
		final Activity context = this;
		HandlerThread t = new HandlerThread("GcmMain") {
			@Override
			protected void onLooperPrepared() {
				if (!checkPlayServices())
					return;
				InstanceID iid = InstanceID.getInstance(context);
				// Log.i(TAG, "getId() = " + iid.getId());
				String token = "";
				try {
					token = iid.getToken(getString(R.string.sender_id), GoogleCloudMessaging.INSTANCE_ID_SCOPE);
					Log.i(TAG, "getToken() = " + token);
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
				Message.obtain(mHandler, MSG_SHOW_TOKEN, token).sendToTarget();
				quit();
			}
		};
		t.start();
		mTokenOkay = true;
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		String token = getToken();
		if (token.isEmpty()) {
			doGetToken();
		}
		Button btn = (Button) findViewById(R.id.btnGetToken);
		btn.setEnabled(true);

		String target = getTarget();
		if (!target.isEmpty()) {
			TextView textTarget = (TextView) findViewById(R.id.textTarget);
			ImageButton imgbtn = (ImageButton) findViewById(R.id.btnEdit);
			imgbtn.setImageResource(android.R.drawable.ic_input_delete);
			textTarget.setEnabled(false);
			textTarget.setText(target);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private void sendMessage() {
		try {
			URL url = new URL("https://gcm-http.googleapis.com/gcm/send");
			HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setRequestProperty("Content-Type", "application/json");
			urlConnection.setRequestProperty("Authorization", "key=" + getString(R.string.api_key));
			urlConnection.setRequestMethod("POST");

			String data = "";
			{
				StringBuilder sb = new StringBuilder();
				sb.append("{\"to\":\"").append(getTarget());
				sb.append("\", \"data\":{\"action\":\"request location\"},\"priority\":10,\"delay_while_idle\":false}");
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
			Log.d(TAG, result);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void onRequestCoordinates(View v) {
		final TextView textTarget = (TextView) findViewById(R.id.textTarget);
		new AsyncTask<Void, Integer, String>() {
			@Override
			protected String doInBackground(Void... params) {
				String msg = "";
				sendMessage();
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				Log.d(TAG, msg);
			}
		}.execute(null, null, null);
	}
	
	public void onEditTarget(View v) {
		TextView textTarget = (TextView) findViewById(R.id.textTarget);
		ImageButton imgbtn = (ImageButton) v;
		if (textTarget.isEnabled()) {
			SharedPreferences sp = getSharedPreferences("WRU", Context.MODE_PRIVATE);
			String target = textTarget.getText().toString();
			sp.edit().putString("target", target).commit();
			imgbtn.setImageResource(android.R.drawable.ic_input_delete);
			textTarget.setEnabled(false);
		} else {
			imgbtn.setImageResource(android.R.drawable.ic_input_add);
			textTarget.setEnabled(true);
			textTarget.setText("");
		}
	}
}
