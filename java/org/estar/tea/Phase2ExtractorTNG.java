package org.estar.tea;

import java.io.File;
import java.util.Date;
import java.util.Map;

import ngat.util.logging.*;
import org.estar.rtml.*;
import org.estar.astrometry.*; //import ngat.phase2.*;
import ngat.astrometry.ReferenceFrame;
import ngat.astrometry.SkyBrightnessCalculator;
import ngat.oss.impl.mysql.util.mutators.SkyBrightnessMutator;
import ngat.oss.model.*;
import ngat.phase2.CCDConfig;
import ngat.phase2.Detector;
import ngat.phase2.FrodoSpecConfig;
import ngat.phase2.IAcquisitionConfig;
import ngat.phase2.IAutoguiderConfig;
import ngat.phase2.IObservingConstraint;
import ngat.phase2.IProgram;
import ngat.phase2.IProposal;
import ngat.phase2.IRCamConfig;
import ngat.phase2.IRotatorConfig;
import ngat.phase2.InstrumentConfig;
import ngat.phase2.LowResSpecConfig;
import ngat.phase2.OConfig;
import ngat.phase2.PolarimeterConfig;
import ngat.phase2.MOPTOPPolarimeterConfig;
import ngat.phase2.RISEConfig;
import ngat.phase2.SpratConfig;
import ngat.phase2.LiricConfig;
import ngat.phase2.LociConfig;
import ngat.phase2.THORConfig;
import ngat.phase2.Window;
import ngat.phase2.XAcquisitionConfig;
import ngat.phase2.XAirmassConstraint;
import ngat.phase2.XArc;
import ngat.phase2.XAutoguiderConfig;
import ngat.phase2.XBranchComponent;
import ngat.phase2.XDetectorConfig;
import ngat.phase2.XDualBeamSpectrographInstrumentConfig;
import ngat.phase2.XExecutiveComponent;
import ngat.phase2.XExtraSolarTarget;
import ngat.phase2.XFilterDef;
import ngat.phase2.XFilterSpec;
import ngat.phase2.XFlexibleTimingConstraint;
import ngat.phase2.XGroup;
import ngat.phase2.XImagerInstrumentConfig;
import ngat.phase2.XImagingSpectrographInstrumentConfig;
import ngat.phase2.XTipTiltImagerInstrumentConfig;
import ngat.phase2.XInstrumentConfig;
import ngat.phase2.XInstrumentConfigSelector;
import ngat.phase2.XIteratorComponent;
import ngat.phase2.XIteratorRepeatCountCondition;
import ngat.phase2.XLampDef;
import ngat.phase2.XMinimumIntervalTimingConstraint;
import ngat.phase2.XMonitorTimingConstraint;
import ngat.phase2.XMoptopInstrumentConfig;
import ngat.phase2.XLiricInstrumentConfig;
import ngat.phase2.XMultipleExposure;
import ngat.phase2.XPeriodExposure;
import ngat.phase2.XPhotometricityConstraint;
import ngat.phase2.XPolarimeterInstrumentConfig;
import ngat.phase2.XPositionOffset;
import ngat.phase2.XRotatorConfig;
import ngat.phase2.XSeeingConstraint;
import ngat.phase2.XSkyBrightnessConstraint;
import ngat.phase2.XSlew;
import ngat.phase2.XSpectrographInstrumentConfig;
import ngat.phase2.XWindow;


/**
 * The next generation  Phase2 extractor. Methods in this class create a "new" Phase2 Group object model
 * from an RTML document, and insert it into the Phase2 database via the OSS RMI handler.
 * @author eng
 */
public class Phase2ExtractorTNG implements Logging
{

	/** Default maximum unguided exposure length (ms). */
	public static final long DEFAULT_MAXIMUM_UNGUIDED_EXPOSURE = 24 * 3600 * 1000L;

	/**
	 * Minimum difference between positions to require an OFFSET to be used.
	 * (0.1 arcsec)
	 */
	public static final double MIN_OFFSET = Math.toRadians(1 / 36000.0);
	/**
	 * Sprat observations should be done at a mount angle of 11 degrees, 
	 * so the slit is aligned to zenith (i.e. vertical) and atmospheric differential refraction occurs 
	 * in the spatial and not spectral direction of Sprat.
	 * The constant needs to be in radians for the PhaseII interface.
	 */
	public static final double SPRAT_MOUNT_ANGLE_RADS = Math.toRadians(11.0);

	public static final String RATCAM_INSTRUMENT = "RATCam";

	public static String CLASS = "Phase2GroupExtract";
	/**
	 * Telescope embedded agent reference.
	 */
	TelescopeEmbeddedAgent tea;
	/**
	 * PhaseII model RMI handler.
	 */
	IPhase2Model phase2;
	/**
	 * Logger.
	 */
	private Logger logger;

	/**
	 * Constructor.
	 * Take a copy of the tea instamce, and create a "TRACE" logger.
	 * @see #tea
	 * @see #logger
	 */
	public Phase2ExtractorTNG(TelescopeEmbeddedAgent tea) 
	{
		this.tea = tea;
		logger = LogManager.getLogger("TRACE");

	}

	/**
	 * Handle a RTML request document.
	 * <ul>
	 * <li>If the document is a TOOP (target of opportunity) document:-
	 *     <ul>
	 *     <li>We get the TOCSessionManager instance.
	 *     <li>We add the document to the list of documents to be processed by this instance.
	 *     <li>We return the document returned by the session manager instance.
	 *     </ul>
	 * <li>Otherwise the document shoule be inserted into the PhaseII database.
	 * <li>We get the Phase2 model instance (tea.getPhase2Model).
	 * <li>We extract the group and send it to the OSS to be inserted into the PhaseII database (extractGroup).
	 * <li>We get a group path from the document (extractGroupPath).
	 * <li>We construct a document filename based on the group path (createNewFileName).
	 * <li>IF we are loading ARQs (Agent Request Handlers):
	 *     <ul>
	 *     <li>We create a new instance of AgentRequestHandler, and set it's parameters.
	 *     <li>We register it as a handler with the TEA instance (registerHandler).
	 *     <li>We setup and start an update handler thread (prepareUpdateHandler / start).
	 *     </ul>
	 * <li>We save the document into the new filename.
	 * <li>We set the document to a request reply (i.e. success), and add a history entry.
	 * <li>We return the document.
	 * </ul>
	 * @param document The RTML request document to process, an instance of RTMLDocument.
	 * @return An instance of RTMLDocument containing the result of processing the document.
	 * @see #extractGroup
	 * @see #extractGroupPath
	 * @see AgentRequestHandler
	 * @see AgentRequestHandler#setGid
	 * @see AgentRequestHandler#setName
	 * @see AgentRequestHandler#setARQId
	 * @see AgentRequestHandler#setDocumentFile
	 * @see AgentRequestHandler#setBaseDocument
	 * @see AgentRequestHandler#prepareUpdateHandler
	 * @see AgentRequestHandler#start
	 * @see TelescopeEmbeddedAgent#getPhase2Model
	 * @see TelescopeEmbeddedAgent#getLoadArqs
	 * @see TelescopeEmbeddedAgent#createNewFileName
	 * @see TelescopeEmbeddedAgent#saveDocument
	 * @see TelescopeEmbeddedAgent#registerHandler
	 * @see TOCSessionManager
	 * @see TOCSessionManager#getSessionManagerInstance
	 * @see TOCSessionManager#addDocument
	 * @see org.estar.rtml.RTMLDocument
	 * @see org.estar.rtml.RTMLDocument#isTOOP
	 * @see org.estar.rtml.RTMLDocument#setRequestReply
	 * @see org.estar.rtml.RTMLDocument#addHistoryEntry
	 * @see ngat.oss.model.IPhase2Model
	 * @see ngat.oss.model.IPhase2Model
	 */
	public RTMLDocument handleRequest(RTMLDocument document) throws Exception 
	{
		String cid = document.getUId();
		logger.log(INFO, 1, CLASS, cid, "handleRequest", "handleRequest for document UId: " + 
			   document.getUId());

		if (document.isTOOP()) 
		{
			// Try and get TOCSessionManager context.
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "Request is a TOOP: finding session manager.");
			TOCSessionManager sessionManager = TOCSessionManager.getSessionManagerInstance(tea, document);
			// add the document to the TOCSessionManager
			// if it succeeds addDocument sets the type to "confirmation".
			logger.log(INFO, 1, CLASS, cid, "handleRequest", 
				   "Request is a TOOP: Adding document to session manager.");
			document = sessionManager.addDocument(document);
			return document;
		}

		// NOt a TOOP so goes in ODB
		phase2 = tea.getPhase2Model();
		// extract the group info and send off to ODB.
		extractGroup(document);
		String groupPath = extractGroupPath(document);
		// Get a unique file Name off the TEA.
		File file = new File(tea.createNewFileName(groupPath));

		// if we failed then we dont get here anyway - now setup the ARQ OR NOT
		if (tea.getLoadArqs()) 
		{
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "Creating AgentRequestHandler.");
			AgentRequestHandler arq = new AgentRequestHandler(tea, document);

			arq.setGid(groupPath);
			arq.setName(document.getUId());
			arq.setARQId(tea.getId() + "/" + arq.getName());

			// Its the one we will use.
			arq.setDocumentFile(file);

			// Set the current request as our basedoc.
			arq.setBaseDocument(document);

