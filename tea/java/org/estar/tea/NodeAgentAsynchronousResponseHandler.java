package org.estar.tea;

import java.rmi.*;

import org.estar.rtml.*;

public interface NodeAgentAsynchronousResponseHandler extends Remote {

    /** Handle an asynchronous response message.*/
    public void handleAsyncResponse(RTMLDocument dec) throws RemoteException;

}
