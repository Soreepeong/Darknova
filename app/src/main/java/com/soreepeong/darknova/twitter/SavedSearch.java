package com.soreepeong.darknova.twitter;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * @author Soreepeong
 *         <p/>
 *         https://dev.twitter.com/rest/reference/get/saved_searches/list
 */
public class SavedSearch implements Parcelable {
	@SuppressWarnings("unused")
	public static final Parcelable.Creator<SavedSearch> CREATOR = new Parcelable.Creator<SavedSearch>() {
		@Override
		public SavedSearch createFromParcel(Parcel in) {
			return new SavedSearch(in);
		}

		@Override
		public SavedSearch[] newArray(int size) {
			return new SavedSearch[size];
		}
	};
	public long created_at;
	public long id;
	public String name;
	public String query;

	public SavedSearch() {
	}

	protected SavedSearch(Parcel in) {
		created_at = in.readLong();
		id = in.readLong();
		name = in.readString();
		query = in.readString();
	}

	public SavedSearch(String key, SharedPreferences in) {
		created_at = in.getLong(key + ".created_at", 0);
		id = in.getLong(key + ".id", 0);
		name = in.getString(key + ".name", null);
		query = in.getString(key + ".query", null);
	}

	public static void removeFromPreference(String key, SharedPreferences.Editor dest) {
		dest.remove(key + ".created_at");
		dest.remove(key + ".id");
		dest.remove(key + ".name");
		dest.remove(key + ".query");
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public int hashCode() {
		return (int) (id ^ (id >> 32));
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SavedSearch && id == ((SavedSearch) o).id;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(created_at);
		dest.writeLong(id);
		dest.writeString(name);
		dest.writeString(query);
	}

	public void writeToPreferences(String key, SharedPreferences.Editor dest) {
		dest.putLong(key + ".created_at", created_at);
		dest.putLong(key + ".id", id);
		dest.putString(key + ".name", name);
		dest.putString(key + ".query", query);
	}
}
