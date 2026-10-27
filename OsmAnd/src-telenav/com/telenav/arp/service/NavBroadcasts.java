package com.telenav.arp.service;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The broadcasts of Telenav's ExternalNotificationPerformer. Telenav's own app does not use them on the
 * MG4 (it feeds the cluster through the SAIC service, see SaicCluster), but other apps can listen.
 */
final class NavBroadcasts {

	static final String ACTION_NAV_STATUS = "com.telenav.action.nav.status";
	static final String ACTION_MANEUVER_STATUS = "com.telenav.action.maneuver.status";

	static final String NAV_START = "KEY_NAV_START";
	static final String NAV_STOP = "KEY_NAV_STOP";
	static final String MANEUVER_START = "KEY_MANEUVER_START";
	/** The spelling of the original. */
	static final String MANEUVER_UPDATE = "KEY_MANERVER_UPDATE";
	static final String MANEUVER_END = "KEY_MANEUVER_END";

	private final Context context;

	NavBroadcasts(@NonNull Context context) {
		this.context = context;
	}

	void navStatus(@NonNull String subAction) {
		send(ACTION_NAV_STATUS, subAction, null);
	}

	/** A maneuver update carries only what changed: a null street name or distance is left out. */
	void maneuver(@NonNull String subAction, @Nullable String streetName, @Nullable String distance) {
		Bundle info = null;
		if (streetName != null || distance != null) {
			info = new Bundle();
			if (streetName != null) {
				info.putString("EXT_STRING_STREET_NAME", streetName);
			}
			if (distance != null) {
				info.putString("EXT_STRING_DISTANCE", distance);
			}
		}
		send(ACTION_MANEUVER_STATUS, subAction, info);
	}

	private void send(@NonNull String action, @NonNull String subAction, @Nullable Bundle info) {
		Intent intent = new Intent(action);
		intent.putExtra("SUB_ACTION", subAction);
		if (info != null) {
			intent.putExtra("ACTION_INFO", info);
		}
		context.sendBroadcast(intent);
	}
}
