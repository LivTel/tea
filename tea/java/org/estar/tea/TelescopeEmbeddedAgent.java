package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
import java.text.*;
import java.rmi.*;
import javax.net.*;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.io.*;
import org.estar.toop.TOCSession;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.net.*;
import ngat.astrometry.*;

import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;


/**
 * This class implements a Telescope Embedded Agent.
 */
public class TelescopeEmbeddedAgent implements eSTARIOConnectionListener, Logging {
    
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: TelescopeEmbeddedAgent.java,v 1.32 2007-07-17 09:56:28 snf Exp $";

	public static final String CLASS = "TelescopeEA";
    
    public static final int ASYNC_MODE_SOCKET = 1;

    public static final int ASYNC_MODE_RMI = 2;


	/** Default agent name for logging.*/
	public static final String DEFAULT_ID = "TELESCOPE_EA";
    
	/** Default port for DN server. (Listening for messages from UA via SOAP NA).*/
	public static final int    DEFAULT_DN_PORT = 2220;
    
	/** Default port for telemetry server (callbacks from RCS).*/
	public static final int    DEFAULT_TELEMETRY_PORT = 2233;

	/** Default host for telemetry server (callbacks from RCS).*/
	public static final String DEFAULT_TELEMETRY_HOST = "localhost";
    
	/** Default port for OSS. (Ongoing DB insertions).*/
	public static final int    DEFAULT_OSS_PORT = 7940;
   
	/** Default host address for OSS. (Ongoing DB insertions).*/
	public static final String DEFAULT_OSS_HOST = "192.168.1.30";

	/** Default port for SSL ITR.*/
	public static final int    DEFAULT_RELAY_PORT = 7166;

	/** Default host for SSL ITR.*/
	public static final String DEFAULT_RELAY_HOST = "192.168.1.30";

	/** Default port for RCS CTRL. (Status requests).*/
	public static final int    DEFAULT_CTRL_PORT = 9110;
    
	/** Default host address for RCS CTRL. (Status requests).*/
	public static final String DEFAULT_CTRL_HOST = "192.168.1.30";
    
	public static final String DEFAULT_IMAGE_DIR = "/home/estar/nosuchdir";
    
	public static final double DEFAULT_LATITUDE = 0.0;
    
	public static final double DEFAULT_LONGITUDE = 0.0;
    
	public static final double DEFAULT_DOME_LIMIT = 0.0;

	public static final String DEFAULT_FILTER_COMBO = "RATCam-Clear-2";
    
	public static final String DEFAULT_DB_ROOT = "/DEV_Phase2_001";
    
	public static final String DEFAULT_SSL_KEY_FILE_NAME   = "nosuchkeystore.private";
    
	public static final String DEFAULT_SSL_TRUST_FILE_NAME = "nosuchtruststore.public";
    
	public static final String DEFAULT_SSL_KEY_FILE_PASSWORD    = "secret";

    	public static final String DEFAULT_OSS_KEY_FILE_NAME   = "nosuchkeystore.private";
    
	public static final String DEFAULT_OSS_TRUST_FILE_NAME = "nosuchtruststore.public";
    
	public static final String DEFAULT_OSS_KEY_FILE_PASSWORD    = "secret";
    
	public static final int    DEFAULT_SSL_FILE_XFER_BANDWIDTH = 50; // kbyte/s
    
	public static final long DEFAULT_TELEMETRY_RECONNECT_TIME = 600*1000L;
    
	public static final String DEFAULT_DOCUMENT_DIR   = "data";
    
	public static final String DEFAULT_EXPIRED_DOCUMENT_DIR   = "expired";
    
	public static final long DEFAULT_EXPIRATOR_POLLING_TIME   = 3600*1000L;
    
	public static final long DEFAULT_EXPIRATOR_OFFSET_TIME    = 1800*1000L;

    public static final String DEFAULT_TAP_PERSISTANCE_FILE_NAME = "availability.dat";


	public static final int GROUP_PRIORITY = 5;

	/** Default maximum observation time (mult*expose) (ms).*/
	public static final long DEFAULT_MAX_OBS_TIME = 7200;
    
	/** (Long ISO8601) date formatter.*/
	public static SimpleDateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd 'T' HH:mm:ss z");
    
	/** Date formatter (Long ISO8601).*/
	public static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    
	/** Date formatter (Medium SOD).*/
	public static SimpleDateFormat mdf = new SimpleDateFormat("ddHHmmss");
    
	/** Date formatter (Day in year).*/
	public static SimpleDateFormat adf = new SimpleDateFormat("yyyyMMdd");
    
	/** UTC Timezone.*/
    public static final SimpleTimeZone UTC = new SimpleTimeZone(0, "UTC");
     
    /** Number formatter for scores.*/
    public static NumberFormat nf;

	/** DN Server port.*/
	protected int dnPort;

	/** Telemetry host i.e. us as seen by RCS.*/
	protected String telemetryHost;

	/** Telemetry port.*/
	protected int telemetryPort;

	/** OSS Server port.*/
	protected int ossPort;
    
	/** OSS host address.*/
	protected String ossHost;
    
	/** True if the OSS requires a secure connection.*/
	protected boolean ossConnectionSecure;

    	protected File   ossKeyFile;
	protected File   ossTrustFile;
	protected String ossPassword;

	/** RCS CTRL Server port.*/
	protected int ctrlPort;
    
	/** RCS CTRL host address.*/
	protected String ctrlHost;
    
	/** RCS SFX Server port.*/
	protected int relayPort;
    
	/** RCS SFX host address.*/
	protected String relayHost;

    protected boolean relaySecure = true;
    
	protected File   relayKeyFile;
	protected File   relayTrustFile;
	protected String relayPassword;

	protected int    transferBandwidth;


	/** Image transfer client.*/
	protected SSLFileTransfer.Client sslClient;
    
	/** ConnectionFactory.*/
	protected ConnectionFactory connectionFactory;
        
	/** Base dir where images are placed - ####project-specific.*/
	protected String imageDir;
    
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

	/** DB Root name.*/
	protected String dbRootName;

	/**
	 * The directory to store serialized documents in.
	 */
	protected String documentDirectory = new String("data");
    
	/**
	 * The directory to store expired serialized documents in.
	 */
	protected String expiredDocumentDirectory = new String("expired");
	
	/**
	 * The number of milliseconds between runs of the expirator.
	 * @see #DEFAULT_EXPIRATOR_POLLING_TIME
	 */
	protected long expiratorSleepMs = DEFAULT_EXPIRATOR_POLLING_TIME;

	/**
	 * Properties loaded from the tea properties file.
	 */
	protected NGATProperties properties = null;
    
	/** Counts connections from UA(s).*/
	protected int connectionCount;

	/** Holds mapping between filter descriptions and filter combos for PCR.*/
	protected ConfigurationProperties filterMap;

	protected Map requestMap;
    
