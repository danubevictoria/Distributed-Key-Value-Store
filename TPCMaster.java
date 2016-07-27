/**
 * Master for Two-Phase Commits
 * 
 * @author Mosharaf Chowdhury (http://www.mosharaf.com)
 *
 * Copyright (c) 2012, University of California at Berkeley
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
package edu.berkeley.cs162;
import java.security.*;
import java.security.spec.KeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;

public class TPCMaster<K extends Serializable, V extends Serializable>  {
	
	/**
	 * Implements NetworkHandler to handle registration requests from 
	 * SlaveServers.
	 * 
	 */
	private class TPCRegistrationHandler implements NetworkHandler {

		private ThreadPool threadpool = null;

		public TPCRegistrationHandler() {
			// Call the other constructor
			this(1);	
		}

		public TPCRegistrationHandler(int connections) {
			threadpool = new ThreadPool(connections);	
		}

		@Override
		public void handle(Socket client) throws IOException {
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
				String line = in.readLine();
				Integer uniqueID;
				Integer port;
				String temp = "";
				String temp2 = "";
				boolean found = false;
				for (int i = 0; i < line.length(); i++) {
					if (line.charAt(i) == '.') {
						found = true;
						continue;
					}
						
					if (!found)
						temp += line.charAt(i);
					else
						temp2 += line.charAt(i);
				}
				uniqueID = Integer.parseInt(temp);
				port = Integer.parseInt(temp2);
				registrationMap.put(uniqueID, port);
			}
			catch (IOException e) {
				System.out.println("something went wrong");
			}
		}
	}
	
	/**
	 *  Data structure to maintain information about SlaveServers
	 *
	 */
	private class SlaveInfo {
		// 128-bit globally unique UUID of the SlaveServer
		private long slaveID = -1;
		// Name of the host this SlaveServer is running on
		private String hostName = null;
		// Port which SlaveServer is listening to
		private int port = -1;
		
		// Variables to be used to maintain connection with this SlaveServer
		private KVClient<K, V> kvClient = null;
		private Socket kvSocket = null;

		/**
		 * 
		 * @param slaveInfo as "SlaveServerID@HostName:Port"
		 * @throws KVException
		 */
		public SlaveInfo(String slaveInfo) throws KVException {
			System.out.println("starting slaveinfo");
			//this.slaveID = UUID.fromString(slaveInfo.substring(0, slaveInfo.indexOf("@")));
			this.slaveID = hashTo64bit(slaveInfo.substring(0, slaveInfo.indexOf("@")));
			System.out.println("slave id " + this.slaveID);
			this.hostName = slaveInfo.substring(slaveInfo.indexOf("@")+1,slaveInfo.indexOf(":"));
			this.port = new Integer(slaveInfo.substring(slaveInfo.indexOf(":")+1));
			System.out.println("I'm making a slaveinfo");
			this.kvClient = new KVClient(this.hostName, this.port);
			try{
				this.kvSocket = new Socket(this.hostName, this.port);
			}
			catch(IOException e){
				System.out.println("error connecting");
				throw new KVException(new KVMessage("resp",null,null,null,e.getMessage()));
			}
		}
		
		public long getSlaveID() {
			return slaveID;
		}

		public KVClient<K, V> getKvClient() {
			return kvClient;
		}

		public Socket getKvSocket() {
			return kvSocket;
		}

		public void setKvSocket(Socket kvSocket) {
			this.kvSocket = kvSocket;
		}
//		public int compareTo(Object o) {
//			SlaveInfo slaveObj = (SlaveInfo)o;
//			return this.slaveID.compareTo(slaveObj.getSlaveID());
//		}
	}
	
	// Timeout value used during 2PC operations
	private static final int TIMEOUT_MILLISECONDS = 5000;
	
	// Cache stored in the Master/Coordinator Server
	private KVCache<K, V> masterCache = new KVCache<K, V>(1000);
	
	// Registration server that uses TPCRegistrationHandler
	private SocketServer regServer = null;
	
	// ID of the next 2PC operation
	private Long tpcOpId = 0L;
	
	private HashMap<K, ReentrantReadWriteLock> locks = new HashMap<K, ReentrantReadWriteLock>();
	private ReentrantLock outsideLock = new ReentrantLock();
	private ThreadPool tcpPool;
	private ThreadPool getPool;
	private Condition waitingThreads = outsideLock.newCondition();
	private String UNICODE_FORMAT = "UTF-8";
	private String DESEDE_ENCRYPTION_SCHEME = "DESede";
	private KeySpec myKeySpec;
	private SecretKeyFactory mySecretKeyFactory;
	private Cipher cipher;
	private byte[] keyAsBytes;
	private String myEncryptionKey;
	private String myEncryptionScheme;
	private SecretKey myKey;

	//array of slaves
	private SlaveInfo[] slaves=null;
	
	
	/**
	 * Creates TPCMaster using SlaveInfo provided as arguments and SlaveServers 
	 * actually register to let TPCMaster know their presence
	 * 
	 * @param listOfSlaves list of SlaveServers in "SlaveServerID@HostName:Port" format
	 * @throws Exception
	 */
	public TPCMaster(String[] listOfSlaves) throws Exception {
		slaves = new TPCMaster.SlaveInfo[listOfSlaves.length];
		for(int i= 0; i< listOfSlaves.length; i++) {
			System.out.println("here");
			slaves[i] = new TPCMaster.SlaveInfo(listOfSlaves[i]);
		}
		try {
			Arrays.sort(slaves);
		} catch (NullPointerException e) {
			throw new KVException(new KVMessage("resp", "Unknown Error: Empty slaves"));
		}
		
		regServer = new SocketServer(InetAddress.getLocalHost().getHostAddress(), 9090);
		regServer.addHandler(new TPCRegistrationHandler());
		tcpPool = new ThreadPool(2);
		getPool = new ThreadPool(2);
		myEncryptionKey = "MySecretEncryptionKey";
		myEncryptionScheme = DESEDE_ENCRYPTION_SCHEME;
		keyAsBytes = myEncryptionKey.getBytes(UNICODE_FORMAT);
		myKeySpec = new DESedeKeySpec(keyAsBytes);
		mySecretKeyFactory = SecretKeyFactory.getInstance(myEncryptionScheme);
		//cipher = Cipher.getInstance(myEncryptionScheme);
		myKey = mySecretKeyFactory.generateSecret(myKeySpec);
	}
	
	public SecretKey getSecretKey() {
		return myKey;
	}
	/**
	 * Calculates tpcOpId to be used for an operation. In this implementation
	 * it is a long variable that increases by one for each 2PC operation. 
	 * 
	 * @return 
	 */
	private String getNextTpcOpId() {
		tpcOpId++;
		return tpcOpId.toString();		
	}
	
	/**
	 * Start registration server in a separate thread
	 */
	public void run() {
		Runnable r = new Runnable() {
			public void run() {
				try {
					regServer.run();
				} catch (IOException e) {
					System.out.println("Unknown Error: Could not start registration server");
				}				}			
			};
		Thread t = new Thread(r);
		t.start();
	}
	
	/**
	 * Find first/primary replica location
	 * @param key
	 * @return
	 */
	private SlaveInfo findFirstReplica(K key) {
		
     // try {
    	  //String strKey = KVMessage.convertToString(key);
		String strKey = key.toString();
    	  long hashedKey = hashTo64bit(strKey);
    	  long replicaSlaveID = slaves[0].getSlaveID();
  		int position = 0;
  		while (this.isLessThanUnsigned(replicaSlaveID, hashedKey) && position < slaves.length) {
  				position++;
  				replicaSlaveID = slaves[position].getSlaveID();
  		}
  		int firstReplica = (position)%(slaves.length);
  		System.out.println(slaves.length + "length");
  		System.out.println("i is" + position + "rep" + firstReplica);
  		return slaves[firstReplica];
//      } catch (KVException e) {
//    	  throw new KVException(e.getMsg());
//      }
		
	}
	
	/**
	 * Find the successor of firstReplica to put the second replica
	 * @param firstReplica
	 * @return
	 */
	private SlaveInfo findSuccessor(SlaveInfo firstReplica) {
		int index = Arrays.binarySearch(slaves, firstReplica);
		System.out.println("index " + index);
		System.out.println("index+1 mod length " + (index+1)%(slaves.length));
		System.out.println("successor: " + slaves[(index+1)%(slaves.length)].getSlaveID());
		return slaves[(index+1)%(slaves.length)];
	}
	

	private class ProcessSocket implements Runnable
	{
		private Socket socket;
		private KVMessage output;
		public ProcessSocket(Socket c, KVMessage output)
		{
			socket = c;
			this.output= output;
		}
		public void run()
		{
			
			PrintWriter out;
			try {
				out = new PrintWriter(socket.getOutputStream(), true);
				try {
					out.println(output.toXML());
				} catch (KVException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			
			
		}
		
	}
	private class SlaveResponse implements Runnable
	{
		private Socket socket;
		private KVMessage[] output;
		public SlaveResponse(Socket c, KVMessage[] temp)
		{
			socket = c;
			output = temp;
			
		}
		
		public void run()
		{
			int curtime = (int)System.currentTimeMillis();
			long time = (long)1000;
			int t= (int)System.currentTimeMillis();
			try {
				
				while(socket.getInputStream().available() == 0 && (t - curtime) < TIMEOUT_MILLISECONDS)
				{
					waitingThreads.awaitNanos(time);
					t = (int)System.currentTimeMillis();
				}
			} catch (IOException e1) {
			} catch (InterruptedException e1) {
			}
			
			try {
				try {
					if((t - curtime) < TIMEOUT_MILLISECONDS)
						output[0] = new KVMessage(socket.getInputStream());
				} catch (KVException e) {
				}
				} catch (IOException e) {
					
				}
			
			
		}
		
	}
	private class SlaveGet implements Runnable
	{
		private SlaveInfo first;
		private Object[] output;
		private K marshKey;
		public SlaveGet(SlaveInfo first, Object[] temp, K key)
		{
			this.first = first;
			this.output = temp;
			marshKey = key;
			
		}
		
		public void run()
		{
			V value;
			KVClient client1 = first.getKvClient();
			value = masterCache.get(marshKey);
			if (value != null) {
				output[0] = value;
				output[1] = new KVException(new KVMessage("resp", null,null,null,"Success"));
			}
			else{
				try {
					value = (V) client1.get(marshKey);
					masterCache.put(marshKey, value);
					
					output[0] =value;
					output[1] = new KVException(new KVMessage("resp", null,null,null,"Success"));
				} catch (KVException e) {
					SlaveInfo successor = findSuccessor(first);
					KVClient client2 = successor.getKvClient();
					try {
						value = (V) client2.get(marshKey);
						masterCache.put(marshKey, value);
						output[0]=value;
						output[1] = new KVException(new KVMessage("resp", null, null, null, "@" + first.getSlaveID() + "=>" + e.getMsg().getMessage()));
					} catch (KVException e2) {
						 output[1] = new KVException(new KVMessage("resp", null, null, null, "@" + first.getSlaveID() + "=>" + e.getMsg().getMessage()+ "\n" + "@" + successor.getSlaveID() + "=>" + e2.getMsg().getMessage()));
					}
				}
			}
			
			
		}
		
	}
	/**
	 * Synchronized method to perform 2PC operations one after another
	 * 
	 * @param msg
	 * @param isPutReq
	 * @return True if the TPC operation has succeeded
	 * @throws KVException
	 */
	public synchronized boolean performTPCOperation(KVMessage msg, boolean isPutReq) throws KVException {
		//V value;
		String strKey = msg.getKey();
		String strValue = msg.getValue();
		V value = (V) KVMessage.convertToGeneralType(strValue);
		K key = (K) KVMessage.convertToGeneralType(strKey);
		outsideLock.lock();
		if(!locks.containsKey(key))
			locks.put(key,new ReentrantReadWriteLock());
		outsideLock.unlock();
		SlaveInfo first = findFirstReplica(key);
		SlaveInfo second = findSuccessor(first);
		Socket socket1 = first.getKvSocket();
		Socket socket2 = second.getKvSocket();
		KVMessage output;
		Lock writelock = locks.get(key).writeLock();
		writelock.lock();
		String nextOpId = getNextTpcOpId();
		if(isPutReq)
			output = new KVMessage(msg.getType(),strKey, strValue,nextOpId);
		else
			output = new KVMessage(msg.getType(),strKey,null,nextOpId);
		
		try{
		tcpPool.addToQueue(new ProcessSocket(socket1, output));
		tcpPool.addToQueue(new ProcessSocket(socket2, output));
		}catch(InterruptedException e2)
		{
			writelock.unlock();
			throw new KVException(new KVMessage("resp", null, null, null, "Unknown Error: Could not add thread to task queue"));
		}
		boolean success;
		
		KVMessage[] firstresponse = new KVMessage[1];
		KVMessage[] secondresponse = new KVMessage[1];
		SlaveResponse firstslave = new SlaveResponse(socket1,firstresponse);
		SlaveResponse secondslave = new SlaveResponse(socket2,secondresponse);
		try{
			Thread t1 = new Thread(firstslave);
			Thread t2 = new Thread(secondslave);
			t1.start();
			t2.start();
			t1.join();
			t2.join();
		}
		catch(InterruptedException e2)
		{
			writelock.unlock();
			throw new KVException(new KVMessage("resp", null, null, null, "Unknown Error: Could not add thread to task queue"));
		}
		KVMessage resp1 = firstresponse[0];
		KVMessage resp2 = secondresponse[0];
		if(resp1 == null || resp2 == null || resp1.getType().equals("abort") || resp2.getType().equals("abort"))
		{
			success= false;
			output = new KVMessage("abort", null, null,nextOpId);
			try {
				tcpPool.addToQueue(new ProcessSocket(socket1, output));
				tcpPool.addToQueue(new ProcessSocket(socket2, output));
			} catch (InterruptedException e) {
				throw new KVException(new KVMessage("resp", "Network Error: Could not create socket"));
			}
				
		}
		else
		{
			success=true;
			output = new KVMessage("commit", null, null,nextOpId);
			try{
				tcpPool.addToQueue(new ProcessSocket(socket1, output));
				tcpPool.addToQueue(new ProcessSocket(socket2, output));
				}catch(Exception e2)
				{
					throw new KVException(new KVMessage("resp", "Network Error: Could not create socket"));
				}
		}
		
		
		
		KVMessage[] firstack = new KVMessage[1];
		KVMessage[] secondack = new KVMessage[1];
		SlaveResponse firstslaveack = new SlaveResponse(socket1,firstack);
		SlaveResponse secondslaveack = new SlaveResponse(socket2,secondack);
		try{
			Thread t1 = new Thread(firstslaveack);
			Thread t2 = new Thread(secondslaveack);
			t1.start();
			t2.start();
			t1.join();
			t2.join();
		}
		catch(InterruptedException e2)
		{
			writelock.unlock();
			throw new KVException(new KVMessage("resp", null, null, null, "Unknown Error: Could not add thread to task queue"));
		}
		if (isPutReq) {
			masterCache.put(key, value);
		}
		else {
			masterCache.del(key);
		}
		writelock.unlock();
		if(!success)
		{
			String errormessage = "";
			if(resp1.getType().equals("abort"))
				errormessage = errormessage + "@"+ first.getSlaveID() + "=>" + resp1.getMessage()+"\n";
			if(resp2.getType().equals("abort"))
				errormessage = errormessage + "@"+ second.getSlaveID() + "=>" + resp2.getMessage();
			throw new KVException(new KVMessage("resp", null, null, null, errormessage));
			
		}
		return success;
	}



	/**
	 * Perform GET operation in the following manner:
	 * - Try to GET from first/primary replica
	 * - If primary succeeded, return Value
	 * - If primary failed, try to GET from the other replica
	 * - If secondary succeeded, return Value
	 * - If secondary failed, return KVExceptions from both replicas
	 * 
	 * @param msg Message containing Key to get
	 * @return Value corresponding to the Key
	 * @throws KVException
	 */
	public V handleGet(KVMessage msg) throws KVException {
		// implement me
		V value = null;
		
			String key = msg.getKey();
			K marshKey = (K) KVMessage.convertToGeneralType(key);
			outsideLock.lock();
			if (!locks.containsKey(marshKey))
				locks.put(marshKey, new ReentrantReadWriteLock());
			outsideLock.unlock();
			ReentrantReadWriteLock lock = locks.get(marshKey);
			SlaveInfo firstReplica = this.findFirstReplica(marshKey);
			Lock readLock = lock.readLock();
			readLock.lock();
			Object[] temp = new Object[2];
			SlaveGet get = new SlaveGet(firstReplica, temp,marshKey);
			Thread getreq = new Thread(get);
			getreq.start();
			
			if(temp[1] == null)
				return (V)(temp[0]);
			else
			{
				KVException exception = (KVException)(temp[1]);
				throw exception;
			}		
			}

		/**
	 * Converts Strings to 64-bit longs
	 * Borrowed from http://stackoverflow.com/questions/1660501/what-is-a-good-64bit-hash-function-in-java-for-textual-strings
	 * Adapted from String.hashCode()
	 * @param string String to hash to 64-bit
	 * @return
	 */
	private long hashTo64bit(String string) {
		// Take a large prime
		long h = 1125899906842597L; 
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31*h + string.charAt(i);
		}
		return h;
	}

	/**
	 * Compares two longs as if they were unsigned (Java doesn't have unsigned data types except for char)
	 * Borrowed from http://www.javamex.com/java_equivalents/unsigned_arithmetic.shtml
	 * @param n1 First long
	 * @param n2 Second long
	 * @return is unsigned n1 less than unsigned n2
	 */
	private boolean isLessThanUnsigned(long n1, long n2) {
		return (n1 < n2) ^ ((n1 < 0) != (n2 < 0));
	}

	private boolean isLessThanEqualUnsigned(long n1, long n2) {
		return isLessThanUnsigned(n1, n2) || n1 == n2;
	}	
	private static HashMap<Integer, Integer> registrationMap;



	public static void main(String[] args) {
		String[] slaves = new String[5];
		long upper0 = 10;
		long lower0 = 10;
		UUID u = new UUID(upper0, lower0);
		slaves[0] = u +"@localhost:8081";
		long upper1 = 11;
		long lower1 = 11;
		slaves[1] = new UUID(upper1, lower1) +"@localhost:8082";
		long upper2 =12;
		long lower2=12;
		slaves[2] = new UUID(upper2, lower2) +"@localhost:8083";
		long upper3=13;
		long lower3=13;
		slaves[3] = new UUID(upper3,lower3) +"@localhost:8084";
		long upper4=14;
		long lower4=14;
		slaves[4] = new UUID(upper4,lower4) +"@localhost:8085";
		
		try {
			
		TPCMaster m = new TPCMaster(slaves);
		System.out.println("master created");
		m.findFirstReplica("abc");
		m.findSuccessor(m.findFirstReplica("abc"));
		} catch (Exception e) {
			System.out.println("whaaaaaaat");
		}
	}
}
