/**
 * Handle TPC connections over a socket interface
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implements NetworkHandler to handle 2PC operation requests from the Master/
 * Coordinator Server
 * 
 */
public class TPCMasterHandler<K extends Serializable, V extends Serializable>
		implements NetworkHandler {
	private KeyServer<K, V> keyserver = null;
	private ThreadPool threadpool = null;
	private TPCLog<K, V> tpcLog = null;
	private boolean ignoreNext = false;
	private HashMap<String, KVMessage> transactions;
	private ReentrantLock exclusiveLock;

	public TPCMasterHandler(KeyServer<K, V> keyserver) {
		this(keyserver, 1);
	}

	public TPCMasterHandler(KeyServer<K, V> keyserver, int connections) {
		this.keyserver = keyserver;
		threadpool = new ThreadPool(connections);
		exclusiveLock = new ReentrantLock();
		transactions = new HashMap<String, KVMessage>();
	}

	/**
	 * Set TPCLog after it has been rebuilt
	 * 
	 * @param tpcLog
	 */
	public void setTPCLog(TPCLog<K, V> tpcLog) {
		this.tpcLog = tpcLog;
	}

	@Override
	public void handle(Socket client) throws IOException {
		// implement me
		try {
			Runnable task = new ProcessMasterSocket(client);
			threadpool.addToQueue(task);
		} catch (InterruptedException e) {
			throw new IOException();
		}
	}

	private class ProcessMasterSocket implements Runnable {
		private Socket masterSocket;

		public ProcessMasterSocket(Socket masterSocket) {
			this.masterSocket = masterSocket;
		}

		public void run(){
			KVMessage output = null;
			String tpcOpId = null;
			try{
				KVMessage socketInput = new KVMessage(masterSocket.getInputStream());
				String value = socketInput.getValue();
				String key = socketInput.getKey();
				tpcOpId = socketInput.getTpcOpId();
				String msgType = socketInput.getType();
				String message = socketInput.getMessage();
				
				if(tpcLog.hasInterruptedTpcOperation()){
					KVMessage interruptedOp = tpcLog.getInterruptedTpcOperation();
					String interruptedOpId = interruptedOp.getTpcOpId();
					exclusiveLock.lock();
					try{
						transactions.put(interruptedOpId, interruptedOp);
					} finally {
						exclusiveLock.unlock();
					}
				} else if (msgType.equals("ignoreNext")){
					ignoreNext = true;
				} else if (msgType.equals("getreq")){
					V temp = null;
					try{
						temp = keyserver.get((K)key);
					} catch (KVException e){
						output = new KVMessage("resp", e.getMsg().getMessage());
					}
					value = KVMessage.encodeObject(temp);
					output = new KVMessage("resp", key, value);
				} else if (msgType.equals("putreq")){
					KVMessage abortPut = new KVMessage("abort", null, null, tpcOpId);
					if (ignoreNext){
						tpcLog.appendAndFlush(abortPut);
						output = new KVMessage("abort", null, null, "IgnoreNext Error: SlaveServer " + message);
						ignoreNext = false;
					} else if (key == null && value == null){
						tpcLog.appendAndFlush(abortPut);
						output = new KVMessage("abort", null, null, null, "Empty Key & Empty Value", tpcOpId);
					} else if (key == null){
						tpcLog.appendAndFlush(abortPut);
						output = new KVMessage("abort", null, null, null, "Empty Key", tpcOpId);
					} else if (value == null){
						tpcLog.appendAndFlush(abortPut);
						output = new KVMessage("abort", null, null, null, "Empty Value", tpcOpId);
					} else {
						ByteArrayOutputStream stream = new ByteArrayOutputStream();
						ObjectOutputStream objectstream = null;
						try {
							objectstream = new ObjectOutputStream(stream);
						} catch (IOException e1) {
							tpcLog.appendAndFlush(abortPut);
							output = new KVMessage("abort", null, null, null, "Unknown Error: IOException creating objectstream", tpcOpId);
						}
						try {
							objectstream.writeObject(key);
						} catch (IOException e1) {
							tpcLog.appendAndFlush(abortPut);
							output = new KVMessage("abort", null, null, null, "Unknown Error: IOException writing objectstream", tpcOpId);
						}
						byte[] keybytes = stream.toByteArray();

						ByteArrayOutputStream valuestream = new ByteArrayOutputStream();
						ObjectOutputStream valueobjectstream = null;
						try {
							valueobjectstream = new ObjectOutputStream(
									valuestream);
						} catch (IOException e1) {
							tpcLog.appendAndFlush(abortPut);
							output = new KVMessage("abort", null, null, null, "Unknown Error: IOException creating valueobjectstream", tpcOpId);
						}
						try {
							valueobjectstream.writeObject(value);
						} catch (IOException e1) {
							tpcLog.appendAndFlush(abortPut);
							output = new KVMessage("abort", null, null, null, "Unknown Error: IOException writing valueobjectstream", tpcOpId);
						}
						byte[] valuebytes = valuestream.toByteArray();
						
						if (keybytes.length > 256 && valuebytes.length > 1024 * 128){
							tpcLog.appendAndFlush(abortPut);
							output = new KVMessage("abort", null, null, null, "Over Sized Key & Over Sized Value", tpcOpId);
						} else if (keybytes.length > 256){
							tpcLog.appendAndFlush(abortPut);
							output = new KVMessage("abort", null, null, null, "Over Sized Key", tpcOpId);
						} else if (valuebytes.length > 1024 * 128){
							tpcLog.appendAndFlush(abortPut);
							output = new KVMessage("resp", null, null, null, "Over Sized Value", tpcOpId);
						} else {
							KVMessage readyPut = new KVMessage("ready", key, value, null, "putreq", tpcOpId);
							tpcLog.appendAndFlush(readyPut);
							exclusiveLock.lock();
							try {
								transactions.put(tpcOpId, readyPut);
							} finally {
								exclusiveLock.unlock();
							}
							output = new KVMessage("ready", null, null, tpcOpId);
						}
					}
				} else if (msgType.equals("delreq")){
					KVMessage abortDel = new KVMessage("abort", null, null, tpcOpId);
					if (ignoreNext){
						tpcLog.appendAndFlush(abortDel);
						output = new KVMessage("abort", null, null, "IgnoreNext Error: SlaveServer " + message);
						ignoreNext = false;
					} else if (key == null){
						tpcLog.appendAndFlush(abortDel);
						output = new KVMessage("abort", null, null, null, "Empty key", tpcOpId);
					} else if (!keyserver.containsKey((K) key)){
						tpcLog.appendAndFlush(abortDel);
						output = new KVMessage("abort", null, null, null, "Does not exist", tpcOpId);
					} else {
						KVMessage readyDel = new KVMessage("ready", key, null, null, "delreq", tpcOpId);
						tpcLog.appendAndFlush(readyDel);
						exclusiveLock.lock();
						try{
							transactions.put(tpcOpId, readyDel);
						} finally {
							exclusiveLock.unlock();
						}
						output = new KVMessage("ready", null, null, tpcOpId);
					}
				} else if (msgType.equals("commit")){
					KVMessage transaction = transactions.get(tpcOpId);
					KVMessage commitMsg = new KVMessage("commit", null, null, tpcOpId);
					tpcLog.appendAndFlush(commitMsg);
					if(transaction.getMessage().equals("putreq")){
						keyserver.put((K)transaction.getKey(), (V)transaction.getValue());
						output = new KVMessage("ack", null, null, tpcOpId);
					} else if (transaction.getMessage().equals("delreq")){
						keyserver.del((K)transaction.getKey());
						output = new KVMessage("ack", null, null, tpcOpId);
					}
//					} else if (transaction.getMessage().equals("abort")){
//						KVMessage abortMsg = new KVMessage("abort", null, null, tpcOpId);
//						tpcLog.appendAndFlush(abortMsg);
//						output = new KVMessage("abort", null, null, "Unknown Error: received abort", tpcOpId);
//					} else {
//						output = new KVMessage("abort", null, null, "Unknown Error: request for transaction not proper/properly recorded", tpcOpId);
//					}
				} else if (msgType.equals("abort")){
					KVMessage abortMsg = new KVMessage("abort", null, null, tpcOpId);
					tpcLog.appendAndFlush(abortMsg);
					output = new KVMessage("ack", null, null, tpcOpId);
				}
				PrintWriter out = new PrintWriter(masterSocket.getOutputStream(), true);
				out.println(output.toXML());
				masterSocket.shutdownOutput();
			} catch (KVException e){
//				output = new KVMessage("abort", null, null, e.getMsg().getMessage(), tpcOpId);
//				PrintWriter errorOut = null;
//				try {
//					errorOut = new PrintWriter(masterSocket.getOutputStream(), true);
//				} catch (IOException e1) {
//					System.out.println("Network Error: Could not send data");
//				}
//				try {
//					errorOut.println(output.toXML());
//				} catch (KVException e1) {
//					System.out.println(e1.getMsg().getMessage());
//				}
				//System.out.println(e.getMsg().getMessage());
				e.printStackTrace();
			} catch (IOException e){
//				output = new KVMessage("resp", "Network Error: Could not receive data");
//				PrintWriter errorOut = null;
//				try {
//					errorOut = new PrintWriter(masterSocket.getOutputStream());
//				} catch (IOException e1) {
//					System.out.println("Network Error: Could not send data");
//				}
//				try {
//					errorOut.println(output.toXML());
//				} catch (KVException e1) {
//					System.out.println(e1.getMsg().getMessage());
//				}
				//System.out.println("Unknown Error: Could not receive data");
				e.printStackTrace();
			}	
		}
	}
}
