package org.estar.tea;

import java.rmi.*;
import java.rmi.server.*;

import ngat.util.logging.*;
import org.estar.rtml.*;

/** Placeholder for enhanced EAR functions using new OSS and new Phase2. */
public class EnhancedEmbeddedAgentRequestHandler extends UnicastRemoteObject implements EmbeddedAgentRequestHandler,
		EmbeddedAgentTestHarness {

	private TelescopeEmbeddedAgent tea;

	Logger alogger;

	LogGenerator logger;

	/** Create an EnhancedEmbeddedAgentRequestHandler for the TEA. */
	public EnhancedEmbeddedAgentRequestHandler(TelescopeEmbeddedAgent tea) throws RemoteException {
		this.tea = tea;
		alogger = LogManager.getLogger("TRACE");
		logger = alogger.generate().system("TEA").subSystem("Receiver").srcCompClass(this.getClass().getName());
	}

	/**
	 * Handle a scoring request.
	 * 
	 * @param doc
	 *            The RTML document.
	 * @return The scored RTML document.
	 * @see org.estar.tea.TelescopeEmbeddedAgent#logRTML
	 */
	public RTMLDocument handleScore(RTMLDocument doc) throws RemoteException {

		// make a scoring request to some sort of ScoringCalculator

		// e.g. ScoringCalculator sc = (SC)lookup("rmi://oss/ScoringCalc");
		// IGroup group = extractor.extractGroup(doc); - which prop and Tag are
		// we using
		// Score score = sc.score(group, propID, tagID)
		// then make up a reply doc from score results..

		RTMLDocument reply = null;

		try {

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

		} catch (Exception e) {
			e.printStackTrace();
			throw new RemoteException("Exception while handling score: " + e);
		}
		return reply;

	}

	/**
	 * Handle a request request.
	 * 
	 * @param doc
	 *            The RTML document.
	 * @see org.estar.tea.TelescopeEmbeddedAgent#logRTML
	 */
	public RTMLDocument handleRequest(RTMLDocument doc) throws RemoteException {

		RTMLDocument reply = null;

		try {

			EnhancedDocumentHandler rdh = new EnhancedDocumentHandler(tea);
			tea.logRTML(alogger, 1, "RequestDocHandler handling request: ", doc);
			reply = rdh.handleRequest(doc);
			alogger.log(1, "RequestDocHandler returned doc: " + reply);
			tea.logRTML(alogger, 1, "RequestDocHandler returned doc: ", reply);

			LogCollator collator = logger.create().info().level(1).extractCallInfo().msg(
					"RequestDocHandler returned document");

			// tea.xlogRTML(collator, reply);

		} catch (Exception e) {
			e.printStackTrace();
			throw new RemoteException("Exception while handling request: " + e);
		}
		return reply;

	}

	/**
	 * Handle an abort request.
	 * 
	 * @param doc
	 *            The RTML document.
	 * @see org.estar.tea.TelescopeEmbeddedAgent#logRTML
	 */
	public RTMLDocument handleAbort(RTMLDocument doc) throws RemoteException {

		return null;

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
