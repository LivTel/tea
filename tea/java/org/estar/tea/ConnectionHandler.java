package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
//import javax.net.ssl.*;
//import javax.security.cert.*;
import java.util.*;
import java.text.*;


import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.io.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.net.*;
import ngat.astrometry.*;

import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;

/** Handles a UA Transaction.*/
public class ConnectionHandler implements Serializable, Logging {

    /** The Classname of this class.*/
    public static final String CLASS = "ConnectionHandler";
    
    /** Session start time.*/
    long   sessionStart;
    
    /** Connection ID.*/
    int connId;

    String id;
    
    /** The globus IO handle of the connection.*/
    transient GlobusIOHandle connectionHandle;

    /** The document structure sent by the IA.*/
    RTMLDocument document = null;

    /** The document structure (as String) to return to the IA.*/
    transient String reply = null;
    
    /** Document parser.*/
    transient RTMLParser parser;
    
    /** Handles estar communications.*/
    transient eSTARIO io;
    
    transient TelescopeEmbeddedAgent tea;

    /** Incoming RTML message string.*/
    String message = null;
    
    // Logging.
    protected Logger traceLog;
    
    protected Logger errorLog;
    
    protected Logger connectLog;
    
    /** Compatibility for java.io.Serializable */
    ConnectionHandler() {}	
    
    /** Create a ConnectionHandler for the specified Agent and GlobusIOHandle.*/
    ConnectionHandler(TelescopeEmbeddedAgent tea, GlobusIOHandle connectionHandle, int connId) {
    	this.tea = tea;
	this.connectionHandle = connectionHandle;     
	this.connId = connId;
	id = "CH"+connId;

	traceLog   = LogManager.getLogger("TRACE");
	errorLog   = LogManager.getLogger("ERROR");
	connectLog = LogManager.getLogger("CONNECT");	
	
	io = tea.getEstarIo();	
		
    }
    
    /** Handle the connection.*/
    public void execute() {

    	sessionStart = System.currentTimeMillis();
	
    	traceLog.log(INFO, 1, CLASS, id, "exec", "CONN: Connection started.");
    	
    	try {
    	    
    	    message = io.messageRead(connectionHandle);
	    
    	    traceLog.log(INFO, 1, CLASS, id, "exec", "CONN: Recieved RTML message: "+message);
	    
    	    // Extract document and determine type.
    	    
    	    parser   = new RTMLParser();
    	    document = parser.parse(message);

	    //DocChecker checker = new DocChecker();
      	    	    
	    //checker.check(document)) {		
	   
    	    String type = document.getType();
    	    
    	    System.err.println("CONN: The doc appears to be a: "+type);    	    
    	    
    	    if (type.equals("score")) {
    		
    		// Do score and return doc.
    			
		AgentRequestHandler arq = new AgentRequestHandler(tea);
    		arq.executeScore(document, connectionHandle);

    	    } else if
    		(type.equals("request")) {
    		
    		// Confirm request is scorable , return doc and start RequestHandler.
    				
    		AgentRequestHandler arq = new AgentRequestHandler(tea);
    		arq.executeRequest(document, connectionHandle);
		
    	    } else {
    		
    		// Error - reject.
		
    		reply = tea.createErrorDocReply(document, "Unknown document type: '"+type+"'");
		
    		traceLog.log(INFO, 1, CLASS, id, "exec", "CONN: Sending reply RTML message: "+reply);
		
    		io.messageWrite(connectionHandle, reply);
		
    	    }
	    
    	} catch (Exception ex) {
    	    traceLog.log(INFO, 1, CLASS, id, "exec", "SERVER:Error parsing doc: "+ex);
	    reply = tea.createErrorDocReply("Exception during parsing: "+ex);
	    io.messageWrite(connectionHandle, reply);

    	} 
	
    	traceLog.log(INFO, 1, CLASS, id, "exec", "SERVER:Connection Finished.");
    	
    	connectionHandle = null;
    	document = null;
    	reply = null;
	
    } // (handleConnection)
    
    
   
} //[ConnectionHandler].
    


