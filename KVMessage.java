/**
 * 
 * XML Parsing library for the key-value store
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

import java.io.*;


//import javax.xml.parsers.DocumentBuilder;
//import javax.xml.parsers.DocumentBuilderFactory;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;


/**
 * This is the object that is used to generate messages the XML based messages 
 * for communication between clients and servers. Data is stored in a 
 * marshalled String format in this object.
 */
public class KVMessage {
	private String msgType = null;
	private String key = null;
	private String value = null;
	private String status = null;
	private String message = null;
	private String tpcOpId = null;
	
	public KVMessage(String msgType) {
		this.msgType = msgType;
	}
	
	public KVMessage(KVMessage kvm) {
		this.msgType = kvm.msgType;
		this.key = kvm.key;
		this.value = kvm.value;
		this.status = kvm.status;
		this.message = kvm.message;
		this.tpcOpId = kvm.tpcOpId;
	}

	public KVMessage(String msgType, String message) {
		this.msgType = msgType;
		this.message = message;
	}
	public KVMessage(String msgType, String key, String value) {
		this.msgType = msgType;
		this.key = key;
		this.value = value;
	}
	
	public KVMessage(String msgType, String key, String value, String tpcOpId) {
		this.msgType = msgType;
		this.key = key;
		this.value = value;
		this.tpcOpId = tpcOpId;
	}
	public KVMessage(String msgType, String key, String value, String status, String message) {
		this.msgType = msgType;
		this.key = key;
		this.value = value;
		this.status = status;
		this.message = message;
	}
	
	public KVMessage(String msgType, String key, String value, String status, String message, String tpcOpId) {
		this.msgType = msgType;
		this.key = key;
		this.value = value;
		this.status = status;
		this.message = message;
		this.tpcOpId = tpcOpId;
	}
	
	public String getType() {
		return this.msgType;
	}
	public String getMessage() {
		return this.message;
	}	
	public String getValue() {
		return this.value;
	}	
	public String getKey() {
		return this.key;
	}	
	public String getStatus() {
		return this.status;
	}
	public String getTpcOpId() {
		return this.tpcOpId;
	}
	
