package org.estar.tea;

import java.io.File;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.text.StyledEditorKit.AlignmentAction;

import ngat.util.logging.*;
import org.estar.rtml.*;
import org.estar.astrometry.*; //import ngat.phase2.*;
import ngat.astrometry.ReferenceFrame;
import ngat.astrometry.SkyBrightnessCalculator;
import ngat.oss.impl.mysql.util.mutators.SkyBrightnessMutator;
import ngat.oss.model.*;
import ngat.phase2.CCDConfig;
import ngat.phase2.Detector;
import ngat.phase2.FrodoSpecConfig;
import ngat.phase2.IAcquisitionConfig;
import ngat.phase2.IAutoguiderConfig;
import ngat.phase2.IObservingConstraint;
import ngat.phase2.IProgram;
import ngat.phase2.IProposal;
import ngat.phase2.IRCamConfig;
import ngat.phase2.IRotatorConfig;
import ngat.phase2.InstrumentConfig;
import ngat.phase2.LowResSpecConfig;
import ngat.phase2.OConfig;
import ngat.phase2.PolarimeterConfig;
import ngat.phase2.RISEConfig;
import ngat.phase2.THORConfig;
import ngat.phase2.Window;
import ngat.phase2.XAcquisitionConfig;
import ngat.phase2.XAirmassConstraint;
import ngat.phase2.XAutoguiderConfig;
import ngat.phase2.XBranchComponent;
import ngat.phase2.XDetectorConfig;
import ngat.phase2.XDualBeamSpectrographInstrumentConfig;
import ngat.phase2.XExecutiveComponent;
import ngat.phase2.XExtraSolarTarget;
import ngat.phase2.XFilterDef;
import ngat.phase2.XFilterSpec;
import ngat.phase2.XFlexibleTimingConstraint;
import ngat.phase2.XGroup;
import ngat.phase2.XImagerInstrumentConfig;
import ngat.phase2.XTipTiltImagerInstrumentConfig;
import ngat.phase2.XInstrumentConfig;
import ngat.phase2.XInstrumentConfigSelector;
import ngat.phase2.XIteratorComponent;
import ngat.phase2.XIteratorRepeatCountCondition;
//import ngat.phase2.XLunarDistanceConstraint;
//import ngat.phase2.XLunarElevationConstraint;
import ngat.phase2.XMinimumIntervalTimingConstraint;
import ngat.phase2.XMonitorTimingConstraint;
import ngat.phase2.XMultipleExposure;
import ngat.phase2.XPhotometricityConstraint;
import ngat.phase2.XPolarimeterInstrumentConfig;
import ngat.phase2.XPositionOffset;
import ngat.phase2.XRotatorConfig;
import ngat.phase2.XSeeingConstraint;
import ngat.phase2.XSkyBrightnessConstraint;
import ngat.phase2.XSlew;
//import ngat.phase2.XSolarElevationConstraint;
import ngat.phase2.XSpectrographInstrumentConfig;
import ngat.phase2.XWindow;

/**
 * 
 */

/**
 * @author eng
 * 
 */
public class Phase2ExtractorTNG implements Logging {

	/** Default maximum unguided exposure length (ms). */
	public static final long DEFAULT_MAXIMUM_UNGUIDED_EXPOSURE = 24 * 3600 * 1000L;

	/**
	 * Minimum difference between positions to require an OFFSET to be used.
	 * (0.1 arcsec)
	 */
	public static final double MIN_OFFSET = Math.toRadians(1 / 36000.0);

	public static final String RATCAM_INSTRUMENT = "RATCam";

	public static String CLASS = "Phase2GroupExtract";

	TelescopeEmbeddedAgent tea;

	IPhase2Model phase2;

	private Logger logger;

	public Phase2ExtractorTNG(TelescopeEmbeddedAgent tea) {
		this.tea = tea;
		logger = LogManager.getLogger("TRACE");

	}

