package com.saicmotor.adapterservice.bean;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.List;

/** The GPS fix of the car, sent for the speed assistant. The fields are written in the order of the original class (the parcel layout is its contract). */
public class AdapterGPSInfo implements Parcelable {

	public static final Creator<AdapterGPSInfo> CREATOR = new Creator<AdapterGPSInfo>() {
		@Override
		public AdapterGPSInfo createFromParcel(Parcel in) {
			return new AdapterGPSInfo(in);
		}

		@Override
		public AdapterGPSInfo[] newArray(int size) {
			return new AdapterGPSInfo[size];
		}
	};

	public int timeStamp;
	public int gpsStatus;
	public int heading;
	public int gpsSpeed;
	public int gpsPdop;
	public int gpsHdop;
	public int vdop;
	public int statelites;
	public int latitude;
	public int longtitude;
	public int altitiude;
	public List<AdapterSatellitesInfo> statelitesInfo;
	public long accuracy;

	public AdapterGPSInfo() {
	}

	protected AdapterGPSInfo(Parcel in) {
		timeStamp = in.readInt();
		gpsStatus = in.readInt();
		heading = in.readInt();
		gpsSpeed = in.readInt();
		gpsPdop = in.readInt();
		gpsHdop = in.readInt();
		vdop = in.readInt();
		statelites = in.readInt();
		latitude = in.readInt();
		longtitude = in.readInt();
		altitiude = in.readInt();
		statelitesInfo = in.createTypedArrayList(AdapterSatellitesInfo.CREATOR);
		accuracy = in.readLong();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(timeStamp);
		dest.writeInt(gpsStatus);
		dest.writeInt(heading);
		dest.writeInt(gpsSpeed);
		dest.writeInt(gpsPdop);
		dest.writeInt(gpsHdop);
		dest.writeInt(vdop);
		dest.writeInt(statelites);
		dest.writeInt(latitude);
		dest.writeInt(longtitude);
		dest.writeInt(altitiude);
		dest.writeTypedList(statelitesInfo);
		dest.writeLong(accuracy);
	}
}
