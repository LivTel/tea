package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
//import javax.net.ssl.*;
//import javax.security.cert.*;
import java.util.*;
import java.text.*;


import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.io.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.net.*;
import ngat.net.camp.*;
import ngat.astrometry.*;
import ngat.phase2.*;
import ngat.message.base.*;
import ngat.message.GUI_RCS.*;

/** 
 * Handles the data returned by the RCS when an observation/group is started, completed, exposes.
 */
public class TelemetryHandler implements CAMPRequestHandler, Logging
{
	public static final String CLASS = "TelemetryHandler";

	/** Default readout time (ms).*/
	private static final long READOUT = 10000L;

	/** Default dprt time (ms).*/
	private static final long DPRT = 5000L;

	IConnection connection;

	TelescopeEmbeddedAgent tea;

	TelemetryInfo telem;
	/**
	 * The logger.
	 */
	Logger logger = null;

	/**
	 * Constructor. Setup logger.
	 * @see #logger
	 */
	public TelemetryHandler(TelescopeEmbeddedAgent tea, 
				IConnection connection, 
				TelemetryInfo telem) {
		this.tea        = tea;
		this.connection = connection;
		this.telem      = telem;
		logger = LogManager.getLogger(this);
	}

	public long getHandlingTime() {return 0L;}

	/** Handle an update in form of: ngat.message.GUI_RCS.TelemetryInfo subclass.
	 *
	 * <ul>
	 *  <li> ObservationInfo       : Record start of group - create UH.
	 *  <li> ReductionInfo         : Record one exposure   - add image data to existing UH.
	 *  <li> ObservationStatusInfo : Record end of group   - send the update using info compiled by UH.
	 * </ul>
	 *
	 */
	public void handleRequest()
	{
		UpdateHandler uh = null;

		logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest","TELH::Received TelemetryRequest: "+telem);
		if (telem instanceof ObservationInfo)
		{
			// we never get these for some reason.
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "TELH::This is an instance of ObservationInfo.");

			// Get the oid
			Observation obs = ((ObservationInfo)telem).getObservation();	    
			if (obs == null)
			{
				logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
					   "TELH::The observation was null.");
				return;
			}
			String oid = obs.getFullPath(); 
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "TELH::ObservationInfo had oid "+oid+".");
			// diddly if we ever got one of these, we could do something here
			// given we are returning update documents ona per-frame basis, what can we do here? 
		}
		else if (telem instanceof ObservationStatusInfo)
		{
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "TELH::This is instance of ObservationStatusInfo.");
			// Either a COMPETED or a FAIL
			String oid = ((ObservationStatusInfo)telem).getObsPathName();
	    
			uh      = tea.getUpdateHandler(oid);
	    
			if (uh == null) {
				logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
					   "TELH::No UpdateHandler found for: "+oid);
			} else {
		
				switch (((ObservationStatusInfo)telem).getCat()) {
					case ObservationStatusInfo.FAILED:
						uh.setObservationFailed();
						break;
					case ObservationStatusInfo.COMPLETED:
						uh.setObservationCompleted();
						break;
				}
			}	    
		}
		else if(telem instanceof ReductionInfo)
		{
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "TELH::This is instance of ReductionInfo.");
	    
			// get the oid and filename from the telemetry
			String oid = ((ReductionInfo)telem).getObsPathName(); 
			String imageFileName = ((ReductionInfo)telem).getFileName();

			RTMLDocument document = tea.getDocument(oid);

			// Not one of ours or weve lost it 
			if (document == null)
			{
				logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
					   "TELH::No document found for oid:"+oid+".");
				return;
			}

			// have we already got an undate handler working on this document?
			// we might have if the next image in a multrun arrives before we have transferred the last one
			uh = tea.getUpdateHandler(oid);

			// if we don't already have a uh, create one
			if(uh == null)
			{
				try
				{
					uh = new UpdateHandler(tea, document);
				} 
				catch (Exception e)
				{
					logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
						   "TELH::Failed to create UH for: "+oid);
					e.printStackTrace();
					return;
				}
			}
			// Add one to the Number of exposures we should get.
			uh.incrementNumberExposures();

			// Try to predict time until obs done message.
			long total = uh.getNumberExposures()* (READOUT + DPRT);
			uh.setExpectedTime(total);
			uh.setObservationId(oid);

			// Register the UH against the obspath -- problem if we get multiple
			// instantiations of an obs (mongroup windows) while still processing
			tea.addUpdateHandler(oid, uh);
	    
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "TELH::UpdateHandler located: Adding image:"+imageFileName);
			uh.addImageFileName(imageFileName);
		}
		else
		{
			// Handle other types of TelemetryInfo here if any....
		}
	
		TELEMETRY_UPDATE_DONE done = new TELEMETRY_UPDATE_DONE("TestReply");
		done.setSuccessful(true);
	
		sendDone(done);
	
	}
    
	public void dispose() {
		if (connection != null) {
			connection.close();
		}
		connection = null;
		telem      = null;
	}

	/** Sends a done message back to client. Breaks conection if any IO errors.*/
	protected void sendDone(TELEMETRY_UPDATE_DONE done) {
		try {
			connection.send(done);
		} catch (IOException iox) {
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "Error sending done: "+iox);
			dispose();
		}
	}

	/** Sends an error message back to client.*/
	protected void sendError(TELEMETRY_UPDATE_DONE done, int errNo, String errMsg) {
		done.setErrorNum(errNo);
		done.setErrorString(errMsg);
		sendDone(done);
	}

}







