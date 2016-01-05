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
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

public class WRUActivity extends Activity {
	private static final String TAG = "WRU";
	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
	private static final int MSG_SHOW_TOKEN = 0x0010;
	private static final String SP_WRU_ME = "ME";
	private static final String SP_WRU = "WRU";
	private static final String SP_MY_TOKEN = "my_token";
	private static final String SP_TARGET_NUMBERS = "target_numbers";
	private static final String SP_TARGET_TOKEN = "target_token_";
	private static final String SP_TARGET_NAME = "target_name_";

	private boolean mTokenOkay = false;

	private ArrayList<LinearLayout> mLinearList = new ArrayList<LinearLayout>();
	private LinearLayout mLinearLayout;
	private LinearLayout mLinearLog;

	private String getMyToken() {
		SharedPreferences sp = getSharedPreferences(SP_WRU_ME, Context.MODE_PRIVATE);
		String token = sp.getString(SP_MY_TOKEN, "");
		return token;
	}

	private void update(ArrayList<Pair<String, String>> list) {
		final int N = list.size();
		SharedPreferences sp = getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
		Editor e = sp.edit();
		e.clear();
		e.putInt(SP_TARGET_NUMBERS, N);
		for (int i = 0; i < N; i++) {
			Pair<String, String> p = list.get(i);
			e.putString(SP_TARGET_NAME + i, p.first);
			e.putString(SP_TARGET_TOKEN + i, p.second);
		}
		e.commit();
	}
	
	private void addTarget(String newName, String newToken) {
		SharedPreferences sp = getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
		ArrayList<Pair<String, String>> list = new ArrayList<Pair<String, String>>();
		final int N = sp.getInt(SP_TARGET_NUMBERS, 0);
		for (int i = 0; i < N; i++) {
			String name = sp.getString(SP_TARGET_NAME + i, "");
			String token = sp.getString(SP_TARGET_TOKEN + i, "");
			if (token.isEmpty())
				continue;
			Pair<String, String> p = new Pair<String, String>(name, token);
			list.add(p);
		}
		// Do add here
		Pair<String, String> p = new Pair<String, String>(newName, newToken);
		list.add(p);
		
		update(list);
	}

	private void removeTarget(String delToken) {
		if (delToken == null || delToken.isEmpty())
			return;
		SharedPreferences sp = getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
		ArrayList<Pair<String, String>> list = new ArrayList<Pair<String, String>>();
		final int N = sp.getInt(SP_TARGET_NUMBERS, 0);
		for (int i = 0; i < N; i++) {
			String name = sp.getString(SP_TARGET_NAME + i, "");
			String token = sp.getString(SP_TARGET_TOKEN + i, "");
			if (token.isEmpty())
				continue;
			
			// Do remove here
			if (delToken.equals(token))
				continue;
			Pair<String, String> p = new Pair<String, String>(name, token);
			list.add(p);
		}
		
		update(list);
	}

	private ArrayList<Pair<String, String>> getTargets() {
		SharedPreferences sp = getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
		ArrayList<Pair<String, String>> list = new ArrayList<Pair<String, String>>();
		final int num = sp.getInt(SP_TARGET_NUMBERS, 0);

		for (int i = 0; i < num; i++) {
			String name = sp.getString(SP_TARGET_NAME + i, "");
			String token = sp.getString(SP_TARGET_TOKEN + i, "");

			if (token.isEmpty())
				continue;
			Pair<String, String> p = new Pair<String, String>(name, token);
			list.add(p);
		}
		return list;
	}

	private void newTargetView() {
		LinearLayout empty = (LinearLayout) getLayoutInflater().inflate(R.layout.target_name_token, null);
		if (empty != null) {
			ImageButton imgbtn = (ImageButton) empty.findViewById(R.id.btnEdit);
			imgbtn.setTag(empty);

			mLinearList.add(empty);
			mLinearLayout.addView(empty);
		}
	}

	private void logText(String text, int action) {
		//TextView txt = new TextView(this);
		//txt.setText(text);
		//mLinearLog.addView(txt);
	}
	
	@SuppressLint("HandlerLeak")
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

				SharedPreferences sp = getSharedPreferences(SP_WRU_ME, Context.MODE_PRIVATE);
				sp.edit().putString(SP_MY_TOKEN, token).commit();
				mTokenOkay = true;
				