			// Register as handler for the current obs.
			tea.registerHandler(groupPath, arq);
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "Registered running ARQ for: " + groupPath
					+ " Using file: " + file.getPath());

			// Initialize and start the ARQ as UpdateHandler. If the ARQ does
			// not successfully prepare for UpdateHandling it will not be started and we get an
			// exception.
			try 
			{
				arq.prepareUpdateHandler();
				arq.start();
			} 
			catch (Exception e) 
			{
				logger.dumpStack(1, e);
			}
		}
		// Save it to the file - we could do this ourself..
		tea.saveDocument(document, file);
		logger.log(INFO, 1, CLASS, cid, "handleRequest", "Saving base document to: " + file.getPath());
		
		// We still send a confirm, even if we cant start the ARQ correctly as
		// the obs is in the DB.
		document.setRequestReply();
		document.addHistoryEntry("TEA:" + tea.getId(), null, "Request confirmed.");
		return document;

	}
	/**
	 * Handle a RTML abort document.
	 * <ul>
	 * <li>If the document is a TOOP (target of opportunity) document:-
	 *     <ul>
	 *     <li>Reply with an error document (reject), we can't abort a TOOP session at the moment.
	 *     </ul>
	 * <li>Otherwise, the document is a PhaseII document, and needs deleting from the database.
	 * <li>We get the phase2 model from the telescope embedded agent instance (getPhase2Model).
	 * <li>We retrieve the proposal ID name from the RTML document Project data.
	 * <li>We get the Phase2 ProposalInfo from the telescope embedded agent's proposal map (getProposalMap).
	 * <li>We get the Phase2 Proposal object from the Phase2 ProposalInfo object.
	 * <li>We get the group name from the RTML document's Uid, with special non-character replacements.
	 *     Basically we replicate the code in extractGroup to get the same group name.
	 * <li>We get the Phase2 proposal ID from the Phase2 Proposal object.
	 * <li>We call the phase2 model's findIdOfGroupInProposal to get the phase2 group Id from
	 *     the phase2 proposal Id and the group anme.
	 * <li>We call the phase2 model's deleteGroup method (with the specified phase2 group Id) to
	 *     delete the group from the phase2 database.
	 * <li>If we are using agent request handlers (getLoadArqs)
	 *     <ul>
	 *     <li>We call extractGroupPath to get the group path from the RTML document.
	 *     <li>We retriebe the agent request handler instance for this document using the group path and 
	 *         getUpdateHandler.
	 *     <li>We call the agent request handler's abort method to tell it's thread to stop.
	 *     <li>We call the agent request handler's expireDocument method to move the saved document file to
	 *         the expired directory.
	 *     </ul>
	 * <li>We DO NOT save the document. This is because there is no map from RTML Document Uid to 
	 *     File as far as I am aware. However, as far as I am aware the same occurs with a document
	 *     that completes/incomplete etc if we are not running ARQs.
	 * <li>We set the document to be an Abort Reply document, and add a history entry to that effect.
	 * <li>We return the modified document.
	 * </ul>
	 * @see #extractGroupPath
	 * @see TelescopeEmbeddedAgent#saveDocument
	 * @see TelescopeEmbeddedAgent#getPhase2Model
	 * @see TelescopeEmbeddedAgent#getProposalMap
	 * @see TelescopeEmbeddedAgent#getLoadArqs
	 * @see TelescopeEmbeddedAgent#getUpdateHandler
	 * @see AgentRequestHandler
	 * @see AgentRequestHandler#abort
	 * @see AgentRequestHandler#expireDocument
	 * @see ngat.oss.model.IPhase2Model#findIdOfGroupInProposal
	 * @see ngat.oss.model.IPhase2Model#deleteGroup
	 * @see org.estar.rtml.RTMLDocument
	 * @see org.estar.rtml.RTMLDocument#getUId
	 * @see org.estar.rtml.RTMLDocument#isTOOP
	 * @see org.estar.rtml.RTMLDocument#setReject
	 * @see org.estar.rtml.RTMLDocument#addHistoryRejection
	 * @see org.estar.rtml.RTMLDocument#setErrorString
	 * @see org.estar.rtml.RTMLDocument#setAbortReply
	 * @see org.estar.rtml.RTMLDocument#addHistoryEntry
	 * @see org.estar.rtml.RTMLDocument#getProject
	 * @see org.estar.rtml.RTMLProject#getProject
	 */
	public RTMLDocument handleAbort(RTMLDocument document) throws Exception 
	{
		AgentRequestHandler arq = null;
		String cid = null;
		String groupName = null;

		cid = document.getUId();

		// Tag/User ID combo is what we expect here.
		RTMLContact contact = document.getContact();
		if (contact == null) 
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort", 
				   "RTML Contact was not specified, failing abort.");
			throw new IllegalArgumentException("No contact was supplied");
		}
		String userId = contact.getUser();
		if (userId == null) 
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort", 
				   "RTML Contact User was not specified, failing abort.");
			throw new IllegalArgumentException("Your User ID was null");
		}
		
		logger.log(INFO, 1, CLASS, cid, "handleAbort", "handleAbort for document UId: " + document.getUId());
		// Is the document a TOOP document
		if (document.isTOOP()) 
		{
			document.setReject();
			document.addHistoryRejection("TEA:"+tea.getId(),null,RTMLHistoryEntry.REJECTION_REASON_SYNTAX,
						     this.getClass().getName()+
						     ":handleAbort:Cannot abort a TOOP document.");
			document.setErrorString(this.getClass().getName()+
						":handleAbort:Cannot abort a TOOP document.");
			logger.log(INFO, 1, CLASS, cid, "handleAbort", "Cannot abort a TOOP document..");
			return document;
		}
		// The document must be a phaseII document
		phase2 = tea.getPhase2Model();
		// Find the proposal ID name from the RTML document's project data
		RTMLProject project = document.getProject();
		if( project == null)
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort","RTML Project was null, failing abort.");
			throw new IllegalArgumentException("handleAbort:RTML Project was null, failing abort.");
		}
		String proposalIdName = project.getProject();
		if (proposalIdName == null) 
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort", 
				   "RTML Project was null, failing abort.");
			throw new IllegalArgumentException("handleAbort:RTML Project was null, failing abort.");
		}
		// Find the proposal info  from the proposal Id name
		Map proposalMap = tea.getProposalMap();
		if (!proposalMap.containsKey(proposalIdName))
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort","Unable to match proposal name: ["+proposalIdName+
					    "] with known proposals.");
			throw new Exception("handleAbort:Unable to match proposal name: [" + proposalIdName + 
					    "] with known proposals.");
		}
		// extract proposal info from proposal map using proposal id name
		ProposalInfo pinfo = (ProposalInfo) proposalMap.get(proposalIdName);
		logger.log(INFO, 1, CLASS, cid, "handleAbort", "Obtained pinfo for: " + proposalIdName);
		if(pinfo == null)
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort",
				   "Unable to extract proposal info from proposal id name "+proposalIdName+".");
			throw new Exception("handleAbort:Unable to extract proposal info from proposal id name "+
					    proposalIdName+".");
		}
		// check extracted user has access permission on this proposal
		if(pinfo.userHasAccess(userId) == false)
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort", "User [" + userId +
				   "] does NOT have access to Proposal [" + proposalIdName + "].");
			throw new Exception("handleAbort:User [" + userId +
				   "] does NOT have access to Proposal [" + proposalIdName + "].");
		}
		else
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort", "User [" + userId +
				   "] does have access to Proposal [" + proposalIdName + "].");
		}
		IProposal proposal = pinfo.getProposal();
		if(proposal == null)
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort",
				   "Unable to extract proposal from proposal id name "+proposalIdName+".");
			throw new Exception("handleAbort:Unable to extract proposal from proposal id name "+
					    proposalIdName+".");
		}
		// Find the name of the group
		// See extractGroup, group.setName is set to the document Uid, with non-word characters replaced by
		// '_'.
		groupName = document.getUId();
		if (groupName == null) 
		{
			logger.log(INFO,1,CLASS,cid,"handleAbort","RTML uid was not specified, failing abort request.");
			throw new IllegalArgumentException("handleAbort:RTML uid was not specified, failing abort request.");
		}
		groupName = groupName.replaceAll("\\W", "_");
		logger.log(INFO,1,CLASS,cid,"handleAbort","PhaseII group name is:"+groupName);
		// Get phase 2 proposal Id
 		long phase2ProposalId = proposal.getID();
		logger.log(INFO,1,CLASS,cid,"handleAbort","PhaseII proposal Id is:"+phase2ProposalId);
		// find group id from proposal id and group name
		logger.log(INFO,1,CLASS,cid,"handleAbort","Attempting to find group name: "+groupName+
			   " in proposal Id:"+phase2ProposalId);
		long phase2GroupId = phase2.findIdOfGroupInProposal(groupName,phase2ProposalId);
		logger.log(INFO,1,CLASS,cid,"handleAbort","Group name: "+groupName+" in proposal Id:"+phase2ProposalId+
			   " has group id:"+phase2GroupId);
		// delete obeervation sequence of group id
		// Do I need to do this, or this deleteGroup do this as well?
		// Not according to Phase2 UI implementation
		//logger.log(INFO,1,CLASS,cid,"handleAbort","Attempting to delete observation sequnce of group id:"+
		//	   phase2GroupId);
		//phase2.deleteObservationSequenceOfGroup(phase2GroupId);
		logger.log(INFO,1,CLASS,cid,"handleAbort","Attempting to delete group id:"+phase2GroupId);
		// delete group from phase2 database
		phase2.deleteGroup(phase2GroupId);
		logger.log(INFO,1,CLASS,cid,"handleAbort","Group id:"+phase2GroupId+" deleted.");
		// signal ARQ to quit
		if (tea.getLoadArqs()) 
		{
			logger.log(INFO, 1, CLASS, cid, "handleAbort", 
				   "Signalling AgentRequestHandler to stop/terminate.");
			String groupPath = extractGroupPath(document);
			arq = tea.getUpdateHandler(groupPath);
			if(arq != null)
			{
				// tell the agent request handler to stop/return some sort of fail document
				arq.abort();
				arq.expireDocument();
			}
			else
			{
				logger.log(INFO,1,CLASS,cid,"handleAbort","Failed to find ARQ for document:"+
					   groupPath);
				// probably not an abort failure here
			}
	        }
		// Note the document on file is not deleted or exprired if we are not using ARQs.
		// This is because there is no map from RTML Document Uid to File as far as I am aware.
		// Save it to the file - we could do this ourself..
		//logger.log(INFO, 1, CLASS, cid, "handleAbort", "Saving document to: " + file.getPath());
		//tea.saveDocument(document, file);
		// reply document aborted
		document.setAbortReply();
		document.addHistoryEntry("TEA:" + tea.getId(), null, "Document aborted.");
		return document;
	}

	/**
	 * Method to extract a group path string from the specified document.
	 * @param document The RTML document to extract the group path from.
	 * @return A string representing the group path. This is of the form:
	 *         "/ODB/" + userId + "/" + proposalId + "/" + requestId
	 * @exception Thrown if an error occurs. i.e. If Contact, User, Project, or Uid data does not exist,
	 *            or no mapping can be found in the TEA's proposal map.
	 * @see #tea
	 * @see TelescopeEmbeddedAgent#getProposalMap
	 * @see org.estar.rtml.RTMLDocument
	 * @see org.estar.rtml.RTMLDocument#getUId
	 * @see org.estar.rtml.RTMLDocument#getContact
	 * @see org.estar.rtml.RTMLDocument#getProject
	 * @see org.estar.rtml.RTMLContact#getUser
	 * @see org.estar.rtmlRTMLProject#getProject
	 */
	public String extractGroupPath(RTMLDocument document) throws Exception 
	{
		String cid = document.getUId();

		// Tag/User ID combo is what we expect here.
		RTMLContact contact = document.getContact();
		if (contact == null) 
		{
			logger.log(INFO, 1, CLASS, cid, "handleRequest", 
				   "RTML Contact was not specified, failing request.");
			throw new IllegalArgumentException("No contact was supplied");
		}
		String userId = contact.getUser();
		if (userId == null) 
		{
			logger.log(INFO, 1, CLASS, cid, "handleRequest", 
				   "RTML Contact User was not specified, failing request.");
			throw new IllegalArgumentException("Your User ID was null");
		}

		// The Proposal ID.
		RTMLProject project = document.getProject();
		String proposalId = project.getProject();

		if (proposalId == null) 
		{
			logger.log(INFO, 1, CLASS, cid, "handleRequest", 
				   "RTML Project was not specified, failing request.");
			throw new IllegalArgumentException("Your Project ID was null");
		}

		// we want the program and proposal ids here
		Map proposalMap = tea.getProposalMap();

		if (!proposalMap.containsKey(proposalId))
			throw new Exception("Proposal [" + proposalId + "] not found in proposal mapping");

		// proposalId = proposalId.replaceAll("\\W", "_");

		String requestId = document.getUId();
		if (requestId == null) 
		{
			logger.log(INFO, 1, CLASS, cid, "handleRequest", 
				   "RTML request ID was not specified, failing request.");
			throw new IllegalArgumentException("Your Request ID was null");
		}
		requestId = requestId.replaceAll("\\W", "_");

		String groupPathName = "/ODB/" + userId + "/" + proposalId + "/" + requestId;

		return groupPathName;

	}

	/** 
	 * Extract a group from the doc. 
	 * Note the group's name becomes it's Uid, with non-word characters replaced by a '_' (i.e.
	 * String.replaceAll("\\W", "_").
	 * @param document A document object model, representing the RTML document containing the group
	 *         data to extract and insert into the Phase2 database.
	 * @exception Exception Thrown if an error occurs.
	 */
	public void extractGroup(RTMLDocument document) throws Exception 
	{
		String cid = document.getUId();

		// Tag/User ID combo is what we expect here.
		RTMLContact contact = document.getContact();
		if (contact == null)
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup", "RTML Contact was not specified, failing request.");
			throw new IllegalArgumentException("No contact was supplied");
		}
		String userId = contact.getUser();
		if (userId == null)
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup", "RTML Contact User was not specified, failing request.");
			throw new IllegalArgumentException("Your User ID was null");
		}

		// The Proposal ID.
		RTMLProject project = document.getProject();
		String proposalId = project.getProject();

		if (proposalId == null)
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup", "RTML Project was not specified, failing request.");
			throw new IllegalArgumentException("Your Project ID was null");
		}

		Map proposalMap = tea.getProposalMap();
		if (!proposalMap.containsKey(proposalId))
			throw new Exception("Unable to match proposal name: [" + proposalId + "] with known proposals");

		// extract rleevant info...
		ProposalInfo pinfo = (ProposalInfo) proposalMap.get(proposalId);
		logger.log(INFO, 1, CLASS, cid, "extractGroup", "Obtained pinfo for: " + proposalId);

		IProposal proposal = pinfo.getProposal();
		ProgramInfo programInfo = pinfo.getProgramInfo();
		IProgram program = programInfo.getProgram();
		Map programTargets = programInfo.getTargetMap();
		Map programConfigs = programInfo.getConfigMap();
		double balance = pinfo.getAccountBalance();

		// check whether the user has permission to add groups to this proposal
		if(pinfo.userHasAccess(userId) == false)
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup", "User [" + userId +
				   "] does NOT have access to Proposal [" + proposalId + "].");
			throw new Exception("User [" + userId + "] does NOT have access to Proposal [" +
					    proposalId + "].");	
		}
		else
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup", "User [" + userId +
				   "] does have access to Proposal [" + proposalId + "].");
		}
		if (balance < 0.0)
		{
			throw new Exception("Proposal [" + proposalId + "] allocation account is overdrawn: Bal=" +
					    balance + "h");
		}
		// proposalId = proposalId.replaceAll("\\W", "_");

		// Retrieve the documents unique ID, either from the uid attribute or
		// user agent's Id
		// depending on RTML version

		String requestId = document.getUId();
		if (requestId == null)
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup",
				   "RTML request ID was not specified, failing request.");
			throw new IllegalArgumentException("Your Request ID was null");
		}
		requestId = requestId.replaceAll("\\W", "_");

		// Pull out unified constraints.
		RTMLSchedule master = getUnifiedConstraints(document);

		// we dont care about this anymore.
		int schedPriority = master.getPriority();

		double seeing = RequestDocumentHandler.DEFAULT_SEEING_CONSTRAINT; // 2.0
		RTMLSeeingConstraint sc = master.getSeeingConstraint();
		if (sc != null)
		{
			seeing = sc.getMaximum();
		}

		double maxair = 2.0;
		RTMLAirmassConstraint airc = master.getAirmassConstraint();
		if (airc != null)
		{
			maxair = airc.getMaximum();
		}

		boolean photom = false;
		RTMLExtinctionConstraint extinct = master.getExtinctionConstraint();
		if (extinct != null)
		{
			photom = extinct.isPhotometric();
		}

		// Extract MG params - many of these can be null !

		RTMLSeriesConstraint scon = master.getSeriesConstraint();

		int count = 0;
		long window = 0L;
		long period = 0L;
		Date startDate = null;
		Date endDate = null;

		if (scon == null)
		{
			// No SC supplied => FlexGroup.
			count = 1;
		}
		else
		{
			// SC supplied => MonitorGroup.
			count = scon.getCount();
			RTMLPeriodFormat pf = scon.getInterval();
			RTMLPeriodFormat tf = scon.getTolerance();

			// No Interval => Wobbly
			if (pf == null)
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup",
						"RTML SeriesConstraint Interval not present, failing request.");
				throw new IllegalArgumentException("No Interval was supplied");
			}
			else
			{
				period = pf.getMilliseconds();

				// No Window => Default to 90% of interval.
				if (tf == null)
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
							"No tolerance supplied, Default window setting to 95% of Interval");
					tf = new RTMLPeriodFormat();
					tf.setSeconds(0.95 * (double) period / 1000.0);
					scon.setTolerance(tf);
				}
			}
			window = tf.getMilliseconds();
			if (count < 1)
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup",
						"RTML SeriesConstraint Count was negative or zero, failing request.");
				throw new IllegalArgumentException("RTML SeriesConstraint Count was negative or zero.");
			}
			if (period < 60000L)
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup",
						"RTML SeriesConstraint Interval is too short, failing request.");
				throw new IllegalArgumentException("You have supplied a ludicrously short monitoring Interval:"+period+" ms.");
			}

			if ((window / period < 0.0) || (window / period > 1.0))
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup", "RTML SeriesConstraint has an odd Window ("+window+" ms) or Period ("+period+" ms).");
				throw new IllegalArgumentException("RTML SeriesConstraint has an odd Window ("+window+" ms) or Period ("+period+" ms).");
			}

		}

		startDate = master.getStartDate();
		endDate = master.getEndDate();

		// FG and MG need an EndDate, No StartDate => Now.
		if (startDate == null)
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup", "Default start date setting to now");
			startDate = new Date();
			master.setStartDate(startDate);
		}

		// No End date => StartDate + 1 day (###this is MicroLens -specific).
		if (endDate == null)
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup", "Default end date setting to Start + 1 day");
			endDate = new Date(startDate.getTime() + 24 * 3600 * 1000L);
			master.setEndDate(endDate);
		}
		// Basic and incomplete sanity checks.
		if (startDate.after(endDate))
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup", "RTML StartDate after EndDate, failing request.");
			throw new IllegalArgumentException("Your StartDate and EndDate do not make sense.");
		}

		logger.log(INFO, 1, CLASS, cid, "extractGroup", "Extracted dates: " + startDate + " -> " + endDate);

		// Uid contains TAG/user
		String proposalPathName = "/ODB/" + userId + "/" + proposalId;

		// Make the group
		XGroup group = new XGroup();
		group.setName(requestId);
		group.setActive(true);

		if (scon == null || count == 1)
		{
			// FlexGroup
			XFlexibleTimingConstraint timing = new XFlexibleTimingConstraint();
			timing.setActivationDate(startDate.getTime());
			timing.setExpiryDate(endDate.getTime());
			group.setTimingConstraint(timing);
		}
		else
		{

			// if (count == 0) {
			// A MonitorGroup.
			XMonitorTimingConstraint timing = new XMonitorTimingConstraint();
			timing.setStartDate(startDate.getTime());
			timing.setEndDate(endDate.getTime());
			timing.setPeriod(period);
			timing.setWindow(window);
			group.setTimingConstraint(timing);
			// } else {
			// XMinimumIntervalTimingConstraint timing = new
			// XMinimumIntervalTimingConstraint();
			// timing.setStart(startDate.getTime());
			// timing.setEnd(endDate.getTime());
			// timing.setMinimumInterval(period);
			// timing.setMaximumRepeats(count);
			// }
		} // TODO can we support IntervalTiming ? probably...

		group.setPriority(TelescopeEmbeddedAgent.GROUP_PRIORITY);

		/*
		 * XLunarDistanceConstraint xld = new XLunarDistanceConstraint(mld);
		 * group.addObservingConstraint(xld); XLunarElevationConstraint xlev =
		 * new XLunarElevationConstraint(lunar);
		 * group.addObservingConstraint(xlev); XSolarElevationConstraint xsol =
		 * new
		 * XSolarElevationConstraint(IObservingConstraint.ASTRONOMICAL_TWILIGHT
		 * ); group.addObservingConstraint(xsol);
		 */

		// extract the sky-b-category from schedule.
		int skyCat = getSkyBrightnessFromConstraints(master);
		XSkyBrightnessConstraint xskyb = new XSkyBrightnessConstraint(skyCat);
		group.addObservingConstraint(xskyb);

		// loks like we currently allow astro-twilight which is 2 or 4 depending
		// on lunar atm !

		// set seeing limits.
		// int seecat = IObservingConstraint.POOR_SEEING;

		/*
		 * if (seeing >= 3.0) { seecat =
		 * IObservingConstraint.UNCONSTRAINED_SEEING; } else if (seeing >= 1.3)
		 * { seecat = IObservingConstraint.POOR_SEEING; } else if (seeing >=
		 * 0.8) { seecat = IObservingConstraint.AVERAGE_SEEING; } else { // this
		 * will also catch any with silly values like < 0.0 ! seecat =
		 * IObservingConstraint.GOOD_SEEING; }
		 */

		// using the actual seeing value now
		XSeeingConstraint xsee = new XSeeingConstraint(seeing);
		group.addObservingConstraint(xsee);

		if (airc != null)
		{
			XAirmassConstraint xair = new XAirmassConstraint(maxair);
			group.addObservingConstraint(xair);
		}

		if (photom)
		{
			int extinctionLevel = XPhotometricityConstraint.PHOTOMETRIC;
			XPhotometricityConstraint xphot = new XPhotometricityConstraint(extinctionLevel, 1.0);
			group.addObservingConstraint(xphot);
		}

		// Extract the Observation request(s) - handle multiple obs per doc.

		XIteratorRepeatCountCondition once = new XIteratorRepeatCountCondition(1);
		XIteratorComponent root = new XIteratorComponent("root", once);

		// FIRST PASS

		// work out which instruments we are using...
		// check if frodo is one of them
		// decide on alignment instrument for slew, at the very least it
		// will be the instrument whose config occurs first

		int nobs = document.getObservationListCount();
		logger.log(INFO, 1, CLASS, cid, "extractGroup", "Begin processing: " + nobs + " observations in rtml doc");

		String useAlignmentInstrument = null;
		boolean hasFrodoObs = false;
		boolean hasSpratObs = false;
		boolean currentConfigIsMoptop = false;
		
		for (int iobsa = 0; iobsa < nobs; iobsa++)
		{

			RTMLObservation obs = document.getObservation(iobsa);
			RTMLDevice rtmlDevice = obs.getDevice();
			if (rtmlDevice == null)
				rtmlDevice = document.getDevice();

			if (rtmlDevice != null)
			{
				try
				{
					InstrumentConfig config = DeviceInstrumentUtilites.getInstrumentConfig(tea, rtmlDevice);
					XInstrumentConfig newConfig = translateToNewStyleConfig(config);

					logger.log(INFO, 1, CLASS, cid, "extractGroup",
							"This observation uses: " + newConfig.getInstrumentName());
					String instName = newConfig.getInstrumentName().toUpperCase();

					if (instName.startsWith("FRODO"))
						hasFrodoObs = true;
					if (instName.startsWith("SPRAT"))
						hasSpratObs = true;

					// always align to chosen instrument - this may be wrong for FRODO
					// TODO note we should use first not LAST !
					useAlignmentInstrument = instName;

				}
				catch (Exception e)
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup", 
						   "Error determining alignment instrument");
				}
			}// end if rtmlDevice
		}// end for over nobs (first pass)

		// It is difficult to determine how to switch targets, or switch instruments to/from Sprat
		// Therefore only allow 1 Sprat multrun per RTML request
		if(hasSpratObs && (nobs > 1))
		{
			throw new IllegalArgumentException("extractGroup:Only one observation per RTML request is supported when using SPRAT.");
		}

		// create frdo red and blue branches
		XIteratorComponent redArm = null;
		XIteratorComponent blueArm = null;
		if(hasFrodoObs)
		{
			redArm = new XIteratorComponent("FRODO_RED", new XIteratorRepeatCountCondition(1));
			blueArm = new XIteratorComponent("FRODO_BLUE", new XIteratorRepeatCountCondition(1));
		}

		// PASS 2 extract info to build sequence

		// keep track of target changes
		XExtraSolarTarget lastTarget = null;

		// keep track of config changes
		XInstrumentConfig lastConfig = null;

		double lastRaOffset = 0.0;
		double lastDecOffset = 0.0;
		boolean isGuiding = false;
		boolean shouldBeGuiding = false;

		for (int iobs = 0; iobs < nobs; iobs++) 
		{
			RTMLObservation obs = document.getObservation(iobs);

			// Extract params
			RTMLTarget target = obs.getTarget();
			// If there is no per-observation target, get the default document target
			if(target == null)
				target = document.getTarget();
			RA ra = target.getRA();
			Dec dec = target.getDec();

			String targetId = target.getName();
			if (targetId == null || targetId.equals(""))
				targetId = "Target_" + iobs + "_" + requestId;
			// e.g. Target_2_UA123
			targetId.replaceAll("\\W", "_");

			// Bizarre element.
			String targetIdent = target.getIdent();

			RTMLSchedule sched = obs.getSchedule();

			String expy = sched.getExposureType();
			String expu = sched.getExposureUnits();
			double expt = 0.0;

			expt = sched.getExposureLengthMilliseconds();

			int expCount = sched.getExposureCount();

			// -------------------------------------------------------------
			// 0. Decide if we need to use the autoguider for this exposure
			// -------------------------------------------------------------

			// check to see if we should be autoguiding, we may be anyway..
			long maxUnguidedExposureLength = DEFAULT_MAXIMUM_UNGUIDED_EXPOSURE;
			try
			{
				maxUnguidedExposureLength = tea.getPropertyLong("maximum.unguided.exposure.length");
			}
			catch (Exception ee)
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup",
						"There was a problem locating the property: maximum.unguided.exposure.length");
			}

			if ((long) expt > maxUnguidedExposureLength)
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup",
					   "Exposure length "+expt+" greater than maximum unguided exposure length "+
					   maxUnguidedExposureLength+":shouldBeGuiding set to true.");
				shouldBeGuiding = true;
			}
			if(hasSpratObs)
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup",
					   "Contains Sprat observations, turning autoguiding on.");
				shouldBeGuiding = true;
			}

			// --------------------------
			// 1. Handle Target Selection
			// --------------------------

			XExtraSolarTarget star = new XExtraSolarTarget(targetId);
			star.setRa(ra.toRadians());
			star.setDec(dec.toRadians());
			star.setFrame(ReferenceFrame.FK5);
			// OOPS we need something in Phase2 for these frames or should it be
			// in new-astro?
			star.setEpoch(2000.0);

			// is this a known target ? If not we will need to create it in
			// program
			boolean knownTarget = false;
			if (programTargets.containsKey(star.getName())) 
			{
				knownTarget = true;
				star = (XExtraSolarTarget) programTargets.get(star.getName());
			}

			// these are in arcsecs
			double raOffsetArcs = target.getRAOffset();
			double decOffsetArcs = target.getDecOffset();

			double raOffset = Math.toRadians(raOffsetArcs / 3600.0);
			double decOffset = Math.toRadians(decOffsetArcs / 3600.0);

			// is this a new target ? same name AND same offsets
			logger.log(INFO, 1, CLASS, cid, "extractGroup","Compare current target: " + star + 
				   " offset: " + raOffsetArcs + "," + decOffsetArcs + 
				   "\n         with previous: " + lastTarget + 
				   " offset: " + lastRaOffset + "," + lastDecOffset);

			boolean sameTargetAsLast = ((lastTarget != null) && (star.getName().equals(lastTarget.getName())));

			boolean sameTargetButOffset = sameTargetAsLast
					&& ((Math.abs(raOffset - lastRaOffset) > MIN_OFFSET) || (Math.abs(decOffset - lastDecOffset) > MIN_OFFSET));

			boolean hasOffset = (Math.abs(raOffset) > 0.0) || (Math.abs(decOffset) > 0.0);

			boolean diffTargetWithOffsets = !sameTargetAsLast && hasOffset;

			logger.log(INFO, 1, CLASS, cid, "extractGroup","sameTargetAsLast: " + sameTargetAsLast + 
				   " sameTargetButOffset: " + sameTargetButOffset + " hasOffset:" + hasOffset + 
				   " diffTargetWithOffsets: " + diffTargetWithOffsets);

			lastRaOffset = raOffset;
			lastDecOffset = decOffset;

			if (!sameTargetAsLast)
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup","Switching target");

				if (!knownTarget)
				{
					// if we dont know about it then create it and add to our
					// records.
					long tid = phase2.addTarget(program.getID(), star);
					star.setID(tid);
					programTargets.put(star.getName(), star);
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Target successfully added to program: " + star);
				}
			}
			lastTarget = star;

			// -----------------------
			// 2. Handle Configuration
			// -----------------------

			// Extract filter info.
			RTMLDevice rtmlDevice = obs.getDevice();
			if (rtmlDevice == null)
				rtmlDevice = document.getDevice();

			// make up the IC - we dont have enough info to do this from
			// filtermap...
			InstrumentConfig config = null;
			XInstrumentConfig newConfig = null;
			String configId = null;


			if (rtmlDevice != null) 
			{
				try
				{
					config = DeviceInstrumentUtilites.getInstrumentConfig(tea, rtmlDevice);
					newConfig = translateToNewStyleConfig(config);
					configId = config.getName();
					// Is the current config a MOPTOP one?
					String instName = newConfig.getInstrumentName().toUpperCase();
					if(instName.equals("MOPTOP"))
						currentConfigIsMoptop = true;
					else
						currentConfigIsMoptop = false;
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "currentConfigIsMoptop =  "+currentConfigIsMoptop);
				}
				catch (Exception e)
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup", "Device configuration error: " + e);
					throw new IllegalArgumentException("Device configuration error: " + e);
				}
			}
			else
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup", "RTML Device not present");
				throw new IllegalArgumentException("Device not set");
			}

			// is this a new config ?
			logger.log(INFO, 1, CLASS, cid, "extractGroup","Compare current config: " + newConfig + 
				   "\n        with previous: " + lastConfig);
			boolean sameConfigAsLast = ((lastConfig != null) && (newConfig.getName().equals(lastConfig.getName())));

			// same instrument or not ?
			boolean sameInstrumentAsLast = ((lastConfig != null) && (newConfig.getInstrumentName()
					.equalsIgnoreCase(lastConfig.getInstrumentName())));
			// Special case for Frodospec, thats the "sameInstrumentAsLast" even though it isn't, as we
			// don't need to insert new FocalPlane or AG off/on commands
			if(((lastConfig != null) && (newConfig.getInstrumentName().startsWith("FRODO")) &&
			    (lastConfig.getInstrumentName().startsWith("FRODO"))))
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup", 
					   "This config and last config are both FRODO: set sameInstrumentAsLast to true.");
				sameInstrumentAsLast = true;
			}
			logger.log(INFO, 1, CLASS, cid, "extractGroup", 
				   "sameConfigAsLast: "+sameConfigAsLast+" sameInstrumentAsLast:"+sameInstrumentAsLast+".");
			// is this a known config ? If not we will need to create it in
			// program
			boolean knownConfig = false;
			if (programConfigs.containsKey(config.getName()))
			{
				knownConfig = true;
				newConfig = (XInstrumentConfig) programConfigs.get(config.getName());
			}

			if (!sameConfigAsLast)
			{
				// switching config
				logger.log(INFO, 1, CLASS, cid, "extractGroup","Switching config - and maybe instrument");
				if (!knownConfig)
				{
					// if we dont know about it then create it and add to our
					// records.
					long ccid = phase2.addInstrumentConfig(program.getID(), newConfig);
					newConfig.setID(ccid);
					programConfigs.put(newConfig.getName(), newConfig);
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Config successfully added to program: " + newConfig);
				}
			}
			lastConfig = newConfig;

			// -----------------------
			// 3. handle exposure
			// -----------------------

			if (expt < 1000.0)
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup",
					   "Exposure time is too short, failing request.");
				throw new IllegalArgumentException("Your Exposure time is too short.");
			}

			// We need to do at least one exposure, unless this is a moptop observation
			// which is a special case.
			if ((expCount < 1) && (currentConfigIsMoptop == false))
			{
				logger.log(INFO, 1, CLASS, cid, "extractGroup",
					   "Exposure Count is less than 1, failing request.");
				throw new IllegalArgumentException("Your Exposure Count is less than 1.");
			}

			float expose = (float) expt;
			int mult = expCount;

			// At this point gather all the relavnt info together
			// targetChanged, instrumentChanged, configChanged, areAutoguiding,
			// wantAutoguiding

			boolean usingredarm = false;
			boolean usingbluearm = false;

			// is it the first observation, slew and rotate then acquire
			if (iobs == 0) 
			{

				// set rotator config - we dont know at this point which
				// instruments we will be using
				// so how do we set the rotator alignment ?
				// XRotatorConfig xrot = new
				// XRotatorConfig(IRotatorConfig.CARDINAL, 0.0,
				// RATCAM_INSTRUMENT);
				
				// TODO we need to have a SetAcquireInst(uai) here before the slew
				XRotatorConfig xrot = null;
				if(hasFrodoObs)
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "We have Frodospec observations, so slewing with rotator aligned to IO:O"); 
					xrot = new XRotatorConfig(IRotatorConfig.CARDINAL, 0.0,"IO:O");
				}
				else if(hasSpratObs)
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "We have Sprat observations, so slewing with rotator aligned to mount angle 11.0"); 
					xrot = new XRotatorConfig(IRotatorConfig.MOUNT, SPRAT_MOUNT_ANGLE_RADS, useAlignmentInstrument);
				}
				else if(currentConfigIsMoptop)
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Current config is a Moptop one, so slewing with rotator aligned to mount angle 0.0"); 
					xrot = new XRotatorConfig(IRotatorConfig.MOUNT, 0.0, useAlignmentInstrument);
				}
				else
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Slewing with rotator aligned to:"+useAlignmentInstrument); 
					xrot = new XRotatorConfig(IRotatorConfig.CARDINAL, 0.0, useAlignmentInstrument);
				}
				// XExecutiveComponent exrot = new
				// XExecutiveComponent("Rotate-Cardinal", xrot);
				// root.addElement(exrot);

				// slew and rotate onto first target
				XSlew xslew = new XSlew(star, xrot, false);
				XExecutiveComponent exslew = new XExecutiveComponent("Slew-" + targetId + "/Cardinal", xslew);
				root.addElement(exslew);

				// test for an offset from base target position
				if (hasOffset) 
				{
					// add an offset
					XPositionOffset xoffset = new XPositionOffset(false, raOffset, decOffset);
					XExecutiveComponent exoffset = new XExecutiveComponent("Offset-(" + raOffsetArcs + ","
							+ decOffsetArcs + ")", xoffset);
					root.addElement(exoffset);
				}

				// acquire first instrument = aperture offset

				// if we are using frodo we aperture offset for IO instead
				// then we do a real acquire
				if (hasFrodoObs) 
				{
					String ACQ_INST_NAME = "IO:O";
					XAcquisitionConfig xap = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					// set the aperture offset onto the acquiring instrument
					xap.setTargetInstrumentName(ACQ_INST_NAME);
					XExecutiveComponent eXAp = new XExecutiveComponent("ApInst", xap);
					root.addElement(eXAp);

					XAcquisitionConfig xaq = new XAcquisitionConfig(IAcquisitionConfig.WCS_FIT);
					// set the target as the real instrument and acquirer to
					// default acquirer
					xaq.setAcquisitionInstrumentName(ACQ_INST_NAME);
					// newConfig.getInstrumentName() could be used here but returns something like
					// "FRODO_RED". This gives an RCS error 660104 "Unknown target instrument".
					// So hard-code "FRODO" here.
					xaq.setTargetInstrumentName("FRODO");
					xaq.setPrecision(IAcquisitionConfig.PRECISION_NORMAL);
					XExecutiveComponent eXAcq = new XExecutiveComponent("AcqInst", xaq);
					root.addElement(eXAcq);

					// We need to Add a Frodospec config before the branch/ AG on
					// This changes the telescope focus offset before we try and turn on the Autoguider
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Handling first non-branched frodo config to set focus offset for autoguider: " + 
						   newConfig.getInstrumentName());
					XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
					XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
					root.addElement(exinst);

					// Autoguider should be turned on before the branch for Frodospec sequences
					if (shouldBeGuiding && (!isGuiding)) 
					{
						IAutoguiderConfig xAutoOn = new XAutoguiderConfig(IAutoguiderConfig.ON, 
												  "AutoOn");
						XExecutiveComponent eXAutoOn = new XExecutiveComponent("AutoOn", xAutoOn);
						root.addElement(eXAutoOn);
						isGuiding = true; // We are guiding now (hopefully)
					}

					// and create a frodo branch with 2 arms
					XBranchComponent branch = new XBranchComponent("FRODO");
					branch.addChildComponent(redArm);
					branch.addChildComponent(blueArm);
					root.addElement(branch);

				} 
				// If doing Sprat we can use the same initial FOCAL_PLANE as the imagers
				else 
				{
					// TODO setAcqInst(uai) and add(ApertureConfig())
					XAcquisitionConfig xap = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					// set the aperture offset onto the real instrument
					xap.setTargetInstrumentName(newConfig.getInstrumentName());
					XExecutiveComponent eXAcq = new XExecutiveComponent("ApInst", xap);
					root.addElement(eXAcq);

				}
				// SPRAT specific acquisition
				// * FINE_TUNE - Normal precision
				// * Autogudier on
				// * FINE-TUNE - High precision
				// * SLIT IMAGE Config
				// * Expose slit for 10 seconds
				if(hasSpratObs)
				{
					// Then we need to FINE_TUNE - Normal precision
					XAcquisitionConfig xAcqCfgFineTune_NORMAL = new XAcquisitionConfig(IAcquisitionConfig.WCS_FIT);
					xAcqCfgFineTune_NORMAL.setAcquisitionInstrumentName("SPRAT");
					xAcqCfgFineTune_NORMAL.setTargetInstrumentName("SPRAT");
					xAcqCfgFineTune_NORMAL.setPrecision(IAcquisitionConfig.PRECISION_NORMAL);
					XExecutiveComponent eXFineTuneNormal = new XExecutiveComponent("FineTune", xAcqCfgFineTune_NORMAL);
					root.addElement(eXFineTuneNormal); // FINE-TUNE - Normal precision

					// Turn the Autoguider on
					IAutoguiderConfig xAutoOn = new XAutoguiderConfig(IAutoguiderConfig.ON,
											  "AutoOn");
					XExecutiveComponent eXAutoOn = new XExecutiveComponent("AutoOn",xAutoOn);
					root.addElement(eXAutoOn); // AUTO-GUIDER
					isGuiding = true;

					// FINE-TUNE - High precision
					XAcquisitionConfig xAcqCfgFineTune_HIGH = new XAcquisitionConfig(IAcquisitionConfig.WCS_FIT);
					xAcqCfgFineTune_HIGH.setAcquisitionInstrumentName("SPRAT");
					xAcqCfgFineTune_HIGH.setTargetInstrumentName("SPRAT");
					xAcqCfgFineTune_HIGH.setPrecision(IAcquisitionConfig.PRECISION_HIGH);
					XExecutiveComponent eXFineTuneHigh = new XExecutiveComponent("FineTune", xAcqCfgFineTune_HIGH);
					root.addElement(eXFineTuneHigh); // FINE-TUNE - High precision
					// SLIT IMAGE Config 
					// create a SPRAT config for imaging the slit
					XInstrumentConfig spratSlitImageConfig = getSPRATSlitImageInstrumentConfig();
					// add it to the phase2database and get an ID for it
					long spratSlitImageConfigId = phase2.addInstrumentConfig(program.getID(), spratSlitImageConfig);
					// set the ID back on the object
					spratSlitImageConfig.setID(spratSlitImageConfigId);
					// add it to the programConfigs map
					programConfigs
						.put(spratSlitImageConfig.getName(), spratSlitImageConfig);
					System.err.println("Config successfully added to program: "
							   + spratSlitImageConfig);
					// create the config selector object
					XInstrumentConfigSelector xinstCfgSelector_slitImage = new XInstrumentConfigSelector(spratSlitImageConfig);
					// wrap selector in an executive component
					XExecutiveComponent ecSlitImageCfg = new XExecutiveComponent("SlitImgConfig-" + configId, 
												     xinstCfgSelector_slitImage);

					root.addElement(ecSlitImageCfg); // SLIT IMAGE CONFIG *

					// expose for 10 seconds
					XMultipleExposure xMultSlitImage = new XMultipleExposure(10000, 1);
					XExecutiveComponent exMultSlitImage = new XExecutiveComponent("slitImageExposure", xMultSlitImage);
					root.addElement(exMultSlitImage);

				}// end if (hasSpratObs)
				// select first instr config
				if(hasFrodoObs) 
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Handling frodo config: " + newConfig.getInstrumentName());

					if (newConfig.getInstrumentName().equalsIgnoreCase("FRODO_RED")) 
					{
						usingredarm = true;
						logger.log(INFO, 1, CLASS, cid, "extractGroup", "Config will go in RED arm");
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						redArm.addElement(exinst);
					} 
					else if (newConfig.getInstrumentName().equalsIgnoreCase("FRODO_BLUE")) 
					{
						usingbluearm = true;
						logger.log(INFO, 1, CLASS, cid, "extractGroup", "Config will go in BLUE arm");
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						blueArm.addElement(exinst);
					
					}
				}
				else if(hasSpratObs)
				{
					// USER DEFINED CONFIG
					// slit = in, grism = in, grism rotation = user defined.

					RTMLGrating rtmlGrating = rtmlDevice.getGrating();
					if (rtmlGrating == null) 
					{
						throw new IllegalArgumentException("null grating on SPRAT RTML request.");
					}
					String gratingName = rtmlGrating.getName();
					XInstrumentConfig userDefinedSpratCfg = getUserDefinedSpratCfg(gratingName);
					// add it to the phase2database and get an ID for it
					long userDefinedSpratCfgId = phase2.addInstrumentConfig(program.getID(), userDefinedSpratCfg);
					// set the ID back on the object
					userDefinedSpratCfg.setID(userDefinedSpratCfgId);
					// add it to the programConfigs map
					programConfigs.put(userDefinedSpratCfg.getName(), userDefinedSpratCfg);
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Config successfully added to program: "+userDefinedSpratCfg);
					// create the config selector object
					XInstrumentConfigSelector xinstCfgSelector_userDef = new XInstrumentConfigSelector(userDefinedSpratCfg);
					// wrap selector in an executive component
					XExecutiveComponent ecUserDefCfg = new XExecutiveComponent("UserDefConfig-" + configId, xinstCfgSelector_userDef);
					root.addElement(ecUserDefCfg); // USER_DEFINED_CONFIG *
				}
				else 
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Handling non-frodo config: " + newConfig.getInstrumentName());
					XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
					XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
					root.addElement(exinst);
				}

				// Maybe AG ON ?
				if (shouldBeGuiding && (!isGuiding)) {
					IAutoguiderConfig xAutoOn = new XAutoguiderConfig(IAutoguiderConfig.ON_IF_AVAILABLE, "AutoOn");
					XExecutiveComponent eXAutoOn = new XExecutiveComponent("AutoOn", xAutoOn);
					root.addElement(eXAutoOn);
					isGuiding = true; // We are guiding now (hopefully)
				}

				// exposure
				if (hasFrodoObs) 
				{
					XMultipleExposure xMult = new XMultipleExposure(expose, mult);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xMult);
					if (usingredarm)
						redArm.addElement(exMult);
					else
						blueArm.addElement(exMult);

				}
				else if(currentConfigIsMoptop)
				{
					XPeriodExposure xPeriodExposure = new XPeriodExposure(expose);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xPeriodExposure);
					root.addElement(exMult);
				}
				else 
				{
					XMultipleExposure xMult = new XMultipleExposure(expose, mult);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xMult);
					root.addElement(exMult);
				}

				// Sprat wants a following ARC
				if(hasSpratObs)
				{
					// Xe ARC
					XArc arc = new XArc();
					arc.setLamp(new XLampDef("Xe"));
					XExecutiveComponent exArc = new XExecutiveComponent("xe_arc", arc);
					root.addElement(exArc); // Xe arc
				}
			} // end if first observation
			else 
			{
				// NOT the first obs but a subsequent one

				// any instrument or telescope changes - autooff (if on)
				if ((!sameTargetAsLast) || (!sameInstrumentAsLast) || (sameTargetButOffset)) {
					logger.log(INFO, 1, CLASS, cid, "extractGroup", 
						   "NOT first obs:Turning off autoguider due to: NOT sameTargetAsLast:"+sameTargetAsLast+
						   " NOT sameInstrumentAsLast:"+sameInstrumentAsLast+
						   " sameTargetButOffset:"+sameTargetButOffset);
					IAutoguiderConfig xAutoOff = new XAutoguiderConfig(IAutoguiderConfig.OFF, "AutoOff");
					XExecutiveComponent eXAutoOff = new XExecutiveComponent("AutoOff", xAutoOff);
					root.addElement(eXAutoOff);
					isGuiding = false; // We are not guiding now
				}
				// change of instrument
				if (!sameInstrumentAsLast)
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "NOT first obs:Change of instrument.");
					XAcquisitionConfig xAcq = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					xAcq.setTargetInstrumentName(newConfig.getInstrumentName());
					XExecutiveComponent eXAcq = new XExecutiveComponent("AcqInst", xAcq);
					root.addElement(eXAcq);
				}

				// change of target - allow a rotate change
				if (!sameTargetAsLast)
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup","NOT first obs:Change of target.");
					// XTargetSelector xtc = new XTargetSelector(star);
					// no parent target as its a one-off, not a clone
					// XExecutiveComponent extc = new
					// XExecutiveComponent("Target-" + targetId, xtc);
					// root.addElement(extc);
					XRotatorConfig xrot = null;
					if(currentConfigIsMoptop)
					{
						logger.log(INFO, 1, CLASS, cid, "extractGroup",
							   "Current config is a Moptop one, so slewing with rotator aligned to mount angle 0.0"); 
						xrot = new XRotatorConfig(IRotatorConfig.MOUNT, 0.0,
									  useAlignmentInstrument);
					}
					else
					{
						logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Slewing with rotator aligned to:"+useAlignmentInstrument); 
						xrot = new XRotatorConfig(IRotatorConfig.CARDINAL, 0.0,
									  useAlignmentInstrument);
					}
					// XExecutiveComponent exrot = new
					// XExecutiveComponent("Rotate-Cardinal", xrot);
					// root.addElement(exrot);

					// slew and rotate onto first target
					XSlew xslew = new XSlew(star, xrot, false);
					XExecutiveComponent exslew = new XExecutiveComponent("Slew-" + targetId + "/Cardinal", xslew);
					root.addElement(exslew);

					// we have changed target, is there ANY offset at all
					if (hasOffset)
					{
						logger.log(INFO, 1, CLASS, cid, "extractGroup","Offsetting");
						XPositionOffset xpoff = new XPositionOffset(false, raOffset, decOffset);
						XExecutiveComponent expoff = new XExecutiveComponent("Offset-(" + raOffsetArcs + ","
								+ decOffsetArcs + ")", xpoff);
						root.addElement(expoff);
					}

					// snf (in response to p2ui-comments) 2-sept-2010 add
					// another aperture as we may have lost it
					XAcquisitionConfig xAcq = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					xAcq.setTargetInstrumentName(newConfig.getInstrumentName());
					XExecutiveComponent eXAcq = new XExecutiveComponent("AcqInst", xAcq);
					root.addElement(eXAcq);
				}

				// offset
				if (sameTargetButOffset)
				{
					// offset
					logger.log(INFO, 1, CLASS, cid, "extractGroup","NOT first obs:Offsetting");
					XPositionOffset xpoff = new XPositionOffset(false, raOffset, decOffset);
					XExecutiveComponent expoff = new XExecutiveComponent("Offset-(" + raOffsetArcs + ","
							+ decOffsetArcs + ")", xpoff);
					root.addElement(expoff);
					// snf (in response to p2ui-comments) 2-sept-2010 add
					// another aperture as we may have lost it
					XAcquisitionConfig xAcq = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					xAcq.setTargetInstrumentName(newConfig.getInstrumentName());
					XExecutiveComponent eXAcq = new XExecutiveComponent("AcqInst", xAcq);
					root.addElement(eXAcq);

				}

				if (hasFrodoObs)
				{
					// for frodo we dont care if its same or not its too
					// blinking difficult
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "NOT first obs:Handling another frodo config: "+newConfig.getInstrumentName());

					if (newConfig.getInstrumentName().equalsIgnoreCase("FRODO_RED"))
					{
						usingredarm = true;
						logger.log(INFO, 1, CLASS, cid, "extractGroup", "Config will go in BLUE arm");
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						redArm.addElement(exinst);
					}
					else if (newConfig.getInstrumentName().equalsIgnoreCase("FRODO_BLUE"))
					{
						usingbluearm = true;
						logger.log(INFO, 1, CLASS, cid, "extractGroup", "Config will go in BLUE arm");
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						blueArm.addElement(exinst);
					}

				}
				else
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "Handling non-frodo config: " + newConfig.getInstrumentName());
					if (!sameConfigAsLast)
					{
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						root.addElement(exinst);
					}
				}

				// do we AG ON ?
				if (shouldBeGuiding && (!isGuiding))
				{
					logger.log(INFO, 1, CLASS, cid, "extractGroup",
						   "NOT first obs:Adding another autoguider ON.");
					IAutoguiderConfig xAutoOn = new XAutoguiderConfig(IAutoguiderConfig.ON_IF_AVAILABLE, "AutoOn");
					XExecutiveComponent eXAutoOn = new XExecutiveComponent("AutoOn", xAutoOn);
					root.addElement(eXAutoOn);
					isGuiding = true; // We are guiding now (hopefully)
				}

				// setup exposure
				if (hasFrodoObs)
				{
					XMultipleExposure xMult = new XMultipleExposure(expose, mult);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xMult);
					if (usingredarm)
						redArm.addElement(exMult);
					else
						blueArm.addElement(exMult);
				}
				else if(currentConfigIsMoptop)
				{
					XPeriodExposure xPeriodExposure = new XPeriodExposure(expose);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xPeriodExposure);
					root.addElement(exMult);
				}
				else
				{
					XMultipleExposure xMult = new XMultipleExposure(expose, mult);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xMult);
					root.addElement(exMult);
				}
			} // not first obs
		} // next observation
		// finally off the guider if its on
		if (isGuiding)
		{
			logger.log(INFO, 1, CLASS, cid, "extractGroup","Turning autoguider off at end of group.");
			IAutoguiderConfig xAutoOff = new XAutoguiderConfig(IAutoguiderConfig.OFF, "AutoOff");
			XExecutiveComponent eXAutoOff = new XExecutiveComponent("AutoOff", xAutoOff);
			root.addElement(eXAutoOff);
		}

		// -----------------------
		// 4. Tear-down
		// ---------------------

		System.err.println("Extracted group from rtml: " + group);
		String sequenceStr = DisplaySeq.display(1, root);
		System.err.println("Extracted obseq from rtml: " + sequenceStr);

		// see which pid is our proposal
		long pid = proposal.getID();

		System.err.println("Attempting to add group to proposal: " + pid);
		long gid = phase2.addGroup(pid, group);
		System.err.println("Group successfully added as ID: " + gid);

		System.err.println("Attempting to set group's sequence...");
		long sid = phase2.addObservationSequence(gid, root);
		System.err.println("Group sequence was successfully set: " + root);

	} // [extractGroup]

	private int getSkyBrightnessFromConstraints(RTMLSchedule master)
	{

		RTMLSkyConstraint skyc = master.getSkyConstraint();
		if (skyc != null)
		{

			// TODO could be new SKY_B values
			if (skyc.getUseValue())
			{
				double maxSky = skyc.getValue();
				return SkyBrightnessCalculator.getSkyBrightnessCategory(maxSky);
			}
			else
			{
				double mld = Math.toRadians(30.0);
				RTMLMoonConstraint mc = master.getMoonConstraint();
				if (mc != null)
					mld = mc.getDistanceRadians();

				boolean dark = false;
				if (skyc.isDark())
					dark = true;

				if (dark || mld < Math.toRadians(30.0))
					return XSkyBrightnessConstraint.MAG_1P5;
				else
					return XSkyBrightnessConstraint.MAG_4;
			}

		}
		return XSkyBrightnessConstraint.MAG_4;

	}

	/**
	 * Check the various constraints and return a unified set.
	 * 
	 * @param document
	 *            The RTMLDocument with potentially more than one set of
	 *            schedule constraints.
	 * @return The unified set of constraints.
	 * @exception IllegalArgumentException
	 *                Thrown if the various constraints in the documents are not
	 *                identical.
	 * @see #equalsOrNull
	 */
	private RTMLSchedule getUnifiedConstraints(RTMLDocument document) throws IllegalArgumentException
	{
		RTMLSeriesConstraint masterSeries, currentSeries;
		RTMLMoonConstraint masterMoon, currentMoon;
		RTMLSeeingConstraint masterSeeing, currentSeeing;
		RTMLExtinctionConstraint masterExtinct, currentExtinct;
		RTMLAirmassConstraint masterAir, currentAir;
		RTMLSkyConstraint masterSky, currentSky;
		RTMLSchedule master;
		Date masterStartDate, currentStartDate;
		Date masterEndDate, currentEndDate;
		int masterPriority, currentPriority;

		int nobs = document.getObservationListCount();

		if (nobs == 0)
			return null;
		// #0 will be our master...
		master = document.getObservation(0).getSchedule();
		if (master == null)
		{
			masterStartDate = null;
			masterEndDate = null;
			masterSeries = null;
			masterMoon = null;
			masterSeeing = null;
			masterSky = null;
			masterAir = null;
			masterExtinct = null;
			masterPriority = -1;
		}
		else
		{
			masterStartDate = master.getStartDate();
			masterEndDate = master.getEndDate();
			masterSeries = master.getSeriesConstraint();
			masterMoon = master.getMoonConstraint();
			masterSeeing = master.getSeeingConstraint();
			masterSky = master.getSkyConstraint();
			masterAir = master.getAirmassConstraint();
			masterExtinct = master.getExtinctionConstraint();
			masterPriority = master.getPriority();
		}
		for (int iobs = 1; iobs < nobs; iobs++)
		{
			RTMLObservation obs = document.getObservation(iobs);
			RTMLSchedule sched = obs.getSchedule();
			if (sched == null)
			{
				currentSeries = null;
				currentMoon = null;
				currentSeeing = null;
				currentSky = null;
				currentAir = null;
				currentExtinct = null;
				currentStartDate = null;
				currentEndDate = null;
				currentPriority = -1;
			}
			else
			{
				currentSeries = sched.getSeriesConstraint();
				currentMoon = sched.getMoonConstraint();
				currentSeeing = sched.getSeeingConstraint();
				currentSky = sched.getSkyConstraint();
				currentAir = sched.getAirmassConstraint();
				currentExtinct = sched.getExtinctionConstraint();
				currentStartDate = sched.getStartDate();
				currentEndDate = sched.getEndDate();
				currentPriority = sched.getPriority();
			}

			if (!equalsOrNull(currentStartDate, masterStartDate))
				throw new IllegalArgumentException("Constraint mismatch: StartDate for " + iobs + " ss="
						+ sched.getStartDate() + " ms=" + master.getStartDate());

			if (!equalsOrNull(currentEndDate, masterEndDate))
				throw new IllegalArgumentException("Constraint mismatch: EndDate for " + iobs + " se="
						+ sched.getEndDate() + " me=" + master.getEndDate());

			if (!equalsOrNull(currentSeries, masterSeries))
				throw new IllegalArgumentException("Constraint mismatch: SeriesConstraint for " + iobs + " ss="
						+ sched.getSeriesConstraint() + " ms=" + master.getSeriesConstraint());

			if (!equalsOrNull(currentMoon, masterMoon))
				throw new IllegalArgumentException("Constraint mismatch: MoonConstraint for " + iobs);

			if (!equalsOrNull(currentSeeing, masterSeeing))
				throw new IllegalArgumentException("Constraint mismatch: SeeingConstraint for " + iobs);

			if (!equalsOrNull(currentSky, masterSky))
				throw new IllegalArgumentException("Constraint mismatch: SkyConstraint for " + iobs);

			if (!equalsOrNull(currentAir, masterAir))
				throw new IllegalArgumentException("Constraint mismatch: AirmassConstraint for " + iobs);

			if (!equalsOrNull(currentExtinct, masterExtinct))
				throw new IllegalArgumentException("Constraint mismatch: ExtinctionConstraint for " + iobs);

			if (currentPriority != masterPriority)
				throw new IllegalArgumentException("Constraint mismatch: Sched priority " + iobs);

		}
		return master;
	} // [getUnifiedConstraints]

	/**
	 * Method that checks the equality of two objects, given one or the other
	 * may be null. If both are null they are the same, if one is null and one
	 * isn't they must be different, otherwise calls the object's equals method
	 * as a comparator.
	 * 
	 * @param o1
	 *            The first object.
	 * @param o2
	 *            The second object.
	 * @return true if both objects are equals according to this method's rules,
	 *         false otherwise.
	 */
	protected boolean equalsOrNull(Object o1, Object o2)
	{
		// if both are null they are the same
		if ((o1 == null) && (o2 == null))
			return true;
		// if one object is null and one is not they are different
		if (((o1 != null) && (o2 == null)) || ((o1 == null) && (o2 != null)))
			return false;
		// both objects _must_ be non-null here
		return o1.equals(o2);
	}

	/**
	 * Convert an old style ngat.phase2.InstrumentConfig to a new ngat.phase2.XInstrumentConfig
	 * @param InstrumentConfig The old style ngat.phase2.InstrumentConfig.
	 * @return The new style XInstrumentConfig.
	 * @exception Exception Thrown when a problem occurs.
	 */
	private XInstrumentConfig translateToNewStyleConfig(InstrumentConfig config) throws Exception
	{

		Detector detector = config.getDetector(0);
		int xbin = detector.getXBin();
		int ybin = detector.getYBin();

		// windows - there are most likely none
		Window[] windows = detector.getWindows();

		XDetectorConfig xdet = new XDetectorConfig();
		xdet.setXBin(xbin);
		xdet.setYBin(ybin);

		// add windows???
		for (int iw = 0; iw < windows.length; iw++)
		{
			Window w = windows[iw];
			if (w != null)
			{
				XWindow xw = new XWindow(w.getXs(), w.getYs(), w.getWidth(), w.getHeight());
				xdet.addWindow(xw);
				System.err.println("Add window: " + xw);
			}
		}

		if (config instanceof CCDConfig)
		{
			CCDConfig ccdConfig = (CCDConfig) config;
			String filters = ccdConfig.getLowerFilterWheel() + "/" + ccdConfig.getUpperFilterWheel();

			XFilterSpec filterSpec = new XFilterSpec();
			filterSpec.addFilter(new XFilterDef(ccdConfig.getLowerFilterWheel()));
			filterSpec.addFilter(new XFilterDef(ccdConfig.getUpperFilterWheel()));

			XImagerInstrumentConfig xim = new XImagerInstrumentConfig(config.getName());
			xim.setFilterSpec(filterSpec);
			xim.setDetectorConfig(xdet);
			xim.setInstrumentName("RATCAM");
			return xim;

		}
		else if (config instanceof OConfig)
		{
			OConfig oConfig = (OConfig) config;
			StringBuffer filterStringBuffer = new StringBuffer();

			XFilterSpec filterSpec = new XFilterSpec();
			filterSpec.addFilter(new XFilterDef(oConfig.getFilterName(1)));
			filterSpec.addFilter(new XFilterDef(oConfig.getFilterName(2)));
			filterSpec.addFilter(new XFilterDef(oConfig.getFilterName(3)));

			XImagerInstrumentConfig xim = new XImagerInstrumentConfig(config.getName());
			xim.setFilterSpec(filterSpec);
			xim.setDetectorConfig(xdet);
			xim.setInstrumentName("IO:O");
			return xim;

		}
		else if (config instanceof LociConfig)
		{
			LociConfig lociConfig = (LociConfig) config;
			StringBuffer filterStringBuffer = new StringBuffer();

			XFilterSpec filterSpec = new XFilterSpec();
			filterSpec.addFilter(new XFilterDef(lociConfig.getFilterName()));

			XImagerInstrumentConfig xim = new XImagerInstrumentConfig(config.getName());
			xim.setFilterSpec(filterSpec);
			xim.setDetectorConfig(xdet);
			xim.setInstrumentName("LOCI");
			return xim;

		}
		else if (config instanceof IRCamConfig)
		{
			IRCamConfig irCamConfig = (IRCamConfig) config;
			String filters = irCamConfig.getFilterWheel();
			XFilterSpec filterSpec = new XFilterSpec();
			filterSpec.addFilter(new XFilterDef(filters));
			XImagerInstrumentConfig xim = new XImagerInstrumentConfig(config.getName());
			xim.setFilterSpec(filterSpec);
			xim.setDetectorConfig(xdet);
			//xim.setInstrumentName("SUPIRCAM");
			xim.setInstrumentName("IO:I");
			return xim;

		}
		else if (config instanceof LiricConfig)
		{
			LiricConfig liricConfig = (LiricConfig) config;

			String filterName = liricConfig.getFilterName();

			XFilterSpec filterSpec = new XFilterSpec();
			filterSpec.addFilter(new XFilterDef(filterName));

			XLiricInstrumentConfig	xLiricConfig = new XLiricInstrumentConfig(config.getName());
			xLiricConfig.setDetectorConfig(xdet);
			xLiricConfig.setInstrumentName("LIRIC");
			if(liricConfig.getNudgematicOffsetSize() == LiricConfig.NUDGEMATIC_OFFSET_SIZE_NONE)
				xLiricConfig.setNudgematicOffsetSize(XLiricInstrumentConfig.NUDGEMATIC_OFFSET_SIZE_NONE);
			else if(liricConfig.getNudgematicOffsetSize() == LiricConfig.NUDGEMATIC_OFFSET_SIZE_SMALL)
				xLiricConfig.setNudgematicOffsetSize(XLiricInstrumentConfig.NUDGEMATIC_OFFSET_SIZE_SMALL);
			else if(liricConfig.getNudgematicOffsetSize() == LiricConfig.NUDGEMATIC_OFFSET_SIZE_LARGE)
				xLiricConfig.setNudgematicOffsetSize(XLiricInstrumentConfig.NUDGEMATIC_OFFSET_SIZE_LARGE);
			else
			{
				throw new Exception(this.getClass().getName()+
						    "translateToNewStyleConfig:LIRIC config:"+
						    liricConfig.getName()+ " has illegal nudgematic offset size "+
						    liricConfig.getNudgematicOffsetSize());
			}
			xLiricConfig.setCoaddExposureLength(liricConfig.getCoaddExposureLength());
			xLiricConfig.setFilterSpec(filterSpec);
			return xLiricConfig;
		}
		else if (config instanceof LowResSpecConfig)
		{

			LowResSpecConfig lowResSpecConfig = (LowResSpecConfig) config;
			double wavelength = lowResSpecConfig.getWavelength();
			XSpectrographInstrumentConfig xspec = new XSpectrographInstrumentConfig(config.getName());
			xspec.setWavelength(wavelength);
			xspec.setDetectorConfig(xdet);
			xspec.setInstrumentName("MEABURN");
			return xspec;

		}
		else if (config instanceof FrodoSpecConfig)
		{
			// this isnt going to work yet anyway, we need some sort of
			// branching effort in the RTML !
			FrodoSpecConfig frodoSpecConfig = (FrodoSpecConfig) config;

			int resolution = frodoSpecConfig.getResolution();
			XDualBeamSpectrographInstrumentConfig xdual = new XDualBeamSpectrographInstrumentConfig(config.getName());
			xdual.setResolution(resolution);
			xdual.setDetectorConfig(xdet);

			int arm = frodoSpecConfig.getArm();
			if (arm == FrodoSpecConfig.RED_ARM)
				xdual.setInstrumentName("FRODO_RED");
			else if (arm == FrodoSpecConfig.BLUE_ARM)
				xdual.setInstrumentName("FRODO_BLUE");
			return xdual;

		}
		else if (config instanceof PolarimeterConfig)
		{
			PolarimeterConfig polarConfig = (PolarimeterConfig) config;

			XPolarimeterInstrumentConfig xpolar = new XPolarimeterInstrumentConfig(config.getName());
			xpolar.setDetectorConfig(xdet);
			xpolar.setInstrumentName("RINGO2");
			return xpolar;

		}
		else if (config instanceof MOPTOPPolarimeterConfig)
		{
			MOPTOPPolarimeterConfig moptopConfig = (MOPTOPPolarimeterConfig) config;

			String filterName = moptopConfig.getFilterName();

			XFilterSpec filterSpec = new XFilterSpec();
			filterSpec.addFilter(new XFilterDef(filterName));

			XMoptopInstrumentConfig	xMoptopConfig = new XMoptopInstrumentConfig(config.getName());
			xMoptopConfig.setDetectorConfig(xdet);
			xMoptopConfig.setInstrumentName("MOPTOP");
			if(moptopConfig.getRotorSpeed() == MOPTOPPolarimeterConfig.ROTOR_SPEED_SLOW)
				xMoptopConfig.setRotorSpeed(XMoptopInstrumentConfig.ROTOR_SPEED_SLOW);
			else if(moptopConfig.getRotorSpeed() == MOPTOPPolarimeterConfig.ROTOR_SPEED_FAST)
				xMoptopConfig.setRotorSpeed(XMoptopInstrumentConfig.ROTOR_SPEED_FAST);
			else
			{
				throw new Exception(this.getClass().getName()+
						    "translateToNewStyleConfig:MOPTOP config:"+
						    moptopConfig.getName()+ " has illegal rotor speed "+
						    moptopConfig.getRotorSpeed());
			}
			xMoptopConfig.setFilterSpec(filterSpec);
			return xMoptopConfig;
		}
		else if (config instanceof RISEConfig)
		{
			RISEConfig riseConfig = (RISEConfig) config;

			XImagerInstrumentConfig xim = new XImagerInstrumentConfig(config.getName());
			xim.setDetectorConfig(xdet);
			xim.setInstrumentName("RISE");
			return xim;

		}
		else if (config instanceof THORConfig)
		{
			THORConfig thorConfig = (THORConfig) config;
			XFilterSpec filterSpec = new XFilterSpec();
			XTipTiltImagerInstrumentConfig xim = new XTipTiltImagerInstrumentConfig(config.getName());
			xim.setFilterSpec(filterSpec);
			xim.setDetectorConfig(xdet);
			xim.setGain(thorConfig.getEmGain());
			xim.setInstrumentName("IO:THOR");
			return xim;

		}
		else if (config instanceof SpratConfig)
		{
			SpratConfig spratConfig = (SpratConfig) config;

			XImagingSpectrographInstrumentConfig xSlitImagingConfig = new XImagingSpectrographInstrumentConfig(
					config.getName());

			switch (spratConfig.getGrismPosition())
			{
			case SpratConfig.POSITION_IN:
				xSlitImagingConfig
						.setGrismPosition(XImagingSpectrographInstrumentConfig.GRISM_IN);
				break;
			case SpratConfig.POSITION_OUT:
				xSlitImagingConfig
						.setGrismPosition(XImagingSpectrographInstrumentConfig.GRISM_OUT);
				break;
			}

			switch (spratConfig.getGrismRotation())
			{
			case 0:
				xSlitImagingConfig
						.setGrismRotation(XImagingSpectrographInstrumentConfig.GRISM_NOT_ROTATED); // i.e.
				// red
				break;
			case 1:
				xSlitImagingConfig
						.setGrismPosition(XImagingSpectrographInstrumentConfig.GRISM_ROTATED); // i.e.
				// blue
				break;
			}

			switch (spratConfig.getSlitPosition())
			{
			case SpratConfig.POSITION_IN:
				xSlitImagingConfig
						.setSlitPosition(XImagingSpectrographInstrumentConfig.SLIT_DEPLOYED);
				break;
			case SpratConfig.POSITION_OUT:
				xSlitImagingConfig
						.setSlitPosition(XImagingSpectrographInstrumentConfig.SLIT_STOWED);
				break;
			}

			xSlitImagingConfig.setInstrumentName("SPRAT");
			XDetectorConfig detectorConfig = new XDetectorConfig();
			detectorConfig.setXBin(1);
			detectorConfig.setYBin(1);
			detectorConfig.setWindows(null);
			xSlitImagingConfig.setDetectorConfig(detectorConfig);

			return xSlitImagingConfig;
		}
		else
		{
			throw new Exception("unknown instrument config:" + config.getClass().getName());
		}

	}

	/**
	 * Returnn a PhaseII XInstrumentConfig describing a slit image configuration for Sprat.
	 * The Grism is out, the slit is in and the detector binned 1.
	 * @return An instance of XInstrumentConfig
	 */
	private XInstrumentConfig getSPRATSlitImageInstrumentConfig()
			throws Exception
	{
		XImagingSpectrographInstrumentConfig xSlitImagingConfig = new XImagingSpectrographInstrumentConfig(
				"slit_image_config");
		xSlitImagingConfig
				.setGrismPosition(XImagingSpectrographInstrumentConfig.GRISM_OUT);
		xSlitImagingConfig
				.setSlitPosition(XImagingSpectrographInstrumentConfig.SLIT_DEPLOYED);

		xSlitImagingConfig.setInstrumentName("SPRAT");
		XDetectorConfig detectorConfig = new XDetectorConfig();
		detectorConfig.setXBin(1);
		detectorConfig.setYBin(1);
		detectorConfig.setWindows(null);
		xSlitImagingConfig.setDetectorConfig(detectorConfig);

		return xSlitImagingConfig;
	}

	/**
	 * Return a PhaseII XInstrumentConfig describing a Sprat configuration containing the specified grating.
	 * The Grism is in, the slit is in and the detector binned 1.
	 * The grism is not rotated if the gratingName contains the work "red".
	 * @param gratingName The name of the grating.
	 * @return An instance of XInstrumentConfig
	 */
	private XInstrumentConfig getUserDefinedSpratCfg(String gratingName)
			throws Exception
	{
		XImagingSpectrographInstrumentConfig xSlitImagingConfig = new XImagingSpectrographInstrumentConfig(
				"user_def_config");
		xSlitImagingConfig
				.setGrismPosition(XImagingSpectrographInstrumentConfig.GRISM_IN);
		xSlitImagingConfig
				.setSlitPosition(XImagingSpectrographInstrumentConfig.SLIT_DEPLOYED);

		if (gratingName == null)
		{
			throw new IllegalArgumentException("Grating name is null");
		}
		int grismRotation;
		if (gratingName.equalsIgnoreCase("red"))
		{
			grismRotation = XImagingSpectrographInstrumentConfig.GRISM_NOT_ROTATED;
		}
		else
		{
			grismRotation = XImagingSpectrographInstrumentConfig.GRISM_ROTATED;
		}

		xSlitImagingConfig.setGrismRotation(grismRotation);

		xSlitImagingConfig.setInstrumentName("SPRAT");
		XDetectorConfig detectorConfig = new XDetectorConfig();
		detectorConfig.setXBin(1);
		detectorConfig.setYBin(1);
		detectorConfig.setWindows(null);
		xSlitImagingConfig.setDetectorConfig(detectorConfig);

		return xSlitImagingConfig;
	}
}
