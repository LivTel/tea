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
    public static final String CLASS = "SDH";

    /** Reference to the TEA.*/
    TelescopeEmbeddedAgent tea;

    /** EstarIO for responses.*/
    private eSTARIO io; 
    
    /** GLobusIO handle for responses.*/
    private GlobusIOHandle handle;

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
	//, eSTARIO io, GlobusIOHandle handle) {
	this.tea    = tea;
	//	this.io     = io;
	//this.handle = handle;
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
	
	Observation observation = null;
	Group       group       = null;	 
	
	// Tag/User ID combo is what we expect here.
	
	RTMLContact contact = document.getContact();
	
	if (contact == null) {
	    logger.log(INFO, 1, CLASS, cid,"handleRequest",
		       "RTML Contact was not specified, failing request.");
	    return setError( document,"No contact was supplied");
	}
	
	String userId = contact.getUser();
	
	if (userId == null) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest",
		       "RTML Contact User was not specified, failing request.");
	    return setError( document, "Your User ID was null");
	}
	
	// The Proposal ID.
	RTMLProject project = document.getProject();
	String proposalId = project.getProject();
	
	if (proposalId == null) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest",
		       "RTML Project was not specified, failing request.");
	    return setError( document, "Your Project ID was null");
	}
	
	// We will use this as the Group ID otherwise use 'default agent'.
	RTMLIntelligentAgent userAgent = document.getIntelligentAgent();
	
	if (userAgent == null) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest",
		       "RTML Intelligent Agent was not specified, failing request.");
	    return setError(document, "No user agent: ###TBD Default UA");
	}
	
	String requestId = userAgent.getId();
	
	cid = requestId+"/"+cc;

	// Extract the Observation request(s) - handle multiple obs per doc.
	
	//int nobs = getObservationListCount();
	
	//for (int iobs = 0; iobs < nobs; iobs++) {
	
	RTMLObservation obs = document.getObservation(0);
	//RTMLObservation obs = document.getObservation(iobs);
	
	// Extract params
	RTMLTarget target = obs.getTarget();

	if (target.isTypeTOOP()) {
	    // Try and get TOCSessionManager context.
	    TOCSessionManager sessionManager = TOCSessionManager.getSessionManagerInstance(tea,document);
	    // score the document
	    document = sessionManager.scoreDocument(document);
	    return document;
	} 

	// Ok its a scheduled group, extract the relevant params.
	
	RA  ra  = target.getRA();		    
	Dec dec = target.getDec();      
	 	
	String targetId = target.getName();
	// Bizarre element.
	String targetIdent = target.getIdent();
	
	RTMLSchedule sched = obs.getSchedule();
	
	String expy = sched.getExposureType();
	String expu = sched.getExposureUnits();
	double expt = 0.0;
	
	try {
	    expt = sched.getExposureLengthMilliseconds();
	}
	catch (IllegalArgumentException iax) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest","Unable to extract exposure time:"+iax);
	    return setError(document, "Unable to extract exposure time: "+iax);
	}
	
	int expCount = sched.getExposureCount();
	
	int schedPriority = sched.getPriority();
	
	// Phase2 has no concept of "best seeing" so dont use sc.getMinimum()	 
	double seeing = RequestDocumentHandler.DEFAULT_SEEING_CONSTRAINT; // 1.3
	RTMLSeeingConstraint sc = sched.getSeeingConstraint();
	if (sc != null) {
	    seeing = sc.getMaximum();
	}
	
	// Extract filter info.
	RTMLDevice dev = obs.getDevice();
	String filter = null;

	// make up the IC - we dont have enough info to do this from filtermap...
	InstrumentConfig config = null;
	String configId = null;

	if (dev == null)
	    dev = document.getDevice();
	
	if (dev != null) {
	    
// 	    String type = dev.getType();
// 	    String filterString = dev.getFilterType();
	    
// 	    if (type.equals("camera")) {
		
// 		// We will need to extract the instrument name from the type field.
// 		//String instName = tea.getConfig().getProperty("camera.instrument", "Ratcam");
		
// 		// Check valid filter and map to UL combo
// 		logger.log(INFO, 1, CLASS, cid,"executeRequest","Checking for: "+filterString+".instrument");
// 		filter = tea.getFilterMap().getProperty(filterString+".instrument");
		
// 		if (filter == null) {			
// 		    logger.log(INFO,1,CLASS,cid,"handleRequest","Unknown filter:"+filterString+
// 					   ", failing request.");
// 		    return setError(document, "Unknown filter: "+filterString);
// 		}

// 		// we have a valid filter name, this is wehere we need to make up an IC.

// 		ccdconfig = new CCDConfig("tea:"+cid);
				

// 	    } else {		    
// 		logger.log(INFO,1,CLASS,cid,"handleRequest","Device is not a camera: failing request.");
// 		return setError(document, "Device is not a camera");
// 	    }

	    // START New DEVINST stuff	    
	    try {
		config = DeviceInstrumentUtilites.getInstrumentConfig(tea, dev);
		configId = config.getName();
	    } catch (Exception e) {
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "Device configuration error: "+e);
		return setError(document, "Device configuration error: "+e);
	    }
	    // END New DEVINST stuff

	} else {
	    logger.log(INFO,1,CLASS,cid,"handleRequest", "RTML Device not present");
	    return setError(document, "Device not set");
	}
	
	// Extract MG params - many of these can be null !	
	
	RTMLSeriesConstraint scon = sched.getSeriesConstraint();
	
	int count    = 0;
	long window  = 0L;
	long period  = 0L;	
	Date startDate = null;
	Date endDate   = null;
	
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
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "RTML SeriesConstraint Interval not present, failing request.");
		return setError(document, "No Interval was supplied");
	    } else {
		period = pf.getMilliseconds();
		
		// No Window => Default to 90% of interval.
		if (tf == null) {
		    logger.log(INFO, 1, CLASS, cid,"executeRequest",
			       "No tolerance supplied, Default window setting to 95% of Interval");
		    tf = new RTMLPeriodFormat();
		    tf.setSeconds(0.95*(double)period/1000.0);
		    scon.setTolerance(tf);	  
		}
	    }
	    window = tf.getMilliseconds();
	    
	    if (count < 1) {
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "RTML SeriesConstraint Count was negative or zero, failing request.");
		return setError(document, "You have supplied a negative or zero repeat Count.");
	    }
	    
	    if (period < 60000L) {
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "RTML SeriesConstraint Interval is too short, failing request.");
		return setError(document, "You have supplied a ludicrously short monitoring Interval.");
	    }
	    
	    if ((window/period < 0.0) || (window/period > 1.0)) {
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "RTML SeriesConstraint has an odd Window or Period.");
		return setError(document, "Your window or Tolerance looks dubious.");
	    }
	    
	}
	
	startDate = sched.getStartDate();
	endDate   = sched.getEndDate();
	
	// FG and MG need an EndDate, No StartDate => Now.
	if (startDate == null) {
	    logger.log(INFO, 1, CLASS, cid,"executeRequest","Default start date setting to now");
	    startDate = new Date(now);
	    sched.setStartDate(startDate);
	}
	
	// No End date => StartDate + 1 day (###this is MicroLens -specific).
	if (endDate == null) {
	    logger.log(INFO, 1, CLASS, cid,"executeRequest","Default end date setting to Start + 1 day");
	    endDate = new Date(startDate.getTime()+24*3600*1000L);
	    sched.setEndDate(endDate);
	}
	
	// Basic and incomplete sanity checks.
	if (startDate.after(endDate)) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest","RTML StartDate after EndDate, failing request.");
	    return setError(document, "Your StartDate and EndDate do not make sense."); 
	}
	
	if (expt < 1000.0) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest","Exposure time is too short, failing request.");
	    return setError(document, "Your Exposure time is too short.");
	}
	
	if (expCount < 1) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest",
		       "Exposure Count is less than 1, failing request.");
	    return setError(document, "Your Exposure Count is less than 1.");
	}
	
	logger.log(INFO, 1, CLASS, cid,"executeScore",
		   "Extracted dates: "+startDate+" -> "+endDate);
	
	// Look up proposal details.
	
	String proposalPathName = tea.getDBRootName()+"/"+userId+"/"+proposalId;
	
	
	ExtraSolarSource source = new ExtraSolarSource(targetId);
	source.setRA(ra.toRadians());
	source.setDec(dec.toRadians());
	source.setFrame(Source.FK5);
	source.setEquinox(2000.0f);
	source.setEpoch(2000.0f);
	source.setEquinoxLetter('J');
	
	Position atarg = new Position(ra.toRadians(), dec.toRadians());
	
	Date completionDate = new Date(System.currentTimeMillis()+365*24*3600*1000L);
	
	
	if (scon == null || count == 1) {
	    
	    // FlexGroup 
	    
	    group = new Group(requestId);
	    
	    
	} else {
	    
	    // A MonitorGroup.
	    
	    group = new MonitorGroup(requestId);
	    // MG-Specific
	    ((MonitorGroup)group).setStartDate(startDate.getTime());
	    ((MonitorGroup)group).setEndDate(endDate.getTime());
	    ((MonitorGroup)group).setPeriod(period);
	    ((MonitorGroup)group).setFloatFraction((float)((double)window/(double)period));
	    
	}
	
	group.setPath(proposalPathName);
	
	group.setExpiryDate(endDate.getTime());
	group.setPriority(TelescopeEmbeddedAgent.GROUP_PRIORITY);
	
	group.setMinimumLunar(Group.BRIGHT);
	group.setMinimumSeeing(Group.POOR);
	group.setTwilightUsageMode(Group.TWILIGHT_USAGE_OPTIONAL);
	
	
	float expose = (float)expt;	
	// Maybe split into chunks NO NOT YET.
	//if ((double)expose > (double)tea.getMaxObservingTime()) {
	//int nn = (int)Math.ceil((double)expose/(double)tea.getMaxObservingTime());
	
	//}	    
	int mult = expCount;
	
	observation = new Observation(targetIdent);
	
	observation.setExposeTime(expose);
	observation.setNumRuns(mult);
	observation.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_NEVER);
	
	Mosaic mosaic = new Mosaic();
	mosaic.setPattern(Mosaic.SINGLE);
	observation.setMosaic(mosaic);
	
	observation.setSource(source);	
	observation.setInstrumentConfig(config);

	group.addObservation(observation);
	
	
	// map rtml priorities to Phase2 priorities.
	int priority = 0;
	switch (schedPriority) {
	case 0:
	    priority = 4; // TOOP
	    break;
	case 1:
	    priority = 3; // URGENT
	    break;
	case 2:
	    priority = 2; // MEDIUM
	    break;
	case 3:
	    priority = 1; // NORMAL
	    break;
	default:
	    priority = 1; // NORMAL
	}
	group.setPriority(priority);
	
	// set seeing limits.
	if (seeing >= 1.3) {
	    group.setMinimumSeeing(Group.POOR);
	} else if(seeing >= 0.8) {
	    group.setMinimumSeeing(Group.AVERAGE);
	} else {
	    // this will also catch any with silly values like < 0.0 !
	    group.setMinimumSeeing(Group.EXCELLENT);
	}
	
	double rankScore = 0.0;
	double diff[];
	double cum[];
	double visibility = 0.0;
	
	SCHEDULABILITY tsched = new SCHEDULABILITY(tea.getId()+":"+requestId);
	tsched.setClientDescriptor(new ClientDescriptor("EmbeddedAgent",
							ClientDescriptor.ADMIN_CLIENT,
							ClientDescriptor.ADMIN_PRIORITY));
	tsched.setCrypto(new Crypto("TEA"));
	
	tsched.setGroup(group);
	
	// For calculating whether the target is visible.
	VisibilityCalculator vc = new VisibilityCalculator(tea.getSiteLatitude(),
							   tea.getSiteLongitude(),
							   tea.getDomeLimit(),
							   Math.toRadians(-12.0));

	int npmax = 20;
	try {
	    npmax = Integer.parseInt(System.getProperty("max.granularity", "20"));
	} catch (Exception nx) {
	    logger.log(1, "Error parsing max granularity parameter- defaulting to 20");
	}

	long s1 = 0L;
	long s2 = 0L;
	long resolution = 900000L; //start off at 15M
	long delta = 0L;
	int np = 1;
	int nw = 1;
	if (scon == null) {
	    
	    // Handle Flexible.
	    	  
	    //tsched.setStart(now); // getStartingDate()
	    // why not now - if start < now ?
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

	    visibility = vc.calculateVisibility(atarg,
						startDate.getTime(),
						endDate.getTime());
	    logger.log(INFO, 1, CLASS, cid,"executeScore",
		       "Target scored "+visibility+" visibility for specified single period");
	    
	    
	} else {
	    
	    // Handle Monitor.
	    
	    //tsched.setStart(startDate.getTime()); 
	    // why not now - if start < now ?
	    s1 = Math.max(startDate.getTime(), now);
	    s2 = endDate.getTime();
	    if (s2 <= s1)
		return setError(document, "Start/end time problem");

	    tsched.setStart(s1);
	    tsched.setEnd(s2);	    
	    np = (int)((s2-s1)/period); 
	    nw = np; // same at this point
	    
	    // try to choose a shorter granularity for short period monitors   
	    if (period <= 900000) {
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

	    visibility = vc.calculateVisibility(atarg,
						startDate.getTime(),
						endDate.getTime(),
						period,
						window);
	    logger.log(INFO, 1, CLASS, cid,"executeScore",
		       "Target scored "+visibility+" visibility for specified monitoring program");
	
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
	//freeLock();
	client.send();
	//waitOnLock();
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
	}
	
	// this will return the average score for the group in the specified interval...
	logger.log(INFO, 1, CLASS, cid,"executeScore",
		   "Target achieved rank score "+rankScore+" for specified period after "+((t2-t1)/1000)+"S");
	
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

	//	if (visibility <= 0.0) {
	//  return setError(document, "Target is not observable or unlikely to be selected during the specified period");		    
	//}
	
	document.setScore(rankScore);
	       
	return document;
	
    }

    
    /** Set the error message in the supplied document.
     * @param document The document to modify.
     * @param errorMessage The error message.
     * @throws Exception if anything goes wrong.
     * @return The modified <i>reject</i> document.
     */
    private RTMLDocument setError(RTMLDocument document, String errorMessage) throws Exception {
	//document.setType("reject");
	document.setType("score");
	document.setScore(0.0);
	//	document.setErrorString(errorMessage); 
	
	logger.log(INFO, 1, CLASS, cid, "setError", "SDH::Setting error to: "+errorMessage);
	
	return document;
    }

} // [ScoreDocumentHandler]
