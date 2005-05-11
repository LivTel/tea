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
import ngat.astrometry.*;

/**
 * This class implements a DN server.
 */
public class DNServerTest implements eSTARIOConnectionListener, Logging {

    public static final String CLASS = "DNServerTest";

    public static final String DEFAULT_ID = "DNSERVER_TEST";

    public static final int    DEFAULT_DN_PORT = 2220;

    public static final int    DEFAULT_PCR_PORT = 8420;
    
    public static final String DEFAULT_PCR_HOST = "ftnproxy";

    public static final String DEFAULT_IMAGE_WEB_URL = "http://tmcweb.livjm.ac.uk/dn_data/";

    public static final double DEFAULT_LATITUDE = 0.0;

    public static final double DEFAULT_LONGITUDE = 0.0;

    public static final double DEFAULT_DOME_LIMIT = 0.0;

    public static final String DEFAULT_FILTER_COMBO = "RGB";

    /** (Long ISO8601) date formatter.*/
    static SimpleDateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd 'T' HH:mm:ss z");

    /** Date formatter (Long ISO8601).*/
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    /** Date formatter (Medium SOD).*/
    static SimpleDateFormat mdf = new SimpleDateFormat("ddHHmmss");

    /** Date formatter (Day in year).*/
    static SimpleDateFormat adf = new SimpleDateFormat("yyyyMMdd");
    
    /** UTC Timezone.*/
    static final SimpleTimeZone UTC = new SimpleTimeZone(0, "UTC");
    
    /** DN Server port.*/
    protected int dnPort;

    /** PCR Server port.*/
    protected int pcrPort;

    /** PCR host address.*/
    protected String pcrHost;

    /** Base URL for image server - where generated images will be served from.*/
    protected String imageWebUrl;

    /** Handles estar communications.*/
    protected eSTARIO io;

    // Logging.
    protected Logger traceLog;
    
    protected Logger errorLog;

    protected Logger connectLog;
    
    /** Identity.*/
    protected String id;

    /** Site latitude (rads).*/
    protected double siteLatitude;

    /** Site longitude (rads).*/
    protected double siteLongitude;

    /** Dome lower limit (rads).*/
    protected double domeLimit;

    /** Counts connections from IA(s).*/
    protected int connectionCount;

    /** Holds mapping between filter descriptions and filter combos for PCR.*/
    protected Properties filterMap;

    /** Create a DNServer.*/
    public DNServerTest(String id) {

	this.id = id;

	io = new eSTARIO();

	ConsoleLogHandler console = new ConsoleLogHandler(new BasicLogFormatter(150));
	console.setLogLevel(ALL);
	
	traceLog   = LogManager.getLogger("TRACE");
	errorLog   = LogManager.getLogger("ERROR");
	connectLog = LogManager.getLogger("CONNECT");
	
	traceLog.setLogLevel(ALL);
	errorLog.setLogLevel(ALL);
	connectLog.setLogLevel(ALL);
	
	traceLog.addHandler(console);
	errorLog.addHandler(console);
	connectLog.addHandler(console);

	filterMap = new Properties();

    }

    protected void start(int port) {

	this.dnPort = port;

	traceLog.log(INFO, 1, CLASS, id, "init", 
		     "Starting eSTARIO server:"+id+
		     "\n DNServer port: "+dnPort+
		     "\n PCR :          "+pcrHost+" : "+pcrPort+
		     "\n Scope at:      Lat: "+Position.toDegrees(siteLatitude, 3)+
		     " Long: "+Position.toDegrees(siteLongitude,3)+
		     "\n Dome limit:    "+Position.toDegrees(domeLimit, 3)+
		     "\n Image WEB:     "+imageWebUrl);
	
	io.serverStart(dnPort, this);	

    }
    
    
    /* Closes eSTARIO session.*/
    protected void shutdown() {
	if (io != null) {
	    
	    io.serverClose();	
	    traceLog.log(INFO, 2, CLASS, id, " shutdown",
			 "Closed down eSTARIO server:");
	    
	}
    }

    /** Sets the PCR port.*/
    protected void setPcrPort(int p) { this.pcrPort = p; }
    
