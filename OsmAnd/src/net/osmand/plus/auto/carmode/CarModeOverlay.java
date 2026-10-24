package net.osmand.plus.auto.carmode;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.auto.TripUtils;
import net.osmand.plus.routing.NextDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.search.ShowQuickSearchMode;
import net.osmand.plus.settings.enums.CompassMode;
import net.osmand.plus.settings.fragments.BaseSettingsFragment;
import net.osmand.plus.settings.fragments.SettingsScreenType;
import net.osmand.plus.settings.enums.ThemeUsageContext;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.mapwidgets.TurnDrawable;
import net.osmand.router.TurnType;
import net.osmand.util.Algorithms;

import java.util.Date;

/**
 * Native-view replacement for the Android Auto screen: a maneuver banner (top left), an ETA bar
 * (bottom left), a row of round buttons (top right: stop while navigating, menu, compass, settings,
 * search) and a column of round map buttons (bottom right: my location, zoom in, zoom out).
 * It only reads state from RoutingHelper and drives OsmandMapTileView, so it can be
 * attached on top of MapActivity without touching the existing HUD.
 */
public class CarModeOverlay extends FrameLayout {

	private static final long REFRESH_INTERVAL_MS = 1000;

	private final MapActivity mapActivity;
	private final OsmandApplication app;
	private final RoutingHelper routingHelper;
	private final Handler handler = new Handler(Looper.getMainLooper());

	private final LinearLayout maneuverPanel;
	private final ImageView turnImage;
	private final TextView distanceText;
	private final TextView streetText;
	private final TurnDrawable turnDrawable;

	private final ImageButton stopButton;
	private final ImageButton compassButton;
	private int compassIconId;

	private final LinearLayout etaPanel;
	private final TextView etaText;
	private final TextView etaDetailsText;

	private final Runnable refreshTask = new Runnable() {
		@Override
		public void run() {
			refresh();
			handler.postDelayed(this, REFRESH_INTERVAL_MS);
		}
	};

