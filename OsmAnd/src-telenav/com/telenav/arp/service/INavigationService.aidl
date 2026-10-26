package com.telenav.arp.service;

import android.location.Address;
import android.location.Location;
import android.os.Bundle;

/**
 * The navigation API of the car's Telenav app (com.telenav.arp.service.INavigationService), kept as is
 * so that the apps already written against it keep working. Clients call the methods by transaction
 * number, which is the declaration order here (1..30): do not reorder or insert anything.
 */
interface INavigationService {
	/** 1: -1 not navigating, 0 paused, 1 guiding. */
	int getNavigationStatus();
	/** 2 */
	String resumeNavigationGuidance();
	/** 3 */
	String pauseNavigationGuidance();
	/** 4 */
	String stopNavigationGuidance();
	/** 5: "-1" without a session. */
	String getCurrentNavigationSessionID();
	/** 6 */
	Location getDestination();
	/** 7 */
	Location getStartingPoint();
	/** 8 */
	List<Location> getWaypointsList();
	/** 9 */
	int getCurrentWaypointIndex();
	/** 10 */
	List<Bundle> getGuidanceSegmentList();
	/** 11 */
	int getCurrentGuidanceSegmentIndex();
	/** 12 */
	String setAudioGuidanceMuted(boolean muted);
	/** 13 */
	boolean isAudioGuidanceMuted();
	/** 14 */
	String repeatAudioGuidance();
	/** 15 */
	List<Bundle> getPoiList(String keyword, in Location location, in List<String> categories, int limit, int radiusKm);
	/** 16 */
	Bundle getPoiDetail(String entityId);
	/** 17 */
	List<Address> geocode(String query, int maxResults);
	/** 18 */
	List<Address> reverseGeocode(double latitude, double longitude, int maxResults);
	/** 19 */
	Bundle getClusterMapImage(int width, int height);
	/** 20 */
	Bundle getClusterTurnImage(int width, int height);
	/** 21 */
	void addWaypointToFront(in Location location);
	/** 22 */
	Bundle getTimeZone();
	/** 23 */
	String getAppVersion();
	/** 24 */
	String getMapVersion();
	/** 25 */
	String getGMPartNumberOfMapSDCard();
	/** 26 */
	List<Bundle> getRouteBasicEdges();
	/** 27 */
	int getServiceState();
	/** 28 */
	String getMapAge();
	/** 29 */
	List<Bundle> getParkingCards(int maxResults);
	/** 30 */
	Bundle getNavigationState();
}
