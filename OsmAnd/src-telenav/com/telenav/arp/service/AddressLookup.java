package com.telenav.arp.service;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.LatLon;
import net.osmand.plus.GeocodingLookupService.AddressLookupRequest;
import net.osmand.plus.OsmandApplication;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** OsmAnd's offline reverse geocoder is asynchronous: this waits for it, but not for long. For binder threads. */
final class AddressLookup {

	private static final long TIMEOUT_SECONDS = 5;

	private AddressLookup() {
	}

	/** The address text of a point, or null when none came in time. Never call it on the main thread. */
	@Nullable
	static String lookup(@NonNull OsmandApplication app, @NonNull LatLon point) {
		String[] result = new String[1];
		CountDownLatch done = new CountDownLatch(1);
		new Handler(Looper.getMainLooper()).post(() -> app.getGeocodingLookupService().lookupAddress(
				new AddressLookupRequest(point, address -> {
					result[0] = address;
					done.countDown();
				}, null)));
		try {
			done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return result[0];
	}
}
