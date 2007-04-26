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
// $Header: /space/home/eng/cjm/cvs/tea/java/org/estar/tea/DeviceInstrumentUtilites.java,v 1.1 2007-04-26 18:03:26 cjm Exp $
package org.estar.tea;

import java.lang.reflect.*;

import org.estar.astrometry.*;
import org.estar.rtml.*;
import org.estar.toop.*;

import ngat.phase2.*;

/**
 * Utility routines for %lt;Device&gt; -> Instrument mapping.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class DeviceInstrumentUtilites
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: DeviceInstrumentUtilites.java,v 1.1 2007-04-26 18:03:26 cjm Exp $";
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
	 * @see #getCCDLowerFilterType
	 * @see #getCCDUpperFilterType
	 * @see #getInstrumentDetectorBinning
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
		String rtmlFilterType = null;
		String lowerFilterType = null;
		String upperFilterType = null;
		String irFilterType = null;
		String configClassName = null;
		int bin,instrumentType;

		instrumentType = getInstrumentType(device);
		switch(instrumentType)
		{
			case INSTRUMENT_TYPE_NONE:
				throw new IllegalArgumentException("getInstrumentConfig:Type NONE detected.");
			case INSTRUMENT_TYPE_CCD:
				rtmlFilterType = device.getFilterType();
				lowerFilterType = getCCDLowerFilterType(tea,rtmlFilterType);
				upperFilterType = getCCDUpperFilterType(tea,rtmlFilterType);
				// This needs to get more sophisticated if we allow non-square binning
				bin = getInstrumentDetectorBinning(instrumentType,device.getDetector());
				// create config
				configClassName = tea.getFilterMap().getProperty("filter."+INSTRUMENT_TYPE_CCD_STRING+
									".config.class");
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
				break;
			case INSTRUMENT_TYPE_IRCAM:
				rtmlFilterType = device.getFilterType();
				irFilterType = getIRCamFilterType(tea,rtmlFilterType);
				// This needs to get more sophisticated if we allow non-square binning
				bin = getInstrumentDetectorBinning(instrumentType,device.getDetector());
				// create config
				configClassName = tea.getFilterMap().getProperty("filter."+
										 INSTRUMENT_TYPE_IRCAM_STRING+
										 ".config.class");
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
				break;
			case INSTRUMENT_TYPE_POLARIMETER:
				rtmlFilterType = device.getFilterType();
				// We could check rtmlFilterType against a fixed constant/null and error
				// if it does not have the correct type.
				// This needs to get more sophisticated if we allow non-square binning
				bin = getInstrumentDetectorBinning(instrumentType,device.getDetector());
				// create config
				configClassName = tea.getFilterMap().getProperty("filter."+
										 INSTRUMENT_TYPE_POLARIMETER_STRING+
										 ".config.class");
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
				break;
			default:
				throw new IllegalArgumentException("getInstrumentConfig:Unknown Type "+instrumentType+
								   " detected.");
		}
		return config;
	}

	/**
	 * Send a suitable INSTR command to a running TOCSession.
	 * @param tea The instance of TelescopeEmbeddedAgent, used to retrieve configuration data.
	 * @param session An instance of TOCSession which has already opened a TOCA session.
	 * @param device The instance of RTML Device data to extract the INSTR parameters from.
	 * @exception IllegalArgumentException Thrown if an argument is wrong.
	 * @exception TOCException Thrown if instrRatcam/instrIRcam/instrRingoStar fails.
	 * @exception Exception Thrown if getCCDLowerFilterType/getCCDUpperFilterType/getIRCamFilterType fails.
	 * @see TelescopeEmbeddedAgent
	 * @see org.estar.rtml.RTMLDevice
	 * @see org.estar.toop.TOCSession
	 * @see org.estar.toop.TOCSession#instrRatcam
	 * @see org.estar.toop.TOCSession#instrIRcam
	 * @see org.estar.toop.TOCSession#instrRingoStar
	 * @see #getInstrumentType
	 * @see #getCCDLowerFilterType
	 * @see #getCCDUpperFilterType
	 * @see #getInstrumentDetectorBinning
	 * @see #getIRCamFilterType
	 * @see #INSTRUMENT_TYPE_CCD
	 * @see #INSTRUMENT_TYPE_IRCAM
	 * @see #INSTRUMENT_TYPE_POLARIMETER
	 */
	public static void sendInstr(TelescopeEmbeddedAgent tea,TOCSession session,RTMLDevice device) throws
		IllegalArgumentException,TOCException, Exception
	{
		String rtmlFilterType = null;
		String lowerFilterType = null;
		String upperFilterType = null;
		String irFilterType = null;
		int bin,instrumentType;

		instrumentType = getInstrumentType(device);
		switch(instrumentType)
		{
			case INSTRUMENT_TYPE_NONE:
				throw new IllegalArgumentException("sendInstr:Type NONE detected.");
			case INSTRUMENT_TYPE_CCD:
				rtmlFilterType = device.getFilterType();
				lowerFilterType = getCCDLowerFilterType(tea,rtmlFilterType);
				upperFilterType = getCCDUpperFilterType(tea,rtmlFilterType);
				// This needs to get more sophisticated if we allow non-square binning
				bin = getInstrumentDetectorBinning(instrumentType,device.getDetector());
				// actually configure instrument
				session.instrRatcam(lowerFilterType,upperFilterType,bin,false,false);
				break;
			case INSTRUMENT_TYPE_IRCAM:
				rtmlFilterType = device.getFilterType();
				irFilterType = getIRCamFilterType(tea,rtmlFilterType);
				// This needs to get more sophisticated if we allow non-square binning
				bin = getInstrumentDetectorBinning(instrumentType,device.getDetector());
				session.instrIRcam(irFilterType,bin,false,false);
				break;
			case INSTRUMENT_TYPE_POLARIMETER:
				rtmlFilterType = device.getFilterType();
				// We could check rtmlFilterType against a fixed constant/null and error
				// if it does not have the correct type.
				// This needs to get more sophisticated if we allow non-square binning
				bin = getInstrumentDetectorBinning(instrumentType,device.getDetector());
				session.instrRingoStar(bin,bin,false,false);
				break;
			default:
				throw new IllegalArgumentException("sendInstr:Unknown Type "+instrumentType+
								   " detected.");
		}
	}

	/**
	 * Get the lower filter type of a CCD instrument, from the TEA's filter map.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the filter map from.
	 * @param rtmlFilterType A string respresenting a CCD filter type, e.g. 'R'.
	 * @return A String containing the CCDConfig filter type of the filter in the lower wheel for this config
	 *         e.g. 'SDSS-R'.
	 * @exception Exception Thrown if the filter mapping is not found in the filter map.
	 * @see TelescopeEmbeddedAgent
	 * @see TelescopeEmbeddedAgent#getFilterMap
	 * @see #INSTRUMENT_TYPE_CCD_STRING
	 */
	public static String getCCDLowerFilterType(TelescopeEmbeddedAgent tea,String rtmlFilterType) 
		throws Exception
	{
		return tea.getFilterMap().getProperty("filter."+INSTRUMENT_TYPE_CCD_STRING+".lower."+rtmlFilterType);
	}

	/**
	 * Get the upper filter type of a CCD instrument, from the TEA's filter map.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the filter map from.
	 * @param rtmlFilterType A string respresenting a CCD filter type, e.g. 'R'.
	 * @return A String containing the CCDConfig filter type of the filter in the upper wheel for this config
	 *         e.g. 'clear'.
	 * @exception Exception Thrown if the filter mapping is not found in the filter map.
	 * @see TelescopeEmbeddedAgent
	 * @see TelescopeEmbeddedAgent#getFilterMap
	 * @see #INSTRUMENT_TYPE_CCD_STRING
	 */
	public static String getCCDUpperFilterType(TelescopeEmbeddedAgent tea,String rtmlFilterType) 
		throws Exception
	{
		return tea.getFilterMap().getProperty("filter."+INSTRUMENT_TYPE_CCD_STRING+".upper."+rtmlFilterType);
	}

	/**
	 * Get the filter type of a IRCAM instrument, from the TEA's filter map.
	 * @param tea An instance of the TelescopeEmbeddedAgent, to get the filter map from.
	 * @param rtmlFilterType A string respresenting an IRCAM filter type, e.g. 'J'.
	 * @return A String containing the IRCamConfig filter type of the filter in the wheel for this config
	 *         e.g. 'Barr-J'.
	 * @exception Exception Thrown if the filter mapping is not found in the filter map.
	 * @see TelescopeEmbeddedAgent
	 * @see TelescopeEmbeddedAgent#getFilterMap
	 * @see #INSTRUMENT_TYPE_IRCAM_STRING
	 */
	public static String getIRCamFilterType(TelescopeEmbeddedAgent tea,String rtmlFilterType) 
		throws Exception
	{
		return tea.getFilterMap().getProperty("filter."+INSTRUMENT_TYPE_IRCAM_STRING+"."+rtmlFilterType);
	}

	/**
	 * Get a binning value for the specified Detector. Note this method currently assumes square binning
	 * for all available instruments, and will have to change when a non-square binned instrument 
	 * becomes available.
	 * @param instrumentType Which sort of instrument we are getting the binning for.
	 * @param detector The RTML detector instance. This is allowed to be null if no
	 *        &lt;Detector&gt; tag has been specified in the RTML.
	 * @see #INSTRUMENT_TYPE_CCD
	 * @see #INSTRUMENT_TYPE_IRCAM
	 * @see #INSTRUMENT_TYPE_POLARIMETER
	 */
	public static int getInstrumentDetectorBinning(int instrumentType,RTMLDetector detector)
	{
		int bin;

		if(detector != null)
		{
			// binning
			if(detector.getColumnBinning() != detector.getRowBinning())
			{
				throw new IllegalArgumentException("getInstrumentDetectorBinning:"+
								   "Row/Column binning must be equal: row: "+
								   detector.getRowBinning()+
								   " and column: "+detector.getColumnBinning()+".");
			}
			bin = detector.getColumnBinning();
			if((instrumentType == INSTRUMENT_TYPE_IRCAM)&&(bin != 1))
			{
				throw new IllegalArgumentException("getInstrumentDetectorBinning:SupIRCam (IRCAM) "+
								   "Row/Column binning must be 1.");
			}
		}
		else 
		{
			// we allow device with no Detector, so set a default bin
			// For RATCam, this should be 2
			// For RINGO, this should be 2
			// For IRCAM (SupIRCam) this _must_ be 1
			if(instrumentType == INSTRUMENT_TYPE_IRCAM)
				bin = 1;
			else
				bin = 2;
		}
		return bin;
	}

	/**
	 * Get the name of the type of instrument the specifed device represents.
	 * @param device The RTMLDevice to parse.
	 * @return A string respresenting the type of instrument, one of: "ccd","ircam", "polarimeter".
	 * @exception IllegalArgumentException Thrown if the instrument type is not receognised.
	 * @see #INSTRUMENT_TYPE_NONE
	 * @see #INSTRUMENT_TYPE_CCD
	 * @see #INSTRUMENT_TYPE_IRCAM
	 * @see #INSTRUMENT_TYPE_POLARIMETER
	 * @see #INSTRUMENT_TYPE_CCD_STRING
	 * @see #INSTRUMENT_TYPE_IRCAM_STRING
	 * @see #INSTRUMENT_TYPE_POLARIMETER_STRING
	 */
	public static String getInstrumentTypeName(RTMLDevice device) throws IllegalArgumentException
	{
		int instrumentType;

		instrumentType = getInstrumentType(device);
		switch(instrumentType)
		{
			case INSTRUMENT_TYPE_NONE:
				throw new IllegalArgumentException("getInstrumentName:Type NONE detected.");
			case INSTRUMENT_TYPE_CCD:
				return INSTRUMENT_TYPE_CCD_STRING;
			case INSTRUMENT_TYPE_IRCAM:
				return INSTRUMENT_TYPE_IRCAM_STRING;
			case INSTRUMENT_TYPE_POLARIMETER:
				return INSTRUMENT_TYPE_POLARIMETER_STRING;
			default:
				throw new IllegalArgumentException("getInstrumentName:Unknown Type "+instrumentType+
								   " detected.");
		}
	}

	/**
	 * Get what type of instrument the specifed device represents.
	 * @param device The RTMLDevice to parse.
	 * @return An integer respresenting the type of instrument.
	 * @exception IllegalArgumentException Thrown if the instrument type is not receognised.
	 * @see #INSTRUMENT_TYPE_CCD
	 * @see #INSTRUMENT_TYPE_IRCAM
	 * @see #INSTRUMENT_TYPE_POLARIMETER
	 */
	public static int getInstrumentType(RTMLDevice device) throws IllegalArgumentException
	{
		String deviceType = null;
		String spectralRegion = null;
		int instrumentType;

		deviceType = device.getType();
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
				throw new IllegalArgumentException("getInstrumentType: camera spectral region: "+
								   spectralRegion+
								   " does not map to a known instrument type.");
			}
		}
		else if(deviceType.equals("polarimeter"))
		{
			instrumentType = INSTRUMENT_TYPE_POLARIMETER;
		}
		else
		{
			throw new IllegalArgumentException("getInstrumentType:Device "+
							   device.getType()+" not supported for TOCA.");
		}
		return instrumentType;
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
			throw new IllegalArgumentException("createInstrumentConfig:Class name was null.");
		}
		configClass = Class.forName(configClassName);		
		con = configClass.getConstructor(new Class[]{String.class});		
		config = (InstrumentConfig)con.newInstance(new Object[]{configId});
		return config;
	}
}
/*
** $Log: not supported by cvs2svn $
*/
