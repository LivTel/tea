package org.estar.tea.remote;

import org.estar.tea.*;
import org.estar.rtml.*;

import java.io.*;
import java.rmi.*;

public class RemoteTapTestClient {

    public static void main(String args[]) {

	try {

	    long start   = TelescopeEmbeddedAgent.sdf.parse(args[0]).getTime();
	    long end     = TelescopeEmbeddedAgent.sdf.parse(args[1]).getTime();
	    double avail = Double.parseDouble(args[2]);

	    TelescopeAvailabilityPredictor tap  = (TelescopeAvailabilityPredictor)Naming.lookup("rmi://localhost/TAPredictor");
            System.err.println("Located TAP: "+tap);

	    tap.setAvailabilityPrediction(start,end,avail);

	    TelescopeAvailability ta = tap.getAvailabilityPrediction();

	    System.err.println("After requesting the TA back we got: "+ta);

	    long et = ta.getEndTime();
	    long now = System.currentTimeMillis();

	    if (et > now)
		System.err.println("Availability is "+ta.getPrediction()+" for next "+((et-now)/3600000)+" hours");
	    else
		System.err.println("Availability est has expired, assume full availability");


	} catch (Exception e) {
	    e.printStackTrace();
	    System.err.println("Usage: RemoteTapTestClient <start> <end> <pred> - use (yyyyMMddHHmmss) ");
	    return;
	}

    }

}