	protected Map fileMap;

	/** Maps each request to the AgentRequestHandler (ARQ) which will handle its 
	 * <i>update</i> messages from the RCS and <i>abort</i> messages from UA.
	 * Mapping is from the generated ObservationID (oid) to the ARQ.
	 * @see #getUpdateHandler
	 */    
	protected Map agentMap;

	/** Recieves Telemetry (updates) from the RCs when observations are performed.*/    
	protected TelemetryReceiver telemetryServer;

	/** Max observing time - ### this should be determined by the OSS via TEST_SCHEDULEImpl.*/
	protected long maxObservingTime;

	/** Keeps on requesting telemetry from RCS incase the RCS loses its subscriber list.*/
	protected TelemetryRequestor telemetryRequestor;

	/** Checks Documents for expiration.*/
	protected DocumentExpirator docExpirator;

    protected AgentRequestHandlerMonitoringThread arqMonitor;

    /** Which mode we make asynch responses.*/
    protected int asyncResponseMode;

    /** The host where we can lookup the NAR if RMI mode.*/
    protected String narHost;

    /** Predicts availability of telescope over various time frames.*/
    protected TelescopeAvailabilityPredictor tap;

    /** File where TAP can persist its state.*/
    protected File tapPersistanceFile;

    /** Handles outgoing mail alerts.*/
    private Mailer mailer;

	/** Create a TEA with the supplied ID.*/
	public TelescopeEmbeddedAgent(String id) {

		sdf.setTimeZone(UTC);
		iso8601.setTimeZone(UTC);

		nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(6);
		nf.setGroupingUsed(false);

		Logger l = null;

		this.id = id;

		io = new eSTARIO();
	
		ConsoleLogHandler console = new ConsoleLogHandler(new BogstanLogFormatter());
		console.setLogLevel(ALL);
	
		traceLog   = LogManager.getLogger("TRACE");
		traceLog.setLogLevel(ALL);	
		traceLog.addHandler(console);
	
		// add handler to other loggers used
	
	
		l = LogManager.getLogger("org.estar.tea.DefaultPipelinePlugin");
		l.setLogLevel(ALL);
		l.addHandler(console);
		l = LogManager.getLogger("org.estar.tea.TOCSessionManager");
		l.setLogLevel(ALL);
		l.addHandler(console);
		l = LogManager.getLogger("org.estar.io.eSTARIO");
                l.setLogLevel(ALL);
                l.addHandler(console);

		TOCSession.initLoggers(console,ALL);
	
		filterMap = new ConfigurationProperties();

		requestMap = new HashMap();//kill
		fileMap    = new HashMap();//kill
		agentMap   = new HashMap();
	
		connectionFactory = new TEAConnectionFactory();

	}

	/** Start the various servers and components.
	 *  TBD - There is some sequencing that needs looking at here.
	 * @throws Exception if something goes wrong.
	 */
	protected void start() throws Exception {

		// TelemRecvr
		telemetryServer = new TelemetryReceiver(this, "TR-TEST");
		telemetryServer.bind(telemetryPort);	
		traceLog.log(INFO, 1, CLASS, id, "init",
			     "Telemetry Server on port: "+telemetryPort+
			     ", We are: "+telemetryHost+" from "+ctrlHost);
	
		// TelemReq
		telemetryRequestor = new TelemetryRequestor(this, DEFAULT_TELEMETRY_RECONNECT_TIME);
		traceLog.log(INFO, 1, CLASS, id, "init",
			     "Telemetry Requestor with reconnect interval: "+
			     (DEFAULT_TELEMETRY_RECONNECT_TIME/1000)+" secs");
	
		// DocExp
		docExpirator = new DocumentExpirator(this, expiratorSleepMs,
						     DEFAULT_EXPIRATOR_OFFSET_TIME); 
		traceLog.log(INFO, 1, CLASS, id, "init",
			     "Create DocExpirator with polling interval: "+
			     (expiratorSleepMs/1000)+" secs");
	
		traceLog.log(INFO, 1, CLASS, id, "init", "Create SSL File Client...");
		sslClient = new SSLFileTransfer.Client("TEA_FILE_GRABBER", relayHost, relayPort, relaySecure);	
		sslClient.setBandWidth(transferBandwidth);
		traceLog.log(INFO, 1, CLASS, id, "init", "OK SSL client is ready and will be "+(relaySecure ? "Secure" : "Nonsecure"));

		// If we fail to setup we just can't grab images back but other stuff will work.
		
		if (relaySecure) {
		    try {
			traceLog.log(INFO, 1, CLASS, id, "init",
				     "Trying to initialize SSL Image Transfer client:"+
				     " KF = "+relayKeyFile+" TF="+relayTrustFile+" Pass="+relayPassword);
			// diddly this sometimes hangs! - usually a problem with the EGD setup.
			sslClient.initialize(relayKeyFile, relayPassword, relayTrustFile);
			traceLog.log(INFO, 1, CLASS, id, "init",
				     "Setup SSL Image Transfer client:"+
				     "Image TransferRelay: "+relayHost+" : "+relayPort);
		    } catch (Exception e) {
			traceLog.dumpStack(1,e);
			sslClient = null;
		    }
		} else {
		    traceLog.log(INFO, 1, CLASS, id, "init",
                                     "Setup NonSecure Image Transfer client:"+
				 "Image TransferRelay: "+relayHost+" : "+relayPort);		    
		}

		String asmode = "UNKNOWN";
		if (asyncResponseMode == ASYNC_MODE_SOCKET)
		    asmode = "SOCKET";
		else
		    asmode = "RMI: "+narHost;
			

		traceLog.log(INFO, 1, CLASS, id, "init",
			     "Starting Telescope Embedded Agent: "+id+
			     "\n Incoming port: "+dnPort+		     
			     "\n  OSS:          "+ossHost+" : "+ossPort+
			     "\n    Root:       "+dbRootName+
			     "\n    Connect:    "+(ossConnectionSecure ? " SECURE" : " NON_SECURE")+
			     "\n  Ctrl:         "+ctrlHost+" : "+ctrlPort+
			     "\n  Async Mode:   "+asmode+
			     "\n Telescope:"+
			     "\n  Lat:          "+Position.toDegrees(siteLatitude, 3)+
			     "\n  Long:         "+Position.toDegrees(siteLongitude,3)+
			     "\n  Dome limit:   "+Position.toDegrees(domeLimit, 3));

		traceLog.log(INFO, 1, CLASS, id, "init",
			     "looking for any documents in persistence store");
		try {
		    loadDocuments();
		    traceLog.log(INFO, 1, CLASS, id, "init",
				 "Loaded all readable current documents from persistence store");
		} catch (Exception e) {
		    traceLog.log(INFO, 1, CLASS, id, "init",
				 "Failed to load current documents from persistence store: "+e);
		    traceLog.dumpStack(1,e);
		}
		
                if (System.getProperty("registerHandler") != null) {
                    traceLog.log(INFO, 1, CLASS, id, "init",
                                 "Registering EA-RequestHandler...");
                    try {
                        DefaultEmbeddedAgentRequestHandler ear = new DefaultEmbeddedAgentRequestHandler(this);
                        Naming.rebind("rmi://localhost/EARequestHandler", ear);
                        traceLog.log(INFO, 1, CLASS, id, "init",
                                     "EmbeddedAgentRequestHandler bound to registry");
                    } catch (Exception e) {
                        traceLog.log(INFO, 1, CLASS, id, "init",
                                     "Failed to register EAR: "+e);

			try {
			    if (mailer != null)
				mailer.send("Starting TEA ("+id+") Failed to bind [EmbeddedAgentRequestHandler] to local registry: "+e);
			    e.printStackTrace();
			} catch (Exception tx) {
			    tx.printStackTrace();
			}
			
                    }

		    try {
			 tap = new DefaultTelescopeAvailabilityPredictor(tapPersistanceFile);
			 Naming.rebind("rmi://localhost/TAPredictor", tap);
			 traceLog.log(INFO, 1, CLASS, id, "init",
				      "TelescopeAvailabilityPredictor bound to registry");
					 
		    } catch (Exception e) {
                        traceLog.log(INFO, 1, CLASS, id, "init",
                                     "Failed to register TAP: "+e);
			
			try {
			    if (mailer != null)
				mailer.send("Starting TEA ("+id+") Failed to bind [TelescopeAvailabilityPredictor] to local registry: "+e);
			    e.printStackTrace();
			} catch (Exception tx) {
			    tx.printStackTrace();
			}
			
                    }
		    
		    try {
			((DefaultTelescopeAvailabilityPredictor)tap).load();
			traceLog.log(INFO, 1, CLASS, id, "init",
				     "Loaded TAP persistant data successfully");
			
		    } catch (Exception e) {
			traceLog.log(INFO, 1, CLASS, id, "init",
				     "Failed to load TAP data: "+e);
			
		    }
		    
		}   
		    
		arqMonitor = new AgentRequestHandlerMonitoringThread(this);
		arqMonitor.start();
		traceLog.log(INFO, 1, CLASS, id, "init",
			     "Started ARQ Monitoring mono-thread...");
		
		telemetryServer.start();	
		traceLog.log(INFO, 1, CLASS, id, "init",
			     "Started Telemetry Server on port: "+telemetryPort+
			     ", We are: "+telemetryHost+" from "+ctrlHost);
		
		telemetryRequestor.start();
		traceLog.log(INFO, 1, CLASS, id, "init",
			     "Started Telemetry Requestor with reconnect interval: "+
			     (DEFAULT_TELEMETRY_RECONNECT_TIME/1000)+" secs");
		
		//docExpirator.start();
		//traceLog.log(INFO, 1, CLASS, id, "init",
		//     "Started DocExpirator with polling interval: "+
		//     (expiratorSleepMs/1000)+" secs");
		
		//io.serverStart(dnPort, this);	
		traceLog.log(INFO, 1, CLASS, id, "init",
			     "NOT Started eSTAR IO server.");

	}
    
