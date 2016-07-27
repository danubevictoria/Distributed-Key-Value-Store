package edu.berkeley.cs162;
/**
 * Socket Server manages network connections 
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
//package edu.berkeley.cs162;
//package edu.berkeley.cs162;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/** 
 * This is an generic class that should handle all TCP network connections 
 * arriving on a given unique (host, port) tuple. Ensure that this class 
 * remains generic by providing the connection handling logic in a NetworkHandler
 */
public class SocketServer {
	String hostname;
	int port;
	NetworkHandler handler;
	ServerSocket server;
	Socket clientsocket;
	
	public SocketServer(String hostname) {
		this.hostname = hostname;
		try {
			this.port = new ServerSocket(0).getLocalPort();
		} catch (IOException e) {
			System.out.println("Could not create socket");
		}
	}
	
	public SocketServer(String hostname, int port) {
		this.hostname = hostname;
		this.port = port;
		
	}
	
	public String getHostname() {
		return this.hostname;
	}
	
	public int getPort() {
		return this.port;
	}
	public void connect() throws IOException {
		try {
		    server = new ServerSocket(port);
		} 
		catch (IOException e) {
		    System.out.println("Could not listen on port");
		    throw new IOException();
		}
		
	}
	
	/**
	 * Accept requests and service them asynchronously. 
	 */
	public void run() throws IOException {
		while(true)
		{
			
			clientsocket = null;
			try {
			    clientsocket = server.accept();
			} 
			catch (IOException e) {
			    System.out.println("Accept failed: 8080");
			    throw new IOException();
			}
			handler.handle(clientsocket);
			
			
	 
	    
	    }
		
	}
	
	/** 
	 * Add the network handler for the current socket server
	 * @param handler is logic for servicing a network connection
	 */
	public void addHandler(NetworkHandler handler) {
		this.handler = handler;
	}
}
