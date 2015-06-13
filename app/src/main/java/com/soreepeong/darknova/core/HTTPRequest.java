package com.soreepeong.darknova.core;

import android.os.Looper;
import android.support.annotation.Nullable;

import com.soreepeong.darknova.extractors.ImageExtractor;
import com.soreepeong.darknova.tools.ArrayTools;
import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.tools.StringTools;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Wrapper for HttpURLConnection made because original one won't close immediately
 *
 * @author Soreepeong
 */
public class HTTPRequest {
	private static final byte[] HTTP_HEADER_SEPARATOR = new byte[]{'\r', '\n', '\r', '\n'};
	private static final int[] HTTP_HEADER_SEPARATOR_FAILURE = ArrayTools.computeFailure(HTTP_HEADER_SEPARATOR);
	private static final int BYTE_BUFFER_SIZE = 2048;
	private static final int MAX_HEADER_SIZE = 65536;
	private static final Pattern URI_BASIC_PATTERN = Pattern.compile("^[a-z0-9]+:.*$", Pattern.CASE_INSENSITIVE);
	private final boolean mIsPostRequest, mIsMultipartRequest;
	private final String mMultipartBoundary;
	private final String mPostData;
	private HttpURLConnection mConnection;
	private ArrayList<MultipartPart> arrMultipart;
	private Exception mLastError;
	private boolean mRequested;
	private HashMap<String, String> mRequestHeaders = new HashMap<>();
	private OAuth mAuth;
	private long mPosted = 0, mPostSize = 0;
	private InputStream mInputStream;
	private OutputStream mOutputStream;
	private int mMaxRedirects = 8;

	private HTTPRequest(OAuth auth, boolean bPost, String sPostData) throws IOException {
		mMultipartBoundary = "MultipartBoundary" + StringTools.getRandomString(32);
		mIsPostRequest = bPost;
		mPostData = sPostData;
		mAuth = auth;
		mIsMultipartRequest = mIsPostRequest && mPostData == null;
		if (mIsMultipartRequest) {
			arrMultipart = new ArrayList<>();
		}
	}

