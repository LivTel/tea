package org.estar.tea;

import java.rmi.Naming;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import ngat.astrometry.Astrometry;
import ngat.astrometry.Position;
import ngat.astrometry.Site;
import ngat.oss.model.IPhase2Model;
import ngat.phase2.Group;
import ngat.phase2.MonitorGroup;
import ngat.phase2.Observation;
import ngat.phase2.Source;
import ngat.util.logging.LogManager;
import ngat.util.logging.Logger;
import ngat.util.logging.Logging;

import org.estar.rtml.RTMLDocument;
import org.estar.rtml.RTMLPeriodFormat;
import org.estar.rtml.RTMLScore;

/**
 * A handler that deals with incoming documents over the TEA's RMI interface.
 * @author eng
 */
public class EnhancedDocumentHandler implements Logging 
{
	/** 
	 * Classname for logging. 
	 */
	public static final String CLASS = "EDH";
	/**
	 * Telescope Embeeded Agent reference.
	 */
	private TelescopeEmbeddedAgent tea;
	/**
	 * Where the telescope using this instance of the TEA is located. Used for scoring (sunrise/sunset etc).
	 */
	private Site site;
	/**
	 * TRACE logger.
	 */
	private Logger logger;

	/** 
	 * Handler ID. 
	 */
	private String cid;

	/** 
	 * SDH counter. Class-wide variable, used to create unique handler id string (cid).
	 * @see #cid
	 */
	private static int cc = 0;

	/**
	 * Constructor.
	 * <ul>
	 * <li>Stores the tea instance.
	 * <li>Increments the class-wide counter cc.
	 * <li>Creates a cid based on EDH plus the cc.
	 * <li>Creates the site, using the TEA's stored SiteLatitude and SiteLongitude.
	 * </ul>
	 * @param tea The telescope embedded agent instance.
	 * @see org.estar.tea.TelescopeEmbeddedAgent#getSiteLatitude
	 * @see org.estar.tea.TelescopeEmbeddedAgent#getSiteLongitude
	 */
	public EnhancedDocumentHandler(TelescopeEmbeddedAgent tea) 
	{
		this.tea = tea;
		logger = LogManager.getLogger("TRACE");
		cc++;
		cid = "EDH/" + cc;
		site = new Site("OBS", tea.getSiteLatitude(), tea.getSiteLongitude());
	}

