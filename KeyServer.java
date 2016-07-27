package edu.berkeley.cs162;
/**
 * Slave Server component of a KeyValue store
 *
 * @author Prashanth Mohan (http://www.cs.berkeley.edu/~prmohan)
 *
 * Copyright (c) 2011, University of California at Berkeley
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  * Neither the name of University of California, Berkeley nor the
 *    names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL PRASHANTH MOHAN BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
//package edu.berkeley.cs162;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * This class defines the slave key value servers. Each individual KeyServer
 * would be a fully functioning Key-Value server. For Project 3, you would
 * implement this class. For Project 4, you will have a Master Key-Value server
 * and multiple of these slave Key-Value servers, each of them catering to a
 * different part of the key namespace.
 * 
 * @param <K>
 *            Java Generic Type for the Key
 * @param <V>
 *            Java Generic Type for the Value
 */
public class KeyServer<K extends Serializable, V extends Serializable>
		implements KeyValueInterface<K, V> {
	private KVStore<K, V> dataStore = null;
	private KVCache<K, V> dataCache = null;
	HashMap<K, ReentrantReadWriteLock> locks = new HashMap<K, ReentrantReadWriteLock>();
	ReentrantLock outsidelock = new ReentrantLock();

	/**
	 * @param cacheSize
	 *            number of entries in the data Cache.
	 */
	public KeyServer(int cacheSize) {
		dataStore = new KVStore<K, V>();
		 try {
		dataCache = new KVCache<K, V>(cacheSize);
		 } catch (KVException e) {
		 //TODO Auto-generated catch block
	 e.getMsg();
		 }

	}

	public boolean put(K key, V value) throws KVException {
		outsidelock.lock();
		if (!locks.containsKey(key))
			locks.put(key, new ReentrantReadWriteLock());
		outsidelock.unlock();
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		ObjectOutputStream objectstream;
		try {
			objectstream = new ObjectOutputStream(stream);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			throw new KVException(
					new KVMessage("resp", null, null, null,
							"Unknown Error: put: IOException creating object stream in KeyServer.java"));
		}
		try {
			objectstream.writeObject(key);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			throw new KVException(
					new KVMessage("resp", null, null, null,
							"Unknown Error: put: IOException writing object stream in KeyServer.java"));
		}
		byte[] bytes = stream.toByteArray();

		ByteArrayOutputStream valuestream = new ByteArrayOutputStream();
		ObjectOutputStream valueobjectstream;
		try {
			valueobjectstream = new ObjectOutputStream(valuestream);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			throw new KVException(
					new KVMessage("resp", null, null, null,
							"Unknown Error: put: IOException creating valueobjectstream in KeyServer.java"));
		}
		try {
			valueobjectstream.writeObject(value);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			throw new KVException(
					new KVMessage("resp", null, null, null,
							"Unknown Error: put: IOException writing valueobjectstream in KeyServer.java"));
		}
		byte[] valuebytes = valuestream.toByteArray();
		Lock temp;
		if (bytes.length > 256)
			throw new KVException(new KVMessage("resp", null, null, null,
					"Over Sized Key"));
		if (valuebytes.length > 1024 * 128)
			throw new KVException(new KVMessage("resp", null, null, null,
					"Over Sized Value"));

		ReentrantReadWriteLock temp1 = locks.get(key);
		temp = temp1.writeLock();
		temp.lock();
		boolean x;
		try {
			x = dataStore.put(key, value);
		} catch (Exception e) {
			temp.unlock();
			throw new KVException(new KVMessage("resp", null, null, null,
					"IO Error"));

		}
		try {
			dataCache.put(key, value);
		} catch (Exception e) {
			dataStore.put(key, value);
			dataCache.put(key, value);
			temp.unlock();
			throw new KVException(new KVMessage("resp", null, null, null,
					"Unknown Error:put keyserver"));
		}
		temp.unlock();
		return x;
	}

	public V get(K key) throws KVException {
		outsidelock.lock();
		if (!locks.containsKey(key))
			locks.put(key, new ReentrantReadWriteLock());
		outsidelock.unlock();

		// ByteArrayOutputStream stream = new ByteArrayOutputStream();
		// ObjectOutputStream objectstream = new ObjectOutputStream(stream);
		// objectstream.writeObject(value);
		// byte[] bytes = stream.toByteArray();
		// if(bytes.length > 1024 *128)
		// throw new KVException(new
		// KVMessage("resp",null,null,null,"Over Sized Value"));

		Lock temp;
		ReentrantReadWriteLock temp1 = locks.get(key);
		temp = temp1.readLock();
		temp.lock();
		try {

			V t1 = dataCache.get(key);
			System.out.println("got from cache");
			if (t1 != null) {
				temp.unlock();
				return t1;
			}

		} catch (Exception e) {
			temp.unlock();
			throw new KVException(new KVMessage("resp", null, null, null,
					"Unknown Error:get keyserver"));
		}
		V t2;
		try {
			t2 = dataStore.get(key);
		} catch (Exception e) {
			temp.unlock();
			throw new KVException(new KVMessage("resp", null, null, null,
					"IO Error"));

		}
		if (t2 == null) {
			temp.unlock();
			throw new KVException(new KVMessage("resp", null, null, null,
					"Does not exist"));
		}
		temp.unlock();
		temp = temp1.writeLock();
		temp.lock();
		dataCache.put(key, t2);
		temp.unlock();

		return t2;
	}

	@Override
	public void del(K key) throws KVException {
		outsidelock.lock();
		if (!locks.containsKey(key))
			locks.put(key, new ReentrantReadWriteLock());
		outsidelock.unlock();

		V value = dataStore.get(key);
		if (value == null)
			throw new KVException(new KVMessage("resp", null, null, null,
					"Does not exist"));
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		ObjectOutputStream objectstream;
		try {
			objectstream = new ObjectOutputStream(stream);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			throw new KVException(
					new KVMessage("resp", null, null, null,
							"Unknown Error: del: IOException creating objectstream in KeyServer.java"));
		}
		try {
			objectstream.writeObject(value);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			throw new KVException(
					new KVMessage("resp", null, null, null,
							"Unknown Error: del: IOException writing object in KeyServer.java"));
		}
		byte[] bytes = stream.toByteArray();
		if (bytes.length > 1024 * 128)
			throw new KVException(new KVMessage("resp", null, null, null,
					"Over Sized Value"));
		Lock temp;
		ReentrantReadWriteLock temp1 = locks.get(key);
		temp = temp1.writeLock();
		temp.lock();
		try {
			dataStore.del(key);
		} catch (Exception e) {
			temp.unlock();
			throw new KVException(new KVMessage("resp", null, null, null,
					"IO Error"));

		}
		try {
			dataCache.del(key);
		} catch (Exception e) {
			dataStore.put(key, value);
			dataCache.put(key, value);
			temp.unlock();
			throw new KVException(new KVMessage("resp", null, null, null,
					"Unknown Error:del keyserver"));
		}
		temp.unlock();
	}
	public boolean containsKey(K key) throws KVException{
		boolean containsKey = false;
		if (dataCache.get(key) != null) {
			return true;
		} else {
			try {
				if (dataStore.get(key) != null){
					containsKey = true;
				} else {
					containsKey = false;
				}
			} catch (KVException e) {
				throw new KVException(new KVMessage("resp", e.getMsg()
						.getMessage()));
			}
			return containsKey;
		}
	}
}