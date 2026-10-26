package com.telenav.arp.service;

import android.app.Service;
import android.content.Intent;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.plus.GeocodingLookupService.AddressLookupRequest;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.Version;
import net.osmand.plus.helpers.TargetPoint;
import net.osmand.plus.helpers.TargetPointsHelper;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.routing.RouteDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.routing.VoiceRouter;
import net.osmand.router.RouteSegmentResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The navigation API of the car's Telenav app, on top of OsmAnd: other apps bind to it (action
 * com.telenav.arp.service.NAVIGATION) to read the guidance state and drive it. Telenav's own service
 * answers some calls with constants (service state, versions, cluster images, time zone): so does this one.
 *
 * Not implemented: the keyword and address search (getPoiList, getPoiDetail, geocode) and the parking
 * cards; they answer like Telenav does when it has nothing (empty list or null).
 */
public class NavigationService extends Service {

	private static final String CHANNEL_ID = "com.telenav.arp.service";
	private static final String CHANNEL_NAME = "NavigationService_channel";

	/** Telenav stops listing the route after this many meters. */
	private static final int MAX_EDGES_LENGTH = 320000;
	private static final long REVERSE_GEOCODE_TIMEOUT_SECONDS = 5;

	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private OsmandApplication app;
	private NavSession session;

	@Override
	public void onCreate() {
		super.onCreate();
		app = (OsmandApplication) getApplication();
		session = NavSession.get(app);
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		ServiceNotifications.startForeground(this, CHANNEL_ID, CHANNEL_NAME);
		return START_NOT_STICKY;
	}

