package com.mine.autoshine;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class QTileService extends TileService {
	private static final long REFRESH_MS = 1000;

	private ShineService sService;
	private boolean isBound = false;

	private final Handler handler = new Handler(Looper.getMainLooper());

	// keeps the tile up to date while the quick panel is open
	private final Runnable refresher = new Runnable() {
		@Override
		public void run() {
			refreshTile();
			handler.postDelayed(this, REFRESH_MS);
		}
	};

	@Override
	public void onStartListening() {
		super.onStartListening();

		if (!isBound)
			bindService(new Intent(this, ShineService.class), connection, 0);

		refreshTile();
		handler.removeCallbacks(refresher);
		handler.postDelayed(refresher, REFRESH_MS);
	}

	@Override
	public void onStopListening() {
		super.onStopListening();
		handler.removeCallbacks(refresher);

		if (isBound)
			unbindService(connection);

		isBound = false;
		sService = null;
	}

	@Override
	public void onClick() {
		super.onClick();

		if (isBound && sService != null) {
			sService.toggleEnabled();
			refreshTile();
		} else {
			// service is not running: start it (a tap on the tile is a user action)
			try {
				Intent intent = new Intent(this, ShineService.class);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
					startForegroundService(intent);
				else
					startService(intent);
			} catch (Exception ignored) {
			}
		}
	}

	private final ServiceConnection connection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			ShineService.LocalBinder binder = (ShineService.LocalBinder) service;
			sService = binder.getService();
			isBound = true;
			refreshTile();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			sService = null;
			isBound = false;
			refreshTile();
		}
	};

	private void refreshTile() {
		Tile tile = getQsTile();
		if (tile == null)
			return;

		if (!isBound || sService == null) {
			tile.setState(Tile.STATE_UNAVAILABLE);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
				tile.setSubtitle(getString(R.string.tile_not_running));
		} else {
			boolean on = sService.isEnabled();
			tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				if (on) {
					int current = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 0);
					tile.setSubtitle(getString(R.string.tile_on, current));
				} else {
					tile.setSubtitle(getString(R.string.tile_off));
				}
			}
		}

		tile.updateTile();
	}
}
