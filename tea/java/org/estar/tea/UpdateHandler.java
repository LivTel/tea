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

/** 
 * Handles observation updates from the RCS. 
 *
 * When the thread is started it will keep looking in its pending list for any
 * new image file names. If any, these should be pulled across and stored in the
 * processing directory. They should then be pipelined and the data extracted and 
 * placed in the update document. 
 *
 */
public class UpdateHandler extends ControlThread implements Logging
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: UpdateHandler.java,v 1.8 2005-05-25 14:26:56 snf Exp $";
	public static final String CLASS = "UpdateHandler";

	/** Polling interval for pending queue.*/
	private static final long SLEEP_TIME = 5000L;

	/** Time margin above expected execution time when we give up as a lost cause.*/
	private static final long TIME_MARGIN = 2*3600*1000L;

	/** Flags completed successful obs.*/
	public static final int OBS_DONE = 1;

	/** Flags completed but failed obs.*/
	public static final int OBS_FAILED = 2;

	/** Flags uncompleted obs.*/
	public static final int OBS_NOT_DONE_YET = 0;

	/** The TEA.*/
	TelescopeEmbeddedAgent tea;

	/** The base document.*/
	private RTMLDocument baseDoc;

	/** The update document.*/
	private RTMLDocument updateDoc;

	/** Pipeline processing plugin implementation. (#### Should be per project)*/
	private PipelineProcessingPlugin pipelinePlugin = null;

	/** List of pending image filenames to transfer and process.*/
	private List pending;

	/** List of processed image filenames - dont know what these will be used for.*/
	private List processed;

	/** Flag to indicate whether the observations have completed.*/
	private volatile int state = OBS_NOT_DONE_YET;

	/** Number of exposures we expect to get.*/
	private int numberExposures;
    
	/** Time we expect this observation/group to take - combination of
	 * number of exposures, exposure time, readout and dprt allowances (ms).
	 */
	private long expectedTime;

	/** Time we started processing.*/
	private long startTime;

	/** Records the elapsed processing time.*/
	private long elapsedTime;

	/** Records the number of exposures received so far.*/
	private int countExposures;

	/** 
	 * The observation identifier, the observation full path.
	 */
	private String observationId = null;
	/**
	 * The logger.
	 */
	Logger logger = null;

	/** 
	 * Create an UpdateHandler. Setup logger. Initialises pending and processed.
	 * Figures out which pipeline plugin to use by calling pipelinePlugin.
	 * @param tea The TEA.
	 * @param baseDoc The base document.
	 * @throws Exception Thrown if an error occurs. i.e. the baseDir deepClone() fails. getPipelinePluginFromDoc
	 *                   fails.
	 * @see #getPipelinePluginFromDoc
	 * @see #tea
	 * @see #baseDoc
	 * @see #updateDoc
	 * @see #pending
	 * @see #processed
	 * @see #pipelinePlugin
	 * @see #logger
	 * @see #countExposures
	 * @see #elapsedTime
	 */
	public UpdateHandler(TelescopeEmbeddedAgent tea, RTMLDocument baseDoc) throws Exception
	{
		super("UH", true);
		this.tea     = tea;
		this.baseDoc = baseDoc;

		pending   = new Vector();
		processed = new Vector();

		logger = LogManager.getLogger(this);

		updateDoc = (RTMLDocument)baseDoc.deepClone();
		pipelinePlugin = getPipelinePluginFromDoc();
		pipelinePlugin.setTea(tea);
		pipelinePlugin.initialise();
		countExposures = 0;
		elapsedTime = 0L;

	}

	/** 
	 * Set the observation ID.
	 */
	public void setObservationId(String s) { this.observationId = s; }

	/**
	 * Sets the number of exposures we expect.
	 */
	public void setNumberExposures(int n) { this.numberExposures = n; }

	/**
	 * Increments the number of exposures we expect.
	 */
	public void incrementNumberExposures() { this.numberExposures++; }

	/**
	 * Gets the number of exposures we expect.
	 */
	public int getNumberExposures() { return this.numberExposures; }

	/** Sets the expected total observation time (ms).*/
	public void setExpectedTime(long t) { this.expectedTime = t; }

	/** Sets flag to indicate that observation has FAILED.*/
	public void setObservationFailed() { this.state = OBS_FAILED; }
    
	/** Sets flag to indicate that observation has SUCCEDED.*/
	public void setObservationCompleted() { this.state = OBS_DONE; }
    
	/** Adds the name of a file to the pending list.
	 * @param fileName The full (path) name of the file on the OCC including instrument NFS mount details.
	 */
	public void addImageFileName(String fileName) {
		pending.add(fileName);
	}

	// Called when processing is started.*/
	protected void initialise() {
		startTime = System.currentTimeMillis();
	}

	/**
	 * Uses the baseDoc to figure out which project this document belong to.
	 * Then tries to create the a suitable pipeline processing plugin.
	 * @return A new instance of a class implementing PipelineProcessingPlugin, suitable for this data.
	 * @exception NullPointerException Thrown if baseDoc is null.
	 * @exception ClassNotFoundException Thrown if the specified class does not exist.
	 * @exception InstantiationException Thrown if an instance of the class cannot be created.
	 * @exception IllegalAccessException Thrown if an instance of the class cannot be created.
	 * @see #baseDoc
	 */
	protected PipelineProcessingPlugin getPipelinePluginFromDoc() throws NullPointerException, 
	       ClassNotFoundException, InstantiationException, IllegalAccessException
	{
		RTMLContact contact = null;
		RTMLProject project = null;
		String userId = null;
		String proposalId = null;
		String pipelinePluginClassname = null;
		Class pipelinePluginClass = null;
		String key = null;

		logger.log(INFO, 1, CLASS, tea.getId(),"getPipelinePluginFromDoc","UH:: Started.");
		if(baseDoc == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPipelinePluginFromDoc:base document was null.");
		}
		// get userId
		contact = baseDoc.getContact();
		if(contact == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPipelinePluginFromDoc:Contact was null for document.");
		}
		userId = contact.getUser();
		// get proposalId
	        project = baseDoc.getProject();
		if(project == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPipelinePluginFromDoc:Project was null for document.");
		}
		proposalId = project.getProject();
		// get pipeline plugin class name
		key = new String("pipeline.plugin.classname."+userId+"."+proposalId);
		logger.log(INFO, 1, CLASS, tea.getId(),"getPipelinePluginFromDoc",
			   "UH:: Trying to get pipeline classname using key "+key+".");
		pipelinePluginClassname = tea.getPropertyString(key);
		if(pipelinePluginClassname == null)
		{
			logger.log(INFO, 1, CLASS, tea.getId(),"getPipelinePluginFromDoc",
				   "UH:: Project specific pipeline does not exist, "+
				   "trying default pipeline.plugin.classname.default.");
			pipelinePluginClassname = tea.getPropertyString("pipeline.plugin.classname.default");
		}
		logger.log(INFO, 1, CLASS, tea.getId(),"getPipelinePluginFromDoc",
			   "UH:: Pipeline classname found was "+pipelinePluginClassname+".");
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

	/** 
	 * Called by wrapping thread forever until terminated.
	 *
	 * Once the end state is set, if FAILED we just quit,
	 * otherwise, check the pending list and clear any images
	 * left inside it, transfer them, pipeline and add details
	 * to the obs.imagedata and obs.clusterdata.
	 *
	 * Other things which can cause this processing to end:-
	 *
	 * elapsedTime > max allowed (v.generous margin).
	 * #num exposures > expected number
	 */
	protected void mainTask()
	{    
		RTMLImageData imageData = null;
		
		if (state == OBS_FAILED)
		{
			// bugger - weve already modified the base doc !
			// do we need to pull the new imagedata out? as we're not
			// going to send the updateDoc now. Maybe we just dont bother
			// to save it.. I think so..
			logger.log(INFO, 1, CLASS, tea.getId(),"mainTask","UH::State is obs failed:terminating for "+
				   observationId+".");
			terminate();
			return;
		}
		else
		{	    
			// Either OBS_DONE or NOT_DONE_YET either way we need to clear out
			// the pending list.

			// If image filenames in queue, grab and process.
			while (! pending.isEmpty())
			{
		
				String imageFileName = (String)pending.remove(0);

				logger.log(INFO, 1, CLASS, tea.getId(),"mainTask","UH::Processing image filename "+
					   imageFileName+" for "+observationId+".");
		
				// Generate the correct destination file name.			
				String destDirName = null;
				try
				{
				    destDirName = pipelinePlugin.getInputDirectory();
				}
				catch(Exception e)
				    {
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::Getting input directory failed for pipeline plugin "
						   +pipelinePlugin+":"+e);
					logger.dumpStack(1,e);
					return;
				}
				File   destDir     = new File(destDirName);
				File   fullFile    = new File(imageFileName);
				String rawFileName = fullFile.getName();
				File   destFile    = new File(destDir, rawFileName);
				String destFileName= destFile.getPath();	
		
				try
				{
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::Transferring image filename "+
						   imageFileName+" to "+destFileName+" for "+observationId+".");
					transfer(imageFileName, destFileName);		    
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::Transfered image filename "+
						   imageFileName+" to "+destFileName+" for "+observationId+".");
				}
				catch (Exception e)
				{
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::Transferring image filename "+
						   imageFileName+" to "+destFileName+" failed for "+
						   observationId+":"+e);
					logger.dumpStack(1,e);
					return;
				}
		
				// pipeline process
				try
				    {
					imageData = pipelinePlugin.processFile(destFile);
				    }
				catch(Exception e)
				    {
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::pipelinePlugin.processFile "+
						   destFile+" failed:"+e);
					logger.dumpStack(1,e);
					return;
				    }
				
				// Add processed data to basedoc
				RTMLObservation obs = baseDoc.getObservation(0);
				if (obs != null)
				    {
					try
					    {
						obs.addImageData(imageData);
					    } 
					catch (Exception e)
					    {
						logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
							   "UH::addImageData "+
						       imageData+" failed:"+e);
						logger.dumpStack(1,e);
						return;
					    }
				    }
				// save the base document
				try
				    {
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::Saving document for "+observationId+".");
					tea.saveDocument(baseDoc);
				    } 
				catch (Exception e)
				    {
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::saveDocument for "+observationId+" failed:"+e);
					logger.dumpStack(1,e);
					return;
				    }


				// Add processed data to updatedoc. 
				// Note we re-use the same UpdateDoc that got cloned from the BaseDoc 
				// at the start - we need to clear out any added image data each time we re-use it.
				obs = updateDoc.getObservation(0);
				if (obs != null)
				    {
					try
					    {
						// clear any left-over image data. 
						obs.clearImageDataList();
						// add just-received reduced image data.
						obs.addImageData(imageData);
					    } 
					catch (Exception e)
					    {
						logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
							   "UH::addImageData for "+observationId+" "+
							   imageData+" failed:"+e);
						logger.dumpStack(1,e);
						return;
					    }
				    }
				
				// Now try to send the update to the UA.
				// this is a mess - need global comms method like
				// tea.sendDoc(doc); This may need to go in athread
				// as it may block for a while if the connection is blocked
				try
				    {
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::Sending update document for "+observationId+".");
					AgentRequestHandler arq = new AgentRequestHandler(tea);
					arq.sendDocUpdate(updateDoc, "update");
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::Sent update document for "+observationId+".");
				    } 
				catch (Exception e)
				    {
					logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
						   "UH::sendDocUpdate failed for "+observationId+":"+e);
					logger.dumpStack(1,e);
					return;
				    }

				// add to list of processed filenames
				processed.add(imageFileName);

			} // while pending list not empty

		} // test (state)


		// Finally quit
		if (state == OBS_DONE)
		    {
			logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
				   "UH::state is now OBS_DONE:terminating for "+observationId+".");
			terminate();
			return;
		    }
		
		elapsedTime = System.currentTimeMillis() - startTime;

		// Quit if weve overrun horribly
		if (elapsedTime > expectedTime + TIME_MARGIN)
		{
			logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
				   "UH::Ran out of time to complete ("+elapsedTime+" > "+(expectedTime+TIME_MARGIN)+
				   "):terminating for "+observationId+".");
			terminate();
			return;
		}

		// Back round once more at least.
		try {Thread.sleep(SLEEP_TIME);} catch (InterruptedException ix) {}

	}

	/** Called at end-of-run.*/
	protected void shutdown() {

	    int multrunExposureCount = 0;

	    // if no series constraint present, default to 1 set of multruns (flexibly scheduled)
	    int seriesConstraintCount = 1;
	    int imageDataCount = 0;
	    
	    logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
		       "UH::Shutdown for this handler for "+observationId+".");
	    
	    // if we have completed the number of observations requested by the UA,
	    // send a complete.
	    // This should only fire on the last UpdateHandler created for a particular MonitorGroup
	    // if <SeriesConstraint><Count> accurately reflects (end date - start_date) / period.
	    // If a MonitorGroup, we expect <SeriesConstraint><Count>* <Exposure><Count> frames, if a MonitorGroup.
	    // If a FlexibleGroup, we expect <Exposure><Count> frames. 
	    RTMLObservation observation = baseDoc.getObservation(0);
	    
	    if(observation != null) {
		
		RTMLSchedule schedule = observation.getSchedule();
		
		if(schedule != null) {
		
		    multrunExposureCount = schedule.getExposureCount();
		    RTMLSeriesConstraint seriesConstraint = schedule.getSeriesConstraint();
	
		    if(seriesConstraint != null) {
			
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
		}

		imageDataCount = observation.getImageDataCount();

	    }

	    logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
		       "UH::Testing whether image data count "+imageDataCount+
		       " is greater than expected number of images "+
		       (multrunExposureCount*seriesConstraintCount)+" for "+observationId+".");

	    if(imageDataCount >= (multrunExposureCount*seriesConstraintCount)) {
		
		// we have done at least as many frames as the UA expects
		try {
		    logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
			       "UH::Sending observation complete document for "+observationId+".");
		    // send "observation" type document to complete
		    AgentRequestHandler arq = new AgentRequestHandler(tea);
		    arq.sendDocUpdate(baseDoc, "observation");

		    logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
			       "UH::Saving observation complete document for "+observationId+".");

		    // save document - pass the filename aswell
		    tea.saveDocument(baseDoc); //, file)

		    logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
			       "UH::Deleting observation complete document for "+observationId+
			       " (to expired directory).");
		    // move document to expired dir
		    // delete document from memory
		    tea.deleteDocument(baseDoc);
		} 
		catch (Exception e) {
		    logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
			       "UH::sendDocUpdate failed:"+e);
		    logger.dumpStack(1,e);
		    return;
		}
	    }

	    // Always pull this UH off the tea.agentMap as its defunct now
	    // #####slightly worrying - the agmap only matches against an obs path
	    // this will be the same if another instantiation of the group
	    // appears before weve done with this one ....aaargh!
	    logger.log(INFO, 1, CLASS, tea.getId(),"mainTask",
		       "UH::Deleting update handler for "+observationId+".");
	    tea.deleteUpdateHandler(observationId);

	} // [shutdown]

	/**
	 * Tries to pull an image file off the OCC and stick it in the appropriate directory.
	 * @param imageFileName The remote filename path.
	 * @param destFileName The local filename path to store the copied file into.
	 * @exception Exception Thrown if the transfer failed.
	 * @see #client
	 * @see #logger
	 */
	private void transfer(String imageFileName, String destFileName) throws Exception
	{
	
		SSLFileTransfer.Client client = tea.getImageTransferClient();

		if (client == null) 
			throw new Exception("The transfer client is not available");

		logger.log(INFO, 1, CLASS, tea.getId(),"transfer",
			   "UH::Requesting image file: "+imageFileName+" -> "+destFileName);
		
		client.request(imageFileName, destFileName);

	}
}
//
// $Log: not supported by cvs2svn $
// Revision 1.7  2005/05/25 11:15:44  cjm
// Now create logger before calling getPipelinePluginFromDoc in connstrcutor,
// as getPipelinePluginFromDoc uses logger.
//
// Revision 1.6  2005/05/23 16:10:00  cjm
// Added initial pipeline plugin implmentation.
//
// Revision 1.5  2005/05/19 12:55:56  cjm
// Added FITS header handling methods.
//
// Revision 1.4  2005/05/12 16:27:15  cjm
// Changed when clearImageDataList was called, so update document contain 1 frame only.
// Added lots of logging.
//
// Revision 1.3  2005/05/11 19:21:41  cjm
// Rewritten to support one update document per frame.
// Also can produce multiple update documents per instance, if new images for one group
// appear quicker than update documents can be sent out (due to data transfer etc)...
//
//