	private final INavigationService.Stub binder = new INavigationService.Stub() {

		@Override
		public int getNavigationStatus() {
			RoutingHelper routing = app.getRoutingHelper();
			if (routing.isPauseNavigation()) {
				return 0;
			}
			return routing.isFollowingMode() ? 1 : -1;
		}

		@Override
		public String resumeNavigationGuidance() {
			return control(() -> app.getRoutingHelper().resumeNavigation());
		}

		@Override
		public String pauseNavigationGuidance() {
			return control(() -> app.getRoutingHelper().pauseNavigation());
		}

		@Override
		public String stopNavigationGuidance() {
			return control(() -> app.stopNavigation());
		}

		@Override
		public String getCurrentNavigationSessionID() {
			return session.sessionIdOrNone();
		}

		@Override
		public Location getDestination() {
			if (session.sessionId() == null) {
				return null;
			}
			TargetPoint destination = app.getTargetPointsHelper().getPointToNavigate();
			return destination != null ? TelenavBundles.location("destination", destination, app) : null;
		}

		@Override
		public Location getStartingPoint() {
			if (session.sessionId() == null) {
				return null;
			}
			LatLon start = null;
			TargetPoint startPoint = app.getTargetPointsHelper().getPointToStart();
			if (startPoint != null) {
				start = startPoint.getLatLon();
			} else {
				RouteCalculationResult route = app.getRoutingHelper().getRoute();
				if (route != null && route.isCalculated() && !route.getImmutableAllLocations().isEmpty()) {
					net.osmand.Location first = route.getImmutableAllLocations().get(0);
					start = new LatLon(first.getLatitude(), first.getLongitude());
				}
			}
			if (start == null) {
				return null;
			}
			Location location = new Location("start");
			location.setLatitude(start.getLatitude());
			location.setLongitude(start.getLongitude());
			return location;
		}

		@Override
		public List<Location> getWaypointsList() {
			if (session.sessionId() == null) {
				return null;
			}
			List<TargetPoint> waypoints = app.getTargetPointsHelper().getIntermediatePointsNavigation();
			if (waypoints == null || waypoints.isEmpty()) {
				return null;
			}
			List<Location> locations = new ArrayList<>(waypoints.size());
			for (int i = 0; i < waypoints.size(); i++) {
				locations.add(TelenavBundles.location("waypoint" + i, waypoints.get(i), app));
			}
			return locations;
		}

		@Override
		public int getCurrentWaypointIndex() {
			if (session.sessionId() == null) {
				return -1;
			}
			RouteCalculationResult route = app.getRoutingHelper().getRoute();
			return route != null && route.isCalculated() ? route.getNextIntermediate() : 0;
		}

		@Override
		public List<Bundle> getGuidanceSegmentList() {
			if (session.sessionId() == null) {
				return null;
			}
			RouteCalculationResult route = app.getRoutingHelper().getRoute();
			if (route == null || !route.isCalculated()) {
				return null;
			}
			List<RouteDirectionInfo> directions = route.getImmutableAllDirections();
			List<Bundle> segments = new ArrayList<>(directions.size());
			for (int i = 0; i < directions.size(); i++) {
				segments.add(TelenavBundles.guidanceSegment(app, i, directions.get(i)));
			}
			return segments;
		}

		@Override
		public int getCurrentGuidanceSegmentIndex() {
			if (session.sessionId() == null) {
				return -1;
			}
			RouteCalculationResult route = app.getRoutingHelper().getRoute();
			RouteDirectionInfo current = route != null && route.isCalculated() ? route.getCurrentDirection() : null;
			return current != null ? route.getImmutableAllDirections().indexOf(current) : -1;
		}

		@Override
		public String setAudioGuidanceMuted(boolean muted) {
			return control(() -> app.getRoutingHelper().getVoiceRouter().setMute(muted));
		}

		@Override
		public boolean isAudioGuidanceMuted() {
			return app.getRoutingHelper().getVoiceRouter().isMute();
		}

		@Override
		public String repeatAudioGuidance() {
			return control(() -> {
				VoiceRouter voice = app.getRoutingHelper().getVoiceRouter();
				voice.announceCurrentDirection(app.getLocationProvider().getLastKnownLocation());
			});
		}

		@Override
		public List<Bundle> getPoiList(String keyword, Location location, List<String> categories, int limit, int radiusKm) {
			return new ArrayList<>();
		}

		@Override
		public Bundle getPoiDetail(String entityId) {
			return null;
		}

		@Override
		public List<Address> geocode(String query, int maxResults) {
			return new ArrayList<>();
		}

		@Override
		public List<Address> reverseGeocode(double latitude, double longitude, int maxResults) {
			List<Address> addresses = new ArrayList<>();
			if (maxResults <= 0) {
				return addresses;
			}
			String text = lookupAddress(new LatLon(latitude, longitude));
			if (text != null && !text.isEmpty()) {
				Address address = new Address(java.util.Locale.getDefault());
				address.setAddressLine(0, text);
				address.setLatitude(latitude);
				address.setLongitude(longitude);
				addresses.add(address);
			}
			return addresses;
		}

		@Override
		public Bundle getClusterMapImage(int width, int height) {
			return TelenavBundles.emptyClusterMapImage();
		}

		@Override
		public Bundle getClusterTurnImage(int width, int height) {
			return TelenavBundles.emptyClusterTurnImage();
		}

		@Override
		public void addWaypointToFront(Location location) {
			if (location == null || session.sessionId() == null) {
				return;
			}
			LatLon point = new LatLon(location.getLatitude(), location.getLongitude());
			PointDescription description = new PointDescription(PointDescription.POINT_TYPE_LOCATION,
					TelenavBundles.label(location));
			mainHandler.post(() -> {
				TargetPointsHelper targets = app.getTargetPointsHelper();
				RouteCalculationResult route = app.getRoutingHelper().getRoute();
				int index = route != null && route.isCalculated() ? route.getNextIntermediate() : 0;
				index = Math.max(0, Math.min(index, targets.getIntermediatePoints().size()));
				// without this OsmAnd keeps the point but routes as if it did not exist
				app.getSettings().USE_INTERMEDIATE_POINTS_NAVIGATION.set(true);
				targets.navigateToPoint(point, true, index, description);
			});
		}

		@Override
		public Bundle getTimeZone() {
			return null;
		}

		@Override
		public String getAppVersion() {
			return Version.getFullVersion(app);
		}

		@Override
		public String getMapVersion() {
			return "";
		}

		@Override
		public String getGMPartNumberOfMapSDCard() {
			return "";
		}

		@Override
		public List<Bundle> getRouteBasicEdges() {
			if (session.sessionId() == null) {
				return null;
			}
			RoutingHelper routing = app.getRoutingHelper();
			RouteCalculationResult route = routing.getRoute();
			List<RouteSegmentResult> segments = route != null && route.isCalculated() ? route.getOriginalRoute() : null;
			if (segments == null || segments.isEmpty()) {
				return null;
			}
			int first = Math.max(0, segments.indexOf(routing.getCurrentSegmentResult()));
			List<Bundle> edges = new ArrayList<>();
			int total = 0;
			for (int i = first; i < segments.size() && total <= MAX_EDGES_LENGTH; i++) {
				Bundle edge = TelenavBundles.basicEdge(segments.get(i));
				total += edge.getInt("length");
				edges.add(edge);
			}
			return edges;
		}

		@Override
		public int getServiceState() {
			return -1;
		}

		@Override
		public String getMapAge() {
			return null;
		}

		@Override
		public List<Bundle> getParkingCards(int maxResults) {
			return null;
		}

		@Override
		public Bundle getNavigationState() {
			return TelenavBundles.navState(session.sessionIdOrNone(), session.state());
		}
	};

	/** Runs a guidance command on the UI thread and returns the session id, or "-1" without a session. */
	@NonNull
	private String control(@NonNull Runnable command) {
		String id = session.sessionId();
		if (id == null) {
			return NavSession.NO_SESSION;
		}
		mainHandler.post(command);
		return id;
	}

	/** OsmAnd's offline reverse geocoder is asynchronous: wait for it, but not for long. */
	@Nullable
	private String lookupAddress(@NonNull LatLon point) {
		String[] result = new String[1];
		CountDownLatch done = new CountDownLatch(1);
		mainHandler.post(() -> app.getGeocodingLookupService().lookupAddress(
				new AddressLookupRequest(point, address -> {
					result[0] = address;
					done.countDown();
				}, null)));
		try {
			done.await(REVERSE_GEOCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return result[0];
	}
}