	/**
	 * Handle a score document request. 
	 * <ul>
	 * <li>If the score document is a TOOP (target of oppurtunity) document:
	 *     <ul>
	 *     <li>Get the TOCSessionManager instance.
	 *     <li>Call TOCSessionManager.scoreDocument to score the TOOP document.
	 *     <li>Return the scored document.
	 *     </ul>
	 * <li>Call Phase2GroupExtractor.extractGroup to convert the RTML document into a PhaseII Group
	 *     object. Note this returns the <b>old style</b> phaseII objects, not the new (latest) phaseII objects.
	 * <li>If the group is a monitor group:
	 *     <ul>
	 *     <li>Calculate the scoring one way.
	 *     </ul>
	 * <li>Otherwise the group is a flexible group:
	 *     <ul>
	 *     <li>Calculate the scoring another way.
	 *     </ul>
	 * <li>Add RTMLScore instances for each delta timeperiod to the document.
	 * <li>Set the document's overall score (only used for RTML 2.2 documents).
	 * <li>Set the document to be a score reply document.
	 * <li>
	 * </ul>
	 * @param document An instance of RTMLDocument describing a document that needs scoring.
	 * @return The scored document, or an error document if scoring fails.
	 * @see #scoreGroup
	 * @see #setScoreReplyError
	 * @see Phase2GroupExtractor
	 * @see Phase2GroupExtractor#extractGroup
	 * @see TOCSessionManager
	 * @see TOCSessionManager#getSessionManagerInstance
	 * @see TOCSessionManager#scoreDocument
	 * @see org.estar.rtml.RTMLDocument#isTOOP
	 * @see org.estar.rtml.RTMLDocument#addScore
	 * @see org.estar.rtml.RTMLDocument#setScore
	 * @see org.estar.rtml.RTMLDocument#setScoreReply
	 * @see org.estar.rtml.RTMLScore#setDelay
	 * @see org.estar.rtml.RTMLScore#setProbability
	 * @see org.estar.rtml.RTMLScore#setCumulative
	 * @see org.estar.rtml.RTMLPeriodFormat#setHours
	 * @see org.estar.rtml.RTMLPeriodFormat#setMinutes
	 * @see org.estar.rtml.RTMLPeriodFormat#setSeconds
	 */
	public RTMLDocument handleScore(RTMLDocument document) throws Exception 
	{
		logger.log(INFO, 1, CLASS, cid, "handleRequest", "Starting scoring request " + cc);

		long now = System.currentTimeMillis();

		if (document.isTOOP()) {
			// Try and get TOCSessionManager context.
			TOCSessionManager sessionManager = TOCSessionManager.getSessionManagerInstance(tea, document);
			// score the document
			document = sessionManager.scoreDocument(document);
			return document;
		}

		// Get the group from the document model
		// NOTE This is the OLD PHASE-2 extractor here
		Phase2GroupExtractor p2x = new Phase2GroupExtractor(tea);

		Group group = p2x.extractGroup(document);

		// Send the scoring request

		double rankScore = 0.0;
		double diff[]; // = new double[];
		double cum[]; // = new double[];

		long s1 = 0L;
		long s2 = 0L;
		long resolution = 900000L; // start off at 15M
		long delta = 0L;
		int np = 1;
		int cw = 0; // count window number

		if (group instanceof MonitorGroup) {

			// Handle Monitor.
			// TODO need to extract these buried schedule parameters again !

			Date startDate = new Date(((MonitorGroup) group).getStartDate());
			Date endDate = new Date(((MonitorGroup) group).getEndDate());

			// lets not look backwards
			s1 = Math.max(startDate.getTime(), now);
			s2 = endDate.getTime();
			if (s2 <= s1)
				return setScoreReplyError(document, "Start/end time are reversed or same");

			// how many windows are there
			long period = ((MonitorGroup) group).getPeriod();
			int nw = 2 + (int) ((s2 - s1) / period);

			// how big is window
			long window = (long) ((double) ((MonitorGroup) group).getFloatFraction() * (double) period);

			// we want to check at window-size/10 or better
			// unless its less than a minute
			resolution = window / 10;
			if (resolution < 60000L)
				resolution = 60000L;

			int nsw = (int) ((double) window / (double) resolution);

			// setup diff array
			diff = new double[nw];
			for (int i = 0; i < nw; i++) {
				diff[i] = 0.0;
			}

			delta = period;
			
			logger.log(INFO, 1, CLASS, cid, "executeScore", 
					"Monitor scoring: Period="+period+", window="+window+" NW="+nw);
			
			cw = 0; // count window number
			boolean inWindowAlready = false;
			long t = s1;
			while (t < s2) {
				if (inWindow((MonitorGroup) group, t)) {
					if (!inWindowAlready) {
						// just entered a window
						cw++;
						inWindowAlready = true;
					}
					double score = scoreGroup(group, t);

					// add to diff score - this is not right but what is ?								
					    diff[cw - 1] += score;
				}

				t += resolution;
			}

			// we have a set of diffs which need normalizing
			for (int i = 0; i < nw; i++) {
				diff[i] /= (double) nsw;
				rankScore += diff[i];
			}
			rankScore /= (double) nw;

		} 
		else 
		{
			// Handle Flexible.

			s1 = Math.max(group.getStartingDate(), now);
			s2 = group.getExpiryDate();
			if (s2 <= s1)
				return setScoreReplyError(document, "Start/end time are reversed or same");

			// 100 samples;
			resolution = (s2 - s1) / 100;

			// setup diff array
			diff = new double[101];
			for (int i = 0; i < 101; i++) {
				diff[i] = 0.0;
			}

			delta = resolution;	
			logger.log(INFO, 1, CLASS, cid, "executeScore", 
					"Flex scoring: NW=101");
			cw = 0;
			long t = s1;
			while (t < s2) {
				cw++;
				double score = scoreGroup(group, t);

				// add to diff score - this is not right but what is ?			
				    diff[cw - 1] += score;
				t += resolution;
			}

			for (int i = 0; i < 101; i++) {
				rankScore += diff[i];
			}
			rankScore /= 100.0;
		}// end if monitor or flexible

		// this will return the average score for the group in the specified
		// interval...
		logger.log(INFO, 1, CLASS, cid, "executeScore", "Target achieved rank score " + rankScore
				+ " for specified period, CW="+cw);

		// cw has the largest window number, ie. runs [0, cw-1] inclusive
		double dc = 0.0; // record maximum cumulative score to-date
		for (int in = 0; in < cw; in++) 
		{
			RTMLScore dscore = new RTMLScore();
			RTMLPeriodFormat dd = new RTMLPeriodFormat();
			if (delta > 14400 * 1000L)
				dd.setHours((int) (delta * in / 3600000));
			else if (delta > 240 * 1000L)
				dd.setMinutes((int) (delta * in / 60000));
			else
				dd.setSeconds((int) (delta * in / 1000));

			dc = Math.max(diff[in], dc); // choose greater of diff() and best cumulative so far
			dscore.setDelay(dd);
			dscore.setProbability(diff[in]);
			dscore.setCumulative(Math.min(dc, 1.0));
			document.addScore(dscore);
		}
		document.setScore(rankScore);
		document.setScoreReply();
		// No failure reasons for now....
		document.addHistoryEntry("TEA:" + tea.getId(), null, "Scored document and returned valid score: "
					 + rankScore+ ".");
		return document;

	}

