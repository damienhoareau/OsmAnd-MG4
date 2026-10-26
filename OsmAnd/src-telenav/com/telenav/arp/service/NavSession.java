package com.telenav.arp.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.ValueHolder;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.routing.IRouteInformationListener;
import net.osmand.plus.routing.RoutingHelper;

import java.util.UUID;

/**
 * Telenav identifies a navigation by a session id and reports a navigation state (NavState). OsmAnd has
 * neither, so this follows the routing events: the id is created when a navigation starts and dropped
 * when it ends, and the state is the last event seen.
 */
final class NavSession implements IRouteInformationListener {

	/** Values of Telenav's NavStateEvent.NavState that OsmAnd can tell apart. */
	static final int NAV_SESSION_UPDATE = 0;
	static final int NAV_SESSION_STARTED = 1;
	static final int DEVIATION_ENDED = 3;
	static final int DESTINATION_ARRIVED = 9;
	static final int NAV_SESSION_ENDED = 10;
	static final int NEW_ROUTE = 16;
	static final int NONE = 36;

	static final String NO_SESSION = "-1";

	private static NavSession instance;

	private final OsmandApplication app;
	private String sessionId;
	private int state = NONE;

	private NavSession(@NonNull OsmandApplication app) {
		this.app = app;
	}

	@NonNull
	static synchronized NavSession get(@NonNull OsmandApplication app) {
		if (instance == null) {
			instance = new NavSession(app);
			app.getRoutingHelper().addListener(instance);
		}
		return instance;
	}

	/** A navigation is in progress while OsmAnd guides or is paused (pausing clears its following mode). */
	boolean isActive() {
		RoutingHelper routing = app.getRoutingHelper();
		return routing.isFollowingMode() || routing.isPauseNavigation();
	}

	/** The id of the navigation in progress, or null when the app is not guiding. */
	@Nullable
	synchronized String sessionId() {
		if (!isActive()) {
			sessionId = null;
			return null;
		}
		if (sessionId == null) {
			sessionId = UUID.randomUUID().toString();
		}
		return sessionId;
	}

	@NonNull
	synchronized String sessionIdOrNone() {
		String id = sessionId();
		return id != null ? id : NO_SESSION;
	}

	synchronized int state() {
		if (isActive() && (state == NONE || state == NAV_SESSION_ENDED
				|| state == DESTINATION_ARRIVED || state == NEW_ROUTE)) {
			return NAV_SESSION_UPDATE;
		}
		return state;
	}

	@Override
	public synchronized void newRouteIsCalculated(boolean newRoute, ValueHolder<Boolean> showToast) {
		if (isActive()) {
			if (newRoute) {
				sessionId = UUID.randomUUID().toString();
			}
			state = newRoute ? NAV_SESSION_STARTED : DEVIATION_ENDED;
		} else {
			state = NEW_ROUTE;
		}
	}

	@Override
	public synchronized void routeWasCancelled() {
		sessionId = null;
		state = NAV_SESSION_ENDED;
	}

	@Override
	public synchronized void routeWasFinished() {
		sessionId = null;
		state = DESTINATION_ARRIVED;
	}
}
