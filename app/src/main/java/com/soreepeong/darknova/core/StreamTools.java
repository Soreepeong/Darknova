package com.soreepeong.darknova.core;

import android.os.Looper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by Soreepeong on 2015-04-27.
 */
public class StreamTools {

	/**
	 * Read to buffer as much as possible, correctly dealing whether in is
	 * BufferedInputStream
	 *
	 * @param in
	 *            InputStream to read from.
	 * @param buffer
	 *            Byte array to write.
	 * @return Length read; -1 if nothing was read.
	 * @throws IOException
	 */
	public static int readToBuffer(InputStream in, byte[] buffer) throws IOException {
		int bytesRead;
		if(in instanceof BufferedInputStream)
			bytesRead = Math.min(buffer.length, Math.max(1, in.available()));
		else
			bytesRead = buffer.length;
		return in.read(buffer, 0, bytesRead);
	}

	/**
	 * Read to buffer as much as possible
	 *
	 * @param in
	 *            InputStream to read from.
	 * @param buffer
	 *            Byte array to write.
	 * @return Length read; -1 if nothing was read.
	 * @throws IOException
	 */
	public static int readToBuffer(InputStreamReader in, char[] buffer) throws IOException{
		return in.read(buffer, 0, buffer.length);
	}

	/**
	 * Close a stream whatever state it is
	 * @param stream Stream to close
	 */
	public static InputStream close(final InputStream stream){
		if(stream != null){
			if(Looper.getMainLooper() == Looper.myLooper()){
				new Thread(){
					@Override
					public void run() {
						try{
							stream.close();
						}catch(IOException e){
							// whatever
						}
					}
				}.start();
				return null;
			}
			try{
				stream.close();
			}catch(IOException e){
				// whatever
			}
		}
		return null;
	}

	/**
	 * Close a stream whatever state it is
	 * @param stream Stream to close
	 */
	public static OutputStream close(final OutputStream stream){
		if(stream != null){
			if(Looper.getMainLooper() == Looper.myLooper()){
				new Thread(){
					@Override
					public void run() {
						try{
							stream.close();
						}catch(IOException e){
							// whatever
						}
					}
				}.start();
				return null;
			}
			try{
				stream.close();
			}catch(IOException e){
				// whatever
			}
		}
		return null;
	}

	public static Socket close(final Socket socket){
		if(socket != null){
			if(Looper.getMainLooper() == Looper.myLooper()){
				new Thread(){
					@Override
					public void run() {
						try{
							socket.close();
						}catch(IOException e){
							// whatever
						}
					}
				}.start();
				return null;
			}
			try{
				socket.close();
			}catch(IOException e){
				// whatever
			}
		}
		return null;
	}

	public static ServerSocket close(final ServerSocket socket){
		if(socket != null){
			if(Looper.getMainLooper() == Looper.myLooper()){
				new Thread(){
					@Override
					public void run() {
						try{
							socket.close();
						}catch(IOException e){
							// whatever
						}
					}
				}.start();
				return null;
			}
			try{
				socket.close();
			}catch(IOException e){
				// whatever
			}
		}
		return null;
	}

	public static Thread interruptStop(Thread t){
		if(t == null) return null;
		t.interrupt();
		return null;
	}

	public static void passthroughStreams(InputStream in, OutputStream out) throws IOException{
		int bytesRead;
		byte buffer[] = new byte[8192];
		while(!Thread.interrupted() && (bytesRead = in.read(buffer)) > 0)
			out.write(buffer, 0, bytesRead);
	}

	public static void consumeJsonValue(JsonParser parser) throws IOException{
		if(parser.getCurrentToken() == JsonToken.START_ARRAY){
			while(!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_ARRAY)
				consumeJsonValue(parser);
		}else if(parser.getCurrentToken() == JsonToken.START_OBJECT){
			while(!Thread.currentThread().isInterrupted() && parser.nextToken() != JsonToken.END_OBJECT){
				parser.nextToken(); // Field Name
				consumeJsonValue(parser);
			}
		}
	}
}
