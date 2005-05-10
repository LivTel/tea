package org.estar.tea;

import java.io.*;
import java.util.*;
import java.net.*;
//import javax.net.ssl.*;
//import javax.security.cert.*;
import java.util.*;
import java.text.*;
import javax.net.ssl.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.io.*;

import ngat.util.*;
import ngat.util.logging.*;
import ngat.phase2.*;
import ngat.net.*;
import ngat.net.camp.*;
import ngat.astrometry.*;

import ngat.message.base.*;
import ngat.message.GUI_RCS.*;
import ngat.message.OSS.*;

/** Handles updates from the RCS. 
 *
 * The method: setUseDocument(RTMLDocument) tells the UH which base document to use.
 * 
 * When the thread is started it will keep looking in its pending list for any
 * new image file names. If any, these should be pulled across and stored in the
 * processing directory. They should then be pipelined and the data extracted and 
 * placed in the update document. 
 *
 */
public class UpdateHandler extends ControlThread {

    /** Polling interval for pending queue.*/
    private static final long SLEEP_TIME = 5000L;

    /** Time margin above expected execution time when we give up as a lost cause.*/
    private static final long TIME_MARGIN = 2*3600*1000L;

    /** Flags completed successful obs.*/
    public static final int OBS_DONE = 1;

    /** Flags completed but failed obs.*/
    public static final int OBS_FAILED = 2;

    /** Flags uncompleted obs.*/
    public static final int OBS_NOT_DONE_YET = 0;

    /** The TEA.*/
    TelescopeEmbeddedAgent tea;

    /** The base document.*/
    private RTMLDocument baseDoc;

    /** The update document.*/
    private RTMLDocument updateDoc;

    /** Pipeline processing plugin implementation. (#### Should be per project)*/
    private PipelineProcessingPlugin pipelinePlugin;

    /** List of pending image filenames to transfer and process.*/
    private List pending;

    /** List of processed image filenames - dont know what these will be used for.*/
    private List processed;

    /** Flag to indicate whether the observations have completed.*/
    private volatile int state = OBS_NOT_DONE_YET;

    /** Number of exposures we expect to get.*/
    private int numberExposures;
    
    /** Time we expect this observation/group to take - combination of
     * number of exposures, exposure time, readout and dprt allowances (ms).
     */
    private long expectedTime;

    /** Time we started processing.*/
    private long startTime;

    /** Records the elapsed processing time.*/
    private long elapsedTime;

    /** Records the number of exposures received so far.*/
    private int countExposures;

    /** The observation.*/
    private Observation observation;

    /** Create an UpdateHandler.
     * @param tea The TEA.
     * @param baseDoc The base document.
     * @throws Exception if anything dodgy occurs - typically the baseDir deepClone().
     */
    public UpdateHandler(TelescopeEmbeddedAgent tea, RTMLDocument baseDoc) throws Exception {
	super("UH", true);
	this.tea     = tea;
	this.baseDoc = baseDoc;

	pending   = new Vector();
	processed = new Vector();

	updateDoc = (RTMLDocument)baseDoc.deepClone();
	
	RTMLObservation obs = updateDoc.getObservation(0);
	if (obs == null)
	    throw new Exception("There was no obs#0 in the document");
	obs.clearImageDataList();

	countExposures = 0;
	elapsedTime = 0L;

    }

    /** Set the observation.*/
    public void setObservation(Observation obs) { this.observation = obs; }

    /** Sets the number of exposures we expect.*/
    public void setNumberExposures(int n) { this.numberExposures = n; }

    /** Sets the expected total observation time (ms).*/
    public void setExpectedTime(long t) { this.expectedTime = t; }

    /** Sets flag to indicate that observation has FAILED.*/
    public void setObservationFailed() { this.state = OBS_FAILED; }
    
    /** Sets flag to indicate that observation has SUCCEDED.*/
    public void setObservationCompleted() { this.state = OBS_DONE; }
    
    /** Adds the name of a file to the pending list.
     * @param fileName The full (path) name of the file on the OCC including instrument NFS mount details.
     */
    public void addImageFileName(String fileName) {
	pending.add(fileName);
    }

    // Called when processing is started.*/
    protected void initialise() {
	startTime = System.currentTimeMillis();
    }

