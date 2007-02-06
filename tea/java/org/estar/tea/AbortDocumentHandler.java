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
import ngat.net.camp.*;
import ngat.astrometry.*;

import ngat.message.base.*;
import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;

/** Handles a <i>score</i> request.*/
public class AbortDocumentHandler implements Logging {
    
    /** Classname for logging.*/
    public static final String CLASS = "AbortDocHandler";
    
    /** Reference to the TEA.*/
    TelescopeEmbeddedAgent tea;
    
    /** EstarIO for responses.*/
    private eSTARIO io; 
    
    /** GLobusIO handle for responses.*/
    private GlobusIOHandle handle;

    /** Class logger.*/
    private Logger logger;

    /** Create an AbortDocumentHandler using the supplied IO parameters.
     * @param io      The eSTARIO.
     * @param handle  Globus IO Handle for the connection.
     */
    public AbortDocumentHandler(TelescopeEmbeddedAgent tea, eSTARIO io, GlobusIOHandle handle) {
	this.tea    = tea;
	this.io     = io;
	this.handle = handle;
	logger = LogManager.getLogger("TRACE");
    }


    /** Called to handle an incoming abort document. 
     * Attempts to score the request via the OSS Phase2 DB.
     * @param document The RTML request document.
     * @throws Exception if anything goes wrong.
     */
    public RTMLDocument handleAbort(RTMLDocument document) throws Exception {

	long now = System.currentTimeMillis();

	// deduce the id of the group we would like to abort...

	// Tag/User ID combo is what we expect here.
	 
	RTMLContact contact = document.getContact();
	
	if (contact == null) {
	    logger.log(INFO, 1, CLASS, "AH","handleAbort",
		       "RTML Contact was not specified, failing request.");
	    return setError( document,"No contact was supplied");
	}
	 
	String userId = contact.getUser();
	
	if (userId == null) {
	    logger.log(INFO,1,CLASS,"AH","handleAbort",
		       "RTML Contact User was not specified, failing request.");
	    return setError( document, "Your User ID was null");
	}
	 
	// The Proposal ID.
	RTMLProject project = document.getProject();
	String proposalId = project.getProject();
	
	if (proposalId == null) {
	    logger.log(INFO,1,CLASS,"AH","handleAbort",
		       "RTML Project was not specified, failing request.");
	    return setError( document, "Your Project ID was null");
	}
	
	// We will use this as the Group ID otherwise use 'default agent'.
	RTMLIntelligentAgent userAgent = document.getIntelligentAgent();
	
	if (userAgent == null) {
	    logger.log(INFO,1,CLASS,"AH","handleAbort",
		       "RTML Intelligent Agent was not specified, failing request.");
	    return setError(document, "No user agent: ###TBD Default UA");
	}
	
	String requestId = userAgent.getId();

	// First call back the proposal so we can extract the offending group..

	String proposalPathName = tea.getDBRootName()+"/"+userId+"/"+proposalId;

	Path path = new Path(groupPathName);
	
	GET_PROPOSAL request = new GET_PROPOSAL(tea.getId()+":"+requestId);
	request.setProposalPath(new Path(proposalPathName));
	request.setEditorPath(new Path("TEA:AbortHandler"));
	request.setKey(5522);
	request.setRegId();
	request.setDoLock(false);
	
	request.setClientDescriptor(new ClientDescriptor("EmbeddedAgent",
							 ClientDescriptor.ADMIN_CLIENT,
							 ClientDescriptor.ADMIN_PRIORITY));
	request.setCrypto(new Crypto("TEA"));
	     
	JMSCommandHandler client = new JMSCommandHandler(tea.getConnectionFactory(), 
							 addgroup, 
							 tea.getOssConnectionSecure());
	
	//freeLock();
	client.send();
	//waitOnLock();
	
	if (client.isError()) {
	    logger.log(INFO,1,CLASS,"AH","handleAbort","Internal error during GET_PROPOSAL: "+
		       client.getErrorMessage());
	    return setError(document, "Internal error during GET_PROPOSAL: "+
			    client.getErrorMessage());
	} 

       GET_PROPOSAL_DONE  get_done = (GET_PROPOSAL_DONE)client.getReply();

       Proposal proposal = get_done.getProposal();
	
       if (proposal == null) {
	   logger.log(INFO,1,CLASS,"AH","handleAbort","Internal error during GET_PROPOSAL: No proposal returned");
	   return setError(document, "Internal error during GET_PROPOSAL: No proposal returned");
       }

       Group group = proposal.findGroup(requestId);

       if (group == null) {
	   logger.log(INFO,1,CLASS,"AH","handleAbort","Internal error during GET_PROPOSAL: Group "+requestId+
		      " was not found in proposal");
	   return setError(document, "Internal error during GET_PROPOSAL: Group "+requestId+" was not found in proposal");
       }
	
       // make it expire right now...
       group.setExpiryDate(now);

       // Now send it back....
       REPLACE_GROUP replace = new REPLACE_GROUP(tea.getId()+":"+requestId);
       ///set various flags and send it...

       document.setType("aborted");
       return document;

    }

    /** Set the error message in the supplied document.
     * @param document The document to modify.
     * @param errorMessage The error message.
     * @throws Exception if anything goes wrong.
     * @return The modified <i>reject</i> document.
     */
    private RTMLDocument setError(RTMLDocument document, String errorMessage) throws Exception
    {
	document.setType("reject");
	document.setErrorString(errorMessage); 
	return document;
    }
    

} // [AbortDocumentHandler]

