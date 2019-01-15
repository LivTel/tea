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

/**
 * Handles an observation request. Target, Group and Observation objects are
 * sent to the OSS. The AgentRequestHandler (ARQ) is registered with the TEA and
 * it waits for updates from the RCS as the observations are performed.
 */
public class AgentRequestHandler extends ControlThread implements Logging {

	/** Class name for logging. */
	public static final String CLASS = "ARQ";

	/** Observation state indicating unable to deduce completion state. */
	public static final int OBSERVATION_STATE_UNKNOWN = 0;

	/**
	 * Observation state indicating that the group has expired and there were no
	 * frames generated.
	 */
	public static final int OBSERVATION_STATE_EXPIRED_FAILED = 1;

	/** Observation state indicating that all image frames were returned. */
	public static final int OBSERVATION_STATE_DONE = 2;

	/**
	 * Observation state indicating that the group has expired and some of the
	 * image frames were done.
	 */
	public static final int OBSERVATION_STATE_EXPIRED_INCOMPLETE = 3;

	/** Observation state indicating that the group is still active. */
	public static final int OBSERVATION_STATE_ACTIVE = 4;

	/** Polling interval for pending queue. */
	private static final long SLEEP_TIME = 5000L;

	/** Polling interval while awaiting first data. */
	private static final long LONG_SLEEP_TIME = 1800 * 1000L;

	/**
	 * Time margin above expected execution time when we give up as a lost
	 * cause.
	 */
	private static final long TIME_MARGIN = 2 * 3600 * 1000L;

	/** Time after expiry before we expire the document (ms). */
	// private static final long DEFAULT_EXPIRY_OFFSET = 2*3600*1000L;
	private static final long DEFAULT_EXPIRY_OFFSET = 600 * 1000L;

	/** Flags completed successful obs as notified by RCS Telemetry. */
	public static final int OBS_DONE = 1;

	/** Flags completed but failed obs as notified by RCS Telemetry. */
	public static final int OBS_FAILED = 2;

	/** Flags uncompleted obs - Default initial state. */
	public static final int OBS_NOT_DONE_YET = 0;

	/** True if we are able to handle updates. */
	private boolean updateHandler = false;

	/** Reference to the TEA. */
	private TelescopeEmbeddedAgent tea;

	/** The base request document. */
	private RTMLDocument baseDocument;

	/** Where we store the base document persistantly. */
	private File file;

	// /** EstarIO for responses.*/
	// private eSTARIO io;

	// /** GLobusIO handle for responses.*/
	// private GlobusIOHandle handle;

	/** Lock for synchronization - used by JMSCommandClient ?. */
	private volatile BooleanLock lock = new BooleanLock(true);

	/** The groupID. */
	private String gid;

	/** ARQ is sleeping. */
	private volatile boolean sleeping = false;

	/**
	 * The observation (within the generated Group) which we are expecting to
	 * perform. - this is not persisted, i.e. is only visible:-
	 * <ul>
	 * <li>when group and obs are created.
	 * <li>when we receive an update (ObsInfo) from RCS.
	 * </ul>
	 */
	// private Observation observation;

	/** The update document - temporarily created to send back to remote UA. */
	private RTMLDocument updateDoc;

	/** Pipeline processing plugin implementation. (#### Should be per project) */
	private PipelineProcessingPlugin pipelinePlugin = null;

	/**
	 * List of pending image filenames to transfer and process - these are lost
	 * between instantiations i.e. non-persistent.
	 */
	private List pending;

	/**
	 * List of processed image filenames - dont know what these will be used for
	 * - these are lost between instantiations i.e. non-persistent.
	 */
	private List processed;

	/** Flag to indicate whether the observations have completed. */
	// private volatile int state = OBS_NOT_DONE_YET;

	/** Records the number of exposures received so far. */
	private int countExposures;

	/** Current sleep setting. */
	private long sleepTime = LONG_SLEEP_TIME;

	/** Time after expiry before we decide to expire the document (ms). */
	private long expiryOffset;

	/** ARQ id. */
	private String id;

	/** Class logger. */
	private Logger alogger = null;

	private LogGenerator logger = null;
	/**
	 * Boolean, true if the document has been aborted.
	 */
	private boolean aborted = false;

