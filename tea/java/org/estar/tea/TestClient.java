package org.estar.tea;

import java.text.*;
import java.util.*;
import java.io.*;
import java.net.*;

import ngat.net.*;
import ngat.net.camp.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.message.base.*;

/** Implements the CAMP protocol as client and response handler.*/
public abstract class TestClient implements Runnable, CAMPResponseHandler {

    public static final long DEFAULT_TIMEOUT = 20000L;

    long timeout;

    /** The command to send.*/
    COMMAND command;

    /** The connection to use.*/
    IConnection connection;

    /** Create a TestClient using supplied params.*/
    public TestClient(COMMAND command, IConnection connection) {
	this.command    = command;
	this.connection = connection;
	timeout         = DEFAULT_TIMEOUT;
    }

    public void setTimeout(long timeout) { this.timeout = timeout; }

    public void run() {

	if (connection == null) {
	    failed(new IOException("No connection available"), null);
	    return;
	}

	try {
	    connection.open();
	} catch (ConnectException cx) {
	    failed(cx, connection);
	    return;
	}
	
	try {
	    connection.send(command);
	} catch (IOException iox) {
	    failed(iox, connection);
	    return;
	}
	
	try {
	    Object obj = connection.receive(timeout);
	    System.err.println("Object recvd: "+(obj != null ? obj.getClass().getName() : "NULL"));
	    COMMAND_DONE update = (COMMAND_DONE)obj;	   
	    handleUpdate(update, connection);
	} catch (ClassCastException cx) {
	    failed(cx, connection);
	    return;
	} catch (IOException iox) {
	    failed(iox, connection);
	    return;
	}

    }

}
