package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
//import javax.net.ssl.*;
//import javax.security.cert.*;
import java.util.*;
import java.text.*;
import javax.net.ssl.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.io.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.phase2.*;
import ngat.net.*;
import ngat.astrometry.*;

public class DocChecker {

    public void check(RTMLDocument document) throws RTMLException {

	RTMLContact contact = document.getContact();
	if (contact == null)
	    throw new RTMLException("No contact details");
	String userId = contact.getUser();

	// The Proposal ID.
	RTMLProject project = document.getProject();
	if (project == null)
	    throw new RTMLException("No project details");
	String proposalId = project.getProject();
	if (proposalId == null)
	    throw new RTMLException("No proposal ID");

	// We will use this as the Group ID.
	RTMLIntelligentAgent userAgent = document.getIntelligentAgent();
	if (userAgent == null)
	    throw new RTMLException("No user agent details");
	String requestId = userAgent.getId();
	if (requestId == null)
	    throw new RTMLException("No request ID");
	
	// Extract the Observation request.
	
	RTMLObservation obs = document.getObservation(0);
	if (obs == null)
	    throw new RTMLException("No observation details");

	// Extract params
	RTMLTarget target = obs.getTarget();
	if (target == null)
	    throw new RTMLException("No target details");

	RA  ra  = target.getRA();
	Dec dec = target.getDec();
	if (ra == null || dec == null)
	    throw new RTMLException("No Ra/Dec details");

	String targetId = target.getName();
	if (targetId == null)
	    throw new RTMLException("No target ID");

	RTMLSchedule sched = obs.getSchedule();
	if (sched == null)
	    throw new RTMLException("No sched details");

	String expy = sched.getExposureType();
	String expu = sched.getExposureUnits();
	double expt = sched.getExposureLength();

	if (expu.equals("s") ||
	    expu.equals("sec") ||
	    expu.equals("secs") ||
	    expu.equals("second") ||
	    expu.equals("seconds"))
	    expt = expt*1.0;
	else if
	    (expu.equals("ms") ||
	     expu.equals("msec") ||
	     expu.equals("msecs") ||
	     expu.equals("millisecond") ||
	     expu.equals("milliseconds"))
	    expt = expt/1000.0;
	else if
	    (expu.equals("min") ||
	     expu.equals("mins"))
	    expt = 60.0*expt;
	else {
	    sendError(document, "Did not understand time units: "+expu);
	    return;
	}

	// Extract filter info.
	RTMLDevice dev = document.getDevice();

	if (dev != null) {

	    String type = dev.getType();
	    String filterString = dev.getFilterType();

	    if (type.equals("camera")) {

		// We will need to extract the instrument name from the type field.
		//String instName = agent.getConfig().getProperty("camera.instrument", "Ratcam");

		// Check valid filter and map to UL combo

		filter = agent.getFilterMap().
		    getProperty(filterString);

		if (filter == null) {
		    sendError(document, "Unknown filter: "+filterString);
		    return;
		}
	    } else {
		sendError(document, "Device is not a camera");
		return;
	    }
	} else {
	    sendError(document, "Device not set");
	    return;
	}

	

    }

}
