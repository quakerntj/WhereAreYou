package com.ntj.whereareyou;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StopTargetReceiver extends BroadcastReceiver {
	private void doStopLocating(Context context, String target) {
		try {
			JSONObject jdata = new JSONObject();
			jdata.put("action", "request cancel")
				.put("time", System.currentTimeMillis())
				.put("return_address", Utility.getMyToken(context))
				.put("priority", 10)
				.put("delay_while_idle", false);
			JSONObject root = new JSONObject();
			root.put("to", target);
			root.put("data", jdata);
			Utility.sendMessageAsync(context, root.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (!Utility.ACTION_STOP_LOCATE_TAGET.equals(intent.getAction()))
			return;
		String target = intent.getStringExtra("to");
		if (target == null) {
			Log.e("WRU", "unknown target");
			return;
		}
		doStopLocating(context, target);
	}
}
