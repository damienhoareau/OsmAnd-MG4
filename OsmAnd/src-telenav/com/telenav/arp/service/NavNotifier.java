package com.telenav.arp.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.SpecialPointType;
import net.osmand.data.ValueHolder;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.routing.IRouteInformationListener;
import net.osmand.plus.routing.NextDirectionInfo;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.routing.RouteDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.utils.OsmAndFormatter;
import net.osmand.router.TurnType;
import net.osmand.util.Algorithms;

import java.util.List;

/**
 * Tells the instrument cluster, and whoever listens to Telenav's broadcasts, how the guidance goes: it
 * starts and stops with the navigation and, while it runs, reads OsmAnd's routing once a second and
 * sends what changed (next maneuver, lanes, remaining distance and time, speed limit).
 */
final class NavNotifier implements IRouteInformationListener {

	private static final String TAG = "NavNotifier";
	private static final long PERIOD_MS = 1000;
	/** Lanes are shown for the next maneuver only when it is this close. */
	private static final int LANES_DISTANCE = 800;
	/** Stands for "the destination" where there is no OsmAnd direction to number. */
	private static final int DESTINATION_ID = -2;

	private static NavNotifier instance;

	private final OsmandApplication app;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final NavBroadcasts broadcasts;
	private final SaicCluster cluster;
	private boolean running;

	private int maneuverId = -1;
	private int icon = Integer.MIN_VALUE;
	private int distance = Integer.MIN_VALUE;
	private String direction = "";
	private String street = "";
	private String lanes = "";
	private int speedLimit = Integer.MIN_VALUE;
	private int remaining = Integer.MIN_VALUE;
	private int minutes = Integer.MIN_VALUE;
	/** Whether the adapter was last told there is a home or a work address (null: not told since it connected). */
	private Boolean homeSent;
	private Boolean workSent;

	/** OsmAnd gives no event when the guidance starts (the route is calculated before), so this keeps watching. */
	private final Runnable tick = new Runnable() {
		@Override
		public void run() {
			try {
				evaluate();
			} catch (RuntimeException e) {
				Log.w(TAG, "update failed", e);
			} finally {
				handler.postDelayed(this, PERIOD_MS);
			}
		}
	};

	private NavNotifier(@NonNull OsmandApplication app) {
		this.app = app;
		this.broadcasts = new NavBroadcasts(app);
		this.cluster = new SaicCluster(app, new MapNotificationListener(app), this::onClusterConnected);
	}

	/** The cluster service starts empty: send it the whole state. */
	private void onClusterConnected() {
		homeSent = null;
		workSent = null;
		syncAddresses();
		if (running) {
			resetSent();
			cluster.navigating(true);
			update();
		}
	}

	static synchronized void start(@NonNull OsmandApplication app) {
		if (instance == null) {
			instance = new NavNotifier(app);
			app.getRoutingHelper().addListener(instance);
			instance.cluster.connect();
			instance.handler.post(instance.tick);
		}
	}

	@Override
	public void newRouteIsCalculated(boolean newRoute, ValueHolder<Boolean> showToast) {
		handler.post(this::evaluate);
	}

	@Override
	public void routeWasCancelled() {
		handler.post(this::evaluate);
	}

	@Override
	public void routeWasFinished() {
		handler.post(this::evaluate);
	}

	/** Starts, updates or stops the notifications according to whether OsmAnd is guiding. */
	private void evaluate() {
		syncAddresses();
		if (NavSession.get(app).isActive()) {
			if (!running) {
				begin();
			}
			update();
		} else {
			stop();
		}
	}

	/** Tells the adapter when a home or work address appears or goes (Telenav does it at start and on change). */
	private void syncAddresses() {
		boolean home = app.getFavoritesHelper().getSpecialPoint(SpecialPointType.HOME) != null;
		boolean work = app.getFavoritesHelper().getSpecialPoint(SpecialPointType.WORK) != null;
		if (homeSent == null || homeSent != home) {
			homeSent = home;
			cluster.homeAddressSet(home);
		}
		if (workSent == null || workSent != work) {
			workSent = work;
			cluster.workAddressSet(work);
		}
	}

	private void begin() {
		Log.i(TAG, "guidance started");
		running = true;
		resetSent();
		broadcasts.navStatus(NavBroadcasts.NAV_START);
		cluster.navigating(true);
	}

	private void stop() {
		if (!running) {
			return;
		}
		Log.i(TAG, "guidance ended");
		running = false;
		broadcasts.maneuver(NavBroadcasts.MANEUVER_END, null, null);
		broadcasts.navStatus(NavBroadcasts.NAV_STOP);
		// Telenav ends with the last maneuver at distance 0 and every figure back to 0
		cluster.maneuver(icon == Integer.MIN_VALUE ? -1 : icon, 0, direction);
		cluster.remainingDistance(0);
		cluster.remainingMinutes(0);
		cluster.redLights(0);
		cluster.laneInfo("");
		cluster.navigating(false);
		resetSent();
	}

