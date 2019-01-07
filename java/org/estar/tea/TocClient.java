package org.estar.tea;

import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;

import java.io.*;
import java.util.*;

/** Handles responses to commands sent via "Target of Opportunity Control Protocol" (TOCP).
 */
public class TocClient implements Logging {
    
    /** Classname for logging.*/
    public static final String CLASS = "TOCClient";

    /** True if the command generated an error.*/
    private volatile boolean error = false;
    
    /** TOCS Server IP Address.*/
    private String host;
    
    /** TOCS Server port.*/
    private int port;
    
    /** Command to send.*/
    private String command;
    
    /** Error message from TOCS server.*/
    private String errorMessage = null;
    
    /** Response from TOCS server.*/
    private String reply;
    
    /** The TelnetConnection to use to connect to the TOCS server.*/
    private TelnetConnection tc;

    /** Class logger.*/
    private Logger logger = null;
    
    /** Create a TOCClient using the supplied parameters.
     * @param command The command string to send.
     * @param host    The TOCS Server IP Address.
     * @param port    The TOCS Server port.
     */
    TocClient(String command, String host, int port) {
	this.command = command;
	this.host = host;
	this.port = port;
	logger = LogManager.getLogger(this);
    }
	
  
    /** Called to send the command. This method which delegates to the TelnetConnection
     * will block until the reply is received from the server or connection fails for some reason.
     * A single line command is sent and a single line reply is expected. The connection will be 
     * closed by this client after receiving this line, any extra lines are lost.
     */	
    public void run() {
	
	try {
	    logger.log(INFO, 1, CLASS, "TC","run","TOCClient::Connecting to "+host+":"+port);
	    tc = new TelnetConnection(host, port);
	    
	    try {
		tc.open();
		logger.log(INFO, 1, CLASS, "TC","run","TOCClient::Opened connection");
	    } catch (Exception e) {
		setError(true, "Failed to open connection to TOCS: "+e);
		return;
	    }
	    tc.sendLine(command);
	    logger.log(INFO, 1, CLASS, "TC","run","TOCClient::Sent ["+command+"]");
	    try {
		reply = tc.readLine();
		logger.log(INFO, 1, CLASS, "TC","run","TOCClient::Reply ["+reply+"]");
		if (reply == null ||
		    reply.equals("")) {
		    setError(true, "Null reply from TOCS");
		    return;
		}
		reply = reply.trim();
		if (reply.startsWith("ERROR")) {
		    setError(true, reply.substring(5));
		    return;
		}
		
	    } catch (Exception e) {
		setError(true, "Failed to read TOCS response: "+e);
		return;
	    }	
	    
	    setError(false, "Command temporarily accepted by TOCS");
	    
	} catch (Exception e) {
	    setError(true, "Failed to read TOCS response: "+e);
	    return;		
	} finally {
	    logger.log(INFO, 1, CLASS, "TC","run","TOCClient::Closing connection");
	    try {
		tc.close();
	    } catch (Exception e) {
		// We dont really care..
		logger.dumpStack(1,e);
	    }	 	  
	}
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
    public String getReply() { return reply; }		

}
    