				code.setEnabled(true);
				code.setVisibility(View.VISIBLE);
			}
		}
	};

	private static Handler mHandler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wru);
		mHandler = new MyHandler(this);
		Button btn = (Button) findViewById(R.id.btnGetToken);
		btn.setEnabled(false);
		btn.setVisibility(View.GONE);
		mLinearLayout = (LinearLayout) findViewById(R.id.linearlist);
		mLinearLog = (LinearLayout) findViewById(R.id.linearLog);
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
		String subject = "How to use WRU (Where are you)";
		String body = "Dear User,\n\nThis mail will show you how to use this in your desktop or other device.\n" +
				"At first, your token is in the following line:\n\n" + token + "\n\n";
		Uri data = Uri.parse("mailto:?subject=" + subject + "&body=" + body);
		intent.setData(data);
		startActivity(Intent.createChooser(intent, "Choose how to deliver this \"INFORMATION\" mail"));
	}

	public void onGetToken(View v) {
		String token = getMyToken();
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

		String mytoken = getMyToken();
		if (mytoken.isEmpty()) {
			doGetToken();
			Button btn = (Button) findViewById(R.id.btnGetToken);
			btn.setEnabled(true);
			btn.setVisibility(View.VISIBLE);
		}

		ArrayList<Pair<String, String>> list = getTargets();
		if (list.size() > 0) {
			for (Pair<String, String> p : list) {
				LinearLayout set = (LinearLayout) getLayoutInflater().inflate(R.layout.target_name_token, null);
				if (set == null)
					continue;
				String name = p.first;
				String token = p.second;

				EditText edit = (EditText) set.findViewById(R.id.textTarget);
				edit.setText(token);
				edit.setVisibility(View.GONE);
				edit.setEnabled(false);

				Button btn = (Button) set.findViewById(R.id.btnTarget);
				btn.setText(name);
				btn.setVisibility(View.VISIBLE);
				btn.setTag(token);

				ImageButton imgbtn = (ImageButton) set.findViewById(R.id.btnEdit);
				imgbtn.setImageResource(android.R.drawable.ic_input_delete);
				imgbtn.setTag(set);

				mLinearList.add(set);
				mLinearLayout.addView(set);
			}
		}
		newTargetView();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mLinearList.clear();
		mLinearLog.removeAllViews();
		mLinearLayout.removeAllViews();
	}

	private void sendMessage(String target) {
		try {
			URL url = new URL("https://gcm-http.googleapis.com/gcm/send");
			HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setRequestProperty("Content-Type", "application/json");
			urlConnection.setRequestProperty("Authorization", "key=" + getString(R.string.api_key));
			urlConnection.setRequestMethod("POST");

			String data = "";
			{
				JSONObject root = new JSONObject();
				root.put("to", target);
				JSONObject jdata = new JSONObject();
				jdata.put("action", "request location");
				jdata.put("return_address", getMyToken());
				jdata.put("priority", 10);
				jdata.put("delay_while_idle", false);

				root.put("data", jdata);
				data = root.toString();
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
			logText(result, 0);
			Log.d(TAG, result);
		} catch (MalformedURLException e) {
			logText("Can't access server", 0);
			e.printStackTrace();
		} catch (IOException e) {
			logText("IO error", 0);
			e.printStackTrace();
		} catch (JSONException e) {
			logText("Error when put data to json object.", 0);
			e.printStackTrace();
		}
	}
	
	public void onRequestCoordinates(View v) {
		final String token = (String) v.getTag();
		if (token == null || token.isEmpty()) {
			logText("Token is invalid.", 0);
			return;
		}
		new AsyncTask<Void, Integer, String>() {
			@Override
			protected String doInBackground(Void... params) {
				String msg = "";
				sendMessage(token);
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				logText(msg, 0);
				Log.d(TAG, msg);
			}
		}.execute(null, null, null);
	}

	public void onEditTarget(View v) {
		final ImageButton imgbtn = (ImageButton) v;
		final LinearLayout set = (LinearLayout) v.getTag();
		final EditText edit = (EditText) set.findViewById(R.id.textTarget);
		final Button btn = (Button) set.findViewById(R.id.btnTarget);

		if (edit.isEnabled()) {
			final EditText textName = new EditText(this);
			AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("Name it")
				.setMessage("Give the id a name")
				.setView(textName)
				.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						SharedPreferences sp = getSharedPreferences(SP_WRU, Context.MODE_PRIVATE);
						String name = textName.getText().toString();

						String token = edit.getText().toString();
						if (token == null || token.isEmpty() || name == null || name.isEmpty()) {
							logText("Name or Token is invalid.", 0);
							return;
						}

						edit.setVisibility(View.GONE);
						edit.setEnabled(false);

						btn.setText(name);
						btn.setVisibility(View.VISIBLE);
						btn.setTag(token);

						ImageButton imgbtn = (ImageButton) set.findViewById(R.id.btnEdit);
						imgbtn.setImageResource(android.R.drawable.ic_input_delete);
						imgbtn.setTag(set);

						addTarget(name, token);
						newTargetView();
					}}).
				setCancelable(false).create();
			dialog.show();
		} else {
			String name = btn.getText().toString();
			AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("Remove target")
				.setMessage("Are you sure to remove the target: " + name + "?")
				.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						imgbtn.setImageResource(android.R.drawable.ic_input_add);
						String token = edit.getText().toString();
						removeTarget(token);
						mLinearList.remove(set);
						mLinearLayout.removeView(set);
					}})
				.setNegativeButton("Cancel", null).create();
			dialog.show();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
/*		MenuItem debugMode = menu.findItem(R.id.action_debug_mode);
		debugMode.setChecked(mDebugMode);
		MenuItem monitorMode = menu.findItem(R.id.action_monitor_mode);
		monitorMode.setChecked(mMonitorEnabled);
*/		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_get_id) {
			Button btn = (Button) findViewById(R.id.btnGetToken);
			btn.setEnabled(true);
			btn.setVisibility(View.VISIBLE);
			onGetToken(btn);
			return true;
		}
		return false;
	}
}
