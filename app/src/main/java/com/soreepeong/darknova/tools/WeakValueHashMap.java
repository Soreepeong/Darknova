package com.soreepeong.darknova.tools;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Minimal map-like object that stores value using WeakReference
 *
 * @author Soreepeong
 */
public class WeakValueHashMap<K, V> {
	private HashMap<K, WeakReference<V>> mHashMap = new HashMap<>();

	public synchronized V get(K key) {
		WeakReference<V> ref = mHashMap.get(key);
		if (ref == null) {
			mHashMap.remove(key);
			return null;
		}
		return ref.get();
	}

	public synchronized void put(K key, V value) {
		mHashMap.put(key, new WeakReference<V>(value));
	}

	public synchronized V remove(K key) {
		WeakReference<V> ref = mHashMap.remove(key);
		if (ref == null) {
			mHashMap.remove(key);
			return null;
		}
		return ref.get();
	}

	public synchronized HashMap<K, V> getMap() {
		HashMap<K, V> map = new HashMap<>();
		for (K key : mHashMap.keySet()) {
			V ref = mHashMap.get(key).get();
			if (ref != null)
				map.put(key, ref);
		}
		return map;
	}
}
