// $Header: /space/home/eng/cjm/cvs/tea/java/org/estar/tea/RequestDocumentHandler.java,v 1.22 2008-08-21 10:02:56 eng Exp $
package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
//import javax.net.ssl.*;
//import javax.security.cert.*;
import java.util.*;
import java.text.*;
import javax.net.ssl.*;
import java.lang.reflect.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.io.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.phase2.*;
import ngat.net.*;
import ngat.net.camp.*;
import ngat.astrometry.*;

import ngat.message.base.*;
import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;

/** Handles a <i>request</i> request.*/
public class RequestDocumentHandler implements Logging {
       
    /** Classname for logging.*/
    public static final String CLASS = "RDH";

    /** Default maximum (worst) seeing allowed (asec).*/
    public static final double DEFAULT_SEEING_CONSTRAINT = 1.3;

    /** Reference to the TEA.*/
    TelescopeEmbeddedAgent tea;
    
    /** Class logger.*/
    private Logger logger;
    
    /** Handler ID.*/
    private String cid; 
    
    private static int cc = 0;
    
    
    /** Create a RequestDocumentHandler using the supplied IO parameters.
     * @param tea     The TEA.
     * @param io      The eSTARIO.
     * @param handle  Globus IO Handle for the connection.
     */
    public RequestDocumentHandler(TelescopeEmbeddedAgent tea) {
	this.tea    = tea;
	logger = LogManager.getLogger("TRACE");
	cc++;
	cid = "RDH/"+cc;
    }
    
