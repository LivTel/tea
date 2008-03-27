// $Header: /space/home/eng/cjm/cvs/tea/java/org/estar/tea/RequestDocumentHandler.java,v 1.16 2008-03-27 12:09:57 snf Exp $
package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
//import javax.net.ssl.*;
//import javax.security.cert.*;
import java.util.*;
import java.text.*;
import javax.net.ssl.*;
import java.lang.reflect.*;

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

/** Handles a <i>request</i> request.*/
public class RequestDocumentHandler implements Logging {
       
    /** Classname for logging.*/
    public static final String CLASS = "RDH";
    
    /** Default maximum (worst) seeing allowed (asec).*/
    public static final double DEFAULT_SEEING_CONSTRAINT = 1.3;

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
    
    private static int cc = 0;
    
    
    /** Create a RequestDocumentHandler using the supplied IO parameters.
     * @param tea     The TEA.
     * @param io      The eSTARIO.
     * @param handle  Globus IO Handle for the connection.
     */
    public RequestDocumentHandler(TelescopeEmbeddedAgent tea) {
	//, eSTARIO io, GlobusIOHandle handle) {
	this.tea    = tea;
	//this.io     = io;
	//cthis.handle = handle;
	logger = LogManager.getLogger("TRACE");
	cc++;
	cid = "RDH/"+cc;
    }

