package com.mine.autoshine;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import java.util.Arrays;

public class MainActivity extends Activity {

	private Button btnStart;
	private TextView tvState;

	private LinearLayout llPoints;
	private EditText etThreshold;

	private PowerManager powerMan;
	private MySettings sett;

	private boolean isExpanded = false;
	private boolean isDialogShown = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		powerMan = (PowerManager) getSystemService(POWER_SERVICE);
		sett = new MySettings(this);

		Button btnExpand = findViewById(R.id.btn_expand);
		LinearLayout llHidden = findViewById(R.id.ll_hidden_settings);
		llHidden.setVisibility(View.GONE);
		btnExpand.setOnClickListener(arg0 -> {
			if (isExpanded) {
				llHidden.setVisibility(View.GONE);
				btnExpand.setText(R.string.show_config);
				isExpanded = false;
			} else {
				llHidden.setVisibility(View.VISIBLE);
				btnExpand.setText(R.string.hide_config);
				isExpanded = true;
				refillCollapsibleSettings();
				requestNotificationPermission();
			}
		});

		tvState = findViewById(R.id.tv_service_state);
		btnStart = findViewById(R.id.btn_start_stop);
		btnStart.setOnClickListener(arg0 -> {
			if (isServiceRunning()) {
				killService();
				displayServiceStatus(0);
			} else {
				runService();
				displayServiceStatus(-1);
			}
		});

		Button btnState = findViewById(R.id.btn_get_state);
		btnState.setOnClickListener(arg0 -> {
			if (isServiceRunning()) {
				displayServiceStatus(1);
			} else {
				displayServiceStatus(0);
			}

			sendBroadcastToService(Constants.SERVICE_INTENT_PAYLOAD_PING);
		});

		Button btnRequest = findViewById(R.id.btn_request);
		btnRequest.setOnClickListener(arg0 -> {
			if (!powerMan.isIgnoringBatteryOptimizations(getPackageName())) {
				Intent intent = new Intent();
				intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
				intent.setData(Uri.parse("package:" + getPackageName()));
				startActivity(intent);
			}
		});

		llPoints = findViewById(R.id.ll_points);
		etThreshold = findViewById(R.id.et_threshold);

		refillCollapsibleSettings();

		Button btnAddPoint = findViewById(R.id.btn_add_point);
		btnAddPoint.setOnClickListener(arg0 -> {
			int n = llPoints.getChildCount();
			if (n >= MySettings.MAX_POINTS) {
				Toast.makeText(this, getString(R.string.max_points, MySettings.MAX_POINTS), Toast.LENGTH_SHORT).show();
				return;
			}

			// suggest a continuation of the curve: double the last sensor value, keep the brightness
			int lastLux = 10;
			int lastBright = 127;
			if (n > 0) {
				View last = llPoints.getChildAt(n - 1);
				lastLux = parseOr((EditText) last.findViewById(R.id.et_point_lux), lastLux);
				lastBright = parseOr((EditText) last.findViewById(R.id.et_point_brightness), lastBright);
			}
			addPointRow(Math.min(lastLux * 2, 9999999), Math.min(lastBright, 255));
		});

		Button btnRemovePoint = findViewById(R.id.btn_remove_point);
		btnRemovePoint.setOnClickListener(arg0 -> {
			int n = llPoints.getChildCount();
			if (n <= MySettings.MIN_POINTS) {
				Toast.makeText(this, getString(R.string.min_points, MySettings.MIN_POINTS), Toast.LENGTH_SHORT).show();
				return;
			}
			llPoints.removeViewAt(n - 1);
		});

		Button btnSave = findViewById(R.id.btn_save_settings);
		btnSave.setOnClickListener(arg0 -> saveSettings());

		CheckBox chWRotate = findViewById(R.id.ch_work_rotate);
		chWRotate.setChecked(sett.rotate);
		chWRotate.setEnabled(sett.mode == Constants.WORK_MODE_UNLOCK);
		chWRotate.setOnCheckedChangeListener((compoundButton, isChecked) -> {
			sett.rotate = isChecked;
			sett.save();
			sendBroadcastToService(Constants.SERVICE_INTENT_PAYLOAD_SET);
		});

