// TOCSessionManager.java
// $Header: /space/home/eng/cjm/cvs/tea/java/org/estar/tea/TOCSessionManager.java,v 1.17 2008-08-20 11:04:17 cjm Exp $
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
 * @version $Revision: 1.17 $
 */
public class TOCSessionManager implements Runnable, Logging
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: TOCSessionManager.java,v 1.17 2008-08-20 11:04:17 cjm Exp $";
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
	 * Setup the pipeline processing plugin, based upon Tag/User/Proposal information and instrument type.
	 * <ul>
	 * <li>Uses tagUserProposalInfo to construct the keyword :
	 *     <pre>
	 *     "pipeline.plugin.classname."+tagId+"/"+userId+"."+proposalId+"."+instrumentId
	 *     </pre>
	 * <li>Looks in the tea properties to see if a value exists for this keyword (which will be the plugin
	 *     classname).
	 * <li>If no value exists, looks for the plugin classname in the tea properties under the keyword
	 *     <pre>pipeline.plugin.classname.default</pre>.
	 * <li>If no valid pipeline plugin classname can be found an error is thrown.
	 * <li>Otherwise an instance of the specified class is constructed.
	 * <li>The tea instance is set, the plugin's id is set, 
	 *     and the pipeline plugin <pre>initialsie</pre> method called.
	 * </ul>
	 * @param instrumentId The instrument to create the pipeline for.
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
	public void setPipeline(String instrumentId) throws NullPointerException, ClassNotFoundException, 
					 InstantiationException, IllegalAccessException, Exception
	{
		String id = null;
		String key = null;
		String pipelinePluginClassname = null;
		Class pipelinePluginClass = null;

		// get pipeline plugin class name
		id = new String(tagUserProposalInfo.getTagID()+"/"+
				tagUserProposalInfo.getUserID()+"."+tagUserProposalInfo.getProposalID());
		key = new String("pipeline.plugin.classname."+id+"."+instrumentId);
		logger.log(INFO, 1, CLASS,
			   "TOCSessionManager::setPipeline: Trying to get pipeline classname using key "+key+".");
		pipelinePluginClassname = tea.getPropertyString(key);
		if(pipelinePluginClassname == null)
		{
			id = new String("default");
			key = new String("pipeline.plugin.classname."+id+"."+instrumentId);
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::setPipeline: Project specific pipeline does not exist, "+
				   "trying default key:"+key);
			pipelinePluginClassname = tea.getPropertyString(key);
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
		pipelinePlugin.setInstrumentId(instrumentId);
		pipelinePlugin.initialise();
	}

	/**
	 * Ping the RCS TOCA interface to ensure the port is open and working.
	 * This sends the following command to the RCS TOCA port "STATUS STATE system.state".
	 * This should return something like "OK system.state=462".
	 * A TOCSessionManager without TUPI information set can be used for this call.
	 * @exception Exception Thrown if the ping fails.
	 * @see #session
	 * @see org.estar.toop.TOCSession#status
	 */
	public void ping() throws Exception
	{
		String statusReturnString = null;

		logger.log(INFO, 1, CLASS,"TOCSessionManager:ping:Start.");
		statusReturnString = session.status("STATE","system.state");
		logger.log(INFO, 1, CLASS,"TOCSessionManager:ping:STATE:system.state returned :"+
			   statusReturnString+".");
		logger.log(INFO, 1, CLASS,"TOCSessionManager:ping:Finished.");
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						      this.getClass().getName()+
						 ":scoreDocument:Failed to set Tag/User/Proposal from document:"+e);
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_OTHER,
						      this.getClass().getName()+
				  ":scoreDocument:Document seems to have been sent to wrong session manager: "+
				  tupi.getUniqueId()+" does not equal "+tagUserProposalInfo.getUniqueId()+".");
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						      this.getClass().getName()+
						      ":scoreDocument:Document has wrong number of observations: "+
						      d.getObservationListCount()+".");
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						      this.getClass().getName()+":scoreDocument:Schedule was null.");
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						      this.getClass().getName()+
						      ":scoreDocument:TOOP Schedule has a SeriesConstraint.");
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						      this.getClass().getName()+
						      ":scoreDocument:TOOP Schedule has a SeeingConstraint.");
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						      this.getClass().getName()+
						      ":scoreDocument:Target was null.");
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_OTHER,
						      this.getClass().getName()+":scoreDocument:TOCS failure:"+e);
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_OTHER,
						      this.getClass().getName()+
						      ":scoreDocument:Service "+sessionData.getServiceId()+
						      " cannot take control for "+seconds+" seconds.");
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_OTHER,
						      this.getClass().getName()+
						      ":scoreDocument:Target RA "+target.getRA()+
						      " Dec "+target.getDec()+" is SET.");
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
		d.setScoreReply();
		d.addHistoryEntry("TEA:"+tea.getId(),null,"TOCSessionManager:Score returned 1.0.");
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						      this.getClass().getName()+
						      ":addDocument:Failed to set Tag/User/Proposal from document:"+e);
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
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						      this.getClass().getName()+
					 ":addDocument:Document seems to have been sent to wrong session manager: "+
					 tupi.getUniqueId()+" does not equal "+tagUserProposalInfo.getUniqueId()+".");
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
		// document should only have 1 observation
		if(d.getObservationListCount() != 1)
		{
			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager:addDocument:Document has wrong number of observations: "+
					 d.getObservationListCount()+".");
			try
			{
				d.setReject();
				d.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						      this.getClass().getName()+
						      ":addDocument:Document has wrong number of observations: "+
						      d.getObservationListCount()+".");
				d.setErrorString(this.getClass().getName()+
					 ":addDocument:Document has wrong number of observations: "+
					 d.getObservationListCount()+".");
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
		d.setRequestReply();
		d.addHistoryEntry("TEA:"+tea.getId(),null,"TOCSessionManager::addDocument:Document added.");
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
		       	logger.log(INFO, 1, CLASS,
				   "TOCSessionManager::run: Waiting for lock on documentList.");
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
					logger.log(INFO, 1, CLASS,
					      "TOCSessionManager::run: Waited for document: There are "+
						   documentList.size()+" documents in the list.");
					if(documentList.size() == 0)// no new document added in timeout
					{
						//if(inSession)// we are still in control of the telescope
						//{
						done = true;
						logger.log(INFO, 1, CLASS,
						      "TOCSessionManager::run: Session timeout for Tag/User/Proposal "+
							   tagUserProposalInfo.getUniqueId()+".");
						//}
						document = null;
					}
					else
						document = (RTMLDocument)(documentList.get(0));
				}
		       		else
			       		document = (RTMLDocument)(documentList.get(0));
			}// end synchronized (documentList)
		       	logger.log(INFO, 1, CLASS,"TOCSessionManager::run: Released documentList lock.");
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
					// acquire if neccessary
					if(acquireNeeded(document))
						acquire(document);
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
					logger.log(INFO, 1, CLASS,
						   "TOCSessionManager::run: Started new post-process thread.");

				}
				catch(Exception e)
				{
					logger.log(INFO, 1, CLASS,this.getClass().getName()+":run:An error occured:"+e);
					logger.dumpStack(1,e);
					// also send error document?
					try
					{
						document.setFail();
						document.addHistoryError("TEA:"+tea.getId(),null,
									 ":run:An error occured:"+e,
									 "Processing document failed.");
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
				// think about session.stop(); here to stop axes tracking into a limit
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
	 * Method to determine whether the acquire command should be called to handle this document.
	 * Acquisition is only needed for spectrographic observations at the moment.
	 * @param document The document to extract target information from.
	 * @exception IllegalArgumentException Thrown if the document contains > 1 observation,
	 *            or no device exists in the observation or document.
	 * @see #getDeviceFromDocument
	 * @see DeviceInstrumentUtilites#getInstrumentType
	 */
	private boolean acquireNeeded(RTMLDocument document) throws IllegalArgumentException
	{
		RTMLDevice device = null;
		int instrumentType;
		boolean acquireNeeded;

		device = getDeviceFromDocument(document);
		instrumentType = DeviceInstrumentUtilites.getInstrumentType(device);
		acquireNeeded = (instrumentType == DeviceInstrumentUtilites.INSTRUMENT_TYPE_SPECTROGRAPH);
		return acquireNeeded;
	}

	/**
	 * Method to acquire the currently active TOCA instrument to the specified target.
	 * The target is extracted from the document. The acquire Mode is synthesized from the instrument Id
	 * and exposure length extracted from the document.
	 * @param document The document to extract target information from.
	 * @exception IllegalArgumentException Thrown if there are the wrong number of observations in the document.
	 * @exception NullPointerException Thrown if the target was not in the document.
	 * @exception TOCException Thrown if the TOCA acquire command fails.
	 * @see #session
	 * @see #tea
	 * @see #getTargetFromDocument
	 * @see #getDeviceFromDocument
	 * @see DeviceInstrumentUtilites#getInstrumentId
	 */
	private void acquire(RTMLDocument document) throws IllegalArgumentException, NullPointerException, TOCException
	{
		RTMLDevice device = null;
		RTMLTarget target = null;
		RTMLObservation observation = null;
		RTMLSchedule schedule = null;
		String acquireMode = null;
		String instrumentId = null;
		int exposureLength,exposureCount;

		// extract target from document
		target = getTargetFromDocument(document);
		// extract device
		device = getDeviceFromDocument(document);
		instrumentId = DeviceInstrumentUtilites.getInstrumentId(tea,device);
		// get exposure length
		// get observation
		if(document.getObservationListCount() != 1)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":acquire:Illegal number of observations "+document.getObservationListCount()+
							   " found in document.");
		}
		observation = document.getObservation(0);
		// get schedule
		schedule = observation.getSchedule();
		if(schedule == null)
		{
			throw new NullPointerException(this.getClass().getName()+
			    ":acquire:No schedule found in observation.");
		}
		if(schedule.getSeriesConstraint() != null)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":acquire:TOOP does not support Schedule with SeriesConstraint.");
		}
		if(schedule.getSeeingConstraint() != null)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":acquire:TOOP does not support Schedule with SeeingConstraint.");
		}
		exposureCount = schedule.getExposureCount();
		exposureLength = (int)(schedule.getExposureLengthMilliseconds());// throws IllegalArgumentException
		// based on instrumentId/exposureLength, set acquireMode
		if(instrumentId.equals("meaburn"))
		{
			if(exposureLength <= 30000)
				acquireMode = TOCSession.ACQUIRE_MODE_BRIGHTEST;
			else
				acquireMode = TOCSession.ACQUIRE_MODE_WCS;
		}
		else if(instrumentId.equals("fixedspec"))
		{
			if(exposureLength <= 30000)
				acquireMode = TOCSession.ACQUIRE_MODE_BRIGHTEST;
			else
				acquireMode = TOCSession.ACQUIRE_MODE_WCS;
		}
		// put other spectrographs here
		else
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":acquire:Unsupported spectrograph "+instrumentId+" detected.");
		}
		session.acquire(target.getRA(),target.getDec(),acquireMode);
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
	 * @see #getTargetFromDocument
	 */
	private void slew(RTMLDocument document) throws NullPointerException, IllegalArgumentException, 
							TOCException, NumberFormatException
	{
		RTMLTarget target = null;

		// extract target from document
		target = getTargetFromDocument(document);
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
	 * @exception TOCException Thrown if the TOCA instr command fails in some way.
	 * @exception Exception Thrown if the sendInstr method fails.
	 * @see #tea
	 * @see #session
	 * @see #getDeviceFromDocument
	 * @see DeviceInstrumentUtilites#sendInstr
	 */
	private void instr(RTMLDocument document) throws NullPointerException, IllegalArgumentException, 
							 TOCException, Exception
	{
		RTMLDevice device = null;

		device = getDeviceFromDocument(document);
		// Parse RTMLDevice and send appropriate instr using TOCSession session.
		DeviceInstrumentUtilites.sendInstr(tea,session,device);
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

	/**
	 * Internal method to extract a valid device from the specified document. This currently only allows
	 * one observation per document, and extracts the device from that observation, or from
	 * the documents default if a observation specific device does not exist.
	 * @param document The document to extract the device information from.
	 * @return The RTMLDevice in this document.
	 * @exception IllegalArgumentException Thrown if the document contains > 1 observation,
	 *            or no device exists in the observation or document.
	 */
	private RTMLDevice getDeviceFromDocument(RTMLDocument document) throws IllegalArgumentException
	{
		RTMLObservation observation = null;
		RTMLDevice device = null;

		// get observation
		if(document.getObservationListCount() != 1)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":getDeviceFromDocument:Illegal number of observations "+
							   document.getObservationListCount()+
							   " found in document.");
		}
		observation = document.getObservation(0);
		// get device
		device = observation.getDevice();
		if(device == null)
		{
			// get default document device if an observation specific one does not exist.
			device = document.getDevice();
			if(device == null)
			{
				throw new NullPointerException(this.getClass().getName()+
						":getDeviceFromDocument:No device found in observation or document.");
			}
		}
		return device;
	}


	/**
	 * Internal method to extract a valid target from the specified document. This currently only allows
	 * one observation per document, and extracts the target from that observation.
	 * @param document The document to extract the target information from.
	 * @return The RTMLTarget in this document.
	 * @exception IllegalArgumentException Thrown if the document contains > 1 observation.
	 * @exception NullPointerException Thrown if the observation contains no target.
	 */
	private RTMLTarget getTargetFromDocument(RTMLDocument document) throws IllegalArgumentException
	{
		RTMLObservation observation = null;
		RTMLTarget target = null;

		// get observation
		if(document.getObservationListCount() != 1)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
			    ":getTargetFromDocument:Illegal number of observations "+
							   document.getObservationListCount()+
							   " found in document.");
		}
		observation = document.getObservation(0);
		// get target
		target = observation.getTarget();
		if(target == null)
		{
			throw new NullPointerException(this.getClass().getName()+
			    ":getTargetFromDocument:No target found in observation.");
		}
		return target;
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
	 * @see DeviceInstrumentUtilites#getInstrumentTypeName
	 */
	public static TOCSessionManager getSessionManagerInstance(TelescopeEmbeddedAgent tea,RTMLDocument document)
		throws IllegalArgumentException,IndexOutOfBoundsException, ClassNotFoundException,
		       IllegalAccessException, Exception
	{
		RTMLObservation observation = null;
		RTMLDevice device = null;
		TOCSessionManager sessionManager = null;
		TagUserProposalInfo tupi = null;
		Thread t = null;
		String instrumentId = null;

		// check is there already a TOCSessionManager for this TagUserProposal?
		tupi = new TOCSessionManager.TagUserProposalInfo();
		tupi.setTagUserProposal(document);
		// get instrument type name
		// diddly This does allow a siruation where a second document 
		// uses a different instrument to the first at the present time.
		// Try to get device from observation
		observation = document.getObservation(0);
		if(observation != null)
			device = observation.getDevice();
		// If no device in observation, get the default from the document
		if(device == null)
			device = document.getDevice();
		instrumentId = DeviceInstrumentUtilites.getInstrumentId(tea,device);
		sessionManager = getSessionManagerInstance(tupi,instrumentId);
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
		sessionManager.setPipeline(instrumentId);
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
	 * @param instrumentId The instrument ID of this document, to ensure the returned
	 *      session manager is for a compatible instrument (not used at the moment).
	 * @return An instance of TOCSessionManager, if one already exists for the specified Tag/User/Proposal,
	 *         otherwise return null.
	 * @see #sessionManagerMap
	 * @see TagUserProposalInfo#getUniqueId
	 */
	public static TOCSessionManager getSessionManagerInstance(TagUserProposalInfo tupi,String instrumentId)
	{
		TOCSessionManager sessionManager = null;

		if(sessionManagerMap.containsKey(tupi.getUniqueId()))
		{
			sessionManager = (TOCSessionManager)(sessionManagerMap.get(tupi.getUniqueId()));
			// diddly should check sessionManager.getPipelinePlugin().getInstrumentId() with
			// instrumentTypeName here, but the relevant API does not exist at the moment.
			// Throw an exception if both not equal - should not return a null.
			return sessionManager;
		}
		else
			return null;
	}
		       
	/**
	 * Static method to get a TOCSessionManager instance without specifying a TAG/User/Project.
	 * This session manager can only be used to call TOCA commands that don't need an active session to run,
	 * e.g. STATUS/INFO/WHEN etc. The returned session manager is NOT put into the sessionManagerMap,
	 * as there is no way to specify the TUPI key.
	 * A thread is NOT started to run the session, as no active session is needed for STATUS/INFO/WHEN.
	 * @param tea The telescope embedded agent reference.
	 */
	public static TOCSessionManager getSessionManagerInstance(TelescopeEmbeddedAgent tea) throws Exception
	{
		TOCSessionManager sessionManager = null;

		// setup new session manager
		sessionManager = new TOCSessionManager();
		sessionManager.setTea(tea);
		sessionManager.setProperties(tea);
		return sessionManager;
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

			logger.log(INFO, 1, CLASS,
				   "TOCSessionManager:PostProcessThread:run: Started new post-process thread with "+
				   remoteFilenameList.size()+" filenames to process.");
			for(int i = 0; i < remoteFilenameList.size(); i++)
			{
				logger.log(INFO, 1, CLASS,
					   "TOCSessionManager:PostProcessThread:run: Processing remote filename "+
				   remoteFilename+".");
				try
				{
					remoteFilename = (String)(remoteFilenameList.get(i));
					logger.log(INFO, 1, CLASS,
						   "TOCSessionManager:PostProcessThread:run:Processing remote filename "+
						   remoteFilename+".");
					// data transfer
					localFilename = dataTransfer(remoteFilename);
					logger.log(INFO, 1, CLASS,
						   "TOCSessionManager:PostProcessThread:run:Data tranferred from "+
						   remoteFilename+" to "+localFilename+".");
					// pipeline process
					imageData = pipelineProcess(document,localFilename);
					logger.log(INFO, 1, CLASS,
						   "TOCSessionManager:PostProcessThread:run:Pipeline processed "+
						   localFilename+".");
					// update doc
					sendUpdateDocument(document,imageData);
					logger.log(INFO, 1, CLASS,
						   "TOCSessionManager:PostProcessThread:run:Sent update document.");
				}
				catch(Exception e)
				{
					try
					{
						document.setFail();
						document.addHistoryError("TEA:"+tea.getId(),null,
									 ":run:An error occured:"+e,
									 "Processing document failed.");
						document.setErrorString(this.getClass().getName()+
								 ":run:An error occured:"+e);
						logger.log(INFO, 1, CLASS,this.getClass().getName()+
							   ":run:An error occured:"+e);
						logger.dumpStack(1,e);
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
				logger.log(INFO, 1, CLASS,
					   "TOCSessionManager:PostProcessThread:run:Sending observation document.");
				// observation doc
				sendObservationDocument(document);
				logger.log(INFO, 1, CLASS,
					   "TOCSessionManager:PostProcessThread:run:Sent observation document.");
				// diddly serialize in expired?
			}
			catch(Exception e)
			{
				try
				{
					document.setFail();
					document.addHistoryError("TEA:"+tea.getId(),null,this.getClass().getName()+
								 ":run:An error occured:"+e,
								 "PostProcessThread:run:failed");
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
			updateDocument.setUpdate();
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
			// it's an observation/complete document
			document.setComplete();
			// send observation document to IA.
			tea.sendDocumentToIA(document);
		}
	}
}
/*
** $Log: not supported by cvs2svn $
** Revision 1.16  2008/08/12 14:06:19  cjm
** Added test in addDocument, such that documents with more than one observation are rejected.
**
** Revision 1.15  2008/05/27 13:57:41  cjm
** Changes relating to RTML parser upgrade.
** getUId used for unique Id retrieval.
** isTOOP used for determining target of oppurtunity.
** RTML setType calls replaced by equivalent RTMLDocument methods for version independant values.
** RTML document history calls added.
**
** Revision 1.14  2008/03/31 14:18:34  cjm
** Pipeline Plugin's are now organised by name/Id rather than type of instrument.
**
** Revision 1.13  2008/03/28 17:14:25  cjm
** Now handles acquisition for spectrographs.
** Added acquireNeeded and acquire methods.
** Rewrote slew to use getDeviceFromDocument.
** Added getDeviceFromDocument, getTargetFromDocument helper methods.
**
** Revision 1.12  2007/05/01 10:05:52  cjm
** Fixed pipeline plugin Id handling, so id does not include instrument type, but
** config lookups do.
** RTMLDevice now correctly picked up from observation, and default to document Device if
** Observation Device foes not exist.
**
** Revision 1.11  2007/04/30 17:15:12  cjm
** Changed setPipeline to pipeline plugin is created per TUPI/instrument type.
** This doesn't really work in this case at the moment, as the session manager is created per TUPI, but
** the pipeline plugin uses the instrument type from the first document - if a subsequent document has a
** different instrument type it will call the original instrument's pipeline, which is incorrect
** (but difficult to fix). Perhaps need an instrument type->pipeline Map in each instance of the session manager
** checked against per-document?
**
** Revision 1.10  2007/04/26 18:03:21  cjm
** Replaced most INSTR code with call to DeviceInstrumentUtilites.sendInstr.
**
** Revision 1.9  2007/04/25 10:57:08  cjm
** Fixed binning/default binning for IRCAM(SupIRCam), this must default to 1 or
** be specified as 1.
**
** Revision 1.8  2007/04/25 10:39:35  cjm
** RTML TOOP documents with IRCAM and RINGO instruments can now be performed.
** Changes to instr to detect via <Device type="camera|polarimeter"> whether the instrument is
** a camera or polarimeter, and via <Device region="optical|infrared"> whether the camera is
** RATCam and SupIRCam. The correct string is then passed to the TOCA INSTR command.
**
** Revision 1.7  2007/04/03 14:58:55  cjm
** Changed instr implementation so if there is not Detector in Device
** the default binning is 2 (rather than an error).
**
** Revision 1.6  2006/02/08 17:24:43  cjm
** Added logging for post process thread run failure.
**
** Revision 1.5  2005/08/16 13:27:44  cjm
** Added PostProcessThread logging.
**
** Revision 1.4  2005/08/08 14:43:37  cjm
** Fixed problem with session which no documents are added during 2 minutes,
** but we are not in an open session. Now quits session manager after timout period.
**
** Revision 1.3  2005/07/27 16:57:17  cjm
** Fixed bug where having a document in documentList when starting run caused an infinite loop.
**
** Revision 1.2  2005/06/22 16:06:13  cjm
** Added ability to set pipeline plugin id.
**
** Revision 1.1  2005/06/17 17:04:05  cjm
** Initial revision
**
*/