    /** Sets the PCR host address.*/
    protected void setPcrHost(String h) { this.pcrHost = h; }
    
    /** Sets the base URL for image server - where generated images will be served from.*/
    protected void setImageWebUrl(String u) { this.imageWebUrl = u; }

    /** Sets the site latitude (rads).*/
    protected void setSiteLatitude(double l) { this.siteLatitude = l; }

    /** Sets the site longitude (rads).*/
    protected void setSiteLongitude(double l) { this.siteLongitude = l; }

    /** Sets the dome limit (rads).*/
    protected void setDomeLimit(double d) { this.domeLimit = d; }


    /** Handle the connection - creates a new ConnectionHandler for the connection.
     * @param connectionHandle The globus IO handle of the connection.
     */
    public void handleConnection(GlobusIOHandle connectionHandle) {

	ConnectionHandler handler = new ConnectionHandler(connectionHandle);

	handler.execute();

    }

    /** Configure the filter combos.*/
    protected void configureFilters(File file) throws IOException {
	
	FileInputStream fin = new FileInputStream(file);
	filterMap.load(fin);
	
    }
	
    /** Create an error (type = 'reject' from supplied document.
     * @param document The document to change to error type.
     * @param errorMessage The error messsage.
     */
    private String createErrorDocReply(RTMLDocument document, String errorMessage) {
	document.setCompletionTime(new Date());
	document.setType("reject");
	try {
	    document.setErrorString(errorMessage); 
	} catch (RTMLException rx) {
	    System.err.println("Error setting error string in doc: "+rx);
	}
	return createReply(document);	   
    }
    
    /** Create a reply from supplied document of given type.
     * @param document The document to change.
     * @param type The type.
     */
    private String createDocReply(RTMLDocument document, String type) {
	System.err.println("Create doc reply using type: "+type);
	document.setCompletionTime(new Date());
	document.setType(type);
	return createReply(document);
    }
    
    /** Creates a reply message from the supplied document.
     * @param document The document to extract a reply message from.
     * @return A reply message in RTML format.
     */
    private String createReply(RTMLDocument document) {
	System.err.println("Create reply for: \n\n\n"+document+"\n\n\n");
	try {
	    RTMLCreate createReply = new RTMLCreate();
	    createReply.create(document);
	    
	    String replyMessage = createReply.toXMLString();
	    createReply = null;
	    return replyMessage;
	} catch (Exception ex) {
	    System.err.println("Error creating RTMLDocument: "+ex);
	    return null;
	}
    }
    

    /** Handles a Client connection session.*/
    private class ConnectionHandler {
	
	/** The Classname of this class.*/
	String CLASS = "DNServerTest.ConnectionHandler";
	
	/** Session start time.*/
	long   sessionStart;

	/** The globus IO handle of the connection.*/
	GlobusIOHandle connectionHandle;

	/** The document structure sent by the IA.*/
	RTMLDocument document = null;

	/** The document structure (as String) to return to the IA.*/
	String reply = null;

	/** Document parser.*/
	RTMLParser parser;

	/** Incoming RTML message string.*/
	String message = null;

	/** Create a ConnectionHandler for the specified GlobusIOHandle.*/
	ConnectionHandler(GlobusIOHandle connectionHandle) {
	    this.connectionHandle = connectionHandle;	  
	}

