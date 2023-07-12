// ProposalInfo.java
package org.estar.tea;

import java.util.Map;

import ngat.phase2.IProgram;
import ngat.phase2.IProposal;
import ngat.phase2.ITag;
import ngat.phase2.IUser;

/**
 * An instance of this class is created during the TEA boot, for each RTML enabled proposal. It is used to store phase2 data about the
 * proposal (IProposal) and associated program data.
 */
public class ProposalInfo
{
	/**
	 * The phase2 IProposal instance for this proposal.
	 */
	private IProposal proposal;
	/**
	 * A ProgramInfo instance containing target and config map's for the program associated with this proposal.
	 */
	private ProgramInfo programInfo;
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
	 * Get the ProgramInfo instance this proposal is attached to.
	 * @return The ProgramInfo instance.
	 * @see #programInfo
	 */
	public ProgramInfo getProgramInfo()
	{
		return programInfo;
	}

	/**
	 * Set the ProgramInfo instance this proposal is attached to.
	 * @param program The ProgramInfo to set.
	 * @see #programInfo
	 */
	public void setProgramInfo(ProgramInfo programInfo)
	{
		this.programInfo = programInfo;
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
	 * @see #programInfo
	 * @see #accountBalance
	 */
	public String toString()
	{
		return new String ("proposal:"+proposal.getName()+
				   ":account balance:"+accountBalance+
				   ":"+programInfo);
	}
}
