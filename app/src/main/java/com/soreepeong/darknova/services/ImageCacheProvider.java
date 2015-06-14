package com.soreepeong.darknova.services;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Minimal image cache ContentProvider.
 *
 * @author Soreepeong
 */
public class ImageCacheProvider extends ContentProvider {
	public static final Uri PROVIDER_URI = Uri.parse("content://com.soreepeong.darknova.services.ImageCacheProvider/");
	private static final int TRUNCATE_DISK_CACHE = 1;
	private static final int TRUNCATE_DISK_CACHE_INTERVAL = 30000;
	private static final String KEY_TABLE_NAME = "cache_keys";
	private static final String[] COLUMN_ID_SIZE = new String[]{"_id", "size"};
	private static final int DISK_MAX_SIZE = 64 * 1048576;
	private Handler mHandler;
	private SQLiteDatabase mDb;
	private File mCachePath;

	/**
	 * Truncate disk cache
	 */
	public void truncateDiskCache() {
		ArrayList<String> removeList = new ArrayList<>();
		File[] fileArray = mCachePath.listFiles();
		ArrayList<File> invalidFiles = fileArray != null ? new ArrayList<>(Arrays.asList(fileArray)) : new ArrayList<File>();
		Cursor cursor = mDb.query(KEY_TABLE_NAME, COLUMN_ID_SIZE, null, null, null, null, "created desc");
		if (cursor.moveToFirst()) {
			long sizeSum = 0;
			do {
				sizeSum += cursor.getLong(1);
				if (sizeSum < DISK_MAX_SIZE)
					invalidFiles.remove(new File(mCachePath, cursor.getString(0)));
				else
					removeList.add(cursor.getString(0));
			} while (cursor.moveToNext());
		}
		cursor.close();
		for (String id : removeList)
			mDb.delete(KEY_TABLE_NAME, "_id=?", new String[]{id});
		for (File f : invalidFiles) {
			if (System.currentTimeMillis() - f.lastModified() > 30000) {
				android.util.Log.d("ImageCacheProvider", "purging old file " + f.getName() + " of " + f.length() + " bytes");
				if (!f.delete())
					f.deleteOnExit();
			}
		}
	}

	@Override
	public boolean onCreate() {
		if (getContext().getExternalCacheDir() != null)
			mCachePath = getContext().getExternalCacheDir();
		else
			mCachePath = getContext().getCacheDir();
		mCachePath = new File(mCachePath, "imagecache");
		if (!mCachePath.isDirectory() && !mCachePath.mkdirs())
			return false;

		HandlerThread t = new HandlerThread("ImageCacher purger");
		t.start();
		mHandler = new Handler(t.getLooper(), new Handler.Callback() {
			@Override
			public boolean handleMessage(Message msg) {
				switch (msg.what) {
					case TRUNCATE_DISK_CACHE:
						truncateDiskCache();
						mHandler.removeMessages(TRUNCATE_DISK_CACHE, msg.obj);
						mHandler.sendMessageDelayed(Message.obtain(mHandler, TRUNCATE_DISK_CACHE, msg.obj), TRUNCATE_DISK_CACHE_INTERVAL);
						break;
				}
				return false;
			}
		});

		mDb = SQLiteDatabase.openOrCreateDatabase(new File(getContext().getCacheDir(), "files.db"), null);
		mDb.execSQL("CREATE TABLE IF NOT EXISTS " + KEY_TABLE_NAME + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, created INTEGER, size INTEGER DEFAULT 0)");
		mDb.delete(KEY_TABLE_NAME, "size=0", null); // temporary row cleanup

		mHandler.sendMessage(Message.obtain(mHandler, TRUNCATE_DISK_CACHE, mCachePath));
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		if (selection.equals("url=?"))
			selectionArgs[0] = selectionArgs[0].substring(selectionArgs[0].indexOf(":")+1);
		ContentValues cv = new ContentValues();
		cv.put("created", System.currentTimeMillis());
		mDb.update(KEY_TABLE_NAME, cv, selection, selectionArgs);
		return mDb.query(KEY_TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
	}

	@Override
	public String getType(Uri uri) {
		return mCachePath.getAbsolutePath();
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		if (values.containsKey("url")) {
			String url = values.getAsString("url");
			url = url.substring(url.indexOf(":") + 1);
			values.put("url", url);
		}
		values.put("created", System.currentTimeMillis());
		return Uri.parse("file://" + new File(mCachePath, Long.toString(mDb.insert(KEY_TABLE_NAME, null, values))).getAbsolutePath());
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		if (selection.equals("url=?"))
			selectionArgs[0] = selectionArgs[0].substring(selectionArgs[0].indexOf(":")+1);
		return mDb.delete(KEY_TABLE_NAME, selection, selectionArgs);
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		if (selection.equals("url=?"))
			selectionArgs[0] = selectionArgs[0].substring(selectionArgs[0].indexOf(":") + 1);
		if (values.containsKey("url")) {
			String url = values.getAsString("url");
			url = url.substring(url.indexOf(":") + 1);
			values.put("url", url);
		}
		values.put("created", System.currentTimeMillis());
		return mDb.update(KEY_TABLE_NAME, values, selection, selectionArgs);
	}
}