	/**
	 * Handle a RTML request document.
	 * <ul>
	 * <li>We create a Phase2ExtractorTNG, and call it's handleRequest method.
	 * </ul>
	 * @param doc The RTML request document to process, an instance of RTMLDocument.
	 * @return An instance of RTMLDocument containing the result of processing the document.
	 * @see #tea
	 * @see TelescopeEmbeddedAgent#getPhase2ModelUrl
	 * @see Phase2ExtractorTNG
	 * @see Phase2ExtractorTNG#handleRequest
	 */
	public RTMLDocument handleRequest(RTMLDocument doc) throws Exception 
	{
		// Now done in handleRequest
		//IPhase2Model phase2 = (IPhase2Model) Naming.lookup(tea.getPhase2ModelUrl());
		Phase2ExtractorTNG extractor = new Phase2ExtractorTNG(tea);
		return extractor.handleRequest(doc);

	}
	
	/**
	 * Handle a RTML abort document.
	 * <ul>
	 * <li>We create a Phase2ExtractorTNG, and call it's handleAbort method.
	 * </ul>
	 * @param doc The RTML abort document to process, an instance of RTMLDocument.
	 * @return An instance of RTMLDocument containing the result of processing the document.
	 * @see #tea
	 * @see TelescopeEmbeddedAgent#getPhase2ModelUrl
	 * @see Phase2ExtractorTNG
	 * @see Phase2ExtractorTNG#handleAbort
	 */
	public RTMLDocument handleAbort(RTMLDocument doc) throws Exception 
	{
		Phase2ExtractorTNG extractor = new Phase2ExtractorTNG(tea);
		return extractor.handleAbort(doc);

	}
	
	
	public RTMLDocument handleModification(RTMLDocument doc) throws Exception {
		
		Phase2ExtractorTNG extractor = new Phase2ExtractorTNG(tea);
		String groupPath = extractor.extractGroupPath(doc);
		
		System.err.println("Modification request for group with path: "+groupPath);
		Map arqs = tea.getAgentMap();
		
		if (arqs.containsKey(groupPath)) {
			logger.log(INFO, 1, CLASS, cid, 
					"EDH:: Modify group: ARQ was located, looking up group's DB ID...");
		} else {
			logger.log(INFO, 1, CLASS, cid, 
					"EDH:: Modify group: No ARQ was found - group is unknown");
		}
		return null;
	}

