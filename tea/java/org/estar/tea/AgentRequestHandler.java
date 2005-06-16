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
public class AgentRequestHandler extends ControlThread implements Logging {

    /** Class name for logging.*/
    public static final String CLASS = "AgentRequestHadler";

    /** Observation state indicating unable to deduce completion state.*/
    public static final int OBSERVATION_STATE_UNKNOWN            = 0;

    /** Observation state indicating that the group has expired and there were no frames generated.*/
    public static final int OBSERVATION_STATE_EXPIRED_FAILED     = 1;     
    
    /** Observation state indicating that all image frames were returned.*/
    public static final int OBSERVATION_STATE_DONE               = 2; 
        
    /** Observation state indicating that the group has expired and some of the image frames were done.*/
    public static final int OBSERVATION_STATE_EXPIRED_INCOMPLETE = 3;

  /** Observation state indicating that the group is still active.*/
    public static final int OBSERVATION_STATE_ACTIVE             = 4;


    /** Polling interval for pending queue.*/
    private static final long SLEEP_TIME = 5000L;

    /** Polling interval while awaiting first data.*/
    private static final long LONG_SLEEP_TIME = 1800*1000L;
    
    /** Time margin above expected execution time when we give up as a lost cause.*/
    private static final long TIME_MARGIN = 2*3600*1000L;

    /** Time after expiry before we expire the document (ms).*/
    private static final long DEFAULT_EXPIRY_OFFSET = 2*3600*1000L;

    /** Flags completed successful obs as notified by RCS Telemetry.*/
    public static final int OBS_DONE = 1;

    /** Flags completed but failed obs as notified by RCS Telemetry.*/
    public static final int OBS_FAILED = 2;

    /** Flags uncompleted obs - Default initial state.*/
    public static final int OBS_NOT_DONE_YET = 0;

    /** True if we are able to handle updates.*/
    private boolean updateHandler = false;
    
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

    /** Lock for synchronization - used by JMSCommandClient ?. */
    private volatile BooleanLock lock  = new BooleanLock(true);

    /** The observationID.*/
    private String oid;
  
    /** The observation (within the generated Group) which we are expecting to perform.
     * - this is not persisted, i.e. is only visible:-
     * <ul>
     *  <li>when group and obs are created.
     *  <li>when we receive an update (ObsInfo) from RCS.
     * </ul>
     */
    private Observation observation;

   /** The update document - temporarily created to send back to remote UA.*/
    private RTMLDocument updateDoc;

    /** Pipeline processing plugin implementation. (#### Should be per project)*/
    private PipelineProcessingPlugin pipelinePlugin = null;

    /** List of pending image filenames to transfer and process 
     * - these are lost between instantiations i.e. non-persistent.
     */
    private List pending;

    /** List of processed image filenames 
     * - dont know what these will be used for 
     * - these are lost between instantiations i.e. non-persistent.
     */
    private List processed;

    /** Flag to indicate whether the observations have completed.*/
    private volatile int state = OBS_NOT_DONE_YET;

    /** Records the number of exposures received so far.*/
    private int countExposures;
    
    /** Current sleep setting.*/
    private long sleepTime = LONG_SLEEP_TIME;

    /** Time after expiry before we decide to expire the document (ms).*/
    private long expiryOffset;

    /** ARQ id.*/
    private String id;

    /** Class logger.*/
    private Logger logger = null;
    
    /**
     * Create an AgentRequestHandler. This constructor is used to create ARQs on
     * startup by the TEA during loadDocuments and by the ConnectionHandler when a
     * new request has been confirmed.
     * @param tea          The TEA instance.
     * @param baseDocument The base request document.
     */
    AgentRequestHandler(TelescopeEmbeddedAgent tea, RTMLDocument baseDocument) {
	super("ARQ", true);
	this.tea          = tea;
	this.baseDocument = baseDocument;

	io = tea.getEstarIo();

	pending   = new Vector();
	processed = new Vector();

	countExposures = 0;
	//elapsedTime = 0L;

	expiryOffset = DEFAULT_EXPIRY_OFFSET;

	logger = LogManager.getLogger(this);

    }

    /** Sets the id from a supplied string. Usually this includes the TEA's id as prepend.*/
    public void setId(String id) { this.id = id; }

    /** Sets the ObservationID.
     * @param oid The ObservationID.
     */
    public void setOid(String oid) { this.oid = oid; }
    
