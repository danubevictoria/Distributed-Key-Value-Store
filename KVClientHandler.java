/**
 * Handle client connections over a socket interface
 * 
 * @author Mosharaf Chowdhury (http://www.mosharaf.com)
 * @author Prashanth Mohan (http://www.cs.berkeley.edu/~prmohan)
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
package edu.berkeley.cs162;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.Socket;

import javax.crypto.SecretKey;

/**
 * This NetworkHandler will asynchronously handle the socket connections. It
 * uses a threadpool to ensure that none of it's methods are blocking.
 * 
 * @param <K>
 *            Java Generic type for the Key
 * @param <V>
 *            Java Generic type for the Value
 */
public class KVClientHandler<K extends Serializable, V extends Serializable>
		implements NetworkHandler {
	private KeyServer<K, V> keyserver = null;
	private ThreadPool threadpool = null;

	private TPCMaster<K, V> tpcMaster = null;

	public KVClientHandler(KeyServer<K, V> keyserver) {
		initialize(keyserver, 1);
	}

	public KVClientHandler(KeyServer<K, V> keyserver, int connections) {
		initialize(keyserver, connections);
	}

	private void initialize(KeyServer<K, V> keyserver, int connections) {
		this.keyserver = keyserver;
		threadpool = new ThreadPool(connections);
	}

	public KVClientHandler(KeyServer<K, V> keyserver, TPCMaster<K, V> tpcMaster) {
		initialize(keyserver, 1, tpcMaster);
	}

	public KVClientHandler(KeyServer<K, V> keyserver, int connections,
			TPCMaster<K, V> tpcMaster) {
		initialize(keyserver, connections, tpcMaster);
	}

	private void initialize(KeyServer<K, V> keyserver, int connections,
			TPCMaster<K, V> tpcMaster) {
		this.keyserver = keyserver;
		threadpool = new ThreadPool(connections);
		this.tpcMaster = tpcMaster;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.cs162.NetworkHandler#handle(java.net.Socket)
	 */
	@Override
	public void handle(Socket client) throws IOException {
		// implement me
		try {
			Runnable task = new ProcessClientSocket(client);
			threadpool.addToQueue(task);
		} catch (InterruptedException e) {
			throw new IOException();
		}
	}

	private class ProcessClientSocket implements Runnable {
		private Socket socket;

		public ProcessClientSocket(Socket client) {
			socket = client;
		}

		public void run() {
			KVMessage output = null;
			try {
				KVMessage socketInput = new KVMessage(socket.getInputStream());
				String value = socketInput.getValue();
				String key = socketInput.getKey();
				String msgType = socketInput.getType();
				String message = socketInput.getMessage();
				if (msgType.equals("getEnKey")) {
					SecretKey enKey = tpcMaster.getSecretKey();
					String strEnKey = KVMessage.encodeObject(enKey);
					output = new KVMessage("resp", strEnKey);
				} else if (msgType.equals("getreq")) {
					V temp = tpcMaster.handleGet(new KVMessage("getreq", key,
							null));
					value = KVMessage.encodeObject(temp);
					output = new KVMessage("resp", key, value);
				} else if (msgType.equals("putreq")) {
					tpcMaster.performTPCOperation(socketInput, true);
					output = new KVMessage("resp", "Success");
				} else if (msgType.equals("delreq")) {
					tpcMaster.performTPCOperation(socketInput, false);
					output = new KVMessage("resp", "Success");
				} else if (msgType.equals("ignoreNext")){
					output = new KVMessage("ignoreNext", message);
				}
				PrintWriter out = new PrintWriter(socket.getOutputStream(),
						true);
				try {
					out.println(output.toXML());
				} catch(KVException e) {
					System.out.println(e.getMessage());
				}
				socket.shutdownOutput();
			} catch (KVException e) {
				output = new KVMessage("resp", e.getMsg().getMessage());
				try {
				PrintWriter errorOut = new PrintWriter(socket.getOutputStream(), true); 
				errorOut.println(output.toXML());}
				catch (Exception e2) {
					System.out.println("something is wrong here");
				}
				
			} catch (IOException e) {
				output = new KVMessage("resp",
						"Network Error: Could not receive data");
				try {
					PrintWriter errorOut = new PrintWriter(
				
						socket.getOutputStream(), true);
				errorOut.println(output.toXML());
				} catch (Exception e2) {
					System.out.println(e2.getMessage());
				}
			}
		}
	}
}
