// DefaultPipelinePlugin.java
// $Header: /space/home/eng/cjm/cvs/tea/java/org/estar/tea/DefaultPipelinePlugin.java,v 1.14 2016-05-23 14:09:40 cjm Exp $
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
	public final static String RCSID = "$Id: DefaultPipelinePlugin.java,v 1.14 2016-05-23 14:09:40 cjm Exp $";
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
	protected static String PROPERTY_KEY_PIPELINE_PLUGIN_ID = "default";
	// fields
	/**
	 * tea reference.
	 */
	protected TelescopeEmbeddedAgent tea = null;
	/**
	 * The id of the pipeline plugin. This is typically "TAG/User.Proposal" of the proposal that
	 * caused this plugin to be created, of "default" if this is <b>not</b> a proposal specific plugin.
	 * @see #PROPERTY_KEY_PIPELINE_PLUGIN_ID
	 */
	protected String id = PROPERTY_KEY_PIPELINE_PLUGIN_ID;
	/**
	 * The instrument this pipeline is for i.e. "ratcam", "io:o", "supircam", "io:i", "ringostar", "ringo3", "rise", "meaburn", "frodospec-red".
	 */
	protected String instrumentId = null;
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
	 * What format the data pipeline returns the data file in. Should be supported by RTML,
	 * i.e. either "cluster" or "votable-url".
	 */
	protected String objectListFormat = null;
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
	 * Method to set the Id of this instance of the plugin. This is used when searching for the plugin defaults.
	 * The id is normally the "TAG/User.Proposal" of the proposal this pipeline is for, or "default" for the
	 * case of the default pipeline.
	 * @param s The id string.
	 * @see #id
	 */
	public void setId(String s)
	{
		id = s;
	}

	/**
	 * Method to set the instrument name of this instance of the plugin. 
	 * @param s The instrument name.
	 * @see #instrumentId
	 */
	public void setInstrumentId(String s)
	{
		instrumentId = s;
	}

	/**
	 * Intialise the plugin. Assumes setTea, setId and setInstrumentId have already been called.
	 * <ul>
	 * <li>Initialise inputDirectory from the tea property PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".input_directory".
	 * <li>Initialise outputDirectory from the tea property PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".output_directory".
	 * <li>Initialise scriptFilename from the tea property PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".script_filename".
	 * <li>Initialise urlBase from the tea property PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".http_base".
	 * <li>Initialise objectListFormat from the tea property PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".object_list_format".
	 * </ul>
	 * @exception NullPointerException Thrown if  a keyword has no value.
	 * @see #id
	 * @see #instrumentId
	 * @see #PROPERTY_KEY_HEADER
	 * @see #PROPERTY_KEY_PIPELINE_PLUGIN_NAME
	 * @see #inputDirectory
	 * @see #outputDirectory
	 * @see #scriptFilename
	 * @see #objectListFormat
	 * @see #urlBase
	 * @see #tea
	 */
	public void initialise() throws NullPointerException,Exception
	{
		String keyString = null;

		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise() started.");
		// inputDirectory
		keyString = PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".input_directory";
		inputDirectory = tea.getPropertyString(keyString);
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise: input directory: key: "+keyString+" value: "+inputDirectory+".");
		if(inputDirectory == null)
		{
			throw new NullPointerException(this.getClass().getName()+":initialise:inputDirectory:key:"+
						       keyString+" returned null value.");
		}
		// outputDirectory
		keyString = PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".output_directory";
		outputDirectory = tea.getPropertyString(keyString);
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise: output directory: key: "+keyString+" value: "+outputDirectory+".");
		if(outputDirectory == null)
		{
			throw new NullPointerException(this.getClass().getName()+":initialise:outputDirectory:key:"+
						       keyString+" returned null value.");
		}
		// scriptFilename
		keyString = PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".script_filename";
		scriptFilename = tea.getPropertyString(keyString);
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise: script filename: key: "+keyString+" value: "+scriptFilename+".");
		if(scriptFilename == null)
		{
			throw new NullPointerException(this.getClass().getName()+":initialise:scriptFilename:key:"+
						       keyString+" returned null value.");
		}
		// urlBase - ensure terminated with '/'
		keyString = PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".http_base";
		urlBase = tea.getPropertyString(keyString);
		if(scriptFilename == null)
		{
			throw new NullPointerException(this.getClass().getName()+":initialise:urlBase:key:"+
						       keyString+" returned null value.");
		}
		if(urlBase.endsWith("/") == false)
			urlBase = urlBase+"/";
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise: URL base: "+urlBase+".");
		// objectListFormat
		keyString = PROPERTY_KEY_HEADER+"."+id+"."+instrumentId+".object_list_format";
		objectListFormat = tea.getPropertyString(keyString);
		logger.log(INFO, 1, CLASS, tea.getId(),"initialise",this.getClass().getName()+
			   ":initialise: object list format: "+objectListFormat+".");
		if(objectListFormat == null)
		{
			throw new NullPointerException(this.getClass().getName()+":initialise:objectListFormat:key:"+
						       keyString+" returned null value.");
		}
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
		String s = null;
		String objectListLeafName = null;
		File outputFile = null;
		File outputObjectListFile = null;

		logger.log(INFO, 1, CLASS, tea.getId(),"processFile",this.getClass().getName()+
			   ":processFile("+inputFile+") started.");
		// construct output FITS filename in outputDirectory
		inputLeafName = inputFile.getName();
		s = stripExtension(inputLeafName);
		if(s.endsWith("_1"))
			s = s.substring(0,s.length()-2);
		outputFile = new File(outputDirectory,s+"_2"+".fits");
		// construct output object list file
		objectListLeafName = stripExtension(inputLeafName);
		objectListLeafName = objectListLeafName+"."+objectListFormat;
		outputObjectListFile = new File(outputDirectory,objectListLeafName);
		// call script
		logger.log(INFO, 1, CLASS, tea.getId(),"processFile",this.getClass().getName()+
			   ":processFile calling callScript.");
		callScript(inputFile,outputFile,outputObjectListFile);
		// copy results into imageData
		logger.log(INFO, 1, CLASS, tea.getId(),"processFile",this.getClass().getName()+
			   ":processFile calling createImageData.");
		imageData = createImageData(outputFile,outputObjectListFile);
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
	 * @param outputObjectListFile The local output object list filename.
	 * @exception Exception Thrown if the script execution failed.
	 * @see #scriptFilename
	 */
	protected void callScript(File inputFile,File outputFile,File outputObjectListFile) throws Exception
	{
		ExecuteCommand executeCommand = null;
		Exception e = null;
		int exitValue;

		logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			   ":callScript("+inputFile+","+outputFile+","+outputObjectListFile+
			   ") started with script filename "+scriptFilename+".");
		executeCommand = new ExecuteCommand("");
		executeCommand.setCommandString(scriptFilename+" "+inputFile+" "+outputFile+" "+outputObjectListFile);
		executeCommand.run();
		logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			   ":callScript("+inputFile+","+outputFile+","+outputObjectListFile+") produced output:\n"+
			   executeCommand.getOutputString());
		logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			   ":callScript("+inputFile+","+outputFile+","+outputObjectListFile+
			   ") produced error string:\n"+
			   executeCommand.getErrorString());
		e = executeCommand.getException();
		if(e != null)
		{
			logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			  ":callScript("+inputFile+","+outputFile+","+outputObjectListFile+
				   ") created exception "+e+".");
			logger.dumpStack(1,e);
			throw e;
		}
		exitValue = executeCommand.getExitValue();
		if(exitValue != 0)
		{
			logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
				":callScript("+inputFile+","+outputFile+","+outputObjectListFile+
				   ") returned exit value "+exitValue+".");
			throw new Exception(this.getClass().getName()+
			      ":callScript("+inputFile+","+outputFile+","+outputObjectListFile+
					    ") returned exit value "+exitValue+".");
		}
		logger.log(INFO, 1, CLASS, tea.getId(),"callScript",this.getClass().getName()+
			   ":callScript("+inputFile+","+outputFile+","+outputObjectListFile+") finished.");
	}

	/**
	 * Create an instance of RTMLImageData.
	 * @param outputFile The file to get the contents of the imageData from.
	 * @param outputObjectListFile The file containing any object data.
	 * @return An instance of RTMLImageData with suitable data.
	 * @see #urlBase
	 * @see #objectListFormat
	 * @see #loadClusterFile
	 */
	protected RTMLImageData createImageData(File outputFile,File outputObjectListFile)
	{
		RTMLImageData data = null;
		FITSHeaderLoader headerLoader = null;

		logger.log(INFO, 1, CLASS, tea.getId(),"createImageData",this.getClass().getName()+
			   ":createImageData("+outputFile+","+outputObjectListFile+") started.");
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
					   ":createImageData("+outputFile+","+outputObjectListFile+
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
					   ":createImageData("+outputFile+","+outputObjectListFile+
					   "): failed to set URL "+e+".");
				logger.dumpStack(1,e);
				// don't fail data pipeline because of this
				// Some data pipelines may fail to produce cluster files.
			}
		}
		if(outputObjectListFile != null)
		{
			if(objectListFormat.equals("cluster"))
			{
				try
				{
					loadClusterFile(outputObjectListFile,data);
				}
				catch(Exception e)
				{
					logger.log(INFO, 1, CLASS, tea.getId(),"createImageData",this.getClass().getName()+
						   ":createImageData("+outputFile+","+outputObjectListFile+
						   "): failed to load cluster file "+e+".");
					logger.dumpStack(1,e);
					// don't fail data pipeline because of this
					// Some data pipelines may fail to produce cluster files.
				}
			}
			else if(objectListFormat.equals("votable"))
			{
				try
				{
					data.setObjectListType("votable-url");
					data.setObjectListVOTableURL(urlBase+outputObjectListFile.getName());
				}
				catch(Exception e)
				{
					logger.log(INFO, 1, CLASS, tea.getId(),"createImageData",this.getClass().getName()+
						   ":createImageData("+outputFile+","+outputObjectListFile+
						   "): failed to set votable-url "+e+".");
					logger.dumpStack(1,e);
					// don't fail data pipeline because of this
					// Some data pipelines may fail to produce cluster files.
				}
			}
		}
		logger.log(INFO, 1, CLASS, tea.getId(),"createImageData",this.getClass().getName()+
			   ":createImageData("+outputFile+","+outputObjectListFile+") returned data:"+data+".");
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
			cluster = Cluster.load(outputClusterFile);
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
// Revision 1.13  2008/03/31 14:14:56  cjm
// Pipeline Plugin's are now organised by name/Id rather than type of instrument.
//
// Revision 1.12  2007/05/01 10:06:42  cjm
// More testing of return values in initialise, and more logging of what
// data it is retrieving.
//
// Revision 1.11  2007/04/30 17:10:32  cjm
// Made loading the pipeline configs TUPI/instrument type specific.
//
// Revision 1.10  2006/02/10 15:01:49  cjm
// objectListFormat now expects "votable" rather than "votable-url".
// This allows created votables to have extension "votable" but we still pass "votable-url"
// to the RTML parsing library.
//
// Revision 1.9  2005/10/24 10:53:06  cjm
// Fixed problem in loadClusterFile, which was calling a static load method as if it was an instance method.
//
// Revision 1.8  2005/08/19 17:26:49  cjm
// Added ability to handle votable-url's as well as cluster for object list files from the data pipeline.
//
// Revision 1.7  2005/08/03 14:01:01  cjm
// More logging added.
//
// Revision 1.6  2005/06/22 16:05:37  cjm
// Added id for per-instance configuration of the default pipeline.
//
// Revision 1.5  2005/05/27 09:51:40  cjm
// Fixed urlBase fix.
//
// Revision 1.4  2005/05/26 11:01:43  cjm
// Added termination check to urlBase.
//
// Revision 1.3  2005/05/25 15:55:16  cjm
// Changed output filename leaf name.
//
// Revision 1.2  2005/05/25 15:12:13  cjm
// Moved output/error string logging so it's logged even if the script fails.
//
// Revision 1.1  2005/05/23 16:00:15  cjm
// Initial revision
//
//
