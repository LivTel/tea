package org.estar.tea.remote;

import org.estar.tea.*;
import org.estar.rtml.*;
import java.io.*;
import java.rmi.*;

public class TestAsynchClient {

    public static void main(String args[]) {

	try {
	    
	    File file = new File(args[0]);
	    FileInputStream fin = new FileInputStream(file);

	    RTMLParser parser = new RTMLParser();
	    RTMLDocument document = parser.parse(fin);

	    String narHost = args[1];

	    NodeAgentAsynchronousResponseHandler narh = (NodeAgentAsynchronousResponseHandler)Naming.
		lookup("rmi://"+narHost+"/NAAsyncResponseHandler");
	    
	    if (narh == null) {
		System.err.println("NA ResponseHandler was null.");
		return;
	    }
	    
	    narh.handleAsyncResponse(document);
	    
	    System.err.println("Sent document successfully via NA ResponseHandler");

	} catch (Exception e) {

	}

    }

}
