package com.telenav.arp.service;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.OsmandApplication;

/**
 * Starts the guidance notifications with the process, so the cluster is told about a navigation however
 * the app was started. It serves no data. A provider is created before Application.onCreate: the start is
 * posted to run right after it, when OsmAnd's helpers exist.
 */
public class ClusterInitProvider extends ContentProvider {

	@Override
	public boolean onCreate() {
		if (getContext() != null && getContext().getApplicationContext() instanceof OsmandApplication) {
			OsmandApplication app = (OsmandApplication) getContext().getApplicationContext();
			new Handler(Looper.getMainLooper()).post(() -> NavNotifier.start(app));
		}
		return true;
	}

	@Nullable
	@Override
	public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
	                    @Nullable String[] selectionArgs, @Nullable String sortOrder) {
		return null;
	}

	@Nullable
	@Override
	public String getType(@NonNull Uri uri) {
		return null;
	}

	@Nullable
	@Override
	public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
		return null;
	}

	@Override
	public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
		return 0;
	}

	@Override
	public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
	                  @Nullable String[] selectionArgs) {
		return 0;
	}
}