	/**
	 * Internal method to determine whether the specified monitor group is in an (observing) window
	 * at the specified moment in time.
	 * @param mg The monitor group.
	 * @param time The specified moment in time, in milliseconds since the epoch.
	 * @return A boolean, true if the monitor group is in an (observing) window, false otherwise.
	 */
	private boolean inWindow(MonitorGroup mg, long time) {

		long startDate = mg.getStartDate();
		long endDate = mg.getEndDate();

		long period = mg.getPeriod();
		float floatFraction = mg.getFloatFraction();

		double fPeriod = (double) (time - startDate) / (double) period;
		double iPeriod = Math.rint(fPeriod);

		long startFloat = startDate + (long) ((iPeriod - (double) floatFraction / 2.0) * (double) period);
		long endFloat = startDate + (long) ((iPeriod + (double) floatFraction / 2.0) * (double) period);

		if (time < startDate)
			return false;
		if (time > endDate)
			return false;

		if ((startFloat <= time) && (endFloat >= time))
			return true;

		return false;

	}

	/**
	 * Method used by handleScore to score an instance of observing the group at a certain time.
	 * If the sun is above the horizon, the method returns 0.0.
	 * If an observation in the group is below the horizon at the specified time, this method returns 0.0.
	 * Otherwise the score is proportional to the average elevation of all the observation target positions
	 * at the specified moment in time.
	 * @param group The group to observe.
	 * @param time The instant of time to score the group for.
	 * @return A double, the score for this group run at this time. 
	 * @see #logger
	 * @see org.estar.tea.TelescopeEmbeddedAgent#getDomeLimit
	 * @see ngat.astrometry.Astrometry
	 * @see ngat.astrometry.Astrometry#getSolarPosition
	 * @see ngat.astrometry.Position
	 * @see ngat.astrometry.Position#getAltitude
	 * @see ngat.astrometry.Position#toDegrees
	 * @see ngat.phase2.Group
	 * @see ngat.phase2.Group#listAllObservations
	 * @see ngat.phase2.Observation
	 * @see ngat.phase2.Observation#getSource
	 * @see ngat.phase2.Observation#getPosition
	 * @see ngat.phase2.Observation#getAltitude
	 */
	private double scoreGroup(Group group, long time) 
	{
		logger.log(INFO, 1, CLASS, cid, 
				"EDH:: Scoring group: "+group.getName()+" at "+(new Date(time)));
		
		// sunup - zero score
		Position sun = Astrometry.getSolarPosition(time);
		if (sun.getAltitude(time, site) > 0.0)
			return 0.0;

		double v = 0.0;
		int cobs = 0;
		Iterator iobs = group.listAllObservations();
		while (iobs.hasNext()) {
			cobs++;
			Observation obs = (Observation) iobs.next();
			Source src = obs.getSource();
			Position p = src.getPosition();
			double elev = p.getAltitude(time);
			logger.log(INFO, 1, CLASS, cid, 
					"EDH:: Scoring group:Target: "+src.getName()+" at elevation: "+Position.toDegrees(elev, 2));
			if (elev < tea.getDomeLimit())
				return 0.0;
			v += elev;
		}
		if (cobs == 0)
			return 0.0;

		v /= (double) cobs;

		return (v - Math.toRadians(20.0)) / Math.toRadians(70.0);

	}

	/**
	 * Set the error message in the supplied document.
	 * The returned document is set to a score reply, with score 0.0 and a history entry added
	 * with the error message.
	 * @param document The document to modify.
	 * @param errorMessage The error message.
	 * @return The modified <i>score reply</i> document, with overall score of 0.0.
	 * @throws Exception if anything goes wrong.
	 */
	private RTMLDocument setScoreReplyError(RTMLDocument document, String errorMessage) throws Exception 
	{
		// document.setType("reject");
		// document.setErrorString(errorMessage);
		document.setScoreReply();
		document.setScore(0.0);
		document.addHistoryError("TEA:" + tea.getId(), null, errorMessage, "Score failed.");
		logger.log(INFO, 1, CLASS, cid, "setScoreReplyError", "EDH::Setting error to: " + errorMessage);
		return document;
	}
}
