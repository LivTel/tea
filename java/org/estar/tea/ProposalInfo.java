// ProposalInfor.java
package org.estar.tea;

import java.util.Map;

import ngat.phase2.IProgram;
import ngat.phase2.IProposal;
import ngat.phase2.ITag;
import ngat.phase2.IUser;

/**
 * An instance of this class is created during the TEA boot, for each RTML enabled proposal. It is used to store phase2 data about the
 * proposal (IProposal/IProgram) and two maps of target name to phase2 ITarget instance, and config name to phase2 IInstrumentConfig instance.
 */
public class ProposalInfo
{
	/**
	 * The phase2 IProposal instance for this proposal.
	 */
	private IProposal proposal;
	/**
	 * The phase2 IProgram instance for this proposal.
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
	 * The amount of time left to be allocated for this proposal, based on the (potentially) two semester balances, where each balance
	 * is allocated - consumed time.
	 */
	private double accountBalance;
	
	/**
	 * Constructor.
	 * @param proposal The phase2 IProposal instance that this ProposalInfo is being created for.
	 * @see #proposal
	 */
	public ProposalInfo(IProposal proposal)
	{
		this.proposal = proposal;
	}

	/**
	 * Get the phase2 IProposal instance.
	 * @return The phase2 IProposal instance
	 * @see #proposal
	 */
	public IProposal getProposal()
	{
		return proposal;
	}

	/**
	 * Set the phase2 IProposal instance.
	 * @param proposal The phase2 IProposal instance to set.
	 * @see #proposal
	 */
	public void setProposal(IProposal proposal)
	{
		this.proposal = proposal;
	}

	/**
	 * Get the phase2 IProgram program instance this proposal is attached to.
	 * @return The phase2 IProgram instance.
	 * @see #program
	 */
	public IProgram getProgram()
	{
		return program;
	}

	/**
	 * Set the phase2 IProgram program instance this proposal is attached to.
	 * @param program The phase2 IProgram to set.
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
	 * Set the targetMap, a Map of target name to phase2 ITarget instances.
	 * @param targetMap the targetMap to set
	 * @see #targetMap
	 */
	public void setTargetMap(Map targetMap)
	{
		this.targetMap = targetMap;
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
	 * Set the configMap, A Map of config name to phase2 IInstrumentConfig instances.
	 * @param configMap The configMap to set.
	 * @see #configMap
	 */
	public void setConfigMap(Map configMap)
	{
		this.configMap = configMap;
	}

	/**
	 * Set the account balance.
	 * @param b A double, the account balance.
	 * @see #accountBalance
	 */
	public void setAccountBalance(double b)
	{
		this.accountBalance = b;
	}

	/**
	 * Get the account balance.
	 * @return A double, the account balance.
	 * @see #accountBalance
	 */
	public double getAccountBalance()
	{
		return accountBalance;
	}

	/**
	 * Return a string representation of this opbject.
	 * @return A string.
	 * @see #proposal
	 * @see #program
	 * @see #accountBalance
	 * @see #targetMap
	 * @see #configMap
	 */
	public String toString()
	{
		return new String (this.getClass().getName()+
				   ":proposal:"+proposal.getName()+
				   ":program:"+program.getName()+
				   ":account balance:"+accountBalance+
				   ":target map has :"+targetMap.size()+" targets"+
				   ":config map has :"+configMap.size()+" configs.");
	}
}