    /** Set the base-document.
     * @param doc The base-document.
     */
    public void setBaseDocument(RTMLDocument doc) { this.baseDocument = doc; }

    /** Set the file to persist the base document to.
     * @param file The file to store the base-document in.
     */
    public void setDocumentFile(File file) { this.file = file; }

   /** Sets flag to indicate that observation has FAILED.
     * ###This is also used to knock the UH into LONG_SLEEP.
     */
    public void setObservationFailed() { 
	this.state = OBS_FAILED; 
	sleepTime = LONG_SLEEP_TIME;
    }
    
    /** Sets flag to indicate that observation has SUCCEDED.
     * ###This is also used to knock the UH into LONG_SLEEP.
     */
    public void setObservationCompleted() { 
	this.state = OBS_DONE;
	sleepTime = LONG_SLEEP_TIME;
    }
    
    /** Returns the base document.
     * @return The base-document..
     */
    public RTMLDocument getBaseDocument() { return baseDocument; }

    /** Returns the base-document file.
     * @return The file used to store the base document.
     */
    public File getDocumentFile() { return file; }
    
   /** Adds the name of a file to the pending list.
     * ###This is also used to knock the UH into SHORT_SLEEP.     
     * @param fileName The full (path) name of the file on the OCC 
     *                 including instrument NFS mount details.
     */
    public void addImageFileName(String fileName) {
	pending.add(fileName);
	sleepTime = SLEEP_TIME; // short period now
	interrupt(); // Actually make the long sleep breakout asap
    }

    /** ###TEMP Called from DocExpirator - 
     * Makes the base document expire and removes this ARQ from the agent list.*/
    public void expireDocument() throws Exception {

	tea.expireDocument(file);	
	tea.deleteUpdateHandler(oid);

    }

    /** Prepare the UpdateHandler thread but dont start it.
     * @throws Exception if the UpdateHandler fails to prepare.
     */
    public void prepareUpdateHandler() throws Exception {
	
	if (updateHandler)
	    throw new Exception("Updatehandler is already prepared for use");
	
	updateDoc = (RTMLDocument)baseDocument.deepClone();
	pipelinePlugin = getPipelinePluginFromDoc();
	pipelinePlugin.setTea(tea);
	pipelinePlugin.initialise();
	
	updateHandler = true;
    }
    
    /** Waits on a Thread lock.*/
    private void waitOnLock() {
    	
	logger.log(INFO, 1, CLASS, getName(),"waitOnLock","Waiting in lock");
	try {
	    lock.waitUntilTrue(0L);
	} catch (InterruptedException ix) {
	    logger.log(INFO, 1, CLASS, getName(),"waitOnLock","Interrupted waiting on lock");
	}
	logger.log(INFO, 1, CLASS, getName(),"waitOnLock","Lock is free");
    }
    
    /** Frees the Thread lock.*/
    private void freeLock() {
	logger.log(INFO, 1, CLASS, getName(),"freeLock"," Releasing lock");
	lock.setValue(true);
    }
    
    /** Set the lock.*/
    private void setLock() {
	lock.setValue(false);
    }

       
    /** Checks the instrument details.*/
    private void checkInstrument() {
	
    }
    
    // ### These doc reply methods all need looking at there is duplication and 
    // ### lack of obvious useful extra functionality.
    
    
    
    /** Send an Error reply with sepcified message.  
     * Uses the already opened eSTAR io handle.
     * @param document The document to send.
     * @param errorMessage The error message to include.
     * @see #io
     * @see TelescopeEmbeddedAgent#createErrorDocReply
     */
    public void sendError(RTMLDocument document, String errorMessage) {

	String reply = TelescopeEmbeddedAgent.createErrorDocReply(document, errorMessage);
    	
	io.messageWrite(handle, reply);     
    	
	logger.log(INFO, 1, CLASS, getName(),"sendError","Sent error message: "+errorMessage);
    	
	io.clientClose(handle);
    	
    }
    
    /** Send a reply of specified type.
     * Uses the already opened eSTAR io handle.
     * @param document The document to send.
     * @param type     The type of document to send.
     * @see #io
     * @see TelescopeEmbeddedAgent#createDocReply
     */
    public void sendDoc(RTMLDocument document, String type) {
	
	String reply = TelescopeEmbeddedAgent.createDocReply(document, type);
	
	io.messageWrite(handle, reply);     
    	
	logger.log(INFO, 1, CLASS, getName(),"sendDoc","Sent doc type: "+type);
    	
	io.clientClose(handle);
    	
    }
    
