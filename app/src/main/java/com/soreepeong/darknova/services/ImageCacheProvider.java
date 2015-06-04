package com.soreepeong.darknova.services;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

/**
 * Minimal image cache ContentProvider.
 *
 * @author Soreepeong
 */
public class ImageCacheProvider extends ContentProvider {
	public static final Uri PROVIDER_URI = Uri.parse("content://com.soreepeong.darknova.core.ImageCache.ImageCacheProvider/");
	private static final String KEY_TABLE_NAME = "cache_keys";
	private SQLiteDatabase mDb;

	@Override
	public boolean onCreate() {
		mDb = SQLiteDatabase.openOrCreateDatabase(getContext().getCacheDir().toString() + "files.db", null);
		mDb.execSQL("CREATE TABLE IF NOT EXISTS " + KEY_TABLE_NAME + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, created INTEGER, size INTEGER DEFAULT 0)");
		mDb.delete(KEY_TABLE_NAME, "size=0", null); // temporary row cleanup
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		return mDb.query(KEY_TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		return Uri.parse("cache://" + mDb.insert(KEY_TABLE_NAME, null, values));
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return mDb.delete(KEY_TABLE_NAME, selection, selectionArgs);
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		return mDb.update(KEY_TABLE_NAME, values, selection, selectionArgs);
	}
}
