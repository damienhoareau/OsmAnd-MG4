package com.telenav.arp.service;

import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.LatLon;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.helpers.TargetPoint;
import net.osmand.plus.routing.RouteDirectionInfo;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.router.GeneralRouter;
import net.osmand.router.RouteSegmentResult;
import net.osmand.router.TurnType;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Builds the Bundles that Telenav's NavigationService returns, key for key (see Telenav's LocationInfo,
 * RoutingOption, GuidanceSegment, LaneInfo, BasicEdge, NavStateEvent and Cluster*Image classes).
 */
final class TelenavBundles {

	private TelenavBundles() {
	}

	// ---- locations ----

	@NonNull
	static Location location(@NonNull String provider, @NonNull TargetPoint point, @NonNull OsmandApplication app) {
		Location location = new Location(provider);
		location.setLatitude(point.getLatitude());
		location.setLongitude(point.getLongitude());

		Bundle info = new Bundle();
		info.putString("locationID", null);
		info.putString("locationLabel", point.getOnlyName());
		info.putString("locationType", "Trip");
		info.putBundle("routingOption", routingOption(app));
		Bundle extras = new Bundle();
		extras.putBundle("locationInfo", info);
		location.setExtras(extras);
		return location;
	}

	@NonNull
	private static Bundle routingOption(@NonNull OsmandApplication app) {
		OsmandSettings settings = app.getSettings();
		ApplicationMode mode = app.getRoutingHelper().getAppMode();
		boolean shortest = settings.getCustomRoutingBooleanProperty(GeneralRouter.USE_SHORTEST_WAY, false).getModeValue(mode);

		Bundle option = new Bundle();
		option.putString("routeStyle", shortest ? "Shortest" : "Fastest");
		option.putBoolean("avoidHOVLane", false);
		option.putBoolean("avoidTollRoad", settings.getCustomRoutingBooleanProperty(GeneralRouter.AVOID_TOLL, false).getModeValue(mode));
		option.putBoolean("avoidHighway", settings.getCustomRoutingBooleanProperty(GeneralRouter.AVOID_MOTORWAY, false).getModeValue(mode));
		option.putBoolean("avoidTraffic", false);
		option.putBoolean("avoidFerry", settings.getCustomRoutingBooleanProperty(GeneralRouter.AVOID_FERRIES, false).getModeValue(mode));
		option.putBoolean("avoidCarTrain", false);
		option.putBoolean("avoidUnpavedRoad", settings.getCustomRoutingBooleanProperty(GeneralRouter.AVOID_UNPAVED, false).getModeValue(mode));
		option.putBoolean("avoidTunnel", false);
		option.putBoolean("avoidUturn", false);
		option.putBoolean("avoidCountryBorders", false);
		option.putBoolean("preferredHighway", false);
		option.putBoolean("enableDoor2door", false);
		return option;
	}

	/** The label of a waypoint sent by a client (LocationInfo.locationLabel), or an empty string. */
	@NonNull
	static String label(@Nullable Location location) {
		Bundle extras = location != null ? location.getExtras() : null;
		Bundle info = extras != null ? extras.getBundle("locationInfo") : null;
		String label = info != null ? info.getString("locationLabel") : null;
		return label != null ? label : "";
	}

	// ---- guidance ----

	@NonNull
	static Bundle guidanceSegment(@NonNull OsmandApplication app, int index, @NonNull RouteDirectionInfo direction) {
		TurnType turn = direction.getTurnType();

		Bundle turnInfo = new Bundle();
		turnInfo.putString("fromRoadType", "");
		turnInfo.putString("toRoadType", "");
		turnInfo.putInt("radiusInMeter", 0);
		turnInfo.putInt("targetNumber", 0);
		turnInfo.putInt("nthTurn", turn != null && turn.isRoundAbout() ? turn.getExitOut() : 0);
		turnInfo.putIntegerArrayList("nthTurnDistances", new ArrayList<>());

		Bundle roadName = new Bundle();
		roadName.putParcelableArrayList("officialNames", names(direction.getStreetName()));
		roadName.putParcelableArrayList("routeNumbers", names(direction.getRef()));
		roadName.putParcelableArrayList("alternateNames", new ArrayList<Bundle>());

		Bundle segment = new Bundle();
		segment.putInt("turnID", index);
		segment.putString("turnType", turn != null ? turnType(turn) : "");
		segment.putBundle("turnInfo", turnInfo);
		segment.putParcelableArrayList("warnings", new ArrayList<Bundle>());
		segment.putBundle("roadName", roadName);
		segment.putString("language", Locale.getDefault().getLanguage());
		segment.putBoolean("isLeftSideDrive", app.getSettings().DRIVING_REGION.get().leftHandDriving);
		segment.putParcelableArrayList("laneInfos", lanes(turn));
		segment.putDouble("lengthInMeters", direction.getDistance());
		return segment;
	}

