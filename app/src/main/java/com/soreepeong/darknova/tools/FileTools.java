package com.soreepeong.darknova.tools;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.support.v4.provider.DocumentFile;

import com.soreepeong.darknova.core.ImageCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

/**
 * Compilation of file functions.
 */
public class FileTools {

	public static byte[] readFile(File f, int max_size) {
		FileInputStream in = null;
		if (f.length() > max_size)
			return null;
		byte b[] = new byte[(int) f.length()];
		try {
			in = new FileInputStream(f);
			if (b.length != in.read(b))
				return null;
		} catch (Exception e) {
			return null;
		} finally {
			StreamTools.close(in);
		}
		return b;
	}

	/**
	 * Copies file from {@param sourceFile} to {@param destFile}
	 *
	 * @param sourceFile Original file
	 * @param destFile   Destination file
	 * @throws IOException
	 */
	public static void copyFile(File sourceFile, File destFile) throws IOException {
		FileChannel source = null;
		FileChannel destination = null;
		try {
			source = new FileInputStream(sourceFile).getChannel();
			destination = new FileOutputStream(destFile).getChannel();
			destination.transferFrom(source, 0, source.size());
		} finally {
			if(source != null)
				source.close();
			if (destination != null)
				destination.close();
		}
	}

	public static void resizeImage(File file, long targetLength) throws IOException {
		Bitmap decoded = ImageCache.decodeFile(file.getAbsolutePath(), 8192, 8192, null);
		if (decoded == null)
			throw new RuntimeException("bad file");
		File tempFile = new File(file.getAbsolutePath() + "_resizing");
		OutputStream out = null;
		InputStream in = null;
		int quality = 90;
		try {
			while (quality >= 0) {
				out = new FileOutputStream(tempFile);
				android.util.Log.e("Darknova", "quality " + quality + " @ " + tempFile.getName());
				decoded.compress(Bitmap.CompressFormat.JPEG, quality, out);
				StreamTools.close(out);
				android.util.Log.e("Darknova", "size " + tempFile.length() + " @ " + tempFile.getName());
				if (tempFile.length() <= targetLength)
					break;
				quality -= 10;
			}
			if (!file.delete() || !tempFile.renameTo(file)) {
				if (!tempFile.exists())
					return;
				in = new FileInputStream(tempFile);
				out = new FileOutputStream(file);
				StreamTools.passthroughStreams(in, out);
			}
		} finally {
			StreamTools.close(in);
			StreamTools.close(out);
			if (tempFile.exists() && !tempFile.delete())
				tempFile.deleteOnExit();
		}
	}


	/**
	 * Delete a file. May be even on external SD card.
	 * <p/>
	 * From https://github.com/jeisfeld/Android/tree/master/Augendiagnose
	 *
	 * @param file the file to be deleted.
	 * @return True if successfully deleted.
	 */
	public static boolean deleteFile(File file, Context context, Uri treeUri) {
		Cursor c = null;
		try {
			if (file.delete())
				return true;
			else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				DocumentFile document = getDocumentFile(file, context, treeUri);
				if (document != null)
					return document.delete();
			} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
				ContentResolver resolver = context.getContentResolver();
				try {
					c = resolver.query(MediaStore.Files.getContentUri("external"), new String[]{BaseColumns._ID}, MediaStore.MediaColumns.DATA + "=?", new String[]{file.getAbsolutePath()}, null);
					if (c.moveToFirst()) {
						resolver.delete(MediaStore.Files.getContentUri("external").buildUpon().appendPath(Integer.toString(c.getInt(0))).build(), null, null);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return !file.exists();
		} finally {
			if (c != null)
				c.close();
		}
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private static DocumentFile getDocumentFile(File file, Context context, Uri treeUri) {
		if (treeUri == null)
			return null;
		String fullPath;
		try {
			fullPath = file.getCanonicalPath();
		} catch (Exception e) {
			return null;
		}
		wholeLoop:
		for (File dir : context.getExternalFilesDirs("external")) {
			try {
				if (dir != null && !dir.equals(context.getExternalFilesDir("external"))) {
					int index = dir.getAbsolutePath().lastIndexOf("/Android/data");
					if (index >= 0) {
						String path = new File(dir.getAbsolutePath().substring(0, index)).getCanonicalPath();
						if (fullPath.startsWith(path)) {
							String relativePath = fullPath.substring(path.length() + 1);
							DocumentFile document = DocumentFile.fromTreeUri(context, treeUri);
							for (String segment : relativePath.split("/")) {
								DocumentFile nextDocument = document.findFile(segment);
								if (nextDocument == null)
									continue wholeLoop;
								document = nextDocument;
							}
							return document;
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}
