/*   
    Copyright 2006, Astrophysics Research Institute, Liverpool John Moores University.

    This file is part of org.estar.tea.

    org.estar.rtml is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    org.estar.rtml is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with org.estar.rtml; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/
// DeviceInstrumentUtilites.java
// $Header: /space/home/eng/cjm/cvs/tea/java/org/estar/tea/DeviceInstrumentUtilites.java,v 1.10 2013-01-14 11:45:54 cjm Exp $
package org.estar.tea;

import java.lang.reflect.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.toop.*;

import ngat.phase2.*;
import ngat.util.*;
import ngat.util.logging.*;

/**
 * Utility routines for %lt;Device&gt; -> Instrument mapping.
 * @author Chris Mottram
 * @version $Revision: 1.10 $
 */
public class DeviceInstrumentUtilites implements Logging
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: DeviceInstrumentUtilites.java,v 1.10 2013-01-14 11:45:54 cjm Exp $";
	/**
	 * Classname for logging.
	 */
	public static final String CLASS = "DeviceInstrumentUtilites";
	/**
	 * The type of instrument.
	 */
	public final static int INSTRUMENT_TYPE_NONE        = 0;
	/**
	 * The type of instrument.
	 */
	public final static int INSTRUMENT_TYPE_CCD         = 1;
	/**
	 * The type of instrument.
	 */
	public final static int INSTRUMENT_TYPE_IRCAM       = 2;
	/**
	 * The type of instrument.
	 */
	public final static int INSTRUMENT_TYPE_POLARIMETER = 3;
	/**
	 * The type of instrument.
	 */
	public final static int INSTRUMENT_TYPE_SPECTROGRAPH = 4;
	/**
	 * A string representation of the instrument type.
	 */
	public final static String INSTRUMENT_TYPE_CCD_STRING = "ccd";
	/**
	 * A string representation of the instrument type.
	 */
	public final static String INSTRUMENT_TYPE_IRCAM_STRING = "ircam";
	/**
	 * A string representation of the instrument type.
	 */
	public final static String INSTRUMENT_TYPE_POLARIMETER_STRING = "polarimeter";
	/**
	 * A string representation of the instrument type.
	 */
	public final static String INSTRUMENT_TYPE_SPECTROGRAPH_STRING = "spectrograph";

	/**
	 * Class logger.
	 */
	protected static Logger logger = null;

	/**
	 * Create a suitable subclass of InstrumentConfig, from the RTML device information.
	 * @param tea The instance of TelescopeEmbeddedAgent, used to retrieve configuration data.
	 * @param device The instance of RTML Device data to create an InstrumentConfig from.
	 * @return An instance of a subclass of InstrumentConfig.
	 * @exception IllegalArgumentException Thrown if an argument is wrong.
	 * @exception ClassNotFoundException Thrown if createInstrumentConfig fails.
	 * @exception NoSuchMethodException Thrown if createInstrumentConfig fails.
	 * @exception InstantiationException Thrown if createInstrumentConfig fails.
	 * @exception IllegalAccessException Thrown if createInstrumentConfig fails.
	 * @exception InvocationTargetException Thrown if createInstrumentConfig fails.
	 * @exception Exception Thrown if getCCDLowerFilterType/getCCDUpperFilterType/getIRCamFilterType fails.
	 * @see TelescopeEmbeddedAgent
	 * @see org.estar.rtml.RTMLDevice
	 * @see #getInstrumentType
	 * @see #getInstrumentId
	 * @see #getInstrumentDetectorBinning
	 * @see #getInstrumentDetectorGain
	 * @see #getCCDSingleFilterType
	 * @see #getCCDLowerFilterType
	 * @see #getCCDUpperFilterType
	 * @see #getCCDIndexFilterType
	 * @see #getIRCamFilterType
	 * @see #createInstrumentConfig
	 * @see #INSTRUMENT_TYPE_CCD
	 * @see #INSTRUMENT_TYPE_IRCAM
	 * @see #INSTRUMENT_TYPE_POLARIMETER
	 * @see #INSTRUMENT_TYPE_CCD_STRING
	 * @see #INSTRUMENT_TYPE_IRCAM_STRING
	 * @see #INSTRUMENT_TYPE_POLARIMETER_STRING
	 * @see ngat.phase2.CCDConfig
	 * @see ngat.phase2.CCDDetector
	 * @see ngat.phase2.IRCamConfig
	 * @see ngat.phase2.IRCamDetector
	 * @see ngat.phase2.PolarimeterConfig
	 * @see ngat.phase2.PolarimeterDetector
	 */
	public static InstrumentConfig getInstrumentConfig(TelescopeEmbeddedAgent tea,RTMLDevice device) throws
		IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, 
		IllegalAccessException, InvocationTargetException, Exception
	{
		InstrumentConfig config = null;
		String instrumentId = null;
		String rtmlFilterType = null;
		String lowerFilterType = null;
		String upperFilterType = null;
		String filterType0 = null;
		String filterType1 = null;
		String filterType2 = null;
		String irFilterType = null;
		String oFilterType = null;
		String configClassName = null;
		double gain;
		int bin,instrumentType;

		// get type
		instrumentType = getInstrumentType(device);
		// get id
		instrumentId = getInstrumentId(tea,device);
		// get config class name for id
		configClassName = tea.getFilterMap().getProperty("filter."+instrumentId+".config.class");
		if(configClassName == null)
		{
			throw new IllegalArgumentException("getInstrumentConfig:No Config class name "+
							   "found for instrument "+instrumentId+".");
		}
		// fill in config based on config class name
		// ugly but it works.
		if(configClassName.equals("dev.lt.RATCamConfig")||configClassName.equals("ngat.phase2.CCDConfig"))
		{
			rtmlFilterType = device.getFilterType();
			lowerFilterType = getCCDLowerFilterType(tea,instrumentId,rtmlFilterType);
			upperFilterType = getCCDUpperFilterType(tea,instrumentId,rtmlFilterType);
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			// create config
			config = createInstrumentConfig(configClassName,"TEA-"+INSTRUMENT_TYPE_CCD_STRING+"-"+
							lowerFilterType+"-"+upperFilterType+"-"+bin+"x"+bin);
			if (! (config instanceof CCDConfig))
			{
				throw new IllegalArgumentException(
					   "getInstrumentConfig:Invalid config class for optical:"+
					   config.getClass().getName());
			}
			// fill in config fields
			CCDConfig ccdConfig = (CCDConfig)config;
			ccdConfig.setLowerFilterWheel(lowerFilterType);
			ccdConfig.setUpperFilterWheel(upperFilterType);
			CCDDetector ccdDetector = (CCDDetector)ccdConfig.getDetector(0);
			ccdDetector.clearAllWindows();
			ccdDetector.setXBin(bin);
			ccdDetector.setYBin(bin);
		}
		else if(configClassName.equals("ngat.phase2.GenericCCDConfig"))
		{
			rtmlFilterType = device.getFilterType();
			filterType0 = getCCDIndexFilterType(tea,instrumentId,0,rtmlFilterType);
			filterType1 = getCCDIndexFilterType(tea,instrumentId,1,rtmlFilterType);
			filterType2 = getCCDIndexFilterType(tea,instrumentId,2,rtmlFilterType);
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			// create config
			config = createInstrumentConfig(configClassName,"TEA-"+INSTRUMENT_TYPE_CCD_STRING+"-"+
							filterType0+"-"+filterType1+"-"+filterType2+"-"+bin+"x"+bin);
			if (! (config instanceof GenericCCDConfig))
			{
				throw new IllegalArgumentException(
					   "getInstrumentConfig:Invalid config class for generic CCD:"+
					   config.getClass().getName());
			}
			// fill in config fields
			GenericCCDConfig ccdConfig = (GenericCCDConfig)config;
			ccdConfig.setFilterName(0,filterType0);
			ccdConfig.setFilterName(1,filterType1);
			ccdConfig.setFilterName(2,filterType2);
			CCDDetector ccdDetector = (CCDDetector)ccdConfig.getDetector(0);
			ccdDetector.clearAllWindows();
			ccdDetector.setXBin(bin);
			ccdDetector.setYBin(bin);
		}
		else if(configClassName.equals("ngat.phase2.OConfig"))
		    {
			rtmlFilterType = device.getFilterType();
			oFilterType = getCCDSingleFilterType(tea,instrumentId,rtmlFilterType);
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			// create config
			config = createInstrumentConfig(configClassName,
							"TEA-"+INSTRUMENT_TYPE_CCD_STRING+"-"+
							oFilterType+"-"+bin+"x"+bin);
			if (! (config instanceof OConfig))
			    {
				throw new 
				    IllegalArgumentException("getInstrumentConfig:Invalid config class for o:"+
							     config.getClass().getName());
			}
			// fill in config fields
			OConfig oConfig = (OConfig)config; 
			oConfig.setFilterWheel(oFilterType);
			ODetector oDetector = (ODetector)oConfig.getDetector(0);
			oDetector.clearAllWindows();
			oDetector.setXBin(bin);
			oDetector.setYBin(bin);
		    }
                else if(configClassName.equals("ngat.phase2.IRCamConfig"))
		    {
                        rtmlFilterType = device.getFilterType();
                        irFilterType = getIRCamFilterType(tea,instrumentId,rtmlFilterType);
                        // This needs to get more sophisticated if we allow non-square binning
                        bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
                        // create config
                        config = createInstrumentConfig(configClassName,
							"TEA-"+INSTRUMENT_TYPE_IRCAM_STRING+"-"+irFilterType+"-"+bin+"x"+bin);
                        if (! (config instanceof IRCamConfig))
			    {
                                throw new IllegalArgumentException(
                                          "getInstrumentConfig:Invalid config class for infrared:"+
                                          config.getClass().getName());
			    }
                        // fill in config fields
                        IRCamConfig irCamConfig = (IRCamConfig)config;
                        irCamConfig.setFilterWheel(irFilterType);
                        IRCamDetector irCamDetector = (IRCamDetector)irCamConfig.getDetector(0);
                        irCamDetector.clearAllWindows();
                        irCamDetector.setXBin(bin);
                        irCamDetector.setYBin(bin);

		    }
		else if(configClassName.equals("ngat.phase2.PolarimeterConfig"))
		    {
			rtmlFilterType = device.getFilterType();
			// We could check rtmlFilterType against a fixed constant/null and error
			// if it does not have the correct type.
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			// create config
			config = createInstrumentConfig(configClassName,
					"TEA-"+INSTRUMENT_TYPE_POLARIMETER_STRING+"-Fixed-"+bin+"x"+bin);
			if (! (config instanceof PolarimeterConfig))
			{
				throw new IllegalArgumentException(
					     "getInstrumentConfig:Invalid config class for polarimeter:"+
					     config.getClass().getName());
			}
			PolarimeterConfig polarimeterConfig = (PolarimeterConfig)config;
			PolarimeterDetector polarimeterDetector = (PolarimeterDetector)polarimeterConfig.
				getDetector(0);
			polarimeterDetector.clearAllWindows();
			polarimeterDetector.setXBin(bin);
			polarimeterDetector.setYBin(bin);
		}
		else if(configClassName.equals("ngat.phase2.Ringo3PolarimeterConfig"))
		    {
			rtmlFilterType = device.getFilterType();
			// We could check rtmlFilterType against a fixed constant/null and error
			// if it does not have the correct type.
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			gain = getInstrumentDetectorGain(tea,instrumentType,instrumentId,device.getDetector());
			// create config
			config = createInstrumentConfig(configClassName,"TEA-Ringo3-External-"+gain+"-"+bin+"x"+bin);
			if (! (config instanceof Ringo3PolarimeterConfig))
			{
				throw new IllegalArgumentException(
					     "getInstrumentConfig:Invalid config class for Ringo3 polarimeter:"+
					     config.getClass().getName());
			}
			Ringo3PolarimeterConfig ringo3Config = (Ringo3PolarimeterConfig)config;
			ringo3Config.setTriggerType(Ringo3PolarimeterConfig.TRIGGER_TYPE_EXTERNAL);
			ringo3Config.setEmGain((int)(gain));
			// loop over detectors 0-2
			for(int i = 0; i < ringo3Config.getMaxDetectorCount(); i++)
			{
				Ringo3PolarimeterDetector ringo3Detector = (Ringo3PolarimeterDetector)
					ringo3Config.getDetector(i);
				ringo3Detector.clearAllWindows();
				ringo3Detector.setXBin(bin);
				ringo3Detector.setYBin(bin);
			}
		}
		else if(configClassName.equals("ngat.phase2.RISEConfig"))
		{
			rtmlFilterType = device.getFilterType();
			// We could check rtmlFilterType against a fixed constant/null and error
			// if it does not have the correct type.
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			// create config
			config = createInstrumentConfig(configClassName,
					"TEA-"+INSTRUMENT_TYPE_CCD_STRING+"-Fixed-"+bin+"x"+bin);
			if (! (config instanceof RISEConfig))
			{
				throw new IllegalArgumentException(
					     "getInstrumentConfig:Invalid config class for RISE:"+
					     config.getClass().getName());
			}
			RISEConfig riseConfig = (RISEConfig)config;
			RISEDetector riseDetector = (RISEDetector)riseConfig.
				getDetector(0);
			riseDetector.clearAllWindows();
			riseDetector.setXBin(bin);
			riseDetector.setYBin(bin);
		}
		else if(configClassName.equals("ngat.phase2.LowResSpecConfig"))
		{
			RTMLGrating grating = null;
			double wavelength;

			// Get central wavelength
			grating = device.getGrating();
			wavelength = grating.getWavelengthAngstroms();
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			// create config
			config = createInstrumentConfig(configClassName,
					"TEA-"+INSTRUMENT_TYPE_SPECTROGRAPH_STRING+"-Fixed-"+bin+"x"+bin);
			if (! (config instanceof LowResSpecConfig))
			{
				throw new IllegalArgumentException(
					     "getInstrumentConfig:Invalid config class for low res spectrograph:"+
					     config.getClass().getName());
			}
			LowResSpecConfig lowResSpecConfig = (LowResSpecConfig)config;
			lowResSpecConfig.setWavelength(wavelength);
			LowResSpecDetector lowResSpecDetector = (LowResSpecDetector)lowResSpecConfig.getDetector(0);
			lowResSpecDetector.clearAllWindows();
			lowResSpecDetector.setXBin(bin);
			lowResSpecDetector.setYBin(bin);
		}
		else if(configClassName.equals("ngat.phase2.FrodoSpecConfig"))
		{
			RTMLGrating grating = null;
			String gratingName = null;

			// Get grating name, low or high
			grating = device.getGrating();
			if(grating == null)
			{
				throw new IllegalArgumentException("getInstrumentConfig:No Grating specified for "+
								   "instrument "+instrumentId+".");
			}
			gratingName = grating.getName();
			if(gratingName == null)
			{
				throw new IllegalArgumentException("getInstrumentConfig:No Grating name specified for "+
								   "instrument "+instrumentId+".");
			}
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			// create config
			config = createInstrumentConfig(configClassName,"TEA-"+INSTRUMENT_TYPE_SPECTROGRAPH_STRING+
							"-"+instrumentId+"-"+gratingName+"-"+bin+"x"+bin);
			if (! (config instanceof FrodoSpecConfig))
			{
				throw new IllegalArgumentException(
					     "getInstrumentConfig:Invalid config class for FrodoSpec spectrograph:"+
					     config.getClass().getName());
			}
			FrodoSpecConfig frodospecConfig = (FrodoSpecConfig)config;
			if(instrumentId.equals("frodospec-red"))
				frodospecConfig.setArm(FrodoSpecConfig.RED_ARM);
			else if(instrumentId.equals("frodospec-blue"))
				frodospecConfig.setArm(FrodoSpecConfig.BLUE_ARM);
			else
			{
				throw new IllegalArgumentException(
					     "getInstrumentConfig:Invalid instrument ID for FrodoSpec spectrograph:"+
					     instrumentId);
			}
			if(gratingName.equals("low"))
				frodospecConfig.setResolution(FrodoSpecConfig.RESOLUTION_LOW);
			else if(gratingName.equals("high"))
				frodospecConfig.setResolution(FrodoSpecConfig.RESOLUTION_HIGH);
			else
			{
				throw new IllegalArgumentException(
					     "getInstrumentConfig:Invalid grating name for FrodoSpec spectrograph:"+
					     gratingName);
			}
			FrodoSpecDetector frodospecDetector = (FrodoSpecDetector)frodospecConfig.getDetector(0);
			frodospecDetector.clearAllWindows();
			frodospecDetector.setXBin(bin);
			frodospecDetector.setYBin(bin);
		}
		else
		{
			throw new IllegalArgumentException("getInstrumentConfig:Unknown Config class name "+
							   configClassName+" for instrument "+instrumentId+".");
		}
		return config;
	}

	/**
	 * Send a suitable INSTR command to a running TOCSession.
	 * @param tea The instance of TelescopeEmbeddedAgent, used to retrieve configuration data.
	 * @param session An instance of TOCSession which has already opened a TOCA session.
	 * @param device The instance of RTML Device data to extract the INSTR parameters from.
	 * @exception IllegalArgumentException Thrown if an argument is wrong, 
	 *            or if there is no toop instrument mapping.
	 * @exception TOCException Thrown if instrRatcam/instrIRcam/instrRingoStar fails.
	 * @exception Exception Thrown if getCCDLowerFilterType/getCCDUpperFilterType/getIRCamFilterType fails.
	 * @see TelescopeEmbeddedAgent
	 * @see org.estar.rtml.RTMLDevice
	 * @see org.estar.toop.TOCSession
	 * @see org.estar.toop.TOCSession#instrRatcam
	 * @see org.estar.toop.TOCSession#instrImager
	 * @see org.estar.toop.TOCSession#instrMerope
	 * @see org.estar.toop.TOCSession#instrRISE
	 * @see org.estar.toop.TOCSession#instrIOO
	 * @see org.estar.toop.TOCSession#instrIRcam
	 * @see org.estar.toop.TOCSession#instrRingoStar
	 * @see org.estar.toop.TOCSession#instrRingo3
	 * @see org.estar.toop.TOCSession#instrMeaburnSpec
	 * @see #getInstrumentType
	 * @see #getCCDSingleFilterType
	 * @see #getCCDLowerFilterType
	 * @see #getCCDUpperFilterType
	 * @see #getCCDIndexFilterType
	 * @see #getInstrumentDetectorBinning
	 * @see #getIRCamFilterType
	 * @see #INSTRUMENT_TYPE_CCD
	 * @see #INSTRUMENT_TYPE_IRCAM
	 * @see #INSTRUMENT_TYPE_POLARIMETER
	 */
	public static void sendInstr(TelescopeEmbeddedAgent tea,TOCSession session,RTMLDevice device) throws
		IllegalArgumentException,TOCException, Exception
	{
		String instrumentId = null;
		String rtmlFilterType = null;
		String lowerFilterType = null;
		String upperFilterType = null;
		String irFilterType = null;
		String oFilterType = null;
		String filterType0 = null;
		String filterType1 = null;
		String filterType2 = null;
		String toopInstrName = null;
		double gain;
		int bin,instrumentType;

		// get instrument type
		instrumentType = getInstrumentType(device);
		// get id
		instrumentId = getInstrumentId(tea,device);
		// get toop instrument name for id
		toopInstrName = tea.getPropertyString("instrument."+instrumentId+".toop");
		if(toopInstrName == null)
		{
			throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
							   "sendInstr:Instrument id "+
							   instrumentId+" has no toop instr mapping.");
		}
		if(toopInstrName.equals("RATCAM")||toopInstrName.equals("EA01")||toopInstrName.equals("EA02"))
		{
			rtmlFilterType = device.getFilterType();
			lowerFilterType = getCCDLowerFilterType(tea,instrumentId,rtmlFilterType);
			upperFilterType = getCCDUpperFilterType(tea,instrumentId,rtmlFilterType);
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			// actually configure instrument
			session.instrImager(toopInstrName,lowerFilterType,upperFilterType,bin,false,false);
		}
		else if(toopInstrName.equals("EM01")||toopInstrName.equals("EM02"))
		{
			rtmlFilterType = device.getFilterType();
			filterType0 = getCCDIndexFilterType(tea,instrumentId,0,rtmlFilterType);
			filterType1 = getCCDIndexFilterType(tea,instrumentId,1,rtmlFilterType);
			filterType2 = getCCDIndexFilterType(tea,instrumentId,2,rtmlFilterType);
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			// actually configure instrument
			session.instrMerope(toopInstrName,filterType0,filterType1,filterType2,bin);
		}
		else if(toopInstrName.equals("RISE"))
		{
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			session.instrRISE(bin,false,false);
		}
		else if(toopInstrName.equals("IRCAM"))
		{
			rtmlFilterType = device.getFilterType();
			irFilterType = getIRCamFilterType(tea,instrumentId,rtmlFilterType);
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			session.instrIRcam(irFilterType,bin,false,false);
		}
		else if(toopInstrName.equals("IO:O"))
		    {
                        rtmlFilterType = device.getFilterType();
                        oFilterType = getCCDSingleFilterType(tea,instrumentId,rtmlFilterType);
                        // This needs to get more sophisticated if we allow non-square binning
                        bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			session.instrIOO(oFilterType,bin,false,false);
		    }
		else if(toopInstrName.equals("RINGO")||toopInstrName.equals("RINGOSTAR")||
			toopInstrName.equals("GROPE"))
		{
			rtmlFilterType = device.getFilterType();
			// We could check rtmlFilterType against a fixed constant/null and error
			// if it does not have the correct type.
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			session.instrRingoStar(bin,bin,false,false);
		}
		else if(toopInstrName.equals("RINGO3"))
		{
			// This needs to get more sophisticated if we allow non-square binning
			bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			gain = getInstrumentDetectorGain(tea,instrumentType,instrumentId,device.getDetector());
			// diddly extract internal/external from RTML?
			session.instrRingo3("external",(int)gain,bin,bin,false,false);
		}
		else if(toopInstrName.equals("NUVSPEC"))
		{
			RTMLGrating grating = null;
			String wavelengthString;

			// Get central wavelength
			grating = device.getGrating();
			wavelengthString = grating.getWavelengthAngstromString();
			// This needs to get more sophisticated if we allow non-square binning
			//bin = getInstrumentDetectorBinning(tea,instrumentType,instrumentId,device.getDetector());
			session.instrMeaburnSpec(wavelengthString,false,false);
		}
		// diddly FrodoSpec
		else
		{
			throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:sendInstr:"+
							   "Unknown Type "+instrumentType+" detected.");
		}
	}

	/**
	 * Get the filter type of an single filter CCD instrument (e.g. IO:O), from the TEA's filter map.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the filter map from.
	 * @param instrumentId The id of the instrument to get the gilter mapping from.
	 * @param rtmlFilterType A string respresenting an single filter type, e.g. '?'.
	 * @return A String containing the filter type of the filter in the wheel for this config
	 *         e.g. '????'.
	 * @exception Exception Thrown if the filter mapping is not found in the filter map.
	 * @see TelescopeEmbeddedAgent
	 * @see TelescopeEmbeddedAgent#getFilterMap
	 */
	public static String getCCDSingleFilterType(TelescopeEmbeddedAgent tea,String instrumentId,
						    String rtmlFilterType)
                throws Exception
	{
		return tea.getFilterMap().getProperty("filter."+instrumentId+"."+rtmlFilterType);
	}
    
	/**
	 * Get the lower filter type of a CCD instrument, from the TEA's filter map.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the filter map from.
	 * @param instrumentId The id of the instrument to get the gilter mapping from.
	 * @param rtmlFilterType A string respresenting a CCD filter type, e.g. 'R'.
	 * @return A String containing the CCDConfig filter type of the filter in the lower wheel for this config
	 *         e.g. 'SDSS-R'.
	 * @exception Exception Thrown if the filter mapping is not found in the filter map.
	 * @see TelescopeEmbeddedAgent
	 * @see TelescopeEmbeddedAgent#getFilterMap
	 * @see #INSTRUMENT_TYPE_CCD_STRING
	 */
	public static String getCCDLowerFilterType(TelescopeEmbeddedAgent tea,String instrumentId,
						   String rtmlFilterType) throws Exception
	{
		return tea.getFilterMap().getProperty("filter."+instrumentId+".lower."+rtmlFilterType);
	}

	/**
	 * Get the upper filter type of a CCD instrument, from the TEA's filter map.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the filter map from.
	 * @param instrumentId The id of the instrument to get the gilter mapping from.
	 * @param rtmlFilterType A string respresenting a CCD filter type, e.g. 'R'.
	 * @return A String containing the CCDConfig filter type of the filter in the upper wheel for this config
	 *         e.g. 'clear'.
	 * @exception Exception Thrown if the filter mapping is not found in the filter map.
	 * @see TelescopeEmbeddedAgent
	 * @see TelescopeEmbeddedAgent#getFilterMap
	 * @see #INSTRUMENT_TYPE_CCD_STRING
	 */
	public static String getCCDUpperFilterType(TelescopeEmbeddedAgent tea,String instrumentId,
						   String rtmlFilterType) 
		throws Exception
	{
		return tea.getFilterMap().getProperty("filter."+instrumentId+".upper."+rtmlFilterType);
	}

	/**
	 * Get the filter type of a CCD instrument, from the TEA's filter map, for index wheel in the filter
	 * (GenericCCDConfig).
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the filter map from.
	 * @param instrumentId The id of the instrument to get the filter mapping from.
	 * @param index The index of the wheel.
	 * @param rtmlFilterType A string respresenting a CCD filter type, e.g. 'R'.
	 * @return A String containing the CCDConfig filter type of the filter in the index'th wheel for this config
	 *         e.g. 'air'.
	 * @exception Exception Thrown if the filter mapping is not found in the filter map.
	 * @see TelescopeEmbeddedAgent
	 * @see TelescopeEmbeddedAgent#getFilterMap
	 * @see #INSTRUMENT_TYPE_CCD_STRING
	 */
	public static String getCCDIndexFilterType(TelescopeEmbeddedAgent tea,String instrumentId,int index,
						   String rtmlFilterType) throws Exception
	{
		return tea.getFilterMap().getProperty("filter."+instrumentId+"."+index+"."+rtmlFilterType);
	}

	/**
	 * Get the filter type of a IRCAM instrument, from the TEA's filter map.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the filter map from.
	 * @param instrumentId The id of the instrument to get the gilter mapping from.
	 * @param rtmlFilterType A string respresenting an IRCAM filter type, e.g. 'J'.
	 * @return A String containing the IRCamConfig filter type of the filter in the wheel for this config
	 *         e.g. 'Barr-J'.
	 * @exception Exception Thrown if the filter mapping is not found in the filter map.
	 * @see TelescopeEmbeddedAgent
	 * @see TelescopeEmbeddedAgent#getFilterMap
	 * @see #INSTRUMENT_TYPE_IRCAM_STRING
	 */
	public static String getIRCamFilterType(TelescopeEmbeddedAgent tea,String instrumentId,String rtmlFilterType) 
		throws Exception
	{
		return tea.getFilterMap().getProperty("filter."+instrumentId+"."+rtmlFilterType);
	}

	/**
	 * Get a binning value for the specified Detector. Note this method currently assumes square binning
	 * for all available instruments, and will have to change when a non-square binned instrument 
	 * becomes available.
	 * If no binning is specified in the RTML, the TEA property "instrument."+instrumentId+".bin.default" is
	 * used to get the default binning.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the instrument properties from.
	 * @param instrumentType Which sort of instrument we are getting the binning for.
	 * @param instrumentId The id of the instrument to get the default binning from, if needed.
	 * @param detector The RTML detector instance. This is allowed to be null if no
	 *        &lt;Detector&gt; tag has been specified in the RTML.
	 * @return An integer, the binning to use for this instrument.
	 * @exception NGATPropertyException Thrown by getPropertyInteger if there is a problem parsing the default
	 *            binning.
	 * @see TelescopeEmbeddedAgent#getPropertyInteger
	 */
	public static int getInstrumentDetectorBinning(TelescopeEmbeddedAgent tea,int instrumentType,
						       String instrumentId,RTMLDetector detector)
		throws NGATPropertyException
	{
		int bin;

		if(detector != null)
		{
			// binning
			if(detector.getColumnBinning() != detector.getRowBinning())
			{
				throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
								   "getInstrumentDetectorBinning:"+
								   "Row/Column binning must be equal: row: "+
								   detector.getRowBinning()+
								   " and column: "+detector.getColumnBinning()+".");
			}
			bin = detector.getColumnBinning();
			if((instrumentType == INSTRUMENT_TYPE_IRCAM)&&(bin != 1))
			{
				throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
								   "getInstrumentDetectorBinning:SupIRCam (IRCAM) "+
								   "Row/Column binning must be 1.");
			}
		}
		else
		{
			// we allow device with no Detector, so set a default bin
			// Retrieved from config
			bin = tea.getPropertyInteger("instrument."+instrumentId+".bin.default");
		}
		return bin;
	}

	/**
	 * Get a gain value for the specified Detector. 
	 * If no gain is specified in the RTML, the TEA property "instrument."+instrumentId+".gain.default" is
	 * used to get the default gain.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the instrument properties from.
	 * @param instrumentType Which sort of instrument we are getting the gain for.
	 * @param instrumentId The id of the instrument to get the default gain from, if needed.
	 * @param detector The RTML detector instance. This is allowed to be null if no
	 *        &lt;Detector&gt; tag has been specified in the RTML.
	 * @return An double, the gain to use for this instrument.
	 * @exception NGATPropertyException Thrown by getPropertyDouble if there is a problem parsing the default
	 *            gain.
	 * @see TelescopeEmbeddedAgent#getPropertyDouble
	 */
	public static double getInstrumentDetectorGain(TelescopeEmbeddedAgent tea,int instrumentType,
						    String instrumentId,RTMLDetector detector)
		throws NGATPropertyException
	{
		double gain = 0.0;
		boolean useDefaultGain;

		useDefaultGain = true;
		if(detector != null)
		{
			if(detector.getUseGain())
			{
				gain = detector.getGain();
				useDefaultGain = false;
			}
		}
		if(useDefaultGain)
		{
			// we allow device with no Detector, or a Detector with no gain set.
			// So set a default gain retrieved from config
			gain = tea.getPropertyDouble("instrument."+instrumentId+".gain.default");
		}
		if((gain < 1.0)||(gain > 100.0))
		{
			throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
							   "getInstrumentDetectorGain:Gain "+gain+
							   " out of range (0..100).");
		}
		return gain;
	}

	/**
	 * Get the name of the type of instrument the specifed device represents.
	 * @param device The RTMLDevice to parse.
	 * @return A string respresenting the type of instrument, one of: "ccd","ircam", "polarimeter", "spectrograph".
	 * @exception IllegalArgumentException Thrown if the instrument type is not receognised.
	 * @see #INSTRUMENT_TYPE_NONE
	 * @see #INSTRUMENT_TYPE_CCD
	 * @see #INSTRUMENT_TYPE_IRCAM
	 * @see #INSTRUMENT_TYPE_POLARIMETER
	 * @see #INSTRUMENT_TYPE_SPECTROGRAPH
	 * @see #INSTRUMENT_TYPE_CCD_STRING
	 * @see #INSTRUMENT_TYPE_IRCAM_STRING
	 * @see #INSTRUMENT_TYPE_POLARIMETER_STRING
	 * @see #INSTRUMENT_TYPE_SPECTROGRAPH_STRING
	 */
	public static String getInstrumentTypeName(RTMLDevice device) throws IllegalArgumentException
	{
		int instrumentType;

		instrumentType = getInstrumentType(device);
		switch(instrumentType)
		{
		case INSTRUMENT_TYPE_NONE:
		    throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
						       "getInstrumentTypeName:Type NONE detected.");
		case INSTRUMENT_TYPE_CCD:
		    return INSTRUMENT_TYPE_CCD_STRING;
		case INSTRUMENT_TYPE_IRCAM:
		    return INSTRUMENT_TYPE_IRCAM_STRING;
		case INSTRUMENT_TYPE_POLARIMETER:
		    return INSTRUMENT_TYPE_POLARIMETER_STRING;
		case INSTRUMENT_TYPE_SPECTROGRAPH:
		    return INSTRUMENT_TYPE_SPECTROGRAPH_STRING;
		default:
		    throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
						       "getInstrumentName:Unknown Type "+instrumentType+
						       " detected.");
		}
	}

	/**
	 * Get what type of instrument the specifed device represents.
	 * @param device The RTMLDevice to parse.
	 * @return An integer respresenting the type of instrument.
	 * @exception IllegalArgumentException Thrown if the instrument type is not receognised.
	 * @exception NullPointerException Thrown if the device of type attribute was null.
	 * @see #INSTRUMENT_TYPE_CCD
	 * @see #INSTRUMENT_TYPE_IRCAM
	 * @see #INSTRUMENT_TYPE_POLARIMETER
	 * @see #INSTRUMENT_TYPE_SPECTROGRAPH
	 */
	public static int getInstrumentType(RTMLDevice device) throws IllegalArgumentException, NullPointerException
	{
		String deviceType = null;
		String spectralRegion = null;
		int instrumentType;

		if(device == null)
		{
			throw new NullPointerException("org.estar.tea.DeviceInstrumentUtilites:"+
						       "getInstrumentType:device was null.");
		}
		deviceType = device.getType();
		if(deviceType == null)
		{
			throw new NullPointerException("org.estar.tea.DeviceInstrumentUtilites:"+
						       "getInstrumentType:device type was null.");
		}
		instrumentType = INSTRUMENT_TYPE_NONE;
		if(deviceType.equals("camera"))
		{
			spectralRegion = device.getSpectralRegion();
			if((spectralRegion == null) || spectralRegion.equals("optical"))
			{
				instrumentType = INSTRUMENT_TYPE_CCD;
			}
			else if(spectralRegion.equals("infrared"))
			{
				instrumentType = INSTRUMENT_TYPE_IRCAM;
			}
			else
			{
				throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
								   "getInstrumentType: camera spectral region: "+
								   spectralRegion+
								   " does not map to a known instrument type.");
			}
		}
		else if(deviceType.equals("polarimeter"))
		{
			instrumentType = INSTRUMENT_TYPE_POLARIMETER;
		}
		else if(deviceType.equals("spectrograph"))
		{
			instrumentType = INSTRUMENT_TYPE_SPECTROGRAPH;
		}
		else
		{
			throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
							   "getInstrumentType:Device "+
							   device.getType()+" not supported.");
		}
		return instrumentType;
	}

	/**
	 * Get the instrument ID string for what instrument the specifed device represents.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get instrument properties from.
	 * @param device The RTMLDevice to parse.
	 * @return A string respresenting the instrument.
	 * @exception IllegalArgumentException Thrown if the instrument is not receognised.
	 * @exception NullPointerException Thrown if the device of type attribute was null.
	 * @see #logger
	 * @see #getLoggerInstance
	 * @see #getInstrumentTypeName
	 * @see TelescopeEmbeddedAgent#getPropertyString
	 */
	public static String getInstrumentId(TelescopeEmbeddedAgent tea,RTMLDevice device) 
		throws IllegalArgumentException, NullPointerException
	{
		String deviceTypeName = null;
		String name = null;
		String instrumentId = null;
		int index;
		boolean done;

		// get logger if instance not initialised.
		if(logger == null)
			getLoggerInstance();
		if(device == null)
		{
			throw new NullPointerException("org.estar.tea.DeviceInstrumentUtilites:"+
						       "getInstrumentId:device was null.");
		}
		// get type of device and it's associated string
		deviceTypeName = getInstrumentTypeName(device);
		logger.log(INFO, 1, CLASS,"DeviceInstrumentUtilites:getInstrumentId:Searching for device of type:"+
			   deviceTypeName);
		// get name
		name = device.getName();
		if(name != null)
		{
			logger.log(INFO, 1, CLASS,"DeviceInstrumentUtilites:getInstrumentId:"+
				   "Searching for device with name:"+name);
			// retrieve instrument id from RTML type/name -> instrument id mapping in tea.properties
			index = 0;
			done = false;
			while(done == false)
			{
				String nameString = null;

				logger.log(INFO, 1, CLASS,"DeviceInstrumentUtilites:getInstrumentId:"+
					   "Checking "+deviceTypeName+" instrument index "+index);
				// get name of index'th instrument of this type
				nameString = tea.getPropertyString("instrument."+deviceTypeName+".name."+index);
				if(nameString != null)
				{
					logger.log(INFO, 1, CLASS,"DeviceInstrumentUtilites:getInstrumentId:"+
						   "Device Type "+deviceTypeName+" instrument index "+index+
						   " has name "+nameString+".");
					// is the instrument at this index the name we are looking for?
					if(name.equals(nameString))
					{
						// get the Id for the name at index
						instrumentId = tea.getPropertyString("instrument."+deviceTypeName+
										     ".id."+index);
						if(instrumentId == null)
						{
							throw new NullPointerException("org.estar.tea.DeviceInstrumentUtilites:"+
										       "getInstrumentId:name "+name+
										       " of type "+deviceTypeName+
										       " found at index "+index+
										       " but no equivalent id found.");
						}
						done = true;
						logger.log(INFO, 1, CLASS,"DeviceInstrumentUtilites:getInstrumentId:"+
							   "Device Type "+deviceTypeName+" instrument index "+index+
							 " name "+nameString+" is the instrument we are looking for.");
					}
				}
				else // run out of names to search
				{
					throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
						       "getInstrumentId:Failed to find instrument name for "+name+
									   " of type "+deviceTypeName);
				}
				index++;
			}// while
		}
		else // instrument ID is default based on type.
		{
			deviceTypeName = getInstrumentTypeName(device);
			logger.log(INFO, 1, CLASS,"DeviceInstrumentUtilites:getInstrumentId:"+
				   "Searching for default device of type:"+deviceTypeName);
			instrumentId = tea.getPropertyString("instrument."+deviceTypeName+".id.default");
			if(instrumentId == null)
			{
				throw new NullPointerException("org.estar.tea.DeviceInstrumentUtilites:"+
							       "getInstrumentId:Default instrument id for type "+
							       deviceTypeName+" not found.");
			}
			logger.log(INFO, 1, CLASS,"DeviceInstrumentUtilites:getInstrumentId:"+
				   "Default device of type:"+deviceTypeName+" is "+instrumentId);
		}
		return instrumentId;
	}

	/**
	 * Create an instance of the class specified by the configClassName string.
	 * @param configClassName The name of the InstrumentConfig subclass to create: i.e. "dev.lt.RATCamConfig".
	 *        This could be null if the property lookup filed.
	 * @param configId The descriptive "name" of the config instance, i.e.: 
	 *    "TEA-"+instName+"-"+filterString+"-"+xbin+"x"+ybin.
	 * @return An instance of a subclass of InstrumentConfig.
	 * @exception ClassNotFoundException Thrown if Class.forName fails.
	 * @exception NoSuchMethodException Thrown if Class.getConstructor fails to find the right constuctor.
	 * @exception InstantiationException Thrown if Constructor.newInstance fails to create a new config object.
	 * @exception IllegalAccessException Thrown if Constructor.newInstance fails as the constructor is not public.
	 * @exception InvocationTargetException Thrown if Constructor.newInstance fails.
	 */
	public static InstrumentConfig createInstrumentConfig(String configClassName,String configId) throws
		ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException,
		InvocationTargetException
	{
		InstrumentConfig config = null;
		Class configClass = null;
		Constructor con = null;

		if(configClassName == null)
		{
			throw new IllegalArgumentException("org.estar.tea.DeviceInstrumentUtilites:"+
							   "createInstrumentConfig:Class name was null.");
		}
		configClass = Class.forName(configClassName);		
		con = configClass.getConstructor(new Class[]{String.class});		
		config = (InstrumentConfig)con.newInstance(new Object[]{configId});
		return config;
	}

	/**
	 * Get whether this instrument produces ReductionInfo telemetry that can be used to trigger 
	 * update documents. Some instrument produce ReductionInfo telemetry from the RCS, whereas others
	 * emit only ExposureInfo.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get instrument properties from.
	 * @param device The RTMLDevice to parse.
	 * @return A boolean. This is true if this instrument produces ReductionInfo telemetry.
	 * @exception IllegalArgumentException Thrown if the instrument is not receognised.
	 * @exception NullPointerException Thrown if the device of type attribute was null.
	 * @see #getInstrumentId
	 * @see TelescopeEmbeddedAgent#getPropertyBoolean
	 */
	public static boolean instrumentUpdateRequiresReductionTelemetry(TelescopeEmbeddedAgent tea,
					  RTMLDevice device) throws IllegalArgumentException, NullPointerException
	{
		String instrumentId = null;
		String telemetryClassName = null;

		instrumentId = getInstrumentId(tea,device);
		telemetryClassName = tea.getPropertyString("instrument."+instrumentId+".update.telemetry");
		return telemetryClassName.equals("ReductionInfo");
	}

	/**
	 * Get whether this instrument produces ExposureInfo telemetry that can be used to trigger 
	 * update documents. Some instrument produce ReductionInfo telemetry from the RCS, whereas others
	 * emit only ExposureInfo.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get instrument properties from.
	 * @param device The RTMLDevice to parse.
	 * @return A boolean. This is true if this instrument only produces ExposureInfo telemetry from the RCS.
	 * @exception IllegalArgumentException Thrown if the instrument is not receognised.
	 * @exception NullPointerException Thrown if the device of type attribute was null.
	 * @see #getInstrumentId
	 * @see TelescopeEmbeddedAgent#getPropertyBoolean
	 */
	public static boolean instrumentUpdateRequiresExposureTelemetry(TelescopeEmbeddedAgent tea,
					  RTMLDevice device) throws IllegalArgumentException, NullPointerException
	{
		String instrumentId = null;
		String telemetryClassName = null;

		instrumentId = getInstrumentId(tea,device);
		telemetryClassName = tea.getPropertyString("instrument."+instrumentId+".update.telemetry");
		return telemetryClassName.equals("ExposureInfo");
	}

	/**
	 * Get the logger instance to log to. As this class is not instantiated (it contains static class
	 * methods) we just return the logger registered against the class name:
	 * "org.estar.tea.DeviceInstrumentUtilites".
	 * @see #logger
	 */
	protected static void getLoggerInstance()
	{
		logger = LogManager.getLogger("org.estar.tea.DeviceInstrumentUtilites");
	}
}
/*
** $Log: not supported by cvs2svn $
** Revision 1.9  2013/01/11 16:03:39  cjm
** First attempt at adding frodospec support.
**
** Revision 1.8  2012/08/23 14:04:39  cjm
** Removed INSTRUMENT_TYPE_IO_O and other minor tweaks
** for IO:O support.
**
** Revision 1.7  2012/08/21 13:04:22  eng
** added support for IO_O
**
** Revision 1.6  2010/10/13 16:02:23  cjm
** Added logging to getInstrumentId.
** Fixed index double increment in getInstrumentId.
**
** Revision 1.5  2009/05/18 13:55:16  cjm
** Added extra configClassName null pointer test in getInstrumentConfig.
**
** Revision 1.4  2008/08/12 09:45:12  cjm
** Added methods to return what sort of telemetry triggers an update document for each instrument.
** Some instruments generate ReductionInfo, whilst some only generate ExposureInfo.
**
** Revision 1.3  2008/03/28 17:18:41  cjm
** Rewritten to allow multiple instrument per each instrument type.
** Instrument Type uses a combination of Device type and spectralRegion to determine type.
** Now a default instrument ID for each type, and the Device "name" can be used to override this.
** Added Merope/GenericCCD, Spectrograph (Meaburn), and RISE instrument handling.
** Default binning now read from configuration.
**
** Revision 1.2  2007/05/01 10:03:50  cjm
** Now checks for null device/device type in getInstrumentType, to throw a
** more understandable exception.
**
** Revision 1.1  2007/04/26 18:03:26  cjm
** Initial revision
**
*/