		RadioButton rbWAlways = findViewById(R.id.rb_work_always);
		RadioButton rbWPortrait = findViewById(R.id.rb_work_portrait);
		RadioButton rbWLandscape = findViewById(R.id.rb_work_landscape);
		RadioButton rbWUnlock = findViewById(R.id.rb_work_unlock);

		if (sett.mode == Constants.WORK_MODE_ALWAYS)
			rbWAlways.setChecked(true);
		if (sett.mode == Constants.WORK_MODE_PORTRAIT)
			rbWPortrait.setChecked(true);
		if (sett.mode == Constants.WORK_MODE_LANDSCAPE)
			rbWLandscape.setChecked(true);
		if (sett.mode == Constants.WORK_MODE_UNLOCK)
			rbWUnlock.setChecked(true);

		RadioGroup rgWorkMode = findViewById(R.id.rg_work_mode);
		rgWorkMode.setOnCheckedChangeListener((radioGroup, checkedId) -> {
			if (checkedId == R.id.rb_work_always)
				sett.mode = Constants.WORK_MODE_ALWAYS;

			if (checkedId == R.id.rb_work_portrait)
				sett.mode = Constants.WORK_MODE_PORTRAIT;

			if (checkedId == R.id.rb_work_landscape)
				sett.mode = Constants.WORK_MODE_LANDSCAPE;

			if (checkedId == R.id.rb_work_unlock)
				sett.mode = Constants.WORK_MODE_UNLOCK;

			sett.save();
			sendBroadcastToService(Constants.SERVICE_INTENT_PAYLOAD_SET);

			if (checkedId == R.id.rb_work_unlock) {
				chWRotate.setEnabled(true);
				requestPhonePermission();
			} else {
				chWRotate.setEnabled(false);
			}
		});

		TextView textView = findViewById(R.id.tv_dontkillmyapp);
		textView.setMovementMethod(LinkMovementMethod.getInstance());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			openAppSettings();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onResume() {
		super.onResume();

		if (checkAndRequestPermissions()) {
			if (isServiceRunning()) {
				displayServiceStatus(1);
			} else {
				runService();
				displayServiceStatus(-1);
			}
		}

		LinearLayout llPower = findViewById(R.id.ll_ignore_battery_request);
		if (powerMan.isIgnoringBatteryOptimizations(getPackageName())) {
			llPower.setVisibility(View.GONE);
		} else {
			llPower.setVisibility(View.VISIBLE);
		}
	}