	public RTMLDocument handleRequest(RTMLDocument document) throws Exception {
		String cid = document.getUId();
		logger.log(INFO, 1, CLASS, cid, "handleRequest", "handleRequest for document UId: " + document.getUId());

		if (document.isTOOP()) {
			// Try and get TOCSessionManager context.
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "Request is a TOOP: finding session manager.");
			TOCSessionManager sessionManager = TOCSessionManager.getSessionManagerInstance(tea, document);
			// add the document to the TOCSessionManager
			// if it succeeds addDocument sets the type to "confirmation".
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "Request is a TOOP: Adding document to session manager.");
			document = sessionManager.addDocument(document);
			return document;
		}

		// NOt a TOOP so goes in ODB
		phase2 = tea.getPhase2Model();

		// extract the group info and send off to ODB.
		extractGroup(document);
		String groupPath = extractGroupPath(document);
		// Get a unique file Name off the TEA.
		File file = new File(tea.createNewFileName(groupPath));

		// if we failed then we dont get here anyway - now setup the ARQ OR NOT
		if (tea.getLoadArqs()) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "Creating AgentRequestHandler.");
			AgentRequestHandler arq = new AgentRequestHandler(tea, document);

			arq.setGid(groupPath);
			arq.setName(document.getUId());
			arq.setARQId(tea.getId() + "/" + arq.getName());

			// Its the one we will use.
			arq.setDocumentFile(file);

			// Set the current request as our basedoc.
			arq.setBaseDocument(document);

			
			// Register as handler for the current obs.
			tea.registerHandler(groupPath, arq);
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "Registered running ARQ for: " + groupPath
					+ " Using file: " + file.getPath());

			// Initialize and start the ARQ as UpdateHandler. If the ARQ does
			// not
			// successfully
			// prepare for UpdateHandling it will not be started and we get an
			// exception.
			try {
				arq.prepareUpdateHandler();
				arq.start();
			} catch (Exception e) {
				logger.dumpStack(1, e);
			}
		}
		
		// Save it to the file - we could do this ourself..
		tea.saveDocument(document, file);
		logger.log(INFO, 1, CLASS, cid, "handleRequest", "Saving base document to: " + file.getPath());
		
		// We still send a confirm, even if we cant start the ARQ correctly as
		// the obs is in the DB.
		document.setRequestReply();
		document.addHistoryEntry("TEA:" + tea.getId(), null, "Request confirmed.");
		return document;

	}

	public String extractGroupPath(RTMLDocument document) throws Exception {

		String cid = document.getUId();

		// Tag/User ID combo is what we expect here.
		RTMLContact contact = document.getContact();

		if (contact == null) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML Contact was not specified, failing request.");
			throw new IllegalArgumentException("No contact was supplied");
		}

		String userId = contact.getUser();

		if (userId == null) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML Contact User was not specified, failing request.");
			throw new IllegalArgumentException("Your User ID was null");
		}
		// userId = userId.replaceAll("\\W", "_");

		// The Proposal ID.
		RTMLProject project = document.getProject();
		String proposalId = project.getProject();

		if (proposalId == null) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML Project was not specified, failing request.");
			throw new IllegalArgumentException("Your Project ID was null");
		}

		// we want the program and proposal ids here
		Map proposalMap = tea.getProposalMap();

		if (!proposalMap.containsKey(proposalId))
			throw new Exception("Proposal [" + proposalId + "] not found in proposal mapping");

		// proposalId = proposalId.replaceAll("\\W", "_");

		String requestId = document.getUId();
		if (requestId == null) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML request ID was not specified, failing request.");
			throw new IllegalArgumentException("Your Request ID was null");
		}
		requestId = requestId.replaceAll("\\W", "_");

		String groupPathName = "/ODB/" + userId + "/" + proposalId + "/" + requestId;

		return groupPathName;

	}

	/** Extract a group from the doc. */
	public void extractGroup(RTMLDocument document) throws Exception {

		String cid = document.getUId();

		// Tag/User ID combo is what we expect here.
		RTMLContact contact = document.getContact();

		if (contact == null) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML Contact was not specified, failing request.");
			throw new IllegalArgumentException("No contact was supplied");
		}

		String userId = contact.getUser();

		if (userId == null) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML Contact User was not specified, failing request.");
			throw new IllegalArgumentException("Your User ID was null");
		}
		// userId = userId.replaceAll("\\W", "_");

		// The Proposal ID.
		RTMLProject project = document.getProject();
		String proposalId = project.getProject();

		if (proposalId == null) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML Project was not specified, failing request.");
			throw new IllegalArgumentException("Your Project ID was null");
		}

		Map proposalMap = tea.getProposalMap();
		if (!proposalMap.containsKey(proposalId))
			throw new Exception("Unable to match proposal name: [" + proposalId + "] with known proposals");

		// extract rleevant info...
		ProposalInfo pinfo = (ProposalInfo) proposalMap.get(proposalId);
		logger.log(INFO, 1, CLASS, cid, "handleRequest", "Obtained pinfo for: " + proposalId);

		IProposal proposal = pinfo.getProposal();
		IProgram program = pinfo.getProgram();
		Map programTargets = pinfo.getTargetMap();
		Map programConfigs = pinfo.getConfigMap();
		double balance = pinfo.getAccountBalance();

		if (balance < 0.0)
			throw new Exception("Proposal [" + proposalId + "] allocation account is overdrawn: Bal=" + balance + "h");

		// proposalId = proposalId.replaceAll("\\W", "_");

		// Retrieve the documents unique ID, either from the uid attribute or
		// user agent's Id
		// depending on RTML version

		String requestId = document.getUId();
		if (requestId == null) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML request ID was not specified, failing request.");
			throw new IllegalArgumentException("Your Request ID was null");
		}
		requestId = requestId.replaceAll("\\W", "_");

		// Pull out unified constraints.
		RTMLSchedule master = getUnifiedConstraints(document);

		// we dont care about this anymore.
		int schedPriority = master.getPriority();

		double seeing = RequestDocumentHandler.DEFAULT_SEEING_CONSTRAINT; // 2.0
		RTMLSeeingConstraint sc = master.getSeeingConstraint();
		if (sc != null) {
			seeing = sc.getMaximum();
		}

		double maxair = 2.0;
		RTMLAirmassConstraint airc = master.getAirmassConstraint();
		if (airc != null) {
			maxair = airc.getMaximum();
		}

		boolean photom = false;
		RTMLExtinctionConstraint extinct = master.getExtinctionConstraint();
		if (extinct != null) {
			photom = extinct.isPhotometric();
		}

		// Extract MG params - many of these can be null !

		RTMLSeriesConstraint scon = master.getSeriesConstraint();

		int count = 0;
		long window = 0L;
		long period = 0L;
		Date startDate = null;
		Date endDate = null;

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
				logger.log(INFO, 1, CLASS, cid, "handleRequest",
						"RTML SeriesConstraint Interval not present, failing request.");
				throw new IllegalArgumentException("No Interval was supplied");
			} else {
				period = pf.getMilliseconds();

				// No Window => Default to 90% of interval.
				if (tf == null) {
					logger.log(INFO, 1, CLASS, cid, "executeRequest",
							"No tolerance supplied, Default window setting to 95% of Interval");
					tf = new RTMLPeriodFormat();
					tf.setSeconds(0.95 * (double) period / 1000.0);
					scon.setTolerance(tf);
				}
			}
			window = tf.getMilliseconds();
			if (count < 1) {
				logger.log(INFO, 1, CLASS, cid, "handleRequest",
						"RTML SeriesConstraint Count was negative or zero, failing request.");
				throw new IllegalArgumentException("RTML SeriesConstraint Count was negative or zero.");
			}

			if (period < 60000L) {
				logger.log(INFO, 1, CLASS, cid, "handleRequest",
						"RTML SeriesConstraint Interval is too short, failing request.");
				throw new IllegalArgumentException("You have supplied a ludicrously short monitoring Interval.");
			}

			if ((window / period < 0.0) || (window / period > 1.0)) {
				logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML SeriesConstraint has an odd Window or Period.");
				throw new IllegalArgumentException("Your window or Tolerance looks dubious.");
			}

		}

		startDate = master.getStartDate();
		endDate = master.getEndDate();

		// FG and MG need an EndDate, No StartDate => Now.
		if (startDate == null) {
			logger.log(INFO, 1, CLASS, cid, "executeRequest", "Default start date setting to now");
			startDate = new Date();
			master.setStartDate(startDate);
		}

		// No End date => StartDate + 1 day (###this is MicroLens -specific).
		if (endDate == null) {
			logger.log(INFO, 1, CLASS, cid, "executeRequest", "Default end date setting to Start + 1 day");
			endDate = new Date(startDate.getTime() + 24 * 3600 * 1000L);
			master.setEndDate(endDate);
		}
		// Basic and incomplete sanity checks.
		if (startDate.after(endDate)) {
			logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML StartDate after EndDate, failing request.");
			throw new IllegalArgumentException("Your StartDate and EndDate do not make sense.");
		}

		logger.log(INFO, 1, CLASS, cid, "executeScore", "Extracted dates: " + startDate + " -> " + endDate);

		// Uid contains TAG/user
		String proposalPathName = "/ODB/" + userId + "/" + proposalId;

		// Make the group
		XGroup group = new XGroup();
		group.setName(requestId);
		group.setActive(true);

		if (scon == null || count == 1) {
			// FlexGroup
			XFlexibleTimingConstraint timing = new XFlexibleTimingConstraint();
			timing.setActivationDate(startDate.getTime());
			timing.setExpiryDate(endDate.getTime());
			group.setTimingConstraint(timing);
		} else {

			// if (count == 0) {
			// A MonitorGroup.
			XMonitorTimingConstraint timing = new XMonitorTimingConstraint();
			timing.setStartDate(startDate.getTime());
			timing.setEndDate(endDate.getTime());
			timing.setPeriod(period);
			timing.setWindow(window);
			group.setTimingConstraint(timing);
			// } else {
			// XMinimumIntervalTimingConstraint timing = new
			// XMinimumIntervalTimingConstraint();
			// timing.setStart(startDate.getTime());
			// timing.setEnd(endDate.getTime());
			// timing.setMinimumInterval(period);
			// timing.setMaximumRepeats(count);
			// }
		} // TODO can we support IntervalTiming ? probably...

		group.setPriority(TelescopeEmbeddedAgent.GROUP_PRIORITY);

		/*
		 * XLunarDistanceConstraint xld = new XLunarDistanceConstraint(mld);
		 * group.addObservingConstraint(xld); XLunarElevationConstraint xlev =
		 * new XLunarElevationConstraint(lunar);
		 * group.addObservingConstraint(xlev); XSolarElevationConstraint xsol =
		 * new
		 * XSolarElevationConstraint(IObservingConstraint.ASTRONOMICAL_TWILIGHT
		 * ); group.addObservingConstraint(xsol);
		 */

		// extract the sky-b-category from schedule.
		int skyCat = getSkyBrightnessFromConstraints(master);
		XSkyBrightnessConstraint xskyb = new XSkyBrightnessConstraint(skyCat);
		group.addObservingConstraint(xskyb);

		// loks like we currently allow astro-twilight which is 2 or 4 depending
		// on lunar atm !

		// set seeing limits.
		// int seecat = IObservingConstraint.POOR_SEEING;

		/*
		 * if (seeing >= 3.0) { seecat =
		 * IObservingConstraint.UNCONSTRAINED_SEEING; } else if (seeing >= 1.3)
		 * { seecat = IObservingConstraint.POOR_SEEING; } else if (seeing >=
		 * 0.8) { seecat = IObservingConstraint.AVERAGE_SEEING; } else { // this
		 * will also catch any with silly values like < 0.0 ! seecat =
		 * IObservingConstraint.GOOD_SEEING; }
		 */

		// using the actual seeing value now
		XSeeingConstraint xsee = new XSeeingConstraint(seeing);
		group.addObservingConstraint(xsee);

		if (airc != null) {
			XAirmassConstraint xair = new XAirmassConstraint(maxair);
			group.addObservingConstraint(xair);
		}

		if (photom) {
			int extinctionLevel = XPhotometricityConstraint.PHOTOMETRIC;
			XPhotometricityConstraint xphot = new XPhotometricityConstraint(extinctionLevel, 1.0);
			group.addObservingConstraint(xphot);
		}

		// Extract the Observation request(s) - handle multiple obs per doc.

		XIteratorRepeatCountCondition once = new XIteratorRepeatCountCondition(1);
		XIteratorComponent root = new XIteratorComponent("root", once);

		// keep track of target changes
		XExtraSolarTarget lastTarget = null;

		// keep track of config changes
		XInstrumentConfig lastConfig = null;

		double lastRaOffset = 0.0;
		double lastDecOffset = 0.0;
		boolean isGuiding = false;
		boolean shouldBeGuiding = false;

		// PROCESS EACH RTML OBSERVATION

		int nobs = document.getObservationListCount();
		logger.log(INFO, 1, CLASS, cid, "handleRequest", "Begin processing: " + nobs + " observations in rtml doc");

		String useAlignmentInstrument = null;
		boolean hasFrodoObs = false;

		// FIRST PASS

		// work out which instruments we are using...
		// check if frodo is one of them
		// decide on alignment instrument for slew, at the very least it
		// will be the instrument whose config occurs first
		for (int iobsa = 0; iobsa < nobs; iobsa++) {

			RTMLObservation obs = document.getObservation(iobsa);
			RTMLDevice dev = obs.getDevice();
			if (dev == null)
				dev = document.getDevice();

			if (dev != null) {

				try {
					InstrumentConfig config = DeviceInstrumentUtilites.getInstrumentConfig(tea, dev);
					XInstrumentConfig newConfig = translateToNewStyleConfig(config);

					logger.log(INFO, 1, CLASS, cid, "handleRequest",
							"This observation uses: " + newConfig.getInstrumentName());
					String instName = newConfig.getInstrumentName().toUpperCase();

					if (instName.startsWith("FRODO"))
						hasFrodoObs = true;

					// always align to chosen instrument - this may be wrong for FRODO
					// TODO note we should use first not LAST !
					useAlignmentInstrument = instName;

				} catch (Exception e) {
					logger.log(INFO, 1, CLASS, cid, "handleRequest", "Error determining alignment instrument");
				}
			}

		}

		// create frdo red and blue branches, we may not need them...
		XIteratorComponent redArm = new XIteratorComponent("FRODO_RED", new XIteratorRepeatCountCondition(1));
		XIteratorComponent blueArm = new XIteratorComponent("FRODO_BLUE", new XIteratorRepeatCountCondition(1));

		// PASS 2 extract info to build sequence

		boolean inbranch = false; // record whether we have started a branch or
									// not.

		for (int iobs = 0; iobs < nobs; iobs++) {

			RTMLObservation obs = document.getObservation(iobs);

			// Extract params
			RTMLTarget target = obs.getTarget();

			RA ra = target.getRA();
			Dec dec = target.getDec();

			String targetId = target.getName();
			if (targetId == null || targetId.equals(""))
				targetId = "Target_" + iobs + "_" + requestId;
			// e.g. Target_2_UA123
			targetId.replaceAll("\\W", "_");

			// Bizarre element.
			String targetIdent = target.getIdent();

			RTMLSchedule sched = obs.getSchedule();

			String expy = sched.getExposureType();
			String expu = sched.getExposureUnits();
			double expt = 0.0;

			expt = sched.getExposureLengthMilliseconds();

			int expCount = sched.getExposureCount();

			// -------------------------------------------------------------
			// 0. Decide if we need to use the autoguider for this exposure
			// -------------------------------------------------------------

			// check to see if we should be autoguiding, we may be anyway..
			long maxUnguidedExposureLength = DEFAULT_MAXIMUM_UNGUIDED_EXPOSURE;
			try {
				maxUnguidedExposureLength = tea.getPropertyLong("maximum.unguided.exposure.length");
			} catch (Exception ee) {
				logger.log(INFO, 1, CLASS, cid, "handleRequest",
						"There was a problem locating the property: maximum.unguided.exposure.length");
			}

			if ((long) expt > maxUnguidedExposureLength) {
				shouldBeGuiding = true;
			}

			// --------------------------
			// 1. Handle Target Selection
			// --------------------------

			XExtraSolarTarget star = new XExtraSolarTarget(targetId);
			star.setRa(ra.toRadians());
			star.setDec(dec.toRadians());
			star.setFrame(ReferenceFrame.FK5);
			// OOPS we need something in Phase2 for these frames or should it be
			// in new-astro?
			star.setEpoch(2000.0);

			// is this a known target ? If not we will need to create it in
			// program
			boolean knownTarget = false;
			if (programTargets.containsKey(star.getName())) {
				knownTarget = true;
				star = (XExtraSolarTarget) programTargets.get(star.getName());
			}

			// these are in arcsecs
			double raOffsetArcs = target.getRAOffset();
			double decOffsetArcs = target.getDecOffset();

			double raOffset = Math.toRadians(raOffsetArcs / 3600.0);
			double decOffset = Math.toRadians(decOffsetArcs / 3600.0);

			// is this a new target ? same name AND same offsets
			System.err.println("Compare current target: " + star + " offset: " + raOffsetArcs + "," + decOffsetArcs
					+ "\n         with previous: " + lastTarget + " offset: " + lastRaOffset + "," + lastDecOffset);

			boolean sameTargetAsLast = ((lastTarget != null) && (star.getName().equals(lastTarget.getName())));

			boolean sameTargetButOffset = sameTargetAsLast
					&& ((Math.abs(raOffset - lastRaOffset) > MIN_OFFSET) || (Math.abs(decOffset - lastDecOffset) > MIN_OFFSET));

			boolean hasOffset = (Math.abs(raOffset) > 0.0) || (Math.abs(decOffset) > 0.0);

			boolean diffTargetWithOffsets = !sameTargetAsLast && hasOffset;

			lastRaOffset = raOffset;
			lastDecOffset = decOffset;

			if (!sameTargetAsLast) {
				System.err.println("Switching target");

				if (!knownTarget) {
					// if we dont know about it then create it and add to our
					// records.
					long tid = phase2.addTarget(program.getID(), star);
					star.setID(tid);
					programTargets.put(star.getName(), star);
					System.err.println("Target successfully added to program: " + star);
				}
			}
			lastTarget = star;

			// -----------------------
			// 2. Handle Configuration
			// -----------------------

			// Extract filter info.
			RTMLDevice dev = obs.getDevice();
			String filter = null;

			// make up the IC - we dont have enough info to do this from
			// filtermap...
			InstrumentConfig config = null;
			XInstrumentConfig newConfig = null;
			String configId = null;

			if (dev == null)
				dev = document.getDevice();

			if (dev != null) {

				try {
					config = DeviceInstrumentUtilites.getInstrumentConfig(tea, dev);
					newConfig = translateToNewStyleConfig(config);
					configId = config.getName();
				} catch (Exception e) {
					logger.log(INFO, 1, CLASS, cid, "handleRequest", "Device configuration error: " + e);
					throw new IllegalArgumentException("Device configuration error: " + e);
				}

			} else {
				logger.log(INFO, 1, CLASS, cid, "handleRequest", "RTML Device not present");
				throw new IllegalArgumentException("Device not set");
			}

			// is this a new config ?
			System.err.println("Compare current config: " + newConfig + "\n         with previous: " + lastConfig);
			boolean sameConfigAsLast = ((lastConfig != null) && (newConfig.getName().equals(lastConfig.getName())));

			// same instrument or not ?
			boolean sameInstrumentAsLast = ((lastConfig != null) && (newConfig.getInstrumentName()
					.equalsIgnoreCase(lastConfig.getInstrumentName())));

			// is this a known config ? If not we will need to create it in
			// program
			boolean knownConfig = false;
			if (programConfigs.containsKey(config.getName())) {
				knownConfig = true;
				newConfig = (XInstrumentConfig) programConfigs.get(config.getName());
			}

			if (!sameConfigAsLast) {

				// switching config
				System.err.println("Switching config - and maybe instrument");

				if (!knownConfig) {
					// if we dont know about it then create it and add to our
					// records.
					long ccid = phase2.addInstrumentConfig(program.getID(), newConfig);
					newConfig.setID(ccid);
					programConfigs.put(newConfig.getName(), newConfig);
					System.err.println("Config successfully added to program: " + newConfig);
				}

			}
			lastConfig = newConfig;

			// -----------------------
			// 3. handle exposure
			// -----------------------

			if (expt < 1000.0) {
				logger.log(INFO, 1, CLASS, cid, "handleRequest", "Exposure time is too short, failing request.");
				throw new IllegalArgumentException("Your Exposure time is too short.");
			}

			if (expCount < 1) {
				logger.log(INFO, 1, CLASS, cid, "handleRequest", "Exposure Count is less than 1, failing request.");
				throw new IllegalArgumentException("Your Exposure Count is less than 1.");
			}

			float expose = (float) expt;
			int mult = expCount;

			// At this point gather all the relavnt info together
			// targetChanged, instrumentChanged, configChanged, areAutoguiding,
			// wantAutoguiding

			boolean usingredarm = false;
			boolean usingbluearm = false;

			// is it the first observation, slew and rotate then acquire
			if (iobs == 0) {

				// set rotator config - we dont know at this point which
				// instruments we will be using
				// so how do we set the rotator alignment ?
				// XRotatorConfig xrot = new
				// XRotatorConfig(IRotatorConfig.CARDINAL, 0.0,
				// RATCAM_INSTRUMENT);
				
				// TODO we need to have a SetAcquireInst(uai) here before the slew
				XRotatorConfig xrot = new XRotatorConfig(IRotatorConfig.CARDINAL, 0.0, useAlignmentInstrument);
				// XExecutiveComponent exrot = new
				// XExecutiveComponent("Rotate-Cardinal", xrot);
				// root.addElement(exrot);

				// slew and rotate onto first target
				XSlew xslew = new XSlew(star, xrot, false);
				XExecutiveComponent exslew = new XExecutiveComponent("Slew-" + targetId + "/Cardinal", xslew);
				root.addElement(exslew);

				// test for an offset from base target position
				if (hasOffset) {
					// add an offset
					XPositionOffset xoffset = new XPositionOffset(false, raOffset, decOffset);
					XExecutiveComponent exoffset = new XExecutiveComponent("Offset-(" + raOffsetArcs + ","
							+ decOffsetArcs + ")", xoffset);
					root.addElement(exoffset);
				}

				// acquire first instrument = aperture offset

				// if we are using frodo we aperture offset for IO instead
				// then we do a real acquire

				if (hasFrodoObs) {

					String ACQ_INST_NAME = "IO:O";
					XAcquisitionConfig xap = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					// set the aperture offset onto the acquiring instrument
					xap.setTargetInstrumentName(ACQ_INST_NAME);
					XExecutiveComponent eXAp = new XExecutiveComponent("ApInst", xap);
					root.addElement(eXAp);

					XAcquisitionConfig xaq = new XAcquisitionConfig(IAcquisitionConfig.WCS_FIT);
					// set the target as the real instrument and acquirer to
					// default acquirer
					xaq.setAcquisitionInstrumentName(ACQ_INST_NAME);
					xaq.setTargetInstrumentName(newConfig.getInstrumentName());
					xaq.setPrecision(IAcquisitionConfig.PRECISION_NORMAL);
					XExecutiveComponent eXAcq = new XExecutiveComponent("AcqInst", xaq);
					root.addElement(eXAcq);

					// and create a frodo branch with 2 arms
					XBranchComponent branch = new XBranchComponent("Branch");
					branch.addChildComponent(redArm);
					branch.addChildComponent(blueArm);
					root.addElement(branch);

				} else {
					// TODO setAcqInst(uai) and add(ApertureConfig())
					XAcquisitionConfig xap = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					// set the aperture offset onto the real instrument
					xap.setTargetInstrumentName(newConfig.getInstrumentName());
					XExecutiveComponent eXAcq = new XExecutiveComponent("ApInst", xap);
					root.addElement(eXAcq);

				}

				// select first instr config

				if (hasFrodoObs) {
					logger.log(INFO, 1, CLASS, cid, "handleRequest",
							"Handling frodo config: " + newConfig.getInstrumentName());

					if (newConfig.getInstrumentName().equalsIgnoreCase("FRODO_RED")) {
						usingredarm = true;
						logger.log(INFO, 1, CLASS, cid, "handleRequest", "Config will go in RED arm");
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						redArm.addElement(exinst);
					} else if (newConfig.getInstrumentName().equalsIgnoreCase("FRODO_BLUE")) {
						usingbluearm = true;
						logger.log(INFO, 1, CLASS, cid, "handleRequest", "Config will go in BLUE arm");
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						blueArm.addElement(exinst);
					}
				} else {
					logger.log(INFO, 1, CLASS, cid, "handleRequest",
							"Handling non-frodo config: " + newConfig.getInstrumentName());
					XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
					XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
					root.addElement(exinst);
				}

				// Maybe AG ON ?
				if (shouldBeGuiding && (!isGuiding)) {
					IAutoguiderConfig xAutoOn = new XAutoguiderConfig(IAutoguiderConfig.ON_IF_AVAILABLE, "AutoOn");
					XExecutiveComponent eXAutoOn = new XExecutiveComponent("AutoOn", xAutoOn);
					root.addElement(eXAutoOn);
					isGuiding = true; // We are guiding now (hopefully)
				}

				// exposure
				if (hasFrodoObs) {
					XMultipleExposure xMult = new XMultipleExposure(expose, mult);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xMult);
					if (usingredarm)
						redArm.addElement(exMult);
					else
						blueArm.addElement(exMult);

				} else {
					XMultipleExposure xMult = new XMultipleExposure(expose, mult);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xMult);
					root.addElement(exMult);
				}

			} else {

				// NOT the first obs but a subsequent one

				// any instrument or telescope changes - autooff (if on)
				if ((!sameTargetAsLast) || (!sameInstrumentAsLast) || (sameTargetButOffset)) {
					IAutoguiderConfig xAutoOff = new XAutoguiderConfig(IAutoguiderConfig.OFF, "AutoOff");
					XExecutiveComponent eXAutoOff = new XExecutiveComponent("AutoOff", xAutoOff);
					root.addElement(eXAutoOff);
					isGuiding = false; // We are not guiding now
				}

				// change of instrument
				if (!sameInstrumentAsLast) {
					XAcquisitionConfig xAcq = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					xAcq.setTargetInstrumentName(newConfig.getInstrumentName());
					XExecutiveComponent eXAcq = new XExecutiveComponent("AcqInst", xAcq);
					root.addElement(eXAcq);
				}

				// change of target - allow a rotate change
				if (!sameTargetAsLast) {
					// XTargetSelector xtc = new XTargetSelector(star);
					// no parent target as its a one-off, not a clone
					// XExecutiveComponent extc = new
					// XExecutiveComponent("Target-" + targetId, xtc);
					// root.addElement(extc);

					XRotatorConfig xrot = new XRotatorConfig(IRotatorConfig.CARDINAL, 0.0, RATCAM_INSTRUMENT);
					// XExecutiveComponent exrot = new
					// XExecutiveComponent("Rotate-Cardinal", xrot);
					// root.addElement(exrot);

					// slew and rotate onto first target
					XSlew xslew = new XSlew(star, xrot, false);
					XExecutiveComponent exslew = new XExecutiveComponent("Slew-" + targetId + "/Cardinal", xslew);
					root.addElement(exslew);

					// we have changed target, is there ANY offset at all
					if (hasOffset) {
						System.err.println("Offsetting");
						XPositionOffset xpoff = new XPositionOffset(false, raOffset, decOffset);
						XExecutiveComponent expoff = new XExecutiveComponent("Offset-(" + raOffsetArcs + ","
								+ decOffsetArcs + ")", xpoff);
						root.addElement(expoff);
					}

					// snf (in response to p2ui-comments) 2-sept-2010 add
					// another aperture as we may have lost it
					XAcquisitionConfig xAcq = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					xAcq.setTargetInstrumentName(newConfig.getInstrumentName());
					XExecutiveComponent eXAcq = new XExecutiveComponent("AcqInst", xAcq);
					root.addElement(eXAcq);
				}

				// offset
				if (sameTargetButOffset) {
					// offset
					System.err.println("Offsetting");
					XPositionOffset xpoff = new XPositionOffset(false, raOffset, decOffset);
					XExecutiveComponent expoff = new XExecutiveComponent("Offset-(" + raOffsetArcs + ","
							+ decOffsetArcs + ")", xpoff);
					root.addElement(expoff);
					// snf (in response to p2ui-comments) 2-sept-2010 add
					// another aperture as we may have lost it
					XAcquisitionConfig xAcq = new XAcquisitionConfig(IAcquisitionConfig.INSTRUMENT_CHANGE);
					xAcq.setTargetInstrumentName(newConfig.getInstrumentName());
					XExecutiveComponent eXAcq = new XExecutiveComponent("AcqInst", xAcq);
					root.addElement(eXAcq);

				}

				if (hasFrodoObs) {
					// for frodo we dont care if its same or not its too
					// blinking difficult
					logger.log(INFO, 1, CLASS, cid, "handleRequest",
							"Handling another frodo config: " + newConfig.getInstrumentName());

					if (newConfig.getInstrumentName().equalsIgnoreCase("FRODO_RED")) {
						usingredarm = true;
						logger.log(INFO, 1, CLASS, cid, "handleRequest", "Config will go in BLUE arm");
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						redArm.addElement(exinst);
					} else if (newConfig.getInstrumentName().equalsIgnoreCase("FRODO_BLUE")) {
						usingbluearm = true;
						logger.log(INFO, 1, CLASS, cid, "handleRequest", "Config will go in BLUE arm");
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						blueArm.addElement(exinst);
					}

				} else {
					logger.log(INFO, 1, CLASS, cid, "handleRequest",
							"Handling non-frodo config: " + newConfig.getInstrumentName());
					if (!sameConfigAsLast) {
						XInstrumentConfigSelector xinst = new XInstrumentConfigSelector(newConfig);
						XExecutiveComponent exinst = new XExecutiveComponent("Config-" + configId, xinst);
						root.addElement(exinst);
					}
				}

				// do we AG ON ?
				if (shouldBeGuiding && (!isGuiding)) {
					IAutoguiderConfig xAutoOn = new XAutoguiderConfig(IAutoguiderConfig.ON_IF_AVAILABLE, "AutoOn");
					XExecutiveComponent eXAutoOn = new XExecutiveComponent("AutoOn", xAutoOn);
					root.addElement(eXAutoOn);
					isGuiding = true; // We are guiding now (hopefully)
				}

				// setup exposure
				if (hasFrodoObs) {
					XMultipleExposure xMult = new XMultipleExposure(expose, mult);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xMult);
					if (usingredarm)
						redArm.addElement(exMult);
					else
						blueArm.addElement(exMult);

				} else {
					XMultipleExposure xMult = new XMultipleExposure(expose, mult);
					XExecutiveComponent exMult = new XExecutiveComponent("0", xMult);
					root.addElement(exMult);
				}

			} // not first obs

		} // next observation

		// finally off the guider if its on
		if (isGuiding) {
			IAutoguiderConfig xAutoOff = new XAutoguiderConfig(IAutoguiderConfig.OFF, "AutoOff");
			XExecutiveComponent eXAutoOff = new XExecutiveComponent("AutoOff", xAutoOff);
			root.addElement(eXAutoOff);
		}

		// -----------------------
		// 4. Tear-down
		// ---------------------

		System.err.println("Extracted group from rtml: " + group);
		String sequenceStr = DisplaySeq.display(1, root);
		System.err.println("Extracted obseq from rtml: " + sequenceStr);

		// see which pid is our proposal
		long pid = proposal.getID();

		System.err.println("Attempting to add group to proposal: " + pid);
		long gid = phase2.addGroup(pid, group);
		System.err.println("Group successfully added as ID: " + gid);

		System.err.println("Attempting to set group's sequence...");
		long sid = phase2.addObservationSequence(gid, root);
		System.err.println("Group sequence was successfully set: " + root);

	} // [extractGroup]

	private int getSkyBrightnessFromConstraints(RTMLSchedule master) {

		RTMLSkyConstraint skyc = master.getSkyConstraint();
		if (skyc != null) {

			// TODO could be new SKY_B values
			if (skyc.getUseValue()) {
				double maxSky = skyc.getValue();
				return SkyBrightnessCalculator.getSkyBrightnessCategory(maxSky);
			} else {

				double mld = Math.toRadians(30.0);
				RTMLMoonConstraint mc = master.getMoonConstraint();
				if (mc != null)
					mld = mc.getDistanceRadians();

				boolean dark = false;
				if (skyc.isDark())
					dark = true;

				if (dark || mld < Math.toRadians(30.0))
					return XSkyBrightnessConstraint.MAG_1P5;
				else
					return XSkyBrightnessConstraint.MAG_4;

			}

		}
		return XSkyBrightnessConstraint.MAG_4;

	}

	/**
	 * Check the various constraints and return a unified set.
	 * 
	 * @param document
	 *            The RTMLDocument with potentially more than one set of
	 *            schedule constraints.
	 * @return The unified set of constraints.
	 * @exception IllegalArgumentException
	 *                Thrown if the various constraints in the documents are not
	 *                identical.
	 * @see #equalsOrNull
	 */
	private RTMLSchedule getUnifiedConstraints(RTMLDocument document) throws IllegalArgumentException {

		RTMLSeriesConstraint masterSeries, currentSeries;
		RTMLMoonConstraint masterMoon, currentMoon;
		RTMLSeeingConstraint masterSeeing, currentSeeing;
		RTMLExtinctionConstraint masterExtinct, currentExtinct;
		RTMLAirmassConstraint masterAir, currentAir;
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
		if (master == null) {
			masterStartDate = null;
			masterEndDate = null;
			masterSeries = null;
			masterMoon = null;
			masterSeeing = null;
			masterSky = null;
			masterAir = null;
			masterExtinct = null;
			masterPriority = -1;
		} else {
			masterStartDate = master.getStartDate();
			masterEndDate = master.getEndDate();
			masterSeries = master.getSeriesConstraint();
			masterMoon = master.getMoonConstraint();
			masterSeeing = master.getSeeingConstraint();
			masterSky = master.getSkyConstraint();
			masterAir = master.getAirmassConstraint();
			masterExtinct = master.getExtinctionConstraint();
			masterPriority = master.getPriority();
		}
		for (int iobs = 1; iobs < nobs; iobs++) {
			RTMLObservation obs = document.getObservation(iobs);
			RTMLSchedule sched = obs.getSchedule();
			if (sched == null) {
				currentSeries = null;
				currentMoon = null;
				currentSeeing = null;
				currentSky = null;
				currentAir = null;
				currentExtinct = null;
				currentStartDate = null;
				currentEndDate = null;
				currentPriority = -1;
			} else {
				currentSeries = sched.getSeriesConstraint();
				currentMoon = sched.getMoonConstraint();
				currentSeeing = sched.getSeeingConstraint();
				currentSky = sched.getSkyConstraint();
				currentAir = sched.getAirmassConstraint();
				currentExtinct = sched.getExtinctionConstraint();
				currentStartDate = sched.getStartDate();
				currentEndDate = sched.getEndDate();
				currentPriority = sched.getPriority();
			}

			if (!equalsOrNull(currentStartDate, masterStartDate))
				throw new IllegalArgumentException("Constraint mismatch: StartDate for " + iobs + " ss="
						+ sched.getStartDate() + " ms=" + master.getStartDate());

			if (!equalsOrNull(currentEndDate, masterEndDate))
				throw new IllegalArgumentException("Constraint mismatch: EndDate for " + iobs + " se="
						+ sched.getEndDate() + " me=" + master.getEndDate());

			if (!equalsOrNull(currentSeries, masterSeries))
				throw new IllegalArgumentException("Constraint mismatch: SeriesConstraint for " + iobs + " ss="
						+ sched.getSeriesConstraint() + " ms=" + master.getSeriesConstraint());

			if (!equalsOrNull(currentMoon, masterMoon))
				throw new IllegalArgumentException("Constraint mismatch: MoonConstraint for " + iobs);

			if (!equalsOrNull(currentSeeing, masterSeeing))
				throw new IllegalArgumentException("Constraint mismatch: SeeingConstraint for " + iobs);

			if (!equalsOrNull(currentSky, masterSky))
				throw new IllegalArgumentException("Constraint mismatch: SkyConstraint for " + iobs);

			if (!equalsOrNull(currentAir, masterAir))
				throw new IllegalArgumentException("Constraint mismatch: AirmassConstraint for " + iobs);

			if (!equalsOrNull(currentExtinct, masterExtinct))
				throw new IllegalArgumentException("Constraint mismatch: ExtinctionConstraint for " + iobs);

			if (currentPriority != masterPriority)
				throw new IllegalArgumentException("Constraint mismatch: Sched priority " + iobs);

		}
		return master;
	} // [getUnifiedConstraints]

	/**
	 * Method that checks the equality of two objects, given one or the other
	 * may be null. If both are null they are the same, if one is null and one
	 * isn't they must be different, otherwise calls the object's equals method
	 * as a comparator.
	 * 
	 * @param o1
	 *            The first object.
	 * @param o2
	 *            The second object.
	 * @return true if both objects are equals according to this method's rules,
	 *         false otherwise.
	 */
	protected boolean equalsOrNull(Object o1, Object o2) {
		// if both are null they are the same
		if ((o1 == null) && (o2 == null))
			return true;
		// if one object is null and one is not they are different
		if (((o1 != null) && (o2 == null)) || ((o1 == null) && (o2 != null)))
			return false;
		// both objects _must_ be non-null here
		return o1.equals(o2);
	}

	private XInstrumentConfig translateToNewStyleConfig(InstrumentConfig config) throws Exception {

		Detector detector = config.getDetector(0);
		int xbin = detector.getXBin();
		int ybin = detector.getYBin();

		// windows - there are most likely none
		Window[] windows = detector.getWindows();

		XDetectorConfig xdet = new XDetectorConfig();
		xdet.setXBin(xbin);
		xdet.setYBin(ybin);

		// add windows???
		for (int iw = 0; iw < windows.length; iw++) {
			Window w = windows[iw];
			if (w != null) {
				XWindow xw = new XWindow(w.getXs(), w.getYs(), w.getWidth(), w.getHeight());
				xdet.addWindow(xw);
				System.err.println("Add window: " + xw);
			}
		}

		if (config instanceof CCDConfig) {
			CCDConfig ccdConfig = (CCDConfig) config;
			String filters = ccdConfig.getLowerFilterWheel() + "/" + ccdConfig.getUpperFilterWheel();

			XFilterSpec filterSpec = new XFilterSpec();
			filterSpec.addFilter(new XFilterDef(ccdConfig.getLowerFilterWheel()));
			filterSpec.addFilter(new XFilterDef(ccdConfig.getUpperFilterWheel()));

			XImagerInstrumentConfig xim = new XImagerInstrumentConfig(config.getName());
			xim.setFilterSpec(filterSpec);
			xim.setDetectorConfig(xdet);
			xim.setInstrumentName("RATCAM");
			return xim;

		} else if (config instanceof OConfig) {
			OConfig oConfig = (OConfig) config;
			StringBuffer filterStringBuffer = new StringBuffer();

			XFilterSpec filterSpec = new XFilterSpec();
			filterSpec.addFilter(new XFilterDef(oConfig.getFilterName(1)));
			filterSpec.addFilter(new XFilterDef(oConfig.getFilterName(2)));
			filterSpec.addFilter(new XFilterDef(oConfig.getFilterName(3)));

			XImagerInstrumentConfig xim = new XImagerInstrumentConfig(config.getName());
			xim.setFilterSpec(filterSpec);
			xim.setDetectorConfig(xdet);
			xim.setInstrumentName("IO:O");
			return xim;

		} else if (config instanceof IRCamConfig) {
			IRCamConfig irCamConfig = (IRCamConfig) config;
			String filters = irCamConfig.getFilterWheel();
			XFilterSpec filterSpec = new XFilterSpec();
			filterSpec.addFilter(new XFilterDef(filters));
			XImagerInstrumentConfig xim = new XImagerInstrumentConfig(config.getName());
			xim.setFilterSpec(filterSpec);
			xim.setDetectorConfig(xdet);
			xim.setInstrumentName("SUPIRCAM");
			return xim;

		} else if (config instanceof LowResSpecConfig) {

			LowResSpecConfig lowResSpecConfig = (LowResSpecConfig) config;
			double wavelength = lowResSpecConfig.getWavelength();
			XSpectrographInstrumentConfig xspec = new XSpectrographInstrumentConfig(config.getName());
			xspec.setWavelength(wavelength);
			xspec.setDetectorConfig(xdet);
			xspec.setInstrumentName("MEABURN");
			return xspec;

		} else if (config instanceof FrodoSpecConfig) {
			// this isnt going to work yet anyway, we need some sort of
			// branching effort in the RTML !
			FrodoSpecConfig frodoSpecConfig = (FrodoSpecConfig) config;

			int resolution = frodoSpecConfig.getResolution();
			XDualBeamSpectrographInstrumentConfig xdual = new XDualBeamSpectrographInstrumentConfig(config.getName());
			xdual.setResolution(resolution);
			xdual.setDetectorConfig(xdet);

			int arm = frodoSpecConfig.getArm();
			if (arm == FrodoSpecConfig.RED_ARM)
				xdual.setInstrumentName("FRODO_RED");
			else if (arm == FrodoSpecConfig.BLUE_ARM)
				xdual.setInstrumentName("FRODO_BLUE");
			return xdual;

		} else if (config instanceof PolarimeterConfig) {

			PolarimeterConfig polarConfig = (PolarimeterConfig) config;

			XPolarimeterInstrumentConfig xpolar = new XPolarimeterInstrumentConfig(config.getName());
			xpolar.setDetectorConfig(xdet);
			xpolar.setInstrumentName("RINGO2");
			return xpolar;

		} else if (config instanceof RISEConfig) {

			RISEConfig riseConfig = (RISEConfig) config;

			XImagerInstrumentConfig xim = new XImagerInstrumentConfig(config.getName());
			xim.setDetectorConfig(xdet);
			xim.setInstrumentName("RISE");
			return xim;

		} else if (config instanceof THORConfig) {

			THORConfig thorConfig = (THORConfig) config;
			XFilterSpec filterSpec = new XFilterSpec();
			XTipTiltImagerInstrumentConfig xim = new XTipTiltImagerInstrumentConfig(config.getName());
			xim.setFilterSpec(filterSpec);
			xim.setDetectorConfig(xdet);
			xim.setGain(thorConfig.getEmGain());
			xim.setInstrumentName("IO:THOR");
			return xim;

		} else {
			throw new Exception("unknown instrument config:" + config.getClass().getName());
		}

	}

}
