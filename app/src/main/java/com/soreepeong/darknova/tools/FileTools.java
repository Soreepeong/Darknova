package com.soreepeong.darknova.tools;

import android.graphics.Bitmap;

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
		Bitmap decoded = ImageCache.decodeFile(file.getAbsolutePath(), 8192);
		File tempFile = new File(file.getAbsolutePath() + "_resizing");
		OutputStream out = null;
		InputStream in = null;
		int quality = 80;
		try {
			while (quality >= 0) {
				out = new FileOutputStream(tempFile);
				android.util.Log.e("Darknova", "quality " + quality + " @ " + tempFile.getName());
				decoded.compress(Bitmap.CompressFormat.WEBP, quality, out);
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
			decoded.recycle();
			StreamTools.close(in);
			StreamTools.close(out);
			if (tempFile.exists() && !tempFile.delete())
				tempFile.deleteOnExit();
		}
	}
}
