package edu.berkeley.cs162;

import java.io.Serializable;

public class clienttester {
	public static void main(String[] args)
	{
		KVClient r = new KVClient("ec2-50-16-116-121.compute-1.amazonaws.com", 8080);
		Object x;
		
		try {
			for(int i = 0; i< 20; i++)
				r.put(i,i);
			x= r.get(12);
			for (int i=0; i< 20; i++) {
				System.out.println(r.get(i));
			}
			//System.out.println(x);
			//x= r.get("wassup");
			
			
			
		} catch (KVException e) {
			System.out.println("wtf");// TODO Auto-generated catch block
			try {
				System.out.println(e.getMsg().toXML());
			} catch (KVException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		
	}

}