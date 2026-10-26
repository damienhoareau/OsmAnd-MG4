package com.telenav.arp.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import net.osmand.plus.activities.MapActivity;

/**
 * Same contract as the NavAppService of the car's Telenav navigation: the SAIC voice assistant
 * starts it with an int extra FOREGROUND (1 = bring the navigation to the front, 0 = just answer)
 * and listens for the SAIC_VR_ACTION_RESPONSE broadcast (STATUS 0 = accepted, 1 = invalid command).
 */
public class NavAppService extends Service {

	private static final String CHANNEL_ID = "com.telenav.app.arp";
	private static final String CHANNEL_NAME = "NavAppService_channel";

	private static final String ACTION_RESPONSE = "SAIC_VR_ACTION_RESPONSE";
	private static final String COMMAND_START_NAV = "SAIC_VR_ACTION_START_NAV";
	private static final String EXTRA_FOREGROUND = "FOREGROUND";

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// the assistant may use startForegroundService(): the notification has to be posted at once
		startForegroundNotification();
		boolean valid = isValidCommand(intent);
		sendResponse(valid);
		if (valid && intent.getIntExtra(EXTRA_FOREGROUND, 0) == 1) {
			launchToForeground();
		}
		stopForeground(true);
		stopSelf(startId);
		return START_NOT_STICKY;
	}

	private boolean isValidCommand(@Nullable Intent intent) {
		if (intent == null || intent.getExtras() == null || !intent.getExtras().containsKey(EXTRA_FOREGROUND)) {
			return false;
		}
		int foreground = intent.getIntExtra(EXTRA_FOREGROUND, -1);
		return foreground == 0 || foreground == 1;
	}

	private void sendResponse(boolean valid) {
		Intent response = new Intent(ACTION_RESPONSE);
		response.putExtra("COMMAND", COMMAND_START_NAV);
		response.putExtra("STATUS", valid ? 0 : 1);
		sendBroadcast(response);
	}

	private void launchToForeground() {
		Intent intent = new Intent(this, MapActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
		startActivity(intent);
	}

	private void startForegroundNotification() {
		ServiceNotifications.startForeground(this, CHANNEL_ID, CHANNEL_NAME);
	}
}