	/**
	 * Create an AgentRequestHandler. This constructor is used to create ARQs on
	 * startup by the TEA during loadDocuments and by the ConnectionHandler when
	 * a new request has been confirmed.
	 * 
	 * @param tea
	 *            The TEA instance.
	 * @param baseDocument
	 *            The base request document.
	 */
	public AgentRequestHandler(TelescopeEmbeddedAgent tea, RTMLDocument baseDocument) {
		super("ARQ", true);
		this.tea = tea;
		this.baseDocument = baseDocument;

		// io = tea.getEstarIo();

		pending = new Vector();
		processed = new Vector();

		countExposures = 0;
		// elapsedTime = 0L;

		expiryOffset = DEFAULT_EXPIRY_OFFSET;

		alogger = LogManager.getLogger("TRACE");

		logger = alogger.generate().system("TEA").subSystem("RequestManagement")
				.srcCompClass(this.getClass().getName()).srcCompId("ARQ");

	}

	/**
	 * Sets the id from a supplied string. Usually this includes the TEA's id as
	 * prepend.
	 */
	public void setARQId(String id) {
		this.id = id;
		// reset the srcid for logging
		logger.srcCompId(id);
	}

	public String getARQId() {
		return id;
	}

	/**
	 * Sets the GroupID.
	 * 
	 * @param gid
	 *            The GroupID.
	 */
	public void setGid(String gid) {
		this.gid = gid;
	}

	public String getGid() {
		return gid;
	}

	/**
	 * Method to call when an abort document has been sent for this ARQ's document.
	 * This will cause the mainTask to terminate.
	 * @see #aborted
	 */
	public void abort()
	{
		aborted = true;
	}

	/**
	 * Set the base-document.
	 * 
	 * @param doc
	 *            The base-document.
	 */
	public void setBaseDocument(RTMLDocument doc) {
		this.baseDocument = doc;
	}

	/**
	 * Set the file to persist the base document to.
	 * 
	 * @param file
	 *            The file to store the base-document in.
	 */
	public void setDocumentFile(File file) {
		this.file = file;
	}

	// /** Sets flag to indicate that observation has FAILED.
	// * ###This is also used to knock the UH into LONG_SLEEP.
	// */
	// public void setObservationFailed() {
	// this.state = OBS_FAILED;
	// sleepTime = LONG_SLEEP_TIME;
	// }

	// /** Sets flag to indicate that observation has SUCCEDED.
	// * ###This is also used to knock the UH into LONG_SLEEP.
	// */
	// public void setObservationCompleted() {
	// this.state = OBS_DONE;
	// sleepTime = LONG_SLEEP_TIME;
	// /}

	/**
	 * Returns the base document.
	 * 
	 * @return The base-document..
	 */
	public RTMLDocument getBaseDocument() {
		return baseDocument;
	}

	/**
	 * Returns the base-document file.
	 * 
	 * @return The file used to store the base document.
	 */
	public File getDocumentFile() {
		return file;
	}

	/** Returns true if this ARQ will only accept reduced images from the scope. */
	public boolean wantsReducedImagesOnly(int obsId) throws Exception {

		RTMLObservation obs = baseDocument.getObservation(obsId);
		RTMLDevice dev = obs.getDevice();

		boolean wantsReducedImagesOnly = DeviceInstrumentUtilites.instrumentUpdateRequiresReductionTelemetry(tea, dev);
		return wantsReducedImagesOnly;

	}

	/**
	 * Adds the name of a file to the pending list. ###This is also used to
	 * knock the UH into SHORT_SLEEP.
	 * 
	 * @param fileName
	 *            The full (path) name of the file on the OCC including
	 *            instrument NFS mount details.
	 */
	public void addImageInfo(ImageInfo info) {

		pending.add(info);
		sleepTime = SLEEP_TIME; // short period now
		// Actually make the long sleep breakout asap
		// but only if its in a sleep, not while its processing.
		if (sleeping)
			interrupt();
	}

	/**
	 * ###TEMP Called from DocExpirator - Makes the base document expire and
	 * removes this ARQ from the agent list.
	 */
	public void expireDocument() throws Exception {

		tea.expireDocument(file);
		tea.deleteUpdateHandler(gid);

	}

