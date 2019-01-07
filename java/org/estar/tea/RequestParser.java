package org.estar.tea;

import java.io.*;
import java.util.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;

/**
 * This class tests RTMLParser on a request type document
 */
public class RequestParser {

    /**
     * Name of file to parse.
     */
    private String filename = null;
    
    /**
     * Parser to use for parsing.
     */
    RTMLParser parser = null;
	
    /**
     * The document structure returned by the parser.
     */
    RTMLDocument document = null;
    
    /**
     * Default constructor.
     */
    public RequestParser() {
	super();
    }

    /**
     * Parse arguments.  
     */
    public void parseArguments(String args[]) {
	if(args.length != 1) {
	    System.err.println("java -Dhttp.proxyHost=wwwcache.livjm.ac.uk -Dhttp.proxyPort=8080 org.estar.rtml.test.RequestParser <filename>");
	    System.exit(2);
	}
	filename = args[0];
    }

    /**
     * run method.   
     */
    public void run() throws Exception {
	parser = new RTMLParser();
	parser.init(true);
	document = parser.parse(new File(filename));
	//System.out.println(document);

	String type = document.getType();

	System.err.println("The doc appears to be a: "+type);

	RTMLObservation obs = document.getObservation(0);

	//System.err.println("Here comes the obs:\n"+obs);

	RTMLTarget target = obs.getTarget();

	RA  ra  = target.getRA();

	Dec dec = target.getDec();

	System.err.println("Here comes the target: "+ra+", "+dec);

	RTMLSchedule sched = obs.getSchedule();

	String expy = sched.getExposureType();
	String expu = sched.getExposureUnits();
	double expt = sched.getExposureLength();
	
	System.err.println("Here comes the schedule: "+expy+" / "+expt+" "+expu);

    }

    /**
     * main method of test program.
     */
    public static void main(String args[]) {
	RequestParser testParser = null;
	
	try {
	    testParser = new RequestParser();
	    testParser.parseArguments(args);
	    testParser.run();
	}
	catch(Exception e)
	    {
		System.err.println("TestParser:main:"+e);
		e.printStackTrace();
		System.exit(1);
	    }
	System.exit(0);
    }
}
