package com.telenav.arp.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.saicmotor.adapterservice.IMapNotificationListener;
import com.saicmotor.adapterservice.IMapService;

/**
 * The link to the SAIC adapter service (the same calls Telenav's SAICMapServiceWrapper makes). It goes both
 * ways: the app tells the instrument cluster how the guidance goes, and registers a listener through which
 * the service sends the voice assistant's commands and the car's state. The link stays up while the app
 * lives: the system binds again by itself if the service restarts. Nothing happens off the car, where the
 * service does not exist.
 */
final class SaicCluster {

	private static final String TAG = "SaicCluster";
	private static final String PACKAGE = "com.saicmotor.adapterservice";
	private static final String SERVICE = "com.saicmotor.adapterservice.services.MapService";

	private final Context context;
	private final Runnable onConnected;
	private final IMapNotificationListener listener;
	@Nullable
	private volatile IMapService service;
	private boolean bound;

	private final ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
			IMapService connected = IMapService.Stub.asInterface(binder);
			service = connected;
			try {
				connected.registerListener(listener);
			} catch (RemoteException | RuntimeException e) {
				Log.w(TAG, "registering the listener failed: " + e);
			}
			onConnected.run();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			service = null;
		}
	};

	/**
	 * @param listener    registered with the service on every connection
	 * @param onConnected called on the main thread when the service is bound, to send the whole state again
	 */
	SaicCluster(@NonNull Context context, @NonNull IMapNotificationListener listener, @NonNull Runnable onConnected) {
		this.context = context;
		this.listener = listener;
		this.onConnected = onConnected;
	}

	void connect() {
		if (bound) {
			return;
		}
		Intent intent = new Intent().setComponent(new ComponentName(PACKAGE, SERVICE));
		try {
			bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
		} catch (SecurityException e) {
			Log.w(TAG, "SAIC map service refused the binding: " + e);
		}
		if (!bound) {
			Log.i(TAG, "SAIC map service not available");
		}
	}

	void navigating(boolean navigating) {
		call("notifyNavStatus", s -> s.notifyNavStatus(navigating ? 1 : 0));
		call("notifyNavigating", s -> s.notifyNavigating(navigating));
	}

	/** The launcher's Navigation card enables its Home button only once the adapter has been told there is a home. */
	void homeAddressSet(boolean set) {
		call("notifySetHomeAddress", s -> s.notifySetHomeAddress(set));
	}

	void workAddressSet(boolean set) {
		call("notifySetWorkAddress", s -> s.notifySetWorkAddress(set));
	}

	void maneuver(int iconId, int distance, @NonNull String direction) {
		call("notifyManeuverInfo", s -> s.notifyManeuverInfo(iconId, distance, direction));
	}

	void roadName(@NonNull String name) {
		call("notifyRoadName", s -> s.notifyRoadName(name));
	}

	void laneInfo(@NonNull String lanes) {
		call("notifyLaneInfo", s -> s.notifyLaneInfo(lanes));
	}

	void speedLimit(int speed) {
		call("notifySpeedLimit", s -> s.notifySpeedLimit(speed));
	}

	void remainingDistance(int distance) {
		call("notifyRemainingDistanceUpdate", s -> s.notifyRemainingDistanceUpdate(distance));
	}

	void remainingMinutes(int minutes) {
		call("notifyETEUpdate", s -> s.notifyETEUpdate(minutes));
	}

	void redLights(int count) {
		call("notifyRedLightCountUpdate", s -> s.notifyRedLightCountUpdate(count));
	}

	private interface Call {
		void run(IMapService service) throws RemoteException;
	}

	private void call(@NonNull String name, @NonNull Call call) {
		IMapService s = service;
		if (s == null) {
			return;
		}
		try {
			call.run(s);
		} catch (RemoteException | RuntimeException e) {
			Log.w(TAG, name + " failed: " + e);
		}
	}
}
