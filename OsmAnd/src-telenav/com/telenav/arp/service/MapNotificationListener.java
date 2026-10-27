package com.telenav.arp.service;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.saicmotor.adapterservice.IMapNotificationListener;
import com.saicmotor.adapterservice.bean.AdapterGPSInfo;
import com.saicmotor.adapterservice.bean.Address;
import com.saicmotor.adapterservice.bean.EVRoutPoiInfo;

import net.osmand.Location;
import net.osmand.data.FavouritePoint;
import net.osmand.data.LatLon;
import net.osmand.data.SpecialPointType;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.router.GeneralRouter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the SAIC adapter service's MapService calls on the map app (Telenav's SAICMapNotificationListener):
 * the commands of the voice assistant and the dashboard (go home, go to a place, zoom, route preferences)
 * and the car's state. The commands are carried out by OsmAnd. Of the car's state, the day/night mode, the
 * distance unit and the voice language are applied to OsmAnd's settings (VehicleSettings); the rest (battery,
 * fuel, gear, speed, sensors, interface language) has no counterpart in OsmAnd and is ignored.
 *
 * Not carried out: the place searches (they answer with an empty list), resetNavAppSettings,
 * startNavFromEVRout and the economical route (OsmAnd has no such style).
 */
final class MapNotificationListener extends IMapNotificationListener.Stub {

	private static final String TAG = "MapNotification";

	private final OsmandApplication app;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final VehicleSettings vehicle;

	MapNotificationListener(@NonNull OsmandApplication app) {
		this.app = app;
		this.vehicle = new VehicleSettings(app);
	}

	// ---- commands ----