	/**
	 * Get HTTP Request prepared
	 *
	 * @param sUrl      URL
	 * @param auth      oauth, if available
	 * @param bPost     Is this POST request?
	 * @param sPostData Use multipart format if null
	 * @return Newly created HTTP Request
	 */
	@Nullable
	public static HTTPRequest getRequest(String sUrl, OAuth auth, boolean bPost, String sPostData) {
		try {
			HTTPRequest ret = new HTTPRequest(auth, bPost, sPostData);
			ret.initHttp(sUrl);
			return ret;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public void setMaxRedirects(int n) {
		mMaxRedirects = n;
	}

	private void initHttp(String sUrl) throws IOException {
		if (!URI_BASIC_PATTERN.matcher(sUrl).matches())
			sUrl = "http://" + sUrl;
		mConnection = (HttpURLConnection) new URL(sUrl).openConnection();
		mConnection.setUseCaches(false);
		mConnection.setDoInput(true);
		mConnection.setInstanceFollowRedirects(false);
		mConnection.setConnectTimeout(30000);
		mConnection.setReadTimeout(30000);
		mConnection.setRequestProperty("Connection", "Close");
		mConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (darknova)");
		if (mAuth != null)
			mConnection.setRequestProperty("Authorization", mAuth.getHeader(mIsPostRequest, sUrl, mPostData == null || mPostData.isEmpty() ? null : mPostData.split("&")));
		if (mIsPostRequest) {
			mConnection.setRequestMethod("POST");
		} else {
			mConnection.setRequestMethod("GET");
		}
		mLastError = null;
	}

	/**
	 * Submit request immediately
	 *
	 * @return True if succeed
	 */
	public boolean submitRequest() {
		mRequested = true;
		int nRedirects = mMaxRedirects;
		while (nRedirects-- > 0 && !Thread.interrupted()) {
			try {
				for (String sName : mRequestHeaders.keySet())
					mConnection.setRequestProperty(sName, mRequestHeaders.get(sName));
				if (mIsPostRequest) {
					mConnection.setDoOutput(true);
					calculatePostSize();
					mConnection.setRequestProperty("Content-Length", Long.toString(mPostSize));
					mConnection.setFixedLengthStreamingMode((int) mPostSize);
					if (mIsMultipartRequest) {
						mConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + mMultipartBoundary);
						mOutputStream = mConnection.getOutputStream();
						putMultipartRequest();
					} else {
						mConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
						mOutputStream = mConnection.getOutputStream();
						recordAndWrite(mPostData);
					}
					mOutputStream.flush();
					mOutputStream = StreamTools.close(mOutputStream);
				}
				if (Thread.interrupted())
					return false;
				if (getStatusCode() / 100 == 3) { // Check if it redirects
					String sUrl = mConnection.getHeaderField("location");
					mConnection.disconnect();
					if (sUrl == null)
						return false;
					initHttp(sUrl);
				} else
					return true;
			} catch (IOException e) {
				mLastError = e;
				break;
			} finally {
				StreamTools.close(mOutputStream);
			}
		}
		return false;
	}

	/**
	 * Set read timeout
	 *
	 * @param t Timeout
	 */
	public void setReadTimeout(int t) {
		mConnection.setReadTimeout(t);
	}

	/**
	 * Set connect timeout
	 *
	 * @param t Timeout
	 */
	public void setConnectTimeout(int t) {
		mConnection.setConnectTimeout(t);
	}

	private void checkRequested() {
		if (!mRequested)
			throw new RuntimeException("Not requested yet.");
	}

	/**
	 * Add request HTTP header
	 *
	 * @param sName  Header name
	 * @param sValue Header value
	 */
	public void addRequestHeader(String sName, String sValue) {
		mRequestHeaders.put(sName, sValue);
	}

	/**
	 * Get URL, after redirection, if requested.
	 *
	 * @return The URL
	 */
	public String getUrl() {
		return mConnection.getURL().toExternalForm();
	}

	/**
	 * Get last error occured.
	 *
	 * @return Last error. Null if no error has occured.
	 */
	public Exception getLastError() {
		return mLastError;
	}

	/**
	 * Get status code.
	 *
	 * @return HTTP Status Code. Zero if the server didn't respond.
	 */
	public int getStatusCode() {
		checkRequested();
		try {
			return mConnection.getResponseCode();
		} catch (Exception e) {
			mLastError = e;
			return 0;
		}
	}

	/**
	 * Get post progress in bytes
	 *
	 * @return sent bytes
	 */
	public long getPostProgress() {
		return mPosted;
	}

	/**
	 * Get post progress max in bytes
	 *
	 * @return bytes sent and to send
	 */
	public long getPostLength() {
		return mPostSize;
	}

	/**
	 * Get content type.
	 *
	 * @return Content type.
	 */
	public String getContentType() {
		checkRequested();
		return mConnection.getContentType();
	}

	/**
	 * Get input length.
	 *
	 * @return Length.
	 */
	public long getInputLength() {
		checkRequested();
		return mConnection.getContentLength();
	}

	/**
	 * Get buffered input stream
	 *
	 * @return Input stream for the response
	 */
	public InputStream getInputStream() {
		return getInputStream(true);
	}

	/**
	 * Get input stream
	 *
	 * @param bUseBufferedStream Wrap the stream in {@see BufferedInputStream}
	 * @return Input stream for the response
	 */
	public InputStream getInputStream(boolean bUseBufferedStream) {
		InputStream in;
		checkRequested();
		try {
			try {
				in = mConnection.getInputStream();
			} catch (IOException e) {
				in = mConnection.getErrorStream();
				if (in == null)
					throw e;
			}
			if (bUseBufferedStream)
				in = new BufferedInputStream(in);
			mInputStream = in;
			return in;
		} catch (Exception e) {
			mLastError = e;
			return null;
		}
	}

	/**
	 * Read all data
	 *
	 * @return all data
	 */
	public String getWholeData() {
		return getWholeData(-1);
	}

	/**
	 * Read all data up to {@param readUpTo}
	 *
	 * @param readUpTo Maximum length of bytes to read
	 * @return data read
	 */
	public String getWholeData(int readUpTo) {
		checkRequested();
		InputStream in = null;
		int bytesRead;
		UniversalDetector detector = new UniversalDetector(null);
		byte[] buffer = new byte[readUpTo == -1 ? BYTE_BUFFER_SIZE : readUpTo];
		ByteArrayOutputStream bos = new ByteArrayOutputStream(buffer.length);
		try {
			try {
				in = mConnection.getInputStream();
			} catch (IOException e) {
				in = mConnection.getErrorStream();
				if (in == null)
					throw e;
			}
			while ((readUpTo == -1 || bos.size() < readUpTo || (bos.size() < readUpTo * 10 && !detector.isDone())) && (bytesRead = in.read(buffer)) >= 0) {
				bos.write(buffer, 0, bytesRead);
				if (!detector.isDone())
					detector.handleData(buffer, 0, bytesRead);
			}
			detector.dataEnd();
			String charset = detector.getDetectedCharset();
			return new String(bos.toByteArray(), charset == null ? "UTF-8" : charset);
		} catch (Exception e) {
			mLastError = e;
		} finally {
			StreamTools.close(in);
			detector.reset();
		}
		return new String(bos.toByteArray());
	}

	/**
	 * Get response header
	 *
	 * @param sName header key
	 * @return header value
	 */
	public String getHeader(String sName) {
		checkRequested();
		return mConnection.getHeaderField(sName);
	}

	/**
	 * Add parameter for multipart post request
	 *
	 * @param sName  parameter key
	 * @param sValue parameter value
	 */
	public void addMultipartParameter(String sName, String sValue) {
		arrMultipart.add(new MultipartPart(sName, sValue));
	}

	/**
	 * Add parameter for multipart post request
	 *
	 * @param sName parameter key
	 * @param file  parameter file
	 */
	public void addMultipartFileParameter(String sName, File file) {
		arrMultipart.add(new MultipartPart(sName, file, 0, file.length()));
	}

	/**
	 * Add parameter for multipart post request
	 *
	 * @param sName parameter key
	 * @param file  parameter file
	 * @param from  read file from
	 * @param to    read file to
	 */
	public void addMultipartFileParameter(String sName, File file, long from, long to) {
		arrMultipart.add(new MultipartPart(sName, file, from, to));
	}

	private void recordAndWrite(byte[] b, int offset, int count) throws IOException {
		mPosted += count;
		mOutputStream.write(b, offset, count);
	}

	private void recordAndWrite(byte[] b) throws IOException {
		mPosted += b.length;
		mOutputStream.write(b);
	}

	private void recordAndWrite(String s) throws IOException {
		recordAndWrite(s.getBytes());
	}

	private void calculatePostSize() {
		if (mIsMultipartRequest) {
			mPostSize = 0;
			for (MultipartPart pt : arrMultipart)
				mPostSize += mMultipartBoundary.length() + pt.partLength + 6;
			mPostSize += 4 + mMultipartBoundary.length() + 2;
		} else {
			mPostSize = mPostData.getBytes().length;
		}
	}

	private void putMultipartRequest() throws IOException {
		for (MultipartPart pt : arrMultipart) {
			if (Thread.interrupted()) return;
			recordAndWrite("--" + mMultipartBoundary + "\r\n");
			pt.writePost();
			recordAndWrite("\r\n");
		}
		recordAndWrite("--" + mMultipartBoundary + "--\r\n");
		mOutputStream.flush();
	}

	public void close() {
		if (Looper.getMainLooper() == Looper.myLooper()) {
			new Thread() {
				@Override
				public void run() {
					close();
				}
			}.start();
			return;
		}
		StreamTools.close(mInputStream);
		StreamTools.close(mOutputStream);
		mConnection.disconnect();
	}

	private static class HeaderInspectResult {
		public final ArrayList<String[]> mHeaders = new ArrayList<>();
		public String mRawHeader;
		public String mFirstLine;
		public String mMethod, mLocation;

		public HeaderInspectResult(PushbackInputStream in) throws IOException {
			final byte buffer[] = new byte[MAX_HEADER_SIZE];
			int read = 0;
			int search = 0;
			int position = 0;
			while (!Thread.interrupted() && -1 != (read = in.read(buffer, position, buffer.length - position))) {
				position += read;
				if (0 <= (search = ArrayTools.indexOf(buffer, search, position, HTTP_HEADER_SEPARATOR, HTTP_HEADER_SEPARATOR_FAILURE)))
					break;
				if (position >= buffer.length)
					throw new IOException("Header not received");
				search = Math.max(0, position - HTTP_HEADER_SEPARATOR.length + 1);
			}
			if (read == 0 || Thread.interrupted())
				throw new IOException("Operation cancelled");
			in.unread(buffer, search + HTTP_HEADER_SEPARATOR.length, position - search - HTTP_HEADER_SEPARATOR.length);
			mRawHeader = new String(buffer, 0, search);
			for (String s : mRawHeader.split("\r\n")) {
				if (mFirstLine == null)
					mFirstLine = s;
				else if (s.contains(":")) {
					String sKey = s.substring(0, s.indexOf(":"));
					String sValue = s.substring(sKey.length() + 2);
					mHeaders.add(new String[]{sKey, sValue});
				}
			}
			mMethod = mFirstLine.substring(0, mFirstLine.indexOf(" "));
			mLocation = mFirstLine.substring(mMethod.length() + 1, mFirstLine.lastIndexOf(" "));
		}

		public String getHeader(String key) {
			for (String[] h : mHeaders)
				if (h[0].equalsIgnoreCase(key))
					return h[1];
			return null;
		}

		public String getHeadersWithoutProxy() {
			StringBuilder s = new StringBuilder();
			for (String[] h : mHeaders)
				if (!h[0].toLowerCase(Locale.ENGLISH).startsWith("proxy-") && !h[0].equals("connection"))
					s.append(h[0]).append(": ").append(h[1]).append("\r\n");
			return s.toString();
		}
	}

	private class MultipartPart {
		static final int TYPE_STRING = 1, TYPE_FILE = 2;
		final int partType;
		final String paramName;
		final String paramData;
		final String paramFileName;
		final File file;
		final long fileLength, partLength, fileFrom;

		public MultipartPart(String name, File f, long from, long to) {
			partType = TYPE_FILE;
			paramName = name;
			file = f;
			paramData = f.getAbsolutePath();
			String fileName = paramData;
			if (fileName.contains("/"))
				fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
			fileName = ImageExtractor.mFileNameReplacer.matcher(fileName).replaceAll("_");
			if (fileName.length() == 0)
				fileName = StringTools.getSafeRandomString(16);
			paramFileName = fileName;
			fileLength = to - from;
			fileFrom = from;
			partLength = paramName.getBytes().length + paramFileName.getBytes().length + Long.toString(fileLength).length() + fileLength + 149;
		}

		public MultipartPart(String name, String value) {
			this.partType = TYPE_STRING;
			this.paramName = name;
			this.paramData = value;
			file = null;
			paramFileName = null;
			fileLength = 0;
			fileFrom = 0;
			partLength = paramName.getBytes().length + Integer.toString(paramData.getBytes().length).length() + paramData.getBytes().length + 61;
		}

		public void writePost() throws IOException {
			byte[] buffer = new byte[BYTE_BUFFER_SIZE];
			switch (partType) {
				case TYPE_FILE: {
					android.util.Log.d("HTTPRequest", "Multipart " + paramName + " (" + fileLength + ": " + fileFrom + "~)");
					recordAndWrite("Content-Disposition: form-data; name=\"" + paramName + "\"; filename=\"" + paramFileName + "\"\r\n" + "Content-Length: " + fileLength + "\r\n" + "Content-Transfer-Encoding: binary\r\n" + "Content-Type: application/octet-stream\r\n" + "\r\n");
					InputStream in = null;
					try {
						in = new FileInputStream(file);
						int bytesRead;
						long left = fileLength;
						in.skip(fileFrom);
						while (!Thread.interrupted() && left > 0 && (bytesRead = in.read(buffer)) > 0) {
							bytesRead = (int) Math.min(left, bytesRead);
							left -= bytesRead;
							recordAndWrite(buffer, 0, bytesRead);
						}
					} finally {
						StreamTools.close(in);
					}
					return;
				}
				case MultipartPart.TYPE_STRING:
					recordAndWrite("Content-Disposition: form-data; name=\"" + (paramName) + "\"\r\n" + "Content-Length: " + paramData.getBytes().length + "\r\n" + "\r\n" + paramData);
			}
		}
	}

}