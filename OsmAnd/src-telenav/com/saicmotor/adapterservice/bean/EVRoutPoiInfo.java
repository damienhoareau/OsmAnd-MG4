package com.saicmotor.adapterservice.bean;

import android.os.Parcel;
import android.os.Parcelable;

/** A stop of a route planned by the electric vehicle service. The fields are written in the order of the original class (the parcel layout is its contract). */
public class EVRoutPoiInfo implements Parcelable {

	public static final Creator<EVRoutPoiInfo> CREATOR = new Creator<EVRoutPoiInfo>() {
		@Override
		public EVRoutPoiInfo createFromParcel(Parcel in) {
			return new EVRoutPoiInfo(in);
		}

		@Override
		public EVRoutPoiInfo[] newArray(int size) {
			return new EVRoutPoiInfo[size];
		}
	};

	public double latitude;
	public double longitude;
	public String addressName;

	public EVRoutPoiInfo() {
	}

	protected EVRoutPoiInfo(Parcel in) {
		latitude = in.readDouble();
		longitude = in.readDouble();
		addressName = in.readString();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeDouble(latitude);
		dest.writeDouble(longitude);
		dest.writeString(addressName);
	}
}