	public static Object convertToGeneralType(String s) throws KVException {
		try {
			byte[] data = DatatypeConverter.parseBase64Binary(s);
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
			Object obj = ois.readObject();
			ois.close();
			return obj;
		} catch (Exception e) {
			throw new KVException(new KVMessage("resp",null,null,null, "Unknown Error: Could not convert to general type"));
		}
	}
	public static String convertToString(Serializable info) throws KVException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(bytes);
			os.writeObject(info);
			os.close();
			String returned = DatatypeConverter.printBase64Binary(bytes.toByteArray());
			//System.out.println("to return: " + returned);
			return returned;
		}
		catch (Exception e) {
			throw new KVException(new KVMessage("resp", null, null, null, "Unknown Error: error-description"));
		}
		
	}
	/**
	 * Encode Object to base64 String 
	 * @param obj
	 * @return
	 */
	public static String encodeObject(Object obj) throws KVException {
        String encoded = null;
        try{
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bs);
            os.writeObject(obj);
            byte [] bytes = bs.toByteArray();
            encoded = DatatypeConverter.printBase64Binary(bytes);
            bs.close();
            os.close();
        } catch(IOException e) {
            throw new KVException(new KVMessage("resp", "Unknown Error: Error serializing object"));
        }
        return encoded;
	}

	/**
	 * Decode base64 String to Object
	 * @param str
	 * @return
	 */
	public static Object decodeObject(String str) throws KVException {
		Object obj = null;
		try{
	        byte[] decoded = DatatypeConverter.parseBase64Binary(str);
	        ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(decoded));
	        obj = is.readObject();
	        is.close();
		} catch(IOException e) {
	        throw new KVException(new KVMessage("resp", "Unknown Error: Unable to decode object"));
		}
		catch (ClassNotFoundException e) {
	        throw new KVException(new KVMessage("resp", "Unknown Error: Decoding object class not found"));
		}
		return obj;
	}
	
	/* Hack for ensuring XML libraries does not close input stream by default.
	 * Solution from http://weblogs.java.net/blog/kohsuke/archive/2005/07/socket_xml_pitf.html */
	private class NoCloseInputStream extends FilterInputStream {
	    public NoCloseInputStream(InputStream in) {
	        super(in);
	    }
	    
	    public void close() {} // ignore close
	}
	
	public KVMessage(InputStream input) throws KVException {
			
			try {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				
				try {
					Document dom = db.parse(new NoCloseInputStream(input));
					Element docElement = dom.getDocumentElement();
					String[] nodes = {"Key", "Value", "Status", "Message", "TPCOpId"};
					String[] values = new String[5];
					for (int i=0; i<nodes.length; i++) {
						NodeList elementNodes = docElement.getElementsByTagName(nodes[i]);
						if (elementNodes != null && elementNodes.getLength() == 1) {
							Element elm = (Element)elementNodes.item(0);
							values[i] = elm.getTextContent();
						}
					}
					this.key = values[0];
					this.value = values[1];
					this.status = values[2];
					this.message = values[3];
					this.tpcOpId = values[4];
					this.msgType = docElement.getAttribute("type");
						//System.out.println("type: " + this.msgType);
				} catch (Exception e) {
					throw new KVException(new KVMessage("resp",null, null,null, "XML Error: Received unparseable message"));
				}
	
			} catch (ParserConfigurationException e) {
				throw new KVException(new KVMessage("resp", null, null, null, "Unknown Error: parser failed"));
			}
		
		}
	

	
	/**
	 * Generate the XML representation for this message.
	 * @return the XML String
	 */
	public String toXML() throws KVException {
		if (this.msgType == null) {
			return null;
		}
		else {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			try {
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document dom = db.newDocument();
				Element root = dom.createElement("KVMessage");
				root.setAttribute("type", this.msgType);
				dom.appendChild(root);
				if (this.key != null) {
					Element keyNode = dom.createElement("Key");
					Text strKey = dom.createTextNode(this.key);
					keyNode.appendChild(strKey);
					root.appendChild(keyNode);
				}
				if (this.value != null) {
					Element valueNode = dom.createElement("Value");
					Text strValue = dom.createTextNode(this.value);
					valueNode.appendChild(strValue);
					root.appendChild(valueNode);
				}
				if (this.status != null) {
					Element valueNode = dom.createElement("Status");
					Text strValue = dom.createTextNode(this.status);
					valueNode.appendChild(strValue);
					root.appendChild(valueNode);	
				}
				if (this.message != null) {
					Element valueNode = dom.createElement("Message");
					Text strValue = dom.createTextNode(this.message);
					valueNode.appendChild(strValue);
					root.appendChild(valueNode);	
				}	
				if (this.tpcOpId != null) {
					Element valueNode = dom.createElement("TPCOpId");
					Text strValue = dom.createTextNode(this.tpcOpId);
					valueNode.appendChild(strValue);
					root.appendChild(valueNode);
				}
				TransformerFactory tf = TransformerFactory.newInstance();
				Transformer t = tf.newTransformer();
				StringWriter writer = new StringWriter();
				StreamResult res = new StreamResult(writer);
				DOMSource src = new DOMSource(dom);
				t.transform(src, res);
				String xml = writer.toString();
				return xml;
			} catch (Exception e) {
				System.out.println("oh no! bad XML");
				throw new KVException(new KVMessage("resp",null,null,null, "Unknown Error: could not form XML"));
			}
		}

	}
	public static void main(String[] args) {
		KVMessage k = new KVMessage("putreq", "a", "non");
		KVMessage k2 = new KVMessage("getreq", "b", "hollaz");
		KVMessage k3 = new KVMessage("delreq", "a", null);
		KVMessage r1 = new KVMessage("resp", null, null, "true", "Success");
		try {
		ByteArrayInputStream sis = new ByteArrayInputStream((k.toXML()).getBytes());
		ByteArrayInputStream sis2 = new ByteArrayInputStream((k2.toXML()).getBytes());
		ByteArrayInputStream sis3 = new ByteArrayInputStream((k3.toXML()).getBytes());
		//ByteArrayInputStream sis4 = new ByteArrayInputStream((r1.toXML()).getBytes());
			System.out.println(k.toXML());
			System.out.println(k2.toXML());
			System.out.println(k3.toXML());
			String s = convertToString(new Test("death"));
			Object o = convertToGeneralType(s);
			
			System.out.println(o.toString());
			System.out.println("here");
			KVMessage k4 = new KVMessage(sis);
			KVMessage k5 = new KVMessage(sis2);
			KVMessage k6 = new KVMessage(sis3);
			System.out.println(k4.getType());
			System.out.println(k4.getKey());
			System.out.println(k4.getValue());
			System.out.println(k4.getStatus());
			System.out.println(k4.getMessage());
			System.out.println(k5.getType());
			System.out.println(k5.getKey());
			System.out.println(k5.getValue());
			System.out.println(k5.getStatus());
			System.out.println(k5.getMessage());
			System.out.println(k6.getType());
			System.out.println(k6.getKey());
			System.out.println(k6.getValue());
			System.out.println(k6.getStatus());
			System.out.println(k6.getMessage());
			System.out.println(r1.getType());
			System.out.println(r1.getKey());
			System.out.println(r1.getValue());
			System.out.println(r1.getStatus());
			System.out.println(r1.getMessage());
			
		} catch (Exception e) {
			System.out.println("help me");
		}
		
	}
}