	/**
	 * Prepare the UpdateHandler thread but dont start it.
	 * 
	 * @throws Exception
	 *             if the UpdateHandler fails to prepare.
	 */
	public void prepareUpdateHandler() throws Exception {

		if (updateHandler)
			throw new Exception("Updatehandler is already prepared for use");

		updateDoc = (RTMLDocument) baseDocument.deepClone();
		pipelinePlugin = getPipelinePluginFromDoc();
		pipelinePlugin.setTea(tea);
		pipelinePlugin.initialise();

		updateHandler = true;
	}

	/** Waits on a Thread lock. */
	private void waitOnLock() {

		logger.create().info().level(5).extractCallInfo().msg("Method entry").send();
		try {
			lock.waitUntilTrue(0L);
		} catch (InterruptedException ix) {
			logger.create().info().level(4).extractCallInfo().msg("Interrupted waiting on lock").send();
		}
		logger.create().info().level(5).extractCallInfo().msg("Lock is free").send();
	}

	/** Frees the Thread lock. */
	private void freeLock() {
		logger.create().info().level(4).extractCallInfo().msg("Releasing lock").send();
		lock.setValue(true);
	}

	/** Set the lock. */
	private void setLock() {
		lock.setValue(false);
	}

	/** Checks the instrument details. */
	private void checkInstrument() {

	}

	// ### These doc reply methods all need looking at there is duplication and
	// ### lack of obvious useful extra functionality.

	// /** Send an Error reply with sepcified message.
	// * Uses the already opened eSTAR io handle.
	// * @param document The document to send.
	// * @param errorMessage The error message to include.
	// * @see #io
	// * @see TelescopeEmbeddedAgent#createErrorDocReply
	// */
	// public void sendError(RTMLDocument document, String errorMessage) {

	// String reply = TelescopeEmbeddedAgent.createErrorDocReply(document,
	// errorMessage);

	// io.messageWrite(handle, reply);

	// logger.create().info().level(1).extractCallInfo().msg(INFO, 1, CLASS,
	// getName(),"sendError","Sent error message: "+errorMessage);

	// io.clientClose(handle);

	// }

	// /** Send a reply of specified type.
	// * Uses the already opened eSTAR io handle.
	// * @param document The document to send.
	// * @param type The type of document to send.
	// * @see #io
	// * @see TelescopeEmbeddedAgent#createDocReply
	// */
	// public void sendDoc(RTMLDocument document, String type) {

	// String reply = TelescopeEmbeddedAgent.createDocReply(document, type);

	// io.messageWrite(handle, reply);

	// logger.create().info().level(1).extractCallInfo().msg(INFO, 1, CLASS,
	// getName(),"sendDoc","Sent doc type: "+type);

	// io.clientClose(handle);

	// }

	/** Initialization. */
	protected void initialise() {
	}

