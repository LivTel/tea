// DefaultPipelinePlugin.java
// $Header: /space/home/eng/cjm/cvs/tea/java/org/estar/tea/DefaultPipelinePlugin.java,v 1.2 2005-05-25 15:12:13 cjm Exp $
package org.estar.tea;

import java.io.*;

import ngat.util.*;
import ngat.util.logging.*;

import org.estar.cluster.*;
import org.estar.fits.*;
import org.estar.rtml.*;

/**
 * Default pipeline plugin implementation.
 */
public class DefaultPipelinePlugin implements PipelineProcessingPlugin, Logging
{
	// static constants
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: DefaultPipelinePlugin.java,v 1.2 2005-05-25 15:12:13 cjm Exp $";
	/**
	 * Logging class identifier.
	 */
	public static final String CLASS = "DefaultPipelinePlugin";

	/**
	 * Start of property keywords used for defining pipeline plugin properties.
	 * This is currently "pipeline.plugin".
	 */
	protected static String PROPERTY_KEY_HEADER = "pipeline.plugin";
	/**
	 * Part of property keywords used for defining pipeline plugin properties - the "name" of this plugin.
	 * This is currently "default".
	 */
	protected static String PROPERTY_KEY_PIPELINE_PLUGIN_NAME = "default";
	// fields
	/**
	 * tea reference.
	 */
	protected TelescopeEmbeddedAgent tea = null;
	/**
	 * A string representing a local input directory.
	 */
	protected String inputDirectory = null;
	/**
	 * A string representing a local directory to put the reduced FITS image in.
	 */
	protected String outputDirectory = null;
	/**
	 * A string representing the URL base of the local output directory.
	 */
	protected String urlBase = null;
	/**
	 * A string representing a filename of the script to call.
	 */
	protected String scriptFilename = null;
	/**
	 * The logger.
	 */
	protected Logger logger = null;

	/**
	 * Default constructor - needed for reflection instance creation. 
	 * Sets up logger.
	 * @see #logger
	 */
	public DefaultPipelinePlugin()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Method to set tea reference.
	 * @param t The tea reference.
	 * @see #tea
	 */
	public void setTea(TelescopeEmbeddedAgent t)
	{
		tea = t;
	}

	/**
	 * Intialise the plugin. Assumes setTea has already been called.
	 * <ul>
	 * <li>Initialise inputDirectory from the tea property PROPERTY_KEY_HEADER+"."+
	 *     PROPERTY_KEY_PIPELINE_PLUGIN_NAME+".input_directory".
	 * <li>Initialise outputDirectory from the tea property PROPERTY_KEY_HEADER+"."+
	 *     PROPERTY_KEY_PIPELINE_PLUGIN_NAME+".output_directory".
	 * <li>Initialise scriptFilename from the tea property PROPERTY_KEY_HEADER+"."+
	 *     PROPERTY_KEY_PIPELINE_PLUGIN_NAME+".script_filename".
	 * </ul>
	 * @see #PROPERTY_KEY_HEADER
	 * @see #PROPERTY_KEY_PIPELINE_PLUGIN_NAME
	 * @see #inputDirectory
	 * @see #outputDirectory
	 * @see #scriptFilename
	 * @see #urlBase
	 * @see #tea
	 */
	public void initialise() throws Exception
	{
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise() started.");
		inputDirectory = tea.getPropertyString(PROPERTY_KEY_HEADER+"."+PROPERTY_KEY_PIPELINE_PLUGIN_NAME+
						       ".input_directory");
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise: input directory: "+inputDirectory+".");
		outputDirectory = tea.getPropertyString(PROPERTY_KEY_HEADER+"."+PROPERTY_KEY_PIPELINE_PLUGIN_NAME+
							".output_directory");
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise: output directory: "+outputDirectory+".");
		scriptFilename = tea.getPropertyString(PROPERTY_KEY_HEADER+"."+PROPERTY_KEY_PIPELINE_PLUGIN_NAME+
						       ".script_filename");
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise: script filename: "+scriptFilename+".");
		urlBase = tea.getPropertyString(PROPERTY_KEY_HEADER+"."+PROPERTY_KEY_PIPELINE_PLUGIN_NAME+
						       ".http_base");
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise: URL base: "+urlBase+".");
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise() finished.");
	}

