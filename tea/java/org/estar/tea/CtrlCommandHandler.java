package org.estar.tea;

import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.message.base.*;

import java.io.*;
import java.util.*;
    
   
/** Handles responses to commands sent via "Control and Monitoring Protocol" (CAMP).*/
public class CtrlCommandHandler extends CAMPClient {
    
    /** True if the command generated an error.*/
    private volatile boolean error = false;
	
    /** Error message returned from server.*/
    private String errorMessage = null;

    /** Reply from server.*/	
    private COMMAND_DONE reply;
    
    /** Class logger.*/
    private Logger logger = null;
    
    /** Create a CtrlCommandHandler with supplied parameters.
     * @param cfy The ConnectionFactory.
     * @param command The command to send.
     */
    public CtrlCommandHandler(ConnectionFactory cfy,
			      COMMAND command) {
	super(cfy, "CTRL", command);
	logger = LogManager.getLogger(this);
    } 
       
    /** Overwrite to handle any i/o errors detected by CAMP implementor..
     * @param e An exception which was thrown by the CAMP implementor.
     */
    public void failed(Exception e, IConnection connection) {
	if (connection != null)
	    connection.close();
	
	setError(true, "Internal error while submitting request: "+e);
    }
    
    /** Overwrite to handle the response message received from a CAMP implementor.
     * @param update The UPDATE received.
     */
    public void handleUpdate(COMMAND_DONE update, IConnection connection) {
	if (connection != null)
	    connection.close();
	
	if (!update.getSuccessful()) {
	    setError(true, "Error submitting request: "+update.getErrorString()); 	
	} else 
	    setError(false, "Command "+command+" accepted");	    
	reply = update;	
    }

    /** Sets the current error state and message.*/
    private void setError(boolean error, String errorMessage) {
	this.error = error;
	this.errorMessage = errorMessage;
    }
	
    /** Returns True if there is an error.*/
    public boolean isError() { return error; }
    
    /** Returns the current error message or null. */
    public String getErrorMessage() { return errorMessage; }
    
    /** Returns the command reply.*/
    public COMMAND_DONE getReply() { return reply; }		
    
} // [CtrlCommandHandler]]
    
    