    /** Called to handle an incoming observation request document.
     * Attempts to add a group to the OSS Phase2 DB.
     * @param document The RTML request document.  
     * @throws Exception if anything goes wrong.
     */
    public RTMLDocument handleRequest(RTMLDocument document) throws Exception {
	
	long now = System.currentTimeMillis();
	
	
	if (document.isTOOP()) {
	    // Try and get TOCSessionManager context.
	    TOCSessionManager sessionManager = TOCSessionManager.getSessionManagerInstance(tea,document);
	    // add the document to the TOCSessionManager
	    // if it succeeds addDocument sets the type to "confirmation".
	    document = sessionManager.addDocument(document);
	    return document;
	} 
	
	// Non toop
	
	Phase2GroupExtractor p2x = new Phase2GroupExtractor(tea);
	Group group = p2x.extractGroup(document);

	System.err.println("Extracted group : "+group);


	String proposalPathName = group.getPath();
	
	// setup mappings
	Map smap = new HashMap();
	Map imap = new HashMap();
	Map tmap = new HashMap();
	
	
	Iterator iobs = group.listAllObservations();
	while (iobs.hasNext()) {
	    
	    Observation obs = (Observation)iobs.next();
	    System.err.println("Observation "+obs);

	    // Add the required target...
	    Source source = obs.getSource();
	    
	    ADD_SOURCE addsource = new ADD_SOURCE(tea.getId()+":"+document.getUId());
	    addsource.setProposalPath(new Path(proposalPathName));
	    addsource.setSource(source);
	    addsource.setReplace(false);
	     
	    addsource.setClientDescriptor(new ClientDescriptor("EmbeddedAgent", 
							       ClientDescriptor.ADMIN_CLIENT,
							       ClientDescriptor.ADMIN_PRIORITY));
	    addsource.setCrypto(new Crypto("TEA"));
	    
	    addsource.setTransactionPriority(0);

	    JMSCommandHandler client = new JMSCommandHandler(tea.getConnectionFactory(), 
							     addsource, 
							     tea.getOssConnectionSecure());
	    client.send();
	    
	    if (client.isError()) {	
		logger.log(INFO, 1, CLASS, cid,"executeRequest","Reply was: "+client.getReply());
		if (client.getReply() != null &&
		    client.getReply().getErrorNum() == ADD_SOURCE.SOURCE_ALREADY_DEFINED) {
		    logger.log(INFO, 1, CLASS, cid,"executeRequest",
			       "Will be using existing target: "+source.getName());
		} else {
		    logger.log(INFO,1,CLASS,cid,"handleRequest",
			       "Internal error during ADD_SOURCE: "+client.getErrorMessage());
		    return setError(document,RTMLHistoryEntry.REJECTION_REASON_OTHER,
				    "Internal error during ADD_SOURCE: "+client.getErrorMessage());
		}
	    }
	    
	    // Add the required inst config...
	    InstrumentConfig config = obs.getInstrumentConfig();
	    
	    ADD_INST_CONFIG addcfg = new ADD_INST_CONFIG(tea.getId()+":"+document.getUId());
	    addcfg.setProposalPath(new Path(proposalPathName));
	    addcfg.setConfig(config);
	    addcfg.setReplace(false);
	    
	    addcfg.setClientDescriptor(new ClientDescriptor("EmbeddedAgent", 
							    ClientDescriptor.ADMIN_CLIENT,
							    ClientDescriptor.ADMIN_PRIORITY));
	    addcfg.setCrypto(new Crypto("TEA"));
	    
	    addcfg.setTransactionPriority(0);
	    
	    JMSCommandHandler client2 = new JMSCommandHandler(tea.getConnectionFactory(), 
							      addcfg, 
							      tea.getOssConnectionSecure());
	    
	    client2.send();
	    
	    if (client2.isError()) {	
		logger.log(INFO, 1, CLASS, cid,"executeRequest","Reply was: "+client2.getReply());
		if (client2.getReply() != null &&
		    client2.getReply().getErrorNum() == ADD_INST_CONFIG.CONFIG_ALREADY_DEFINED) {
		    logger.log(INFO, 1, CLASS, cid,"executeRequest",
			       "Will be using existing config: "+config.getName());
		} else {
		    logger.log(INFO,1,CLASS,cid,"handleRequest",
			       "Internal error during ADD_CONFIG: "+client2.getErrorMessage());
		    return setError(document,RTMLHistoryEntry.REJECTION_REASON_OTHER,
				    "Internal error during ADD_CONFIG: "+client2.getErrorMessage());
		}
	    }

	    smap.put(obs, source.getName());
	    imap.put(obs, config.getName());		
	    tmap.put(obs, "DEFAULT");

	} // next obs
	
	ADD_GROUP addgroup = new ADD_GROUP(tea.getId()+":"+document.getUId());
	addgroup.setClientDescriptor(new ClientDescriptor("EmbeddedAgent",
							  ClientDescriptor.ADMIN_CLIENT,
							  ClientDescriptor.ADMIN_PRIORITY));
	addgroup.setCrypto(new Crypto("TEA"));
	
	addgroup.setProposalPath(new Path(proposalPathName));
	addgroup.setGroup(group);
	
	addgroup.setSrcMap(smap);
	addgroup.setIcMap(imap);
	addgroup.setTcMap(tmap);
	addgroup.setTransactionPriority(0);
	
	JMSCommandHandler client = new JMSCommandHandler(tea.getConnectionFactory(), 
				       addgroup, 
				       tea.getOssConnectionSecure());
	
	client.send();
		
	if (client.isError()) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest","Internal error during ADD_GROUP: "+
		       client.getErrorMessage());
	    return setError(document,RTMLHistoryEntry.REJECTION_REASON_OTHER,"Internal error during ADD_GROUP: "+
			    client.getErrorMessage());
	}

	// Ok setup the ARQ now
	// ### this maybe should go in a seperate method to be called by the CH
	// ### or the CH calls these methods itself one-by-one.

	AgentRequestHandler arq = new  AgentRequestHandler(tea, document);

	// Extract the observation path - we already have it anyway.
	// observation needs to be declared global.- look at UH which defines on ObsInfo.
	Path groupPath = new Path(group.getFullPath());	
	String gid = groupPath.getProposalPathByName()+"/"+group.getName();
	arq.setGid(gid);
	arq.setName(document.getUId());
	arq.setARQId(tea.getId()+"/"+arq.getName());

	// Get a unique file Name off the TEA.
	File file = new File(tea.createNewFileName(gid));

	// Its the one we will use.
	arq.setDocumentFile(file);

	// Set the current request as our basedoc.
	arq.setBaseDocument(document);

	// Save it to the file - we could do this ourself..
	tea.saveDocument(document, file);
	logger.log(INFO, 1, CLASS, cid, "handleRequest",
		   "Saving base document to: "+file.getPath());

	// Register as handler for the current obs.
	tea.registerHandler(gid, arq);
	logger.log(INFO, 1, CLASS, cid, "handleRequest",
		   "Registered running ARQ for: "+gid+" Using file: "+file.getPath());


	// Initialize and start the ARQ as UpdateHandler. If the ARQ does not successfully
	// prepare for UpdateHandling it will not be started and we get an exception.
	try {
	    arq.prepareUpdateHandler();
	    arq.start();
	} catch (Exception e) {
	    logger.dumpStack(1, e);
	}
	
	// We still send a confirm, even if we cant start the ARQ correctly as the obs is in the DB.
	document.setRequestReply();
	document.addHistoryEntry("TEA:"+tea.getId(),null,"Request confirmed.");
	return document;
	
    }
    
    /** 
     * Set the error message in the supplied document.
     * @param document The document to modify.
     * @param rejectionReason The reason the abort request was rejected. Must be a standard string from 
     *       RTMLHistoryEntry.
     * @param errorMessage The error message.
     * @throws Exception if anything goes wrong.
     * @return The modified <i>reject</i> document.
     * @see org.estar.rtml.RTMLHistoryEntry
     */
    private RTMLDocument setError(RTMLDocument document,String rejectionReason,String errorMessage) throws Exception
    {
	document.setReject();
	document.addHistoryRejection("TEA:"+tea.getId(),null,rejectionReason,errorMessage);
	document.setErrorString(errorMessage); 
	return document;
    }

} // [RequestDocumentHandler]


