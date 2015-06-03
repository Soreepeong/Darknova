package com.soreepeong.darknova.core;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Created by Soreepeong on 2015-04-28.
 */
public class WeakValueHashMap<K,V>{
	private HashMap<K,WeakReference<V>> mHashMap = new HashMap<>();
	public synchronized V get(K key){
		WeakReference<V> ref = mHashMap.get(key);
		if(ref == null){
			mHashMap.remove(key);
			return null;
		}
		return ref.get();
	}
	public synchronized void put(K key, V value) {
		mHashMap.put(key, new WeakReference<V>(value));
	}
	public synchronized V remove(K key){
		WeakReference<V> ref = mHashMap.remove(key);
		if(ref == null){
			mHashMap.remove(key);
			return null;
		}
		return ref.get();
	}
	public synchronized HashMap<K,V> getMap(){
		HashMap<K,V> map = new HashMap<>();
		for(K key : mHashMap.keySet()){
			V ref = mHashMap.get(key).get();
			if(ref != null)
				map.put(key, ref);
		}
		return map;
	}
}
