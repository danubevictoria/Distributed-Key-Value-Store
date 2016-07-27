package edu.berkeley.cs162;
//package edu.berkeley.cs162;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.channels.IllegalBlockingModeException;
import java.io.*;


public class PingPong {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String output = "pong\n";
		try {
			System.out.println("Server starting on port 8081");
			ServerSocket srvr = new ServerSocket(8081);
			while (true)
			{
				try {
					Socket skt = srvr.accept();
					System.out.println("Server listening on port 8081");
					try {
						PrintWriter out = new PrintWriter(skt.getOutputStream(), true);
						out.println(output);
						out.close();
						System.out.println("output closed");
						Socket skt2 = new Socket("localhost", 8081);
						BufferedReader in = new BufferedReader(new InputStreamReader(skt2.getInputStream()));
						String line = in.readLine();
						System.out.println(line);
						in.close();
					}
					catch (IOException e){
						System.out.println("Failed to send data");
					}
					skt.close();
				}
				catch (IOException e) {
					System.out.println("Error listening on port 8081: IOException");
				}
				catch (SecurityException e){
					System.out.println("Error listening on port 8081: Security Exception");
				}
				catch (IllegalBlockingModeException e){
					System.out.println("Error listening on port 8081: IllegalBlockingModeException");
				}
			}
		}
		catch (IOException e){
			System.out.println("Error opening socket: I/O Exception");
		}
		catch (SecurityException e){
			System.out.println("Error opening socket: Security Exception");
		}
	}
}
