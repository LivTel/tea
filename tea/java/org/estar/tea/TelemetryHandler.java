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
		logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest","TELH::Received TelemetryRequest: "+telem);
		if (telem instanceof ObservationInfo)
		{
			// we never these for some reason.
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "TELH::This is an instance of ObservationInfo.");

			// Create a UH for this oid and add to TEA's agentMap.
			Observation obs = ((ObservationInfo)telem).getObservation();	    
			if (obs == null)
			{
				logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
					   "TELH::The observation was null.");
				return;
			}
			String oid = obs.getFullPath(); 
	 
			RTMLDocument document = tea.getDocument(oid);

			// Not one of ours or weve lost it 
			if (document == null)
			{
				logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
					   "TELH::No document found for oid:"+oid+".");
				return;
			}
			UpdateHandler uh = null;

			try {
				uh = new UpdateHandler(tea, document);
			} catch (Exception e) {
			        logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
					   "TELH::Failed to create UH for: "+oid);
				e.printStackTrace();
				return;
			}

			// Number of exposures we should get.
			uh.setNumberExposures(obs.getNumRuns());

			// Try to predict time until obs done message.
			long total = obs.getNumRuns()* ((long)obs.getExposeTime() + READOUT + DPRT);
			uh.setExpectedTime(total);

			uh.setObservation(obs);
			// ###since weve told it the obs it could work most of the above anyway??

			// Register the UH against the obspath -- problem if we get multiple
			// instantiations of an obs (mongroup windows) while still processing
			tea.addUpdateHandler(oid, uh);

		} else if (telem instanceof ObservationStatusInfo) {
	    
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "TELH::This is instance of ObservationStatusInfo.");
			// Either a COMPETED or a FAIL
			String oid = ((ObservationStatusInfo)telem).getObsPathName();
	    
			UpdateHandler uh      = tea.getUpdateHandler(oid);
	    
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
	    
			// Push this to the already created ? ARQ
			// it may pickup via its thread the image file via SFX
	    
			String oid = ((ReductionInfo)telem).getObsPathName(); 
			String imageFileName = ((ReductionInfo)telem).getFileName();
	    
			UpdateHandler uh      = tea.getUpdateHandler(oid);
	    
			if (uh == null) {
				logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
					   "TELH::No UpdateHandler found for: "+oid);
			} else {
				logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
					   "TELH::UpdateHandler located: Adding image:"+imageFileName);
				uh.addImageFileName(imageFileName);
			}

	   

			// 	RTMLObservation obs = document.getObservation(0);
	
			// 	try {
			// 	    // obs.setImageDataURL(tea.getImageWebUrl()+"/"+imageFileName);	    
			// 	    arq.sendDocUpdate(document, "update");
			// 	} catch (Exception rx) {
			// 	    System.err.println("Error setting up update document: "+rx);
			// 	}
			//     }
    
		} else {
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