    /* Closes eSTARIO session, TelemetryServer, TelemetryRequestor, DocExpirator.*/
    protected void shutdown() {
	
	if (telemetryServer != null) {
			telemetryServer.terminate();
			traceLog.log(INFO, 2, CLASS, id, " shutdown",
				     "Closed down Telemetry server:");
		}
	
		if (telemetryRequestor != null) {
			telemetryRequestor.terminate();
			traceLog.log(INFO, 2, CLASS, id, " shutdown",
				     "Closed down Telemetry receiver:");
		}
	
		if (docExpirator  != null) {
			docExpirator.terminate();
			traceLog.log(INFO, 2, CLASS, id, " shutdown",
				     "Closed down DocExpirator:");
		}

		if (io != null) {	     
			io.serverClose();	
			traceLog.log(INFO, 2, CLASS, id, " shutdown",
				     "Closed down eSTARIO server:");	    
		}
	
	}

	/** Sets the incoming request port ###dn is an old name.*/
	public void setDnPort(int p) { this.dnPort = p; }
    
	/** Sets the OSS port.*/
	public void setOssPort(int p) { this.ossPort = p; }
    
	/** Sets the OSS host address.*/
	public void setOssHost(String h) { this.ossHost = h; }

	/** Sets whether the OSS Connection is secure.*/    
	public void setOssConnectionSecure(boolean s) { this.ossConnectionSecure = s; }


	/** Sets the SSL transfer keystore.*/
	public void setOssKeyFile(File k) { ossKeyFile = k; }
    
	/** Sets the SSL transfer trust store.*/
	public void setOssTrustFile(File t) { ossTrustFile = t; };
    
	/** Sets the SSL transfer keypass.*/
	public void setOssPassword(String p) { ossPassword = p; }
    

	/** Sets the RCS CTRL port.*/
	public void setCtrlPort(int p) { this.ctrlPort = p; }
    
	/** Sets the RCS CTRL host address.*/
	public void setCtrlHost(String h) { this.ctrlHost = h; }

	/** Sets the incoming Telemetry port.*/
	public void setTelemetryPort(int p) { this.telemetryPort = p; }

	/** Sets the incoming Telemetry host address (this host as seen by RCS).*/
	public void setTelemetryHost(String h) { this.telemetryHost = h; }

	/** Sets the SSL Fileserver host address.*/
	public void setRelayHost(String r) { this.relayHost = r; }

	/** Sets the SSL Fileserver port.*/
	public void setRelayPort(int p) { this.relayPort = p; }
    
	/** Sets the Base dir where transferred images are placed. ###project-specific*/
	public void setImageDir(String d) { this.imageDir = d; }

    /** Set the NodeAgent Asynch Response Handler host address.*/
    public void setNarHost(String h) { this.narHost = h; }

    public void setAsyncResponseMode(int m) { this.asyncResponseMode = m; }

    /** Set the TAP's persistance file.*/
    public void setTapPersistanceFile(File f) { this.tapPersistanceFile = f; }

	/** Sets the site latitude (rads).*/
	public void setSiteLatitude(double l) { this.siteLatitude = l; }
    
	/** Sets the site longitude (rads).*/
	public void setSiteLongitude(double l) { this.siteLongitude = l; }
    
	/** Sets the dome limit (rads).*/
	public void setDomeLimit(double d) { this.domeLimit = d; }
    
	/** Sets the DB rootname.*/
	public void setDBRootName(String r) { this.dbRootName = r; }
    
	/** Sets the Maximum allowable observation length (ms).*/
	public void setMaxObservingTime(long t) { this.maxObservingTime = t; }

	/**
	 * Set the directory to store serialised document in.
	 * @param s A directory.
	 * @see #documentDirectory
	 */
	public void setDocumentDirectory(String s)
	{
		documentDirectory = s;
	}
    
