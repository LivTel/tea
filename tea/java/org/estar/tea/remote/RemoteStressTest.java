package org.estar.tea.remote;

import org.estar.tea.*;
import org.estar.rtml.*;

import java.io.*;
import java.rmi.*;

import ngat.util.*;

public class RemoteStressTest {

    public static void main(String args[]) {

	try {

	    CommandTokenizer ct = new CommandTokenizer("--");
	    ct.parse(args);
	    ConfigurationProperties cfg = ct.getMap();

	    File file = new File(cfg.getProperty("rtml"));
	    FileInputStream fin = new FileInputStream(file);

	    RTMLParser parser = new RTMLParser();
	    RTMLDocument document = parser.parse(fin);

	    int number = cfg.getIntValue("number", 1);

	    long interval = cfg.getLongValue("interval", 30*1000L);

	    boolean output = (cfg.getProperty("out")!= null);

	    for (int i = 0; i < number; i++) {

		System.err.println("Starting score run: "+i);

		EmbeddedAgentRequestHandler ear = (EmbeddedAgentRequestHandler)Naming.
		    lookup("rmi://localhost/EARequestHandler");
		System.err.println("Located EAR: "+ear);

		RTMLDocument reply = null;
		if (! document.getType().equals("score"))
		    return;

		long t1 = System.currentTimeMillis();
		reply = ear.handleScore(document);
		long t2 = System.currentTimeMillis();
	   
		System.err.println("Received: Reply after: "+(t2-t1)+" MS");

		if (output) {
		    RTMLCreate create = new RTMLCreate();
		    create.create(reply);
		    System.err.println(create.toXMLString());
		}
		
		// wait a while...
		try {Thread.sleep(interval);} catch (InterruptedException x) {}
		
	    }

	} catch (Exception e) {
	    e.printStackTrace();
	    System.err.println("Usage: RemoteStressTest --rtml <file> --number <n> --interval <ms>");
	    return;
	}

    }

}
