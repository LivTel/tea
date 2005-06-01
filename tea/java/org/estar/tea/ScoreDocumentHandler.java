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
import org.estar.io.*;

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
    public static final String CLASS = "ScoreDocHandler";

    /** Reference to the TEA.*/
    TelescopeEmbeddedAgent tea;

    /** EstarIO for responses.*/
    private eSTARIO io; 
    
    /** GLobusIO handle for responses.*/
    private GlobusIOHandle handle;

    /** Class logger.*/
    private Logger logger;

    /** Create a ScoreDocumentHandler using the supplied IO parameters.
     * @param io      The eSTARIO.
     * @param handle  Globus IO Handle for the connection.
     */
    public ScoreDocumentHandler(TelescopeEmbeddedAgent tea, eSTARIO io, GlobusIOHandle handle) {
	this.tea    = tea;
	this.io     = io;
	this.handle = handle;
	logger = LogManager.getLogger("TRACE");
    }


    /** Called to handle an incoming score document. 
     * Attempts to score the request via the OSS Phase2 DB.
     * @param document The RTML request document.
     * @param handle   Handle for the return connection.
     * @throws Exception if anything goes wrong.
     */
    public RTMLDocument handleScore(RTMLDocument document) throws Exception {
    
	long now = System.currentTimeMillis();
	
	RTMLObservation obs = document.getObservation(0);
	
	if (obs == null) {
	    return setError(document, "There was no observation in the request");
	}
	
	RTMLTarget target = obs.getTarget();
	
	if (target == null) {
	    return setError(document, "There was no target in the request");	 
	}
	
	RA      ra   = target.getRA();	
	Dec     dec  = target.getDec();
	boolean toop = target.isTypeTOOP();
	
	if (ra == null || dec == null) {
	    return setError(document, "Missing ra/dec for target");
	}
	
	// Convert to rads and compute elevation - we dont use this for veto now.
	double rad_ra = Math.toRadians(ra.toArcSeconds()/3600.0);
	double rad_dec= Math.toRadians(dec.toArcSeconds()/3600.0);
	
	Position targ = new Position(rad_ra, rad_dec);
	
	double elev = targ.getAltitude();
	double tran = targ.getTransitHeight();
	
	logger.log(INFO, 1, CLASS, "SH","executeScore","INFO:Target at: "+targ.toString()+
		   "\n Elevation:   "+Position.toDegrees(elev,3)+
		   "\n Transits at: "+Position.toDegrees(tran,3));
	
	RTMLSchedule sched = obs.getSchedule();
	
	// Test for sched == null
	if (sched == null) {
	    return setError(document, "No schedule was specified");
	}
	
	// May not need this or they may be wanted to predict future completion time?
	

	//#### does Schedule have a getExposureTimeMillis() or the likes ?
	//### may need to throw a wobbly if the units are not understood.
	String expy = sched.getExposureType();
	String expu = sched.getExposureUnits();
	double expt = 0.0;
	
	try {
	    expt = sched.getExposureLengthMilliseconds(); 
	} catch (IllegalArgumentException iax) {
	    return setError(document, "Unable to extract exposure time: "+iax);
	}
	
	if (sched.isExposureTypeSNR()) {
	    return setError(document, "Sorry, we cant handle SNR requests at the mo.");
	}

	int expCount = sched.getExposureCount();

	// Extract MG params - many of these can be null !	
	
	RTMLSeriesConstraint scon = sched.getSeriesConstraint();
	
	int count    = 0;
	long window  = 0L;
	long period  = 0L;	
	Date startDate = null;
	Date endDate   = null;

	// Check what type of group we are being asked for.

	if (scon == null) {

	    // No SC supplied => FlexGroup.
	    count = 1;

	} else {

	    // SC supplied => MonitorGroup.
	    
	    count = scon.getCount();
	    RTMLPeriodFormat pf = scon.getInterval();
	    RTMLPeriodFormat tf = scon.getTolerance();
	    
	    // No Interval => Wobbly
	    if (pf == null) { 
		return setError(document, "No Interval was supplied");
	    } else {
		period = pf.getMilliseconds();
		
		// No Window => Default to 90% of interval - is this best bet
		// -> big windows  = poor periodicity, high hit count
		// -> small window = good periodicity, low hit count

		if (tf == null) {
		    logger.log(INFO, 1, CLASS, "SH", "executeScore",
			       "No tolerance supplied, Default window setting to 95% of Interval");
		    tf = new RTMLPeriodFormat();
		    tf.setSeconds(0.95*(double)period/1000.0);
		    scon.setTolerance(tf);	  
		}
	    }
	    window = tf.getMilliseconds();
	    
	    if (count < 1) {
		return setError(document, "You have supplied (or I deduced) a negative or zero repeat Count.");
	    }
	    
	    if (period < 60000L) {
		return setError(document, "You have supplied too short a monitoring Interval - try at LEAST a minute.");
	    }
	    
	    if ((window/period < 0.0) || (window/period > 1.0)) {
		return setError(document, "Your window or Tolerance looks dubious.");
	    }
	    
	    
	}
	
	startDate = sched.getStartDate();
	endDate   = sched.getEndDate();
	
	// FG and MG need an EndDate, No StartDate => Now.
	if (startDate == null) {
	    logger.log(INFO, 1, CLASS, "SH","executeScore",
		       "No start date suppled, Default start date setting to now");
	    startDate = new Date(now);
	    sched.setStartDate(startDate);
	}
	
	// No End date => StartDate + 1 day (###this is MicroLens -specific).
	if (endDate == null) {
	    logger.log(INFO, 1, CLASS, "SH","executeScore",
		       "No end date supplied, Default end date setting to Start + 1 day");
	    endDate = new Date(startDate.getTime()+24*3600*1000L);
	    sched.setEndDate(endDate);
	}
	
	// Basic and incomplete sanity checks.
	if (startDate.after(endDate)) {
	    return setError(document, "StartDate and EndDate do not make sense.");
	}
	
	if (expt < 1000.0) {
	    return setError(document, "Exposure time is too short - at LEAST one second.");
	}
	
	if (expCount < 1) {
	    return setError(document, "Exposure Count is less than 1.");
	}
	
	// ### This should be when we expect to be able to complete by...
	// but is endDate for now
	document.setCompletionTime(endDate);
	
	if (toop) {
	    
	    // ### Send a WHEN based on our serviceID.
	    String when = "WHEN "+tea.getTocsServiceId();
	    TocClient client = new TocClient(when, tea.getTocsHost(), tea.getTocsPort());
	    
	    //setLock();	
	    logger.log(INFO, 1, CLASS, "SH","executeScore","Sending WHEN request to TOCS");
	    client.run();
	    //waitOnLock();
	    
	    if (client.isError()) {		    
		return setError(document, 
				"Unable to invoke TO Service:"+tea.getTocsServiceId()+" : "+client.getErrorMessage());
	    }
	    
	    String reply = client.getReply();
	    
	    // ### Temp fail these.
	    return setError(document, "Targets of type toop are not yet implemented");
	    
	    // ###  We allow TOOP to take over anyway for now score is MAX.
	    //document.setScore(0.99);   
	    //sendDoc(document, "score");

	    
	} else {

	    // Non toop
	    
	    // Extract filter info.
	    RTMLDevice dev = obs.getDevice();
    	    
	    if (dev == null)
		dev = document.getDevice();
	    
	    if (dev != null) {
		
		String type = dev.getType();
		String filterString = dev.getFilterType();
		
		if (type.equals("camera")) {
		    
		    // We will need to extract the instrument name from the type field.
		    //String instName = tea.getConfig().getProperty("camera.instrument", "Ratcam");
		    
		    // Check valid filter and map to UL combo
		    logger.log(INFO, 1, CLASS, "SH","executeScore","Checking for: "+filterString);
		    String filter = tea.getFilterMap().
			getProperty(filterString);
		    
		    if (filter == null) {			
			return setError(document, "Unknown filter: "+filterString);
		    }
		} else {		    
		    return setError(document, "Device is not a camera");
		}
	    } else {
		return setError(document, "Device not set");
	    }
	    
	    
	    // We should send a request to the OSS to test-schedule here.
	    if (tran < tea.getDomeLimit()) {     
		// Never rises at site.
		logger.log(INFO, 1, CLASS, "SH","executeScore","Target NEVER RISES");
		return setError(document, 
				"Target transit height: "+Position.toDegrees(tran,3)+
				" is below dome limit: "+Position.toDegrees(tea.getDomeLimit(),3)+
				" so will never be visible at this site");
	    } 
	    
	    
	    
	    // else if (elev < Math.toRadians(20.0) ) {
	    // 		// Currently too low to see.
	    // 		System.err.println("ARQ:INFO:Target LOW");
	    // 		return setError(document, 
	    // 			  "Target currently at elevation: "+Position.toDegrees(elev, 3)+
	    // 			  " is not visible at this time"); 	
	    // 		return;
	    //  } else {    
	    
	    // ### COMMENTED OUT AS WE ARE EXPECTING MONITORS FOR NOW
	    
	    
	    // 		// Determine telescope status - maybe this could be done by a regular polling from 
	    // 		// another thread as if from a GUI. Note we use CAMP rather than JMSMA for GUI commands !
	    
	    // 		ID getid = new ID("TEA");
	    
	    // 		CtrlCommandHandler client = new CtrlCommandHandler(tea.getConnectionFactory(), getid);
	    
	    // 		freeLock();	
	    // 		client.run();
	    // 		waitOnLock();
	    
	    // 		if (client.isError()) {		    
	    // 		    return setError(document, 
	    // 			      "Unable to retrieve RCS operational status: "+client.getErrorMessage());
	    // 		    return;
	    // 		}
	    
	    // 		ID_DONE idd = (ID_DONE)client.getReply();
	    
	    
	    
	    // 		if (!idd.getOperational()) {
	    // 		    return setError(document, "The telescope is not currently operational");
	    // 		    return;
	    // 		}
	    
	    // 		// This is rather telescope-specific.
	    
	    // 		if ("PCA".equals(idd.getAgentInControl()) ||
	    // 		    "TOCA".equals(idd.getAgentInControl())) {
	    // 		    return setErryor(document, 
	    // 			      "A high priority agent is controlling the scope: "+
	    // 			      idd.getAgentInControl()+", Please try again later");
	    // 		    return;
	    // 		}
	    
	    // ### END OF COMMENT OUT
	    
	    
	    // ### SCA mode and target visible and p5? so score.
	    logger.log(INFO, 1, CLASS, "SH","executeScore","Target OK Score = "+(2.0*tran/Math.PI));
	    //document.setScore(elev/tran);

	    document.setScore(2.0*tran/Math.PI);
	    return document;
	    
	}
    }
    	
    /** Set the error message in the supplied document.
     * @param document The document to modify.
     * @param errorMessage The error message.
     * @throws Exception if anything goes wrong.
     * @return The modified <i>reject</i> document.
     */
    private RTMLDocument setError(RTMLDocument document, String errorMessage) throws Exception {
	document.setType("reject");
	document.setErrorString(errorMessage); 
	return document;
    }

} // [ScoreDocumentHandler]