	/**
	 * Set the directory to store expired serialised document in.
	 * @param s A directory.
	 * @see #expiredDocumentDirectory
	 */
	public void setExpiredDocumentDirectory(String s)
	{
		expiredDocumentDirectory = s;
	}
    
	/**
	 * Set how long to sleep between calls to the document exirator.
	 * @param l A long, representing a period in milliseconds.
	 * @see #expiratorSleepMs
	 */
	public void setExpiratorSleepMs(long l)
	{
		expiratorSleepMs = l;
	}

    /** Set whether the image relay is secure.*/
    public void setRelaySecure(boolean r) { this.relaySecure = r;}
    
	/** Sets the SSL transfer keystore.*/
	public void setRelayKeyFile(File k) { relayKeyFile = k; }
    
	/** Sets the SSL transfer trust store.*/
	public void setRelayTrustFile(File t) { relayTrustFile = t; };
    
	/** Sets the SSL transfer keypass.*/
	public void setRelayPassword(String p) { relayPassword = p; }
    
	/** Sets the SSL transfer bandwidth (kbyte/s).*/
	public void setTransferBandwidth(int b) { transferBandwidth = b; }

	/**
	 * Set the tea pipeline properties.
	 * @param file The tea pipeline property file.
	 * @see #properties
	 * @exception FileNotFoundException Thrown if the file doesn't exist. 
	 * @exception IOException Thrown if the load failed.
	 */
	public void configureProperties(File file) throws java.io.FileNotFoundException,
							  java.io.IOException
	{
		properties = new NGATProperties();
		properties.load(file);
	}
    
	/** Returns the Id for this TEA.*/
	public String getId() { return id; }

	/** Returns the DB rootname.*/
	public String getDBRootName() {  return dbRootName; }
    
	/** DN Server port.*/
	public int getDnPort() { return dnPort; }

	/** OSS Server port.*/
	public int getOssPort() { return ossPort; }

	/** Returns whether the OSS Conneciton is secure. */
	public boolean getOssConnectionSecure() { return ossConnectionSecure; }

	/** OSS host address.*/
	public String getOssHost() { return ossHost; }

	/** RCS CTRL Server port.*/
	public int getCtrlPort() { return ctrlPort; }
    
	/** RCS CTRL host address.*/
	public String getCtrlHost() { return ctrlHost; }
    
	/** Telemetry Server port.*/
	public int getTelemetryPort() { return telemetryPort; }
    
	/** Telemetry Server host.*/
	public String getTelemetryHost() { return telemetryHost; }

	/** Image transfer client.*/
	public SSLFileTransfer.Client getImageTransferClient() { return sslClient; }

	/** Base dir where transferred images are placed.*/
	public String getImageDir() { return imageDir; }

	/** Dome limit (rads).*/
	public double getDomeLimit() { return domeLimit; }

	/** Returns the site lattude (rads).*/
	public double getSiteLatitude() { return siteLatitude; }

	/** Returns the site longitude (rads).*/
	public double getSiteLongitude() { return siteLongitude; }

	/** Returns a reference to the filter mapping. ###instrument-specific.*/
	public ConfigurationProperties getFilterMap() { return filterMap; }
    
	public Map getRequestMap() { return requestMap; }
    
	public long getMaxObservingTime() { return maxObservingTime; }

    public TelescopeAvailabilityPredictor getTap() { return tap; }
    
	/**
	 * Get the loaded tea properties.
	 * @return The properties.
	 * @see #properties
	 */
	public NGATProperties getProperties() 
	{
		return properties;
	}

	/**
	 * Get an string value using the specified key from the loaded tea properties.
	 * @param key The key.
	 * @return The key's value in the properties.
	 * @see #properties
	 * @exception NullPointerException Thrown if the properties are null.
	 */
	public String getPropertyString(String key) throws NullPointerException
	{
		if(properties == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPropertiesString:properties were null.");
		}
		return properties.getProperty(key);
	}

	/**
	 * Get an integer value using the specified key from the loaded tea properties.
	 * @param key The key.
	 * @return The key's value in the properties.
	 * @see #properties
	 * @exception NullPointerException Thrown if the properties are null.
	 * @exception NGATPropertyException Thrown if the get failed.
	 */
	public int getPropertyInteger(String key) throws NullPointerException, NGATPropertyException
	{
		if(properties == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPropertiesInteger:properties were null.");
		}
		return properties.getInt(key);
	}
    
	/**
	 * Get an double value using the specified key from the loaded tea properties.
	 * @param key The key.
	 * @return The key's value in the properties.
	 * @see #properties
	 * @exception NullPointerException Thrown if the properties are null.
	 * @exception NGATPropertyException Thrown if the get failed.
	 */
	public double getPropertyDouble(String key) throws NullPointerException, NGATPropertyException
	{
		if(properties == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPropertiesDouble:properties were null.");
		}
		return properties.getDouble(key);
	}

	/**
	 * Get an boolean value using the specified key from the loaded tea properties.
	 * @param key The key.
	 * @return The key's value in the properties.
	 * @see #properties
	 * @exception NullPointerException Thrown if the properties are null.
	 */
	public boolean getPropertyBoolean(String key) throws NullPointerException
	{
		if(properties == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPropertiesBoolean:properties were null.");
		}
		return properties.getBoolean(key);
	}
    
	/**
	 * Get an class value using the specified key from the loaded tea properties.
	 * @param key The key.
	 * @return The key's value in the properties.
	 * @see #properties
	 * @exception NullPointerException Thrown if the properties are null.
	 * @exception NGATPropertyException Thrown if the get failed.
	 */
	public Class getPropertyClass(String key) throws NullPointerException, NGATPropertyException
	{
		if(properties == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPropertiesClass:properties were null.");
		}
		return properties.getClass(key);
	}
    
	/** Handles estar communications.*/
	public eSTARIO getEstarIo() { return io; }   
    
    
	/** Handle the connection - creates a new ConnectionHandler for the connection.
	 * @param connectionHandle The globus IO handle of the connection.
	 */
	public void handleConnection(GlobusIOHandle connectionHandle) {
	
		ConnectionHandler handler = new ConnectionHandler(this, connectionHandle, ++connectionCount);
	
		handler.execute();
	
	}
    
	// ---------------------------------------------
	// Update mapping Mutators and Extractors.
	// ---------------------------------------------
    
	/** Add an ARQ for observationID.
	 * @param oid The observationID.
	 * @param arq The ARQ for this oid.
	 */
	public void registerHandler(String oid, AgentRequestHandler arq) {
		agentMap.put(oid, arq);
	}
    
    /** Returms the Agent Map.*/
    public Map getAgentMap() {
	return agentMap;
    }

