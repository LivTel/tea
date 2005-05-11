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

/**
 * Control thread that runs periodically, and looks for documents whose Schedule end date
 * is after now + some constant. These documents will never be done and are expired in the database,
 * so we create a failed document and send it to the agent.
 */
public class DocumentExpirator extends ControlThread implements Logging
{
	/**
	 * Class constant for logging.
	 */
	public static final String CLASS = "DocumentExpirator";

	/** Polling time.*/
	long time;

	/** Offset to allow after expiry.*/
	long offset;

	TelescopeEmbeddedAgent tea;

	/**
	 * Logger.
	 */
	Logger logger = null;

	/**
	 * Constructor. Also creates logger.
	 * @param tea The instance of TelescopeEmbeddedAgent.
	 * @param time How often the expirator runs.
	 * @param offset How long after now the document expirity date is before we send a message to the agent.
	 *        This ensures a currently running document is not expired.
	 * @see #tea
	 * @see #time
	 * @see #offset
	 * @see #logger
	 */
	public DocumentExpirator(TelescopeEmbeddedAgent tea, long time, long offset)
	{
		super("DOCEXP", true);
		this.tea    = tea;
		this.time   = time;
		this.offset = offset;
		logger = LogManager.getLogger(this);
	}

	protected void initialise() {}
    
    
	protected void mainTask()
	{	
		try
		{
			logger.log(INFO,1,CLASS,tea.getId(),"mainTask","Running expirator.");
			// Check to see if there are any expired docs still kept by the TEA.

			RTMLDocument    doc   = null;
			RTMLObservation obs   = null;
			RTMLSchedule    sched = null;
			RTMLIntelligentAgent agent = null;
			AgentRequestHandler arq = null;
			String documentId = null;
			String key = null;

			List keyList = tea.getDocumentKeysList();
			for(int i = 0; i< keyList.size(); i++)
			{
				key = (String)(keyList.get(i));
				doc = tea.getDocument(key);
				if(doc != null)
				{
					obs = doc.getObservation(0);
					if (obs != null)
					{
						sched = obs.getSchedule();
						if (sched != null)
						{
							Date endDate = sched.getEndDate();
							if (endDate != null)
							{
								long end = endDate.getTime();
								logger.log(INFO,1,CLASS,tea.getId(),"mainTask",
									   "Expirator testing document "+key+
									   " with end date "+endDate+".");
								if (end < (System.currentTimeMillis()-offset))
								{
									// Expire the Doc - simple for now needs FG/MG stuff
									agent = doc.getIntelligentAgent();
									if(agent != null)
										documentId = agent.getId();
									logger.log(INFO,1,CLASS,tea.getId(),"mainTask",
										   "Expirator trying to expire document "+documentId+".");
									try
									{
										// send failed to UA
										arq = new AgentRequestHandler(tea);
										arq.sendDocUpdate(doc,"failed");
										// save document
										tea.saveDocument(key,doc);
										// move document to expired dir
										// delete document from memory
										tea.deleteDocument(key);
									}
									catch (Exception e)
									{
										logger.log(INFO,1,CLASS,tea.getId(),"mainTask",
											   "Expirator failed for document "+
											   documentId+":"+e);
										logger.dumpStack(1,e);
									}
								}
							}
						}
					}
				}
			}
		}
		catch(Exception e)
		{
			logger.log(INFO,1,CLASS,tea.getId(),"mainTask","Expirator caught un-caught exception "+e+".");
			logger.dumpStack(1,e);
		}
		logger.log(INFO,1,CLASS,tea.getId(),"mainTask","Expirator sleeping for "+time+" ms.");
		try {Thread.sleep(time);} catch (InterruptedException ix) {}
	}
    
	protected void shutdown() {}
    

}
