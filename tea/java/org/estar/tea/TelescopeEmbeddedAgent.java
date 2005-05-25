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

import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;


/**
 * This class implements a Telescope Embedded Agent.
 */
public class TelescopeEmbeddedAgent implements eSTARIOConnectionListener, Logging {
    
    /**
     * Revision control system version id.
     */
    public final static String RCSID = "$Id: TelescopeEmbeddedAgent.java,v 1.7 2005-05-25 13:16:46 snf Exp $";

    public static final String CLASS = "TelescopeEA";
    
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
    
    /** Default port for RCS TOCS. (TOC requests).*/
    public static final int    DEFAULT_TOCS_PORT = 8610;
   
    /** Default host address for RCS TOCS. (TOC requests).*/
    public static final String DEFAULT_TOCS_HOST = "192.168.1.30";

    
    public static final String DEFAULT_IMAGE_DIR = "/home/estar/nosuchdir";
    
    public static final double DEFAULT_LATITUDE = 0.0;
    
    public static final double DEFAULT_LONGITUDE = 0.0;
    
    public static final double DEFAULT_DOME_LIMIT = 0.0;

    public static final String DEFAULT_FILTER_COMBO = "RATCam-Clear-2";
    
    public static final String DEFAULT_DB_ROOT = "/DEV_Phase2_001";
    
    public static final String DEFAULT_TOCS_SERVICE_ID = "GRB";

    public static final String DEFAULT_SSL_KEY_FILE_NAME   = "nosuchkeystore.private";
    
    public static final String DEFAULT_SSL_TRUST_FILE_NAME = "nosuchtruststore.public";
    
    public static final String DEFAULT_SSL_KEY_FILE_PASSWORD    = "secret";
    
    public static final int    DEFAULT_SSL_FILE_XFER_BANDWIDTH = 50; // kbyte/s
    
    public static final long DEFAULT_TELEMETRY_RECONNECT_TIME = 600*1000L;
    
    public static final String DEFAULT_DOCUMENT_DIR   = "data";
    
    public static final String DEFAULT_EXPIRED_DOCUMENT_DIR   = "expired";
    
    public static final long DEFAULT_EXPIRATOR_POLLING_TIME   = 3600*1000L;
    
    public static final long DEFAULT_EXPIRATOR_OFFSET_TIME    = 1800*1000L;

    public static final int GROUP_PRIORITY = 5;

    /**
     * The default filename for the tea pipeline properties file.
     */
    public static final String DEFAULT_PLUGIN_PROPERTIES_FILENAME   = "../config/tea.properties";
    
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
    
    /** RCS CTRL Server port.*/
    protected int ctrlPort;
    
    /** RCS CTRL host address.*/
    protected String ctrlHost;
    
    /** RCS TOCS Server port.*/
    protected int tocsPort;

    /** RCS TOCS host address.*/
    protected String tocsHost;

    /** RCS SFX Server port.*/
    protected int relayPort;
    
    /** RCS SFX host address.*/
    protected String relayHost;

    protected File   relayKeyFile;
    protected File   relayTrustFile;
    protected String relayPassword;

    protected int    transferBandwidth;


    /** Image transfer client.*/
    protected SSLFileTransfer.Client sslClient;
    
    /** ConnectionFactory.*/
    protected ConnectionFactory connectionFactory;
    
    /** Base URL for image server - where generated images will be served from.*/
    protected String imageWebUrl;
    
    /** Base dir where images are placed.*/
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

    /** ### TOCS Service ID - should be a mapping from something in RTML doc to a SvcID.*/
    protected String tocsServiceId;

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
     * Plugin properties loaded from the tea properties file 
     * (infact currently just the basic tea.properties file).
     */
    protected NGATProperties pluginProperties = null;
    
    /** Counts connections from UA(s).*/
    protected int connectionCount;

    /** Holds mapping between filter descriptions and filter combos for PCR.*/
    protected Properties filterMap;

    protected Map requestMap;
    
    protected Map fileMap;
    
    protected Map agentMap;
    
    protected TelemetryReceiver telemetryServer;

    /** Max observing time - ### this should be determined by the OSS via TEST_SCHEDULEImpl.*/
    protected long maxObservingTime;

    /** Keeps on requesting telemetry from RCS incase the RCS loses its subscriber list.*/
    protected TelemetryRequestor telemetryRequestor;

