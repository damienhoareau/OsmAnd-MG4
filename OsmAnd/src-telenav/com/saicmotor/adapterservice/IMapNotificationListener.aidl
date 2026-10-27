package com.saicmotor.adapterservice;

import com.saicmotor.adapterservice.bean.AdapterGPSInfo;
import com.saicmotor.adapterservice.bean.Address;
import com.saicmotor.adapterservice.bean.EVRoutPoiInfo;

/**
 * What the SAIC adapter service's MapService calls on the map app once the app has registered this listener
 * (the car's state, and the commands of the voice assistant and the dashboard). The names of the original
 * are kept where Telenav's decompiled code shows them; the transaction codes follow the declaration order.
 */
interface IMapNotificationListener {
    void startNavApp();
    void stopNav();
    void setHomeAddress(in Address address, String language);
    void setOfficeAddress(in Address address, String language);
    void goHome();
    void goOffice();
    List<Address> SearchPoi(String keyword, String language);
    void goToPoi(String entityId, String addressName, String addressDesc, double latitude, double longitude);
    String getCurLocationDesc();
    void zoomIn();
    void zoomInMax();
    void zoomOut();
    void zoomReset();
    void reCenter();
    String setFastestRoute();
    String setShortestRoute();
    String setEconomicalRoute();
    String setAvoidingTollwayRoute();
    void onEnduranceMileageChanged(int mileageInKm);
    void onDayNightModeChanged(int mode);
    void onTtsLanguageChanged(String language);
    void onNetworkIsAvailableChanged(boolean available);
    void onSubjectIDChanged(String subjectId);
    void onUpdateAccelerometerChanged(float x, float y, float z);
    void onUpdateGyroScopeChanged(float x, float y, float z);
    void onUpdateCarSpeedChanged(float speed);
    void onUpdateGearStatusChanged(int gear);
    void onUpdateCurrentWheelTickChanged(float tick);
    void onUpdateAverageSpeedHasResetChanged(int value);
    void onUpdateAverageSpeedIsValidityChanged(int value);
    void systemShutDown();
    void resetNavAppSettings();
    void onUpdateLowBatteryChanged(int level);
    void onUpdateLowFuelChanged(int level);
    void onUpdateGdprMapStatus(int status);
    void onUpdateUnitChange(int unit);
    void onUpdateBatteryPercentageChanged(int percentage);
    void onUpdateChargingStatusChanged(boolean charging);
    void onUpdateChargingFinishTimeChanged(int time);
    void onUpdateCardDisplayStatusChanged(int status);
    void onUpdateClusterShowStatusChanged(boolean show);
    void onNavAppSubscribeStatusChanged(boolean subscribed);
    void onOtSdPM25LvlChanged(int level);
    void onInCarPM25LvlChanged(int level);
    void onOtSdPM25ValChanged(int value);
    void onInCarPM25ValChanged(int value);
    void onUpdateISAAccelerometerChanged(int a, int b, float x, float y, float z, long time);
    void onUpdateISAGyroScopeChanged(int a, int b, float x, float y, float z, long time);
    void onUpdateCarSteeringAngleChanged(float angle);
    void onNmeaMessageChange(boolean valid, String message, long time);
    void onSettingsLanguageChanged(String language);
    void startUpdateMapNotification(int value);
    void onGPSInfoChangeToIsa(in AdapterGPSInfo info);
    List<Address> SearchAroundPoi(String keyword, int radius, String language);
    void openNavAppPropertyOperation(int operation);
    void startNavFromEVRout(in List<EVRoutPoiInfo> waypoints, in List<EVRoutPoiInfo> destinations);
    void onUpdateTimeUnitChange(int unit);
    void updateDialpadPasswordTriggerMap(boolean triggered);
    List<Address> SearchCategoryPoi(int category, String language);
}
