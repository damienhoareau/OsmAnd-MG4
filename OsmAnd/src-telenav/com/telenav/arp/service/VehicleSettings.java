package com.telenav.arp.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.IndexConstants;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.enums.DayNightMode;
import net.osmand.shared.settings.enums.MetricsConstants;

import java.io.File;
import java.util.Locale;

/**
 * Applies to OsmAnd's settings what the car says about itself: day or night, distance unit, voice language.
 * Like Telenav it follows the car without taking the settings away from the user:
 * <ul>
 * <li>day/night follows the car for every profile whose map theme is automatic, was never set by the user, or
 * is what the car set;</li>
 * <li>the unit and the voice are set when the car's value changes, so a later choice in OsmAnd stays until
 * the car changes again.</li>
 * </ul>
 * Everything else the car sends (battery, fuel, gear, speed, sensors, interface language) has no counterpart
 * in OsmAnd and is ignored. Call it on the main thread.
 */
final class VehicleSettings {

	private static final String TAG = "VehicleSettings";
	private static final String PREFS = "mg4_vehicle";
	private static final String KEY_THEME = "theme_set_by_car";
	private static final String KEY_UNIT = "unit";
	private static final String KEY_VOICE = "voice_language";

	private final OsmandApplication app;
	private final SharedPreferences prefs;

	VehicleSettings(@NonNull OsmandApplication app) {
		this.app = app;
		this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	/**
	 * @param mode the car's value: 0 is day, anything else is night. Applies to every profile (the profile in
	 *             use is "browse the map" outside a navigation, the car's when guiding) that has no choice of its
	 *             own: automatic, never set by the user, or set by the car before.
	 */
	void dayNight(int mode) {
		OsmandSettings settings = app.getSettings();
		DayNightMode wanted = mode == 0 ? DayNightMode.DAY : DayNightMode.NIGHT;
		String setByCar = prefs.getString(KEY_THEME, null);
		for (ApplicationMode appMode : ApplicationMode.allPossibleValues()) {
			DayNightMode current = settings.DAYNIGHT_MODE.getModeValue(appMode);
			boolean following = current == DayNightMode.AUTO || !settings.DAYNIGHT_MODE.isSetForMode(appMode)
					|| current.name().equals(setByCar);
			if (following && current != wanted) {
				settings.DAYNIGHT_MODE.setModeValue(appMode, wanted);
			}
		}
		prefs.edit().putString(KEY_THEME, wanted.name()).apply();
		// OsmAnd redraws the map by itself only when the user changes the theme on screen
		app.getOsmandMap().getMapView().refreshMap();
	}

	/** @param unit the car's value: 1 is metric, 2 is imperial, anything else is no preference */
	void unit(int unit) {
		if ((unit != 1 && unit != 2) || prefs.getInt(KEY_UNIT, 0) == unit) {
			return;
		}
		OsmandSettings settings = app.getSettings();
		for (ApplicationMode appMode : ApplicationMode.allPossibleValues()) {
			MetricsConstants metric;
			if (unit == 1) {
				metric = MetricsConstants.KILOMETERS_AND_METERS;
			} else {
				boolean uk = "UK_AND_OTHERS".equals(settings.DRIVING_REGION.getModeValue(appMode).name());
				metric = uk ? MetricsConstants.MILES_AND_YARDS : MetricsConstants.MILES_AND_FEET;
			}
			settings.METRIC_SYSTEM.setModeValue(appMode, metric);
		}
		prefs.edit().putInt(KEY_UNIT, unit).apply();
	}

	/**
	 * @param carLanguage the car's code, such as FRE-EU. The voice changes only to one OsmAnd already has on
	 *                    the device: no pack is downloaded, so the car never ends up without voice.
	 */
	void voiceLanguage(@Nullable String carLanguage) {
		String language = languageOf(carLanguage);
		if (language == null || language.equals(prefs.getString(KEY_VOICE, null))) {
			return;
		}
		String voice = language + IndexConstants.VOICE_PROVIDER_SUFFIX;
		if (!new File(app.getAppPath(IndexConstants.VOICE_INDEX_DIR), voice).exists()) {
			Log.i(TAG, "no voice " + voice + " on the device: the voice language stays as it is");
			return;
		}
		OsmandSettings settings = app.getSettings();
		for (ApplicationMode appMode : ApplicationMode.allPossibleValues()) {
			settings.VOICE_PROVIDER.setModeValue(appMode, voice);
		}
		app.initVoiceCommandPlayer(app, settings.getApplicationMode(), null, false, false, true, false);
		prefs.edit().putString(KEY_VOICE, language).apply();
	}

	/** The ISO language of the SAIC language codes Telenav knows (its own table, app.a.b), or null. */
	@Nullable
	static String languageOf(@Nullable String carLanguage) {
		if (carLanguage == null) {
			return null;
		}
		switch (carLanguage.toUpperCase(Locale.ROOT)) {
			case "ENG-UK":
			case "ENG-IN":
			case "ENG-AUS":
				return "en";
			case "THAI-THAI":
				return "th";
			case "GER-EU":
				return "de";
			case "FRE-EU":
				return "fr";
			case "SPN-EU":
			case "SPN-SA":
				return "es";
			case "ARAB-GCC":
				return "ar";
			case "HINDI-IN":
				return "hi";
			default:
				return null;
		}
	}
}