	/**
	 * Called by wrapping thread forever until terminated. This thread should
	 * only terminate when the ARQ itself is deregistered and destroyed, which
	 * indicates that one or more of:-
	 * <ul>
	 * <li>Group's End Date has been exceeded by a generous margin. (this
	 * function is currently performed by the DocumentExpirator).
	 * <li>#numberExposures > expected number.
	 * </ul>
	 */
	protected void mainTask() {

		// Back round once more at least.

		sleeping = true;
		try {
			Thread.sleep(sleepTime);
		} catch (InterruptedException ix) {
		}
		sleeping = false;

		logger.create().info().level(5).extractCallInfo().msg("Polling, entered maintask").send();

		RTMLImageData imageData = null;

		// Always clear out the pending list.

		// ### WARNING: There are several spots where the new sleeping flag
		// could cause us not
		// ### to process a new image immediately beacuse we drop out if the
		// loop and
		// ### back to the start of maintask - these need changing to 'continue'
		// rather than
		// ### return (probably).

		// If image filenames in queue, grab and process.
		while (!pending.isEmpty()) {

			ImageInfo info = (ImageInfo) pending.remove(0);

			int iobs = info.getObsId();
			String imageFileName = info.getImageFileName();

			logger.create().info().level(2).extractCallInfo()
					.msg("Processing image filename " + imageFileName + " for " + gid + "/" + iobs).send();

			// Generate the correct destination file name.
			String destDirName = null;
			try {
				destDirName = pipelinePlugin.getInputDirectory();
			} catch (Exception e) {
				logger.create().error().level(3).extractCallInfo()
						.msg("Getting input directory failed for pipeline plugin " + pipelinePlugin + ":" + e).send();
				alogger.dumpStack(1, e);
				return;
			}

			File destDir = new File(destDirName);
			File fullFile = new File(imageFileName);
			String rawFileName = fullFile.getName();
			File destFile = new File(destDir, rawFileName);
			String destFileName = destFile.getPath();

			// Grab image from OCC/ICC.
			try {
				logger.create()
						.info()
						.level(2)
						.extractCallInfo()
						.msg("Transferring image filename " + imageFileName + " to " + destFileName + " for " + gid
								+ ".").send();
				transfer(imageFileName, destFileName);
				logger.create().info().level(2).extractCallInfo().msg("Transfered image okay").send();
			} catch (Exception e) {
				logger.create()
						.error()
						.level(3)
						.extractCallInfo()
						.msg("Transferring image filename " + imageFileName + " to " + destFileName + " failed for "
								+ gid + ":" + e).send();
				alogger.dumpStack(1, e);
				return;
			}

			// Pipeline process
			try {
				imageData = pipelinePlugin.processFile(destFile);
			} catch (Exception e) {
				logger.create().error().level(3).extractCallInfo()
						.msg("pipelinePlugin.processFile " + destFile + " failed:" + e).send();
				alogger.dumpStack(1, e);
				return;
			}

			// Add processed data to basedoc
			RTMLObservation obs = baseDocument.getObservation(iobs);
			if (obs != null) {
				try {
					obs.addImageData(imageData);
				} catch (Exception e) {
					logger.create().error().level(3).extractCallInfo()
							.msg("addImageData " + imageData + " failed:" + e).send();
					alogger.dumpStack(1, e);
					return;
				}
			}

			// Save the modified base document.
			try {
				logger.create().info().level(1).extractCallInfo().msg("Saving document for " + gid + ".").send();
				tea.saveDocument(baseDocument, getDocumentFile());
			} catch (Exception e) {
				logger.create().error().level(3).extractCallInfo().msg("saveDocument for " + gid + " failed:" + e)
						.send();
				alogger.dumpStack(1, e);
				return;
			}

			// Add processed data to updatedoc.
			// Note we re-use the same UpdateDoc that got cloned from the
			// BaseDoc
			// at the start - we need to clear out any added image data each
			// time we re-use it.
			obs = updateDoc.getObservation(iobs);
			if (obs != null) {
				try {
					// clear any left-over image data.
					obs.clearImageDataList();
					// add just-received reduced image data.
					obs.addImageData(imageData);
				} catch (Exception e) {
					logger.create().error().level(3).extractCallInfo()
							.msg("addImageData for " + gid + " " + imageData + " failed:" + e).send();
					alogger.dumpStack(1, e);
					return;
				}
			}

			// Now try to send the update to the UA.
			// this is a mess - need global comms method like
			// tea.sendDoc(doc); This may need to go in athread
			// as it may block for a while if the connection is blocked
			try {
				logger.create().info().level(2).extractCallInfo().msg("Sending update document for " + gid + ".")
						.send();
				updateDoc.setUpdate();
				updateDoc.addHistoryEntry("TEA:" + tea.getId(), null, "ARQ::Sending update document for " + gid + "/"
						+ iobs + ".");
				baseDocument.addHistoryEntry("TEA:" + tea.getId(), null, "ARQ::Sending update document for " + gid
						+ "/" + iobs + ".");
				tea.sendDocumentToIA(updateDoc);
				logger.create().info().level(2).extractCallInfo().msg("Sent update document for " + gid + ".").send();
			} catch (Exception e) {
				logger.create().error().level(3).extractCallInfo().msg("sendDocumentToIA failed for " + gid + ":" + e)
						.send();
				alogger.dumpStack(1, e);
				return;
			}

			// Add to list of processed filenames.
			processed.add(imageFileName);

		} // while pending list not empty

		// Now we should do the test to see if weve got all the expected images
		// and or expired.
		int observationState = testCompletion();
		String docType = null;

		switch (observationState) {
		case OBSERVATION_STATE_ACTIVE:
			logger.create().info().level(3).extractCallInfo().msg("Observation is still active: " + gid).send();
			break;
		case OBSERVATION_STATE_UNKNOWN:
			logger.create().info().level(3).extractCallInfo().msg("Unable to determine observation state for: " + gid)
					.send();
			break;
		case OBSERVATION_STATE_DONE:
			docType = "observation";
			baseDocument.setComplete();
			baseDocument.addHistoryEntry("TEA:" + tea.getId(), null, "Observations completed.");
			break;
		case OBSERVATION_STATE_EXPIRED_INCOMPLETE:
			docType = "incomplete";
			baseDocument.setIncomplete();
			baseDocument.addHistoryEntry("TEA:" + tea.getId(), null, "Observations incomplete.");
			break;
		case OBSERVATION_STATE_EXPIRED_FAILED:
			docType = "fail";
			baseDocument.setFail();
			baseDocument.addHistoryEntry("TEA:" + tea.getId(), null, "Observations failed.");
			break;
		}

		// All done, send final document and disengage.
		if (docType != null) {
			try {
				logger.create().info().level(2).extractCallInfo()
						.msg("Sending observation final status document (" + docType + ") document for " + gid + ".")
						.send();
				tea.sendDocumentToIA(baseDocument);
				// Only if we succeeded in sending can we disengage the ARQ.

				// Deregister immediately.
				tea.deleteUpdateHandler(gid);
				logger.create().info().level(3).extractCallInfo()
						.msg("Deregistering handler: " + getName() + " for " + gid + ".").send();

				try {

					logger.create().info().level(2).extractCallInfo()
							.msg("Saving observation-completed document for " + gid + ".").send();

					// save document - pass the filename aswell
					tea.saveDocument(baseDocument, getDocumentFile());

					logger.create().info().level(2).extractCallInfo()
							.msg("Moving observation-completed document for " + gid + " (to expired directory).")
							.send();

					expireDocument();

					// ####Need to sort out the sequence here- e.g. ARQ
					// terminates ARQ on diposal.
					// probably delete the registeration first to prevent
					// spurious extra updates!
					logger.create().info().level(2).extractCallInfo().msg("Terminating ARQ/UH thread for " + gid + ".")
							.send();

					// Terminate this thread.
					terminate();

				} catch (Exception e) {
					logger.create().error().level(3).extractCallInfo()
							.msg("Failed to disengage correctly on completion of observations:" + e).send();
					alogger.dumpStack(1, e);
				}

			} catch (Exception e) {
				logger.create().error().level(3).extractCallInfo().msg("Failed to send final document to UA: " + e)
						.send();
				alogger.dumpStack(1, e);
			}

		}

		// Back round once more at least.
		// try {Thread.sleep(sleepTime);} catch (InterruptedException ix) {}

	}