    /** Checks Documents for expiration.*/
    protected DocumentExpirator docExpirator;
   
    /** Create a TEA with the supplied ID.*/
    public TelescopeEmbeddedAgent(String id) {
	
	Logger logger = null;

	this.id = id;

	io = new eSTARIO();
	
	ConsoleLogHandler console = new ConsoleLogHandler(new BasicLogFormatter(150));
	console.setLogLevel(ALL);
	
	traceLog   = LogManager.getLogger("TRACE");
	traceLog.setLogLevel(ALL);	
	traceLog.addHandler(console);
	
	// add handler to other loggers used
	logger = LogManager.getLogger("org.estar.tea.TelemetryRequestor");
	logger.setLogLevel(ALL);
	logger.addHandler(console);
	logger = LogManager.getLogger("org.estar.tea.TelemetryHandlerFactory");
	logger.setLogLevel(ALL);
	logger.addHandler(console);
	logger = LogManager.getLogger("org.estar.tea.TelemetryHandler");
	logger.setLogLevel(ALL);
	logger.addHandler(console);
	logger = LogManager.getLogger("org.estar.tea.UpdateHandler");
	logger.setLogLevel(ALL);
	logger.addHandler(console);
	logger = LogManager.getLogger("org.estar.tea.DocumentExpirator");
	logger.setLogLevel(ALL);
	logger.addHandler(console);
	logger = LogManager.getLogger("org.estar.tea.AgentRequestHandler");
	logger.setLogLevel(ALL);
	logger.addHandler(console);
	logger = LogManager.getLogger("org.estar.tea.DefaultPipelinePlugin");
	logger.setLogLevel(ALL);
	logger.addHandler(console);
	
	filterMap = new Properties();

	requestMap = new HashMap();
	fileMap    = new HashMap();
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
		     "DocExpirator with polling interval: "+
		     (expiratorSleepMs/1000)+" secs");
	
	sslClient = new SSLFileTransfer.Client("TEA_GRABBER", relayHost, relayPort);
	
	sslClient.setBandWidth(transferBandwidth);

	// If we fail to setup we just can't grab images back but other stuff will work.
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
	
	traceLog.log(INFO, 1, CLASS, id, "init",
		     "Starting Telescope Embedded Agent: "+id+
		     "\n Incoming port: "+dnPort+		     
		     "\n  OSS:          "+ossHost+" : "+ossPort+
		     "\n    Root:       "+dbRootName+
		     "\n    Connect:    "+(ossConnectionSecure ? " SECURE" : " NON_SECURE")+
		     "\n  TOCS:         "+tocsHost+": "+tocsPort+
		     "\n    Svc:        "+tocsServiceId+
		     "\n  Ctrl:         "+ctrlHost+" : "+ctrlPort+
		     "\n"+
		     "\n Telescope:"+
		     "\n  Lat:          "+Position.toDegrees(siteLatitude, 3)+
		     "\n  Long:         "+Position.toDegrees(siteLongitude,3)+
		     "\n  Dome limit:   "+Position.toDegrees(domeLimit, 3));

	traceLog.log(INFO, 1, CLASS, id, "init",
		     "looking for any documents in persistence store");
	try {
	    loadDocuments();
	    traceLog.log(INFO, 1, CLASS, id, "init",
			 "Loaded all current documents from persistence store"+
			 "- TBD Associate ARQs and start UH Threads for these...");
	} catch (Exception e) {
	    traceLog.log(INFO, 1, CLASS, id, "init",
			 "Failed to load current documents from persistence store: "+e);
	    traceLog.dumpStack(1,e);
	}

	telemetryServer.start();	
	traceLog.log(INFO, 1, CLASS, id, "init",
		     "Started Telemetry Server on port: "+telemetryPort+
		     ", We are: "+telemetryHost+" from "+ctrlHost);
	
	telemetryRequestor.start();
	traceLog.log(INFO, 1, CLASS, id, "init",
		     "Started Telemetry Requestor with reconnect interval: "+
		     (DEFAULT_TELEMETRY_RECONNECT_TIME/1000)+" secs");
	
	docExpirator.start();
	traceLog.log(INFO, 1, CLASS, id, "init",
		     "Started DocExpirator with polling interval: "+
		     (expiratorSleepMs/1000)+" secs");
	
