package org.estar.tea;

import java.rmi.*;
import java.rmi.server.*;

import org.estar.rtml.*;

public class DefaultEmbeddedAgentRequestHandler extends UnicastRemoteObject 
    implements EmbeddedAgentRequestHandler {

    TelescopeEmbeddedAgent tea;

    /** Create a DefaultEmbeddedAgentRequestHandler for the TEA.*/
    public DefaultEmbeddedAgentRequestHandler(TelescopeEmbeddedAgent tea) throws RemoteException {
	this.tea = tea;
    } 

    /** Handle a scoring request.*/
    public RTMLDocument handleScore(RTMLDocument doc) throws RemoteException {
	RTMLDocument reply = null;
	try {
	    ScoreDocumentHandler sdh = new ScoreDocumentHandler(tea);
	    reply = sdh.handleScore(doc);
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

}