	/**
	 * Called at end-of-run. Nothing to do here now...
	 */
	protected void shutdown() {
		logger.create().info().level(1).extractCallInfo().msg("Shutting down " + getName()).send();
	}

	/**
	 * This test is called from the maintask to test each time round after the
	 * pending list is cleared out.
	 * 
	 * @return An int (state) denoting the completion state of the group. One
	 *         of:-
	 *         <dl>
	 *         <dt>OBSERVATION_STATE_EXPIRED_FAILED
	 *         <dd>Expired and there were no frames generated, or aborted.
	 *         <dt>OBSERVATION_STATE_DONE
	 *         <dd>All image frames were returned.
	 *         <dt>OBSERVATION_STATE_EXPIRED_INCOMPLETE
	 *         <dd>Expired and some of the image frames were done.
	 *         <dt>OBSERVATION_STATE_UNKNOWN
	 *         <dd>Something weird occured and cannot compute the completion
	 *         state.
	 *         <dt>OBSERVATION_STATE_ACTIVE
	 *         <dd>The document is still active.
	 *         </dl>
	 * @see #aborted
	 */
	protected int testCompletion() {

		// if no series constraint present, default to 1 set of multruns
		// (flexibly scheduled)
		int totalRequiredExposureCount = 1;
		int actualImageDataCount = 0;

		logger.create().info().level(3).extractCallInfo().msg("Testing for completion of handler for " + gid + ".")
				.send();

		// if we have completed the number of observations requested by the UA,
		// send a complete.
		// This should only fire on the last UpdateHandler created for a
		// particular MonitorGroup
		// if <SeriesConstraint><Count> accurately reflects (end date -
		// start_date) / period.
		// If a MonitorGroup, we expect <SeriesConstraint><Count>*
		// <Exposure><Count> frames, if a MonitorGroup.
		// If a FlexibleGroup, we expect <Exposure><Count> frames.

		// extract enddate from master schedule (obs-0)
		RTMLObservation obs0 = baseDocument.getObservation(0);
		Date endDate = null;
		if (obs0 != null) {
			RTMLSchedule schedule = obs0.getSchedule();
			if (schedule != null) {
				endDate = schedule.getEndDate();
			}
		}

		// count totals over all obs in document
		int nobs = baseDocument.getObservationListCount();
		for (int iobs = 0; iobs < nobs; iobs++) {

			int multrunExposureCount = 0;

			// if no series constraint present, default to 1 set of multruns
			// (flexibly scheduled)
			int seriesConstraintCount = 1;
			int imageDataCount = 0;

			RTMLObservation observation = baseDocument.getObservation(iobs);
			RTMLSchedule schedule = null;

			if (observation != null) {

				schedule = observation.getSchedule();

				if (schedule != null) {

					multrunExposureCount = schedule.getExposureCount();
					RTMLSeriesConstraint seriesConstraint = schedule.getSeriesConstraint();

					if (seriesConstraint != null) {
						// ### what does this have for FGs - they may not have
						// been set,
						// ### we just assume 1 in the obs.
						seriesConstraintCount = seriesConstraint.getCount();
						// Note should really check :
						// (schedule.getEndDate() - schedule.getStartDate())/
						// seriesConstraint.getInterval()
						// does not give a number less than
						// seriesConstraintCount, in which
						// case it is unlikely seriesConstraintCount monitor
						// period will occur before
						// the document expires,
						// or it does not give a number greater than
						// seriesConstraintCount,
						// in which case we may provide more data than the UA
						// has asked for.
					}

					totalRequiredExposureCount += seriesConstraintCount * multrunExposureCount;

				}

				actualImageDataCount += observation.getImageDataCount();
			}

		} // next obs

		logger.create()
				.info()
				.level(3)
				.extractCallInfo()
				.msg("Testing whether total image data count " + actualImageDataCount
						+ " is greater than expected number of images " + totalRequiredExposureCount + " for " + gid
						+ ".").send();

		// At this point we have the following:
		// multrunExposureCount == obs.getNumRuns()
		// imageDataCount == No of images we processed.
		// seriesConstraintCount == Requested no of monitor windows (FG deduced
		// as 1)
		//

		if (actualImageDataCount >= totalRequiredExposureCount)
			return OBSERVATION_STATE_DONE;

		// Not complete, check for expiry.
		if (endDate != null) {

			long end = endDate.getTime();

			logger.create().info().level(3).extractCallInfo()
					.msg("Testing document for expiry against end date " + endDate + ".").send();

			if (end < (System.currentTimeMillis() - expiryOffset)) {

				if (actualImageDataCount > 0)
					return OBSERVATION_STATE_EXPIRED_INCOMPLETE;
				else
					return OBSERVATION_STATE_EXPIRED_FAILED;
			} else
				return OBSERVATION_STATE_ACTIVE;
		}
		// Have we been aborted
		if(aborted)
		{
			logger.create().info().level(3).extractCallInfo().msg("Document was aborted.").send();
			return OBSERVATION_STATE_EXPIRED_FAILED;
		}
		// Unable to deduce..
		return OBSERVATION_STATE_UNKNOWN;

	} // [testCompletion]

