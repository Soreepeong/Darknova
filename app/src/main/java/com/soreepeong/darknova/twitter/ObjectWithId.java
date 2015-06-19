package com.soreepeong.darknova.twitter;

import android.os.Parcelable;

/**
 * @author Soreepeong
 */
public interface ObjectWithId extends Parcelable, Comparable<ObjectWithId> {
	long getId();
}
