/**
 * 
 */
package org.estar.tea;

import java.util.Map;

import ngat.phase2.IProgram;
import ngat.phase2.IProposal;
import ngat.phase2.ITag;
import ngat.phase2.IUser;

/**
 * @author eng
 *
 */
public class ProposalInfo {

	private IProposal proposal;

	private IProgram program;

	private Map targetMap;
	private Map configMap;
	

    private double accountBalance;
	
	/**
	 * @param proposal
	 */
	public ProposalInfo(IProposal proposal) {
		this.proposal = proposal;
	}

	/**
	 * @return the proposal
	 */
	public IProposal getProposal() {
		return proposal;
	}

	/**
	 * @param proposal the proposal to set
	 */
	public void setProposal(IProposal proposal) {
		this.proposal = proposal;
	}


	/**
	 * @return the program
	 */
	public IProgram getProgram() {
		return program;
	}

	/**
	 * @param program the program to set
	 */
	public void setProgram(IProgram program) {
		this.program = program;
	}

	/**
	 * @return the targetMap
	 */
	public Map getTargetMap() {
		return targetMap;
	}

	/**
	 * @param targetMap the targetMap to set
	 */
	public void setTargetMap(Map targetMap) {
		this.targetMap = targetMap;
	}

	/**
	 * @return the configMap
	 */
	public Map getConfigMap() {
		return configMap;
	}

	/**
	 * @param configMap the configMap to set
	 */
	public void setConfigMap(Map configMap) {
		this.configMap = configMap;
	}
	
    public void setAccountBalance(double b) {
	this.accountBalance = b;
    }

    public double getAccountBalance() { return accountBalance;}

	
}
