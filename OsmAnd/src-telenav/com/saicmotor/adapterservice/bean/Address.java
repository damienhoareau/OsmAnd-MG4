package com.saicmotor.adapterservice.bean;

import android.os.Parcel;
import android.os.Parcelable;

/** A place of the voice assistant (a search result, or the home or work address to set). The fields are written in the order of the original class (the parcel layout is its contract). */
public class Address implements Parcelable {

	public static final Creator<Address> CREATOR = new Creator<Address>() {
		@Override
		public Address createFromParcel(Parcel in) {
			return new Address(in);
		}

		@Override
		public Address[] newArray(int size) {
			return new Address[size];
		}
	};

	public String id;
	public String addressName;
	public String addressDesc;
	public double distance;
	public double latitude;
	public double longitude;

	public Address() {
	}

	protected Address(Parcel in) {
		id = in.readString();
		addressName = in.readString();
		addressDesc = in.readString();
		distance = in.readDouble();
		latitude = in.readDouble();
		longitude = in.readDouble();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(id);
		dest.writeString(addressName);
		dest.writeString(addressDesc);
		dest.writeDouble(distance);
		dest.writeDouble(latitude);
		dest.writeDouble(longitude);
	}
}
