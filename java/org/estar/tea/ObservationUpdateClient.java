package org.estar.tea;

import java.text.*;
import java.util.*;
import java.io.*;
import java.net.*;

import ngat.net.*;
import ngat.net.camp.*;
import ngat.phase2.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.message.base.*;
import ngat.message.GUI_RCS.*;

/** Implements the CAMP protocol as client and response handler.*/
public class ObservationUpdateClient extends TestClient implements CAMPResponseHandler {

    /** Create an ObservationUpdateClient with supplied params.*/
    public ObservationUpdateClient(TELEMETRY_UPDATE telemetry, IConnection connection) {
        super(telemetry, connection);
    }


    public void handleUpdate(COMMAND_DONE update, IConnection connection) {

        System.err.println(command.getId()+" Closing connection.");
	if (connection != null)
	    connection.close();

        System.err.println(command.getId()+" Received CAMP update: "+update);      

    }


    public void failed(Exception e, IConnection connection) {

	System.err.println("Closing connection.");
        if (connection != null)
            connection.close();
	
        System.err.println(command.getId()+" CAMP Error: "+e);
        e.printStackTrace();
       
    }

    /** Send an observation update message.*/
    public static final void main(String args[]) {

	CommandTokenizer ct = new CommandTokenizer("--");
        ct.parse(args);
  
	ConfigurationProperties config = ct.getMap();

	String host = config.getProperty("host", "localhost");
	int    port = config.getIntValue("port", 2233);
	
	boolean completed = (config.getProperty("completed") != null);
	boolean failed    = (config.getProperty("failed") != null);
	boolean reduced    = (config.getProperty("reduced") != null);
	boolean exposure   = (config.getProperty("exposure") != null);
	boolean start     = (config.getProperty("start") != null);
	// can also create a ObsInfo for start of obs.


	String oid = config.getProperty("oid");
	
	TelemetryInfo data = null;
	long now = System.currentTimeMillis();
	if (completed) {
	    data = new ObservationStatusInfo(now,
					     oid,
					     ObservationStatusInfo.COMPLETED,
					     "Observation is complete");
	} else if
	    (failed) {
	    int    failcode = config.getIntValue("code", 0);
	    String reason   = config.getProperty("reason", "Not known");
	    data = new ObservationStatusInfo(now,
					     oid,
					     ObservationStatusInfo.FAILED,
					     reason);	    
	    ((ObservationStatusInfo)data).setErrorCode(failcode);
	} else if 
	    (reduced) {
	    String file = config.getProperty("file");
	    if (file == null) {
		System.err.println("No file supplied for update");
		return;
	    }
	    data = new ReductionInfo(now);
	    ((ReductionInfo)data).setObsPathName(oid);
	    ((ReductionInfo)data).setFileName(file);
	} else if
	      (exposure) {
            String file = config.getProperty("file");
            if (file == null) {
                System.err.println("No file supplied for update");
                return;
            }
            data = new ExposureInfo(now);
            ((ExposureInfo)data).setObsPathName(oid);
            ((ExposureInfo)data).setFileName(file);
	} else if 
	    (start) {	    
	    int mult = config.getIntValue("mult", 1);
	    float exp = config.getFloatValue("expose", 1000.0f);
	    String name = config.getProperty("name");
	    Observation obs = new Observation(name);
	    obs.setPath(oid);
	    obs.setNumRuns(mult);
	    obs.setExposeTime(exp);
	    data = new ObservationInfo(now);
	    ((ObservationInfo)data).setObservation(obs);
	     
	} else {
	    System.err.println("No message type supplied");
	    return;
	}
	
	TELEMETRY_UPDATE telem = new TELEMETRY_UPDATE("TestUpdateClient");
	telem.setData(data);
	
	IConnection connection = null;
	try {
	    connection = new SocketConnection(host, port);

	    System.err.println("Ready to send:["+telem+"]");
	    
	    final ObservationUpdateClient client = new ObservationUpdateClient(telem, connection);

	    (new Thread(client)).start();


	} catch (Exception ex) {
	    System.err.println("An error occurred: "+ex);
	    return;
	}

    }

}
