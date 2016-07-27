package edu.berkeley.cs162;
import java.io.Serializable;

public class Test implements Serializable {
	String msg;
	public Test(String m) {
		this.msg = m;
	}
	public static void main(String[] args) {
		try {
			//String msg = KVMessage.convertToString(new Test("i hate my life"));
			//System.out.println("the msg:" + msg);
			//Object o = KVMessage.convertToGeneralType("hello");
			//System.out.println(o.toString());
			//static KVClient<String, String> client = null;
			String s = "232@localhost:8080";
			System.out.println(s.substring(0, s.indexOf("@")));
			System.out.println(s.substring(s.indexOf("@") +1, s.indexOf(":")));
			System.out.println(s.substring(s.indexOf(":") +1));
			
		} catch (Exception e) {
			System.out.println("tears");
		}
	}
}