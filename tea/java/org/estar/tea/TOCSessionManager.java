// TOCSessionManager.java
// $Header: /space/home/eng/cjm/cvs/tea/java/org/estar/tea/TOCSessionManager.java,v 1.2 2005-06-22 16:06:13 cjm Exp $
package org.estar.tea;

import java.io.*;
import java.util.*;

import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;

import org.estar.rtml.*;
import org.estar.toop.*;

/** 
 * Class to manage TOCSession interaction for RTML documents for a specified Tag/User/Project.
 * @author Steve Fraser, Chris Mottram
 * @version $Revision: 1.2 $
 */
public class TOCSessionManager implements Runnable, Logging
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: TOCSessionManager.java,v 1.2 2005-06-22 16:06:13 cjm Exp $";
	/**
	 * Classname for logging.
	 */
	public static final String CLASS = "TOCSessionManager";
	/**
	 * Class map of tag/user/proposal -> session managers.
	 */
	private static Map sessionManagerMap = new HashMap();
	/**
	 * Class logger.
	 */
	private Logger logger = null;
	/**
	 * Reference to the session.
	 */
	private TOCSession session = null;
	/**
	 * Reference to the session data.
	 */
	private TOCSessionData sessionData = null;
	/**
	 * Reference to the tea.
	 */
	private TelescopeEmbeddedAgent tea = null;
	/**
	 * Reference to the properties.
	 */
	private NGATProperties properties = null;
	/**
	 * Information about the Tag/User and Proposal this manager was created to handle.
	 * @see TagUserProposalInfo
	 */
	private TagUserProposalInfo tagUserProposalInfo = null;
	/**
	 * Pipeline processing plugin implementation.
	 */
	private PipelineProcessingPlugin pipelinePlugin = null;
	/**
	 * List of documents to process in this session.
	 */
	private List documentList = null;

	/**
	 * Default constructor. Initialise session and sessionData.
	 * Initialise logger.
	 * @see #session
	 * @see #sessionData
	 * @see #logger
	 * @see #documentList
	 */
	public TOCSessionManager()
	{
		super();
		session = new TOCSession();
		sessionData = new TOCSessionData();
		session.setSessionData(sessionData);
		logger = LogManager.getLogger(this);
		documentList = new Vector();
	}

	/**
	 * Set TEA (Telescope Embedded Agent) instance.
	 * @param t The tea instance.
	 * @see #tea
	 */
	public void setTea(TelescopeEmbeddedAgent t)
	{
		tea = t;
	}

	/**
	 * Set properties data. Gets the loaded tea properties, 
	 * and sets the session data properties. A <b>copy</b> of the tea properties is used,
	 * not the tea properties reference itself. This is because different instances of the 
	 * TOCSessionManager will need different values for the "toop.session_id" keyword.
	 * @param t The tea instance.
	 * @see #tea
	 * @see #properties
	 * @see #sessionData
	 * @see TelescopeEmbeddedAgent#getProperties
	 */
	public void setProperties(TelescopeEmbeddedAgent t)
	{
		NGATProperties teaProperties = null;

		teaProperties = t.getProperties();
		if(teaProperties == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":setProperties:TEA properties were null.");
		}
		// create a COPY of the tea properties.
		// We need a copy, as different TOCSessionManager instances will need different values for
		// the "toop.session_id" keyword.
		properties = teaProperties.copy();
		// set session data properties
		sessionData.set(properties);
	}

	/**
	 * Set the tag, user and proposal information got from the RTML document.
	 * @param tupi The information.
	 * @see #tagUserProposalInfo
	 */
	public void setTagUserProposal(TagUserProposalInfo tupi)
	{
		tagUserProposalInfo = tupi;
	}

	/**
	 * Set the TOCA service id, using a property lookup based on the Tag/User/Proposal Ids.
	 * Looks up <pre>"toop.service_id."+tagId+"/"+userId+"."+proposalId</pre>
	 * If no value for this keyword exists, this Tag/User/Proposal does not have a valid service id associated
	 * with it and an error is returned. 
	 * If the lookup suceeds, the service ID is set in the session data.
	 * @exception IllegalArgumentException
	 * @see #tagUserProposalInfo
	 * @see #properties
	 * @see #sessionData
	 */
	public void setServiceIdFromTagUserProposal() throws IllegalArgumentException
	{
		String serviceId = null;

		serviceId = properties.getProperty("toop.service_id."+tagUserProposalInfo.getTagID()+"/"+
						   tagUserProposalInfo.getUserID()+"."+
						   tagUserProposalInfo.getProposalID());
		if(serviceId == null)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
				   ":setServiceIdFromTagUserProposal:No service found for Tag: "+
							   tagUserProposalInfo.getTagID()+
							   " User: "+tagUserProposalInfo.getUserID()+
							   " proposal: "+tagUserProposalInfo.getProposalID());
		}
		sessionData.setServiceId(serviceId);
	}

	/**
	 * Setup the pipeline processing plugin, based upon Tag/User/Proposal information.
	 * <ul>
	 * <li>Uses tagUserProposalInfo to construct the keyword :
	 *     <pre>
	 *     "pipeline.plugin.classname."+tagId+"/"+userId+"."+proposalId
	 *     </pre>
	 * <li>Looks in the tea properties to see if a value exists for this keyword (which will be the plugin
	 *     classname).
	 * <li>If no value exists, looks for the plugin classname in the tea properties under the keyword
	 *     <pre>pipeline.plugin.classname.default</pre>.
	 * <li>If no valid pipeline plugin classname can be found an error is thrown.
	 * <li>Otherwise an instance of the specified class is constructed.
	 * <li>The tea instance is set, the plugin's id is set, 
	 *     and the pipeline plugin <pre>initialsie</pre> method called.
	 * @see #logger
	 * @see #tea
	 * @see #tagUserProposalInfo
	 * @see #pipelinePlugin
	 * @exception NullPointerException Thrown if a suitable pipeline proxessing plugin classname cannot be found.
	 * @exception ClassNotFoundException Thrown if the specified pipeline plugin class cannot be found.
	 * @exception InstantiationException Thrown if the pipeline processing class cannot be instantiated.
	 * @exception IllegalAccessException Thrown if the pipeline processing class cannot be instantiated.
	 * @exception Exception Can be thrown when the pipeline plugin is initialised.
	 */
	public void setPipeline() throws NullPointerException, ClassNotFoundException, 
					 InstantiationException, IllegalAccessException, Exception
	{
		String id = null;
		String key = null;
		String pipelinePluginClassname = null;
		Class pipelinePluginClass = null;

		// get pipeline plugin class name
		id = new String(tagUserProposalInfo.getTagID()+"/"+
				 tagUserProposalInfo.getUserID()+"."+tagUserProposalInfo.getProposalID());
		key = new String("pipeline.plugin.classname."+id);
		logger.log(INFO, 1, CLASS,
			   "TOCSessionManager::setPipeline: Trying to get pipeline classname using key "+key+".");
		pipelinePluginClassname = tea.getPropertyString(key);
		if(pipelinePluginClassname == null)
		{
			id = new String("default");
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::setPipeline: Project specific pipeline does not exist, "+
				   "trying default pipeline.plugin.classname."+id);
			pipelinePluginClassname = tea.getPropertyString("pipeline.plugin.classname."+id);
		}
		logger.log(INFO, 1, CLASS,
			 "TOCSessionManager::setPipeline: Pipeline classname found was "+pipelinePluginClassname+".");
		// if we could not find a class name to instansiate, fail.
		if(pipelinePluginClassname == null)
		{
			throw new NullPointerException(this.getClass().getName()+
					       ":setPipeline:Pipeline classname found was null.");
		}
		// get pipeline plugin class from class name
		pipelinePluginClass = Class.forName(pipelinePluginClassname);
		// get pipeline plugin instance from class
		pipelinePlugin = (PipelineProcessingPlugin)(pipelinePluginClass.newInstance());
		if(pipelinePlugin == null)
		{
			throw new NullPointerException(this.getClass().getName()+
					       ":setPipeline:Pipeline plugin was null.");
		}
		pipelinePlugin.setTea(tea);
		pipelinePlugin.setId(id);
		pipelinePlugin.initialise();
	}

	/**
	 * Score the specified document.
	 * <ul>
	 * <li>We generate a TagUserProposalInfo from the document, and ensure it is the same as the
	 *     tagUserProposalInfo of this session manager.
	 * <li>We ensure the document has only 1 observation, otherwise a reject document is returned.
	 * <li>We ensure the document is for a flexibly scheduled observation (no SeriesConstraint/Monitor groups)
	 *     with no SeeingConstraint, otherwise a reject document is returned.
	 * <li>We ensure the document has a Schedule (for the tests above) and a Target 
	 *     (for the position command below) otherwise a reject document is returned.
	 * <li>We use a TOCSession to perform a <b>when</b> and <b>position</b> command. If an exception occurs,
	 *     a reject document is returned.
	 * <li>If the number of seconds returned from the <b>when</b> command is non-zero, we cannot take control now.
	 *     A reject document is returned.
	 * <li>If the position state returned from the <b>position</b> command is not RISEN, 
	 *     the target is not above the horizon at the moment. A reject document is returned.
	 * <li>We set the score to 1.0, and the completion time to now, and return the document parameter.
	 * </ul>
	 * Note we don't check filter/instrument information at the moment, database observation score requests
	 * do, maybe this should be added? See ScoreDocumentHandler for details.
	 * @param d The document.
	 * @return The return document, either set to state "reject" with an error message, or containing a score.
	 * @see #tagUserProposalInfo
	 * @see #sessionData
	 * @see #session
	 * @see ScoreDocumentHandler
	 * @see #logger
	 */
	public RTMLDocument scoreDocument(RTMLDocument d)
	{
		RTMLObservation observation = null;
		RTMLSchedule schedule = null;
		RTMLTarget target = null;
		TagUserProposalInfo tupi = null;
		int seconds;
		String stateString = null;

		logger.log(INFO, 1, CLASS,"TOCSessionManager::scoreDocument: Starting scoreDocument.");
		// get a TagUserProposalInfo for the specified document
		tupi = new TagUserProposalInfo();
		try
		{
			tupi.setTagUserProposal(d);
		}
		catch(Exception e)
		{
			logger.log(INFO, 1, CLASS,
				 "TOCSessionManager::scoreDocument:Failed to set Tag/User/Proposal from document:"+e);
			logger.dumpStack(1,e);
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:Failed to set Tag/User/Proposal from document:"+e);
			}
			catch(RTMLException re)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		// ensure it is really associated with this session manager
		if(tupi.getUniqueId().equals(tagUserProposalInfo.getUniqueId()) == false)
		{
			logger.log(INFO, 1, CLASS,
			"TOCSessionManager::scoreDocument:Document seems to have been sent to wrong session manager: "+
				   tupi.getUniqueId()+" does not equal "+tagUserProposalInfo.getUniqueId()+".");
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:Document seems to have been sent to wrong session manager: "+
					 tupi.getUniqueId()+" does not equal "+tagUserProposalInfo.getUniqueId()+".");
			}
			catch(RTMLException e)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		// document should only have 1 observation
		if(d.getObservationListCount() != 1)
		{
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::scoreDocument:Document has wrong number of observations: "+
					 d.getObservationListCount()+".");
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:Document has wrong number of observations: "+
					 d.getObservationListCount()+".");
			}
			catch(RTMLException e)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		observation = d.getObservation(0);
		// document should be flexibly scheduled, no series constraint should have been supplied
		schedule = observation.getSchedule();
		if(schedule == null)
		{
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::scoreDocument:Schedule was null.");
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:Schedule was null.");
			}
			catch(RTMLException e)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		if(schedule.getSeriesConstraint() != null)
		{
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::scoreDocument:TOOP Schedule has a SeriesConstraint.");
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:TOOP Schedule has a SeriesConstraint.");
			}
			catch(RTMLException e)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		if(schedule.getSeeingConstraint() != null)
		{
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::scoreDocument:TOOP Schedule has a SeeingConstraint.");
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:TOOP Schedule has a SeeingConstraint.");
			}
			catch(RTMLException e)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		// check startDate and endDate are sensible?
		// get target for position call
		target = observation.getTarget();
		if(target == null)
		{
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::scoreDocument:Target was null.");
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:Target was null.");
			}
			catch(RTMLException e)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		try
		{
		// when
			seconds = session.when();
		// position
			stateString = session.position(target.getRA(),target.getDec());
		}
		catch(Exception e)
		{
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::scoreDocument:TOCS failure:"+e);
			logger.dumpStack(1,e);
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:TOCS failure:"+e);
			}
			catch(RTMLException re)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		// Can we take control now?
		if(seconds != 0)
		{
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::scoreDocument:Service "+sessionData.getServiceId()+
					 " cannot take control for "+seconds+" seconds.");
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:Service "+sessionData.getServiceId()+
					 " cannot take control for "+seconds+" seconds.");
			}
			catch(RTMLException e)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		// ensure document is above horizon
		if(stateString.equals(Position.POSITION_STATE_RISEN) == false)
		{
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::scoreDocument:Target RA "+target.getRA()+
					 " Dec "+target.getDec()+" is SET.");
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":scoreDocument:Target RA "+target.getRA()+
					 " Dec "+target.getDec()+" is SET.");
			}
			catch(RTMLException e)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		// We are going to do this now - set to 1
		d.setScore(1.0);
		d.setCompletionTime(new Date());
		logger.log(INFO, 1, CLASS,
			   "TOCSessionManager::scoreDocument: Finished scoreDocument.");
		return d;
	}

	/**
	 * Add the specified document to the list of documents to be processed.
	 * @param d The document.
	 * @return The input document, with type set to confirmation, or with an error code set.
	 * @see #documentList
	 * @see #logger
	 */
	public RTMLDocument addDocument(RTMLDocument d)
	{
		TagUserProposalInfo tupi = null;

		logger.log(INFO, 1, CLASS,"TOCSessionManager::addDocument: Starting addDocument.");
		// check Tag/User/Proposal vs document 
		// get a TagUserProposalInfo for the specified document
		tupi = new TagUserProposalInfo();
		try
		{
			tupi.setTagUserProposal(d);
		}
		catch(Exception e)
		{
			logger.log(INFO, 1, CLASS,
				 "TOCSessionManager::addDocument:Failed to set Tag/User/Proposal from document:"+e);
			logger.dumpStack(1,e);
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":addDocument:Failed to set Tag/User/Proposal from document:"+e);
			}
			catch(RTMLException re)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		logger.log(INFO, 1, CLASS,
			   "TOCSessionManager::addDocument: document has Tag/User/Proposal : "+tupi.getUniqueId()+".");
		// ensure it is really associated with this session manager
		if(tupi.getUniqueId().equals(tagUserProposalInfo.getUniqueId()) == false)
		{
			try
			{
				d.setType("reject");
				d.setErrorString(this.getClass().getName()+
					 ":addDocument:Document seems to have been sent to wrong session manager: "+
					 tupi.getUniqueId()+" does not equal "+tagUserProposalInfo.getUniqueId()+".");
			}
			catch(RTMLException e)
			{
				// this can never occur - only occurs if setErrorString called with type != reject
			}
			return d;
		}
		logger.log(INFO, 1, CLASS,"TOCSessionManager::addDocument: About to add to list.");
		// add to list
		synchronized(documentList)
		{
			logger.log(INFO, 1, CLASS,"TOCSessionManager::addDocument: In synchronized.");
			documentList.add(d);
			logger.log(INFO, 1, CLASS,"TOCSessionManager::addDocument: added document.");
			documentList.notifyAll();
			logger.log(INFO, 1, CLASS,"TOCSessionManager::addDocument: notifyed all.");
		}
		// set type confirmation
		d.setType("confirmation");
		logger.log(INFO, 1, CLASS,"TOCSessionManager::addDocument: Finished.");
		return d;
	}

	/**
	 * Run method, called from a separate thread.
	 * Finally, removes itself from the class sessionManagerMap.
	 * @see #documentList
	 * @see #tagUserProposalInfo
	 * @see #sessionManagerMap
	 * @see #logger
	 */
	public void run()
	{
		PostProcessThread postProcessThread = null;
		RTMLDocument document = null;
		List filenameList = null;
		List localFilenameList = null;
		Thread t = null;
		boolean done;
		boolean inSession;

		logger.log(INFO, 1, CLASS,"TOCSessionManager::run: Started for Tag/User/Proposal "+
			   tagUserProposalInfo.getUniqueId()+".");
		done = false;
		inSession = false;
		// whilst we are waiting to start a TOCA session,
		// or a TOCA session is underway
		while(done == false)
		{
			// get the lock, wait some time to see if a document arrives for processing
			synchronized(documentList)
			{
				if(documentList.size() == 0)
				{
					logger.log(INFO, 1, CLASS,
					      "TOCSessionManager::run: Waiting for document for Tag/User/Proposal "+
						   tagUserProposalInfo.getUniqueId()+".");
					try
					{
						documentList.wait(120000);// diddly configurable
					}
					catch(InterruptedException e)
					{
						logger.log(INFO, 1, CLASS,
							   "TOCSessionManager::run: Wait inerrupted:"+e);
					}
					if(documentList.size() == 0)// no new document added
					{
						if(inSession)// we are still in control of the telescope
						{
							done = true;
							logger.log(INFO, 1, CLASS,
						      "TOCSessionManager::run: Session timeout for Tag/User/Proposal "+
								   tagUserProposalInfo.getUniqueId()+".");
						}
						document = null;
					}
					else
						document = (RTMLDocument)(documentList.get(0));
				}
			}// end synchronized (documentList)
			// if there is a document available we should process it
			if(document != null)
			{
				logger.log(INFO, 1, CLASS,
					   "TOCSessionManager::run: Processing document.");
				try
				{
					// process document
					if(inSession == false)
					{
						logger.log(INFO, 1, CLASS,
							   "TOCSessionManager::run: Starting session.");
						// start session
						session.helo();
						session.init();
						inSession = true;
					}
					// slew telescope
					slew(document);
					// configure instrument
					instr(document);
					// expose
					filenameList = expose(document);
					// pass filenameList into inner class thread to handle data
					// transfer etc, so we can go back to watching for new docs to process
					// whilst transfering/pipelineprocess/update/obs doc on this one
					postProcessThread = new PostProcessThread();
					postProcessThread.setDocument(document);
					postProcessThread.setRemoteFilenameList(filenameList);
					t = new Thread(postProcessThread);
					t.start();
				}
				catch(Exception e)
				{
					logger.log(INFO, 1, CLASS,this.getClass().getName()+":run:An error occured:"+e);
					logger.dumpStack(1,e);
					// also send error document?
					try
					{
						document.setType("fail");
						document.setErrorString(this.getClass().getName()+
								 ":run:An error occured:"+e);
						// send document back to IA
						tea.sendDocumentToIA(document);
					}
					catch(Exception ee)
					{
						logger.log(INFO, 1, CLASS,this.getClass().getName()+
							   ":run:An error occured whilst trying to "+
							   "send a fail document back to the IA:"+ee);
						logger.dumpStack(1,ee);
						// allow document to be remoeved even if informing IA fails
						// otherwise manager can get in a loop trying to do this.
					}
				}
				finally
				{
					logger.log(INFO, 1, CLASS,
						   "TOCSessionManager::run: About to try and remove document.");
					// remove document from documentList
					synchronized(documentList)
					{
						logger.log(INFO, 1, CLASS,
							   "TOCSessionManager::run: In synchronised.");
						if(documentList.remove(document) == false)
						{
							logger.log(INFO, 1, CLASS,
							   "TOCSessionManager::run: Failed to remove document.");
						}
					}
				}
			}// end if there is a document in the list
		}// end while
		logger.log(INFO, 1, CLASS,
			   "TOCSessionManager::run: Exited main while loop.");
		if(inSession)
		{
			try
			{
				logger.log(INFO, 1, CLASS,
					   "TOCSessionManager::run: Qutting TOCA session.");
				// quit session.
				session.quit();
			}
			catch(Exception e)
			{
				logger.log(INFO, 1, CLASS,this.getClass().getName()+":run:quit:An error occured:"+e);
				logger.dumpStack(1,e);
			}
		}
		logger.log(INFO, 1, CLASS,"TOCSessionManager::run: Removing session manager for Tag/User/Proposal "+
			   tagUserProposalInfo.getUniqueId()+".");
		// remove from session manager map
		sessionManagerMap.remove(tagUserProposalInfo.getUniqueId());
		logger.log(INFO, 1, CLASS,"TOCSessionManager::run: Finished for Tag/User/Proposal "+
			   tagUserProposalInfo.getUniqueId()+".");
	}

	/**
	 * Method to slew the telescope to the target specified in the specified document.
	 * Assumes the session <b>helo</b> and <b>init</b> methods have been called first.
	 * @param document The document to extract target information from.
	 * @exception IllegalArgumentException Thrown if there are the wrong number of observations in the document.
	 * @exception NullPointerException Thrown if the target was not in the document.
	 * @exception TOCException Thrown if the TOCA slew command fails in some way.
	 * @exception NumberFormatException Thrown if the TOCA slew command could not parse a number.
	 * @see #session
	 */
	private void slew(RTMLDocument document) throws NullPointerException, IllegalArgumentException, 
							TOCException, NumberFormatException
	{
		RTMLObservation observation = null;
		RTMLTarget target = null;

		// get observation
		if(document.getObservationListCount() != 1)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":slew:Illegal number of observations "+document.getObservationListCount()+
							   " found in document.");
		}
		observation = document.getObservation(0);
		// get target
		target = observation.getTarget();
		if(target == null)
		{
			throw new NullPointerException(this.getClass().getName()+
			    ":slew:No target found in observation.");
		}
		// slew
		session.slew(target.getName(),target.getRA(),target.getDec());
	}

	/**
	 * Method to configure the instrument specified in the specified document.
	 * Assumes the session <b>helo</b> and <b>init</b> methods have been called first.
	 * @param document The document to extract the instrument configuration information from.
	 * @exception IllegalArgumentException Thrown if there are the wrong number of observations in the document.
	 *           Thrown if the filter type failed to map. Thrown if the row and column binning was not equal.
	 * @exception NullPointerException Thrown if the device or detector was not in the document.
	 * @exception TOCException Thrown if the TOCA slew command fails in some way.
	 * @see #tea
	 * @see #session
	 */
	private void instr(RTMLDocument document) throws NullPointerException, IllegalArgumentException, 
							TOCException
	{
		RTMLObservation observation = null;
		RTMLDevice device = null;
		RTMLDetector detector = null;
		String rtmlFilterType = null;
		String lowerFilterType = null;
		String upperFilterType = null;
		int bin;

		// get observation
		if(document.getObservationListCount() != 1)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":instr:Illegal number of observations "+document.getObservationListCount()+
							   " found in document.");
		}
		observation = document.getObservation(0);
		// get device
		device = observation.getDevice();
		if(device == null)
		{
			throw new NullPointerException(this.getClass().getName()+
			    ":instr:No device found in observation.");
		}
		if(device.getType().equals("camera") == false)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":instr:Device "+device.getType()+" not supported for TOCA.");
		}
		// filter types
		rtmlFilterType = device.getFilterType();
		lowerFilterType = tea.getFilterMap().getProperty("toop.lower."+rtmlFilterType);
		upperFilterType = tea.getFilterMap().getProperty("toop.upper."+rtmlFilterType);
		if((lowerFilterType == null)||(upperFilterType == null))
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":instr:Failed to map filter type: "+rtmlFilterType+" mapped to lower "+
							   lowerFilterType+" and upper "+upperFilterType+".");
		}
		// get detector
		detector = device.getDetector();
		if(detector == null)
		{
			throw new NullPointerException(this.getClass().getName()+
			    ":instr:No detector found in observation.");
		}
		// binning
		if(detector.getColumnBinning() != detector.getRowBinning())
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":instr:Row/Column binning must be equal: row: "+detector.getRowBinning()+
							   " and column: "+detector.getColumnBinning()+".");
		}
		bin = detector.getColumnBinning();
		// instr - default calibrateBefore and calibrateAfter to false for RATCAM/DILLCAM.
		session.instrRatcam(lowerFilterType,upperFilterType,bin,false,false);
	}

	/**
	 * Method to do the exposure sequence specified in the specified document.
	 * Assumes the session <b>helo</b>, <b>init</b>, <b>slew</b>, <b>instr</b> methods have been called first.
	 * @param document The document to extract the exposure sequence information from.
	 * @return A list of exposure filenames are returned.
	 * @exception IllegalArgumentException Thrown if there are the wrong number of observations in the document.
	 *           Thrown if the Schedule has a SeriesConstraint and SeeingConstraint. Thrown if getting
	 *           the exposure length in milliseconds fails (exposure type is "snr" or unknown units).
	 * @exception NullPointerException Thrown if the Schedule was not in the document.
	 * @exception TOCException Thrown if the TOCA expose command fails in some way.
	 * @see #session
	 */
	private List expose(RTMLDocument document) throws NullPointerException, IllegalArgumentException, 
							TOCException
	{
		RTMLObservation observation = null;
		RTMLSchedule schedule = null;
		Expose expose = null;
		List filenameList = null;
		int exposureLength = 0;
		int exposureCount = 0;

		// get observation
		if(document.getObservationListCount() != 1)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":expose:Illegal number of observations "+document.getObservationListCount()+
							   " found in document.");
		}
		observation = document.getObservation(0);
		// get schedule
		schedule = observation.getSchedule();
		if(schedule == null)
		{
			throw new NullPointerException(this.getClass().getName()+
			    ":expose:No schedule found in observation.");
		}
		if(schedule.getSeriesConstraint() != null)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":expose:TOOP does not support Schedule with SeriesConstraint.");
		}
		if(schedule.getSeeingConstraint() != null)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":expose:TOOP does not support Schedule with SeeingConstraint.");
		}
		exposureCount = schedule.getExposureCount();
		exposureLength = (int)(schedule.getExposureLengthMilliseconds());// throws IllegalArgumentException
		// expose - default data pipeline flag to true for RATCAM/DILLCAM.
		session.expose(exposureLength,exposureCount,true);
		// extract filenames of data taken
		filenameList = new Vector();
		expose = session.getExpose();
		for(int i = 0;i < expose.getFilenameCount(); i++)
		{
			filenameList.add(expose.getFilename(i));
		}
		return filenameList;
	}

	// static methods
	/**
	 * Static method to get a TOCSessionManager instance based upon the TAG/User/Project inside
	 * the specified RTMLDocument. Checks the sessionManagerMap to see if an instance already exists,
	 * otherwise:
	 * <ul>
	 * <li>Sets the TelescopeEmbeddedAgent instance and reads TAG/User/Proposal 
	 *     information. 
	 * <li>Creates the TOCSessionManager and starts it's thread running.
	 * </ul>
	 * @param tea The telescope embedded agent reference.
	 * @param document The document to get the session manager for.
	 * @exception IllegalArgumentException Thrown if the document does not contain the right Tag/User/Proposal 
	 *            data, or if the Tag/User/Proposal does not map to a valid service ID.
	 * @exception IndexOutOfBoundsException Thrown if the Tag/User string is not formatted correctly.
	 * @exception ClassNotFoundException Thrown if the pipeline processing class cannot be found.
	 * @exception InstantiationException Thrown if the pipeline processing class cannot be instantiated
	 * @exception Exception Can be thrown when the pipeline plugin is initialised.
	 * @exception IllegalAccessException Thrown if the pipeline processing class cannot be instantiated.
	 * @see #getSessionManagerInstance(TOCSessionManager.TagUserProposalInfo)
	 */
	public static TOCSessionManager getSessionManagerInstance(TelescopeEmbeddedAgent tea,RTMLDocument document)
		throws IllegalArgumentException,IndexOutOfBoundsException, ClassNotFoundException,
		       IllegalAccessException, Exception
	{
		TOCSessionManager sessionManager = null;
		TagUserProposalInfo tupi = null;
		Thread t = null;

		// check is there already a TOCSessionManager for this TagUserProposal?
		tupi = new TOCSessionManager.TagUserProposalInfo();
		tupi.setTagUserProposal(document);
		sessionManager = getSessionManagerInstance(tupi);
		// If there is a running TOCSessionManager for this TagUserProposal, return that.
		if(sessionManager != null)
			return sessionManager;
		// setup new session manager
		sessionManager = new TOCSessionManager();
		sessionManager.setTea(tea);
		sessionManager.setProperties(tea);
		sessionManager.setTagUserProposal(tupi);
		sessionManager.setServiceIdFromTagUserProposal();
		// pipeline config
		sessionManager.setPipeline();
		// spawn session manager thread
		t = new Thread(sessionManager);
		t.start();
		// add to session manager map
		sessionManagerMap.put(tupi.getUniqueId(),sessionManager);
		// return new instance
		return sessionManager;
	}

	/**
	 * Attempt to get the session manager instance associated with the specified Tag/User/Proposal.
	 * If a suitable session manager exists in the sessionManagerMap return that, otherwise return null.
	 * @param tupi An instance of TagUserProposalInfo, containing the Tag/User/Proposal to search for.
	 * @return An instance of TOCSessionManager, if one already exists for the specified Tag/User/Proposal,
	 *         otherwise return null.
	 * @see #sessionManagerMap
	 * @see TagUserProposalInfo#getUniqueId
	 */
	public static TOCSessionManager getSessionManagerInstance(TagUserProposalInfo tupi)
	{
		if(sessionManagerMap.containsKey(tupi.getUniqueId()))
			return (TOCSessionManager)(sessionManagerMap.get(tupi.getUniqueId()));
		else
			return null;
	}
		       
	// internal classes
	/**
	 * Internal class holding data on Tag/User/Proposal extracted from an RTML document.
	 * This is a "static" class as instances of it need to be instansiated in TOCSessionManager static (class)
	 * methods.
	 */
	public static class TagUserProposalInfo
	{
		/**
		 * The TAG ID, retrieved from the RTML document's Contact / User tag.
		 */
		private String tagId = null;
		/**
		 * The User ID, retrieved from the RTML document's Contact / User tag.
		 */
		private String userId = null;
		/**
		 * The proposal ID, retrieved from the RTML document's Project tag.
		 */
		private String proposalId = null;

		/**
		 * Default constructor.
		 */
		public TagUserProposalInfo()
		{
			super();
		}

		/**
		 * Set the Tag, User and Proposal data from information contained within the document.
		 * @param d The RTML document to extract the data from.
		 * @exception IllegalArgumentException Thrown if the document does not contain the right data.
		 * @exception IndexOutOfBoundsException Thrown if the Tag/User string is not formatted correctly.
		 * @see #tagId
		 * @see #userId
		 * @see #proposalId
		 */
		public void setTagUserProposal(RTMLDocument d) throws IllegalArgumentException,
								      IndexOutOfBoundsException
		{
			RTMLContact contact = null;
			RTMLProject project = null;
			String userString = null;
			int slashIndex;

			// Tag/User contained within Contact Node's User sub-node.
			contact = d.getContact();
			if(contact == null)
			{
				throw new IllegalArgumentException(this.getClass().getName()+
								   ":setTagUserProposal:No contact was supplied.");
			}
			userString = contact.getUser();
			if (userString == null)
			{
				throw new IllegalArgumentException(this.getClass().getName()+
								   ":setTagUserProposal:The User ID was null.");
			}
			// userString is of the form: Tag/UserId
			slashIndex = userString.indexOf("/");
			if(slashIndex < 0)
			{
				throw new IllegalArgumentException(this.getClass().getName()+
				      ":setTagUserProposal:Illegal user string "+userString+" : slash not found.");
			}
			tagId = userString.substring(0,slashIndex);
			userId = userString.substring(slashIndex+1);
			// The Proposal ID.
			project = d.getProject();
			if(project == null)
			{
				throw new IllegalArgumentException(this.getClass().getName()+
								   ":setTagUserProposal:No project was supplied.");
			}
			proposalId = project.getProject();
			if (proposalId == null)
			{
				throw new IllegalArgumentException(this.getClass().getName()+
								   ":setTagUserProposal:Project was null.");
			}
		}

		/**
		 * Get the tag ID.
		 * @return A string representing the ID.
		 * @see #tagId
		 */
		protected String getTagID()
		{
			return tagId;
		}

		/**
		 * Get the user ID.
		 * @return A string representing the ID.
		 * @see #userId
		 */
		protected String getUserID()
		{
			return userId;
		}

		/**
		 * Get the proposal ID.
		 * @return A string representing the ID.
		 * @see #proposalId
		 */
		protected String getProposalID()
		{
			return proposalId;
		}

		/**
		 * Gets a unique ID string based on the contents of this object.
		 * The string actually returned is:
		 * <pre>
		 * tagId+"/"+userId+"."+proposalId
		 * </pre>
		 * @return A string unique to a Tag/User/Proposal combination.
		 * @see #tagId
		 * @see #userId
		 * @see #proposalId
		 */
		protected String getUniqueId()
		{
			return new String(tagId+"/"+userId+"."+proposalId);
		}
	}

	/**
	 * Inner class to do processing after the TOCA exposure has completed.
	 * Performs data transfer to tea machine, Runs data pipeline, sends update document, send observation document,
	 * or sends fail document on error.
	 * An instance of this class is instansiated and started for each TOCA expose command completed.
	 */
	public class PostProcessThread implements Runnable
	{
		/**
		 * The TOOP document that generated a TOCA exposure generating the remoteFilenameList data.
		 */
		private RTMLDocument document = null;
		/**
		 * List of filenames on the occ for this exposure.
		 */
		private List remoteFilenameList = null;

		/**
		 * Default constructor.
		 */
		public PostProcessThread()
		{
			super();
		}

		/**
		 * Set the document to post-process.
		 * @param d The document.
		 * @see #document
		 */
		public void setDocument(RTMLDocument d)
		{
			document = d;
		}

		/**
		 * Set the remote filename list to post-process.
		 * @param l A list, contaning a list of strings (filename) of FITS file on the remote (occ) machine.
		 * @see #remoteFilenameList
		 */
		public void setRemoteFilenameList(List l)
		{
			remoteFilenameList = l;
		}

		/**
		 * Run method.
		 */
		public void run()
		{
			RTMLImageData imageData = null;
			String remoteFilename = null;
			String localFilename = null;

			for(int i = 0; i < remoteFilenameList.size(); i++)
			{
				try
				{
					remoteFilename = (String)(remoteFilenameList.get(i));
					// data transfer
					localFilename = dataTransfer(remoteFilename);
					// pipeline process
					imageData = pipelineProcess(document,localFilename);
					// update doc
					sendUpdateDocument(document,imageData);
				}
				catch(Exception e)
				{
					try
					{
						document.setType("fail");
						document.setErrorString(this.getClass().getName()+
								 ":run:An error occured:"+e);
						// send document back to IA
						tea.sendDocumentToIA(document);
					}
					catch(Exception ee)
					{
						logger.log(INFO, 1, CLASS,this.getClass().getName()+
							   ":run:An error occured whilst trying to "+
							   "send a fail document back to the IA:"+ee);
						logger.dumpStack(1,ee);
						// carry on and try to reduce other documents
					}
				}
			}// end for on exposure documents
			try
			{
				// observation doc
				sendObservationDocument(document);
				// diddly serialize in expired?
			}
			catch(Exception e)
			{
				try
				{
					document.setType("fail");
					document.setErrorString(this.getClass().getName()+
								":run:An error occured:"+e);
					// send document back to IA
					tea.sendDocumentToIA(document);
				}
				catch(Exception ee)
				{
					logger.log(INFO, 1, CLASS,this.getClass().getName()+
						   ":run:An error occured whilst trying to "+
						   "send a fail document back to the IA:"+ee);
					logger.dumpStack(1,ee);
					// carry on and try to reduce other documents
				}
			}
		}

		/**
		 * Transfer data from the instrument/occ machines to the machine the tea is running on.
		 * @param remoteFilename A string representing the filenames on the occ of a FITS
		 *        image taken with the last expose command.
		 * @return A String representing the filename on the tea machine of the locally copied FITS
		 *        image taken with the last expose command.
		 * @exception Exception Thrown if the pipeline plugin cannot get the input directory. 
		 *            Thrown if the image transfer client is null.
		 * @see #tea
		 * @see #session
		 * @see #pipelinePlugin
		 */
		private String dataTransfer(String remoteFilename) throws Exception
		{
			SSLFileTransfer.Client client = null;
			File remoteFile = null;
			File localFile = null;
			String localDirectoryName = null;
			String remoteLeafFilename = null;
			String localFilename = null;

			// get image transfer client - this can be NULL if initialisation failed.
			client = tea.getImageTransferClient();
			if (client == null)
			{
				throw new Exception(this.getClass().getName()+
						    ":dataTransfer:The transfer client is not available");
			}
			// get local input directory for the specified pipeline
			localDirectoryName = pipelinePlugin.getInputDirectory();// can throw Exception
			// find the remote and local filenames
			remoteFile = new File(remoteFilename);
			remoteLeafFilename = remoteFile.getName();
			localFile = new File(localDirectoryName,remoteLeafFilename);
			localFilename = localFile.getPath();
			// transfer remote to local
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::dataTransfer:Requesting image file: "+
				   remoteFilename+" -> "+localFilename);
			client.request(remoteFilename, localFilename);
			return localFilename;
		}

		/**
		 * Pipeline process the local filename.
		 * Update the document with the returned image data. 
		 * @param document The document.
		 * @param localFilename The local FITS image filename on the tea machine.
		 * @return An instance of RTMLImageData containing the returned data products.
		 * @exception IllegalArgumentException Thrown if the document does not contain ONLY 1 observation.
		 * @exception Exception Thrown if the data pipeline fails.
		 * @see #pipelinePlugin
		 */
		public RTMLImageData pipelineProcess(RTMLDocument document,String localFilename) 
			throws IllegalArgumentException, Exception
		{
			RTMLObservation observation = null;
			RTMLImageData imageData = null;

			// call data pipeline
			imageData = pipelinePlugin.processFile(new File(localFilename));
			// add to document
			if(document.getObservationListCount() != 1)
			{
				throw new IllegalArgumentException(this.getClass().getName()+
								   ":pipelineProcess:Illegal observation count "+
								   document.getObservationListCount() +".");
			}
			observation = document.getObservation(0);
			observation.addImageData(imageData);
			return imageData;
		}

		/**
		 * Send an update document to the IA.
		 * @param document The document to send.
		 * @param imageData The image data generated by the last pipeline process.
		 * @exception Exception Thrown if the document deep clone fails. Thrown if sending the document fails.
		 * @see #tea
		 */
		public void sendUpdateDocument(RTMLDocument document,RTMLImageData imageData) throws Exception
		{
			RTMLDocument updateDocument = null;
			RTMLObservation observation = null;

			// create update doc - clone of document
			updateDocument = (RTMLDocument)document.deepClone();
			if(updateDocument.getObservationListCount() != 1)
			{
				throw new IllegalArgumentException(this.getClass().getName()+
								   ":sendUpdateDocument:Illegal observation count "+
								   updateDocument.getObservationListCount() +".");
			}
			// clear observation image data and add just this reduced image data.
			observation = updateDocument.getObservation(0);
			observation.clearImageDataList();
			observation.addImageData(imageData);
			// it's an update document
			updateDocument.setType("update");
			// send update document to IA.
			tea.sendDocumentToIA(updateDocument);
		}

		/**
		 * Send an observation document to the IA.
		 * @param document The document to send.
		 * @exception Exception Thrown if the document deep clone fails. Thrown if sending the document fails.
		 * @see #tea
		 */
		public void sendObservationDocument(RTMLDocument document) throws Exception
		{
			// it's an update document
			document.setType("observation");
			// send observation document to IA.
			tea.sendDocumentToIA(document);
		}
	}
}
/*
** $Log: not supported by cvs2svn $
** Revision 1.1  2005/06/17 17:04:05  cjm
** Initial revision
**
*/