	/**
	 * Tries to pull an image file off the OCC and stick it in the appropriate
	 * directory.
	 * 
	 * @param imageFileName
	 *            The remote filename path.
	 * @param destFileName
	 *            The local filename path to store the copied file into.
	 * @exception Exception
	 *                Thrown if the transfer failed.
	 * @see TelescopeEmbeddedAgent#getImageTransferClient
	 * @see #logger
	 */
	private void transfer(String imageFileName, String destFileName) throws Exception {

		SSLFileTransfer.Client client = tea.getImageTransferClient();

		if (client == null)
			throw new Exception("The transfer client is not available");

		logger.create().info().level(2).extractCallInfo()
				.msg("Requesting image file: " + imageFileName + " -> " + destFileName).send();

		client.request(imageFileName, destFileName);

	}

	/**
	 * Uses the baseDocument to figure out which project this document belong
	 * to. Then tries to create the a suitable pipeline processing plugin. The
	 * plugin's ID is set.
	 * 
	 * @return A new instance of a class implementing PipelineProcessingPlugin,
	 *         suitable for this data.
	 * @exception NullPointerException
	 *                Thrown if baseDocument is null.
	 * @exception ClassNotFoundException
	 *                Thrown if the specified class does not exist.
	 * @exception InstantiationException
	 *                Thrown if an instance of the class cannot be created.
	 * @exception IllegalAccessException
	 *                Thrown if an instance of the class cannot be created.
	 * @see #baseDocument
	 * @see DeviceInstrumentUtilites#getInstrumentId
	 */
	protected PipelineProcessingPlugin getPipelinePluginFromDoc() throws NullPointerException, ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		PipelineProcessingPlugin plugin = null;
		RTMLContact contact = null;
		RTMLProject project = null;
		RTMLDevice device = null;
		String userId = null;
		String proposalId = null;
		String instrumentId = null;
		String pipelinePluginClassname = null;
		Class pipelinePluginClass = null;
		String pluginId = null;
		String key = null;

