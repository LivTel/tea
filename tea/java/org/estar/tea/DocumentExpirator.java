package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
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

import ngat.message.base.*;
import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;


public class DocumentExpirator extends ControlThread {

    /** Polling time.*/
    long time;

    /** Offset to allow after expiry.*/
    long offset;

    TelescopeEmbeddedAgent tea;


    public DocumentExpirator(TelescopeEmbeddedAgent tea, long time, long offset) {
	super("DOCEXP", true);
	this.tea    = tea;
	this.time   = time;
	this.offset = offset;
    }

    protected void initialise() {}
    
    
    protected void mainTask() {
	
	// Check to see if there are any expired docs still kept by the TEA.

	RTMLDocument    doc   = null;
	RTMLObservation obs   = null;
	RTMLSchedule    sched = null;

	AgentRequestHandler arq = null;

	Iterator keys = tea.listDocumentKeys();
	while (keys.hasNext()) {
	    String key = (String)keys.next();
	    doc = tea.getDocument(key);
	    obs = doc.getObservation(0);
	    if (obs != null) {
		sched = obs.getSchedule();
		if (sched != null) {
		    Date endDate = sched.getEndDate();
		    if (endDate != null) {
			
			long end = endDate.getTime();
			if (end < (System.currentTimeMillis()-offset)) {
			    
			    // Expire the Doc - simple for now needs FG/MG stuff
			    
			    arq = new AgentRequestHandler(tea);
			    arq.sendError(doc, "Document expired on "+endDate.toGMTString());
			  
			}
		    }
		}
	    }
	}
	try {Thread.sleep(time);} catch (InterruptedException ix) {}
    }
    
    protected void shutdown() {}
    

}