	traceLog.log(INFO, 1, CLASS, id, "init",
		     "Starting eSTAR IO server.");
	io.serverStart(dnPort, this);	
	traceLog.log(INFO, 1, CLASS, id, "init",
		     "Started eSTAR IO server.");
	
	traceLog.log(INFO, 1, CLASS, id, "init",
		     "TEA: "+id+" is up and running");

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

    /** Sets the DN (incoming) port.*/
    public void setDnPort(int p) { this.dnPort = p; }
    
    /** Sets the OSS port.*/
    public void setOssPort(int p) { this.ossPort = p; }
    
    /** Sets the OSS host address.*/
    public void setOssHost(String h) { this.ossHost = h; }

    /** Sets whether the OSS Connection is secure.*/    
    public void setOssConnectionSecure(boolean s) { this.ossConnectionSecure = s; }

    /** Sets the RCS CTRL port.*/
    public void setCtrlPort(int p) { this.ctrlPort = p; }
    
    /** Sets the RCS CTRL host address.*/
    public void setCtrlHost(String h) { this.ctrlHost = h; }

    /** Sets the RCS TCOS port.*/
    public void setTocsPort(int p) { this.tocsPort = p; }
    
    /** Sets the RCS TOCS host address.*/
    public void setTocsHost(String h) { this.tocsHost = h; }
   
    /** Sets the Telemetry port.*/
    public void setTelemetryPort(int p) { this.telemetryPort = p; }

    /** Sets the Telemetry  host address.*/
    public void setTelemetryHost(String h) { this.telemetryHost = h; }

    /** Sets the SSL Fileserver host address.*/
    public void setRelayHost(String r) { this.relayHost = r; }

    /** Sets the SSL Fileserver port.*/
    public void setRelayPort(int p) { this.relayPort = p; }
    
    /** Sets the Base dir where transferred images are placed.*/
    public void setImageDir(String d) { this.imageDir = d; }
    
    /** Sets the site latitude (rads).*/
    public void setSiteLatitude(double l) { this.siteLatitude = l; }
    
    /** Sets the site longitude (rads).*/
    public void setSiteLongitude(double l) { this.siteLongitude = l; }
    
    /** Sets the dome limit (rads).*/
    public void setDomeLimit(double d) { this.domeLimit = d; }
    
    /** Sets the DB rootname.*/
    public void setDBRootName(String r) { this.dbRootName = r; }
    
    /** Sets the TOCS Service ID.*/
    public void setTocsServiceId(String s) { this.tocsServiceId = s; }
    
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
     * @param filename A tea pipeline property filename.
     * @see #properties
     * @exception FileNotFoundException Thrown if the file doesn't exist. 
     * @exception IOException Thrown if the load failed.
     */
    public void setPluginProperties(String filename) throws java.io.FileNotFoundException,
						      java.io.IOException
    {
	pluginProperties = new NGATProperties();
	pluginProperties.load(filename);
    }
    
    /** Returns the Id for this TEA.*/
    public String getId() { return id; }

    /** Returns the DB rootname.*/
    public String getDBRootName() {  return dbRootName; }
    
    /** Returns the TOCS service ID.*/
    public String getTocsServiceId() { return tocsServiceId; }

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
    
    /** RCS TOCS Server port.*/
    public int getTocsPort() { return tocsPort; }
    
    /** RCS TOCS host address.*/
    public String getTocsHost() { return tocsHost; }
    
    /** Telemetry Server port.*/
    public int getTelemetryPort() { return telemetryPort; }
    
    /** Telemetry Server host.*/
    public String getTelemetryHost() { return telemetryHost; }

    /** Image transfer client.*/
    public SSLFileTransfer.Client getImageTransferClient() { return sslClient; }

    /** Base dir where transferred images are placed.*/
    public String getImageDir() { return imageDir; }

    public double getDomeLimit() { return domeLimit; }
    
    /**Base URL for web image server - where transferred images will be served from.*/
    public String getImageWebUrl() { return imageWebUrl; }
    
    public Properties getFilterMap() { return filterMap; }
    
    public Map getRequestMap() { return requestMap; }
    
    public long getMaxObservingTime() { return maxObservingTime; }
    
