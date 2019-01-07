package org.estar.tea.scoring;

import ngat.phase2.*;
import ngat.util.*;
import ngat.net.*;
import ngat.message.OSS.*;
import ngat.message.base.*;
import ngat.astrometry.*;
import ngat.util.logging.*;

import org.estar.tea.*;
import org.estar.rtml.*;
import org.estar.astrometry.*;

import java.util.*;
import java.io.*;
import java.text.*;
import java.net.*;
import javax.net.*;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;


/** Send a scoring request for a group.*/
public class ScoringTest {

    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static String rootname;

    static Logger logger;

    /** Run the test.*/
    public static void main(String args[]) {

	try {
	    
	    logger = LogManager.getLogger("TRACE");
	    logger.setLogLevel(5);
	    ConsoleLogHandler con = new ConsoleLogHandler(new BasicLogFormatter(150));
	    con.setLogLevel(5);
	    logger.addHandler(con);
	    
	    Logger jms = LogManager.getLogger("JMS");
	    jms.setLogLevel(5);
	    jms.addHandler(con);

	    CommandTokenizer parser = new CommandTokenizer("--");
	    parser.parse(args);
	    ConfigurationProperties config = parser.getMap();

	    final String host = config.getProperty("oss");
	    final int    port = config.getIntValue("port");

	    Group group = null;
	    Position target = null;

	    rootname = config.getProperty("root");

	    boolean rtml = (config.getProperty("rtml") != null);

	    if (rtml) {
		
		String rtmlFilename = config.getProperty("rtml");
		group = createRtmlGroup(new File(rtmlFilename));
		System.err.println("Made group: "+group);
		target = getRtmlTarget(new File(rtmlFilename));
	    } else {
		
		String gFilename = config.getProperty("group");
		group = createGroup(new File(gFilename));
	    }


	    long start = (sdf.parse(config.getProperty("start"))).getTime();
	    long end   = (sdf.parse(config.getProperty("end"))).getTime();
	    long resolution = config.getLongValue("resolution");


	    final boolean secure = (config.getProperty("secure") != null);
	    
	    File kmf = new File(config.getProperty("kmf", "test.private"));
	    File tmf = new File(config.getProperty("tmf", "oar.public"));
	    String pass = config.getProperty("pass");

	    ConnectionFactory cf = new MyConnectionFactory(host,port,secure, kmf, tmf, pass);

	    double latitude = Math.toRadians(config.getDoubleValue("lat"));
	    double longitude = Math.toRadians(config.getDoubleValue("long"));
	    double domeLimit = Math.toRadians(config.getDoubleValue("dome-limit"));

	
	    SCHEDULABILITY tsched = new SCHEDULABILITY("ScoreTest");
	    tsched.setClientDescriptor(new ClientDescriptor("ScoringTest",
							    ClientDescriptor.ADMIN_CLIENT,
							    ClientDescriptor.ADMIN_PRIORITY));
	    tsched.setCrypto(new Crypto("SCORE-TEST"));
	    
	    tsched.setGroup(group);
	    tsched.setStart(start);
	    tsched.setEnd(end);
	    tsched.setResolution(resolution);
	    
	    JMSCommandHandler client = new JMSCommandHandler(cf, 
							     tsched, 
							     false);
	    
	    //freeLock();	
	    client.send();
	    //waitOnLock();
	  
	    if (client.isError()) {
		System.err.println("Internal error during SCHEDULABILITY check: "+
				   client.getErrorMessage());
		
	    } else {
		SCHEDULABILITY_DONE sched_done = (SCHEDULABILITY_DONE)client.getReply();
		double score = sched_done.getSchedulability();
		System.err.println("Group scored [new] "+score+" for specified period");

		VisibilityCalculator vc = new VisibilityCalculator(latitude, longitude, domeLimit, Math.toRadians(-12.0));

		double vscore = vc.calculateVisibility(target, start, end);
		System.err.println("Group scored [old] "+vscore+" for specified period");

		System.err.println("Combined score "+(score*vscore));		

	    }

	} catch (Exception e) {
	    e.printStackTrace();

	    System.err.println("ScoringTest --oss <oss> --port <oss port> --group <cfg-file> --start <yyyy-MM-dd HH:mm:ss> -end  <yyyy-MM-dd HH:mm:ss>");

	    return;
	}

    }