	/** Handle the connection.*/
	private void execute() {

	    connectionCount++;
	    sessionStart = System.currentTimeMillis();

	    traceLog.log(INFO, 1, CLASS, id, "exec", "CONN: Connection "+connectionCount+" started.");
	    
	    try {
		
		message = io.messageRead(connectionHandle);
		
		System.err.println("\n\n");
		traceLog.log(INFO, 1, CLASS, id, "exec", "CONN: Recieved RTML message: "+message);
		System.err.println("\n\n");

		// Extract document and determine type.
		
		parser   = new RTMLParser();
		document = parser.parse(message);
		
		String type = document.getType();
		
		System.err.println("CONN: The doc appears to be a: "+type);
		
		
		if (type.equals("score")) {
		    
		    // Do score and return doc.
		    
		    reply = processDocument(document, "score");

		    System.err.println("\n\n");
		    traceLog.log(INFO, 1, CLASS, id, "exec", "CONN: Sending reply RTML message: "+reply);
		    System.err.println("\n\n");
		    
		    io.messageWrite(connectionHandle, reply);

		} else if
		    (type.equals("request")) {
		    
		    // Confirm request is scorable , return doc and start RequestHandler.
		    
		    reply = processDocument(document, "confirmation");

		    System.err.println("\n\n");
		    traceLog.log(INFO, 1, CLASS, id, "exec", "CONN: Sending reply RTML message: "+reply);
		    System.err.println("\n\n");
		    
		    io.messageWrite(connectionHandle, reply);

		    RequestHandler req = new RequestHandler(document);
		    req.execute();

		} else {
		    
		    // Error - reject.

		    reply = createErrorDocReply(document, "Unknown document type: '"+type+"'");

		    System.err.println("\n\n");
		    traceLog.log(INFO, 1, CLASS, id, "exec", "CONN: Sending reply RTML message: "+reply);
		    System.err.println("\n\n");

		    io.messageWrite(connectionHandle, reply);

		}
		   
	    } catch (Exception ex) {
		traceLog.log(INFO, 1, CLASS, id, "exec", "SERVER:Error parsing doc: "+ex);
	    } 

	    traceLog.log(INFO, 1, CLASS, id, "exec", "SERVER:Connection Finished.");
	    
	    connectionHandle = null;
	    document = null;
	    reply = null;

	} // (handleConnection)


	/** Process the supplied 'score' document.
	 * @param document The document sent by the IA.
	 * @param type The expected return document type.
	 * @return The processed score document.
	 */
	private String processDocument(RTMLDocument document, String type) {
	    
	    long now = System.currentTimeMillis();

	    RTMLObservation obs = document.getObservation(0);
	  	    
	    if (obs == null)
		return createErrorDocReply(document, "There was no observation in the request");

	    RTMLTarget target = obs.getTarget();

	    if (target == null)
		return  createErrorDocReply(document, "There was no target in the request");
	    
	    RA  ra  = target.getRA();	    
	    Dec dec = target.getDec();
		
	    if (ra == null || dec == null)
		return  createErrorDocReply(document, "Missing ra/dec for target");
	    
	    // Convert to rads and compute elevation.
	    double rad_ra = Math.toRadians(ra.toArcSeconds()/3600.0);
	    double rad_dec= Math.toRadians(dec.toArcSeconds()/3600.0);

	    Position targ = new Position(rad_ra, rad_dec);

	    double elev = targ.getAltitude();
	    double tran = targ.getTransitHeight();

	    System.err.println("CONN:INFO:Target at: "+targ.toString()+
			       "\n Elevation:   "+Position.toDegrees(elev,3)+
			       "\n Transits at: "+Position.toDegrees(tran,3));
				
	    RTMLSchedule sched = obs.getSchedule();
	    
	    // May not need these or they may be wanted to predict future completion time?
	    String expy = sched.getExposureType();
	    String expu = sched.getExposureUnits();
	    double expt = sched.getExposureLength();
	
	    // ### TEMP
	    document.setCompletionTime(new Date());
	    
	    if (tran < domeLimit) {	
		// Never rises at site.
		System.err.println("CONN:INFO:Target NEVER RISES");
		return createErrorDocReply(document, 
					   "Target transit height: "+Position.toDegrees(tran,3)+
					   " is below dome limit: "+Position.toDegrees(domeLimit,3)+
					   " so will never be visible at this site");
	    } else if (elev < Math.toRadians(20.0) ) {
		// Currently too low to see.
		System.err.println("CONN:INFO:Target LOW");
		return createErrorDocReply(document, 
					   "Target currently at elevation: "+Position.toDegrees(elev, 3)+
					   " is not visible at this time");			
	    } else {	
		// Valid so score.
		System.err.println("CONN:INFO:Target OK Score = "+(elev/tran));
		document.setScore(elev/tran);	
		return createDocReply(document, type);
	    }		    
	    
	}

    } //[ConnectionHandler].
    
