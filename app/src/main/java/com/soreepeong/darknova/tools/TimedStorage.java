package com.soreepeong.darknova.tools;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.Nullable;

import java.util.HashMap;

/**
 * TimedStorage stores some data for some duration, and then evicts it.
 *
 * @author Soreepeong
 */
public class TimedStorage<V> implements Handler.Callback{
	private static final int DEFAULT_KEEP_DURATION = 10000; // 10 sec
	private final HashMap<V, Long> mStorage = new HashMap<>();
	private final Handler mHandler;
	private final int mKeepDuration;

	/**
	 * Create timed storage that stores some data for duration
	 *
	 * @param duration Time in storage
	 */
	public TimedStorage(int duration) {
		HandlerThread thread = new HandlerThread("TimedStorage");
		thread.start();
		mHandler = new Handler(thread.getLooper(), this);
		mKeepDuration = duration;
	}

	/**
	 * Created timed storage tht stores some data for 10 seconds
	 */
	public TimedStorage() {
		this(DEFAULT_KEEP_DURATION);
	}

	/**
	 * Get data in the storage
	 * @return data, null if none found
	 */
	@Nullable
	public V obtain(){
		V res = null;
		long nEarliestModifiedTime = Long.MAX_VALUE;
		synchronized (mStorage){
			for(V v : mStorage.keySet()){
				if(nEarliestModifiedTime > mStorage.get(v)){
					nEarliestModifiedTime = mStorage.get(v);
					mHandler.removeMessages(0, v);
					res = v;
				}
			}
			if(res != null)
				mStorage.remove(res);
		}
		return res;
	}

	/**
	 * Put data in storage
	 * @param value data to store
	 */
	public void release(V value){
		synchronized (mStorage){
			mStorage.put(value, System.currentTimeMillis());
			mHandler.sendMessageDelayed(Message.obtain(mHandler, 0, value), mKeepDuration);
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		synchronized (mStorage){
			mStorage.remove(msg.obj);
		}
		return true;
	}
}