    public static Group createGroup(File file) throws Exception {
	ConfigurationProperties config = new ConfigurationProperties();
	config.load(new FileInputStream(file));

	// extract parameters for group test
	Group group = null;
	
	if (config.getProperty("group") != null) {
	    String name = config.getProperty("grp-name", "Test");
	    group = new Group(name);
	    
	    String lunar = config.getProperty("lunar", "bright");
	    if ("bright".equals(lunar))
		group.setMinimumLunar(Group.BRIGHT);
	    else
		group.setMinimumLunar(Group.DARK);
	    
	    String see = config.getProperty("seeing", "poor");
	    if ("poor".equals(see))
		group.setMinimumSeeing(Group.POOR);
	    else if
		("av".equals(see))
		group.setMinimumSeeing(Group.AVERAGE);
	    else if
		("ex".equals(see))
		group.setMinimumSeeing(Group.EXCELLENT);
	    else
		group.setMinimumSeeing(Group.CRAP);
	    
	    int priority = config.getIntValue("priority", 1);
	    group.setPriority(priority);
	    
	    int nobs = config.getIntValue("grp-nobs", 1);
	    double expose = config.getDoubleValue("expose");
	    int nm = config.getIntValue("mult",1);
	    
	    long gstart = (sdf.parse(config.getProperty("grp-start"))).getTime();
	    long gend   = (sdf.parse(config.getProperty("grp-end"))).getTime();
	    
	    group.setStartingDate(gstart);
	    group.setExpiryDate(gend);
	    
	    double ra = Position.parseHMS(config.getProperty("tgt-ra"));
	    double dec = Position.parseDMS(config.getProperty("tgt-dec"));
	    
	    ExtraSolarSource src = new ExtraSolarSource(name+"-tgt");
	    src.setRA(ra);
	    src.setDec(dec);
	    
	    for (int io = 0; io < nobs; io++) {
		Observation obs = new Observation("obs-"+io);
		obs.setExposeTime((float)expose);
		obs.setNumRuns(nm);
		obs.setNumRuns(nm);
		Mosaic mosaic = new Mosaic();
		mosaic.setPattern(Mosaic.SINGLE);
		obs.setMosaic(mosaic);
		obs.setInstrumentConfig(new CCDConfig());
		obs.setSource(src);
		group.addObservation(obs);
	    }
	    
	}
	
	return group;
    }

    public static class MyConnectionFactory implements ConnectionFactory {

	String  host;
	int     port;
	boolean secure;
	File kmf;
	File tmf;
	String pass;

	public MyConnectionFactory(String host, int port, boolean secure, File kmf, File tmf, String pass) {
	    this.host = host;
	    this.port = port;
	    this.secure = secure;
	    this.kmf = kmf;
	    this.tmf = tmf;
	    this.pass = pass;
	}

