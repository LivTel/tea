package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.*;
import java.text.*;


import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.io.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.net.*;
import ngat.net.camp.*;
import ngat.astrometry.*;

import ngat.message.base.*;
import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;


public class TelemetryRequestor extends ControlThread {
    
    /** Command request counter - increments each time a TELEM command is sent.*/
    private static int cc = 0;

    /** Polling time (ms).*/
    private long time;

    /** The TEA.*/
    private TelescopeEmbeddedAgent tea;
    
    /** Connection Setup Information.*/
    private ConnectionSetupInfo conset;

    /** Create a TelemetryRequestor.
     * @param tea  The TEA.
     * @param time Polling time (ms).
     */
    public TelemetryRequestor(TelescopeEmbeddedAgent tea, long time) {
	super("TELREQ", true);
	this.tea = tea;
	this.time = time;
	conset = new ConnectionSetupInfo(System.currentTimeMillis(), 
					 ConnectionSetupInfo.CAMP, 
					 tea.getTelemetryHost(), 
					 tea.getTelemetryPort());
    }
    
    protected void initialise() {}


    
    protected void mainTask() {

	TELEMETRY tel = new TELEMETRY("TELREQ:"+(++cc));
	tel.setConnect(conset);
	tel.setClientId("TEA");
	
	Vector wants = new Vector();
	wants.add(ReductionInfo.class);
	wants.add(ObservationInfo.class);
	wants.add(ObservationStatusInfo.class);


	tel.setWants(wants);
	
	System.err.println("Contacting OCC on: "+tea.getCtrlHost()+":"+tea.getCtrlPort());
	final RequestorClient client = new RequestorClient(new SocketConnection(tea.getCtrlHost(), tea.getCtrlPort()), tel);
	(new Thread(client)).start();
	
	try {Thread.sleep(time);} catch (InterruptedException ix) {}
    
    }

    protected void shutdown() {}

    /** Request sender.*/
    private class RequestorClient extends CAMPClient implements CAMPResponseHandler {

	//String host;
	//int    port;

	public RequestorClient(IConnection connection, GUI_TO_RCS command) {
	    super(connection, command);
	    // host = ((TELEMETRY)command).getConnect().host;
	    //port = ((TELEMETRY)command).getConnect().port;
	}
	
	/** handle telemetry request response.*/
	public void handleUpdate(COMMAND_DONE update, IConnection connection) {
	    
	    System.err.println(command.getId()+" Closing connection.");
	    if (connection != null)
		connection.close();
	    connection = null;  

	    if (! (update instanceof TELEMETRY_DONE)) {
		System.err.println(command.getId()+" CAMP Error: Unexpected class: "+update);
		return;
	    }
	    
	    if (update instanceof TELEMETRY_DONE) {
		
		TELEMETRY_DONE sd = (TELEMETRY_DONE)update;
		
		if (update.getSuccessful()) {
		    // Dont care at the mo.
		} else {
		    // Dont care at the mo.
		    int    errno  = update.getErrorNum();
		    String errmsg = update.getErrorString();
		    		    
		}
		
	    } 	    
	    
	}
	
	/** Handle failed telemtry request.*/
	public void failed(Exception e, IConnection connection) {
	    
	    System.err.println(command.getId()+"TELEMETRY:CAMP Error: "+e);  
	    e.printStackTrace();
	    if (connection != null)
		connection.close();	
	    connection = null;  
	}        
	
    } // [RequestorClient]

    
} // [TelemetryRequestor]

