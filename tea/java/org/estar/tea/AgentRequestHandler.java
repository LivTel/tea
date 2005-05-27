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


/** Handles an observation request. Target, Group and Observation objects are sent to the
 * OSS. The AgentRequestHandler (ARQ) is registered with the TEA and it waits for updates
 * from the RCS as the observations are performed.
 */
public class AgentRequestHandler implements Logging {

    /** Class name for logging.*/
    public static final String CLASS = "AgentRequestHadler";

    /** Reference to the TEA.*/
    private TelescopeEmbeddedAgent tea;

    /** The base request document.*/
    private RTMLDocument baseDocument;

    /** Where we store the base document persistantly.*/ 
    private File file;

    /** EstarIO for responses.*/
    private eSTARIO io; 

    /** GLobusIO handle for responses.*/
    private GlobusIOHandle handle;

    /** Handles TelemetryUpdates from RCS.*/
    private UpdateHandler updateHandler;

    /** Lock for synchronization - used by JMSCommandClient. */
    private volatile BooleanLock lock  = new BooleanLock(true);

    /** The ARQ's ident - do we need this?.*/
    private String id = "ARQ";

    /** The observationID.*/
    private String oid;
  
    /**
     * The logger.
     */
    protected Logger logger = null;
    
    /**
     * Create an AgentRequestHandler. This constructor is used to create ARQs on
     * startup by the TEA during loadDocuments.
     * @param tea The TEA instance.
     * @param baseDocument The base request document.
     */
    AgentRequestHandler(TelescopeEmbeddedAgent tea, RTMLDocument baseDocument) {
	this(tea);
	this.baseDocument = baseDocument;
	
    }

    /** Create an AgentRequestHandler linked to TEA. This constructor is generally used 
     * to create a temporary ARQ for executing <i>score</i> or <i>request</i> RTML requests.
     * ### This should dissappear later..
     */
    public AgentRequestHandler(TelescopeEmbeddedAgent tea) {
	this.tea = tea;
        io = tea.getEstarIo();

        logger   = LogManager.getLogger(this);

    }

    /** Set the baseDocument.*/
    public void setBaseDocument(RTMLDocument doc) { this.baseDocument = doc; }

    /** Set the file to persist the base document to.*/
    public void setDocumentFile(File file) { this.file = file; }

    /** Called to handle an incoming score document. 
     * Attempts to score the request via the OSS Phase2 DB.
     * @param document The RTML request document.
     * @param handle   Handle for the return connection.
     */
    public void executeScore(RTMLDocument document, GlobusIOHandle handle) {
	
	this.handle   = handle;
	
	long now = System.currentTimeMillis();
	
	RTMLObservation obs = document.getObservation(0);
	
	if (obs == null) {
	    sendError(document, "There was no observation in the request");
	    return;
	}
	
	RTMLTarget target = obs.getTarget();
	
	if (target == null) {
	    sendError(document, "There was no target in the request");
	    return;
	}
	
	RA      ra   = target.getRA();	
	Dec     dec  = target.getDec();
	boolean toop = target.isTypeTOOP();
	
	if (ra == null || dec == null) {
	    sendError(document, "Missing ra/dec for target");
	    return;
	}
	
	// Convert to rads and compute elevation - we dont use this for veto now.
	double rad_ra = Math.toRadians(ra.toArcSeconds()/3600.0);
	double rad_dec= Math.toRadians(dec.toArcSeconds()/3600.0);
	
	Position targ = new Position(rad_ra, rad_dec);
	
	double elev = targ.getAltitude();
	double tran = targ.getTransitHeight();
	
	logger.log(INFO, 1, CLASS, id,"executeScore","INFO:Target at: "+targ.toString()+
		   "\n Elevation:   "+Position.toDegrees(elev,3)+
		   "\n Transits at: "+Position.toDegrees(tran,3));
	
	RTMLSchedule sched = obs.getSchedule();
	
	// Test for sched == null
	if (sched == null) {
	    sendError(document, "No schedule was specified");
	    return;
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
	    sendError(document, "Unable to extract exposure time: "+iax);
	    return;
	}
	
	if (sched.isExposureTypeSNR()) {
	    sendError(document, "Sorry, we cant handle SNR requests at the mo.");
	    return;
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
		sendError(document, "No Interval was supplied");
		return;
	    } else {
		period = pf.getMilliseconds();
		
		// No Window => Default to 90% of interval - is this best bet
		// -> big windows  = poor periodicity, high hit count
		// -> small window = good periodicity, low hit count

		if (tf == null) {
		    logger.log(INFO, 1, CLASS, id, "executeScore",
			       "No tolerance supplied, Default window setting to 95% of Interval");
		    tf = new RTMLPeriodFormat();
		    tf.setSeconds(0.95*(double)period/1000.0);
		    scon.setTolerance(tf);	  
		}
	    }
	    window = tf.getMilliseconds();
	    
	    if (count < 1) {
		sendError(document, "You have supplied (or I deduced) a negative or zero repeat Count.");
		return;
	    }
	    
	    if (period < 60000L) {
		sendError(document, "You have supplied too short a monitoring Interval - try at LEAST a minute.");
		return;
	    }
	    
	    if ((window/period < 0.0) || (window/period > 1.0)) {
		sendError(document, "Your window or Tolerance looks dubious.");
		return;
	    }
	    
	    
	}
	