    /**
     * Get an string value using the specified key from the loaded tea properties.
     * @param key The key.
     * @return The key's value in the properties.
     * @see #properties
     * @exception NullPointerException Thrown if the properties are null.
     */
    public String getPropertyString(String key) throws NullPointerException
    {
	if(pluginProperties == null)
	    {
		throw new NullPointerException(this.getClass().getName()+
					       ":getPropertiesString:properties were null.");
	    }
	return pluginProperties.getProperty(key);
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
	if(pluginProperties == null)
	    {
		throw new NullPointerException(this.getClass().getName()+
					       ":getPropertiesInteger:properties were null.");
	    }
	return pluginProperties.getInt(key);
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
	if(pluginProperties == null)
		{
		    throw new NullPointerException(this.getClass().getName()+
						   ":getPropertiesDouble:properties were null.");
		}
	return pluginProperties.getDouble(key);
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
	if(pluginProperties == null)
	    {
		throw new NullPointerException(this.getClass().getName()+
					       ":getPropertiesBoolean:properties were null.");
	    }
	return pluginProperties.getBoolean(key);
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
	if(pluginProperties == null)
	    {
		throw new NullPointerException(this.getClass().getName()+
					       ":getPropertiesClass:properties were null.");
	    }
	return pluginProperties.getClass(key);
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
    
    /** Add an UH for obsid.*/
    public void addUpdateHandler(String key, UpdateHandler uh) {
	agentMap.put(key, uh);
    }
    
    /** Find an UH for obsid.*/
    public UpdateHandler getUpdateHandler(String key) {
	if (agentMap.containsKey(key))
	    return (UpdateHandler)agentMap.get(key);
	return null;
    }

    /** Remove the UH for obsid.*/
    public void deleteUpdateHandler(String key) {
	if (agentMap.containsKey(key))
	    agentMap.remove(key);
    }
    
    // ---------------------------------------------
    // Document Persistance Mutators and Extractors.
    // ---------------------------------------------
    
    /** Returns an Iterator over the set of document keys.*/
    public Iterator listDocumentKeys() { 
	return requestMap.keySet().iterator();
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
    
    /**
     * Add a document into request map for supplied key and saves to file.
     * @param document The document to save.
     * @throws Exception If anything goes horribly wrong.
     * @see #createKeyFromDoc
     */
    public void addDocument(RTMLDocument document) throws Exception
    {
	String key = createKeyFromDoc(document);
	requestMap.put(key, document);
	saveDocument(key, document);
    }
    
    /** Get the Doucument for the supplied key or null.
     * @param key      Document's group path.
     */
    public RTMLDocument getDocument(String key) {
	return (RTMLDocument)requestMap.get(key);
    }
    
    /** 
     * Save the document. Assumes the document already has a key->filename mapping,
     * which is used to get the filename to save the document into.
     * @param document The document to save.
     * @throws Exception If anything goes horribly wrong.
     * @see #createKeyFromDoc
     * @see #saveDocument(String,RTMLDocument)
     */
    public void saveDocument(RTMLDocument document) throws Exception
    {
	String key = createKeyFromDoc(document);
	saveDocument(key,document);
    }
    
    /** 
     * Save the document with group path supplied.
     * Note this creates a new unique key -> filename mapping, if one does not already exist.
     * @param key      Document's group path.
     * @param document The document to save.
     * @throws Exception If anything goes horribly wrong.
     * @see #createFileName
     * @see #documentDirectory
     */
    public void saveDocument(String key, RTMLDocument document) throws Exception
    {
	String fname = null;
	
	traceLog.log(INFO, 1, CLASS, id, "saveDocument",
		     "TEA::Started saving document "+key+".");
	if (fileMap.containsKey(key))
	    {
		fname = (String)(fileMap.get(key));
	    }
	else
	    fname = createFileName(key);
	traceLog.log(INFO, 1, CLASS, id, "saveDocument",
		     "TEA::Document "+key+" has filename "+fname+".");
	File docFile = new File(documentDirectory, fname);
	FileOutputStream fos = new FileOutputStream(docFile);
	RTMLCreate create = new RTMLCreate();
	create.create(document);
	create.toStream(fos);
	
	fileMap.put(key, fname);
	
	System.err.println("TEA::Saved document for path: "+key+" as file: "+docFile.getPath());
	traceLog.log(INFO, 1, CLASS, id, "saveDocument",
		     "TEA::Saved document for path: "+key+" as file: "+docFile.getPath());	
    }
    
    /** 
     * Load all current rtml docs as at startup.
     * @see #documentDirectory
     */
    public void loadDocuments() throws Exception
    {
	traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
		     "TEA::Started loadDocuments from "+documentDirectory+".");
	File base = new File(documentDirectory);
	File[] flist = base.listFiles();
	for (int i = 0; i < flist.length; i++ ){
	    
	    File file = flist[i];
	    String fname = file.getName();
	    traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
			 "Loading from file: "+file.getPath());
	    
	    RTMLDocument doc = readDocument(file);
	    traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
			 "Read document "+file.getPath());
	    String key = createKeyFromDoc(doc);
	    traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
			 "Created key "+key);
	    
