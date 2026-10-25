package com.mine.autoshine;

import android.content.Context;
import android.content.SharedPreferences;

public class MySettings {
	public static final int MIN_POINTS = 2;
	public static final int MAX_POINTS = 30;

	// default curve: 10 points, anchored on the original four (20/31, 200/63, 2000/127, 20000/255)
	private static final int[] DEFAULT_LUX = {20, 50, 100, 200, 400, 800, 2000, 4000, 8000, 20000};
	private static final int[] DEFAULT_BRIGHT = {31, 41, 51, 63, 77, 95, 127, 155, 195, 255};

	private final Context context;
	private SharedPreferences sharedPref;

	public int count;
	public final int[] lux = new int[MAX_POINTS];
	public final int[] bright = new int[MAX_POINTS];

	public int threshold;
	public int luxPercent;
	public int holdMs;
	public int mode;
	public boolean rotate;

	MySettings(Context context) {
		this.context = context;
		load();
	}

	public void load() {
		sharedPref = context.getSharedPreferences("mine.authoshine", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);

		if (sharedPref.contains("pn")) {
			count = Math.max(MIN_POINTS, Math.min(MAX_POINTS, sharedPref.getInt("pn", DEFAULT_LUX.length)));
			for (int i = 0; i < count; i++) {
				lux[i] = sharedPref.getInt("pl" + i, 0);
				bright[i] = sharedPref.getInt("pb" + i, 0);
			}
		} else {
			count = DEFAULT_LUX.length;
			System.arraycopy(DEFAULT_LUX, 0, lux, 0, count);
			System.arraycopy(DEFAULT_BRIGHT, 0, bright, 0, count);
		}

		threshold = sharedPref.getInt("threshold", 0);
		luxPercent = sharedPref.getInt("luxPercent", 20);
		holdMs = sharedPref.getInt("holdMs", 1500);
		mode = sharedPref.getInt("mode", Constants.WORK_MODE_ALWAYS);
		rotate = sharedPref.getBoolean("rotate", true);
	}

	public void save() {
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putInt("pn", count);
		for (int i = 0; i < MAX_POINTS; i++) {
			if (i < count) {
				editor.putInt("pl" + i, lux[i]);
				editor.putInt("pb" + i, bright[i]);
			} else {
				editor.remove("pl" + i);
				editor.remove("pb" + i);
			}
		}
		editor.putInt("threshold", threshold);
		editor.putInt("luxPercent", luxPercent);
		editor.putInt("holdMs", holdMs);
		editor.putInt("mode", mode);
		editor.putBoolean("rotate", rotate);
		editor.commit();
	}
}