	startDate = sched.getStartDate();
	endDate   = sched.getEndDate();
	
	// FG and MG need an EndDate, No StartDate => Now.
	if (startDate == null) {
	    logger.log(INFO, 1, CLASS, id,"executeScore",
		       "No start date suppled, Default start date setting to now");
	    startDate = new Date(now);
	    sched.setStartDate(startDate);
	}
	
	// No End date => StartDate + 1 day (###this is MicroLens -specific).
	if (endDate == null) {
	    logger.log(INFO, 1, CLASS, id,"executeScore",
		       "No end date supplied, Default end date setting to Start + 1 day");
	    endDate = new Date(startDate.getTime()+24*3600*1000L);
	    sched.setEndDate(endDate);
	}
	
	// Basic and incomplete sanity checks.
	if (startDate.after(endDate)) {
	    sendError(document, "StartDate and EndDate do not make sense.");
	    return;	    
	}
	
	if (expt < 1000.0) {
	    sendError(document, "Exposure time is too short - at LEAST one second.");
	    return;
	}
	
	if (expCount < 1) {
	    sendError(document, "Exposure Count is less than 1.");
	    return;
	}
	
	// ### This should be when we expect to be able to complete by...
	// but is endDate for now
	document.setCompletionTime(endDate);
	
	if (toop) {
	    
	    // ### Send a WHEN based on our serviceID.
	    String when = "WHEN "+tea.getTocsServiceId();
	    TocClient client = new TocClient(when, tea.getTocsHost(), tea.getTocsPort());
	    
	    setLock();	
	    logger.log(INFO, 1, CLASS, id,"executeScore","Sending WHEN request to TOCS");
	    client.run();
	    waitOnLock();
	    
	    if (client.isError()) {		    
		sendError(document, 
			  "Unable to invoke TO Service:"+tea.getTocsServiceId()+" : "+client.getErrorMessage());
		return;
	    }
	    
	    String reply = client.getReply();
	    
	    // ### Temp fail these.
	    sendError(document, "Targets of type toop are not yet implemented");
	    return;
	    
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
		    logger.log(INFO, 1, CLASS, id,"executeScore","Checking for: "+filterString);
		    String filter = tea.getFilterMap().
			getProperty(filterString);
		    
		    if (filter == null) {			
			sendError(document, "Unknown filter: "+filterString);
			return;
		    }
		} else {		    
		    sendError(document, "Device is not a camera");
		    return;
		}
	    } else {
		sendError(document, "Device not set");
		return;
	    }
	    
	    
	    // We should send a request to the OSS to test-schedule here.
	    if (tran < tea.getDomeLimit()) {     
		// Never rises at site.
		logger.log(INFO, 1, CLASS, id,"executeScore","Target NEVER RISES");
		sendError(document, 
			  "Target transit height: "+Position.toDegrees(tran,3)+
			  " is below dome limit: "+Position.toDegrees(tea.getDomeLimit(),3)+
			  " so will never be visible at this site");
		return;
	    } 
	    
	    
	    
	    // else if (elev < Math.toRadians(20.0) ) {
	    // 		// Currently too low to see.
	    // 		System.err.println("ARQ:INFO:Target LOW");
	    // 		sendError(document, 
	    // 			  "Target currently at elevation: "+Position.toDegrees(elev, 3)+
	    // 			  " is not visible at this time"); 	
	    // 		return;
	    //  } else {    
	    
	    // ### COMMENTED OUT AS WE ARE EXPECTING MONITORS FOR NOW
	    
	    
	    // 		// Determine telescope status - maybe this could be done by a regular polling from 
	    // 		// another thread as if from a GUI. Note we use CAMP rather than JMSMA for GUI commands !
	    
	    // 		ID getid = new ID("TEA");
	    
	    // 		IDClient client = new IDClient(getid);
	    
	    // 		freeLock();	
	    // 		client.run();
	    // 		waitOnLock();
	    
	    // 		if (client.isError()) {		    
	    // 		    sendError(document, 
	    // 			      "Unable to retrieve RCS operational status: "+client.getErrorMessage());
	    // 		    return;
	    // 		}
	    
	    // 		ID_DONE idd = (ID_DONE)client.getReply();
	    
	    
	    
	    // 		if (!idd.getOperational()) {
	    // 		    sendError(document, "The telescope is not currently operational");
	    // 		    return;
	    // 		}
	    
	    // 		// This is rather telescope-specific.
	    
	    // 		if ("PCA".equals(idd.getAgentInControl()) ||
	    // 		    "TOCA".equals(idd.getAgentInControl())) {
	    // 		    sendError(document, 
	    // 			      "A high priority agent is controlling the scope: "+
	    // 			      idd.getAgentInControl()+", Please try again later");
	    // 		    return;
	    // 		}
	    
	    // ### END OF COMMENT OUT
	    
	    
	    // ### SCA mode and target visible and p5? so score.
	    logger.log(INFO, 1, CLASS, id,"executeScore","Target OK Score = "+(2.0*tran/Math.PI));
	    //document.setScore(elev/tran);
	    document.setScore(2.0*tran/Math.PI);
	    sendDoc(document, "score");
	    
	}
    }
    
    
    /** Called to handle an incoming observation request document.
     * Attempts to add a group to the OSS Phase2 DB.
     * @param document The RTML request document.
     * @param handle   Handle for the return connection.
     */
    public void executeRequest(RTMLDocument document, GlobusIOHandle handle) {
	
	this.handle   = handle;
	
	long now = System.currentTimeMillis();
	
	Observation observation = null;
	Group       group       = null;
	
	try {
	    
	    // Tag/User ID combo is what we expect here.
	    
	    RTMLContact contact = document.getContact();
	    
	    if (contact == null) {
		sendError(document,
			  "No contact was supplied");
		return;
	    }
	    
	    String userId = contact.getUser();
	    
	    if (userId == null) {
		sendError(document, "Your User ID was null");
		return;
	    }
	    
	    // The Proposal ID.
	    RTMLProject project = document.getProject();
	    String proposalId = project.getProject();
	    
	    if (proposalId == null) {
		sendError(document, "Your Project ID was null");
		return;
	    }
	    
	    // We will use this as the Group ID.
	    RTMLIntelligentAgent userAgent = document.getIntelligentAgent();
	    String requestId = userAgent.getId();
	    
	    // Extract the Observation request.
	    
	    RTMLObservation obs = document.getObservation(0);
    	    
	    // Extract params
	    RTMLTarget target = obs.getTarget();
    	    
	    RA  ra  = target.getRA();		    
	    Dec dec = target.getDec();      
	    
	    boolean toop = target.isTypeTOOP();
	    
	    String targetId = target.getName();
	    // Bizarre element.
	    String targetIdent = target.getIdent();
	    
	    RTMLSchedule sched = obs.getSchedule();
    	    
	    String expy = sched.getExposureType();
	    String expu = sched.getExposureUnits();
	    double expt = 0.0;

	    try {
		expt = sched.getExposureLengthMilliseconds();
	    } catch (IllegalArgumentException iax) {
		sendError(document, "Unable to extract exposure time: "+iax);
		return;
	    }
	    
	    int expCount = sched.getExposureCount();
	    
	    // Extract filter info.
	    RTMLDevice dev = obs.getDevice();
    	    String filter = null;

	    if (dev == null)
		dev = document.getDevice();
	    
	    if (dev != null) {
		
		String type = dev.getType();
		String filterString = dev.getFilterType();
		
		if (type.equals("camera")) {
		    
		    // We will need to extract the instrument name from the type field.
		    //String instName = tea.getConfig().getProperty("camera.instrument", "Ratcam");
		    
		    // Check valid filter and map to UL combo
		    logger.log(INFO, 1, CLASS, id,"executeRequest","Checking for: "+filterString);
		    filter = tea.getFilterMap().
			getProperty(filterString);
		    
		    if (filter == null) {			
			sendError(document, "Unknown filter: "+filterString);
			return;
		    }
		} else {		    
		    sendError(document, "Device is not a camera");
		    return;
		}
	    } else {
		sendError(document, "Device not set");
		return;
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
		    sendError(document, "No Interval was supplied");
		    return;
		} else {
		    period = pf.getMilliseconds();
		    
		    // No Window => Default to 90% of interval.
		    if (tf == null) {
			logger.log(INFO, 1, CLASS, id,"executeRequest",
				   "No tolerance supplied, Default window setting to 95% of Interval");
			tf = new RTMLPeriodFormat();
			tf.setSeconds(0.95*(double)period/1000.0);
			scon.setTolerance(tf);	  
		    }
		}
		window = tf.getMilliseconds();
		
		if (count < 1) {
		    sendError(document, "You have supplied a negative or zero repeat Count.");
		    return;
		}
		
		if (period < 60000L) {
		    sendError(document, "You have supplied a ludicrously short monitoring Interval.");
		    return;
		}
		
		if ((window/period < 0.0) || (window/period > 1.0)) {
		    sendError(document, "Your window or Tolerance looks dubious.");
		    return;
		}
		
	    }
	    
	    startDate = sched.getStartDate();
	    endDate   = sched.getEndDate();
	    
	    // FG and MG need an EndDate, No StartDate => Now.
	    if (startDate == null) {
		logger.log(INFO, 1, CLASS, id,"executeRequest","Default start date setting to now");
		startDate = new Date(now);
		sched.setStartDate(startDate);
	    }
	    
	    // No End date => StartDate + 1 day (###this is MicroLens -specific).
	    if (endDate == null) {
		logger.log(INFO, 1, CLASS, id,"executeRequest","Default end date setting to Start + 1 day");
		endDate = new Date(startDate.getTime()+24*3600*1000L);
		sched.setEndDate(endDate);
	    }
	    
	    // Basic and incomplete sanity checks.
	    if (startDate.after(endDate)) {
		sendError(document, "Your StartDate and EndDate do not make sense.");
		return;	    
	    }
	    
	    if (expt < 1000.0) {
		sendError(document, "Your Exposure time is too short.");
		return;
	    }
	    
	    if (expCount < 1) {
		sendError(document, "Your Exposure Count is less than 1.");
		return;
	    }
	    
	    // Look up proposal details.
	    
	    String proposalPathName = tea.getDBRootName()+"/"+userId+"/"+proposalId;
	    
	    if (toop) {

		sendError(document, "Toop targets are not supported yet");
		return;
		
	    } else {
		
		// Non toop
		
		// Create a target to add to the OSS DB.
		
		ExtraSolarSource source = new ExtraSolarSource(targetId);
		source.setRA(ra.toRadians());
		source.setDec(dec.toRadians());
		source.setFrame(Source.FK5);
		source.setEquinox(2000.0f);
		source.setEpoch(2000.0f);
		source.setEquinoxLetter('J');
		
		logger.log(INFO, 1, CLASS, id,"executeRequest","Creating source: "+source);
		
		ADD_SOURCE addsource = new ADD_SOURCE(tea.getId()+":"+requestId);
		addsource.setProposalPath(new Path(proposalPathName));
		addsource.setSource(source);
		addsource.setReplace(false);
		
		addsource.setClientDescriptor(new ClientDescriptor("EmbeddedAgent", 
								   ClientDescriptor.ADMIN_CLIENT,
								   ClientDescriptor.ADMIN_PRIORITY));
		addsource.setCrypto(new Crypto("TEA"));
		
		CommandHandler client = new CommandHandler(addsource);
		
		freeLock();	
		client.send();
		waitOnLock();
		
		if (client.isError()) {	
		    logger.log(INFO, 1, CLASS, id,"executeRequest","Reply was: "+client.getReply());
		    if (client.getReply() != null &&
			client.getReply().getErrorNum() == ADD_SOURCE.SOURCE_ALREADY_DEFINED) {
			logger.log(INFO, 1, CLASS, id,"executeRequest",
				   "Will be using existing target: "+targetId);
		    } else {
			sendError(document, "Internal error during ADD_SOURCE: "+client.getErrorMessage());
			return;
		    }
		}

		// ### INSERT ADD_INST_CONFIG code and INST_CFG_ALREADY_DEFINED here.
		// ### TBD
		// ### END OF ADD_INST_CFG code.

		
		// Create the group now.
		// Try to sort out the group type.
		
		// ### USE 1 YEAR FOR NOW =- will come from Schedule constraint thingy
		// or the proposal end date  which we dont know yet
		Date completionDate = new Date(System.currentTimeMillis()+365*24*3600*1000L);
		
		if (scon == null || count == 1) {
		    // Hurrah its a FlexGroup !
		    
		    group = new Group(requestId);
		    group.setPath(proposalPathName);
		    
		    group.setExpiryDate(endDate.getTime());
		    group.setPriority(TelescopeEmbeddedAgent.GROUP_PRIORITY);
		    
		    group.setMinimumLunar(Group.BRIGHT);
		    group.setMinimumSeeing(Group.POOR);
		    group.setTwilightUsageMode(Group.TWILIGHT_USAGE_OPTIONAL);
		    
		    float expose = 1000.0f*(float)expt;
		    // Maybe split into chunks NO NOT YET.
		    //if ((double)expose > (double)tea.getMaxObservingTime()) {
		    //int nn = (int)Math.ceil((double)expose/(double)tea.getMaxObservingTime());
		    
		    //}
		    int mult = expCount;
		    
		    observation = new Observation(targetIdent);
		    
		    observation.setExposeTime(expose);
		    observation.setNumRuns(mult);
		    
		    Mosaic mosaic = new Mosaic();
		    mosaic.setPattern(Mosaic.SINGLE);
		    observation.setMosaic(mosaic);
		    
		    group.addObservation(observation);
		    
		} else {
		    // A MonitorGroup.
		    
		    group = new MonitorGroup(requestId);
		    group.setPath(proposalPathName);
		    
		    group.setExpiryDate(endDate.getTime());
		    group.setPriority(TelescopeEmbeddedAgent.GROUP_PRIORITY);
		    
		    group.setMinimumLunar(Group.BRIGHT);
		    group.setMinimumSeeing(Group.POOR);
		    group.setTwilightUsageMode(Group.TWILIGHT_USAGE_OPTIONAL);
		    
		    // MG-Specific
		    ((MonitorGroup)group).setStartDate(startDate.getTime());
		    ((MonitorGroup)group).setEndDate(endDate.getTime());
		    ((MonitorGroup)group).setPeriod(period);
		    ((MonitorGroup)group).setFloatFraction((float)((double)window/(double)period));
		    
		    float expose = 1000.0f*(float)expt;	
		    // Maybe split into chunks NO NOT YET.
		    //if ((double)expose > (double)tea.getMaxObservingTime()) {
		    //int nn = (int)Math.ceil((double)expose/(double)tea.getMaxObservingTime());
		    
		    //}	    
		    int mult = expCount;
		    
		    observation = new Observation(targetIdent);
		    
		    observation.setExposeTime(expose);
		    observation.setNumRuns(mult);
		    
		    Mosaic mosaic = new Mosaic();
		    mosaic.setPattern(Mosaic.SINGLE);
		    observation.setMosaic(mosaic);
		    
		    group.addObservation(observation);
		    
		}
		
		
		Map smap = new HashMap();
		smap.put(observation, targetId);
		Map imap = new HashMap();
		imap.put(observation, filter);		
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
		
		client = new CommandHandler(addgroup);
		
		freeLock();
		client.send();
		waitOnLock();
		
		if (client.isError()) {
		    sendError(document, "Internal error during ADD_GROUP: "+client.getErrorMessage());
		    return;
		}
		
	    }
	    
	    sendDoc(document, "confirmation");
  
	    // TEA will Save this for persistance.
	    //tea.addDocument(document);


	    // #### START OF NEW ARCH STUFF - commented out till ready to run
	    
	    // Extract the observation path - we already have it anyway.
	    // observation needs to be declared global.- look at UH which defines on ObsInfo.
	    oid = observation.getFullPath();

 	    // Get a unique file Name off the TEA.
 	    File file = new File(tea.createNewFileName(oid));

 	    // Its the one we will use.
 	    setDocumentFile(file);

 	    // Set the current request as our basedoc.
 	    setBaseDocument(document);

 	    // Save it to our file - we could do this ourself..
 	    tea.saveDocument(document, file);

 	    // Register as handler for the current obs.
 	    tea.registerHandler(oid, this);

 	    // Initialize and start the UpdateHandler (which is this). 
	    // these should not fail..but can throw exceptions if called more than once
	    
	    createUpdateHandler();
	    startUpdateHandler();
	    
	    // ### END OF NEW ARCH STUFF
	    
	} catch (Exception ex) {
	    
	    logger.log(INFO, 1, CLASS, id, "exec", "Error occurred: "+ex);   	
	    logger.dumpStack(1,ex);
	}
	
    }

    /** Returns a reference to the UpdateHandler thread.*/
    public UpdateHandler getUpdateHandler() { return updateHandler; }

    /** Create the UpdateHandler thread but dont start it.
     * @throws Exception if the UpdateHandler fails to construct.
     */
    public void createUpdateHandler() throws Exception {
	if (updateHandler != null)
	    throw new Exception("Updatehandler already created");
	updateHandler = new UpdateHandler(tea, baseDocument);
    }
    
    /** Start the UpdateHandler if its not already running.
     * @throws Exception If the UH does not exist or is already running.
     */
    public void startUpdateHandler() {
	if (updateHandler == null)
	    throw new NullPointerException("UpdateHandler thread does not exist");
	
	if (updateHandler.isAlive())
	    throw new IllegalThreadStateException("UpdateHandler is already running");

	updateHandler.start();

    }


    /** Waits on a Thread lock.*/
    private void waitOnLock() {
    	
	logger.log(INFO, 1, CLASS, id,"waitOnLock","Waiting in lock");
	try {
	    lock.waitUntilTrue(0L);
	} catch (InterruptedException ix) {
	    logger.log(INFO, 1, CLASS, id,"waitOnLock","Interrupted waiting on lock");
	}
	logger.log(INFO, 1, CLASS, id,"waitOnLock","Lock is free");
    }
    
    /** Frees the Thread lock.*/
    private void freeLock() {
	logger.log(INFO, 1, CLASS, id,"freeLock"," Releasing lock");
	lock.setValue(true);
    }
    
    /** Set the lock.*/
    private void setLock() {
	lock.setValue(false);
    }

    public RTMLDocument getBaseDocument() { return baseDocument; }
    
    /** Checks the instrument details.*/
    private void checkInstrument() {
	
    }
    
    // ### These doc reply methods all need looking at there is duplication and 
    // ### lack of obvious useful extra functionality.
    
    
    
    /** Send an Error reply with sepcified message.*/
    public void sendError(RTMLDocument document, String errorMessage)
    {
	String reply = tea.createErrorDocReply(document, errorMessage);
    	
	io.messageWrite(handle, reply);     
    	
	logger.log(INFO, 1, CLASS, id,"sendError","Sent error message: "+errorMessage);
    	
	io.clientClose(handle);
    	
    }
    
    /** 
     * Send a reply of specified type.
     * Uses the already opened eSTAR io handle.
     * @param document The document to send.
     * @param type The type of document to send.
     * @see #io
     * @see #handle
     */
    public void sendDoc(RTMLDocument document, String type)
    {
	
	String reply = tea.createDocReply(document, type);
	
	io.messageWrite(handle, reply);     
    	
	logger.log(INFO, 1, CLASS, id,"sendDoc","Sent doc type: "+type);
    	
	io.clientClose(handle);
    	
    }
    
    /** 
     * Send a reply of specified type. This differs from sendDoc(doc,type) in that
     * an io client connection is made to the intelligent agent using the information in the
     * documents intelligen agent tag, rather than replying to an agent request.
     * @param document The document to send.
     * @param type A string denoting the type of document to send.
     */
    public void sendDocUpdate(RTMLDocument document, String type) throws Exception
    {
	logger.log(INFO, 1, CLASS, id, "sendDocUpdate", "Started.");   	
	
	RTMLIntelligentAgent userAgent = document.getIntelligentAgent();
	if(userAgent == null)
	    {
		logger.log(INFO, 1, CLASS, id, "sendDocUpdate", "User agent was null.");
		throw new Exception(this.getClass().getName()+":sendDocUpdate:user agent was null");
	    }
	
	String agid = userAgent.getId();
	String host = userAgent.getHostname();
	int    port = userAgent.getPort();
	
	logger.log(INFO, 1, CLASS, id, "sendDocUpdate", "Opening eSTAR IO client connection to ("+host+
		   ","+port+").");
	GlobusIOHandle handle = io.clientOpen(host, port);
	if(handle == null)
	    {
		logger.log(INFO, 1, CLASS, id, "sendDocUpdate", "Failed to open client connection to ("+host+
			   ","+port+").");
		throw new Exception(this.getClass().getName()+":sendDocUpdate:handle was null");
	    }
	
	
	String reply = tea.createDocReply(document, type);
	
	logger.log(INFO, 1, CLASS, id, "sendDocUpdate", "Writing:\n"+reply+
		   "\n to handle "+handle+".");
	
	// how do we check this has failed?
	io.messageWrite(handle, reply);
	
	logger.log(INFO, 1, CLASS, id, "sendDocUpdate","ARQ::Sent document "+agid+" type: "+type);
	
	io.clientClose(handle);
	
    }
    
    /** Handles responses to commands sent via "Java Message Service (MA) Protocol" (JMS).*/
    private class CommandHandler extends JMSMA_ClientImpl {
	
	private volatile boolean error = false;
	
	private int errorNum;
	
	private String errorMessage = null;
	
	private COMMAND_DONE reply;
	
	CommandHandler(COMMAND command) {
	    super();
	    this.command = command;
	}
	
	protected void send() {
	    JMSMA_ProtocolClientImpl protocol = null;
	    
	    if (tea.getOssConnectionSecure()) {
		try {
		    SSLSocketFactory sf = (SSLSocketFactory)SSLSocketFactory.getDefault();
		    
		    protocol =
			new JMSMA_ProtocolClientImpl(this, 
						     new SocketConnection(tea.getOssHost(), tea.getOssPort(), sf));
		} catch (Exception ex) {
		    setError(true, "An error occurred making connection to the OSS: "+ex);
		    return;
		}
	    } else {
		protocol =
		    new JMSMA_ProtocolClientImpl(this, 
						 new SocketConnection(tea.getOssHost(), tea.getOssPort()));
	    }
	    logger.log(INFO, 1, CLASS, id,"send","CMD Client::Connecting to "+tea.getOssHost()+":"+tea.getOssPort());
	    logger.log(INFO, 1, CLASS, id,"send","CMD Client::Sending ["+command.getClass().getName()+"]");
	    protocol.implement();
	    
	}		
	
	public void handleAck  (ACK ack) {
	    logger.log(INFO, 1, CLASS, id,"handleAck","CMD Client::Ack received");
	}
	
	public void handleDone (COMMAND_DONE response) {
	    
	    if (! response.getSuccessful()) {
		setError(true, "Error submitting request: "+response.getErrorString()); 
	    } else {
		setError(false, "OSS Command "+command+" accepted");					
	    }	
	    reply = response;
	}
	
	
	public void failedConnect  (Exception e) {
	    setError(true,"Internal error while submitting request: Failed to connect to OSS: "+e);
	}
	
	
	public void failedDespatch (Exception e) {
	    setError(true,"Internal error while submitting request: Failed to despatch command: "+e);
	}
        
	
	public void failedResponse  (Exception e) {
	    setError(true,"Internal error while submitting request: Failed to get reply: "+e);
	}
	
	public void exceptionOccurred(Object source, Exception e) {
	    setError(true, "Internal error while submitting request: Exception: "+e);
	}
	
	public void sendCommand(COMMAND command) {}
	
	/** Sets the current error state and message.*/
	private void setError(boolean error, String errorMessage) {
	    this.error = error;
	    this.errorMessage = errorMessage;
	}
	
	/** Returns True if there is an error.*/
	public boolean isError() { return error; }
	
	/** Returns the error code.*/
	public int getErrorNum() { return errorNum; }
	
	/** Returns the current error message or null. */
	public String getErrorMessage() { return errorMessage; }
	
	/** Returns the command reply.*/
	public COMMAND_DONE getReply() { return reply; }		
	
    }// [CommandHandler]
    
    /** Handles responses to commands sent via "Target of Opportunity Control Protocol" (TOCP).
     * ### THIS WILL MOVE into a seperate class somewhere else...
     */
    private class TocClient {
	
	private volatile boolean error = false;
	
	private String host;
	
	private int port;
	
	private String command;
	
	private String errorMessage = null;
	
	private String reply;
	
	private TelnetConnection tc;
	
	TocClient(String command, String host, int port) {
	    this.command = command;
	    this.host = host;
	    this.port = port;
	}
	
	/** Sets the current error state and message.*/
	private void setError(boolean error, String errorMessage) {
	    this.error = error;
	    this.errorMessage = errorMessage;
	}
	
	/** Returns True if there is an error.*/
	public boolean isError() { return error; }
	
	/** Returns the current error message or null. */
	public String getErrorMessage() { return errorMessage; }
	
	/** Returns the command reply.*/
	public String getReply() { return reply; }		
	
	/** Overwrite to handle any i/o errors detected by implementor..
	 * @param e An exception which was thrown by the implementor.
	 */
	public void failed(Exception e, IConnection connection) {
	    if (connection != null)
		connection.close();
	    
	    setError(true, "Internal error while submitting request: "+e);
	}
	
	public void run() {
	    
	    try {
		logger.log(INFO, 1, CLASS, id,"run","TOC Client::Connecting to "+host+":"+port);
		tc = new TelnetConnection(host, port);
		
		try {
		    tc.open();
		    logger.log(INFO, 1, CLASS, id,"run","TOC Client::Opened connection");
		} catch (Exception e) {
		    setError(true, "Failed to open connection to TOCS: "+e);
		    return;
		}
		tc.sendLine(command);
		logger.log(INFO, 1, CLASS, id,"run","TOC Client::Sent ["+command+"]");
		try {
		    reply = tc.readLine();
		    logger.log(INFO, 1, CLASS, id,"run","TOC Client::Reply ["+reply+"]");
		    if (reply == null ||
			reply.equals("")) {
			setError(true, "Null reply from TOCS");
			return;
		    }
		    reply = reply.trim();
		    if (reply.startsWith("ERROR")) {
			setError(true, reply.substring(5));
			return;
		    }
		    
		} catch (Exception e) {
		    setError(true, "Failed to read TOCS response: "+e);
		    return;
		}	
		
		setError(false, "Command temporarily accepted by TOCS");
		
	    } catch (Exception e) {
		setError(true, "Failed to read TOCS response: "+e);
		return;		
	    } finally {
		logger.log(INFO, 1, CLASS, id,"run","TOC Client::Closing connection");
		try {
		    tc.close();
		} catch (Exception e) {
		    // We dont really care..
		    logger.dumpStack(1,e);
		}
		logger.log(INFO, 1, CLASS, id,"run","TOC Client::Freeing lock");
		freeLock();
	    }
	}
	
	
    }
    
    /** Handles responses to commands sent via "Control and Monitoring Protocol" (CAMP).*/
    private class IDClient extends CAMPClient {
	
	private volatile boolean error = false;
	
	private String errorMessage = null;
	
	private COMMAND_DONE reply;
	
	IDClient(COMMAND command) {
	    super(tea.getConnectionFactory(), "CTRL", command);
	} 
	
	/** Sets the current error state and message.*/
	private void setError(boolean error, String errorMessage) {
	    this.error = error;
	    this.errorMessage = errorMessage;
	}
	
	/** Returns True if there is an error.*/
	public boolean isError() { return error; }
	
	/** Returns the current error message or null. */
	public String getErrorMessage() { return errorMessage; }
	
	/** Returns the command reply.*/
	public COMMAND_DONE getReply() { return reply; }		
	
	/** Overwrite to handle any i/o errors detected by CAMP implementor..
	 * @param e An exception which was thrown by the CAMP implementor.
	 */
	public void failed(Exception e, IConnection connection) {
	    if (connection != null)
		connection.close();
	    
	    setError(true, "Internal error while submitting request: "+e);
	}
	
	/** Overwrite to handle the response message received from a CAMP implementor.
	 * @param update The UPDATE received.
	 */
	public void handleUpdate(COMMAND_DONE update, IConnection connection) {
	    if (connection != null)
		connection.close();
	    
	    if (!update.getSuccessful()) {
		setError(true, "Error submitting request: "+update.getErrorString()); 	
	    } else 
		setError(false, "Command "+command+" accepted");	    
	    reply = update;	
	}
	
    } // [IDClient]]
    
    
} //[RequestHandler