	    requestMap.put(key, doc);
	    fileMap.put(key, fname);
	    
	    traceLog.log(INFO, 1, CLASS, id, "loadDocuments",
			 "Loaded document for path: "+key+" from file: "+file.getPath());
	    
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
     * Delete the document.
     * First figure out the key from the document using createKeyFromDoc.
     * @param document The document.
     * @throws FileNotFoundException if key exists but file does not.
     * @see #createKeyFromDoc
     */
    public void deleteDocument(RTMLDocument document) throws Exception
    {
	String key = null;
	
	key = createKeyFromDoc(document);
	deleteDocument(key);
    }
    
    /** 
     * Delete the document represented by key from persistence store and in-memory.
     * If key not present fails silently. 
     * @param key Document's group path.
     * @throws FileNotFoundException if key exists but file does not.
     * @see #documentDirectory
     * @see #expiredDocumentDirectory
     */
    public void deleteDocument(String key) throws Exception
    {
	if (requestMap.containsKey(key))
	    {
		requestMap.remove(key);
		
		if (fileMap.containsKey(key))
		    {
			String fname = (String)fileMap.get(key);
			File docFile = new File(documentDirectory, fname);
			File expiredFile = new File(expiredDocumentDirectory,fname);
			// rename
			docFile.renameTo(expiredFile);
			fileMap.remove(key);
		    }
	    }
    }

    /** Creates a filename for the supplied key.*/
    private String createFileName(String key) {
	return "doc-"+System.currentTimeMillis()+".rtml";
    }

    
    /** 
     * Gets the GroupID from the RTMLDocument supplied.
     * @param doc The RTMLDocument.
     * @return A string unique to a document (group).
     * @exception Exception Thrown if the document's contact/project,intelligent agent is null.
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
	
    }
    
    /** Configure the filter combos from a file - ### this is camera only for now..*/
    protected void configureFilters(File file) throws IOException {
	
	FileInputStream fin = new FileInputStream(file);
	filterMap.load(fin);
	
    }
    
    /**Create an error (type = 'reject' from scratch).
     * @param errorMessage The error messsage.
     */
    public String createErrorDocReply(String errorMessage) {
	RTMLDocument document = new RTMLDocument();	
	return createErrorDocReply(document, errorMessage);
    }
    
