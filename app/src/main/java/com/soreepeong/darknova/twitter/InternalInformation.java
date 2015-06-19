package com.soreepeong.darknova.twitter;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @author Soreepeong
 */
public class InternalInformation implements Parcelable {
	@SuppressWarnings("unused")
	public static final Creator<InternalInformation> CREATOR = new Creator<InternalInformation>() {
		@Override
		public InternalInformation createFromParcel(Parcel in) {
			return new InternalInformation(in);
		}

		@Override
		public InternalInformation[] newArray(int size) {
			return new InternalInformation[size];
		}
	};
	public long lastUpdated;
	public boolean stub;

	public InternalInformation() {
	}

	protected InternalInformation(Parcel in) {
		lastUpdated = in.readLong();
		stub = in.readByte() != 0x00;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(lastUpdated);
		dest.writeByte((byte) (stub ? 0x01 : 0x00));
	}
}