	public CarModeOverlay(@NonNull MapActivity mapActivity, @NonNull OsmandApplication app) {
		super(mapActivity);
		android.content.Context context = mapActivity;
		this.mapActivity = mapActivity;
		this.app = app;
		this.routingHelper = app.getRoutingHelper();
		// Let touches fall through to the map except on our own controls.
		setClickable(false);
		setFocusable(false);

		// Keep the controls clear of system bars (status bar, navigation bar).
		ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
			Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
			return insets;
		});

		turnDrawable = new TurnDrawable(context, false);

		// ---- Maneuver banner (top left) ----
		maneuverPanel = new LinearLayout(context);
		maneuverPanel.setOrientation(LinearLayout.HORIZONTAL);
		maneuverPanel.setGravity(Gravity.CENTER_VERTICAL);
		maneuverPanel.setBackground(roundedBackground(Color.parseColor("#E6202124"), dp(16)));
		maneuverPanel.setPadding(dp(16), dp(12), dp(20), dp(12));

		turnImage = new ImageView(context);
		turnImage.setImageDrawable(turnDrawable);
		maneuverPanel.addView(turnImage, new LinearLayout.LayoutParams(dp(88), dp(88)));

		LinearLayout texts = new LinearLayout(context);
		texts.setOrientation(LinearLayout.VERTICAL);
		texts.setPadding(dp(16), 0, 0, 0);

		distanceText = makeText(34, true);
		streetText = makeText(20, false);
		streetText.setMaxLines(2);
		texts.addView(distanceText);
		texts.addView(streetText);
		maneuverPanel.addView(texts, new LinearLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

		LayoutParams maneuverParams = new LayoutParams(dp(380), LayoutParams.WRAP_CONTENT,
				Gravity.TOP | Gravity.START);
		maneuverParams.setMargins(dp(16), dp(16), 0, 0);
		addView(maneuverPanel, maneuverParams);

		// ---- ETA bar (bottom left) ----
		etaPanel = new LinearLayout(context);
		etaPanel.setOrientation(LinearLayout.VERTICAL);
		etaPanel.setBackground(roundedBackground(Color.parseColor("#E6202124"), dp(16)));
		etaPanel.setPadding(dp(20), dp(10), dp(20), dp(10));
		etaText = makeText(28, true);
		etaDetailsText = makeText(18, false);
		etaPanel.addView(etaText);
		etaPanel.addView(etaDetailsText);

		LayoutParams etaParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
				Gravity.BOTTOM | Gravity.START);
		etaParams.setMargins(dp(16), 0, 0, dp(16));
		addView(etaPanel, etaParams);

		// ---- Top right row: stop (while navigating), menu, compass, settings, search ----
		LinearLayout topRow = new LinearLayout(context);
		topRow.setOrientation(LinearLayout.HORIZONTAL);
		stopButton = makeButton(R.drawable.ic_action_close, v -> {
			app.stopNavigation();
			refresh();
		});
		topRow.addView(stopButton);
		topRow.addView(makeButton(R.drawable.ic_action_list_flat, v -> mapActivity.openDrawer()));
		compassButton = makeButton(R.drawable.ic_action_compass, v -> {
			app.getMapViewTrackingUtilities().requestSwitchCompassToNextMode();
			updateCompassIcon();
		});
		topRow.addView(compassButton);
		topRow.addView(makeButton(R.drawable.ic_action_settings,
				v -> BaseSettingsFragment.showInstance(mapActivity, SettingsScreenType.MAIN_SETTINGS)));
		topRow.addView(makeButton(R.drawable.ic_action_search_dark,
				v -> mapActivity.getFragmentsHelper().showQuickSearch(ShowQuickSearchMode.NEW_IF_EXPIRED, false)));

		LayoutParams topRowParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
				Gravity.TOP | Gravity.END);
		topRowParams.setMargins(0, dp(10), dp(10), 0);
		addView(topRow, topRowParams);

		// ---- Bottom right column: my location, zoom in, zoom out ----
		LinearLayout column = new LinearLayout(context);
		column.setOrientation(LinearLayout.VERTICAL);
		column.addView(makeButton(R.drawable.ic_my_location, v -> getMapView().backToLocation()));
		column.addView(makeButton(R.drawable.ic_zoom_in, v -> getMapView().zoomIn()));
		column.addView(makeButton(R.drawable.ic_zoom_out, v -> getMapView().zoomOut()));

		LayoutParams columnParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
				Gravity.BOTTOM | Gravity.END);
		columnParams.setMargins(0, 0, dp(10), dp(10));
		addView(column, columnParams);

		refresh();
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		handler.removeCallbacks(refreshTask);
		handler.post(refreshTask);
	}

	@Override
	protected void onDetachedFromWindow() {
		handler.removeCallbacks(refreshTask);
		super.onDetachedFromWindow();
	}

	@NonNull
	private OsmandMapTileView getMapView() {
		return app.getOsmandMap().getMapView();
	}

	private void refresh() {
		updateCompassIcon();
		if (app.getSettings().CAR_MODE_UI_DEMO.get()) {
			showDemo();
			return;
		}
		boolean navigating = routingHelper.isRouteCalculated() && routingHelper.isFollowingMode();
		stopButton.setVisibility(navigating ? VISIBLE : GONE);
		maneuverPanel.setVisibility(navigating ? VISIBLE : GONE);
		etaPanel.setVisibility(navigating ? VISIBLE : GONE);
		if (!navigating) {
			return;
		}
		updateManeuver();
		updateEta();
	}

	private void updateCompassIcon() {
		CompassMode mode = app.getSettings().getCompassMode();
		int iconId = mode.getIconId(true);
		if (iconId != compassIconId) {
			compassIconId = iconId;
			compassButton.setImageResource(iconId);
			compassButton.clearColorFilter(); // the compass icons are already two-colored
		}
	}

	/** Sample values so the layout can be checked without a calculated route (emulator screenshots). */
	private void showDemo() {
		stopButton.setVisibility(VISIBLE);
		maneuverPanel.setVisibility(VISIBLE);
		etaPanel.setVisibility(VISIBLE);
		boolean leftSide = app.getSettings().DRIVING_REGION.get().leftHandDriving;
		turnDrawable.setTurnType(TurnType.valueOf(TurnType.TR, leftSide));
		turnDrawable.setTurnImminent(0, false);
		turnDrawable.updateColors(app.getDaynightHelper().isNightMode(ThemeUsageContext.MAP));
		turnImage.invalidate();
		setTextIfChanged(distanceText, "350 m");
		setTextIfChanged(streetText, "Rue Général de Gaulle");
		streetText.setVisibility(VISIBLE);
		setTextIfChanged(etaText, "23:27");
		setTextIfChanged(etaDetailsText, "14 min  ·  7,2 km");
	}

	private void updateManeuver() {
		boolean deviated = routingHelper.isDeviatedFromRoute();
		NextDirectionInfo info = routingHelper.getNextRouteDirectionInfo(new NextDirectionInfo(), true);
		TurnType turnType = null;
		int distance = 0;
		int imminent = 0;
		if (deviated) {
			boolean leftSide = app.getSettings().DRIVING_REGION.get().leftHandDriving;
			turnType = TurnType.valueOf(TurnType.OFFR, leftSide);
			distance = (int) routingHelper.getRouteDeviation();
		} else if (info != null && info.distanceTo >= 0 && info.directionInfo != null) {
			turnType = info.directionInfo.getTurnType();
			distance = info.distanceTo;
			imminent = info.imminent;
		}

		turnDrawable.setTurnType(turnType);
		turnDrawable.setTurnImminent(imminent, deviated);
		turnDrawable.updateColors(app.getDaynightHelper().isNightMode(ThemeUsageContext.MAP));
		turnImage.invalidate();

		setTextIfChanged(distanceText, TripUtils.getFormattedDistanceStr(app, distance));
		String street = deviated ? null : TripUtils.defineStreetName(app, info);
		setTextIfChanged(streetText, Algorithms.isEmpty(street) ? "" : street);
		streetText.setVisibility(Algorithms.isEmpty(street) ? GONE : VISIBLE);
	}

	private void updateEta() {
		int leftSeconds = routingHelper.getLeftTime();
		int leftMeters = routingHelper.getLeftDistance();
		if (leftSeconds < 0) {
			setTextIfChanged(etaText, "--");
		} else {
			Date arrival = new Date(System.currentTimeMillis() + leftSeconds * 1000L);
			setTextIfChanged(etaText, DateFormat.getTimeFormat(getContext()).format(arrival));
		}
		setTextIfChanged(etaDetailsText, formatDuration(leftSeconds) + "  ·  "
				+ TripUtils.getFormattedDistanceStr(app, leftMeters));
	}

	@NonNull
	private static String formatDuration(int seconds) {
		if (seconds < 0) {
			return "--";
		}
		int minutes = Math.round(seconds / 60f);
		if (minutes < 60) {
			return minutes + " min";
		}
		return (minutes / 60) + " h " + String.format("%02d", minutes % 60) + " min";
	}

	/** Avoids redundant updates (and accessibility events) on the once-per-second refresh. */
	private static void setTextIfChanged(@NonNull TextView view, @NonNull CharSequence text) {
		if (!text.toString().contentEquals(view.getText())) {
			view.setText(text);
		}
	}

	@NonNull
	private TextView makeText(float sp, boolean bold) {
		TextView tv = new TextView(getContext());
		tv.setTextColor(Color.WHITE);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
		tv.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
		return tv;
	}

	@NonNull
	private ImageButton makeButton(@DrawableRes int iconId, @NonNull OnClickListener listener) {
		ImageButton button = new ImageButton(getContext());
		button.setImageResource(iconId);
		button.setColorFilter(Color.WHITE);
		button.setScaleType(ImageView.ScaleType.CENTER);
		GradientDrawable background = new GradientDrawable();
		background.setShape(GradientDrawable.OVAL);
		background.setColor(Color.parseColor("#F2101010"));
		button.setBackground(background);
		button.setOnClickListener(listener);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), dp(72));
		params.setMargins(dp(6), dp(6), dp(6), dp(6));
		button.setLayoutParams(params);
		return button;
	}

	@NonNull
	private GradientDrawable roundedBackground(int color, int radiusPx) {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(color);
		drawable.setCornerRadius(radiusPx);
		return drawable;
	}

	private int dp(int value) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
				getResources().getDisplayMetrics()));
	}
}
