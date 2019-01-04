package org.estar.tea.remote;

import org.estar.tea.*;
import org.estar.rtml.*;

import java.io.*;
import java.rmi.*;

public class TestRemoteClient {

    public static void main(String args[]) {

	try {

	    File file = new File(args[0]);
	    FileInputStream fin = new FileInputStream(file);

	    RTMLParser parser = new RTMLParser();
	    parser.init(true);
	    RTMLDocument document = parser.parse(fin);

	    EmbeddedAgentRequestHandler ear = (EmbeddedAgentRequestHandler)Naming.lookup("rmi://localhost/EARequestHandler");
            System.err.println("Located EAR: "+ear);

	    RTMLDocument reply = null;
	    if (document.getType().equals("score"))
		reply = ear.handleScore(document);
	    else if
		(document.getType().equals("request"))
		reply = ear.handleRequest(document);

	    System.err.println("Received: \n"+reply);

	} catch (Exception e) {
	    e.printStackTrace();
	    return;
	}

    }

}
