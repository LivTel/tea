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
public class ConnectionHandler implements Logging {

    /** The Classname of this class.*/
    public static final String CLASS = "CH";
    
    /** Session start time.*/
    long   sessionStart;
    
    /** Connection ID.*/
    int connId;

    /** ConnectionHandler ID.*/
    String id;
    
    /** The globus IO handle of the connection.*/
    GlobusIOHandle handle;

    /** The document structure sent by the IA.*/
    RTMLDocument document = null;

    /** The document structure (as String) to return to the IA.*/
    String reply = null;
    
    /** Document parser.*/
    RTMLParser parser;
    
    /** Handles estar communications.*/
    eSTARIO io;
    
    TelescopeEmbeddedAgent tea;

    /** Incoming RTML message string.*/
    String message = null;
    
    // Logging.
    protected Logger traceLog;
    
    protected Logger errorLog;
    
    protected Logger connectLog;
    
    /** */
    ConnectionHandler() {}	
    
    /** Create a ConnectionHandler for the specified Agent and GlobusIOHandle.*/
    ConnectionHandler(TelescopeEmbeddedAgent tea, GlobusIOHandle handle, int connId) {
    	this.tea = tea;
	this.handle = handle;     
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
	
    	traceLog.log(INFO, 1, CLASS, id, "exec", "CH::Connection started.");

	RTMLDocument replyDocument = null;

    	try {
    	    
    	    message = io.messageRead(handle);
	    
    	    traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH::Recieved RTML message: "+message);
	    
    	    // Extract document and determine type.
    	    
    	    parser   = new RTMLParser();
	    parser.init(true);
    	    document = parser.parse(message);

	    //DocChecker checker = new DocChecker();
      	    	    
	    //checker.check(document)) {		
	   
    	    String type = document.getType();
    	    
    	    System.err.println("CH::The doc appears to be a: "+type);    	    
    	    
    	    if (document.isScoreRequest()) {
    		
    		// Do score and return doc.
    			
		ScoreDocumentHandler sdh = new ScoreDocumentHandler(tea);
		//, io, handle);
		replyDocument = sdh.handleScore(document);
		reply = tea.createReply(replyDocument);

		traceLog.log(INFO, 1, CLASS, "CH", "exec",
		     "About to send score response...\n"+reply);
		
		io.messageWrite(handle, reply);
		
		traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH::Sent reply class: "+
			     (replyDocument != null ? replyDocument.getType() : "NULL"));

    	    }
	    else if(document.isRequest())
	    {
    		
    		// Confirm request is scorable.
    
		RequestDocumentHandler rdh = new RequestDocumentHandler(tea);
		//, io, handle);
		replyDocument = rdh.handleRequest(document);
		reply = tea.createReply(replyDocument);

		traceLog.log(INFO, 1, CLASS, "CH", "exec",
			     "About to send request response...\n"+reply);
		
		
		io.messageWrite(handle, reply);
		
		traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH::Sent reply class: "+
			     (replyDocument != null ? replyDocument.getType() : "NULL"));
		
	    }
	    else if(document.isAbort())
	    {

		AbortDocumentHandler adh = new AbortDocumentHandler(tea);
		//, io, handle);
		replyDocument = adh.handleAbort(document);
		reply = tea.createReply(replyDocument);

		traceLog.log(INFO, 1, CLASS, "CH", "exec",
		     "About to send abort response...\n"+reply);
		
		io.messageWrite(handle, reply);
		
		traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH::Sent reply class: "+
			     (replyDocument != null ? replyDocument.getType() : "NULL"));
    	    } 
	    else
	    {
    		// General Error - reject.
    		reply = tea.createErrorDocReply(document,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						"Unknown document type: '"+type+"'");
    		traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH::Sending reply RTML message: "+reply);
    		io.messageWrite(handle, reply);
		traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH::Sent reply class: "+
			     (replyDocument != null ? replyDocument.getType() : "NULL"));
    	    }
	    
    	} 
	catch (Exception ex)
	{
	    traceLog.dumpStack(1, ex);
    	    traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH::Error while processing doc: "+ex);
	    reply = tea.createErrorDocReply(RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
							       "Exception during parsing: "+ex);
	    traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH::Sending reply RTML message: "+reply);
	    try {
		io.messageWrite(handle, reply);
	    } catch (Exception ee) {
		traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH::Error sending error reply doc: "+ee);
		traceLog.dumpStack(1, ee);
	    }
    	} finally {
	
	    traceLog.log(INFO, 1, CLASS, "CH", "exec", "CH:Connection Finished.");
	    
	    handle        = null;
	    document      = null;
	    replyDocument = null;
	    reply         = null;
	}

    } // (handleConnection)
      
   
} //[ConnectionHandler].
    


