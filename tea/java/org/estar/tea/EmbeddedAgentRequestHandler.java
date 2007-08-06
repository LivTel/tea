package org.estar.tea;

import java.rmi.*;

import org.estar.rtml.*;

public interface EmbeddedAgentRequestHandler extends Remote {

    /** Handle a scoring request.*/
    public RTMLDocument handleScore(RTMLDocument doc) throws RemoteException;

    /** Handle a request request.*/
    public RTMLDocument handleRequest(RTMLDocument doc) throws RemoteException;

    /** handle an abort request.*/
    public RTMLDocument handleAbort(RTMLDocument dec) throws RemoteException;

}