    /** 
     * Send a reply of specified type. This differs from sendDoc(doc,type) in that
     * an io client connection is made to the intelligent agent using the information in the
     * documents intelligen agent tag, rather than replying to an agent request.
     * This method is used by the UpdateHandler to send update messages.
     * @param document The document to send.
     * @param type     A string denoting the type of document to send.
     */
    public void sendDocUpdate(RTMLDocument document, String type) throws Exception {

	logger.log(INFO, 1, CLASS, getName(), "sendDocUpdate", "Started.");   	
	
	RTMLIntelligentAgent userAgent = document.getIntelligentAgent();
	if(userAgent == null)
	    {
		logger.log(INFO, 1, CLASS, getName(), "sendDocUpdate", "User agent was null.");
		throw new Exception(this.getClass().getName()+":sendDocUpdate:user agent was null");
	    }
	
	String agid = userAgent.getId();
	String host = userAgent.getHostname();
	int    port = userAgent.getPort();
	
	logger.log(INFO, 1, CLASS, getName(), "sendDocUpdate", "Opening eSTAR IO client connection to ("+host+
		   ","+port+").");
	GlobusIOHandle handle = io.clientOpen(host, port);
	if(handle == null)
	    {
		logger.log(INFO, 1, CLASS, getName(), "sendDocUpdate", "Failed to open client connection to ("+host+
			   ","+port+").");
		throw new Exception(this.getClass().getName()+":sendDocUpdate:handle was null");
	    }
	
	
	String reply = TelescopeEmbeddedAgent.createDocReply(document, type);
	
	logger.log(INFO, 1, CLASS, getName(), "sendDocUpdate", "Writing:\n"+reply+
		   "\n to handle "+handle+".");
	
	// how do we check this has failed?
	io.messageWrite(handle, reply);
	
	logger.log(INFO, 1, CLASS, getName(), "sendDocUpdate","ARQ::Sent document "+agid+" type: "+type);
	
	io.clientClose(handle);
	
    }

    /** Initialization.*/
    protected void initialise() {}
    

