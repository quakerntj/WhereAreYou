package com.ntj.whereareyou;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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

	private boolean mTokenOkay = false;
	private boolean mPreciseChecked = true;
	private boolean mTrackChecked = false;

	private ArrayList<LinearLayout> mLinearList = new ArrayList<LinearLayout>();
	private LinearLayout mLinearLayout;


	private LinearLayout newTargetView() {
		LinearLayout empty = (LinearLayout) getLayoutInflater().inflate(R.layout.target_name_token, null);
		if (empty != null) {
			ImageButton imgbtn = (ImageButton) empty.findViewById(R.id.btnEdit);
			imgbtn.setTag(empty);

			mLinearList.add(empty);
			mLinearLayout.addView(empty);
		}
		return empty;
	}

	private void logText(String text, int action) {
		//TextView txt = new TextView(this);
		//txt.setText(text);
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

				Utility.putMyToken(activity, token);
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
		SharedPreferences sp = getSharedPreferences(Utility.SP_WRU_ME, Context.MODE_PRIVATE);
		mPreciseChecked = sp.getBoolean(Utility.SP_PRECISE_CHECKED, true);
		mTrackChecked = sp.getBoolean(Utility.SP_TRACK_CHECKED, false);
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
		String token = Utility.getMyToken(this);
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

		String mytoken = Utility.getMyToken(this);
		if (mytoken.isEmpty()) {
			doGetToken();
			Button btn = (Button) findViewById(R.id.btnGetToken);
			btn.setEnabled(true);
			btn.setVisibility(View.VISIBLE);
		}

		Intent intent = getIntent();
		Bundle bundle = intent.getExtras();
		String newToken = null;
		if (bundle != null)
			newToken = bundle.getString("newtoken");
		
		ArrayList<Target> list = Utility.getTargets(this);
		if (list.size() > 0) {
			for (Target p : list) {
				LinearLayout set = (LinearLayout) getLayoutInflater().inflate(R.layout.target_name_token, null);
				if (set == null)
					continue;
				String name = p.mName;
				String token = p.mToken;

				EditText edit = (EditText) set.findViewById(R.id.textTarget);
				edit.setText(token);
				edit.setVisibility(View.GONE);
				edit.setEnabled(false);

				Button btn = (Button) set.findViewById(R.id.btnTarget);
				btn.setText(name);
				btn.setVisibility(View.VISIBLE);
				btn.setTag(token);

				CheckBox chk = (CheckBox) set.findViewById(R.id.chkAllow);
				chk.setVisibility(View.VISIBLE);
				chk.setChecked(p.mAllow);
				chk.setTag(token);

				ImageButton imgbtn = (ImageButton) set.findViewById(R.id.btnEdit);
				imgbtn.setImageResource(android.R.drawable.ic_input_delete);
				imgbtn.setTag(set);

				mLinearList.add(set);
				mLinearLayout.addView(set);
			}
		}
		LinearLayout newTargetView = newTargetView();
		if (newToken != null) {
			EditText edit = (EditText) newTargetView.findViewById(R.id.textTarget);
			edit.setText(newToken);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mLinearList.clear();
		mLinearLayout.removeAllViews();
	}

	public void onRequestCoordinates(View v) {
		final String token = (String) v.getTag();
		if (token == null || token.isEmpty()) {
			logText("Token is invalid.", 0);
			return;
		}
		try {
			JSONObject jdata = new JSONObject();
			jdata.put("action", "request location");
			jdata.put("return_address", Utility.getMyToken(this));
			jdata.put("priority", (int) (10));
			jdata.put("precise", (int) (mPreciseChecked ? 10 : 1));
			jdata.put("track", (int) (mTrackChecked ? 10 : 1));
			jdata.put("time", System.currentTimeMillis());
			jdata.put("delay_while_idle", false);

			JSONObject root = new JSONObject();
			root.put("to", token);
			root.put("data", jdata);

			Utility.sendMessageAsync(this, root.toString());
		} catch (JSONException e) {
			logText("Error when put data to json object.", 0);
			e.printStackTrace();
		}
	}

	public void onAllowChanged(View v) {
		String token = (String) v.getTag();
		CheckBox check = (CheckBox) v;
		final boolean allow = check.isChecked();
		ArrayList<Target> list = Utility.getTargets(this);
		for (Target p : list) {
			if (p.mToken.equals(token)) {
				p.mAllow = allow;
			}
		}
		Utility.updateTarget(this, list);
	}

	public void onEditTarget(View v) {
		final ImageButton imgbtn = (ImageButton) v;
		final LinearLayout set = (LinearLayout) v.getParent();
		final EditText edit = (EditText) set.findViewById(R.id.textTarget);
		final Button btn = (Button) set.findViewById(R.id.btnTarget);
		final CheckBox check = (CheckBox) set.findViewById(R.id.chkAllow);

		if (edit.isEnabled()) {
			final EditText textName = new EditText(this);
			AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("Name it")
				.setMessage("Give the id a name")
				.setView(textName)
				.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
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

						check.setVisibility(View.VISIBLE);
						check.setTag(token);

						ImageButton imgbtn = (ImageButton) set.findViewById(R.id.btnEdit);
						imgbtn.setImageResource(android.R.drawable.ic_input_delete);

						Utility.addTarget(getApplicationContext(), name, token);
						newTargetView();
					}}).create();
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
						Utility.removeTarget(getApplicationContext(), token);
						mLinearList.remove(set);
						mLinearLayout.removeView(set);
					}})
				.setNegativeButton("Cancel", null).create();
			dialog.show();
		}
	}

	public void onStopBackground(View v) {
		Intent intent = new Intent(Utility.ACTION_STOP_BACKGROUND);
		sendBroadcast(intent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem preciseCheck = menu.findItem(R.id.action_check_precise);
		preciseCheck.setChecked(mPreciseChecked);
		MenuItem trackCheck = menu.findItem(R.id.action_check_track);
		trackCheck.setChecked(mTrackChecked);
		return true;
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
		} else if (id == R.id.action_check_precise) {
			mPreciseChecked = !item.isChecked();
			item.setChecked(mPreciseChecked);
			SharedPreferences sp = getSharedPreferences(Utility.SP_WRU_ME, Context.MODE_PRIVATE);
			sp.edit().putBoolean(Utility.SP_PRECISE_CHECKED, mPreciseChecked).commit();
			return true;
		} else if (id == R.id.action_check_track) {
			mTrackChecked = !item.isChecked();
			item.setChecked(mTrackChecked);
			SharedPreferences sp = getSharedPreferences(Utility.SP_WRU_ME, Context.MODE_PRIVATE);
			sp.edit().putBoolean(Utility.SP_TRACK_CHECKED, mTrackChecked).commit();
			return true;
		}
		return false;
	}
}
