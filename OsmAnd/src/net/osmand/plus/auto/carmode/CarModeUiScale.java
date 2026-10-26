package net.osmand.plus.auto.carmode;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import net.osmand.plus.OsmandApplication;

/**
 * The MG4 head unit reports 160 dpi on a 1920x720 panel, so menus and dialogs drawn at that
 * density are too small to use while driving. In car mode every activity gets a larger density.
 * The map itself is not affected: it reads the real display metrics.
 */
public final class CarModeUiScale {

	private static final float UI_SCALE = 1.5f;

	private CarModeUiScale() {
	}

	@NonNull
	public static Context wrap(@NonNull OsmandApplication app, @NonNull Context base) {
		if (!app.getSettings().CAR_MODE_UI.get()) {
			return base;
		}
		Configuration config = new Configuration(base.getResources().getConfiguration());
		config.densityDpi = Math.round(config.densityDpi * UI_SCALE);
		return base.createConfigurationContext(config);
	}
}
