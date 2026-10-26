package com.telenav.arp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import net.osmand.plus.R;

/** The silent foreground notification the Telenav services post when they are started as such. */
final class ServiceNotifications {

	private ServiceNotifications() {
	}

	static void startForeground(@NonNull Service service, @NonNull String channelId, @NonNull String channelName) {
		NotificationManager manager = (NotificationManager) service.getSystemService(Service.NOTIFICATION_SERVICE);
		if (manager == null) {
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			manager.createNotificationChannel(new NotificationChannel(channelId, channelName,
					NotificationManager.IMPORTANCE_MIN));
		}
		Notification notification = new NotificationCompat.Builder(service, channelId)
				.setSmallIcon(R.drawable.ic_notification_start_navigation)
				.build();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			// Android 14+ refuses a foreground service without a type (declared in the manifest)
			service.startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		} else {
			service.startForeground(1, notification);
		}
	}
}
