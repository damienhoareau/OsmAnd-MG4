package net.osmand.plus.auto.carmode;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import net.osmand.core.android.MapRendererView;
import net.osmand.core.jni.AreaI;
import net.osmand.core.jni.PointI;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.corenative.NativeCoreContext;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary diagnostics for the MG4 head unit, where the OpenGL map stays blank: dumps the state
 * of the native renderer and of the Java map view. Collect with: adb logcat -s MG4DIAG
 */
public final class RendererDiag {

	private static final String TAG = "MG4DIAG";
	private static final long[] DELAYS_MS = {3000, 8000, 20000, 45000};
	private static final AtomicBoolean SCHEDULED = new AtomicBoolean();

	private RendererDiag() {
	}

	/** Dumps now, and schedules a few later dumps (once per process). */
	public static void onRenderingViewSetup(@NonNull OsmandApplication app) {
		dump(app, "setupRenderingView");
		if (SCHEDULED.compareAndSet(false, true)) {
			Log.i(TAG, "device sdk=" + Build.VERSION.SDK_INT + " abis=" + TextUtils.join(",", Build.SUPPORTED_ABIS)
					+ " model=" + Build.MODEL);
			Handler handler = new Handler(Looper.getMainLooper());
			for (long delay : DELAYS_MS) {
				handler.postDelayed(() -> dump(app, "+" + delay + "ms"), delay);
			}
		}
	}

	public static void dump(@NonNull OsmandApplication app, @NonNull String label) {
		try {
			OsmandMapTileView mapView = app.getOsmandMap().getMapView();
			StringBuilder sb = new StringBuilder("[").append(label).append("] ");
			sb.append("openGl=").append(app.useOpenGlRenderer());
			sb.append(" coreInit=").append(NativeCoreContext.isInit());
			sb.append(" ctx=").append(NativeCoreContext.getMapRendererContext() != null);
			sb.append(" | java: zoom=").append(mapView.getZoom()).append('+').append(mapView.getZoomFloatPart());
			sb.append(" lat=").append(mapView.getLatitude()).append(" lon=").append(mapView.getLongitude());
			sb.append(" elev=").append(mapView.getElevationAngle()).append(" rot=").append(mapView.getRotate());
			RotatedTileBox box = mapView.getRotatedTileBox();
			sb.append(" box=").append(box.getPixWidth()).append('x').append(box.getPixHeight());

			MapRendererView r = mapView.getMapRenderer();
			sb.append(" | native: ").append(r == null ? "renderer=null" : "renderer=set");
			if (r != null) {
				sb.append(" view=").append(r.getWidth()).append('x').append(r.getHeight());
				sb.append(" visible=").append(r.getVisibility() == android.view.View.VISIBLE);
				sb.append(" active=").append(r.isRenderingActive());
				sb.append(" invalidated=").append(r.isFrameInvalidated());
				sb.append(" symbolsSuspended=").append(r.isSymbolsUpdateSuspended());
				sb.append(" zoom=").append(r.getZoom()).append(" level=").append(r.getZoomLevel());
				sb.append(" azimuth=").append(r.getAzimuth()).append(" elev=").append(r.getElevationAngle());
				PointI target = r.getTarget();
				sb.append(" target31=").append(target.getX()).append(',').append(target.getY());
				sb.append(" targetHeightM=").append(r.getMapTargetHeightInMeters());
				sb.append(" cameraHeightM=").append(r.getCameraHeightInMeters());
				AreaI bbox = r.getVisibleBBox31();
				sb.append(" bbox31=").append(bbox.getTopLeft().getX()).append(',').append(bbox.getTopLeft().getY())
						.append("..").append(bbox.getBottomRight().getX()).append(',').append(bbox.getBottomRight().getY());
			}
			Log.i(TAG, sb.toString());
		} catch (Throwable t) {
			Log.e(TAG, "dump failed at " + label, t);
		}
	}
}
