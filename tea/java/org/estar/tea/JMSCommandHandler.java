package org.estar.tea;

import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.message.base.*;

import java.io.*;
import java.util.*;
import javax.net.ssl.*;

/** Handles responses to commands sent via "Java Message Service (MA) Protocol" (JMS).*/
public class JMSCommandHandler extends JMSMA_ClientImpl implements Logging {
    
    /** Classname for logging.*/
    public static final String CLASS = "JMSClient";

    /** True if the command generated an error.*/
    private volatile boolean error = false;
    
    /** Error code returned by server.*/
    private int errorNum;
    
    /** Error message returned from server.*/
    private String errorMessage = null;
    
    /** Reply from server.*/
    private COMMAND_DONE reply;

    /** ConnectionFactory to use.*/
    private ConnectionFactory cfy;

    /** True if the connection is to be secure.*/
    private boolean secure;

    /** Class logger.*/
    private Logger logger = null;
    
    /** Create a JMSCommandHandler for the supplied command and using the supplied ConnectionFactory.
     * @param cfy The ConnectionFactory.
     * @param command The JMS COMMAND to send.
     * @param secure True if the connection is to be secure.
     */
    public JMSCommandHandler(ConnectionFactory cfy,
		      COMMAND command,
		      boolean secure) {
	super();
	this.cfy     = cfy;
	this.command = command;
	this.secure  = secure;	
	logger = LogManager.getLogger("TRACE");
    }

    /** Called to send the command. This method which delegates to a JMS ProtocolClientImpl 
     * will block until the reply is received from the server or connection fails for some reason.
     */	
    public void send() {

	if (command == null) {
	    setError(true, "Null command");
	    return;
	}

	JMSMA_ProtocolClientImpl protocol   = null;
	IConnection              connection = null;

	if (secure) {
	    // this will need changes to the CFY interface to allow a secure version to be setup.
	    try {
		SSLSocketFactory sf = (SSLSocketFactory)SSLSocketFactory.getDefault();		
		connection = cfy.createConnection("OSS_SECURE");		
		protocol = new JMSMA_ProtocolClientImpl(this, connection);
	    } catch (Exception ex) {
		setError(true, "An error occurred making connection to the OSS: "+ex);
		return;
	    }
	} else {  
	    try {
		connection = cfy.createConnection("OSS");
		protocol = new JMSMA_ProtocolClientImpl(this, connection); 
	    } catch (Exception ex) {
		setError(true, "An error occurred making connection to the OSS: "+ex);
		return;
	    }
	}

	logger.log(INFO, 1, CLASS, "JMS","send",
		   "JMSCommandClient::Connect:"+connection+", Sending ["+command.getClass().getName()+"]");
	protocol.implement();
	
    } // [send]		
    
    /** Handles an ACK response.*/
    public void handleAck  (ACK ack) {
	logger.log(INFO, 1, CLASS, "JMS","handleAck","CMD Client::Ack received");
    }
    
    /** Handles the DONE response. Saves reply and internal parameters and sets error flag if failed.*/
    public void handleDone (COMMAND_DONE response) {
	logger.log(INFO, 1, CLASS, "JMS","handleDone", "CMD Client:: Done recieved:"+response);
	if (response == null) {
	    setError(true, "Response was null");
	    return;
	}
	logger.log(INFO, 1, CLASS, "JMS","handleDone", "CMD Client:: Response status: "+response.getSuccessful());
	if (! response.getSuccessful()) {
	    setError(true, "Error submitting request: "+response.getErrorString()); 
	} else {
	    setError(false, "OSS Command "+command+" accepted");					
	}	
	reply = response;
    }
    
    /** Failed to connect.*/    
    public void failedConnect  (Exception e) {
	setError(true,"Internal error while submitting request: Failed to connect to OSS: "+e);
    }
    
    /** Failed to send command.*/
    public void failedDespatch (Exception e) {
	setError(true,"Internal error while submitting request: Failed to despatch command: "+e);
    }
    
    /** Failed to receive reply.*/
    public void failedResponse  (Exception e) {
	setError(true,"Internal error while submitting request: Failed to get reply: "+e);
    }
	
    /** A general exception.*/
    public void exceptionOccurred(Object source, Exception e) {
	setError(true, "Internal error while submitting request: Exception: "+e);
    }
    
    /** Does nothing.*/
    public void sendCommand(COMMAND command) {}
    
    /** Sets the current error state and message.*/
    private void setError(boolean error, String errorMessage) {
	this.error        = error;
	this.errorMessage = errorMessage;
    }
    
    /** Returns True if there was an error.*/
    public boolean isError() { return error; }
    
    /** Returns the error code.*/
    public int getErrorNum() { return errorNum; }
    
    /** Returns the current error message or null. */
    public String getErrorMessage() { return errorMessage; }
    
    /** Returns the command reply or null.*/
    public COMMAND_DONE getReply() { return reply; }		
    
}// [JMSCommandHandler]
    
