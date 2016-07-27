package edu.berkeley.cs162;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;


public class PingPongTest {
	public static void main(String args[]) {
		try {
			Socket skt = new Socket("localhost", 8081);
			BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
			String line = in.readLine();
			System.out.println(line);
			in.close();
		}
			catch (IOException e) {
				System.out.println("No I/O");
			}
			
		
	}
}
