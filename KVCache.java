package edu.berkeley.cs162;
/**
 * Implementation of an LRU Cache (copied from the Internet)
 * 
 * Copyright (c) 2011, University of California at Berkeley
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  * Neither the name of University of California, Berkeley nor the
 *    names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *    
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL PRASHANTH MOHAN BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
//package edu.berkeley.cs162;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An LRU cache which has a fixed maximum number of elements (cacheSize). If the
 * cache is full and another entry is added, the LRU (least recently used) entry
 * is dropped.
 */
public class KVCache<K extends Serializable, V extends Serializable> implements
		KeyValueInterface<K, V> {
	private final int cacheSize;
	private LinkedHashMap<K, V> cache;
	private HashMap<K, ReentrantLock> keyLocks;
	private ReentrantLock Lock;

	/**
	 * Creates a new LRU cache.
	 * 
	 * @param cacheSize
	 *            the maximum number of entries that will be kept in this cache.
	 * @throws KVException
	 */
	public KVCache(int cacheSize) throws KVException {
		// implement me
		try {
			this.cacheSize = cacheSize;
			cache = new LinkedHashMap<K, V>(cacheSize, (float) 0.75, true);
		} catch (IllegalArgumentException e) {
			throw new KVException(
					new KVMessage("resp", null, null, null,
							"Unknown Error: Illegal Argument Exception in trying to create cache"));
		}
		keyLocks = new HashMap<K, ReentrantLock>();
		Lock = new ReentrantLock();
	}
	
	public int getCacheSize(){
		return this.cacheSize;
	}
	
	public int getNumEntries(){
		return cache.size();
	}

	/**
	 * Retrieves an entry from the cache. The retrieved entry becomes the MRU
	 * (most recently used) entry.
	 * 
	 * @param key
	 *            the key whose associated value is to be returned.
	 * @return the value associated to this key, or null if no value with this
	 *         key exists in the cache.
	 * @throws KVException
	 */
	public V get(K key) {
		// implement me

		V tmpValue = null;
		Lock.lock();
		try {
			try {
				lock(key);
			} catch (KVException e) {
				e.getMsg().getMessage();
			}
		} finally {
			Lock.unlock();
		}
		if (cacheSize > 0 && cache.size() > 0 && cache.containsKey(key)) {
			Lock.lock();
			try {
				tmpValue = cache.get(key);
			} finally {
				Lock.unlock();
			}
		}
		try {
			unlock(key);
		} catch (KVException e) {
			e.getMsg().getMessage();
		}
		return tmpValue;
	}

	/**
	 * Adds an entry to this cache. The new entry becomes the MRU (most recently
	 * used) entry. If an entry with the specified key already exists in the
	 * cache, it is replaced by the new entry. If the cache is full, the LRU
	 * (least recently used) entry is removed from the cache.
	 * 
	 * @param key
	 *            the key with which the specified value is to be associated.
	 * @param value
	 *            a value to be associated with the specified key.
	 * @return
	 */
	public boolean put(K key, V value) {
		// implement me

		boolean overwritten = false;
		Lock.lock();
		try {
			try {
				lock(key);
			} catch (KVException e) {
				e.getMsg().getMessage();
			}
		} finally {
			Lock.unlock();
		}

		while (cacheSize > 0 && reachedMaxCapacity() && !cache.containsKey(key)) {
			Map.Entry<K, V> eldest = cache.entrySet().iterator().next();
			/*
			 * Lock.lock(); try { try { lock(eldest.getKey()); } catch
			 * (KVException e) { e.getMsg(); } } finally { Lock.unlock(); }
			 * cache.remove(eldest);
			 */
			del(eldest.getKey());
		}

		if (cacheSize > 0 && cache.size() <= cacheSize) {
			if (cache.containsKey(key)) {
				overwritten = true;
			}
			Lock.lock();
			try {
				cache.put(key, value);
			} finally {
				Lock.unlock();
			}
		}
		try {
			unlock(key);
		} catch (KVException e) {
			e.getMsg().getMessage();
		}
		return overwritten;
	}

	/**
	 * Removes an entry to this cache.
	 * 
	 * @param key
	 *            the key with which the specified value is to be associated.
	 */
	public void del(K key) {
		// implement me

		Lock.lock();
		try {
			try {
				lock(key);
			} catch (KVException e) {
				e.getMsg().getMessage();
			}
		} finally {
			Lock.unlock();
		}

		if (cache.containsKey(key)) {
			Lock.lock();
			try {
				cache.remove(key);
			} finally {
				Lock.unlock();
			}
		}
		try {
			unlock(key);
		} catch (KVException e) {
			e.printStackTrace();
		}

	}

	public boolean reachedMaxCapacity() {
		return cache.size() >= cacheSize;
	}

	private synchronized void lock(K key) throws KVException {
		ReentrantLock keyLock;
		if (!keyLocks.containsKey(key)) {
			keyLock = new ReentrantLock();
			keyLocks.put(key, keyLock);
		} else {
			keyLock = keyLocks.get(key);
		}
		while (keyLock.isLocked()) {
			try {
				keyLock.wait();
			} catch (InterruptedException e) {
				throw new KVException(
						new KVMessage(
								"resp",
								null,
								null,
								null,
								"Unknown Error: Interrupted Exception in trying to sleep on keyLock in lock(key)"));
			} catch (IllegalMonitorStateException e) {
				throw new KVException(
						new KVMessage(
								"resp",
								null,
								null,
								null,
								"Unknown Error: in KVCache, keyLock.wait: current thread does not own this object's monitor"));
			}
		}
		keyLock.lock();
	}

	private synchronized void unlock(K key) throws KVException {
		ReentrantLock keyLock;
		if (keyLocks.containsKey(key)) {
			keyLock = keyLocks.get(key);
			if (keyLock.isHeldByCurrentThread()) {
				if (keyLock.hasQueuedThreads()) {
					try {
						keyLock.notify();
					} catch (IllegalMonitorStateException e) {
						throw new KVException(
								new KVMessage(
										"resp",
										null,
										null,
										null,
										"Unknown Error: in KVCache, keyLock.notify current thread not owner of this object's monitor"));
					}
				}
				keyLock.unlock();
			}
		}
	}

	public boolean containsKey(K key) {
		return cache.containsKey(key);
	}

} // end class LRUCache