    /** Create an error (type = 'reject' from supplied document.
     * @param document The document to change to error type.
     * @param errorMessage The error messsage.
     */
    public String createErrorDocReply(RTMLDocument document, String errorMessage) {
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
    public String createDocReply(RTMLDocument document, String type) {
	System.err.println("Create doc reply using type: "+type);
	// diddly error!
	document.setCompletionTime(new Date());
	document.setType(type);
	return createReply(document);
    }
    
    /** Creates a reply message from the supplied document.
     * @param document The document to extract a reply message from.
     * @return A reply message in RTML format.
     */
    public String createReply(RTMLDocument document) {
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
    


    /** Configures and starts a Telescope Embedded Agent.*/
    public static void main(String args[]) {
	
	// args[0] is the ID args[1] is a config file - use abs filename preferably.
	
	if (args == null || args.length < 2) {
	    usage("No args supplied");
	    System.exit(1);
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

    /** Configure the TEA from a file.   
     * @throws IOException if any IO/file errors.
     * @throws IllegalArgumentException if any properties missing, wrong, illegal.
     */
    public void configure(File file) throws IOException, IllegalArgumentException {
	ConfigurationProperties config = new ConfigurationProperties();
	config.load(new FileInputStream(file));
	configure(config);
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
	
		int ctrlPort = config.getIntValue("ctrl.port", DEFAULT_CTRL_PORT);
	
		String ctrlHost = config.getProperty("ctrl.host", DEFAULT_CTRL_HOST);

		int telPort = config.getIntValue("telemetry.port", DEFAULT_TELEMETRY_PORT);

		String telHost = config.getProperty("telemetry.host", DEFAULT_TELEMETRY_HOST);
	
		int tocsPort = config.getIntValue("tocs.port", DEFAULT_TOCS_PORT);
	
		String tocsHost = config.getProperty("tocs.host", DEFAULT_TOCS_HOST);
	
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

		String tocsServiceId = config.getProperty("service", DEFAULT_TOCS_SERVICE_ID);

		String rkf = config.getProperty("ssl.keyfile", DEFAULT_SSL_KEY_FILE_NAME);
		File relayKeyFile = new File(rkf);

		String rtf = config.getProperty("ssl.trustfile", DEFAULT_SSL_TRUST_FILE_NAME);
		File relayTrustFile = new File(rtf);

		String rpass = config.getProperty("ssl.pass", DEFAULT_SSL_KEY_FILE_PASSWORD);

		int band = config.getIntValue("bandwidth", DEFAULT_SSL_FILE_XFER_BANDWIDTH);

		String rhost = config.getProperty("relay.host", DEFAULT_RELAY_HOST);

		int rport = config.getIntValue("relay.port", DEFAULT_RELAY_PORT);

		String documentDirectory        = config.getProperty("document.dir", DEFAULT_DOCUMENT_DIR);
		String expiredDocumentDirectory = config.getProperty("expired.document.dir",
								     DEFAULT_EXPIRED_DOCUMENT_DIR);
		long expiratorSleepMs = config.getLongValue("expirator.sleep", DEFAULT_EXPIRATOR_POLLING_TIME);

		String pluginPropertiesFilename = config.getProperty("plugin.properties", DEFAULT_PLUGIN_PROPERTIES_FILENAME);

	
		setDnPort(dnPort);
		setOssHost(ossHost);
		setOssPort(ossPort);
		setOssConnectionSecure(ossSecure);
		setCtrlHost(ctrlHost);
		setCtrlPort(ctrlPort);
		setTocsHost(tocsHost);
		setTocsPort(tocsPort);
		setTelemetryHost(telHost);
		setTelemetryPort(telPort);
		setRelayHost(rhost);
		setRelayPort(rport);
		setSiteLatitude(Math.toRadians(dlat));
		setSiteLongitude(Math.toRadians(dlong));
		setDomeLimit(Math.toRadians(domeLimit));
		setImageDir(bdir);
		setDBRootName(dbroot);
		setTocsServiceId(tocsServiceId);

		setRelayKeyFile(relayKeyFile);
		setRelayTrustFile(relayTrustFile);
		setRelayPassword(rpass);
		setTransferBandwidth(band);

		setMaxObservingTime(maxTime);

		setDocumentDirectory(documentDirectory);
		setExpiredDocumentDirectory(expiredDocumentDirectory);
		setExpiratorSleepMs(expiratorSleepMs);
		
		setPluginProperties(pluginPropertiesFilename);
	
		if (filterMapFileName != null) {
		    File file = new File(filterMapFileName);		    
		    configureFilters(file);		    
		} else {
		    throw new IllegalArgumentException("Filter properties file not specified");
		}

		Position.setViewpoint(Math.toRadians(dlat), Math.toRadians(dlong));	
	
    } // [Configure(ConfigProperties)]
    
    private static void usage(String message) {
	System.err.println("TEA initialization: "+message);
	System.err.println("Usage: java -Djava.security.egd=<entropy-gathering-url> -Dastrometry.impl=<astrometry-class> org.estar.tea.TelescopeEmbeddedAgent [id] [config-file]");
	
    }

    /** Returns ref to the TEAConnectionFactory.
     * Valid conection IDs are:-
     *
     * <dl>
     *  <dt>OSS<dd>OSS: For ongoing DB entries and corrections.
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
	    } else
		throw new UnknownResourceException("Not known: "+connId);
	}
	
    }
    
}