    /** Handles an observation request to the POS.*/
    private class RequestHandler implements POSSocketListener {
	
	private RTMLDocument document;

        private GlobusIOHandle handle;

	private POSSocketClient client;

	private volatile BooleanLock lock  = new BooleanLock(true);

	private volatile boolean error = false;

	private String errorMessage = null;
	
	private String cmdReturnValue;

	private String filter;

	private boolean rgb = false;
	
	/** Create a RequestHandler */
	RequestHandler(RTMLDocument document) {
	    this.document = document;	  
	    
	    client = new POSSocketClient(pcrHost, pcrPort, true);

	}

	/** Executes the ongoing connection to PCR.*/
	public void execute() {

	    System.err.println("REQH: Connecting to PCR on: "+pcrHost+" : "+pcrPort);
	    
	    try {
		client.connect();
		
		System.err.println("REQH: Connected to PCR");
		
	    } catch (IOException iox) {
		
		sendError(document, "Failed to connect to PCR: "+iox);
		return;

	    }
	    
	    client.setSocketListener(this);

	    String frameNumber1 = null;
	    String frameNumber2 = null;
	    String frameNumber3 = null;

	    String processFlag = null;

	    try {
	
		RTMLObservation obs = document.getObservation(0);
		
		// Extract params
		RTMLTarget target = obs.getTarget();
		
		RA  ra  = target.getRA();		
		Dec dec = target.getDec();	
		
		RTMLSchedule sched = obs.getSchedule();
		
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

		    String filterString = dev.getFilterType();
		    
		    // Check valid filter and map to UL combo

		    filter = filterMap.getProperty(filterString, DEFAULT_FILTER_COMBO);
			
		} else {
		    filter = DEFAULT_FILTER_COMBO;
		}

		if (filter.equals("RGB")) 
		    rgb = true;

		// Following curent PCR RTI operating procedures - will change when 2G commands are implemented.

		// 1. SLEW.
		String cmdString = "CCDFIXED NO_ID_FOR_DN_SLEW "+ra+" "+dec+" SINGLE 0 0 0 CR 1 -1";	
		
		System.err.println("REQH: POS send ["+cmdString+"]");
		
		freeLock();

		client.sendCommand(cmdString);
		
		waitOnLock();
		
		if (isError()) {		
		    sendError(document, "Internal DN error during SLEW: "+getErrorMessage());
		    return;
		}

		// No parameters to extract.

		if (rgb) {

		    // 2. CONFIG + EXPOSE.
		    cmdString = "CCDFIXED \""+target.getName()+"\" "+ra+" "+dec+" SINGLE 0 0 0 CR 2 "+expt;	
		    System.err.println("REQH: POS send ["+cmdString+"]");
		    
		    freeLock();
		    
		    client.sendCommand(cmdString);
		    
		    waitOnLock();
		    
		    if (isError()) {		
			sendError(document, "Internal DN error during EXPOSE/RED: "+getErrorMessage());
			return;
		    }	

		    // Extract frame number.
		    frameNumber1 = cmdReturnValue;
		    
		    // 2. CONFIG + EXPOSE.
		    cmdString = "CCDFIXED \""+target.getName()+"\" "+ra+" "+dec+" SINGLE 0 0 0 CV 2 "+expt;	
		    System.err.println("REQH: POS send ["+cmdString+"]");
		    
		    freeLock();
		    
		    client.sendCommand(cmdString);
		    
		    waitOnLock();
		    
		    if (isError()) {		
			sendError(document, "Internal DN error during EXPOSE/GREEN: "+getErrorMessage());
			return;
		    }	
		    
		    // Extract frame number.
		    frameNumber2 = cmdReturnValue;
		    
		    // 2. CONFIG + EXPOSE.
		    cmdString = "CCDFIXED \""+target.getName()+"\" "+ra+" "+dec+" SINGLE 0 0 0 CB 2 "+expt;	
		    System.err.println("REQH: POS send ["+cmdString+"]");
		    
		    freeLock();
		    
		    client.sendCommand(cmdString);
		    
		    waitOnLock();
		    
		    if (isError()) {		
			sendError(document, "Internal DN error during EXPOSE/BLUE: "+getErrorMessage());
			return;
		    }	

		    // Extract frame number.
		    frameNumber3 = cmdReturnValue;

		    processFlag = "COLORJPEG";

		} else {
		   
		    // 2. CONFIG + EXPOSE.
		    cmdString = "CCDFIXED \""+target.getName()+"\" "+ra+" "+dec+" SINGLE 0 0 0 "+filter+" 2 "+expt;	
		    System.err.println("REQH: POS send ["+cmdString+"]");
		    
		    freeLock();
		    
		    client.sendCommand(cmdString);
		    
		    waitOnLock();
		    
		    if (isError()) {		
			sendError(document, "Internal DN error during EXPOSE: "+getErrorMessage());
			return;
		    }	

		    // Extract frame number.
		    frameNumber1 = cmdReturnValue;
		    frameNumber3 = cmdReturnValue;

		    processFlag = "JPEG";
		
		}

	
	
		// 3. PROCESS.
		cmdString = "CCDPROCESS SERVERPC "+processFlag+" "+frameNumber1+" "+frameNumber3+" STELLAR";	
		System.err.println("REQH: POS send ["+cmdString+"]");

		freeLock();

		client.sendCommand(cmdString);
		
		waitOnLock();

		if (isError()) {		
		    sendError(document, "Internal DN error during PROCESS: "+getErrorMessage());
		    return;
		}

		// Extract image filename.
		String imageFileName = cmdReturnValue;

		obs.setImageDataURL(imageWebUrl+"/"+imageFileName);
		
		sendDoc(document, "update");

		sendDoc(document, "observation");

	    } catch (Exception ex) {

		traceLog.log(INFO, 1, CLASS, id, "exec", "REQH:Error occurred: "+ex);
	    }

	}

