// ProgramInfo.java
package org.estar.tea;

import java.util.Map;
import java.util.HashMap;

import ngat.phase2.IInstrumentConfig;
import ngat.phase2.IProgram;
import ngat.phase2.ITarget;

/**
 * An instance of this class is created during the TEA boot, for each Program associated with an RTML enabled proposal.
 * It is used to store two maps of target name to phase2 ITarget instance, 
 * and config name to phase2 IInstrumentConfig instance, which are per-program.
 */
public class ProgramInfo
{
	/**
	 * The phase2 IProgram instance for this program.
	 */
	private IProgram program;
	/**
	 * A Map of target name to phase2 ITarget instances.
	 */
	private Map targetMap;
	/**
	 * A Map of config name to phase2 IInstrumentConfig instances.
	 */
	private Map configMap;
	
	/**
	 * Constructor.
	 * @param program The phase2 IProgram instance that this ProgramInfo is being created for.
	 * @see #program
	 * @see #targetMap
	 * @see #configMap
	 */
	public ProgramInfo(IProgram program)
	{
		this.program = program;
		this.targetMap = new HashMap();
		this.configMap = new HashMap();
	}

	/**
	 * Get the phase2 IProgram instance.
	 * @return The phase2 IProgram instance.
	 * @see #program
	 */
	public IProgram getProgram()
	{
		return program;
	}

	/**
	 * Set the phase2 IProgram instance.
	 * @param program The phase2 IProgram instance to set.
	 * @see #program
	 */
	public void setProgram(IProgram program)
	{
		this.program = program;
	}

	/**
	 * Get the targetMap, a Map of target name to phase2 ITarget instances.
	 * @return The targetMap.
	 * @see #targetMap
	 */
	public Map getTargetMap()
	{
		return targetMap;
	}

	/**
	 * Add the specified target phase2 object to the targetMap. The targetMapis added as the target name
	 * as a key to the ITarget data instance.
	 * @param target The target phase2 object.
	 * @see #targetMap
	 */
	public void addTarget(ITarget target)
	{
		this.targetMap.put(target.getName(), target);
	}

	/**
	 * Get the configMap, A Map of config name to phase2 IInstrumentConfig instances.
	 * @return The configMap
	 * @see #configMap
	 */
	public Map getConfigMap()
	{
		return configMap;
	}

	/**
	 * Add the specified instrument config phase2 object to the configMap. 
	 * The configMap is added as the instrument config name
	 * as a key to the IInstrumentConfig data instance.
	 * @param config The IInstrumentConfig config phase2 object.
	 * @see #configMap
	 */
	public void addConfig(IInstrumentConfig config)
	{
		this.configMap.put(config.getName(),config);
	}

	/**
	 * Return a string representation of this opbject.
	 * @return A string.
	 * @see #program
	 * @see #targetMap
	 * @see #configMap
	 */
	public String toString()
	{
		return new String (this.getClass().getName()+
				   ":program:"+program.getName()+
				   ":target map has :"+targetMap.size()+" targets"+
				   ":config map has :"+configMap.size()+" configs.");
	}
}