		logger.create().info().level(5).extractCallInfo().msg("Method entry").send();

		if (baseDocument == null) {
			throw new NullPointerException(this.getClass().getName()
					+ ":getPipelinePluginFromDoc:base document was null.");
		}
		// get userId
		contact = baseDocument.getContact();
		if (contact == null) {
			throw new NullPointerException(this.getClass().getName()
					+ ":getPipelinePluginFromDoc:Contact was null for document.");
		}
		userId = contact.getUser();
		// get proposalId
		project = baseDocument.getProject();
		if (project == null) {
			throw new NullPointerException(this.getClass().getName()
					+ ":getPipelinePluginFromDoc:Project was null for document.");
		}
		proposalId = project.getProject();
		// get instrument type name

		RTMLObservation obs = baseDocument.getObservation(0);
		if (obs != null)
			device = obs.getDevice();
		if (device == null)
			device = baseDocument.getDevice();
		logger.create().info().level(4).extractCallInfo().msg("Getting inst-type for device: " + device).send();
		instrumentId = DeviceInstrumentUtilites.getInstrumentId(tea, device);
		// get pipeline plugin class name
		pluginId = new String(userId + "." + proposalId);
		key = new String("pipeline.plugin.classname." + pluginId + "." + instrumentId);
		logger.create().info().level(4).extractCallInfo()
				.msg("Trying to get pipeline classname using key " + key + ".").send();
		pipelinePluginClassname = tea.getPropertyString(key);
		if (pipelinePluginClassname == null) {
			pluginId = new String("default");
			key = new String("pipeline.plugin.classname." + pluginId + "." + instrumentId);
			logger.create().info().level(3).extractCallInfo()
					.msg("Project specific pipeline does not exist, " + "trying default key " + key).send();
			pipelinePluginClassname = tea.getPropertyString(key);
		}
		logger.create().info().level(3).extractCallInfo()
				.msg("Pipeline classname found was " + pipelinePluginClassname + ".").send();
		// if we could not find a class name to instansiate, fail.
		if (pipelinePluginClassname == null) {
			throw new NullPointerException(this.getClass().getName()
					+ ":getPipelinePluginFromDoc:Pipeline classname found was null.");
		}
		// get pipeline plugin class from class name
		pipelinePluginClass = Class.forName(pipelinePluginClassname);
		// get pipeline plugin instance from class
		plugin = (PipelineProcessingPlugin) (pipelinePluginClass.newInstance());
		// set plugin id
		plugin.setId(pluginId);
		plugin.setInstrumentId(instrumentId);
		return plugin;
	}

} // [AgentRequestHandler]