	@NonNull
	private static ArrayList<Bundle> names(@Nullable String text) {
		ArrayList<Bundle> names = new ArrayList<>();
		if (!Algorithms.isEmpty(text)) {
			Bundle name = new Bundle();
			name.putString("text", text);
			name.putString("phoneme", null);
			names.add(name);
		}
		return names;
	}

	/** OsmAnd's turn types by name in Telenav's TurnType enum. */
	@NonNull
	private static String turnType(@NonNull TurnType turn) {
		switch (turn.getValue()) {
			case TurnType.TL:
				return "TurnLeft";
			case TurnType.TSLL:
				return "TurnSlightLeft";
			case TurnType.TSHL:
				return "TurnHardLeft";
			case TurnType.TR:
				return "TurnRight";
			case TurnType.TSLR:
				return "TurnSlightRight";
			case TurnType.TSHR:
				return "TurnHardRight";
			case TurnType.KL:
				return "StayLeft";
			case TurnType.KR:
				return "StayRight";
			case TurnType.TU:
				return "LeftUTurn";
			case TurnType.TRU:
				return "RightUTurn";
			case TurnType.RNDB:
			case TurnType.RNLB:
				return roundaboutExit(turn.getTurnAngle());
			default:
				return "Continue";
		}
	}

	/** Telenav has no roundabout turn: it is described by the direction of its exit (angle clockwise). */
	@NonNull
	private static String roundaboutExit(float angle) {
		float a = angle % 360;
		if (a > 180) {
			a -= 360;
		} else if (a < -180) {
			a += 360;
		}
		float abs = Math.abs(a);
		if (abs < 30) {
			return "Continue";
		}
		String side = a > 0 ? "Right" : "Left";
		if (abs < 60) {
			return "TurnSlight" + side;
		}
		return abs < 120 ? "Turn" + side : "TurnHard" + side;
	}

	@NonNull
	private static ArrayList<Bundle> lanes(@Nullable TurnType turn) {
		ArrayList<Bundle> lanes = new ArrayList<>();
		int[] values = turn != null ? turn.getLanes() : null;
		if (values != null) {
			for (int lane : values) {
				lanes.add(lane(lane));
			}
		}
		return lanes;
	}

	/** One lane of OsmAnd (bit 0: used by the maneuver, then up to three turn types) as a Telenav LaneInfo. */
	@NonNull
	private static Bundle lane(int lane) {
		int[] turns = {TurnType.getPrimaryTurn(lane), TurnType.getSecondaryTurn(lane), TurnType.getTertiaryTurn(lane)};
		boolean straight = false;
		boolean left = false;
		boolean right = false;
		boolean leftUTurn = false;
		boolean rightUTurn = false;
		String highlighted = "None";
		for (int i = 0; i < turns.length; i++) {
			int turn = turns[i];
			if (i == 0 && turn == 0) {
				turn = TurnType.C; // no primary turn is how OsmAnd writes "straight on"
			}
			String kind = laneKind(turn);
			if (kind == null) {
				continue;
			}
			switch (kind) {
				case "Continue":
					straight = true;
					break;
				case "Left":
					left = true;
					break;
				case "Right":
					right = true;
					break;
				case "LeftUTurn":
					leftUTurn = true;
					break;
				default:
					rightUTurn = true;
			}
			if (i == 0 && (lane & 1) == 1) {
				highlighted = kind;
			}
		}

		String pattern;
		if (straight && left && right) {
			pattern = "ContinueAndLeftAndRight";
		} else if (straight && left) {
			pattern = "ContinueAndLeft";
		} else if (straight && right) {
			pattern = "ContinueAndRight";
		} else if (straight && leftUTurn) {
			pattern = "ContinueAndLeftUTurn";
		} else if (left && right) {
			pattern = "LeftAndRight";
		} else if (left && leftUTurn) {
			pattern = "LeftAndLeftUTurn";
		} else if (right && rightUTurn) {
			pattern = "RightAndRightUTurn";
		} else if (straight) {
			pattern = "Continue";
		} else if (left) {
			pattern = "Left";
		} else if (right) {
			pattern = "Right";
		} else if (leftUTurn) {
			pattern = "LeftUTurn";
		} else if (rightUTurn) {
			pattern = "RightUTurn";
		} else {
			pattern = "None";
		}

		Bundle bundle = new Bundle();
		bundle.putString("lanePattern", pattern);
		bundle.putString("highlightedLane", highlighted);
		return bundle;
	}

