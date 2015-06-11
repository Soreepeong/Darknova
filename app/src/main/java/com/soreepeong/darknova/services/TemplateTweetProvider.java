package com.soreepeong.darknova.services;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.File;

/**
 * @author Soreepeong
 */
public class TemplateTweetProvider extends ContentProvider {
	public static final Uri URI_BASE = Uri.parse("content://com.soreepeong.darknova.services.TemplateTweetProvider/");
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;
	public static final int MEDIA_TYPE_GIF = 3;
	public static final int TEMPLATE_TYPE_ONESHOT = 1;
	public static final int TEMPLATE_TYPE_SCHEDULED = 2; // startTime
	public static final int TEMPLATE_TYPE_PERIODIC = 3; // Interval, startTime, endTime
	public static final int TEMPLATE_TYPE_REPLY = 4; // startTime, endTime, Trigger, isRegex
	private static final String TABLE_TEMPLATES = "template_tweets";
	public static final Uri URI_TEMPLATES = Uri.parse("content://com.soreepeong.darknova.services.TemplateTweetProvider/" + TABLE_TEMPLATES);
	private static final String TABLE_FROM_USERS = "from_users";
	public static final Uri URI_FROM_USERS = Uri.parse("content://com.soreepeong.darknova.services.TemplateTweetProvider/" + TABLE_FROM_USERS);
	private static final String TABLE_ATTACHMENTS = "attachments";
	public static final Uri URI_ATTACHMENTS = Uri.parse("content://com.soreepeong.darknova.services.TemplateTweetProvider/" + TABLE_ATTACHMENTS);
	private SQLiteDatabase mDb;
	private File mAttachmentDir;

	@Override
	public boolean onCreate() {
		mAttachmentDir = new File(getContext().getFilesDir(), "attachment");
		if (!mAttachmentDir.exists())
			mAttachmentDir.mkdirs();
		mDb = SQLiteDatabase.openOrCreateDatabase(new File(getContext().getFilesDir(), "template-tweets.db"), null);
		mDb.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TEMPLATES + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, type INTEGER DEFAULT 0, created_at INTEGER, enabled INTEGER DEFAULT 0, remove_after INTEGER DEFAULT 0, interval INTEGER DEFAULT 0, selection_start INTEGER DEFAULT 0, selection_end INTEGER DEFAULT 0, start_time INTEGER DEFAULT  0, end_time INTEGER DEFAULT  0, trigger_pattern TEXT, use_regex INTEGER DEFAULT 0, text TEXT, in_reply_to INTEGER DEFAULT 0, latitude REAL DEFAULT 0, longitude REAL DEFAULT 0, use_coordinates INTEGER DEFAULT 0, autoresolve_coordinates INTEGER DEFAULT 0)");
		mDb.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FROM_USERS + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, template_id INTEGER, user_id INTEGER)");
		mDb.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ATTACHMENTS + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, template_id INTEGER, original_url TEXT, media_type INTEGER)");
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		return mDb.query(uri.getLastPathSegment(), projection, selection, selectionArgs, null, null, sortOrder);
	}

	@Override
	public String getType(Uri uri) {
		if (uri.equals(URI_ATTACHMENTS))
			return mAttachmentDir.getAbsolutePath();
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		long id = mDb.insert(uri.getLastPathSegment(), null, values);
		if (uri.equals(URI_ATTACHMENTS)) {
			File f = new File(mAttachmentDir, Long.toString(id));
			if (f.exists())
				f.delete();
		}
		Uri result = Uri.parse(uri.toString() + "/" + id);
		getContext().getContentResolver().notifyChange(result, null);
		return result;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Cursor c = mDb.query(uri.getLastPathSegment(), null, selection, selectionArgs, null, null, null);
		if (c.moveToFirst()) {
			do {
				if (uri.equals(URI_ATTACHMENTS)) {
					File f = new File(mAttachmentDir, c.getString(c.getColumnIndex("_id")));
					if (f.exists() && !f.delete())
						f.deleteOnExit();
				} else if (uri.equals(URI_TEMPLATES)) {
					delete(URI_ATTACHMENTS, "template_id=?", new String[]{c.getString(c.getColumnIndex("_id"))});
					delete(URI_FROM_USERS, "template_id=?", new String[]{c.getString(c.getColumnIndex("_id"))});
				}
			} while (c.moveToNext());
		}
		c.close();

		int result = mDb.delete(uri.getLastPathSegment(), selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);
		return result;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		int result = mDb.update(uri.getLastPathSegment(), values, selection, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);
		return result;
	}
}
