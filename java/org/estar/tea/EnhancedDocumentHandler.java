/**
 * 
 */
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
 * @author eng
 * 
 */
public class EnhancedDocumentHandler implements Logging {

	/** Classname for logging. */
	public static final String CLASS = "EDH";

	private TelescopeEmbeddedAgent tea;

	private Site site;

	private Logger logger;

	/** Handler ID. */
	private String cid;

	/** SDH counter. */
	private static int cc = 0;

	/**
	 * @param tea
	 * @param phase2
	 */
	public EnhancedDocumentHandler(TelescopeEmbeddedAgent tea) {
		this.tea = tea;
		logger = LogManager.getLogger("TRACE");
		cc++;
		cid = "EDH/" + cc;
		site = new Site("OBS", tea.getSiteLatitude(), tea.getSiteLongitude());
	}

	public RTMLDocument handleScore(RTMLDocument document) throws Exception {

		// ## START
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
				return setError(document, "Start/end time are reversed or same");

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

		} else {

			// Handle Flexible.

			s1 = Math.max(group.getStartingDate(), now);
			s2 = group.getExpiryDate();
			if (s2 <= s1)
				return setError(document, "Start/end time are reversed or same");

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
		}

		// this will return the average score for the group in the specified
		// interval...
		logger.log(INFO, 1, CLASS, cid, "executeScore", "Target achieved rank score " + rankScore
				+ " for specified period, CW="+cw);

		// cw has the largest window number, ie. runs [0, cw-1] inclusive
		double dc = 0.0; // record maximum cumulative score to-date
		for (int in = 0; in < cw; in++) {

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
		document.addHistoryEntry("TEA:" + tea.getId(), null, "Scored document and returned valid score: " + rankScore
				+ ".");

		return document;

	}

	public RTMLDocument handleRequest(RTMLDocument doc) throws Exception {

		IPhase2Model phase2 = (IPhase2Model) Naming.lookup(tea.getPhase2ModelUrl());

		Phase2ExtractorTNG extractor = new Phase2ExtractorTNG(tea);
		return extractor.handleRequest(doc);

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

	private double scoreGroup(Group group, long time) {
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
	 * 
	 * @param document
	 *            The document to modify.
	 * @param errorMessage
	 *            The error message.
	 * @throws Exception
	 *             if anything goes wrong.
	 * @return The modified <i>reject</i> document.
	 */
	private RTMLDocument setError(RTMLDocument document, String errorMessage) throws Exception {
		// document.setType("reject");
		// document.setErrorString(errorMessage);
		document.setScoreReply();
		document.setScore(0.0);
		document.addHistoryError("TEA:" + tea.getId(), null, errorMessage, "Score failed.");
		logger.log(INFO, 1, CLASS, cid, "setError", "EDH::Setting error to: " + errorMessage);
		return document;
	}
}
