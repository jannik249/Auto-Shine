package com.mine.autoshine;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;

import android.content.ContentResolver;
import android.provider.Settings;
import android.telephony.TelephonyManager;

public class ShineControl implements SensorEventListener {
	private final SensorManager sMgr;
	private final Sensor lightSensor;
	private final MySettings sett;
	private final ContentResolver cResolver;

	private final Context mContext;
	private final TelephonyManager telephonyManager;

	private final Handler delayer = new Handler();

	private final long pause = 1000;

	// Some systems silently stop delivering sensor events to background apps.
	// Re-registering the listener regularly and holding a wake lock while listening works around that.
	private static final long REFRESH_INTERVAL = 20000;
	private static final long WAKE_LOCK_TIMEOUT = 5 * 60 * 1000;
	private final PowerManager.WakeLock wakeLock;

	private final Runnable refresher = new Runnable() {
		@Override
		public void run() {
			if (!onListen)
				return;

			sMgr.unregisterListener(ShineControl.this);
			sMgr.registerListener(ShineControl.this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
			acquireWakeLock();
			delayer.postDelayed(this, REFRESH_INTERVAL);
		}
	};

	private final LuxFilter luxFilter = new LuxFilter();
	private boolean holdCheckPosted = false;

	// fires when the hold time is over and checks whether the light value stayed beyond the limit
	private final Runnable holdCheck = new Runnable() {
		@Override
		public void run() {
			holdCheckPosted = false;
			if (!onListen)
				return;

			handleLux(lux);
		}
	};

	private boolean onListen = false;
	private boolean landscape = false;

	private float lux = 0;
	private int tempBrightness = 0;

	ShineControl(Context context) {
		sett = new MySettings(context);
		cResolver = context.getContentResolver();
		sMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		lightSensor = sMgr.getDefaultSensor(Sensor.TYPE_LIGHT);

		mContext = context;

		PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "autoshine:listen");
		wakeLock.setReferenceCounted(false);
		telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			lux = event.values[0];
			handleLux(lux);
		}
	}

	private boolean shouldSuspend() {
		if (sett.mode == Constants.WORK_MODE_ALWAYS)
			return false;

		if (sett.mode == Constants.WORK_MODE_LANDSCAPE && landscape)
			return false;

		if (sett.mode == Constants.WORK_MODE_PORTRAIT && !landscape)
			return false;

		return true;
	}

	private void scheduleSuspend() {
		if (shouldSuspend())
			delayer.postDelayed(new Runner(), pause);
	}

	public int getState() {
		if (!shouldSuspend())
			if (onListen)
				return Constants.SERVICE_STATE_ACTIVE;
			else
				return Constants.SERVICE_STATE_PAUSED;

		return Constants.SERVICE_STATE_SUSPENDED;
	}

	private class Runner implements Runnable {
		public void run() {
			boolean ringing = false;
			if (mContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
				int state = telephonyManager.getCallState();
				ringing = (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK);
			}

			if (ringing) {
				delayer.postDelayed(new Runner(), pause * 2);
			} else {
				stopListening();
			}

		}
	}

	public void startListening(boolean onRotate) {
		if ((sett.mode == Constants.WORK_MODE_UNLOCK) && !sett.rotate && onRotate)
			return;

		if (!onListen) {
			luxFilter.reset();
			holdCheckPosted = false;
			sMgr.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
			onListen = true;

			acquireWakeLock();
			delayer.removeCallbacks(refresher);
			delayer.postDelayed(refresher, REFRESH_INTERVAL);
		}

		scheduleSuspend();
	}

	public void stopListening() {
		if (onListen)
			sMgr.unregisterListener(this);

		delayer.removeCallbacksAndMessages(null);
		holdCheckPosted = false;
		onListen = false;
		releaseWakeLock();
	}

	private void acquireWakeLock() {
		try {
			wakeLock.acquire(WAKE_LOCK_TIMEOUT);
		} catch (Exception ignored) {
		}
	}

	private void releaseWakeLock() {
		try {
			if (wakeLock.isHeld())
				wakeLock.release();
		} catch (Exception ignored) {
		}
	}

	private void handleLux(float value) {
		long now = SystemClock.elapsedRealtime();

		if (luxFilter.onValue(value, now, sett.luxPercent, sett.holdMs)) {
			setBrightness(value);
			return;
		}

		// the sensor only reports changes, so the end of the hold time has to be checked by a timer
		if (luxFilter.isPending() && !holdCheckPosted) {
			holdCheckPosted = true;
			delayer.postDelayed(holdCheck, luxFilter.remaining(now, sett.holdMs));
		}
	}

	private void setBrightness(float value) {
		int brightness = BrightnessCurve.compute(sett.lux, sett.bright, sett.count, value);

		int currentBrightness = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, 0);
		tempBrightness = brightness;
		if (Math.abs(currentBrightness - brightness) > sett.threshold) {
			Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
		}

	}

	public void reconfigure() {
		stopListening();
		sett.load();
		startListening(false);
	}

	public void setLandscape(boolean land) {
		landscape = land;
	}

	public void onScreenUnlock() {
		Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
				Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

		startListening(false);
	}

	public int getLastSensorValue() {
		return (int) lux;
	}

	public int getSetBrightness() {
		return tempBrightness;
	}

	public boolean getOnListen() {
		return onListen;
	}
}
