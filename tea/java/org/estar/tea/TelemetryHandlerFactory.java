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

import ngat.message.base.*;
import ngat.message.GUI_RCS.*;

/**
 * Creates a new instance of TelemetryHandler for each Telemetry update we receive.
 */
public class TelemetryHandlerFactory implements CAMPRequestHandlerFactory, Logging
{
	public static final String CLASS = "TelemetryHandlerFactory";
	TelescopeEmbeddedAgent tea;
	Logger logger = null;

	/**
	 * Constructor. Sets tea. Creates class logger.
	 * @param tea The instance of tea that uses this server.
	 * @see #logger
	 */
	public TelemetryHandlerFactory(TelescopeEmbeddedAgent tea)
	{
		this.tea = tea;
		logger = LogManager.getLogger("TRACE");
	}

	/** Selects the appropriate handler for the specified command.
	 * May return <i>null</i> if the ProtocolImpl is not defined or not an
	 * instance of JMSMA_ProtocolServerImpl or the request is not
	 * defined or not an instance of CTRL_TO_RCS.
	 * @see #logger
	 */
	public CAMPRequestHandler createHandler(IConnection connection, COMMAND command)
	{
		logger.log(INFO, 1, CLASS, tea.getId(), "createHandler","Received request: "+command);
       
		// Deal with undefined and illegal args.
		if (connection == null)
		{
			logger.log(INFO, 1, CLASS, tea.getId(), "createHandler","Null connection");
 			return null;
		}
		if ( (command == null) ||
		     ! (command instanceof TELEMETRY_UPDATE) )
		{
			logger.log(INFO, 1, CLASS, tea.getId(), "createHandler","Command "+command+
				   " null or not instance of TELEMETRY_UPDATE.");
			return null;
		}
		// Cast to correct subclass.
		TELEMETRY_UPDATE tu = (TELEMETRY_UPDATE)command;
	
		TelemetryInfo telem = tu.getData();
	
		return new TelemetryHandler(tea, connection, telem); 
	}
}
