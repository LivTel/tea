package org.estar.tea;

import java.rmi.*;

import org.estar.rtml.*;

/** Tests the EARH to NAARH callback mechanism.*/
public interface EmbeddedAgentTestHarness extends Remote {

    /** Request to return an RTML <i>update</i> document via the normal NodeAgentAsynchronousResponseHandler mechanism.
     * @param doc The source document.
     * @param howlong How long to wait before doing that which needs doing (ms).     
     */
    public void testUpdateCallback(RTMLDocument doc, long howlong) throws RemoteException;

}
    