	/**
	 * Get the directory on the tea machine to copy the FITS image into.
	 * @return A local directory.
	 * @see #inputDirectory
	 */
	public String getInputDirectory() throws Exception
	{
		return inputDirectory;
	}

	/**
	 * Process the specified file.
	 * @param inputFile The input filename on the local (tea) machine.
	 * @see #stripExtension
	 * @see #callScript
	 * @see #createImageData
	 * @see #outputDirectory
	 */
	public RTMLImageData processFile(File inputFile) throws Exception
	{
		RTMLImageData imageData = null;
		String inputLeafName = null;
		String clusterLeafName = null;
		File outputFile = null;
		File outputClusterFile = null;

		logger.log(INFO, 1, CLASS, tea.getId(),"processFile",this.getClass().getName()+
			   ":processFile("+inputFile+") started.");
		// construct output FITS filename in outputDirectory
		inputLeafName = inputFile.getName();
		outputFile = new File(outputDirectory,inputLeafName);
		// construct output cluster file
		clusterLeafName = stripExtension(inputLeafName);
		clusterLeafName = clusterLeafName+".cluster";
		outputClusterFile = new File(outputDirectory,clusterLeafName);
		// call script
		logger.log(INFO, 1, CLASS, tea.getId(),"processFile",this.getClass().getName()+
			   ":processFile calling callScript.");
		callScript(inputFile,outputFile,outputClusterFile);
		// copy results into imageData
		logger.log(INFO, 1, CLASS, tea.getId(),"processFile",this.getClass().getName()+
			   ":processFile calling createImageData.");
		imageData = createImageData(outputFile,outputClusterFile);
		logger.log(INFO, 1, CLASS, tea.getId(),"processFile",this.getClass().getName()+
			   ":processFile("+inputFile+") returned image data:"+imageData+".");
		return imageData;
	}

	/**
	 * Strip any extension off a string e.g. stripExtension("test.fits") returns "test".
	 * @param s The string to strip.
	 * @return The stripped string.
	 */
	protected String stripExtension(String s)
	{
		int eIndex;

		eIndex = s.lastIndexOf(".");
		if(eIndex < 0)
			return s;
		return s.substring(0,eIndex);
	}

