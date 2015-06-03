package com.soreepeong.darknova.core;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import java.util.HashMap;

/**
 * Created by Soreepeong on 2015-06-02.
 */
public class TimedStorage<V> implements Handler.Callback{
	private static final int KEEP_DURATION = 10000; // 10 sec
	private final HashMap<V, Long> mStorage = new HashMap<>();
	private final Handler mHandler;

	public TimedStorage(){
		HandlerThread thread = new HandlerThread("TimedStorage");
		thread.start();
		mHandler = new Handler(thread.getLooper(), this);
	}

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
		res = null;
		return res;
	}

	public void release(V value){
		synchronized (mStorage){
			mStorage.put(value, System.currentTimeMillis());
			mHandler.sendMessageDelayed(Message.obtain(mHandler, 0, value), KEEP_DURATION);
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
