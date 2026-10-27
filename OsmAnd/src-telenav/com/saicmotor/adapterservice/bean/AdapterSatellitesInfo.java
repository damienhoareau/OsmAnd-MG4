package com.saicmotor.adapterservice.bean;

import android.os.Parcel;
import android.os.Parcelable;

/** One satellite of the car GPS. The fields are written in the order of the original class (the parcel layout is its contract). */
public class AdapterSatellitesInfo implements Parcelable {

	public static final Creator<AdapterSatellitesInfo> CREATOR = new Creator<AdapterSatellitesInfo>() {
		@Override
		public AdapterSatellitesInfo createFromParcel(Parcel in) {
			return new AdapterSatellitesInfo(in);
		}

		@Override
		public AdapterSatellitesInfo[] newArray(int size) {
			return new AdapterSatellitesInfo[size];
		}
	};

	public int satellitesInviewID;
	public int evalution;
	public int azimuth;
	public int snr;
	public int isUsed;

	public AdapterSatellitesInfo() {
	}

	protected AdapterSatellitesInfo(Parcel in) {
		satellitesInviewID = in.readInt();
		evalution = in.readInt();
		azimuth = in.readInt();
		snr = in.readInt();
		isUsed = in.readInt();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(satellitesInviewID);
		dest.writeInt(evalution);
		dest.writeInt(azimuth);
		dest.writeInt(snr);
		dest.writeInt(isUsed);
	}
}
