package org.estar.tea;

import java.io.*;
import java.util.*;

import java.net.*;
//import javax.net.ssl.*;
//import javax.security.cert.*;
import java.util.*;
import java.text.*;

import ngat.util.*;
import ngat.util.logging.*;


public class AgentRequestHandlerMonitoringThread extends ControlThread implements Logging {

    /** Class name for logging.*/
    public static final String CLASS = "AgentRequestHandlerMonitor";

    /** Polling interval for pending queue.*/
    private static final long SLEEP_TIME = 5000L;

    /** Reference to the TEA.*/
    TelescopeEmbeddedAgent tea;
  
    /** Class logger.*/
    private Logger logger = null;
    
    /** Create an AgentRequestHandlerMonitoringThread bound to TEA.*/
    public AgentRequestHandlerMonitoringThread(TelescopeEmbeddedAgent tea) {
	super("ARQMON", true);	
	this.tea = tea;
	logger = LogManager.getLogger("ARQ");
    }

    protected void initialise() {}

    protected void mainTask() {

	// sleep for polling interval
	try {Thread.sleep(SLEEP_TIME);} catch (InterruptedException ix) {}

	// TODO clear the kill list

	// check pending job list for all ARQs on the list.
	Map agentMap = tea.getAgentMap();
	Iterator iarq = agentMap.values().iterator();
	while (iarq.hasNext()) {
	    
	    AgentRequestHandler arq = (AgentRequestHandler)iarq.next();
	    logger.log(INFO, 3, CLASS, "-","mainTask", "ARQMON::Testing ARQ: "+arq.getId()+":"+arq.getOid());
	    //TODO  arq.checkPendingList();

	    // TODO if (arq.isReadyForExpiration()) -> move it onto kill list...

	}

	// at this point we now query the ARQs to see if they are ready for termination

	// TODO foreach arq on kill list {
	// TODO     docfile = arq.getDocFile(); 
	// TODO     oid = arq.getOid();
	// TODO     tea.expireDocument(file);	
	// TODO     tea.deleteUpdateHandler(oid);
	// TODO next arq

	// clear the kill list

    }

    protected void shutdown() {}

}