	private void runService() {
		Intent serviceIntent = new Intent(getBaseContext(), ShineService.class);
		// From Android O (8.0), background services must be started as foreground services
		// to avoid being killed immediately.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(serviceIntent);
		} else {
			startService(serviceIntent);
		}
		Toast.makeText(this, getResources().getString(R.string.starting_service), Toast.LENGTH_SHORT).show();
	}

	private void killService() {
		stopService(new Intent(getBaseContext(), ShineService.class));
	}

	private boolean isServiceRunning() {
		ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

		for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
			if (ShineService.class.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private void sendBroadcastToService(int payload) {
		Intent i = new Intent();
		i.putExtra(Constants.SERVICE_INTENT_EXTRA, payload);
		i.setAction(Constants.SERVICE_INTENT_ACTION);
		sendBroadcast(i);
	}

	private void requestNotificationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
				this.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 123);
			}
		}
	}

	private void requestPhonePermission() {
		if (this.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
			this.requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 123);
		}
	}

	private boolean checkAndRequestPermissions() {
		if (Settings.System.canWrite(this)) {
			return true;
		} else {
			if (isDialogShown)
				return false;

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.permission_request);
			builder.setPositiveButton(R.string.settings, (dialog, id) -> {
				startActivityForResult(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS), 0);
				isDialogShown = false;
			});

			AlertDialog dialog = builder.create();
			dialog.setCanceledOnTouchOutside(false);
			dialog.setCancelable(false);
			dialog.show();
			isDialogShown = true;
			return false;
		}
	}

	private void openAppSettings() {
		Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		intent.setData(Uri.parse("package:" + getPackageName()));
		startActivity(intent);
	}

	private void displayServiceStatus(int status) {
		switch (status) {
			case 0:
				btnStart.setText(R.string.start);
				tvState.setTextColor(getResources().getColor(R.color.red, null));
				tvState.setText(R.string.service_stopped);
				break;
			case 1:
				btnStart.setText(R.string.stop);
				tvState.setTextColor(getResources().getColor(R.color.green, null));
				tvState.setText(R.string.service_running);
				break;
			case -1:
				btnStart.setText(R.string.stop);
				tvState.setTextColor(getResources().getColor(R.color.gray, null));
				tvState.setText(R.string.starting_service);
		}
	}

	private void refillCollapsibleSettings() {
		llPoints.removeAllViews();
		for (int i = 0; i < sett.count; i++) {
			addPointRow(sett.lux[i], sett.bright[i]);
		}

		etThreshold.setText(String.valueOf(sett.threshold));
	}

	private void addPointRow(int lux, int bright) {
		View row = getLayoutInflater().inflate(R.layout.item_point, llPoints, false);
		((TextView) row.findViewById(R.id.tv_point_index))
				.setText(getString(R.string.point_index, llPoints.getChildCount() + 1));
		((EditText) row.findViewById(R.id.et_point_lux)).setText(String.valueOf(lux));
		((EditText) row.findViewById(R.id.et_point_brightness)).setText(String.valueOf(bright));
		llPoints.addView(row);
	}

	private static int parseOr(EditText et, int fallback) {
		try {
			return Integer.parseInt(et.getText().toString().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void saveSettings() {
		int n = llPoints.getChildCount();
		final int[] l = new int[n];
		final int[] b = new int[n];
		int threshold;

		try {
			for (int i = 0; i < n; i++) {
				View row = llPoints.getChildAt(i);
				l[i] = Integer.parseInt(((EditText) row.findViewById(R.id.et_point_lux)).getText().toString().trim());
				b[i] = Integer.parseInt(((EditText) row.findViewById(R.id.et_point_brightness)).getText().toString().trim());
			}
			threshold = Integer.parseInt(etThreshold.getText().toString().trim());
		} catch (NumberFormatException e) {
			Toast.makeText(this, R.string.invalid_number, Toast.LENGTH_LONG).show();
			return;
		}

		if (n < MySettings.MIN_POINTS) {
			Toast.makeText(this, getString(R.string.min_points, MySettings.MIN_POINTS), Toast.LENGTH_LONG).show();
			return;
		}

		for (int i = 0; i < n; i++) {
			if (b[i] < 0 || b[i] > 255) {
				Toast.makeText(this, getString(R.string.brightness_range, i + 1), Toast.LENGTH_LONG).show();
				return;
			}
		}

		// order the points by sensor value, so they can be entered in any order
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++)
			order[i] = i;
		Arrays.sort(order, (x, y) -> Integer.compare(l[x], l[y]));

		int[] sortedLux = new int[n];
		int[] sortedBright = new int[n];
		for (int i = 0; i < n; i++) {
			sortedLux[i] = l[order[i]];
			sortedBright[i] = b[order[i]];
			if (i > 0 && sortedLux[i] == sortedLux[i - 1]) {
				Toast.makeText(this, getString(R.string.duplicate_lux, sortedLux[i]), Toast.LENGTH_LONG).show();
				return;
			}
		}

		sett.count = n;
		System.arraycopy(sortedLux, 0, sett.lux, 0, n);
		System.arraycopy(sortedBright, 0, sett.bright, 0, n);
		sett.threshold = threshold;

		sett.save();
		sendBroadcastToService(Constants.SERVICE_INTENT_PAYLOAD_SET);

		refillCollapsibleSettings();
		Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
	}
}