    /** Called by wrapping thread forever until terminated.
     *
     * Once the end state is set, if FAILED we just quit,
     * otherwise, check the pending list and clear any images
     * left inside it, transfer them, pipeline and add details
     * to the obs.imagedata and obs.clusterdata.
     *
     * Other things which can cause this processing to end:-
     *
     * elapsedTime > max allowed (v.generous margin).
     * #num exposures > expected number
     * 
     */
    protected void mainTask() {
    
	if (state == OBS_FAILED) {

	    // bugger - weve already modified the base doc !
	    // do we need to pull the new imagedata out? as we're not
	    // going to send the updateDoc now. Maybe we just dont bother
	    // to save it.. I think so..
	    terminate();
	    return;

	} else {
	    
	    // Either OBS_DONE or NOT_DONE_YET either way we need to clear out
	    // the pending list.

	    // If image filenames in queue, grab and process.
	    while (! pending.isEmpty()) {
		
		String imageFileName = (String)pending.remove(0);
		
		// Generate the correct destination file name.
		String destDirName = tea.getImageDir();
		File   destDir     = new File(destDirName);
		File   fullFile    = new File(imageFileName);
		String rawFileName = fullFile.getName();
		File   destFile    = new File(destDir, rawFileName);
		String destFileName= destFile.getPath();	
		
		try {		    
		    transfer(imageFileName, destFileName);		    
		} catch (Exception e) {
		    e.printStackTrace();
		    return;
		}
		
		// pipeline process

		// pipelinePlugin = create from reflection..
		// needs a null constructor and we get from the tea as 
		// tea.getPipelinePluginClassname() -> String
		// then
		// try { 
		//  RTMLImageData data = p.reduce(destFileName, 
		//                                tea.getProjectProperties(project));
		//	or for now simpler ...	
		//  RTMLImageData data = p.reduce(destFileName, tea.getImageDir());
		//
		// } catch (Thingy..) {}
		
		// Add to updatedoc and basedoc
		RTMLObservation obs = baseDoc.getObservation(0);
		if (obs != null) {
		    try {
			RTMLImageData data = new RTMLImageData();
			//data.setFITSHeader("");
			data.setImageDataType("FITS16");
			data.setImageDataURL(tea.getImageWebUrl()+"/noideayetbutincludessomethingfromdestfile.fits");
			data.setObjectListType("cluster");
			data.setObjectListCluster("a data point from the pipeline");
			
			obs.addImageData(data);
		    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
		    
		}
		
		obs = updateDoc.getObservation(0);
		if (obs != null) {

		    try {
			RTMLImageData data = new RTMLImageData();
			//data.setFITSHeader("");
			data.setImageDataType("FITS16");
			data.setImageDataURL(tea.getImageWebUrl()+"/noideayetbutincludessomethingfromdestfile.fits");
			data.setObjectListType("cluster");
			data.setObjectListCluster("a data point from the pipeline");
			
			obs.addImageData(data);
		    } catch (Exception e) {
			e.printStackTrace();
			return;
		    }

		}
		
		// Now try to send the update to the UA.
		// this is a mess - need global comms method like
		// tea.sendDoc(doc); This may need to go in athread
		// as it may block for a while if the connection is blocked
		AgentRequestHandler arq = new AgentRequestHandler(tea);
		arq.sendDocUpdate(updateDoc, "update");
				  
		processed.add(imageFileName);
		
	    } // while not empty
	    
	} // test (state)

	// Finally quit
	if (state == OBS_DONE) {
	    terminate();
	    return;
	}

	elapsedTime = System.currentTimeMillis() - startTime;

	// Quit if weve overrun horribly
	if (elapsedTime > expectedTime + TIME_MARGIN) {
	    terminate();
	    return;
	}

	// Back round once more at least.
	try {Thread.sleep(SLEEP_TIME);} catch (InterruptedException ix) {}

    }

    /** Called at end-of-run.*/
    protected void shutdown() {

	// time to check if weve done all the exposures we expect to do
	// not easy to work out. No easy way to record how many weve
	// got persistently...
	// we can count the number of imageddatas in the basedoc's obs#0 and
	// see if it matches the number we would expect if the
	// correct no of monitor periods were observed which is
	// almost never correct! The value of count*expcount should 
	// equal no of imagaedatas recorded - doesnt cope with partial
	// completed obs or missed periods..... TBD

	// if (weve got all the exposures we were expecting over the entire
	//       monitor startdate-enddate ) {
	//  arq.sendDocUpdate(baseDoc, "completed");
	// ## baseDoc not updateDoc weve already sent heaps of those..
	// }

	// Always pull this UH off the tea.agentMap as its defunct now
	// #####slightly worrying - the agmap only matches against an obs path
	// this will be the same if another instantiation of the group
	// appears before weve done with this one ....aaargh!
	String oid = observation.getFullPath();
       	tea.deleteUpdateHandler(oid);

	// Save the master baseDoc ie. replace it in the same file TBD
	// tea.replaceDoc(blahblah)

    }

    /** Tries to pull an image file off the OCC and stick it in the appropriate directory.*/
    private void transfer(String imageFileName, String destFileName) throws Exception {
	
	SSLFileTransfer.Client client = tea.getImageTransferClient();

	if (client == null) 
	    throw new Exception("The transfer client is not available");

	System.err.println("UH::Requesting image file: "+imageFileName+" -> "+destFileName);

	client.request(imageFileName, destFileName);

    }

}
