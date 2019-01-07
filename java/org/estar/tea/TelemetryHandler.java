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
public class TelemetryHandler implements CAMPRequestHandler, Logging {

    public static final String CLASS = "TELH";
    
    /** Default readout time (ms).*/
    private static final long READOUT = 10000L;
    
    /** Default dprt time (ms).*/
    private static final long DPRT = 5000L;

    /** CAMP Connection used.*/
    IConnection connection;
    
    /** Reference to the TEA.*/
    TelescopeEmbeddedAgent tea;
    
    /** The encapsulated Telemetry Info.*/
    TelemetryInfo telem;

    /** Class logger. */
    Logger logger = null;

    /**
     * Create a TelemetryHandler for the supplied conneciton and telemetry info.
     * @param connection The Connection to use for reply.
     * @param telem      The encapsulated Telemetry Info.
     * @see #logger
     */
    public TelemetryHandler(TelescopeEmbeddedAgent tea, 
			    IConnection            connection, 
			    TelemetryInfo          telem) {
	this.tea        = tea;
	this.connection = connection;
	this.telem      = telem;
	logger = LogManager.getLogger("TRACE");
    }
    
    /** Returns the handling time - ### fudged.*/
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
    public void handleRequest() {

	AgentRequestHandler arq = null;

	TELEMETRY_UPDATE_DONE done = new TELEMETRY_UPDATE_DONE("TestReply");
	
	logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest","TELH::Received TelemetryRequest: "+telem);

	if 
	    (telem instanceof ReductionInfo) {
	   
	    logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
		       "TELH::This is instance of ReductionInfo.");
	    
	    // get the oid and filename from the telemetry
	    String oid = ((ReductionInfo)telem).getObsPathName();

	    Path   obsPath = new Path(oid);
	    String gid     = obsPath.getProposalPathByName()+"/"+obsPath.getGroupByName();
	    String obsId   = obsPath.getObservationByName();

	    int iobs = 0;
	    // if we cant extract the obsid were stuffed
	    try {
		iobs = Integer.parseInt(obsId);
	    } catch (Exception px) {
		// send telemetry reply to RCS
		done.setSuccessful(false);
		sendDone(done);		
		return;
	    }
	    
	    // The observation id is the group id with a /obsid on the end e.g.:
	    // /LT_Phase2_001/PATT/keith.horne/PL04B17/000086:UA:v1-15:run#10:user#agent/ExoPlanetMonitor
	    logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
		       "TELH::ReductionInfo has oid: "+oid+".");
	    
	    String imageFileName = ((ReductionInfo)telem).getFileName();
	   
	    arq  = tea.getUpdateHandler(gid);
	    if (arq == null) {
		logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
			   "TELH::No AgentRequestHandler found for: "+gid+" - Not one of ours");
	    } else {

		try {
		    if (arq.wantsReducedImagesOnly(iobs)) {
			
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "TELH::UpdateHandler located: Adding image:"+imageFileName);
			arq.addImageInfo(new ImageInfo(iobs, imageFileName));
		    }
		} catch (Exception dx) {
		    done.setSuccessful(false);
		    sendDone(done);
		    return;
		}
	    }
	} else if
	      (telem instanceof ExposureInfo) {
	    
	    logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
                       "TELH::This is instance of ExposureInfo.");
	    
            // get the oid and filename from the telemetry
            String oid = ((ExposureInfo)telem).getObsPathName();
	    
            Path obsPath = new Path(oid);
            String gid = obsPath.getProposalPathByName()+"/"+obsPath.getGroupByName();
            String obsId = obsPath.getObservationByName();
	    
            int iobs = 0;
            // if we cant extract the obsid were stuffed
            try {
                iobs = Integer.parseInt(obsId);
            } catch (Exception px) {
                // send telemetry reply to RCS
                done.setSuccessful(false);
                sendDone(done);
                return;
            }
	    
            // The observation id is the group id with a /obsid on the end e.g.:
            // /LT_Phase2_001/PATT/keith.horne/PL04B17/000086:UA:v1-15:run#10:user#agent/ExoPlanetMonitor
            logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
                       "TELH::ExposureInfo has oid: "+oid+".");
	    
            String imageFileName = ((ExposureInfo)telem).getFileName();
	    
            arq  = tea.getUpdateHandler(gid);
            if (arq == null) {
                logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
                           "TELH::No AgentRequestHandler found for: "+gid+" - Not one of ours");
            } else {
		
		try {
		    if (! arq.wantsReducedImagesOnly(iobs)) {
			
			logger.log(INFO, 1, CLASS, tea.getId(),"handleRequest",
				   "TELH::UpdateHandler located: Adding image:"+imageFileName);
			arq.addImageInfo(new ImageInfo(iobs, imageFileName));
		    }
		} catch (Exception dx) {
		    done.setSuccessful(false);
		    sendDone(done);
		    return;
		}

            }

	} else {
	    // Handle other types of TelemetryInfo here if any....
	}
	
	// send telemetry reply to RCS
	done.setSuccessful(true);	
	sendDone(done);
	
    }
    
    /** Dispose this handler and close connection from RCS Telemtry sender.*/
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







