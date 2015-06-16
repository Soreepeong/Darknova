package com.soreepeong.darknova.core;

import java.util.LinkedList;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Soreepeong
 */
public class ThreadScheduler {
	private final ThreadGroup mThreadGroup;
	private final int mConcurrentActiveLoaders;
	private final LinkedList<Runnable> mPending = new LinkedList<>();
	private final AtomicInteger mActiveCount = new AtomicInteger(0);
	private final WeakHashMap<Runnable, Thread> mRunningThreads = new WeakHashMap<>();

	public ThreadScheduler(int loaderCount, String name) {
		mConcurrentActiveLoaders = loaderCount;
		mThreadGroup = new ThreadGroup(name);
	}

	public synchronized void schedule(Runnable task) {
		if (task != null) {
			mPending.remove(task);
			mPending.addFirst(task);
		}
		while (mActiveCount.get() < mConcurrentActiveLoaders && !mPending.isEmpty()) {
			final Runnable newLoader = mPending.removeFirst();
			mActiveCount.incrementAndGet();
			Thread t = new Thread(mThreadGroup, newLoader, mThreadGroup.getName() + ": " + newLoader.toString()) {
				@Override
				public void run() {
					super.run();
					mActiveCount.decrementAndGet();
					mRunningThreads.remove(newLoader);
					schedule(null);
				}
			};
			mRunningThreads.put(newLoader, t);
			t.start();
		}
	}

	public synchronized void cancel(Runnable task) {
		Thread active = mRunningThreads.get(task);
		if (active != null)
			active.interrupt();
		mPending.remove(task);
		schedule(null);
	}
}
