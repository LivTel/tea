package org.estar.tea;

import java.rmi.*;

public interface TelescopeAvailabilityPredictor extends Remote {

    /** Set the availability prediction for the specified period.
     * @param periodStart start of the period for which prediction is valid.
     * @param periodEnd   end of the period for which prediction is valid.
     * @param prediction The predicted availability. 0=not avail, 1=definitely available.
     * In between values are inceasing likeliness of full availability.
    */
    public void setAvailabilityPrediction(long periodStart, long periodEnd, double prediction) throws RemoteException;

    /** @return the predicted availability of the telescope over the specified period.
     * @param periodStart start of the period for which prediction is valid.
     * @param periodEnd   end of the period for which prediction is valid.
     * @param prediction The predicted availability. 0=not avail, 1=definitely available.
     * In between values are inceasing likeliness of full availability.
     */
    public TelescopeAvailability getAvailabilityPrediction() throws RemoteException;

}