	/**
	 * Call a script with the specified input and output filenames.
	 * @param inputFile The local input filename.
	 * @param outputFile The local output filename.
	 * @param outputClusterFile The local output cluster filename.
	 * @exception Exception Thrown if the script execution failed.
	 * @see #scriptFilename
	 */
	protected void callScript(File inputFile,File outputFile,File outputClusterFile) throws Exception
	{
		ExecuteCommand executeCommand = null;
		Exception e = null;
		int exitValue;

		logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			   ":callScript("+inputFile+","+outputFile+","+outputClusterFile+") started.");
		executeCommand = new ExecuteCommand("");
		executeCommand.setCommandString(scriptFilename+" "+inputFile+" "+outputFile+" "+outputClusterFile);
		executeCommand.run();
		logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			   ":callScript("+inputFile+","+outputFile+","+outputClusterFile+") produced output:\n"+
			   executeCommand.getOutputString());
		logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			   ":callScript("+inputFile+","+outputFile+","+outputClusterFile+") produced error string:\n"+
			   executeCommand.getErrorString());
		e = executeCommand.getException();
		if(e != null)
		{
			logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			  ":callScript("+inputFile+","+outputFile+","+outputClusterFile+") created exception "+e+".");
			logger.dumpStack(1,e);
			throw e;
		}
		exitValue = executeCommand.getExitValue();
		if(exitValue != 0)
		{
			logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
				":callScript("+inputFile+","+outputFile+","+outputClusterFile+") returned exit value "+
					    exitValue+".");
			throw new Exception(this.getClass().getName()+
			      ":callScript("+inputFile+","+outputFile+","+outputClusterFile+") returned exit value "+
					    exitValue+".");
		}
		logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			   ":callScript("+inputFile+","+outputFile+","+outputClusterFile+") finished.");
	}

	/**
	 * Create an instance of RTMLImageData.
	 * @param outputFile The file to get the contents of the imageData from.
	 * @param outputClusterFile The file containing any cluster data.
	 * @return An instance of RTMLImageData with suitable data.
	 * @see #urlBase
	 * @see #loadClusterFile
	 */
	protected RTMLImageData createImageData(File outputFile,File outputClusterFile)
	{
		RTMLImageData data = null;
		FITSHeaderLoader headerLoader = null;

		logger.log(INFO, 1, CLASS, tea.getId(),"createImageData",this.getClass().getName()+
			   ":createImageData("+outputFile+","+outputClusterFile+") started.");
		data = new RTMLImageData();
		if(outputFile != null)
		{
			// load fits headers
			try
			{
				headerLoader = new FITSHeaderLoader();
				headerLoader.load(outputFile);
				data.setFITSHeader(headerLoader.toString());
			}
			catch(Exception e)
			{
				logger.log(INFO, 1, CLASS, tea.getId(),"createImageData",this.getClass().getName()+
					   ":createImageData("+outputFile+","+outputClusterFile+
					   "): failed to load FITS header "+e+".");
				logger.dumpStack(1,e);
				// don't fail data pipeline because of this
				// Some data pipelines may fail to produce cluster files.
			}
			// set URL
			try
			{
				data.setImageDataType("FITS16");
				data.setImageDataURL(urlBase+outputFile.getName());
			}
			catch(Exception e)
			{
				logger.log(INFO, 1, CLASS, tea.getId(),"createImageData",this.getClass().getName()+
					   ":createImageData("+outputFile+","+outputClusterFile+
					   "): failed to set URL "+e+".");
				logger.dumpStack(1,e);
				// don't fail data pipeline because of this
				// Some data pipelines may fail to produce cluster files.
			}
		}
		if(outputClusterFile != null)
		{
			try
			{
				loadClusterFile(outputClusterFile,data);
			}
			catch(Exception e)
			{
				logger.log(INFO, 1, CLASS, tea.getId(),"createImageData",this.getClass().getName()+
					   ":createImageData("+outputFile+","+outputClusterFile+
					   "): failed to load cluster file "+e+".");
				logger.dumpStack(1,e);
				// don't fail data pipeline because of this
				// Some data pipelines may fail to produce cluster files.
			}
		}
		logger.log(INFO, 1, CLASS, tea.getId(),"createImageData",this.getClass().getName()+
			   ":createImageData("+outputFile+","+outputClusterFile+") returned data:"+data+".");
		return data;
	}

	/**
	 * Method to load the contents of the cluster file into the relevant part of the imageData.
	 * @param outputClusterFile A local filename containing cluster format data.
	 * @param imageData An instance of RTMLImageData to fill.
	 * @exception FileNotFoundException Thrown if the specified cluster file is not found.
	 * @exception IOException Thrown if there is a problem reading the cluster file.
	 */
	protected void loadClusterFile(File outputClusterFile,RTMLImageData imageData) throws FileNotFoundException,
											      IOException
	{
		Cluster cluster = null;
		String clusterString = null;

		// load clusterString from outputClusterFile
		if(outputClusterFile.exists())
		{
			cluster = new Cluster();
			cluster.load(outputClusterFile);
			clusterString = cluster.toString();
		}
		// set clusterString in image data
		if(clusterString != null)
		{
			imageData.setObjectListType("cluster");
			imageData.setObjectListCluster(clusterString);
		}
	}
}
//
// $Log: not supported by cvs2svn $
// Revision 1.1  2005/05/23 16:00:15  cjm
// Initial revision
//
//
