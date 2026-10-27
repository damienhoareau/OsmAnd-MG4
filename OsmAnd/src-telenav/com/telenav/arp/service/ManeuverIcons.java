package com.telenav.arp.service;

import androidx.annotation.NonNull;

/** The maneuver pictures of the SAIC cluster and their text, as Telenav's product class (app.as) numbers them. */
final class ManeuverIcons {

	static final int DESTINATION_AHEAD = 20;

	private static final String[] DIRECTIONS = new String[40];

	static {
		DIRECTIONS[0] = "Go Straight";
		DIRECTIONS[1] = "Turn Left";
		DIRECTIONS[2] = "Turn Right";
		DIRECTIONS[3] = "Direction to Left";
		DIRECTIONS[4] = "Direction Right";
		DIRECTIONS[5] = "U-Turn";
		DIRECTIONS[6] = "U-Turn";
		DIRECTIONS[7] = "To right 4 o’clock";
		DIRECTIONS[8] = "To left 8 o’clock";
		DIRECTIONS[9] = "Go straight (At a rotary)";
		DIRECTIONS[10] = "To left 9 o’clock (At a rotary)";
		DIRECTIONS[11] = "To right 3 o’clock (At a rotary)";
		DIRECTIONS[12] = "U-Turn (At a rotary)";
		DIRECTIONS[13] = "To right 1 o’clock (At a rotary)";
		DIRECTIONS[14] = "To right 4 o’clock (At a rotary)";
		DIRECTIONS[15] = "To left 8 o’clock (At a rotary)";
		DIRECTIONS[16] = "To left 11 o’clock (At a rotary)";
		DIRECTIONS[17] = "Keep Left";
		DIRECTIONS[18] = "Keep Right";
		DIRECTIONS[19] = "Ferry";
		DIRECTIONS[20] = "Reach destination";
		DIRECTIONS[21] = "Reach destination";
		DIRECTIONS[22] = "Reach destination";
		DIRECTIONS[23] = "Reach way point 1";
		DIRECTIONS[24] = "Reach way point 1";
		DIRECTIONS[25] = "Reach way point 1";
		DIRECTIONS[26] = "Reach way point 2";
		DIRECTIONS[27] = "Reach way point 2";
		DIRECTIONS[28] = "Reach way point 2";
		DIRECTIONS[29] = "Reach way point 3";
		DIRECTIONS[30] = "Reach way point 3";
		DIRECTIONS[31] = "Reach way point 3";
		DIRECTIONS[32] = "Enter Right";
		DIRECTIONS[33] = "Merge Left";
		DIRECTIONS[34] = "Enter Left";
		DIRECTIONS[35] = "Merge Right";
		DIRECTIONS[36] = "Exit Left";
		DIRECTIONS[37] = "Exit Right";
		DIRECTIONS[38] = "PassTollBooth";
		DIRECTIONS[39] = "HookTurnRight";
	}

	private ManeuverIcons() {
	}

	@NonNull
	static String direction(int icon) {
		return icon >= 0 && icon < DIRECTIONS.length && DIRECTIONS[icon] != null ? DIRECTIONS[icon] : "";
	}

	/** The picture of a Telenav TurnType name, or -1 when the cluster has none. */
	static int turn(@NonNull String turnType) {
		switch (turnType) {
			case "Continue":
				return 0;
			case "TurnLeft":
				return 1;
			case "TurnRight":
				return 2;
			case "TurnSlightLeft":
				return 3;
			case "TurnSlightRight":
				return 4;
			case "LeftUTurn":
				return 5;
			case "RightUTurn":
				return 6;
			case "TurnHardRight":
				return 7;
			case "TurnHardLeft":
				return 8;
			case "StayLeft":
				return 17;
			case "StayRight":
				return 18;
			default:
				return -1;
		}
	}

	/** The way point pictures stop at the third one; the next ones reuse it. */
	static int waypointAhead(int waypointIndex) {
		return waypointIndex <= 0 ? 23 : waypointIndex == 1 ? 26 : 29;
	}

	/** A roundabout is shown by the direction of its exit, the angle being clockwise (right is positive). */
	static int roundabout(float angle) {
		float a = angle % 360;
		if (a > 180) {
			a -= 360;
		} else if (a < -180) {
			a += 360;
		}
		float abs = Math.abs(a);
		if (abs < 20) {
			return 9;
		}
		if (a > 0) {
			return abs < 60 ? 13 : abs < 120 ? 11 : abs < 165 ? 14 : 12;
		}
		return abs < 60 ? 16 : abs < 120 ? 10 : abs < 165 ? 15 : 12;
	}
}