	/** Find an ARQ for observationID.
	 * @param oid The observationID.
	 * @return The ARQ for the specified observationID.
	 */
	public AgentRequestHandler getUpdateHandler(String oid) {
		if (agentMap.containsKey(oid))
			return (AgentRequestHandler)agentMap.get(oid);
		return null;
	}

	/** Remove the ARQ for an observationID.
	 * If there is no ARQ registered then fails silently.
	 * @param oid The observationID.
	 */
	public void deleteUpdateHandler(String oid) {
		if (agentMap.containsKey(oid))
			agentMap.remove(oid);
	}
    
	// ---------------------------------------------
	// Document Persistance Mutators and Extractors. ###deprecate
	// ---------------------------------------------
    
	/** Returns an Iterator over the set of document keys.*/
	public Iterator listDocumentKeys() { 
		return agentMap.keySet().iterator();
	}

	/** 
	 * Returns an List of the set of document keys.
	 * Gets round a problem with listDocumentKeys, that prevents you
	 * removing a document from the list from within the iterator.
	 * But note theoretically, each document key contained within this list might not exist
	 * by the time you come to read it.
	 * @return A List (vector) containing strings of document keys.
	 * @see #listDocumentKeys
	 */
	public List getDocumentKeysList()
	{ 
		Vector v = null;
		Iterator keys = null;
		String key = null;
	
		v = new Vector();
		keys = listDocumentKeys();
		while (keys.hasNext())
		{
			key = (String)keys.next();
			v.add(key);
		}
		return v;
	}
    
	//  /**
	//      * Add a document into request map for supplied key and saves to file.
	//      * @param document The document to save.
	//      * @throws Exception If anything goes horribly wrong.
	//      * @see #createKeyFromDoc
	//      */
	//     public void addDocument(RTMLDocument document) throws Exception
	//     {
	// 	String key = createKeyFromDoc(document);
	// 	requestMap.put(key, document);
	// 	saveDocument(key, document);
	//     }
    
	//     /** Get the Doucument for the supplied key or null.
	//      * @param key      Document's group path.
	//      */
	//     public RTMLDocument getDocument(String key) {
	// 	return (RTMLDocument)requestMap.get(key);
	//     }
      
	/** Save the RTMLDocument to the specified file.
	 * @param document The document to save.
	 * @param file Where to save it.
	 */
	public void saveDocument(RTMLDocument document, File file) throws Exception {
		FileOutputStream fos = new FileOutputStream(file);
		RTMLCreate create = new RTMLCreate();
		create.create(document);
		create.toStream(fos);
		fos.flush();
		fos.close();
		traceLog.log(INFO, 1, CLASS, id, "saveDocument",
			     "Saving document: to file: "+file.getName()+" ...\n"+
			     create.toXMLString());
	}

    /** 
     * Load all current rtml docs from the documentDirectory at startup.
     * @throws Exception if any really dodgy stuff occurs. We trap basic file errors
     * whenever possible and keep going. 
     * @see #documentDirectory
     */
    public void loadDocuments() throws Exception {
	
	int arqCount = 0;
	
	traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
		     "TEA::Starting loadDocuments from "+documentDirectory+".");
	File base = new File(documentDirectory);
	File[] flist = base.listFiles();

	traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
		     "There should be "+flist.length+" docs loaded");
	
	for (int i = 0; i < flist.length; i++ ){
	    
	    File file = flist[i];
	    String fname = file.getName();
	    traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
			 "Loading from file: "+file.getPath());
	    
	    // if any docs fail to load we keep going
	    try {
		
		RTMLDocument doc = readDocument(file);
		traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
			     "Read document "+file.getPath());
		
		// If we fail to extract an oid, log but keep going.
		String oid = createKeyFromDoc(doc);
		traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
			     "Created ObservationID: "+oid);
		
		String requestId = createRequestIdFromDoc(doc);
		
		AgentRequestHandler arq = new AgentRequestHandler(this, doc);
		
		arq.setName(requestId);
		arq.setId(getId()+"/"+arq.getName());
		arq.setDocumentFile(file);
		arq.setOid(oid);
		
		// If we cant prepare the ARQ for UpdateHandling then it wont be started.
		arq.prepareUpdateHandler();
		arq.start();
		
		traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
			     "Started ARQ UpdateHandler thread: "+arq);
		
		// Ok, now register it.
		agentMap.put(oid, arq);	    
		traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
			     "Registered running ARQ for: "+oid+" Using file: "+file.getPath());
		arqCount++;
	    } catch (Exception e) {
		traceLog.log(WARNING, 1, CLASS, id, "loadDocuments",
			     "Error during loading for: "+file.getPath()+" : "+e);
		traceLog.dumpStack(1, e);
	    }
	    
	}

	traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
		     "Loaded and started "+arqCount+" ARQs out of "+flist.length+" possibles");
	
	if (arqCount < flist.length) {
	    try {
		if (mailer != null)
		    mailer.send("Starting TEA ("+id+") A total of "+arqCount+" out of "+flist.length+" active RTML Docs were loaded from persistance store");
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}

	
    }

	/**
	 * Read a document from a file.
	 * @param file The file to load from.
	 * @return A Java object structure, representing the document parsed.
	 * @exception Exception Thrown if the parsing failed.
	 * @see org.estar.rtml.RTMLDocument
	 * @see org.estar.rtml.RTMLParser
	 */
	public RTMLDocument readDocument(File file) throws Exception {
	
		RTMLParser parser = new RTMLParser();
		RTMLDocument doc = parser.parse(file);
		return doc;
	
	}
        
	/** 
	 * Delete the document represented by key from persistence store -
	 * moving to the expired directory.
	 * @param docFile The name of the file.
	 * @see #expiredDocumentDirectory
	 */
	public void expireDocument(File docFile) throws Exception {
	
		String fname = docFile.getName();
		File expiredFile = new File(expiredDocumentDirectory,fname);
		// rename
		if (! docFile.renameTo(expiredFile))
		    throw new IOException("ExpireDoc: Failed to rename "+docFile.getPath()+ "to "+ expiredFile.getPath());
 
	}

	/** Creates a new unique filename.
	 * @param oid Not currently used.
	 */
	public String createNewFileName(String oid) {
		File base    = new File(documentDirectory);
		File newFile = new File(base, "doc-"+System.currentTimeMillis()+".rtml");
		return newFile.getPath();
	}

    
	/** 
	 * Gets the ObservationID from the RTMLDocument supplied.
	 * @param doc The RTMLDocument.
	 * @return A string unique to a document (observation).
	 * @exception Exception Thrown if any of the document's contact/project/intelligent agent/obs/target are null.
	 * @see #getDBRootName
	 */
	public String createKeyFromDoc(RTMLDocument doc) throws Exception {
	
		RTMLContact contact = doc.getContact();
		if(contact == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":createKeyFromDoc:Contact was null for document.");
		}
		String      userId  = contact.getUser();
	
		RTMLProject project    = doc.getProject();
		if(project == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":createKeyFromDoc:Project was null for document.");
		}
		String      proposalId = project.getProject();
	
		RTMLIntelligentAgent userAgent = doc.getIntelligentAgent();
		if(userAgent == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":createKeyFromDoc:Intelligent Agent was null for document.");
		}
		String               requestId = userAgent.getId();
	
		RTMLObservation observation = doc.getObservation(0);
		if(observation == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":createKeyFromDoc:Observation(0) was null for document.");
		}
	
		RTMLTarget target = observation.getTarget();
		if(target == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":createKeyFromDoc:Target was null for document.");
		}
		String targetIdent = target.getIdent();
	
		return getDBRootName()+"/"+userId+"/"+proposalId+"/"+requestId+"/"+targetIdent;
	
	} // [createKeyFromDoc(RTMLDocument doc)]


    /** 
	 * Gets the ObservationID from the RTMLDocument supplied.
	 * @param doc The RTMLDocument.
	 * @return A string unique to a document (observation).
	 * @exception Exception Thrown if any of the document's contact/project/intelligent agent/obs/target are null.
	 * @see #getDBRootName
	 */
	public String createRequestIdFromDoc(RTMLDocument doc) throws Exception {
	
		RTMLContact contact = doc.getContact();
		if(contact == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":createKeyFromDoc:Contact was null for document.");
		}
		String      userId  = contact.getUser();
	
		RTMLProject project    = doc.getProject();
		if(project == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":createKeyFromDoc:Project was null for document.");
		}
		String      proposalId = project.getProject();
	
		RTMLIntelligentAgent userAgent = doc.getIntelligentAgent();
		if(userAgent == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":createKeyFromDoc:Intelligent Agent was null for document.");
		}
		String               requestId = userAgent.getId();
	
	
		return requestId;
	
	} // [createRequestIdFromDoc(RTMLDocument doc)]


    
	/** Load the filter combos from a file.
	 * ### This is camera only for now..
	 * ### Should be replaced with a configured ngat.instrument.Instrument
	 */
	protected void configureFilters(File file) throws IOException {
	
		FileInputStream fin = new FileInputStream(file);
		filterMap.load(fin);
	
	}
    
	/**Create an error (type = 'reject' from scratch).
	 * @param errorMessage The error messsage.
	 */
	public static String createErrorDocReply(String errorMessage) {
		RTMLDocument document = new RTMLDocument();	
		return createErrorDocReply(document, errorMessage);
	}
    
	/** Create an error (type = 'reject' from supplied document.
	 * @param document The document to change to error type.
	 * @param errorMessage The error messsage.
	 */
	public static String createErrorDocReply(RTMLDocument document, String errorMessage) {
	    document.setCompletionTime(new Date());
	    document.setType("reject");
	    try {
		document.setErrorString(errorMessage); 
	    } catch (RTMLException rx) {
		rx.printStackTrace();
		System.err.println("Error setting error string in doc: "+rx);
	    }
	    return createReply(document);	   
	}
    
    /** Create a reply from supplied document of given type.
     * @param document The document to change.
     * @param type The type.
     */
    public static String createDocReply(RTMLDocument document, String type) {
	System.err.println("Create doc reply using type: "+type);
	// diddly error!
	//document.setCompletionTime(new Date());
	document.setType(type);
	return createReply(document);
    }
    
	/** Creates a reply message from the supplied document.
	 * @param document The document to extract a reply message from.
	 * @return A reply message in RTML format.
	 */
	public static String createReply(RTMLDocument document)
	{
		//System.err.println("Create reply for: \n\n\n"+document+"\n\n\n");
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
    
	/** 
	 * Send a reply of specified type. This differs from sendDoc(doc,type) in that
	 * an io client connection is made to the intelligent agent using the information in the
	 * documents intelligen agent tag, rather than replying to an agent request.
	 * This method is used by the UpdateHandler to send update messages.
	 * @param document The document to send.
	 * @param type     A string denoting the type of document to send.
	 */
	public void sendDocumentToIA(RTMLDocument document) throws Exception {
		
	    traceLog.log(INFO, 1,CLASS, "sendDocumentToIA Started.");

	    switch (asyncResponseMode) {
	    case ASYNC_MODE_SOCKET:
		
		RTMLIntelligentAgent userAgent = document.getIntelligentAgent();
		if(userAgent == null) {
		    traceLog.log(INFO, 1, CLASS, "sendDocumentToIA: User agent was null.");
		    throw new Exception(this.getClass().getName()+":sendDocumentToIA:user agent was null");
		}
		String agid = userAgent.getId();
		String host = userAgent.getHostname();
		int    port = userAgent.getPort();
		if((host == null)||(port == 0))
		{
			traceLog.log(INFO, 1, CLASS,
				     "sendDocumentToIA: IA host was null/port was 0:not sending document "+agid+
				     " back to IA.");
			// don't throw an excetption, just return so TEA deletes ARQ.
			// This assumes null host/0 port means no IA to send document back to.
			return;
		}
		traceLog.log(INFO, 1, CLASS, "sendDocumentToIA: Opening eSTAR IO client connection to ("+host+
			     ","+port+").");
		GlobusIOHandle handle = io.clientOpen(host, port);
		if(handle == null) {
		    traceLog.log(INFO, 1, CLASS, "sendDocumentToIA:Failed to open client connection to ("+host+
				 ","+port+").");
		    throw new Exception(this.getClass().getName()+":sendDocumentToIA:handle was null");
		}
		String reply = TelescopeEmbeddedAgent.createReply(document);
		
		traceLog.log(INFO, 1, CLASS, "sendDocumentToIA:Writing:...\n"+reply+"\n ...to handle "+handle+".");
		// how do we check this has failed?
		io.messageWrite(handle, reply);
		traceLog.log(INFO, 1, CLASS,"sendDocumentToIA:Sent document "+agid+".");
		io.clientClose(handle);
		
		break;

	    case ASYNC_MODE_RMI:

		NodeAgentAsynchronousResponseHandler narh = (NodeAgentAsynchronousResponseHandler)Naming.
		    lookup("rmi://"+narHost+"/NAAsyncResponseHandler");
 
		if (narh == null) {
		    traceLog.log(INFO, 1, CLASS, "sendDocumentToIA: NA ResponseHandler was null.");
		    throw new Exception(this.getClass().getName()+":sendDocumentToIA:Lookup [NAAsyncResponseHandler] was null.");
		}

		narh.handleAsyncResponse(document);

		traceLog.log(INFO, 1, CLASS,"sendDocumentToIA:Sent document successfully via NA ResponseHandler");

		break;

	    default:

	    }
		
	}
	

	/** Configures and starts a Telescope Embedded Agent.*/
	public static void main(String args[]) {
	
		// args[0] is the ID args[1] is a config file - use abs filename preferably.
	
		if (args == null || args.length < 2) {
			usage("No args supplied");
			System.exit(1);
		}
	
		try {
		    if (System.getSecurityManager() == null) {
			System.setSecurityManager(new RMISecurityManager());
		    }
		} catch (Exception e) {
		    e.printStackTrace();
		    return;
		}

		String id = args[0];

		File configFile = new File(args[1]);
	
		TelescopeEmbeddedAgent tea = new TelescopeEmbeddedAgent(id);
	
		try {
			tea.configure(configFile);
		} catch (Exception e) {	   
			tea.traceLog.dumpStack(1,e); 
			usage("Configuring TEA: "+e);
			System.exit(2);
		}
	
		try {
			tea.start();
		} catch (Exception e) {
			tea.traceLog.dumpStack(1,e);			
		} finally {	    
			tea.shutdown();
		}
	
	}

	/** 
	 * Configure the TEA from a properties file. Load the properties.  
	 * @throws IOException if any IO/file errors.
	 * @throws IllegalArgumentException if any properties missing, wrong, illegal.
	 * @see #properties
	 * @see #configureProperties
	 */
	public void configure(File file) throws IOException, IllegalArgumentException
	{
		ConfigurationProperties config = new ConfigurationProperties();
		config.load(new FileInputStream(file));
		configure(config);
		configureProperties(file);
	}

	/** Configure the TEA from a ConfigurationProperties.
	 * @throws IOException if any IO/file errors.
	 * @throws IllegalArgumentException if any properties missing, wrong, illegal.
	 */
	public void configure(ConfigurationProperties config) throws IOException, IllegalArgumentException {
	
		int dnPort = config.getIntValue("dn.port", DEFAULT_DN_PORT);
	
		int ossPort = config.getIntValue("oss.port", DEFAULT_OSS_PORT);
	
		String ossHost = config.getProperty("oss.host", DEFAULT_OSS_HOST);

		boolean ossSecure = (config.getProperty("oss.secure") != null);
	
		String okf   = config.getProperty("oss.keyfile", DEFAULT_OSS_KEY_FILE_NAME);
		File ossKeyFile = new File(okf);
		
		String otf   = config.getProperty("oss.trustfile", DEFAULT_OSS_TRUST_FILE_NAME);
		File ossTrustFile = new File(otf);
		
		String osspass = config.getProperty("oss.pass",   DEFAULT_OSS_KEY_FILE_PASSWORD);
	
		int ctrlPort = config.getIntValue("ctrl.port", DEFAULT_CTRL_PORT);
	
		String ctrlHost = config.getProperty("ctrl.host", DEFAULT_CTRL_HOST);

		int telPort = config.getIntValue("telemetry.port", DEFAULT_TELEMETRY_PORT);

		String telHost = config.getProperty("telemetry.host", DEFAULT_TELEMETRY_HOST);
	
		String id = config.getProperty("id", DEFAULT_ID);
	
		// Specify as degrees
		double dlat = config.getDoubleValue("latitude", DEFAULT_LATITUDE);
	
		// Specify as degrees
		double dlong = config.getDoubleValue("longitude", DEFAULT_LONGITUDE);
	
		double domeLimit =  config.getDoubleValue("dome.limit", DEFAULT_DOME_LIMIT);
	
		long maxTime = config.getLongValue("max.obs", DEFAULT_MAX_OBS_TIME);
		maxTime = maxTime*1000L;

		String bdir = config.getProperty("base.dir", DEFAULT_IMAGE_DIR);
	
		String dbroot = config.getProperty("db.root", DEFAULT_DB_ROOT);

		String filterMapFileName = config.getProperty("filter.map.file");


		boolean relaySecure = (config.getProperty("relay.secure") != null);

		String rkf   = config.getProperty("ssl.keyfile", DEFAULT_SSL_KEY_FILE_NAME);
		File relayKeyFile = new File(rkf);

		String rtf   = config.getProperty("ssl.trustfile", DEFAULT_SSL_TRUST_FILE_NAME);
		File relayTrustFile = new File(rtf);

		String rpass = config.getProperty("ssl.pass",   DEFAULT_SSL_KEY_FILE_PASSWORD);

		int    band  = config.getIntValue("bandwidth",  DEFAULT_SSL_FILE_XFER_BANDWIDTH);

		String rhost = config.getProperty("relay.host", DEFAULT_RELAY_HOST);

		int    rport = config.getIntValue("relay.port", DEFAULT_RELAY_PORT);

		String documentDirectory        = config.getProperty("document.dir", DEFAULT_DOCUMENT_DIR);
		String expiredDocumentDirectory = config.getProperty("expired.document.dir",
								     DEFAULT_EXPIRED_DOCUMENT_DIR);
		long expiratorSleepMs = config.getLongValue("expirator.sleep", DEFAULT_EXPIRATOR_POLLING_TIME);

		String arm = config.getProperty("async.response.mode", "SOCKET");

		if ("RMI".equals(arm)) {		
		    String nh = config.getProperty("async.response.handler.host", "localhost");
		    setAsyncResponseMode(ASYNC_MODE_RMI);
		    setNarHost(nh);
		} else if
		    ("SOCKET".equals(arm)){
		    setAsyncResponseMode(ASYNC_MODE_SOCKET);	
		} else
		    throw new IllegalArgumentException("Unknown response handler mode; "+arm);

		String tpf = config.getProperty("tap.persistance.file", DEFAULT_TAP_PERSISTANCE_FILE_NAME);
		File tpFile = new File(tpf);
	
		setDnPort(dnPort);
		setOssHost(ossHost);
		setOssPort(ossPort);
		setOssConnectionSecure(ossSecure);
		setOssKeyFile(ossKeyFile);
		setOssTrustFile(ossTrustFile);
		setOssPassword(osspass);
		setCtrlHost(ctrlHost);
		setCtrlPort(ctrlPort);
		setTelemetryHost(telHost);
		setTelemetryPort(telPort);
		setRelayHost(rhost);
		setRelayPort(rport);
		setSiteLatitude(Math.toRadians(dlat));
		setSiteLongitude(Math.toRadians(dlong));
		setDomeLimit(Math.toRadians(domeLimit));
		setImageDir(bdir);
		setDBRootName(dbroot);
	
		setRelaySecure(relaySecure);
		setRelayKeyFile(relayKeyFile);
		setRelayTrustFile(relayTrustFile);
		setRelayPassword(rpass);
		setTransferBandwidth(band);

		setMaxObservingTime(maxTime);

		setDocumentDirectory(documentDirectory);
		setExpiredDocumentDirectory(expiredDocumentDirectory);
		setExpiratorSleepMs(expiratorSleepMs);

		setTapPersistanceFile(tpFile);

		if (filterMapFileName != null) {
			File file = new File(filterMapFileName);		    
			configureFilters(file);		    
		} else {
			throw new IllegalArgumentException("Filter properties file not specified");
		}

		Position.setViewpoint(Math.toRadians(dlat), Math.toRadians(dlong));	

		// If we cant have mail - keep going...				
		String smtpHost = config.getProperty("smtp.host", "localhost");
		try {
		    mailer = new Mailer(smtpHost);
		    String mailToAddr = config.getProperty("mail.to", "eng@astro.livjm.ac.uk");  
		    mailer.setMailToAddr(mailToAddr);
		    String mailFromAddr = config.getProperty("mail.from", id+"_tea@astro.livjm.ac.uk");  
		    mailer.setMailFromAddr(mailFromAddr);
		    String mailCcAddr = config.getProperty("mail.cc", "eng@astro.livjm.ac.uk");  
		    mailer.setMailCcAddr(mailCcAddr);	

		    mailer.setMailSubj("TEA-Error: ("+id+")");
	
		} catch (Exception e) {
		    e.printStackTrace();
		}


	} // [Configure(ConfigProperties)]
    
	/** Prints a message and usage instructions.*/
	private static void usage(String message) {
		System.err.println("TEA initialization: "+message);
		System.err.println("Usage: java -Djava.security.egd=<entropy-gathering-url> "+
				   "\n -Dastrometry.impl=<astrometry-class> \\"+
				   "\n [-DregisterHandler=true] "+
				   "\n org.estar.tea.TelescopeEmbeddedAgent <id> <config-file> "); 
	
	}
    
	/** Returns a reference to the TEAConnectionFactory.
	 * Valid conection IDs are:-
	 *
	 * <dl>
	 *  <dt>OSS<dd>OSS Transaction Server: For ongoing DB entries and corrections.
	 *  <dt>CTRL<dd>RCS Control Server: For status comands.
	 *  <dt>TOCS<dd>RCS TOC Server: For TO control.
	 * </dl>
	 */
	public ConnectionFactory getConnectionFactory() { return connectionFactory; }
    
	private class TEAConnectionFactory implements ConnectionFactory {
	
	    public IConnection createConnection(String connId) throws UnknownResourceException {
	    
			if (connId.equals("CTRL")) {
				return new SocketConnection(ctrlHost, ctrlPort);
			} else if
				(connId.equals("OSS")) {
				return new SocketConnection(ossHost, ossPort);
			} else if
			    (connId.equals("OSS_SECURE")) {

			    try {
				KeyManager[]   kms = getKeyManagers(ossKeyFile, ossPassword);
				TrustManager[] tms = getTrustManagers(ossTrustFile);
				
				SSLContext context = SSLContext.getInstance("TLS");
				
				context.init(kms, tms, null);
				
				SSLSocketFactory secureSocketFactory = (SSLSocketFactory)context.getSocketFactory();
				
				return new SocketConnection(ossHost, ossPort, secureSocketFactory );
				
			    } catch (Exception e) {
				e.printStackTrace();
				throw new UnknownResourceException("Unable to create secure connection: "+e);
			    }
			} else
			    throw new UnknownResourceException("Not known: "+connId);
		}
	
	    private KeyManager[] getKeyManagers(File keyStore, String password) throws Exception {
            String alg = KeyManagerFactory.getDefaultAlgorithm();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(alg);
            FileInputStream fis = new FileInputStream(keyStore);
            KeyStore ks = KeyStore.getInstance("jks");
            ks.load(fis, password.toCharArray());
            fis.close();
            kmf.init(ks, password.toCharArray());
            KeyManager[] kms = kmf.getKeyManagers();
            return kms;
        }

        private TrustManager[] getTrustManagers(File trustStore) throws Exception {

            String alg = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(alg);
            FileInputStream fis = new FileInputStream(trustStore);
            KeyStore ks = KeyStore.getInstance("jks");
            ks.load(fis, "public".toCharArray());
            fis.close();
            tmf.init(ks);
            TrustManager[] tms = tmf.getTrustManagers();
            return tms;
        }



	}
    
}