	private void resetSent() {
		maneuverId = -1;
		icon = Integer.MIN_VALUE;
		distance = Integer.MIN_VALUE;
		direction = "";
		street = "";
		lanes = "";
		speedLimit = Integer.MIN_VALUE;
		remaining = Integer.MIN_VALUE;
		minutes = Integer.MIN_VALUE;
	}

	private void update() {
		RoutingHelper routing = app.getRoutingHelper();
		if (!routing.isRouteCalculated() || !routing.isFollowingMode()) {
			return;
		}
		updateManeuver(routing);
		updateTotals(routing);

		float limit = routing.getCurrentMaxSpeed();
		int kph = limit > 0 ? Math.round(limit * 3.6f) : -1;
		if (kph != speedLimit) {
			speedLimit = kph;
			cluster.speedLimit(kph);
		}
	}

	private void updateTotals(@NonNull RoutingHelper routing) {
		int left = routing.getLeftDistance();
		if (left != remaining && left >= 0) {
			remaining = left;
			cluster.remainingDistance(inUnit(left));
		}
		int leftMinutes = (routing.getLeftTime() + 59) / 60;
		if (leftMinutes != minutes && leftMinutes >= 0) {
			minutes = leftMinutes;
			cluster.remainingMinutes(leftMinutes);
		}
	}

	private void updateManeuver(@NonNull RoutingHelper routing) {
		RouteCalculationResult route = routing.getRoute();
		NextDirectionInfo next = routing.getNextRouteDirectionInfo(new NextDirectionInfo(), false);

		int id;
		int newIcon;
		int meters;
		String newStreet;
		TurnType turn = null;
		if (next == null || next.directionInfo == null) {
			// OsmAnd has no direction left on the last stretch: only the destination remains
			id = DESTINATION_ID;
			newIcon = ManeuverIcons.DESTINATION_AHEAD;
			meters = Math.max(0, routing.getLeftDistance());
			newStreet = "";
		} else {
			RouteDirectionInfo info = next.directionInfo;
			List<RouteDirectionInfo> all = route.getImmutableAllDirections();
			id = all.indexOf(info);
			boolean last = id == all.size() - 1;
			turn = info.getTurnType();
			newIcon = iconOf(turn, last && !next.intermediatePoint, next.intermediatePoint, route.getNextIntermediate());
			meters = Math.max(0, next.distanceTo);
			newStreet = streetOf(info);
		}
		if (newIcon < 0) {
			return;
		}
		String newDirection = ManeuverIcons.direction(newIcon);

		boolean newManeuver = id != maneuverId || newIcon != icon;
		boolean distanceChanged = meters != distance;
		boolean streetChanged = !newStreet.equals(street);
		if (!newManeuver && !distanceChanged && !streetChanged) {
			updateLanes(turn, meters);
			return;
		}
		cluster.maneuver(newIcon, inUnit(meters), newDirection);
		if (streetChanged || newManeuver) {
			cluster.roadName(newStreet);
		}
		broadcasts.maneuver(newManeuver ? NavBroadcasts.MANEUVER_START : NavBroadcasts.MANEUVER_UPDATE,
				streetChanged || newManeuver ? newStreet : null,
				distanceChanged || newManeuver ? OsmAndFormatter.getFormattedDistance(meters, app) : null);
		maneuverId = id;
		icon = newIcon;
		distance = meters;
		direction = newDirection;
		street = newStreet;
		updateLanes(turn, meters);
	}

	private void updateLanes(@Nullable TurnType turn, int meters) {
		String newLanes = turn != null && meters <= LANES_DISTANCE ? TelenavBundles.laneCodes(turn) : "";
		if (!newLanes.equals(lanes)) {
			lanes = newLanes;
			cluster.laneInfo(newLanes);
		}
	}

	@NonNull
	private static String streetOf(@NonNull RouteDirectionInfo info) {
		String name = info.getStreetName();
		if (Algorithms.isEmpty(name)) {
			name = info.getRef();
		}
		return name != null ? name : "";
	}

	/** The cluster icon of a maneuver, or -1 when OsmAnd gives no turn to show. */
	private static int iconOf(@Nullable TurnType turn, boolean destination, boolean waypoint, int waypointIndex) {
		if (destination) {
			return ManeuverIcons.DESTINATION_AHEAD;
		}
		if (waypoint) {
			return ManeuverIcons.waypointAhead(waypointIndex);
		}
		if (turn == null) {
			return -1;
		}
		if (turn.isRoundAbout()) {
			return ManeuverIcons.roundabout(turn.getTurnAngle());
		}
		return ManeuverIcons.turn(TelenavBundles.turnType(turn));
	}

	/** Telenav reports distances in feet where the user's units are imperial. */
	private int inUnit(int meters) {
		String system = app.getSettings().METRIC_SYSTEM.getModeValue(app.getRoutingHelper().getAppMode()).name();
		boolean feet = system.equals("MILES_AND_FEET") || system.equals("MILES_AND_YARDS");
		return feet ? Math.round(meters * 3.28084f) : meters;
	}
}