	/** Waits on a Thread lock.*/
	private void waitOnLock() {
	    
		System.err.println("REQH: Waiting in lock");
		try {
		    lock.waitUntilTrue(0L);
		} catch (InterruptedException ix) {
		    System.err.println("Interrupted waiting on lock");
		}
		System.err.println("REQH: Lock is free");
	}
	
	/** Frees the Thread lock.*/
	private void freeLock() {
	    System.err.println("REQH: Releasing lock");
	    lock.setValue(true);
	}

	/** Sets the current error state and message.*/
	private void setError(boolean error, String errorMessage) {
	    this.error = error;
	    this.errorMessage = errorMessage;
	}
	
	/** Returns True if there is an error.*/
	private boolean isError() { return error; }

	/** Returns the current error message or null. */
	private String getErrorMessage() { return errorMessage; }

	/** Send an Error reply.*/
	private void sendError(RTMLDocument document, String errorMessage) {
	    RTMLIntelligentAgent agent = document.getIntelligentAgent();
	    
	    String agid = agent.getId();
	    String host = agent.getHostname();
	    int    port = agent.getPort();
	    
	    GlobusIOHandle handle = io.clientOpen(host, port);
	    
	    String reply = createErrorDocReply(document, errorMessage);
	    
	    io.messageWrite(handle, reply);	
	    
	    System.err.println("REQH:Sent error message: "+errorMessage);
	    
	    io.clientClose(handle);
	    
	}

	/** Send a reply.*/
	private void sendDoc(RTMLDocument document, String type) {
	    
	    RTMLIntelligentAgent agent = document.getIntelligentAgent();
	    
	    String agid = agent.getId();
	    String host = agent.getHostname();
	    int    port = agent.getPort();
	    
	    GlobusIOHandle handle = io.clientOpen(host, port);
	    
	    String reply = createDocReply(document, type);
	    
	    io.messageWrite(handle, reply);	
	    
	    System.err.println("REQH:Sent doc type: "+type);
	    
	    io.clientClose(handle);
	    
	}

