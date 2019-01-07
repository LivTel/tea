package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.*;
import java.text.*;
import javax.net.ssl.*;
import java.rmi.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.oss.model.IPhase2Model;
import ngat.oss.transport.RemotelyPingable;
import ngat.phase2.*;
import ngat.net.*;
import ngat.net.camp.*;
import ngat.astrometry.*;

import ngat.message.base.*;
import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;

public class SystemTest implements Logging {

	/** Classname for logging. */
	public static final String CLASS = "SYST";

	/** Reference to the TEA. */
	TelescopeEmbeddedAgent tea;

	/** Class logger. */
	private Logger logger;

	/** Handler ID. */
	private String cid;

	/** ST counter. */
	private static int cc = 0;

	/** Create a SystemTest linked to TEA. */
	public SystemTest(TelescopeEmbeddedAgent tea) {
		this.tea = tea;

		logger = LogManager.getLogger("TRACE");

		cc++;
		cid = "SYST/" + cc;
	}

	/** Run a system test. */
	public void runTest() throws Exception {

		// assume they all work till were told otherwise.
		boolean ossFail = false;
		String ossError = null;
		boolean tocsFail = false;
		String tocsError = null;

		logger.log(INFO, 1, CLASS, cid, "runTest", "Starting system test...");

		/*
		 * // Test the OSS connection. NETWORK_TEST test = new NETWORK_TEST("");
		 * test.setClientDescriptor(new ClientDescriptor("EmbeddedAgent",
		 * ClientDescriptor.ADMIN_CLIENT, ClientDescriptor.ADMIN_PRIORITY));
		 * test.setCrypto(new Crypto("TEA")); test.setBlocks(1000);
		 * 
		 * logger.log(INFO,1,CLASS,cid,"runTest",
		 * "Starting OSS connection test...");
		 * 
		 * // Send it onwards JMSCommandHandler client = new
		 * JMSCommandHandler(tea.getConnectionFactory(), test,
		 * tea.getOssConnectionSecure());
		 * 
		 * client.send();
		 * 
		 * // wait reply. if (client.isError()) {
		 * logger.log(INFO,1,CLASS,cid,"runTest"
		 * ,"Error while running OSS connection test: "+
		 * client.getErrorMessage()); ossFail = true; ossError =
		 * client.getErrorMessage(); } else { NETWORK_TEST_DONE test_done =
		 * (NETWORK_TEST_DONE)client.getReply();
		 * logger.log(INFO,1,CLASS,cid,"runTest", "OSS connection test ok"); }
		 */

		logger.log(INFO, 1, CLASS, cid, "runTest", "Starting TOCS connection test...");

		// TODO Insert code to test the TOCS connection - some harmless command.
		// Try and get TOCSessionManager context.
		TOCSessionManager sessionManager = TOCSessionManager.getSessionManagerInstance(tea);
		try {
			sessionManager.ping();
			logger.log(INFO, 1, CLASS, cid, "runTest", "TOCS connection test ok");
		} catch (Exception e) {
			tocsFail = true;
			tocsError = e.getMessage();
		}

		// here we ping the base-models (basically just the p2)
		try {
			IPhase2Model phase2 = (IPhase2Model) Naming.lookup(tea.getPhase2ModelUrl());
			RemotelyPingable pinger = (RemotelyPingable) phase2;
			pinger.ping();
		} catch (Exception e) {
			ossFail = true;
			ossError = e.getMessage();
		}
		
		// There are a choice of return messages...
		String errStr = null;
		if (ossFail)
			errStr = "Error connecting to OSS: "+ossError;

		if (tocsFail)
			errStr += " Error connecting to TOCS: " + tocsError;

		if (ossFail || tocsFail)
			throw new RemoteException("SystemTest: " + cc + " " + errStr);

	}

}
