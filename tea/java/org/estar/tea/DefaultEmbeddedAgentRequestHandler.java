package org.estar.tea;

import java.rmi.*;
import java.rmi.server.*;

import ngat.util.logging.*;
import org.estar.rtml.*;

public class DefaultEmbeddedAgentRequestHandler extends UnicastRemoteObject 
    implements EmbeddedAgentRequestHandler, EmbeddedAgentTestHarness {

    TelescopeEmbeddedAgent tea;

    Logger logger;


    /** Create a DefaultEmbeddedAgentRequestHandler for the TEA.*/
    public DefaultEmbeddedAgentRequestHandler(TelescopeEmbeddedAgent tea) throws RemoteException {
	this.tea = tea;
	logger = LogManager.getLogger("TRACE");
    } 

    /** Handle a scoring request.*/
    public RTMLDocument handleScore(RTMLDocument doc) throws RemoteException {
	RTMLDocument reply = null;
	try {
	    ScoreDocumentHandler sdh = new ScoreDocumentHandler(tea);	
	    reply = sdh.handleScore(doc);
	    logger.log(1, "ScoreDocHandler returned doc: "+reply);
	} catch (Exception e) { 	  
	    throw new RemoteException("Exception while handling score: "+e);
	}
	return reply;
    }
    
    /** Handle a request request.*/
    public RTMLDocument handleRequest(RTMLDocument doc) throws RemoteException {
	RTMLDocument reply = null;
	try {
	    RequestDocumentHandler rdh = new RequestDocumentHandler(tea);
	    reply = rdh.handleRequest(doc);
	    logger.log(1, "RequestDocHandler returned doc: "+reply);
	} catch (Exception e) {
	    throw new RemoteException("Exception while handling request: "+e);
	}
	return reply;
    }

    /** handle an abort request.*/
    public RTMLDocument handleAbort(RTMLDocument doc) throws RemoteException {
	RTMLDocument reply = null;
	try {	
	    AbortDocumentHandler adh = new AbortDocumentHandler(tea);
	    reply = adh.handleAbort(doc);
	} catch (Exception e) {
	    throw new RemoteException("Exception while handling abort: "+e);
	}
	return reply;
    }

    /** Request to return an RTML <i>update</i> document via the normal NodeAgentAsynchronousResponseHandler mechanism.
     * @param doc The source document.
     * @param howlong How long to wait before doing that which needs doing (ms).     
     */
    public void testUpdateCallback(RTMLDocument doc, long howlong) throws RemoteException {
	
	RTMLIntelligentAgent userAgent = doc.getIntelligentAgent();
	String agid = "Unknown";
	String host = "Unknown";
	int    port = -1;
	if(userAgent == null) {
	    traceLog.log(INFO, 1, CLASS, "testUpdateCallback: Warning, User agent was null.");
	} else {
	    agid = userAgent.getId();
	    host = userAgent.getHostname();
	    port = userAgent.getPort();
	    logger.log(1, "Testharness: testUpdateCallback: Sending update to: "+agid+"@ "+host+":"+port+" in "+howlong+" msec");
	}

	doc.setType("update");
	
	final TelescopeEmbeddedAgent mytea = tea;
	final long myhowlong = howlong;
	final String myagent = agid+"@ "+host+":"+port;
	Runnable r = new Runnable() {
		public void run() {
		    try {	
			try {Thread.sleep(myhowlong);} catch (InterruptedException ix) {}
			mytea.sendDocumentToIA(doc);
			logger.log(1, "Testharness: testUpdateCallback: Sent update to: "+myagent);
		    } catch (Exception e) {
			logger.log(1, "An error occurred during TestHarness callback test: "+e);
			e.printStackTrace();
		    }
		}
	    };
	
	Thread thread = new Thread(r);
	thread.start();
	
    }


}