/* 
** $Log: not supported by cvs2svn $
** Revision 1.31  2007/07/06 15:16:25  cjm
** Added test for host/port == null/0 in sendDocumentToIA.
** This stops us trying to send docs with no IA on.
**
** Revision 1.30  2007/07/06 11:32:01  cjm
** no difference.
**
** Revision 1.29  2007/05/25 10:49:25  snf
** added set mail subj
**
** Revision 1.28  2007/05/25 08:12:14  snf
** tcheckin
**
** Revision 1.27  2007/05/25 08:04:42  snf
** added TAP persistance file as config parameter "tap.persistance.file"
**
** Revision 1.26  2007/05/04 09:32:33  cjm
** Added a flush to saveDocument's FileOutputStream.
**
** Revision 1.25  2007/05/02 07:23:59  snf
** removed spurious loggers.
**
** Revision 1.24  2007/04/04 08:55:52  snf
** removed spurious loggers, using TRACE for all main loggers and bogstanlogformatter
**
** Revision 1.23  2007/02/20 13:19:27  snf
** added a stack dump to tea createErrorDoc if the error cannot be created - weird
**
** Revision 1.22  2006/12/11 11:49:13  snf
** Added a test for rename doc failure in expireDoc soas to throw an Exception.
**
** Revision 1.21  2006/03/27 13:40:58  snf
** added estario logging
**
** Revision 1.20  2005/06/16 17:49:00  cjm
** Added sendDocumentToIA (previously sendDocUpdate in AgentRequestHandler.java).
**
** Revision 1.19  2005/06/16 10:06:18  cjm
** Javadoc fixes.
**
** Revision 1.18  2005/06/15 15:42:56  cjm
** Various changes. Logging upgrades for new TOCSession stuff.
**
/** Revision 1.17  2005/06/10 13:57:39  snf
/** Added getters for lat and longitude.
/**
/** Revision 1.16  2005/06/02 08:25:00  snf
/** Added call to set the ARQs id using tea.id plus its own.
/**
/** Revision 1.15  2005/06/02 08:22:43  snf
/** Commented out unused methods addDoc, getDoc.
/**
/** Revision 1.14  2005/06/02 08:16:01  snf
/** Disabled the DocExpirator for testing of ARQ's internal checking.
/**
/** Revision 1.13  2005/06/02 06:29:18  snf
/** Changed createNewFileName() to include document-base directory.
/**
/** Revision 1.12  2005/06/01 16:07:24  snf
/** Updated to use new architecture.
/**
/** Revision 1.11  2005/05/27 14:05:39  snf
/** First phse of upgrade completed.
/**
/** Revision 1.10  2005/05/27 09:43:39  snf
/** Added call to createUpdateHandler before starting it.
/**
/** Revision 1.9  2005/05/27 09:35:20  snf
/** Started modification to attach an AgentRequestHandler to each request over its lifetime.
/**
/** Revision 1.8  2005/05/25 14:27:56  snf
/** Changed handling of plugin properties to allow future porject-specific handling.
/** */