	public IConnection createConnection(String id) {
	    try {
		if (secure) {
		    
		    KeyManager[]   kms = getKeyManagers(kmf, pass);
		    TrustManager[] tms = getTrustManagers(tmf);
		    
		    SSLContext context = SSLContext.getInstance("TLS");
		    
		    context.init(kms, tms, null);
		    
		    SSLSocketFactory secureSocketFactory = (SSLSocketFactory)context.getSocketFactory();
		    
		    return new SocketConnection(host,port, secureSocketFactory );
		    
		} else {
		    
		    return new SocketConnection(host,port);
		}
	    } catch (Exception e) {
		e.printStackTrace();
		return null;
	    }
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

    public static Group createRtmlGroup(File file) throws Exception {
 
	FileInputStream fin = new FileInputStream(file);

	RTMLParser parser = new RTMLParser();
	RTMLDocument document = parser.parse(fin);

	System.err.println("Parsed: "+document);

	long now = System.currentTimeMillis();
	
	Observation observation = null;
	Group       group       = null;	 
	
	// Tag/User ID combo is what we expect here.
	
	RTMLContact contact = document.getContact();
	
	if (contact == null) {
	    System.err.println(
		       "RTML Contact was not specified, failing request.");
	    return null;
	}
	
	String userId = contact.getUser();
	
	if (userId == null) {
	    System.err.println(
		       "RTML Contact User was not specified, failing request.");
	    return null;
	}
	
	// The Proposal ID.
	RTMLProject project = document.getProject();
	String proposalId = project.getProject();
	
	if (proposalId == null) {
	    System.err.println(
		       "RTML Project was not specified, failing request.");
	    return null;
	}
	
	// We will use this as the Group ID otherwise use 'default agent'.
	RTMLIntelligentAgent userAgent = document.getIntelligentAgent();
	
	if (userAgent == null) {
	    System.err.println(
		       "RTML Intelligent Agent was not specified, failing request.");
	    return null;
	}
	
	String requestId = userAgent.getId();
	
	// Extract the Observation request(s) - handle multiple obs per doc.
	
	//int nobs = getObservationListCount();
	
	//for (int iobs = 0; iobs < nobs; iobs++) {
	
	RTMLObservation obs = document.getObservation(0);
	//RTMLObservation obs = document.getObservation(iobs);
	
	// Extract params
	RTMLTarget target = obs.getTarget();


	// Ok its a scheduled group, extract the relevant params.
	
	RA  ra  = target.getRA();		    
	Dec dec = target.getDec();      
	 	
	String targetId = target.getName();
	// Bizarre element.
	String targetIdent = target.getIdent();
	
	RTMLSchedule sched = obs.getSchedule();
	
	String expy = sched.getExposureType();
	String expu = sched.getExposureUnits();
	double expt = 0.0;
	
	try {
	    expt = sched.getExposureLengthMilliseconds();
	}
	catch (IllegalArgumentException iax) {
	    System.err.println("Unable to extract exposure time:"+iax);
	    return null;
	}
	
	int expCount = sched.getExposureCount();
	
	int schedPriority = sched.getPriority();
	
	// Phase2 has no concept of "best seeing" so dont use sc.getMinimum()	 
	double seeing = RequestDocumentHandler.DEFAULT_SEEING_CONSTRAINT; // 1.3
	RTMLSeeingConstraint sc = sched.getSeeingConstraint();
	if (sc != null) {
	    seeing = sc.getMaximum();
	}
	
	// Extract filter info.
	RTMLDevice dev = obs.getDevice();
	String filter = null;
	
	if (dev == null)
	    dev = document.getDevice();
	
	if (dev != null) {
	    
	    String type = dev.getType();
	    String filterString = dev.getFilterType();
	    
	    if (type.equals("camera")) {
		
		// We will need to extract the instrument name from the type field.
		//String instName = tea.getConfig().getProperty("camera.instrument", "Ratcam");
		
	
	    } else {		    
		System.err.println("Device is not a camera: failing request.");
		return null;
	    }
	} else {
	    System.err.println("RTML Device not present, failing request.");
	    return null;
	}
	
	// Extract MG params - many of these can be null !	
	
	RTMLSeriesConstraint scon = sched.getSeriesConstraint();
	
	int count    = 0;
	long window  = 0L;
	long period  = 0L;	
	Date startDate = null;
	Date endDate   = null;
	
	if (scon == null) {
	    // No SC supplied => FlexGroup.
	    count = 1;
	} else {
	    
	    // SC supplied => MonitorGroup.		
	    count = scon.getCount();
	    RTMLPeriodFormat pf = scon.getInterval();
	    RTMLPeriodFormat tf = scon.getTolerance();
	    
	    // No Interval => Wobbly
	    if (pf == null) { 
		System.err.println(
			   "RTML SeriesConstraint Interval not present, failing request.");
		return null;
	    } else {
		period = pf.getMilliseconds();
		
		// No Window => Default to 90% of interval.
		if (tf == null) {
		    System.err.println(
			       "No tolerance supplied, Default window setting to 95% of Interval");
		    tf = new RTMLPeriodFormat();
		    tf.setSeconds(0.95*(double)period/1000.0);
		    scon.setTolerance(tf);	  
		}
	    }
	    window = tf.getMilliseconds();
	    
	    if (count < 1) {
		System.err.println(
			   "RTML SeriesConstraint Count was negative or zero, failing request.");
		return null;
	    }
	    
	    if (period < 60000L) {
		System.err.println(
			   "RTML SeriesConstraint Interval is too short, failing request.");
		return null;
	    }
	    
	    if ((window/period < 0.0) || (window/period > 1.0)) {
		System.err.println(
			   "RTML SeriesConstraint has an odd Window or Period.");
		return null;
	    }
	    
	}
	
	startDate = sched.getStartDate();
	endDate   = sched.getEndDate();
	
	// FG and MG need an EndDate, No StartDate => Now.
	if (startDate == null) {
	    System.err.println( "Default start date setting to now");
	    startDate = new Date(now);
	    sched.setStartDate(startDate);
	}
	
	// No End date => StartDate + 1 day (###this is MicroLens -specific).
	if (endDate == null) {
	    System.err.println( "Default end date setting to Start + 1 day");
	    endDate = new Date(startDate.getTime()+24*3600*1000L);
	    sched.setEndDate(endDate);
	}
	
	// Basic and incomplete sanity checks.
	if (startDate.after(endDate)) {
	    System.err.println("RTML StartDate after EndDate, failing request.");
	    return null;
	}
	
	if (expt < 1000.0) {
	    System.err.println("Exposure time is too short, failing request.");
	    return null;
	}
	
	if (expCount < 1) {
	    System.err.println(
		       "Exposure Count is less than 1, failing request.");
	    return null;
	}
	
	logger.log(1,
		   "Extracted dates: "+startDate+" -> "+endDate);
	
	// Look up proposal details.
	 
	String proposalPathName = rootname+"/"+userId+"/"+proposalId;
	

	ExtraSolarSource source = new ExtraSolarSource(targetId);
	source.setRA(ra.toRadians());
	source.setDec(dec.toRadians());
	source.setFrame(Source.FK5);
	source.setEquinox(2000.0f);
	source.setEpoch(2000.0f);
	source.setEquinoxLetter('J');
	     
	Date completionDate = new Date(System.currentTimeMillis()+365*24*3600*1000L);
	

	if (scon == null || count == 1) {

	    // FlexGroup 
	    
	    group = new Group(requestId);
	 
	    
	} else {
	    
	    // A MonitorGroup.
	    
	    group = new MonitorGroup(requestId);
	    // MG-Specific
	    ((MonitorGroup)group).setStartDate(startDate.getTime());
	    ((MonitorGroup)group).setEndDate(endDate.getTime());
	    ((MonitorGroup)group).setPeriod(period);
	    ((MonitorGroup)group).setFloatFraction((float)((double)window/(double)period));
	    
	}
	
	group.setPath(proposalPathName);
	
	group.setExpiryDate(endDate.getTime());
	group.setPriority(TelescopeEmbeddedAgent.GROUP_PRIORITY);
	
	group.setMinimumLunar(Group.BRIGHT);
	group.setMinimumSeeing(Group.POOR);
	group.setTwilightUsageMode(Group.TWILIGHT_USAGE_OPTIONAL);
	
	
	float expose = (float)expt;	
	// Maybe split into chunks NO NOT YET.
	//if ((double)expose > (double)tea.getMaxObservingTime()) {
	//int nn = (int)Math.ceil((double)expose/(double)tea.getMaxObservingTime());
	
	//}	    
	int mult = expCount;
	
	observation = new Observation(targetIdent);
	
	observation.setExposeTime(expose);
	observation.setNumRuns(mult);
	observation.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_NEVER);
	
	Mosaic mosaic = new Mosaic();
	mosaic.setPattern(Mosaic.SINGLE);
	observation.setMosaic(mosaic);
	
	observation.setSource(source);
	observation.setInstrumentConfig(new CCDConfig());

	group.addObservation(observation);
	
	
	// map rtml priorities to Phase2 priorities.
	int priority = 0;
	switch (schedPriority) {
	case 0:
	    priority = 4; // TOOP
	    break;
	case 1:
	    priority = 3; // URGENT
	    break;
	case 2:
	    priority = 2; // MEDIUM
	    break;
	case 3:
	    priority = 1; // NORMAL
	    break;
	default:
	    priority = 1; // NORMAL
	}
	group.setPriority(priority);
	
	// set seeing limits.
	if (seeing >= 1.3) {
	    group.setMinimumSeeing(Group.POOR);
	} else if(seeing >= 0.8) {
	    group.setMinimumSeeing(Group.AVERAGE);
	} else {
	    // this will also catch any with silly values like < 0.0 !
	    group.setMinimumSeeing(Group.EXCELLENT);
	}
	
	
	return group;

    }

   
    public static Position getRtmlTarget(File file) throws Exception {
 
	FileInputStream fin = new FileInputStream(file);

	RTMLParser parser = new RTMLParser();
	RTMLDocument document = parser.parse(fin);
	RTMLObservation obs = document.getObservation(0);
	RTMLTarget target = obs.getTarget();
	RA  ra  = target.getRA();	    
	Dec dec = target.getDec();      
	
	return new Position(ra.toRadians(), dec.toRadians());
    }

}
