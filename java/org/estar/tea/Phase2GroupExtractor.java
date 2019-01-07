package org.estar.tea;

import org.estar.rtml.*;
import org.estar.astrometry.*;

import ngat.phase2.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.astrometry.*;

import java.util.*;

/** Extracts Phase2 group from a supplied RTMLDoc.*/
public class Phase2GroupExtractor implements Logging {

    /** Default maximum unguidd exposure length (ms).*/
    public static final long DEFAULT_MAXIMUM_UNGUIDED_EXPOSURE = 24*3600*1000L;

    public static String CLASS = "Phase2GroupExtract";

    private Logger logger;

    private TelescopeEmbeddedAgent tea;

    public Phase2GroupExtractor( TelescopeEmbeddedAgent tea) {
	this.tea = tea;
	logger = LogManager.getLogger("TRACE");
    }


    /** Extract a group from the doc.*/
    public Group extractGroup(RTMLDocument document) throws IllegalArgumentException {

	String cid = document.getUId();

	// Tag/User ID combo is what we expect here.
        RTMLContact contact = document.getContact();

        if (contact == null) {
            logger.log(INFO, 1, CLASS, cid,"handleRequest",
                       "RTML Contact was not specified, failing request.");
	    throw new IllegalArgumentException("No contact was supplied");
        }

        String userId = contact.getUser();

        if (userId == null) {
            logger.log(INFO,1,CLASS,cid,"handleRequest",
                       "RTML Contact User was not specified, failing request.");
            throw new IllegalArgumentException("Your User ID was null");
        }
	//	userId = userId.replaceAll("\\W", "_");

        // The Proposal ID.
        RTMLProject project = document.getProject();
        String proposalId = project.getProject();

        if (proposalId == null) {
            logger.log(INFO,1,CLASS,cid,"handleRequest",
                       "RTML Project was not specified, failing request.");
            throw new IllegalArgumentException("Your Project ID was null");
        }
	//proposalId = proposalId.replaceAll("\\W", "_");


        // Retrieve the documents unique ID, either from the uid attribute or user agent's Id
        // depending on RTML version

	String requestId = document.getUId();
	if (requestId == null) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest",
		       "RTML request ID was not specified, failing request.");
	    throw new IllegalArgumentException("Your Request ID was null");
	}
	requestId = requestId.replaceAll("\\W", "_");

        String gid = requestId;

	// Pull out unified constraints.
	RTMLSchedule master = getUnifiedConstraints(document);

	int schedPriority = master.getPriority();

	double seeing = RequestDocumentHandler.DEFAULT_SEEING_CONSTRAINT; // 1.3
	RTMLSeeingConstraint sc = master.getSeeingConstraint();
	if (sc != null) {
	    seeing = sc.getMaximum();
	}

	double mld = Math.toRadians(15.0); // default minimum disatance unless otherwise selected
	RTMLMoonConstraint mc = master.getMoonConstraint();
	if (mc != null) {
	    mld = mc.getDistanceRadians();
	}

	int lunar = Group.BRIGHT;
	RTMLSkyConstraint skyc = master.getSkyConstraint();
	if (skyc != null) {
	    if (skyc.isBright())
		lunar = Group.BRIGHT;
	    else if
		(skyc.isDark())
		lunar = Group.DARK;
	    else {
		logger.log(INFO,1,CLASS,cid,"handleRequest","Unable to extract sky constraint info");
		throw new IllegalArgumentException("Unable to extract sky brightness constraint");
	    }
	}

        // Extract MG params - many of these can be null !

	RTMLSeriesConstraint scon = master.getSeriesConstraint();

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
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "RTML SeriesConstraint Interval not present, failing request.");
		throw new IllegalArgumentException("No Interval was supplied");
	    } else {
		period = pf.getMilliseconds();

		// No Window => Default to 90% of interval.
		if (tf == null) {
		    logger.log(INFO, 1, CLASS, cid,"executeRequest",
			       "No tolerance supplied, Default window setting to 95% of Interval");
		    tf = new RTMLPeriodFormat();
		    tf.setSeconds(0.95*(double)period/1000.0);
		    scon.setTolerance(tf);
		}
	    }
	    window = tf.getMilliseconds();
	    if (count < 1) {
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "RTML SeriesConstraint Count was negative or zero, failing request.");
		throw new IllegalArgumentException("You have supplied a negative or zero repeat Count.");
	    }

	    if (period < 60000L) {
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "RTML SeriesConstraint Interval is too short, failing request.");
		throw new IllegalArgumentException("You have supplied a ludicrously short monitoring Interval.");
	    }

	    if ((window/period < 0.0) || (window/period > 1.0)) {
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "RTML SeriesConstraint has an odd Window or Period.");
		throw new IllegalArgumentException("Your window or Tolerance looks dubious.");
	    }

	}

	startDate = master.getStartDate();
	endDate   = master.getEndDate();

	// FG and MG need an EndDate, No StartDate => Now.
	if (startDate == null) {
	    logger.log(INFO, 1, CLASS, cid,"executeRequest","Default start date setting to now");
	    startDate = new Date();
	    master.setStartDate(startDate);
	}

	// No End date => StartDate + 1 day (###this is MicroLens -specific).
	if (endDate == null) {
	    logger.log(INFO, 1, CLASS, cid,"executeRequest","Default end date setting to Start + 1 day");
	    endDate = new Date(startDate.getTime()+24*3600*1000L);
	    master.setEndDate(endDate);
	}
	// Basic and incomplete sanity checks.
	if (startDate.after(endDate)) {
	    logger.log(INFO,1,CLASS,cid,"handleRequest","RTML StartDate after EndDate, failing request.");
	    throw new IllegalArgumentException("Your StartDate and EndDate do not make sense.");
	}

	logger.log(INFO, 1, CLASS, cid,"executeScore",
		   "Extracted dates: "+startDate+" -> "+endDate);

	// Look up proposal details.
	String proposalPathName = tea.getDBRootName()+"/"+userId+"/"+proposalId;

	Date completionDate = new Date(System.currentTimeMillis()+365*24*3600*1000L);

	// Make the group	
	Group group = null;

	if (scon == null || count == 1) {
	    // FlexGroup
	    group = new Group(requestId);
	    // set the startingdate which is the only way a flex-group can pass over 
	    // its start time to scoredoc handler.
	    group.setStartingDate(startDate.getTime());

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

	group.setMinimumLunarDistance(mld);
	group.setMinimumLunar(lunar);
	group.setTwilightUsageMode(Group.TWILIGHT_USAGE_OPTIONAL);

	// map rtml priorities to Phase2 priorities.
        int priority = 0;
        switch (schedPriority) {
        case 0:
            priority = 5; // Phase2 - ODB: MOST URGENT(5)
            break;
        case 1:
            priority = 4; // Phase2 - ODB: QUITE URGENT(4)
            break;
        case 2:
            priority = 3; // Phase2 - ODB: HIGH(3)
            break;
        case 3:
            priority = 2; // Phase2 - ODB: MEDIUM(2)
            break;
        case 4:
            priority = 1; // Phase2 - ODB: NORMAL(1)
            break;
        default:
            priority = 1; // Phase2 - ODB: NORMAL(1)
        }
        group.setPriority(priority);

        // set seeing limits.
        group.setMinimumSeeing(Group.POOR);
        if (seeing >= 1.3) {
            group.setMinimumSeeing(Group.POOR);
        } else if(seeing >= 0.8) {
            group.setMinimumSeeing(Group.AVERAGE);
        } else {
            // this will also catch any with silly values like < 0.0 !
            group.setMinimumSeeing(Group.EXCELLENT);
        }

        // Extract the Observation request(s) - handle multiple obs per doc.

        int nobs = document.getObservationListCount();

        for (int iobs = 0; iobs < nobs; iobs++) {

	    RTMLObservation obs = document.getObservation(iobs);
	    
	    // Extract params
	    RTMLTarget target = obs.getTarget();

	    RA  ra  = target.getRA();
	    Dec dec = target.getDec();
	    
	    String targetId = target.getName();
	    if (targetId == null || targetId.equals(""))
		targetId = "Target_"+iobs+"_"+requestId;
	    // e.g. Target_2_UA123
	    targetId.replaceAll("\\W","_");

	    // Bizarre element.
	    String targetIdent = target.getIdent();
	    
	    RTMLSchedule sched = obs.getSchedule();
	    
	    String expy = sched.getExposureType();
	    String expu = sched.getExposureUnits();
	    double expt = 0.0;

	    expt = sched.getExposureLengthMilliseconds();
	    
	    int expCount = sched.getExposureCount();
	    
	    // Extract filter info.
	    RTMLDevice dev = obs.getDevice();
	    String filter = null;
	    
	    // make up the IC - we dont have enough info to do this from filtermap...
	    InstrumentConfig config = null;
	    String configId = null;
	    
	    if (dev == null)
		dev = document.getDevice();
	    
	    if (dev != null) {
		
		// START New DEVINST stuff
		try {
		    config = DeviceInstrumentUtilites.getInstrumentConfig(tea, dev);
		    configId = config.getName();
		} catch (Exception e) {
		    logger.log(INFO,1,CLASS,cid,"handleRequest",
			       "Device configuration error: "+e);
		    logger.dumpStack(1,e);
		    throw new IllegalArgumentException("Device configuration error: "+e);
		}
		// END New DEVINST stuff
		
	    } else {
		logger.log(INFO,1,CLASS,cid,"handleRequest", "RTML Device not present");
		throw new IllegalArgumentException("Device not set");
	    }

	    if (expt < 1000.0) {
		logger.log(INFO,1,CLASS,cid,"handleRequest","Exposure time is too short, failing request.");
		throw new IllegalArgumentException("Your Exposure time is too short.");
	    }
	    
	    if (expCount < 1) {
		logger.log(INFO,1,CLASS,cid,"handleRequest",
			   "Exposure Count is less than 1, failing request.");
		throw new IllegalArgumentException("Your Exposure Count is less than 1.");
	    }
	    
	    ExtraSolarSource source = new ExtraSolarSource(targetId);
	    source.setRA(ra.toRadians());
	    source.setDec(dec.toRadians());
	    source.setFrame(Source.FK5);
	    source.setEquinox(2000.0f);
	    source.setEpoch(2000.0f);
	    source.setEquinoxLetter('J');
	    
	    double raOffset  = target.getRAOffset();
	    double decOffset = target.getDecOffset();

	    Position atarg = new Position(ra.toRadians(), dec.toRadians());
	    
	    float expose = (float)expt;
	    // Maybe split into chunks NO NOT YET.
	    //if ((double)expose > (double)tea.getMaxObservingTime()) {
	    //int nn = (int)Math.ceil((double)expose/(double)tea.getMaxObservingTime());
	    
	    //}
	    int mult = expCount;

	    String obsId = ""+iobs;	    
	    Observation observation = new Observation(obsId);
	    
	    observation.setExposeTime(expose);
	    observation.setNumRuns(mult);
	    observation.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_NEVER);
	    
	    Mosaic mosaic = new Mosaic();
	    mosaic.setPattern(Mosaic.SINGLE);
	    observation.setMosaic(mosaic);
	    
	    observation.setSource(source);
	    observation.setSourceOffsetRA(raOffset);
            observation.setSourceOffsetDec(decOffset);
	    
	    observation.setInstrumentConfig(config);

	    // Decide if we need to use the autoguider;
            long maxUnguidedExposureLength = DEFAULT_MAXIMUM_UNGUIDED_EXPOSURE;
            try {
                maxUnguidedExposureLength = tea.getPropertyLong("maximum.unguided.exposure.length");
            } catch (Exception ee) {
                logger.log(INFO,1,CLASS,cid,"handleRequest",
                           "There was a problem locating the property: maximum.unguided.exposure.length");
            }


	    // decide whether to use autoguider
	    if ((long)expt > maxUnguidedExposureLength)
		observation.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_OPTIONAL);
	    else
		observation.setAutoGuiderUsageMode(TelescopeConfig.AGMODE_NEVER);

	    // ARGH another massive/evil fudge, more instrument-specifics...
	    if (config instanceof PolarimeterConfig) {
		observation.setRotatorMode(TelescopeConfig.ROTATOR_MODE_MOUNT);
		observation.setRotatorAngle(0.0);
	    }

	    // AARGH another massive/evil fudge, more instrument-specifics...
	    if (config instanceof LowResSpecConfig) {
		//TelescopeConfig.ACQUIRE_MODE_BRIGHTEST = 1;
		//TelescopeConfig.ACQUIRE_MODE_WCS       = 2;
		// somehow we need to be able to decide which of these to select....
		//maybe using exposure length and other stufff
		observation.setAcquisitionMode(TelescopeConfig.ACQUIRE_MODE_WCS);
	    }

	    // path will be set here
	    group.addObservation(observation);
	    
	} // next observation

	return group;
	    
    } // [extractGroup]


	/** 
	 * Check the various constraints and return a unified set.
	 * @param document The RTMLDocument with potentially more than one set of schedule constraints.
	 * @return The unified set of constraints.
	 * @exception IllegalArgumentException Thrown if the various constraints in the documents are not
	 *           identical.
	 * @see #equalsOrNull
	 */
	private RTMLSchedule getUnifiedConstraints(RTMLDocument document) throws IllegalArgumentException
	{
	
		RTMLSeriesConstraint masterSeries,currentSeries;
		RTMLMoonConstraint masterMoon,currentMoon;
		RTMLSeeingConstraint masterSeeing, currentSeeing;
		RTMLSkyConstraint masterSky, currentSky;
		RTMLSchedule master;
		Date masterStartDate, currentStartDate;
		Date masterEndDate, currentEndDate;
		int masterPriority, currentPriority;

		int nobs = document.getObservationListCount();
	
		if (nobs == 0)
			return null;
		// #0 will be our master...
		master = document.getObservation(0).getSchedule();
		if(master == null)
		{
			masterStartDate = null;
			masterEndDate = null;
			masterSeries = null;
			masterMoon = null;
			masterSeeing = null;
			masterSky = null;
			masterPriority = -1;
		}
		else
		{
			masterStartDate = master.getStartDate();
			masterEndDate = master.getEndDate();
			masterSeries = master.getSeriesConstraint();
			masterMoon = master.getMoonConstraint();
			masterSeeing = master.getSeeingConstraint();
			masterSky = master.getSkyConstraint();
			masterPriority = master.getPriority();
		}
		for (int iobs = 1; iobs < nobs; iobs++)
		{
			RTMLObservation obs = document.getObservation(iobs);	    
			RTMLSchedule sched = obs.getSchedule();
			if(sched == null)
			{
				currentSeries = null;
				currentMoon = null;
				currentSeeing = null;
				currentSky = null;
				currentStartDate = null;
				currentEndDate = null;
				currentPriority = -1;
			}
			else
			{
				currentSeries = sched.getSeriesConstraint();
				currentMoon = sched.getMoonConstraint();
				currentSeeing = sched.getSeeingConstraint();
				currentSky = sched.getSkyConstraint();
				currentStartDate = sched.getStartDate();
				currentEndDate = sched.getEndDate();
				currentPriority = sched.getPriority();
			}

			if (! equalsOrNull(currentStartDate,masterStartDate))
		throw new IllegalArgumentException("Constraint mismatch: StartDate for "+iobs+" ss="+sched.getStartDate()+" ms="+master.getStartDate());
	    
			if (! equalsOrNull(currentEndDate,masterEndDate))
				throw new IllegalArgumentException("Constraint mismatch: EndDate for "+iobs+
								   " se="+sched.getEndDate()+" me="+master.getEndDate());
			
			if (! equalsOrNull(currentSeries,masterSeries))
				throw new IllegalArgumentException("Constraint mismatch: SeriesConstraint for "+iobs+
								   " ss="+sched.getSeriesConstraint()+
								   " ms="+master.getSeriesConstraint());
	    
			if (! equalsOrNull(currentMoon,masterMoon))
				throw new IllegalArgumentException("Constraint mismatch: MoonConstraint for "+iobs);

			if (! equalsOrNull(currentSeeing,masterSeeing))
				throw new IllegalArgumentException("Constraint mismatch: SeeingConstraint for "+iobs);

			if (! equalsOrNull(currentSky,masterSky))
				throw new IllegalArgumentException("Constraint mismatch: SkyConstraint for "+iobs);

			if (currentPriority != masterPriority)
				throw new IllegalArgumentException("Constraint mismatch: Sched priority "+iobs);
			
		}
		return master;
	} // [getUnifiedConstraints]

	/**
	 * Method that checks the equality of two objects, given one or the other may be null.
	 * If both are null they are the same, if one is null and one isn't they must be different,
	 * otherwise calls the object's equals method as a comparator.
	 * @param o1 The first object.
	 * @param o2 The second object.
	 * @return true if both objects are equals according to this method's rules, false otherwise.
	 */
	protected boolean equalsOrNull(Object o1,Object o2)
	{
		// if both are null they are the same
		if((o1 == null) && (o2 == null))
			return true;
		// if one object is null and one is not they are different
		if(((o1 != null) && (o2 == null))||((o1 == null) && (o2 != null)))
			return false;
		// both objects _must_ be non-null here
		return o1.equals(o2);
	}
}
