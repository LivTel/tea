// ProposalInfo.java
package org.estar.tea;

import java.util.HashMap;
import java.util.Iterator;
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
	 * A Map of user name to phase2 IUser instances.
	 */
	private Map userMap;

	/**
	 * The amount of time left to be allocated for this proposal, based on the (potentially) two semester balances, where each balance
	 * is allocated - consumed time.
	 */
	private double accountBalance;
	
	/**
	 * Constructor.
	 * @param proposal The phase2 IProposal instance that this ProposalInfo is being created for.
	 * @see #proposal
	 * @see #userMap
	 */
	public ProposalInfo(IProposal proposal)
	{
		this.proposal = proposal;
		this.userMap = new HashMap();
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
	 * Add the specified user phase2 object to the userMap. 
	 * The userMap key is "<tag name>/<user name>", which should match the RTML Contact User. 
	 * The IUser data instance is the map value.
	 * @param tag The tag phase2 object.
	 * @param user The user phase2 object.
	 * @see #userMap
	 */
	public void addUser(ITag tag,IUser user)
	{
		String key = new String(tag.getName()+"/"+user.getName());
		this.userMap.put(key, user);
	}

	/**
	 * Check whether the specified user has access to this proposal. This is done by seeing whether the 
	 * "<tag name>/<user name>" string (as retrieved from an RTML document) is a key in the userMap. 
	 * @param rtmlTagUsername The RTML Contact User as retrieved from an RTML document, this should be in the form:
	 *                        "<tag name>/<user name>".
	 * @return The method returns true if the user is allowed to access this proposal, 
	 *         and false if it does not have access to this proposal.
	 * @see #userMap
	 */
	public boolean userHasAccess(String rtmlTagUsername)
	{
		IUser user = (IUser) this.userMap.get(rtmlTagUsername);
		if(user != null)
			return true;
		else
			return false;
	}
	
	/**
	 * Method to list (as a String) the users that have access permissions for this proposal.
	 * @param keyOnly A boolean, if true return a string with user names (keys) only i.e. '<user>,<user>...'
	 *                If false, try and print the contents of the IUser i.e. '<username> = { IUser },\n ...'
	 * @return A string containing a representation of a list of users that have access permissions 
	 *         for this proposal.
	 * @see #userMap
	 */
	public String listUsers(boolean keyOnly)
	{
		StringBuffer sb = null;

		sb = new StringBuffer();
		Iterator ui = userMap.entrySet().iterator();
		while(ui.hasNext())
		{
			Map.Entry entry = (Map.Entry) ui.next();
			String key = (String) entry.getKey();
			IUser userValue = (IUser) entry.getValue();
			if(keyOnly)
			{
				sb.append(key);
				sb.append(",");
			}
			else
			{
				sb.append(key);
				sb.append(" = {");
				sb.append(userValue);
				sb.append("},\n");
			}
		}
		return sb.toString();
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
	 * @see #userMap
	 */
	public String toString()
	{
		return new String ("proposal:"+proposal.getName()+
				   ":account balance:"+accountBalance+
				   ":user map has :"+userMap.size()+" users"+
				   ":"+programInfo);
	}
}
