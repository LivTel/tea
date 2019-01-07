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

import ngat.message.GUI_RCS.*;

/**
 * CAMPServer that uses a TelemetryHandlerFactory for handling connection requests.
 * @see TelemetryHandlerFactory
 */
public class TelemetryReceiver extends CAMPServer
{

	/** The EmbeddedAgent which this server is attached to.*/
	TelescopeEmbeddedAgent tea;

	public TelemetryReceiver(TelescopeEmbeddedAgent tea, String name)
	{
		super(name);
		this.tea = tea;
		handlerFactory = new TelemetryHandlerFactory(tea);
	}

	public static final void main(String args[])
	{
		int port = 0;
		try {
			port = Integer.parseInt(args[0]);
			TelemetryReceiver tr = new TelemetryReceiver(null, "TEST");
			tr.bind(port);
			tr.start();

			System.err.println("Started TelemetryReceiver on port: "+port);

		} catch (Exception e) {
			System.err.println("Failed to start TelemetryReceiver on port: "+port);
		}

	}

}
