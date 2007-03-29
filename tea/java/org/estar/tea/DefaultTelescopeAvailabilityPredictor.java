package org.estar.tea;

import java.rmi.*;
import java.rmi.server.*;

public class DefaultTelescopeAvailabilityPredictor extends UnicastRemoteObject 
    implements  TelescopeAvailabilityPredictor {

    private double available = 1.0;

    public DefaultTelescopeAvailabilityPredictor() throws RemoteException {
	super();	
    }

    /** Set the availability prediction for the specified period.
     * @param periodStart start of the period for which prediction is valid.
     * @param periodEnd   end of the period for which prediction is valid.
     * @param prediction The predicted availability. 0=not avail, 1=definitely available.
     * In between values are inceasing likliness of full availability.
    */
    public void setAvailabilityPrediction(long periodStart, long periodEnd, double prediction) throws RemoteException {
	this.available = prediction;
    }

    /** @return the predicted availability of the telescope over the specified period.
     * @param periodStart start of the period for which prediction is valid.
     * @param periodEnd   end of the period for which prediction is valid.
     * @param prediction The predicted availability. 0=not avail, 1=definitely available.
     * In between values are inceasing likliness of full availability.
     */
    public double getAvailabilityPrediction(long periodStart, long periodEnd) throws RemoteException {
	return available;
    }

}
