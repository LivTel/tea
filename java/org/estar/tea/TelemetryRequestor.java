package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.*;
import java.text.*;


import org.estar.astrometry.*;
import org.estar.rtml.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.net.*;
import ngat.net.camp.*;
import ngat.astrometry.*;

import ngat.message.base.*;
import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;

/**
 * This control thread requests the RCS to send telemetry of the required types.
 * The telemetry is requested periodically, in case the tea/RCS goes down at some point.
 */
public class TelemetryRequestor extends ControlThread implements Logging
{
	/**
	 * Class constant for logging.
	 */
	public static final String CLASS = "TelemetryRequestor";
	/** Command request counter - increments each time a TELEM command is sent.*/
	private static int cc = 0;

	/** Polling time (ms).*/
	private long time;

	/** The TEA.*/
	private TelescopeEmbeddedAgent tea;
    
	/** Connection Setup Information.*/
	private ConnectionSetupInfo conset;
	/**
	 * Logger.
	 */
	Logger logger = null;

	/** Create a TelemetryRequestor. Creates a new ConnectionSetupInfo. Sets up logger.
	 * @param tea  The TEA.
	 * @param time Polling time (ms).
	 * @see #conset
	 * @see #logger
	 */
	public TelemetryRequestor(TelescopeEmbeddedAgent tea, long time) {
		super("TELREQ", true);
		this.tea = tea;
		this.time = time;
		conset = new ConnectionSetupInfo(System.currentTimeMillis(), 
						 ConnectionSetupInfo.CAMP, 
						 tea.getTelemetryHost(), 
						 tea.getTelemetryPort());
		logger = LogManager.getLogger("TRACE");
	}
    
	protected void initialise() {}


	/**
	 * This method is called in a loop.
	 * It creates a RequestorClient to request the telemetry from the RCS (ina new thread). It then sleeps.
	 */
	protected void mainTask()
	{
		TELEMETRY tel = new TELEMETRY("TELREQ:"+(++cc));
		tel.setConnect(conset);
		tel.setClientId("TEA");
	
		Vector wants = new Vector();
		// I am not receiving ObservationInfo when directly requesting it.
		wants.add(ExposureInfo.class);
		wants.add(ReductionInfo.class);
		wants.add(ObservationInfo.class);
		wants.add(ObservationStatusInfo.class);

		tel.setWants(wants);

		System.err.println("TelemetryRequestor:mainTask:Contacting OCC on: "+
				 tea.getCtrlHost()+":"+tea.getCtrlPort());
		logger.log(INFO,1,CLASS,tea.getId(),"mainTask",
			   "Contacting OCC on: "+tea.getCtrlHost()+":"+tea.getCtrlPort());
		final RequestorClient client = new RequestorClient(new SocketConnection(tea.getCtrlHost(), tea.getCtrlPort()), tel);
		(new Thread(client)).start();
	
		try {Thread.sleep(time);} catch (InterruptedException ix) {}
    
	}

	protected void shutdown() {}

	/** Request sender.*/
	private class RequestorClient extends CAMPClient implements CAMPResponseHandler {

		//String host;
		//int    port;

		public RequestorClient(IConnection connection, GUI_TO_RCS command) {
			super(connection, command);
			// host = ((TELEMETRY)command).getConnect().host;
			//port = ((TELEMETRY)command).getConnect().port;
		}
	
		/** handle telemetry request response.*/
		public void handleUpdate(COMMAND_DONE update, IConnection connection) {
	    
			logger.log(INFO,1,CLASS,tea.getId(),"handleUpdate",command.getId()+" Closing connection.");
			if (connection != null)
				connection.close();
			connection = null;  

			if (! (update instanceof TELEMETRY_DONE)) {
				logger.log(INFO,1,CLASS,tea.getId(),"handleUpdate",
					   command.getId()+" CAMP Error: Unexpected class: "+update);
				return;
			}

			if (update instanceof TELEMETRY_DONE) {
		
				TELEMETRY_DONE sd = (TELEMETRY_DONE)update;
		
				if (update.getSuccessful()) {
					// Dont care at the mo.
					logger.log(INFO,1,CLASS,tea.getId(),"handleUpdate",
						   command.getId()+" Update successful.");
				} else {
					// Dont care at the mo.
					int    errno  = update.getErrorNum();
					String errmsg = update.getErrorString();
					logger.log(INFO,1,CLASS,tea.getId(),"handleUpdate",
						   command.getId()+" Update NOT successful:"+errno+":"+errmsg+".");
				}
			}
		}
	
		/** Handle failed telemtry request.*/
		public void failed(Exception e, IConnection connection)
		{
	    
			logger.log(INFO,1,CLASS,tea.getId(),"failed"+command.getId()+"TELEMETRY:CAMP Error: "+e);  
			logger.dumpStack(1,e);
			if (connection != null)
				connection.close();	
			connection = null;  
		}	
	} // [RequestorClient]

    
} // [TelemetryRequestor]

