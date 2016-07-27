package edu.berkeley.cs162;
//package edu.berkeley.cs162;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;

import junit.framework.TestCase;

public class KVCacheTest<K extends Serializable,V extends Serializable> extends TestCase {
	KVCache<K,V> a = null;
	
	public void testConstructorAndGet(){
		String k1 = "1", k2 = "2", k3 = "3", k4= "4", k5= "5", k6 = "6", k7 = "7";
		String v1 = "1", v2 = "2", v3 = "3", v4 = "4", v5= "5", v6 = "6", v7 = "7";
		KVCache a = null;
		try {
			a = new KVCache<K,V>(5);
		} catch (KVException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assertTrue(a!=null);
		
		//testing first put of valid key and value
		assertFalse(a.put((K)k1, (V) v1));
		
		//testing first get of valid key
		assertEquals(a.get((K)k1),v1);
		
		//testing second get of valid key
		assertTrue(a.get((K) k1).equals(v1));
		
		//testing get of invalid key
		String badkey = "boo";
		assertNull(a.get((K)badkey));
		
		//testing second put of valid key and value
		assertFalse(a.put((K)k2, (V) v2 ));
		
		//testing gets of all valid keys
		assertEquals(a.get((K)k1), v1);
		assertEquals(a.get((K)k2), v2);
		assertTrue(a.put((K)k1, (V)v2));
		assertEquals(a.get((K)k1), v2);
		
		
	}
	
	public void testLRU(){
		String k1 = "1", k2 = "2", k3 = "3", k4= "4", k5= "5", k6 = "6", k7 = "7";
		String v1 = "1", v2 = "2", v3 = "3", v4 = "4", v5= "5", v6 = "6", v7 = "7";
		KVCache a = null;
		try {
			a = new KVCache<K,V>(5);
		} catch (KVException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assertTrue(a!=null);
		assertFalse(a.put((K)k1, (V)v1));
		assertFalse(a.put((K)k2, (V)v2));
		assertFalse(a.put((K)k3, (V)v3));
		assertFalse(a.put((K)k4, (V)v4));
		assertFalse(a.put((K)k5, (V)v5));
		assertTrue(a.containsKey((K)k1));
		assertTrue(a.containsKey((K)k2));
		assertTrue(a.containsKey((K)k3));
		assertTrue(a.containsKey((K)k4));
		assertTrue(a.containsKey((K)k5));
		
		//inserting extra key beyond cacheSize; checks k1 removed
		assertFalse(a.put((K)k6, (V)v6));
		assertFalse(a.containsKey((K)k1));
		assertTrue(a.containsKey((K)k2));
		assertTrue(a.containsKey((K)k3));
		assertTrue(a.containsKey((K)k4));
		assertTrue(a.containsKey((K)k5));
		assertTrue(a.containsKey((K)k6));
		
		//access k2 (MRU), k3 (LRU), insert key beyond cacheSize
		a.get((K) k2);
		assertFalse(a.put((K) k7, (V)v7));
		assertFalse(a.containsKey((K)k1));
		assertTrue(a.containsKey((K)k2));
		assertFalse(a.containsKey((K)k3));
		assertTrue(a.containsKey((K)k4));
		assertTrue(a.containsKey((K)k5));
		assertTrue(a.containsKey((K)k6));
		assertTrue(a.containsKey((K)k7));
		
		//attempt to get a key that should no longer be entered
		assertNull(a.get((K) k3));
		
		//access k5, k4 (MRU), put new value for already existing key, should not del LRU (k6)
		a.get((K) k5);
		a.get((K) k4);
		System.out.println(a.getNumEntries());
		assertTrue(a.put((K) k7, (V)v1));
		assertFalse(a.containsKey((K)k1));
		assertTrue(a.containsKey((K)k2));
		assertFalse(a.containsKey((K)k3));
		assertTrue(a.containsKey((K)k4));
		assertTrue(a.containsKey((K)k5));
		assertTrue(a.containsKey((K)k6));
		assertTrue(a.containsKey((K)k7));
		//check new value was properly stored
		assertEquals(a.get((K)k7), v1);
		
		//replacing two existing entries, no deletions
		assertTrue(a.put((K) k7, (V)v1));
		assertTrue(a.put((K)k7,(V)v7));
		assertFalse(a.containsKey((K)k1));
		assertTrue(a.containsKey((K)k2));
		assertFalse(a.containsKey((K)k3));
		assertTrue(a.containsKey((K)k4));
		assertTrue(a.containsKey((K)k5));
		assertTrue(a.containsKey((K)k6));
		assertTrue(a.containsKey((K)k7));
		
		//puts in two new entries, removes two entries k6, then k2
		assertFalse(a.put((K)k1, (V)v1));
		assertFalse(a.put((K)k3, (V)v3));
		assertTrue(a.containsKey((K)k1));
		assertFalse(a.containsKey((K)k2));
		assertTrue(a.containsKey((K)k3));
		assertTrue(a.containsKey((K)k4));
		assertTrue(a.containsKey((K)k5));
		assertFalse(a.containsKey((K)k6));
		assertTrue(a.containsKey((K)k7));
	}
	
	public void testDel(){
		String k1 = "1", k2 = "2", k3 = "3", k4= "4", k5= "5", k6 = "6", k7 = "7";
		String v1 = "1", v2 = "2", v3 = "3", v4 = "4", v5= "5", v6 = "6", v7 = "7";
		KVCache a = null;
		try {
			a = new KVCache<K,V>(5);
		} catch (KVException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assertTrue(a!=null);
		
		//insert 1 key, delete 1 key
		assertFalse(a.put((K)k1, (V)v1));
		a.del((K)k1);
		assertEquals(a.getNumEntries(), 0);
		assertFalse(a.containsKey(k1));
		
		//insert two keys, try to delete non-existent key
		assertFalse(a.put((K) k2, (V)v2));
		assertFalse(a.put((K) k3, (V)v3));
		a.del((K)k1);
		assertEquals(a.getNumEntries(), 2);
		assertTrue(a.containsKey(k2));
		assertEquals(a.get((K)k2),v2);
		assertTrue(a.containsKey(k3));
		assertEquals(a.get((K)k3), v3);
		
		//valid delete
		a.del((K)k3);
		assertEquals(a.getNumEntries(), 1);
		assertTrue(a.containsKey((K) k2));
		assertFalse(a.containsKey((K)k3));
		assertEquals(a.get((K)k2),v2);
		
		//multiple deletes
		a.put((K) k4, (V)v4);
		a.del((K)k2);
		a.del((K)k4);
		assertFalse(a.containsKey(k4));
		assertFalse(a.containsKey(k2));
		assertFalse(a.containsKey(k3));
		assertEquals(a.getNumEntries(),0);
	}
	
	public void testKVMessage(){
		//testing convertToString in KVMessage
		Serializable k1obj = "1";
		String rtn = null;
		try {
			rtn = KVMessage.convertToString(k1obj);
		} catch (KVException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assertTrue(rtn!=null);
		System.out.println("rtn:" + rtn);
		
		//testing convertToGeneralType in KVMessage
		Object k2obj = null;
		try {
			k2obj = KVMessage.convertToGeneralType(rtn);
		} catch (KVException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assertTrue(k2obj != null);
	}
}