	/** From POSSocketListener - handles an ACK message.*/       
	public void handleAcknowledge(String result) {
	    System.err.println("REQH:LISTENER: Received ACK: ["+result+"]");
	    
	    // ACK OK number | ACK FAIL position

	    StringTokenizer st = new StringTokenizer(result, " ");

	    // Check number of tokens.
	    if (st.countTokens() < 3) {		
		setError(true, "Unexpected ACK reply from PCR: "+result);
		freeLock();
		return;
	    }

	    st.nextToken(); // skip ACK

	    String state = st.nextToken(); 

	    if (state.equals("FAIL")) {
		String value = st.nextToken();
		if (value.equals("NO_COMMS"))
		    setError(true, "No comms between PCR and RCS");
		else
		    setError(true, "Illegal command parameter at position: "+value);
		freeLock();
		return;
	    }

	    setError(false, null);
	  
	}
	
	/** From POSSocketListener - handles a RESULT message.*/   
	public void handleResult(String result) {
	    System.err.println("REQH:LISTENER:Received RESPONSE: ["+result+"]");

	    // RESULT OKAY number value | RESULT FAIL number error
	    
	    StringTokenizer st = new StringTokenizer(result, " ");
	    
	    // Check number of tokens.
	    if (st.countTokens() < 4) {		
		setError(true, "Unexpected RESULT reply from PCR: "+result);
		freeLock();
		return;
	    }

	    st.nextToken(); // skip RESULT

	    String state = st.nextToken(); 

	    if (state.equals("FAIL")) {
		st.nextToken(); // skip number
		setError(true, "While executing PCR command: "+st.nextToken());
		freeLock();
		return;
	    }

	    // Extract the return value;

	    st.nextToken(); // skip number
	  
	    cmdReturnValue = st.nextToken();
  
	    System.err.println("REQH:LISTENER: Returning: "+cmdReturnValue);
	    
	    setError(false, null);
	    freeLock();
	   
	} 

    }// [RequestHandler]
   

    /** Configures and starts a DNServer.*/
    public static void main(String args[]) {
	
	// Extract command line parameters.
	CommandParser cp = new CommandParser("@");
	try {
	    cp.parse(args);
	} catch (ParseException px) {	    
	    System.err.println("Error parsing command line args: "+px);
	    usage();
	    return;
	}
	
	ConfigurationProperties config = cp.getMap();
	
	int dnPort = config.getIntValue("dn-port", DEFAULT_DN_PORT);
	
	int pcrPort = config.getIntValue("pcr-port", DEFAULT_PCR_PORT);
	
	String pcrHost = config.getProperty("pcr-host", DEFAULT_PCR_HOST);
	
	String id = config.getProperty("id", DEFAULT_ID);
	
	// Specify as degrees
	double dlat = config.getDoubleValue("latitude", DEFAULT_LATITUDE);
	
	// Specify as degrees
	double dlong = config.getDoubleValue("longitude", DEFAULT_LONGITUDE);
	
	double domeLimit =  config.getDoubleValue("dome-limit", DEFAULT_DOME_LIMIT);
	
	String url = config.getProperty("image-web-url", DEFAULT_IMAGE_WEB_URL);

	String filterMapFileName = config.getProperty("filter-map-file");

	DNServerTest dn = new DNServerTest(id);
	
	dn.setPcrHost(pcrHost);
	dn.setPcrPort(pcrPort);
	dn.setSiteLatitude(Math.toRadians(dlat));
	dn.setSiteLongitude(Math.toRadians(dlong));
	dn.setDomeLimit(Math.toRadians(domeLimit));
	dn.setImageWebUrl(url);

	if (filterMapFileName != null) {
	    File file = new File(filterMapFileName);
	    try {
		dn.configureFilters(file);
	    } catch (IOException iox) {
		System.err.println("Error reading filter combo map: "+iox);	
	    }
	}

	Position.setViewpoint(Math.toRadians(dlat), Math.toRadians(dlong));
	
	try {
	    dn.start(dnPort);
	} finally {	    
	    dn.shutdown();
	}
    }
    
    private static void usage() {
	
	System.err.println("Usage: java -Dastrometry.impl=<astrometry-class> DNServerTest [options]");
	
    }
    
}