    /** Called to handle an incoming observation request document.
     * Attempts to add a group to the OSS Phase2 DB.
     * @param document The RTML request document.  
     * @throws Exception if anything goes wrong.
     */
    public RTMLDocument handleRequest(RTMLDocument document) throws Exception {
	
	long now = System.currentTimeMillis();
	
	Observation observation = null;
	Group       group       = null;	 
	
	ExtraSolarSource source = null;
	String targetId = null;
	InstrumentConfig config = null;
	String configId = null;
	int xbin = 1;
	int ybin = 1;

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
	
	RA  ra  = target.getRA();		    
	Dec dec = target.getDec();      
	
	boolean toop = target.isTypeTOOP();
	
	targetId = target.getName();
	// Bizarre element.
	String targetIdent = target.getIdent();
	
	RTMLSchedule sched = obs.getSchedule();
	
	String expy = sched.getExposureType();
	String expu = sched.getExposureUnits();
	double expt = 0.0;
	 
	try{
	    expt = sched.getExposureLengthMilliseconds();
	} catch (IllegalArgumentException iax){
	    logger.log(INFO,1,CLASS,cid,"handleRequest","Unable to extract exposure time:"+iax);
	    return setError(document, "Unable to extract exposure time: "+iax);
	}
	 
	int expCount = sched.getExposureCount();

	int schedPriority = sched.getPriority();

	// Phase2 has no concept of "best seeing" so dont use sc.getMinimum()	 
	double seeing = DEFAULT_SEEING_CONSTRAINT; // 1.3
	RTMLSeeingConstraint sc = sched.getSeeingConstraint();
	if (sc != null){
	    seeing = sc.getMaximum();	
	}

	double mld = 0.0;
	RTMLMoonConstraint mc = sched.getMoonConstraint();
	if (mc != null) {
	    mld = mc.getDistanceRadians();
 	}

	int lunar = Group.BRIGHT;
	RTMLSkyConstraint skyc = sched.getSkyConstraint();	
	if (skyc != null) {
	    if (skyc.isBright())
		lunar = Group.BRIGHT;
	    else if
		(skyc.isDark())
		lunar = Group.DARK;
	    else {	
		logger.log(INFO,1,CLASS,cid,"handleRequest","Unable to extract sky constraint info");
		return setError(document, "Unable to extract sky brightness constraint");
	    }
	}


	// Extract filter info.
	RTMLDevice dev = obs.getDevice();
	//	String filter = null;

	if (dev == null)
	    dev = document.getDevice();
	
	if (dev != null) {
	    
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
	    logger.log(INFO,1,CLASS,cid,"handleRequest","RTML Device not present, failing request.");
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
	
	if (toop) {
	    // Try and get TOCSessionManager context.
	    TOCSessionManager sessionManager = TOCSessionManager.getSessionManagerInstance(tea,document);
	    // add the document to the TOCSessionManager
	    // if it succeeds addDocument sets the type to "confirmation".
	    document = sessionManager.addDocument(document);
	    return document;
	} else {
	    
	    // Non toop
	    
	    // Create a target to add to the OSS DB.
	    
	    // ### this maybe should go in a seperate method to be called by the CH
	    
	    source = new ExtraSolarSource(targetId);
	    source.setRA(ra.toRadians());
	    source.setDec(dec.toRadians());
	    source.setFrame(Source.FK5);
	    source.setEquinox(2000.0f);
	    source.setEpoch(2000.0f);
	    source.setEquinoxLetter('J');
	    
	    logger.log(INFO, 1, CLASS, cid,"executeRequest","Creating source: "+source);
	    
	    ADD_SOURCE addsource = new ADD_SOURCE(tea.getId()+":"+requestId);
	    addsource.setProposalPath(new Path(proposalPathName));
	    addsource.setSource(source);
	    addsource.setReplace(false);
	     
	    addsource.setClientDescriptor(new ClientDescriptor("EmbeddedAgent", 
							       ClientDescriptor.ADMIN_CLIENT,
							       ClientDescriptor.ADMIN_PRIORITY));
	    addsource.setCrypto(new Crypto("TEA"));
		
	    addsource.setTransactionPriority(0);

	    JMSCommandHandler client = new JMSCommandHandler(tea.getConnectionFactory(), 
							     addsource, 
							     tea.getOssConnectionSecure());
	     
	    //freeLock();	
	    client.send();
	    //waitOnLock();
	     
	    if (client.isError()) {	
		logger.log(INFO, 1, CLASS, cid,"executeRequest","Reply was: "+client.getReply());
		if (client.getReply() != null &&
		    client.getReply().getErrorNum() == ADD_SOURCE.SOURCE_ALREADY_DEFINED) {
		    logger.log(INFO, 1, CLASS, cid,"executeRequest",
			       "Will be using existing target: "+targetId);
		} else {
		    logger.log(INFO,1,CLASS,cid,"handleRequest",
			       "Internal error during ADD_SOURCE: "+client.getErrorMessage());
		    return setError(document, "Internal error during ADD_SOURCE: "+
				    client.getErrorMessage());
		}
	    }
	     
	    // ### INSERT ADD_INST_CONFIG code and INST_CFG_ALREADY_DEFINED here.
	    // ### TBD
	    // ### END OF ADD_INST_CFG code.
	     
	    logger.log(INFO, 1, CLASS, cid,"executeRequest","Creating config: "+config);
	     
	    ADD_INST_CONFIG addcfg = new ADD_INST_CONFIG(tea.getId()+":"+requestId);
	    addcfg.setProposalPath(new Path(proposalPathName));
	    addcfg.setConfig(config);
	    addcfg.setReplace(false);
	    
	    addcfg.setClientDescriptor(new ClientDescriptor("EmbeddedAgent", 
							    ClientDescriptor.ADMIN_CLIENT,
							    ClientDescriptor.ADMIN_PRIORITY));
	    addcfg.setCrypto(new Crypto("TEA"));
	    
	    addcfg.setTransactionPriority(0);
	    
	    JMSCommandHandler client2 = new JMSCommandHandler(tea.getConnectionFactory(), 
							      addcfg, 
							      tea.getOssConnectionSecure());
	    
	    //freeLock();	
	    client2.send();
	    //waitOnLock();
	    
	    if (client2.isError()) {	
		logger.log(INFO, 1, CLASS, cid,"executeRequest","Reply was: "+client2.getReply());
		if (client2.getReply() != null &&
		    client2.getReply().getErrorNum() == ADD_INST_CONFIG.CONFIG_ALREADY_DEFINED) {
		    logger.log(INFO, 1, CLASS, cid,"executeRequest",
			       "Will be using existing config: "+configId);
		} else {
		    logger.log(INFO,1,CLASS,cid,"handleRequest",
			       "Internal error during ADD_CONFIG: "+client2.getErrorMessage());
		    return setError(document, "Internal error during ADD_CONFIG: "+
				    client2.getErrorMessage());
		}
	    }
	    
	    
	    // Create the group now.
	    // Try to sort out the group type.
	    // ### this maybe should go in a seperate method to be called by the CH
	    
	    // ### USE 1 YEAR FOR NOW =- will come from Schedule constraint thingy
	    // or the proposal end date  which we dont know yet
	    Date completionDate = new Date(System.currentTimeMillis()+365*24*3600*1000L);
	    
	    if (scon == null || count == 1) {
		// FLEXIBLE
		group = new Group(requestId);
		group.setPath(proposalPathName);
		
		group.setStartingDate(startDate.getTime());
		group.setExpiryDate(endDate.getTime());
		group.setPriority(TelescopeEmbeddedAgent.GROUP_PRIORITY);
	
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
		
		// ARGH another massive/evil fudge, more instrument-specifics...
		if (config instanceof PolarimeterConfig) {
		    observation.setRotatorMode(TelescopeConfig.ROTATOR_MODE_MOUNT);
		    observation.setRotatorAngle(0.0);
		}


		// TODO Set the pipeline config here [e.g. dprt = true, priority = n]

		Mosaic mosaic = new Mosaic();
		mosaic.setPattern(Mosaic.SINGLE);
		observation.setMosaic(mosaic);
		
		group.addObservation(observation);
		
	    } else {
		// MONITOR.
		
		group = new MonitorGroup(requestId);
		group.setPath(proposalPathName);
		
		group.setExpiryDate(endDate.getTime());
		group.setPriority(TelescopeEmbeddedAgent.GROUP_PRIORITY);
	
		// MG-Specific
		((MonitorGroup)group).setStartDate(startDate.getTime());
		((MonitorGroup)group).setEndDate(endDate.getTime());
		((MonitorGroup)group).setPeriod(period);
		((MonitorGroup)group).setFloatFraction((float)((double)window/(double)period));
		
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
		
		// ARGH another massive/evil fudge, more instrument-specifics...
		if (config instanceof PolarimeterConfig) {
		    observation.setRotatorMode(TelescopeConfig.ROTATOR_MODE_MOUNT);
		    observation.setRotatorAngle(0.0);
		}

		// AARGH another massive/evil fudge, more instrument-specifics...
		if (config instanceof LowResSpecConfig) {
		    //TelescopeConfig.ACQUIRE_MODE_BRIGHTEST = 1;
		    //TelescopeConfig.ACQUIRE_MODE_WCS       = 2;
		    // somehow we need to be able to decide which of these to select....
		    //maybe using exposure length and other stufff
		     observation.setAcquisitionMode(TelescopeConfig.ACQUIRE_MODE_WCS);
		}
		
		Mosaic mosaic = new Mosaic();
		mosaic.setPattern(Mosaic.SINGLE);
		observation.setMosaic(mosaic);
		
		group.addObservation(observation);
		
	    }
	    
	    // map rtml priorities to Phase2 priorities.
	    int priority = 0;
	    switch (schedPriority) {
	    case 0:
		priority = 4; // Phase2 - ODB: QUITE URGENT(4)
		break;
	    case 1:
		priority = 3; // Phase2 - ODB: HIGH(3)
		break;
	    case 2:
		priority = 2; // Phase2 - ODB: MEDIUM(2)
		break;
	    case 3:
		priority = 1; // Phase2 - ODB: NORMAL(1)
		break;
	    default:
		priority = 1; // Phase2 - ODB: NORMAL(1)
	    }
	    group.setPriority(priority);

	    // set lunar and other cosntraints.
	    group.setMinimumLunarDistance(mld);	    
	    group.setMinimumLunar(lunar);
	
	    group.setTwilightUsageMode(Group.TWILIGHT_USAGE_OPTIONAL);	
	    
	    // set seeing limits.
	    if (seeing >= 1.3) {
		group.setMinimumSeeing(Group.POOR);
	    } else if(seeing >= 0.8) {
		group.setMinimumSeeing(Group.AVERAGE);
	    } else {
		// this will also catch any with silly values like < 0.0 !
		group.setMinimumSeeing(Group.EXCELLENT);
	    }
	    
	    Map smap = new HashMap();
	    smap.put(observation, targetId);
	    Map imap = new HashMap();
	    imap.put(observation, configId);		
	    Map tmap = new HashMap();
	    tmap.put(observation, "DEFAULT");
	    
	    ADD_GROUP addgroup = new ADD_GROUP(tea.getId()+":"+requestId);
	    addgroup.setClientDescriptor(new ClientDescriptor("EmbeddedAgent",
							      ClientDescriptor.ADMIN_CLIENT,
							      ClientDescriptor.ADMIN_PRIORITY));
	    addgroup.setCrypto(new Crypto("TEA"));
	    
	    addgroup.setProposalPath(new Path(proposalPathName));
	    addgroup.setGroup(group);
	    
	    addgroup.setSrcMap(smap);
	    addgroup.setIcMap(imap);
	    addgroup.setTcMap(tmap);
	    addgroup.setTransactionPriority(0);
	    
	    client = new JMSCommandHandler(tea.getConnectionFactory(), 
					   addgroup, 
					   tea.getOssConnectionSecure());
	    
	    //freeLock();
	    client.send();
	    //waitOnLock();
	    
	    if (client.isError()) {
		logger.log(INFO,1,CLASS,cid,"handleRequest","Internal error during ADD_GROUP: "+
			   client.getErrorMessage());
		return setError(document, "Internal error during ADD_GROUP: "+
				client.getErrorMessage());
	    }
	    
	}
	
	// ### this maybe should go in a seperate method to be called by the CH
	// ### or the CH calls these methods itself one-by-one.
	
	AgentRequestHandler arq = new  AgentRequestHandler(tea, document);
	
	// Extract the observation path - we already have it anyway.
	// observation needs to be declared global.- look at UH which defines on ObsInfo.
	String oid = observation.getFullPath();
	arq.setOid(oid);
	arq.setName(requestId);
	arq.setId(tea.getId()+"/"+arq.getName());
	
	// Get a unique file Name off the TEA.
	File file = new File(tea.createNewFileName(oid));
	
	// Its the one we will use.
	arq.setDocumentFile(file);
	
	// Set the current request as our basedoc.
	arq.setBaseDocument(document);
	
	// Save it to the file - we could do this ourself..
	tea.saveDocument(document, file);
	logger.log(INFO, 1, CLASS, cid, "handleRequest",
		   "Saving base document to: "+file.getPath());
	
	// Register as handler for the current obs.
	tea.registerHandler(oid, arq);
	logger.log(INFO, 1, CLASS, cid, "handleRequest",
		   "Registered running ARQ for: "+oid+" Using file: "+file.getPath());
	
	// Initialize and start the ARQ as UpdateHandler. If the ARQ does not successfully
	// prepare for UpdateHandling it will not be started and we get an exception.
	try {
	    arq.prepareUpdateHandler();
	    arq.start();
	} catch (Exception e) {
	    logger.dumpStack(1, e);
	}
	
	// We still send a confirm, even if we cant start the ARQ correctly as the obs is in the DB.
	document.setType("confirmation");
	
	return document;
	
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

} // [RequestDocumentHandler]


//
// $Log: not supported by cvs2svn $
// Revision 1.15  2007/09/27 08:25:13  snf
// *** empty log message ***
//
// Revision 1.14  2007/08/06 09:25:03  snf
// checkin
//
// Revision 1.13  2007/04/04 09:55:56  snf
// changed arq.name to reqId which is the group ID
//
// Revision 1.12  2007/04/04 08:51:12  snf
// changed arq name to oid
//
// Revision 1.11  2007/02/20 12:39:26  snf
// changed comments around priority settings.
//
// Revision 1.10  2007/01/26 10:20:27  snf
// checking
//
// Revision 1.9  2006/07/17 07:16:58  snf
// Added comments.
//
// Revision 1.8  2006/05/15 10:04:07  snf
// Added extra priority level.
//
// Revision 1.7  2006/02/27 17:22:24  cjm
// Added more logging.
//
//