//
// $Log: not supported by cvs2svn $
// Revision 1.21  2008/07/25 15:31:33  cjm
// Changed AgentRequestHandler setId to setARQId.
//
// Revision 1.20  2008/07/25 15:26:27  cjm
// Steve made some priority changes here.
//
// Revision 1.19  2008/05/27 13:41:09  cjm
// Changes relating to RTML parser upgrade.
// RTML setType calls replaced by equivalent RTMLDocument methods for version independant values.
// RTML document history calls added.
// getUId used for unique Id retrieval.
// isTOOP used for determining target of oppurtunity.
//
// Revision 1.18  2008/04/17 11:05:09  snf
// typo
//
// Revision 1.17  2008/04/17 11:04:06  snf
// added handling of autoguider based on length of exposure
//
// Revision 1.16  2008/03/27 12:09:57  snf
// added acquire mode for lowresspec thingy
//
// Revision 1.15  2007/09/27 08:25:13  snf
// *** empty log message ***
//
// Revision 1.14  2007/08/06 09:25:03  snf
// checkin
//
// Revision 1.13  2007/04/04 09:55:56  snf
// changed arq.name to reqId which is the group ID
//
// Revision 1.12  2007/04/04 08:51:12  snf
// changed arq name to oid
//
// Revision 1.11  2007/02/20 12:39:26  snf
// changed comments around priority settings.
//
// Revision 1.10  2007/01/26 10:20:27  snf
// checking
//
// Revision 1.9  2006/07/17 07:16:58  snf
// Added comments.
//
// Revision 1.8  2006/05/15 10:04:07  snf
// Added extra priority level.
//
// Revision 1.7  2006/02/27 17:22:24  cjm
// Added more logging.
//
//
