package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
//import javax.net.ssl.*;
//import javax.security.cert.*;
import java.util.*;
import java.text.*;
import javax.net.ssl.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.phase2.*;
import ngat.net.*;
import ngat.net.camp.*;
import ngat.astrometry.*;

import ngat.message.base.*;
import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;

/** Handles a <i>score</i> request.*/
public class ScoreDocumentHandler implements Logging {
     
    /** Classname for logging.*/
    public static final String CLASS = "SDH";

    /** Reference to the TEA.*/
    TelescopeEmbeddedAgent tea;

    /** Class logger.*/
    private Logger logger;

    /** Handler ID.*/
    private String cid; 

    /** SDH counter.*/
    private static int cc = 0;

    /** Create a ScoreDocumentHandler using the supplied IO parameters.
     * @param io      The eSTARIO.
     * @param handle  Globus IO Handle for the connection.
     */
    public ScoreDocumentHandler(TelescopeEmbeddedAgent tea) {

	this.tea    = tea;

	logger = LogManager.getLogger("TRACE");
	cc++;
	cid = "SDH/"+cc;
    }


    /** Called to handle an incoming score document. 
     * Attempts to score the request via the OSS Phase2 DB.
     * @param document The RTML request document.
     * @throws Exception if anything goes wrong.
     */
    public RTMLDocument handleScore(RTMLDocument document) throws Exception {

	// ## START
	logger.log(INFO,1,CLASS,cid,"handleRequest",
		   "Starting scoring request "+cc);
	
	long now = System.currentTimeMillis();
	

	if (document.isTOOP()) {
	    // Try and get TOCSessionManager context.
	    TOCSessionManager sessionManager = TOCSessionManager.getSessionManagerInstance(tea,document);
	    // score the document
	    document = sessionManager.scoreDocument(document);
	    return document;
	} 
	
	// Get the group from the document model
	Phase2GroupExtractor p2x = new Phase2GroupExtractor(tea);
	
	Group group = p2x.extractGroup(document);
	
	// Send the scoring request
	
	double rankScore = 0.0;
	double diff[];
	double cum[];
	
	String failureReasons = null;
	
	SCHEDULABILITY tsched = new SCHEDULABILITY(tea.getId()+":"+document.getUId());
	tsched.setClientDescriptor(new ClientDescriptor("EmbeddedAgent",
							ClientDescriptor.ADMIN_CLIENT,
							ClientDescriptor.ADMIN_PRIORITY));
	tsched.setCrypto(new Crypto("TEA"));
	
	tsched.setGroup(group);
	
	int npmax = 20;
	try {
	    npmax = Integer.parseInt(System.getProperty("max.granularity", "20"));
	    logger.log(1, "Using max granularity: "+npmax);
	} catch (Exception nx) {
	    logger.log(1, "Error parsing max granularity parameter- defaulting to 20");
	}
	
	long s1 = 0L;
	long s2 = 0L;
	long resolution = 900000L; //start off at 15M
	long delta = 0L;
	int np = 1;
	int nw = 1;
	
	
	if (group instanceof MonitorGroup) {

            // Handle Monitor.
            // TODO need to extract these buried schedule parameters again !

            Date startDate = new Date(((MonitorGroup)group).getStartDate());
	    Date endDate   = new Date(((MonitorGroup)group).getEndDate());
	    
            s1 = Math.max(startDate.getTime(), now);
            s2 = endDate.getTime();
            if (s2 <= s1)
                return setError(document, "Start/end time problem");

            tsched.setStart(s1);
            tsched.setEnd(s2);
            np = (int)((s2-s1)/((MonitorGroup)group).getPeriod());
            nw = np; // same at this point

            // try to choose a shorter granularity for short period monitors
            long period = ((MonitorGroup)group).getPeriod();
            if (period <= 900000L) {
                resolution = period/5;
                np = (int)((s2-s1)/resolution);
                logger.log(INFO, 1, CLASS, cid,"executeScore",
                           "Setting resolution for short period monitor to "+(resolution/1000)+" S with "+np+" samples");
                // using p/3 resolution unless too many of them to process
	    } else {
                resolution = period/2;
                np = (int)((s2-s1)/resolution);
                logger.log(INFO, 1, CLASS, cid,"executeScore",
                           "Setting resolution for long period monitor to "+(resolution/1000)+" S with "+np+" samples");
            }
            // now re-adjust back to a sensible number to avoid overloading the processor.
            while (np > npmax) {
                np /= 2;
                resolution = (s2 - s1)/np;
                logger.log(INFO, 1, CLASS, cid,"executeScore",
                           "Adjusting resolution for monitor to "+(resolution/1000)+" S with "+np+" samples");
            }

            tsched.setResolution(resolution);
	    
            delta = resolution;
	    
	    
	} else {
	    
	    // Handle Flexible.
	    
	    s1 = Math.max(group.getStartingDate(), now);
	    s2 = group.getExpiryDate();
	    if (s2 <= s1)
		return setError(document, "Start/end time problem");
	    
            tsched.setStart(s1);
	    tsched.setEnd(s2);	   
	    np = (int)((s2-s1)/resolution);
	    if (np > npmax) {
		np = npmax;
		resolution = (s2 - s1)/np;
	    }
	    
	    tsched.setResolution(resolution); // using 30 minute resolution
	    delta = resolution;
	    
	} 

	logger.log(INFO, 1, CLASS, cid,"executeScore",
		   "Submit to TscoreCalc using: "+
		   " S1    = "+TelescopeEmbeddedAgent.sdf.format(new Date(s1))+
		   " S2    = "+TelescopeEmbeddedAgent.sdf.format(new Date(s2))+
		   " Intvl = "+((s2-s1)/1000)+" S"+
		   " Nwin  = "+nw+
		   " Res   = "+(resolution/1000)+" S"+
		   " NSamp = "+np);
	
	JMSCommandHandler client = new JMSCommandHandler(tea.getConnectionFactory(), 
							 tsched, 
							 tea.getOssConnectionSecure());
	
	long t1 = System.currentTimeMillis();

	client.send();

	long t2 = System.currentTimeMillis();

	if (client.isError()) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest","Internal error during SCHEDULABILITY: "+
		       client.getErrorMessage());
	    return setError(document, "Internal error during SCHEDULABILITY: "+
			    client.getErrorMessage());
	} else {
	    SCHEDULABILITY_DONE sched_done = (SCHEDULABILITY_DONE)client.getReply();
	    rankScore = sched_done.getSchedulability();
	    diff = sched_done.getDifferentialFunction();
	    cum  = sched_done.getCumulativeFunction();
	    failureReasons = sched_done.getFailureReasons();
	}
	
	// this will return the average score for the group in the specified interval...
	logger.log(INFO, 1, CLASS, cid,"executeScore",
		   "Target achieved rank score "+rankScore+" for specified period after "+((t2-t1)/1000)+"S");
	

	if (failureReasons == null) {
	    // No failure reasons for now....
	    document.addHistoryEntry("TEA:"+tea.getId(),null,"Scored document and returned valid score: "+rankScore+".");
	} else {
	    document.addHistoryEntry("TEA:"+tea.getId(),null,"Scored document and returned (score "+
				     rankScore+") with failed schedubility as follows: "+failureReasons);
	}

	for (int in = 0; in < diff.length; in++) {
	    System.err.println("Differential["+in+"] = "+(diff != null ? ""+diff[in] : "null")+
			       " Cumulative["+in+"] = "+(cum != null ? ""+cum[in] : "null"));
	    RTMLScore dscore = new RTMLScore();
	    RTMLPeriodFormat dd = new RTMLPeriodFormat();
	    if (delta > 14400*1000L)		
		dd.setHours((int)(delta*in/3600000));
	    else if
		(delta > 240*1000L)
		dd.setMinutes((int)(delta*in/60000));
	    else
		dd.setSeconds((int)(delta*in/1000));
	    
	    dscore.setDelay(dd);
	    dscore.setProbability(diff[in]);
	    if (cum != null)
		dscore.setCumulative(cum[in]);
	    else
		dscore.setCumulative(Double.NaN);
	    document.addScore(dscore);
	}

	// add this in to zero the score if we dont think the scope is available...
	try {
	    TelescopeAvailability ta = tea.getTap().getAvailabilityPrediction(); 
	    double prediction = ta.getPrediction();
	    long   end        = ta.getEndTime();

	    if (end > now) {
		rankScore = rankScore*prediction;
		logger.log(INFO, 1, CLASS, cid,"executeScore",
			   "Current TAP value: "+ta+" Modified score after TAP = "+rankScore);
	    }
	} catch (Exception e) {
	    logger.log(INFO, 1, CLASS, cid, "executeScore",
		       "An error occurred accessing  TAP");
	    
	}
	document.setScore(rankScore);
	document.setScoreReply();
	
	// 
	if (failureReasons == null) {
	    // No failure reasons for now....
	    document.addHistoryEntry("TEA:"+tea.getId(),null,"Scored document and returned valid score: "+rankScore+".");
	} else {
	    document.addHistoryEntry("TEA:"+tea.getId(),null,"Scored document and returned (score "+
				     rankScore+") with *some* of requested period unschedulable as follows: "+failureReasons);
	}


	return document;
	
    }

    
	/** 
	 * Set the error message in the supplied document.
	 * @param document The document to modify.
	 * @param errorMessage The error message.
	 * @throws Exception if anything goes wrong.
	 * @return The modified <i>reject</i> document.
	 */
	private RTMLDocument setError(RTMLDocument document, String errorMessage) throws Exception 
	{
		//document.setType("reject");
		//document.setErrorString(errorMessage); 
		document.setScoreReply();
		document.setScore(0.0);
		document.addHistoryError("TEA:"+tea.getId(),null,errorMessage,"Score failed.");
		logger.log(INFO, 1, CLASS, cid, "setError", "SDH::Setting error to: "+errorMessage);
		return document;
	}
} // [ScoreDocumentHandler]
