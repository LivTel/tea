package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.*;
import java.text.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.io.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.phase2.*;
import ngat.astrometry.*;

/** Utility to calculate the visibility of a target during a monitoring program.*/
public class VisibilityCalculator {



    /** Telescope latitude(rads).*/
    private double latitude;

    /** Telescope longitude(rads).*/
    private double longitude;

    /** Dome low limit (rads).*/
    private double domeLimit;

    /** Sun elevation limit (rads).*/
    private double sunElevation;


    /** Create a VisibilityCalculator.
     * @param latitude      Telescope latitude (rads).
     * @param longitude     Telescope longitude E (rads).
     * @param domeLimit     Dome low limit (rads).
     * @param sunElevation  Sun elevation for calculating twilight (-12.0 deg = nautical) (rads).
    */
    public VisibilityCalculator(double latitude, double longitude, double domeLimit, double sunElevation) {
	this.latitude  = latitude ;
	this.longitude = longitude;
	this.domeLimit = domeLimit;
	this.sunElevation = sunElevation;
    }

    /** Calculate the visibility between the specified start and end dates for a 
     * Flexibly scheduled group. 
     * @param target    The target position.
     * @param startDate The date to start.
     * @param endDate   The date to end.
     * @return Fraction of time between start and end when target is observable.
     */
    public double calculateVisibility(Position target, long startDate, long endDate) {
	
	if (startDate > endDate)
	    return 0.0;

	int okcount = 0;
	int count   = 0;
	
	long t = startDate;    
	long dt = 60000L;

	//### make DT dependant on ed-sd.
	//long dt = Math.max(60000L, (endDate-startDate)/1000L);

	while (t < endDate) {
		
	    double targ_elev = target.getAltitude(t);
	    
	    String up = (targ_elev > domeLimit ? " UP" : " DN");
		
	    Position sun = Astrometry.getSolarPosition(t);
	    
	    double sun_elev = sun.getAltitude(t);
	    
	    String day = (sun_elev > sunElevation ? " DAY" : " NGT");
	    
	    String obs = (sun_elev <  sunElevation && targ_elev > domeLimit ? " OBSRVE" : " NO_OBS");
	    
	    //System.err.println(sdf.format(new Date(t))+" Elevation: "+Position.toDegrees(targ_elev, 3)+up+" : "+day+" : "+obs);
	    
	    if (sun_elev < sunElevation && targ_elev > domeLimit)
		okcount++;
	    
	    count++;
	    
	    t += dt;
	    
	}

	return (double)okcount/(double)count;
    
    }

       /** Calculate the visibility between the specified start and end dates for a 
     * Monitoring group.
     * @param target    The target position.
     * @param startDate The date to start.
     * @param endDate   The date to end.
     * @param period    The monitor period (millis).
     * @param window    The monitor window (millis).
     * @return Fraction of windows when target is observable for some of time.
     */
    public double calculateVisibility(Position target, long startDate, long endDate, long period, long window) {

	int np = (int)((endDate - startDate)/period);

	int totok = 0;

	int totcount = 0;
	int totokcount = 0;

	for (int ip = 0; ip < np; ip++) {
	    
	    long w1 = startDate + ip*period - window/2;
	    long w2 = startDate + ip*period + window/2;
	    
	    int okcount = 0;
	    int count   = 0;
	    
	    long t = w1;    
	    long dt = 60000L;

	    //### make DT dependant on ed-sd.

	    while (t < w2) {
		
		double targ_elev = target.getAltitude(t);
		
		String up = (targ_elev > domeLimit ? " UP" : " DN");
		
		Position sun = Astrometry.getSolarPosition(t);
		
		double sun_elev = sun.getAltitude(t);
		
		String day = (sun_elev > sunElevation ? " DAY" : " NGT");
		
		String obs = (sun_elev < sunElevation && targ_elev > domeLimit ? " OBSRVE" : " NO_OBS");
		
		//System.err.println(sdf.format(new Date(t))+" Elevation: "+Position.toDegrees(targ_elev, 3)+up+" : "+day+" : "+obs);
		    
		if (sun_elev < sunElevation && targ_elev > domeLimit) {
		    okcount++;
		    totokcount++;
		}

		count++;
		totcount++;

		t += dt;
		    
	    }
	    
	    System.err.println("VC::Moncalc:Window: "+ip+" : "+
			       TelescopeEmbeddedAgent.iso8601.format(new Date(w1))+" - "+
			       TelescopeEmbeddedAgent.iso8601.format(new Date(w2))+
			       " : Observable: "+okcount+"/"+count+" = "+((double)okcount/(double)count));
	    
	    if (okcount > 0 )
		totok++;

	}

	System.err.println("VC::Moncalc:Target visibility scored >0 for: "+totok+" out of "+np+" windows");
	return (double)totokcount/(double)totcount;

    }

}