    /** 
     * Called by wrapping thread forever until terminated.
     * This thread should only terminate when the ARQ itself is deregistered and destroyed,
     * which indicates that one or more of:-
     * <ul>
     *   <li> Group's End Date has been exceeded by a generous margin.
     *       (this function is currently performed by the DocumentExpirator).
     *   <li> #numberExposures > expected number.
     * </ul>
     */
    protected void mainTask() {

        // Back round once more at least.
        try {Thread.sleep(sleepTime);} catch (InterruptedException ix) {}
    
	System.err.println("ARQ/UH::Polling, entered maintask");
	
	RTMLImageData imageData = null;
		
	// Always clear out the pending list.
		    
	// If image filenames in queue, grab and process.
	while (! pending.isEmpty()) {
	    
	    String imageFileName = (String)pending.remove(0);
	    
	    logger.log(INFO, 1, CLASS, id,"mainTask",
		       "ARQ::Processing image filename "+imageFileName+" for "+oid+".");
	    
	    // Generate the correct destination file name.			
	    String destDirName = null;
	    try {
		destDirName = pipelinePlugin.getInputDirectory();
	    }
	    catch(Exception e) {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Getting input directory failed for pipeline plugin "
			   +pipelinePlugin+":"+e);
		logger.dumpStack(1,e);
		return;
	    }

	    File   destDir     = new File(destDirName);
	    File   fullFile    = new File(imageFileName);
	    String rawFileName = fullFile.getName();
	    File   destFile    = new File(destDir, rawFileName);
	    String destFileName= destFile.getPath();	
		
	    // Grab image from OCC/ICC.
	    try {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Transferring image filename "+
			   imageFileName+" to "+destFileName+" for "+oid+".");
		transfer(imageFileName, destFileName);		    
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Transfered image okay");
	    } catch (Exception e) {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Transferring image filename "+
			   imageFileName+" to "+destFileName+" failed for "+
			   oid+":"+e);
		logger.dumpStack(1,e);
		return;
	    }
	    
	    // Pipeline process
	    try {
		imageData = pipelinePlugin.processFile(destFile);
	    } catch(Exception e) {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::pipelinePlugin.processFile "+
			   destFile+" failed:"+e);
		logger.dumpStack(1,e);
		return;
	    }
	    
	    // Add processed data to basedoc
	    RTMLObservation obs = baseDocument.getObservation(0);
	    if (obs != null) {
		try {
		    obs.addImageData(imageData);
		} catch (Exception e) {
		    logger.log(INFO, 1, CLASS, id,"mainTask",
			       "ARQ::addImageData "+
			       imageData+" failed:"+e);
		    logger.dumpStack(1,e);
		    return;
		}
	    }
		
	    // Save the modified base document.
	    try {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Saving document for "+oid+".");
		tea.saveDocument(baseDocument, getDocumentFile());
	    } catch (Exception e) {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::saveDocument for "+oid+" failed:"+e);
		logger.dumpStack(1,e);
		return;
	    }
	    
	    
	    // Add processed data to updatedoc. 
	    // Note we re-use the same UpdateDoc that got cloned from the BaseDoc 
	    // at the start - we need to clear out any added image data each time we re-use it.
	    obs = updateDoc.getObservation(0);
	    if (obs != null) {
		try {
		    // clear any left-over image data. 
		    obs.clearImageDataList();
		    // add just-received reduced image data.
		    obs.addImageData(imageData);
		} catch (Exception e) {
		    logger.log(INFO, 1, CLASS, id,"mainTask",
			       "ARQ::addImageData for "+oid+" "+
			       imageData+" failed:"+e);
		    logger.dumpStack(1,e);
		    return;
		}
	    }
		
	    // Now try to send the update to the UA.
	    // this is a mess - need global comms method like
	    // tea.sendDoc(doc); This may need to go in athread
	    // as it may block for a while if the connection is blocked
	    try {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Sending update document for "+oid+".");
		sendDocUpdate(updateDoc, "update");
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Sent update document for "+oid+".");
	    } catch (Exception e) {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::sendDocUpdate failed for "+oid+":"+e);
		logger.dumpStack(1,e);
		return;
	    }
	    
	    // Add to list of processed filenames.
	    processed.add(imageFileName);
	    
	} // while pending list not empty
	
	// Now we should do the test to see if weve got all the expected images
	// and or expired. 	
	int    observationState = testCompletion();
	String docType = null;

	switch (observationState) {
	case OBSERVATION_STATE_ACTIVE:
	    logger.log(INFO, 1, CLASS, id,"mainTask",
		       "ARQ::Observation is still active: "+oid);
	    break;
	case OBSERVATION_STATE_UNKNOWN:
	    logger.log(INFO, 1, CLASS, id,"mainTask",
		       "ARQ::Unable to determine observation state for: "+oid);
	    break;
	case OBSERVATION_STATE_DONE:
	    docType = "observation";
	    break;
	case OBSERVATION_STATE_EXPIRED_INCOMPLETE:
	    docType = "incomplete";
	    break;
	case OBSERVATION_STATE_EXPIRED_FAILED:
	    docType = "fail";
	    break;
	}

	// All done, send final document and disengage.
	if (docType != null) {
	    try {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Sending observation final status document ("+docType+") document for "+oid+".");
		sendDocUpdate(baseDocument, docType);
		
		// Only if we succeeded in sending can we disengage the ARQ.
		
		// Deregister immediately.
		tea.deleteUpdateHandler(oid);
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Deregistering handler: "+getName()+" for "+oid+".");
		
		try {
		    
		    logger.log(INFO, 1, CLASS, id,"mainTask",
			       "ARQ::Saving observation-completed document for "+oid+".");
		    
		    // save document - pass the filename aswell
		    tea.saveDocument(baseDocument, getDocumentFile());
		    
		    logger.log(INFO, 1, CLASS, id,"mainTask",
			       "ARQ::Moving observation-completed document for "+oid+
			       " (to expired directory).");
		    
		    expireDocument();
		    
		    // ####Need to sort out the sequence here- e.g. ARQ terminates ARQ on diposal.
		    // probably delete the registeration first to prevent spurious extra updates!
		    logger.log(INFO, 1, CLASS, id,"mainTask",
			       "ARQ::Terminating ARQ/UH thread for "+oid+".");
		    
		    // Terminate this thread.
		    terminate();
		    
		} catch (Exception e) {
		    logger.log(INFO, 1, CLASS, id,"mainTask",
			       "ARQ::Failed to disengage correctly on completion of observations:"+e);
		    logger.dumpStack(1,e);	
		}

		
	    } catch (Exception e) {
		logger.log(INFO, 1, CLASS, id,"mainTask",
			   "ARQ::Failed to send final document to UA: "+e);
		logger.dumpStack(1,e);	
	    }
	    
	}
   
	// Back round once more at least.	    
	//try {Thread.sleep(sleepTime);} catch (InterruptedException ix) {}
	
    }
	
    /** Called at end-of-run.
     * Nothing to do here now...
     */
    protected void shutdown() {
	logger.log(INFO, 1, CLASS, id,"shutdown",
		   "Shutting down "+getName());
    }

    /**
     * This test is called from the maintask to test each time round
     * after the pending list is cleared out. 
     * @return An int (state) denoting the completion state of the group.
     *         One of:-
     *  <dl>
     *    <dt>OBSERVATION_STATE_EXPIRED_FAILED     <dd>Expired and there were no frames generated.
     *    <dt>OBSERVATION_STATE_DONE               <dd>All image frames were returned.
     *    <dt>OBSERVATION_STATE_EXPIRED_INCOMPLETE <dd>Expired and some of the image frames were done.
     *    <dt>OBSERVATION_STATE_UNKNOWN            <dd>Something weird occured and cannot compute the completion state.
     *    <dt>OBSERVATION_STATE_ACTIVE             <dd>The document is still active.
     *  </dl>
     */
    protected int testCompletion() {

	int multrunExposureCount = 0;

	// if no series constraint present, default to 1 set of multruns (flexibly scheduled)
	int seriesConstraintCount = 1;
	int imageDataCount = 0;
	
	logger.log(INFO, 1, CLASS, id,"testCompletion",
		   "ARQ::Testing for completion of handler for "+oid+".");
	
	// if we have completed the number of observations requested by the UA,
	// send a complete.
	// This should only fire on the last UpdateHandler created for a particular MonitorGroup
	// if <SeriesConstraint><Count> accurately reflects (end date - start_date) / period.
	// If a MonitorGroup, we expect <SeriesConstraint><Count>* <Exposure><Count> frames, if a MonitorGroup.
	// If a FlexibleGroup, we expect <Exposure><Count> frames. 

	RTMLObservation observation = baseDocument.getObservation(0);
	RTMLSchedule    schedule    = null;
	Date            endDate     = null;

	if (observation != null) {
	    
	    schedule = observation.getSchedule();
	    
	    if (schedule != null) {
		
		multrunExposureCount = schedule.getExposureCount();
		RTMLSeriesConstraint seriesConstraint = schedule.getSeriesConstraint();
		
		if (seriesConstraint != null) {
		    // ### what does this have for FGs - they may not have been set,
		    // ### we just assume 1 in the obs.
		    seriesConstraintCount = seriesConstraint.getCount();
		    // Note should really check :
		    // (schedule.getEndDate() - schedule.getStartDate())/
		    // seriesConstraint.getInterval() 
		    // does not give a number less than seriesConstraintCount, in which
		    // case it is unlikely seriesConstraintCount monitor period will occur before 
		    // the document expires,
		    // or it does not give a number greater than seriesConstraintCount,
		    // in which case we may provide more data than the UA has asked for.
		}

		endDate = schedule.getEndDate();

	    }
	    
	    imageDataCount = observation.getImageDataCount();
	    
	}
	
	logger.log(INFO, 1, CLASS, id,"testCompletion",
		   "ARQ::Testing whether image data count "+imageDataCount+
		   " is greater than expected number of images "+
		   (multrunExposureCount*seriesConstraintCount)+" for "+oid+".");
	
	// At this point we have the following:
	// multrunExposureCount  == obs.getNumRuns()
	// imageDataCount        == No of images we processed.
	// seriesConstraintCount == Requested no of monitor windows (FG deduced as 1)
	//
      
	if (imageDataCount >= (multrunExposureCount*seriesConstraintCount)) 
	    return OBSERVATION_STATE_DONE;

	// Not complete, check for expiry.
	if (endDate != null) {

	    long end = endDate.getTime();

	    logger.log(INFO,1,CLASS,id,"testCompletion",
		       "ARQ::Testing document for expiry against end date "+endDate+".");

	    if (end < (System.currentTimeMillis()-expiryOffset)) {
	
		if (imageDataCount > 0)
		    return OBSERVATION_STATE_EXPIRED_INCOMPLETE;
		else
		    return OBSERVATION_STATE_EXPIRED_FAILED;
	    } else 
		return OBSERVATION_STATE_ACTIVE;
	}

	// Unable to deduce..	
	return OBSERVATION_STATE_UNKNOWN;
     
    } // [testCompletion]

  
    /**
     * Tries to pull an image file off the OCC and stick it in the appropriate directory.
     * @param imageFileName The remote filename path.
     * @param destFileName The local filename path to store the copied file into.
     * @exception Exception Thrown if the transfer failed.
     * @see TelescopeEmbeddedAgent#getImageTransferClient
     * @see #logger
     */
    private void transfer(String imageFileName, String destFileName) throws Exception {
	
	SSLFileTransfer.Client client = tea.getImageTransferClient();
	
	if (client == null) 
	    throw new Exception("The transfer client is not available");
	
	logger.log(INFO, 1, CLASS, id,"transfer",
		   "ARQ::Requesting image file: "+imageFileName+" -> "+destFileName);
	
	client.request(imageFileName, destFileName);
	
    }


    /**
     * Uses the baseDocument to figure out which project this document belong to.
     * Then tries to create the a suitable pipeline processing plugin.
     * @return A new instance of a class implementing PipelineProcessingPlugin, suitable for this data.
     * @exception NullPointerException Thrown if baseDocument is null.
     * @exception ClassNotFoundException Thrown if the specified class does not exist.
     * @exception InstantiationException Thrown if an instance of the class cannot be created.
     * @exception IllegalAccessException Thrown if an instance of the class cannot be created.
     * @see #baseDocument
     */
    protected PipelineProcessingPlugin getPipelinePluginFromDoc() 
	throws NullPointerException, 
	       ClassNotFoundException, InstantiationException, IllegalAccessException
    {
	RTMLContact contact = null;
	RTMLProject project = null;
	String userId = null;
	String proposalId = null;
	String pipelinePluginClassname = null;
	Class pipelinePluginClass = null;
	String key = null;

	System.err.println("Starting dodgy bit...");
	
	logger.log(INFO, 1, CLASS, id,"getPipelinePluginFromDoc","ARQ:: Started.");
	
	System.err.println("Done dodgy bit...");
	
	if(baseDocument == null)
	    {
		throw new NullPointerException(this.getClass().getName()+
					       ":getPipelinePluginFromDoc:base document was null.");
	    }
	// get userId
	contact = baseDocument.getContact();
	if(contact == null)
	    {
		throw new NullPointerException(this.getClass().getName()+
					       ":getPipelinePluginFromDoc:Contact was null for document.");
	    }
	userId = contact.getUser();
	// get proposalId
	project = baseDocument.getProject();
	if(project == null)
	    {
		throw new NullPointerException(this.getClass().getName()+
					       ":getPipelinePluginFromDoc:Project was null for document.");
	    }
	proposalId = project.getProject();
	// get pipeline plugin class name
	key = new String("pipeline.plugin.classname."+userId+"."+proposalId);
	logger.log(INFO, 1, CLASS, id,"getPipelinePluginFromDoc",
		   "ARQ:: Trying to get pipeline classname using key "+key+".");
	pipelinePluginClassname = tea.getPropertyString(key);
	if(pipelinePluginClassname == null)
	    {
		logger.log(INFO, 1, CLASS, id,"getPipelinePluginFromDoc",
			   "ARQ:: Project specific pipeline does not exist, "+
			   "trying default pipeline.plugin.classname.default.");
		pipelinePluginClassname = tea.getPropertyString("pipeline.plugin.classname.default");
	    }
	logger.log(INFO, 1, CLASS, id,"getPipelinePluginFromDoc",
		   "ARQ:: Pipeline classname found was "+pipelinePluginClassname+".");
	// if we could not find a class name to instansiate, fail.
	if(pipelinePluginClassname == null)
	    {
		throw new NullPointerException(this.getClass().getName()+
					       ":getPipelinePluginFromDoc:Pipeline classname found was null.");
	    }
	// get pipeline plugin class from class name
	pipelinePluginClass = Class.forName(pipelinePluginClassname);
	// get pipeline plugin instance from class
	return  (PipelineProcessingPlugin)(pipelinePluginClass.newInstance());
    }
    
} //[AgentRequestHandler]