	@Override
	public void startNavApp() {
		Log.i(TAG, "startNavApp");
		mainHandler.post(() -> {
			Intent intent = new Intent(app, MapActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			app.startActivity(intent);
		});
	}

	@Override
	public void stopNav() {
		Log.i(TAG, "stopNav");
		mainHandler.post(app::stopNavigation);
	}

	@Override
	public void setHomeAddress(Address address, String language) {
		setSpecialPoint(address, SpecialPointType.HOME);
	}

	@Override
	public void setOfficeAddress(Address address, String language) {
		setSpecialPoint(address, SpecialPointType.WORK);
	}

	private void setSpecialPoint(@Nullable Address address, @NonNull SpecialPointType type) {
		Log.i(TAG, "set " + type + " " + address);
		if (address == null || !validLocation(address.latitude, address.longitude)) {
			return;
		}
		mainHandler.post(() -> app.getFavoritesHelper().setSpecialPoint(
				new LatLon(address.latitude, address.longitude), type, address.addressName));
	}

	@Override
	public void goHome() {
		goToSpecialPoint(SpecialPointType.HOME);
	}

	@Override
	public void goOffice() {
		goToSpecialPoint(SpecialPointType.WORK);
	}

	/** Like Telenav, this goes only where the user has set the place: nothing happens otherwise. */
	private void goToSpecialPoint(@NonNull SpecialPointType type) {
		Log.i(TAG, "go to " + type);
		mainHandler.post(() -> {
			FavouritePoint point = app.getFavoritesHelper().getSpecialPoint(type);
			if (point != null) {
				showRouteTo(point.getLatitude(), point.getLongitude());
			}
		});
	}

	@Override
	public void goToPoi(String entityId, String addressName, String addressDesc, double latitude, double longitude) {
		Log.i(TAG, "goToPoi " + addressName + " " + latitude + "," + longitude);
		if (validLocation(latitude, longitude)) {
			mainHandler.post(() -> showRouteTo(latitude, longitude));
		}
	}

	/** Telenav shows its route planning screen: OsmAnd's route preview comes from its own navigation link. */
	private void showRouteTo(double latitude, double longitude) {
		Intent intent = new Intent(Intent.ACTION_VIEW,
				Uri.parse(String.format(Locale.US, "google.navigation:q=%f,%f", latitude, longitude)));
		intent.setPackage(app.getPackageName());
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		app.startActivity(intent);
	}

	private static boolean validLocation(double latitude, double longitude) {
		return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
	}

	@Override
	public String getCurLocationDesc() {
		Location location = app.getLocationProvider().getLastKnownLocation();
		JSONObject json = new JSONObject();
		try {
			if (location != null) {
				json.put("lon", location.getLongitude());
				json.put("lat", location.getLatitude());
				String text = AddressLookup.lookup(app, new LatLon(location.getLatitude(), location.getLongitude()));
				if (text != null && !text.isEmpty()) {
					// OsmAnd gives one formatted line: it goes where Telenav puts the street
					json.put("streetName", text);
				}
			}
		} catch (JSONException e) {
			Log.w(TAG, "getCurLocationDesc failed", e);
		}
		return json.toString();
	}

	@Override
	public void zoomIn() {
		mainHandler.post(() -> mapView().zoomIn());
	}

	@Override
	public void zoomOut() {
		mainHandler.post(() -> mapView().zoomOut());
	}

	@Override
	public void zoomInMax() {
		mainHandler.post(() -> mapView().setIntZoom(mapView().getMaxZoom()));
	}

	@Override
	public void zoomReset() {
		mainHandler.post(() -> app.getMapViewTrackingUtilities().backToLocationImpl());
	}

	@Override
	public void reCenter() {
		mainHandler.post(() -> app.getMapViewTrackingUtilities().backToLocationImpl());
	}

	@NonNull
	private OsmandMapTileView mapView() {
		return app.getOsmandMap().getMapView();
	}

	@Override
	public String setFastestRoute() {
		setShortest(false);
		return app.getString(R.string.fast_route_mode);
	}

	@Override
	public String setShortestRoute() {
		setShortest(true);
		return "";
	}

	@Override
	public String setEconomicalRoute() {
		return "";
	}

	@Override
	public String setAvoidingTollwayRoute() {
		mainHandler.post(() -> {
			RoutingHelper routing = app.getRoutingHelper();
			ApplicationMode mode = routing.getAppMode();
			app.getSettings().getCustomRoutingBooleanProperty(GeneralRouter.AVOID_TOLL, false).setModeValue(mode, true);
			routing.onSettingsChanged(mode);
		});
		return app.getString(R.string.avoid_toll_roads);
	}

	private void setShortest(boolean shortest) {
		mainHandler.post(() -> {
			RoutingHelper routing = app.getRoutingHelper();
			ApplicationMode mode = routing.getAppMode();
			app.getSettings().getCustomRoutingBooleanProperty(GeneralRouter.USE_SHORTEST_WAY, false)
					.setModeValue(mode, shortest);
			routing.onSettingsChanged(mode);
		});
	}

	@Override
	public void resetNavAppSettings() {
		Log.i(TAG, "resetNavAppSettings: not supported");
	}

	@Override
	public void startNavFromEVRout(List<EVRoutPoiInfo> waypoints, List<EVRoutPoiInfo> destinations) {
		Log.i(TAG, "startNavFromEVRout: not supported");
	}

	// ---- place search: not implemented ----

	@Override
	public List<Address> SearchPoi(String keyword, String language) {
		return new ArrayList<>();
	}

	@Override
	public List<Address> SearchAroundPoi(String keyword, int radius, String language) {
		return new ArrayList<>();
	}

	@Override
	public List<Address> SearchCategoryPoi(int category, String language) {
		return new ArrayList<>();
	}

	// ---- the car's state ----

	@Override
	public void onEnduranceMileageChanged(int mileageInKm) {
	}

	@Override
	public void onDayNightModeChanged(int mode) {
		mainHandler.post(() -> vehicle.dayNight(mode));
	}

	@Override
	public void onTtsLanguageChanged(String language) {
		mainHandler.post(() -> vehicle.voiceLanguage(language));
	}

	@Override
	public void onNetworkIsAvailableChanged(boolean available) {
	}

	@Override
	public void onSubjectIDChanged(String subjectId) {
	}

	@Override
	public void onUpdateAccelerometerChanged(float x, float y, float z) {
	}

	@Override
	public void onUpdateGyroScopeChanged(float x, float y, float z) {
	}

	@Override
	public void onUpdateCarSpeedChanged(float speed) {
	}

	@Override
	public void onUpdateGearStatusChanged(int gear) {
	}

	@Override
	public void onUpdateCurrentWheelTickChanged(float tick) {
	}

	@Override
	public void onUpdateAverageSpeedHasResetChanged(int value) {
	}

	@Override
	public void onUpdateAverageSpeedIsValidityChanged(int value) {
	}

	@Override
	public void systemShutDown() {
	}

	@Override
	public void onUpdateLowBatteryChanged(int level) {
	}

	@Override
	public void onUpdateLowFuelChanged(int level) {
	}

	@Override
	public void onUpdateGdprMapStatus(int status) {
	}

	@Override
	public void onUpdateUnitChange(int unit) {
		mainHandler.post(() -> vehicle.unit(unit));
	}

	@Override
	public void onUpdateBatteryPercentageChanged(int percentage) {
	}

	@Override
	public void onUpdateChargingStatusChanged(boolean charging) {
	}

	@Override
	public void onUpdateChargingFinishTimeChanged(int time) {
	}

	@Override
	public void onUpdateCardDisplayStatusChanged(int status) {
	}

	@Override
	public void onUpdateClusterShowStatusChanged(boolean show) {
	}

	@Override
	public void onNavAppSubscribeStatusChanged(boolean subscribed) {
	}

	@Override
	public void onOtSdPM25LvlChanged(int level) {
	}

	@Override
	public void onInCarPM25LvlChanged(int level) {
	}

	@Override
	public void onOtSdPM25ValChanged(int value) {
	}

	@Override
	public void onInCarPM25ValChanged(int value) {
	}

	@Override
	public void onUpdateISAAccelerometerChanged(int a, int b, float x, float y, float z, long time) {
	}

	@Override
	public void onUpdateISAGyroScopeChanged(int a, int b, float x, float y, float z, long time) {
	}

	@Override
	public void onUpdateCarSteeringAngleChanged(float angle) {
	}

	@Override
	public void onNmeaMessageChange(boolean valid, String message, long time) {
	}

	@Override
	public void onSettingsLanguageChanged(String language) {
	}

	@Override
	public void startUpdateMapNotification(int value) {
	}

	@Override
	public void onGPSInfoChangeToIsa(AdapterGPSInfo info) {
	}

	@Override
	public void openNavAppPropertyOperation(int operation) {
	}

	@Override
	public void onUpdateTimeUnitChange(int unit) {
	}

	@Override
	public void updateDialpadPasswordTriggerMap(boolean triggered) {
	}
}
