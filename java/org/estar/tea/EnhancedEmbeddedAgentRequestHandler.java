package org.estar.tea;

import java.rmi.*;
import java.rmi.server.*;

import ngat.util.logging.*;
import org.estar.rtml.*;

/** 
 * This class implements the TEA RMI interface to the NodeAgent.
 * Placeholder for enhanced EAR functions using new OSS and new Phase2. 
 */
public class EnhancedEmbeddedAgentRequestHandler extends UnicastRemoteObject implements EmbeddedAgentRequestHandler,
		EmbeddedAgentTestHarness 
{
	/**
	 * Stored instance of the telescope embedded agent.
	 */
	private TelescopeEmbeddedAgent tea;
	/**
	 * TRACE logger.
	 */
	Logger alogger;
	/**
	 * Instance of LogGenerator used to generate log messages.
	 */
	LogGenerator logger;

	/** 
	 * Create an EnhancedEmbeddedAgentRequestHandler for the TEA. 
	 * <ul>
	 * <li>The telescope embedded agent instance is tored for later use.
	 * <li>The alogger instance is created as the "TRACE" logger.
	 * <li>The LogGenerator logger is created with system TEA and subsystem Receiver.
	 * </ul>
	 * @param tea The telescope embedded agent, stored for later use.
	 * @see #tea
	 * @see #alogger
	 * @see #logger
	 */
	public EnhancedEmbeddedAgentRequestHandler(TelescopeEmbeddedAgent tea) throws RemoteException 
	{
		this.tea = tea;
		alogger = LogManager.getLogger("TRACE");
		logger = alogger.generate().system("TEA").subSystem("Receiver").
			srcCompClass(this.getClass().getName());
	}

	/**
	 * Handle a scoring request.
	 * <ul>
	 * <li>A new EnhancedDocumentHandler instance is created.
	 * <li>The EnhancedDocumentHandler's handleScore method is invoked.
	 * <li>The returned document is logged and returned.
	 * </ul>
	 * @param doc The RTML document to be scored.
	 * @return The scored reply RTML document, or an error document if an error occured.
	 * @see org.estar.tea.EnhancedDocumentHandler
	 * @see org.estar.tea.EnhancedDocumentHandler#handleScore
	 * @see org.estar.tea.TelescopeEmbeddedAgent#logRTML
	 */
	public RTMLDocument handleScore(RTMLDocument doc) throws RemoteException 
	{

		// make a scoring request to some sort of ScoringCalculator

		// e.g. ScoringCalculator sc = (SC)lookup("rmi://oss/ScoringCalc");
		// IGroup group = extractor.extractGroup(doc); - which prop and Tag are
		// we using
		// Score score = sc.score(group, propID, tagID)
		// then make up a reply doc from score results..

		RTMLDocument reply = null;

		try 
		{

			LogCollator collator = logger.create().info().level(1).extractCallInfo().msg(
					"Sending doc to ScoreDocHandler");
			// tea.xlogRTML(collator, doc);

			EnhancedDocumentHandler sdh = new EnhancedDocumentHandler(tea);

			tea.logRTML(alogger, 1, "ScoreDocHandler scoring doc: ", doc);
			reply = sdh.handleScore(doc);
			alogger.log(1, "ScoreDocHandler returned doc: " + reply);
			tea.logRTML(alogger, 1, "ScoreDocHandler returned doc: ", reply);

			collator.msg("ScoreDocHandler returned document");
			// tea.xlogRTML(collator, reply);

		} 
		catch (Exception e) 
		{
			e.printStackTrace();
			throw new RemoteException("Exception while handling score: " + e);
		}
		return reply;

	}

	/**
	 * Handle a request document (i.e. PhaseII submission or TOOP (target of opportunity) document).
	 * <ul>
	 * <li>A new EnhancedDocumentHandler instance is created.
	 * <li>The EnhancedDocumentHandler's handleRequest method is invoked.
	 * <li>The returned document is logged and returned.
	 * </ul>
	 * @param doc The RTML request document to be processed.
	 * @return The reply RTML document, or an error document if an error occured.
	 * @see org.estar.tea.EnhancedDocumentHandler
	 * @see org.estar.tea.EnhancedDocumentHandler#handleRequest
	 * @see org.estar.tea.TelescopeEmbeddedAgent#logRTML
	 */
	public RTMLDocument handleRequest(RTMLDocument doc) throws RemoteException 
	{
		RTMLDocument reply = null;

		try 
		{

			EnhancedDocumentHandler rdh = new EnhancedDocumentHandler(tea);
			tea.logRTML(alogger, 1, "RequestDocHandler handling request: ", doc);
			reply = rdh.handleRequest(doc);
			alogger.log(1, "RequestDocHandler returned doc: " + reply);
			tea.logRTML(alogger, 1, "RequestDocHandler returned doc: ", reply);

			LogCollator collator = logger.create().info().level(1).extractCallInfo().msg(
					"RequestDocHandler returned document");

			// tea.xlogRTML(collator, reply);

		} 
		catch (Exception e) 
		{
			e.printStackTrace();
			throw new RemoteException("Exception while handling request: " + e);
		}
		return reply;

	}

	/**
	 * Handle an abort request (i.e. delete the PhaseII group associated with this RTML document).
	 * <ul>
	 * <li>A new EnhancedDocumentHandler instance is created.
	 * <li>The EnhancedDocumentHandler's handleAbort method is invoked.
	 * <li>The returned document is logged and returned.
	 * </ul>
	 * @param doc The RTML document to be deleted.
	 * @return The reply RTML document, or an error document if an error occured.
	 * @see org.estar.tea.EnhancedDocumentHandler
	 * @see org.estar.tea.EnhancedDocumentHandler#handleAbort
	 * @see org.estar.tea.TelescopeEmbeddedAgent#logRTML
	 */
	public RTMLDocument handleAbort(RTMLDocument doc) throws RemoteException 
	{
		RTMLDocument reply = null;

		try 
		{
			EnhancedDocumentHandler rdh = new EnhancedDocumentHandler(tea);
			tea.logRTML(alogger, 1, "RequestDocHandler handling abort request: ", doc);
			reply = rdh.handleAbort(doc);
			alogger.log(1, "RequestDocHandler returned abort doc: " + reply);
			tea.logRTML(alogger, 1, "RequestDocHandler returned abort doc: ", reply);

			LogCollator collator = logger.create().info().level(1).extractCallInfo().msg(
					"RequestDocHandler returned abort document.");
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
			throw new RemoteException("Exception while handling abort request: " + e);
		}
		return reply;
	}

	/**
	 * Handle an update request (i.e. request the status of the group associated with the  RTML document with the specified UID).
	 * <ul>
	 * <li>A new EnhancedDocumentHandler instance is created.
	 * <li>The EnhancedDocumentHandler's handleUpdate method is invoked.
	 * <li>The returned document is logged and returned.
	 * </ul>
	 * @param doc The RTML document to be deleted.
	 * @return The reply RTML document, or an error document if an error occured.
	 * @see org.estar.tea.EnhancedDocumentHandler
	 * @see org.estar.tea.EnhancedDocumentHandler#handleUpdate
	 * @see org.estar.tea.TelescopeEmbeddedAgent#logRTML
	 */
	public RTMLDocument handleUpdate(RTMLDocument doc) throws RemoteException 
	{
		RTMLDocument reply = null;

		try 
		{
			EnhancedDocumentHandler rdh = new EnhancedDocumentHandler(tea);
			tea.logRTML(alogger, 1, "RequestDocHandler handling update request: ", doc);
			reply = rdh.handleUpdate(doc);
			alogger.log(1, "RequestDocHandler returned update reply doc: " + reply);
			tea.logRTML(alogger, 1, "RequestDocHandler returned update reply doc: ", reply);

			LogCollator collator = logger.create().info().level(1).extractCallInfo().msg(
					"RequestDocHandler returned update reply document.");
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
			throw new RemoteException("Exception while handling update request: " + e);
		}
		return reply;
	}

	/** Request the system to test ongoing throughput. */
	public void testThroughput() throws RemoteException {

		try {

			SystemTest st = new SystemTest(tea);
			st.runTest();

		} catch (Exception e) {
			throw new RemoteException("Exception while handling testThroughput: " + e);
		}

	}

	/**
	 * Request to return an RTML <i>update</i> document via the normal
	 * NodeAgentAsynchronousResponseHandler mechanism.
	 * 
	 * @param doc
	 *            The source document.
	 * @param howlong
	 *            How long to wait before doing that which needs doing (ms).
	 */
	public void testUpdateCallback(RTMLDocument doc, long howlong) throws RemoteException {

		RTMLIntelligentAgent userAgent = doc.getIntelligentAgent();
		String agid = "Unknown";
		String host = "Unknown";
		int port = -1;

		agid = doc.getUId();
		if (userAgent == null) {
			alogger.log(1, "testUpdateCallback: Warning, User agent was null.");
		} else {
			host = userAgent.getHostname();
			port = userAgent.getPort();
			alogger.log(1, "TestHarness: testUpdateCallback: Sending update to: " + agid + "@ " + host + ":" + port
					+ " in " + howlong + " msec");
		}

		doc.setUpdate();
		doc.addHistoryEntry("TEA:" + tea.getId(), null, "Sending update.");

		final RTMLDocument mydoc = doc;
		final TelescopeEmbeddedAgent mytea = tea;
		final long myhowlong = howlong;
		final String myagent = agid + "@ " + host + ":" + port;
		Runnable r = new Runnable() {
			public void run() {
				try {
					try {
						Thread.sleep(myhowlong);
					} catch (InterruptedException ix) {
					}
					mytea.sendDocumentToIA(mydoc);
					alogger.log(1, "TestHarness: testUpdateCallback: Sent update to: " + myagent);
				} catch (Exception e) {
					alogger.log(1, "An error occurred during TestHarness callback test: " + e);
					e.printStackTrace();
				}
			}
		};

		Thread thread = new Thread(r);
		thread.start();

	}

}
