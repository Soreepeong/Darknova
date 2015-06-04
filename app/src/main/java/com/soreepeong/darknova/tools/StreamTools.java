package com.soreepeong.darknova.tools;

import android.os.Looper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Soreepeong
 */
public class StreamTools {

	/**
	 * Close a stream whatever state it is
	 *
	 * @param stream Stream to close
	 * @return null
	 */
	public static InputStream close(final InputStream stream) {
		if (stream != null) {
			if (Looper.getMainLooper() == Looper.myLooper()) {
				new Thread() {
					@Override
					public void run() {
						try {
							stream.close();
						} catch (IOException e) {
							// whatever
						}
					}
				}.start();
				return null;
			}
			try {
				stream.close();
			} catch (IOException e) {
				// whatever
			}
		}
		return null;
	}

	/**
	 * Close a stream whatever state it is
	 *
	 * @param stream Stream to close
	 * @return null
	 */
	public static OutputStream close(final OutputStream stream) {
		if (stream != null) {
			if (Looper.getMainLooper() == Looper.myLooper()) {
				new Thread() {
					@Override
					public void run() {
						try {
							stream.close();
						} catch (IOException e) {
							// whatever
						}
					}
				}.start();
				return null;
			}
			try {
				stream.close();
			} catch (IOException e) {
				// whatever
			}
		}
		return null;
	}

	/**
	 * Close socket whatever state it is
	 *
	 * @param socket socket to close
	 * @return null
	 */
	public static Socket close(final Socket socket) {
		if (socket != null) {
			if (Looper.getMainLooper() == Looper.myLooper()) {
				new Thread() {
					@Override
					public void run() {
						try {
							socket.close();
						} catch (IOException e) {
							// whatever
						}
					}
				}.start();
				return null;
			}
			try {
				socket.close();
			} catch (IOException e) {
				// whatever
			}
		}
		return null;
	}


	/**
	 * Close socket whatever state it is
	 *
	 * @param socket socket to close
	 * @return null
	 */
	public static ServerSocket close(final ServerSocket socket) {
		if (socket != null) {
			if (Looper.getMainLooper() == Looper.myLooper()) {
				new Thread() {
					@Override
					public void run() {
						try {
							socket.close();
						} catch (IOException e) {
							// whatever
						}
					}
				}.start();
				return null;
			}
			try {
				socket.close();
			} catch (IOException e) {
				// whatever
			}
		}
		return null;
	}


	/**
	 * Interrupt, and stop thread
	 *
	 * @param t thread to interrupt
	 * @return null
	 */
	public static Thread interruptStop(Thread t) {
		if (t == null) return null;
		t.interrupt();
		return null;
	}

	/**
	 * Write whatever was read from in to out
	 *
	 * @param in  InputStream to read from
	 * @param out OutputStream to write to
	 * @throws IOException
	 */
	public static void passthroughStreams(InputStream in, OutputStream out) throws IOException {
		int bytesRead;
		byte buffer[] = new byte[8192];
		while (!Thread.interrupted() && (bytesRead = in.read(buffer)) > 0)
			out.write(buffer, 0, bytesRead);
		close(in);
		close(out);
	}

	/**
	 * Jump current JSON Object or Array
	 *
	 * @param parser JsonParser
	 * @throws IOException
	 */
	public static void consumeJsonValue(JsonParser parser) throws IOException {
		if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
			while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_ARRAY)
				consumeJsonValue(parser);
		} else if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
			while (!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT) {
				parser.nextToken(); // Field Name
				consumeJsonValue(parser);
			}
		}
	}
}
