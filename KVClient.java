/**
 * * Client component for generating load for the KeyValue store. 
 * This is also used by the Master server to reach the slave nodes.
 * 
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.Socket;

import javax.crypto.SecretKey;

/**
 * This class is used to communicate with (appropriately marshalling and unmarshalling) 
 * objects implementing the {@link KeyValueInterface}.
 *
 * @param <K> Java Generic type for the Key
 * @param <V> Java Generic type for the Value
 */
public class KVClient<K extends Serializable, V extends Serializable> implements KeyValueInterface<K, V> {

	private String server = null;
	private int port = 0;
	private KVCrypt myCrypt = new KVCrypt();
	
	/**
	 * @param server is the DNS reference to the Key-Value server
	 * @param port is the port on which the Key-Value server is listening
	 */
	public KVClient(String server, int port) {
		this.server = server;
		this.port = port;
	}
	
	private void createCipher() throws KVException {
		KVMessage request = new KVMessage("getEnKey");
		Socket s;
		try {
			s = new Socket(server, port);
		} catch (IOException e) {
			throw new KVException(new KVMessage("resp", "Network Error: Could not connect"));
		}

		try {

			PrintWriter out = new PrintWriter(s.getOutputStream(), true);
			out.println(request.toXML());
			System.out.println(request.toXML());
			s.shutdownOutput();

		} catch (IOException e) {
			throw new KVException(new KVMessage("resp", "Network Error: Could not send data"));
		}

		try {

			KVMessage response = new KVMessage(s.getInputStream());
			if (response.getType().equals("resp")) {
				String encodedKeyString = response.getMessage();
				SecretKey enKey = (SecretKey) KVMessage
						.decodeObject(encodedKeyString);
				// Set up cipher
				myCrypt.setKey(enKey);
				myCrypt.setUp();
			}
			s.close();
		} catch (IOException e) {
			throw new KVException(new KVMessage("resp",
					"Network Error: Could not read data"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean put(K key, V value) throws KVException {
		if (key == null)
			throw new KVException(new KVMessage("resp", null, null, null, "Empty key"));
		if (value == null)
			throw new KVException(new KVMessage("resp", null, null, null, "Empty Value"));
		if (!myCrypt.hasValidKey()) {
			createCipher();
		}
		 String strKey = KVMessage.convertToString(key);
		 System.out.println("key: " + strKey);
		 String strValue = KVMessage.convertToString(value);
		 KVMessage request = new KVMessage("putreq", strKey, strValue);
	 Socket s;
	 try {
		 s = new Socket(server, port);
	} catch (IOException e) {
		 throw new KVException(new KVMessage("resp",null,null,null,
		 "Network Error: Could not connect"));
		 }
	 try {
		 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
		 out.println(request.toXML());
		 System.out.println(request.toXML());
		 s.shutdownOutput();
	 } catch (IOException e) {
		 throw new KVException(new KVMessage("resp",null,null,null, "Network Error: Could not send data"));
	 }
	 try {
		 KVMessage response = new KVMessage(s.getInputStream());
		 if (response.getType().equals("resp")) {
			 if (response.getMessage().equals("Success")) {
				 return true;
			 }
			 else if (!response.getMessage().equals("Success")) {
				 throw new KVException(response);
			 }
		 }
	 } catch (IOException e) {
		 throw new KVException(new KVMessage("resp",null,null,null, "Network Error: Could not read data"));
	 }
			
	
	try {
		s.close();
	} catch (IOException e) {
		throw new KVException(new KVMessage("resp", "Network Error: Could not disconnect"));
	}

		return false;
	}


	@SuppressWarnings("unchecked")
	@Override
	public V get(K key) throws KVException {
		if(key == null)
			throw new KVException(new KVMessage("resp",null,null,null, "Empty key"));
		if (!myCrypt.hasValidKey()) {
			createCipher();
		}
		String strKey = KVMessage.convertToString(key);
		KVMessage request = new KVMessage("getreq", strKey, null); 
		//try {
			Socket s;
			try {
			s = new Socket(server, port);
			}
			catch (IOException e) {
				throw new KVException(new KVMessage("resp",null,null,null, "Network Error: Could not connect"));
			}

			try {
				PrintWriter out = new PrintWriter(s.getOutputStream(), true);
				out.println(request.toXML());
				System.out.println(request.toXML());
				s.shutdownOutput();
			} catch (IOException e) {
				throw new KVException(new KVMessage("resp",null,null,null, "Network Error: Could not send data"));
			}
			try {
				KVMessage response = new KVMessage(s.getInputStream());
				
				if (response.getType().equals("resp")) {
					if (response.getMessage() != null) {
						throw new KVException(response);
					}
					else {
						String value = response.getValue();
						System.out.println(value);
						return (V) KVMessage.convertToGeneralType(value);
					}
				}
				
			} catch (IOException e) {
				throw new KVException(new KVMessage("resp",null,null,null, "Network Error: Could not read data"));
			}
		//} catch (IOException e) {
		//	throw new KVException(new KVMessage("resp", null, null, null, "Network Error: Could not create socket"));
		//}
		return null;
	}

	@Override
	public void del(K key) throws KVException {
		if(key == null)
			throw new KVException(new KVMessage("resp",null,null,null, "Empty key"));
		if (!myCrypt.hasValidKey()) {
			createCipher();
		}
		String strKey = KVMessage.convertToString(key);
		KVMessage request = new KVMessage("delreq", strKey, null);
		//try {
			Socket s;
			try {
			s = new Socket(server, port);
			}
			catch (IOException e) {
				throw new KVException(new KVMessage("resp",null,null,null, e.getMessage()));
			}

			try {
				PrintWriter out = new PrintWriter(s.getOutputStream(), true);
				out.println(request.toXML());
				System.out.println(request.toXML());
				s.shutdownOutput();
				
			} catch (IOException e) {
				throw new KVException(new KVMessage("resp",null,null,null, "Network Error: Could not send data"));
			}
			try {
				KVMessage response = new KVMessage(s.getInputStream());
				if (response.getType().equals("resp") && !response.getMessage().equals("Success")) {
					throw new KVException(response);
				}			
				s.close();
			} catch (IOException e) {
				throw new KVException(new KVMessage("resp",null,null,null, "Network Error: Could not read data"));
			}
	//} catch (IOException e) {
	//	throw new KVException(new KVMessage("resp", null, null, null, "Network Error: Could not create socket"));
	//}
	}
}
