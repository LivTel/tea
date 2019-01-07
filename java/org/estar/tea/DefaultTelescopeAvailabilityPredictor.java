package org.estar.tea;

import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;

import ngat.util.*;
import ngat.util.logging.*;

/** Default implementation for a Telescope Availability prediction service.
 * Needs to be able to persist this information between reboots incase it
 * does not get updated in the intervening period.
 */
public class DefaultTelescopeAvailabilityPredictor extends UnicastRemoteObject 
    implements  TelescopeAvailabilityPredictor {

    /** The current prediction.*/
    private TelescopeAvailability ta;

    /** Where we store the TAP data for persistance.*/
    private File tapDataFile;

    /** Logging.*/
    private Logger logger;

    /** Create a DefaultTelescopeAvailabilityPredictor using the supplied persistance file.*/
    public DefaultTelescopeAvailabilityPredictor(File tapDataFile) throws RemoteException {
	super();
	this.tapDataFile = tapDataFile;
	logger = LogManager.getLogger("TRACE");
    }

    /** Set the availability prediction for the specified period.
     * @param periodStart start of the period for which prediction is valid.
     * @param periodEnd   end of the period for which prediction is valid.
     * @param prediction The predicted availability. 0=not avail, 1=definitely available.
     * In between values are inceasing likeliness of full availability.
    */
    public void setAvailabilityPrediction(long periodStart, long periodEnd, double prediction) throws RemoteException {
	this.ta = new TelescopeAvailability(periodStart, periodEnd, prediction);

	long duration = periodEnd - periodStart;
	logger.log(1, "Received TAP update: For "+
		   TelescopeEmbeddedAgent.nf.format(duration/3600000)+" Hours, starting "+
		   TelescopeEmbeddedAgent.iso8601.format(new Date(periodStart))+
		   " Predicted availability = "+TelescopeEmbeddedAgent.nf.format(prediction));
	
	// save this info..
	Properties tap = new Properties();
	tap.setProperty("from", TelescopeEmbeddedAgent.iso8601.format(new Date(periodStart)));
	tap.setProperty("to",   TelescopeEmbeddedAgent.iso8601.format(new Date(periodEnd)));
	tap.setProperty("availability", TelescopeEmbeddedAgent.nf.format(prediction));

	try {
	    FileOutputStream fout = new FileOutputStream(tapDataFile);
	    tap.store(fout, "Prediction set on: "+TelescopeEmbeddedAgent.iso8601.format(new Date()));
	    fout.close();
	} catch (Exception e) {
	    throw new RemoteException("Problem persisting TAP update",e);
	}
    }

    /** @return the predicted availability of the telescope over the specified period.
     * The predicted availability. 0=not avail, 1=definitely available.
     * In between values are inceasing likeliness of full availability.
     */
    public TelescopeAvailability getAvailabilityPrediction() throws RemoteException {
	return ta;
    }

    /** Load the persisted data.*/
    public void load() throws RemoteException {
	ConfigurationProperties tap = new ConfigurationProperties();
	try {
	    tap.load(new FileInputStream(tapDataFile));
	    long   periodStart = (TelescopeEmbeddedAgent.iso8601.parse(tap.getProperty("from"))) .getTime();
	    long   periodEnd   = (TelescopeEmbeddedAgent.iso8601.parse(tap.getProperty("to"))).getTime();
	    double prediction  = tap.getDoubleValue("availability");
	    
	    this.ta = new TelescopeAvailability(periodStart, periodEnd, prediction);
	} catch (Exception e) {
	    throw new RemoteException("Problem reading persited TAP data",e);
	}
    }

}