	@Nullable
	private static String laneKind(int turn) {
		switch (turn) {
			case TurnType.C:
				return "Continue";
			case TurnType.TL:
			case TurnType.TSLL:
			case TurnType.TSHL:
			case TurnType.KL:
				return "Left";
			case TurnType.TR:
			case TurnType.TSLR:
			case TurnType.TSHR:
			case TurnType.KR:
				return "Right";
			case TurnType.TU:
				return "LeftUTurn";
			case TurnType.TRU:
				return "RightUTurn";
			default:
				return null;
		}
	}

	// ---- route edges ----

	/** One road segment of the route as a Telenav BasicEdge (speeds in m/s, lengths in meters). */
	@NonNull
	static Bundle basicEdge(@NonNull RouteSegmentResult segment) {
		ArrayList<Location> shape = new ArrayList<>();
		float length = 0;
		LatLon previous = null;
		int start = segment.getStartPointIndex();
		int end = segment.getEndPointIndex();
		int step = start <= end ? 1 : -1;
		for (int i = start; ; i += step) {
			LatLon point = segment.getPoint(i);
			Location location = new Location("");
			location.setLatitude(point.getLatitude());
			location.setLongitude(point.getLongitude());
			shape.add(location);
			if (previous != null) {
				length += (float) MapUtils.getDistance(previous, point);
			}
			previous = point;
			if (i == end) {
				break;
			}
		}

		Bundle edge = new Bundle();
		edge.putInt("length", Math.round(length));
		edge.putFloat("speedLimit", segment.getObject().getMaximumSpeed(segment.isForwardDirection()));
		edge.putFloat("travelSpeed", segment.getSegmentSpeed());
		edge.putFloat("etaLiveSpeed", 0f);
		edge.putInt("turnCost", 0);
		edge.putInt("elevation", 0);
		edge.putInt("id", 0);
		edge.putParcelableArrayList("shapePoints", shape);
		return edge;
	}

	// ---- state and cluster ----

	@NonNull
	static Bundle navState(@NonNull String sessionId, int state) {
		Bundle bundle = new Bundle();
		bundle.putString("navigationSessionId", sessionId);
		bundle.putInt("navState", state);
		return bundle;
	}

	/** Telenav's own answer: an empty image (no buffer, zeroed vehicle positions). */
	@NonNull
	static Bundle emptyClusterMapImage() {
		Bundle bundle = new Bundle();
		bundle.putByteArray("imageBuffer", null);
		bundle.putLong("imageCaptureTimestamp", 0L);
		bundle.putIntArray("vehiclePositionX", new int[40]);
		bundle.putIntArray("vehiclePositionY", new int[40]);
		bundle.putIntArray("vehicleHeading", new int[40]);
		return bundle;
	}

	@NonNull
	static Bundle emptyClusterTurnImage() {
		Bundle bundle = new Bundle();
		bundle.putString("imageType", "");
		bundle.putString("imageUrl", null);
		bundle.putLong("imageCaptureTimestamp", 0L);
		return bundle;
	}
}
