package org.estar.tea;

import java.rmi.*;

import org.estar.rtml.*;

/**
 * RMI interface for the TEA.
 */
public interface EmbeddedAgentRequestHandler extends Remote 
{

    /** 
     * Handle a scoring request.
     * @param doc The RTML document to score.
     * @return A scored RTML document, or an error document describing what went wrong.
     */
    public RTMLDocument handleScore(RTMLDocument doc) throws RemoteException;

    /** 
     * Handle a request request.
     * @param doc The RTML document to submit to the PhaseII, or take over the telescope as a TOOP observation.
     * @return A RTML document confirming the request, or an error document describing what went wrong.
     */
    public RTMLDocument handleRequest(RTMLDocument doc) throws RemoteException;

    /** 
     * Handle an abort request.
     * @param doc The RTML document to abort processing on (delete from PhaseII).
     * @return A RTML document confirming successful deletion, or an error document describing what went wrong.
     */
    public RTMLDocument handleAbort(RTMLDocument dec) throws RemoteException;

}
