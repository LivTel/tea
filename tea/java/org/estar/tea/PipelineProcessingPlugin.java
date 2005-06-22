package org.estar.tea;

import org.estar.rtml.*;

import java.io.*;

/**
 * Interface for pipeline processing plugin.
 */
public interface PipelineProcessingPlugin
{
	/**
	 * Set the telescope embedded agent reference.
	 */
	public void setTea(TelescopeEmbeddedAgent tea);
	/**
	 * Set the Id "name" of this plugin. This is typically "TAG/User.Proposal" of the proposal that
	 * caused this plugin to be created, of "default" if this is <b>not</b> a proposal specific plugin.
	 * This value can be used internally by the plugin for configuration purposes.
	 */
	public void setId(String s);
	/**
	 * Intialise the plugin.
	 */
	public void initialise() throws Exception;
	/**
	 * Get the directory on the tea machine to copy the FITS image into.
	 * @return A local directory.
	 */
	public String getInputDirectory() throws Exception;
	/**
	 * Process the file.
	 * @param file The input filename on the local (tea) machine.
	 */
	public RTMLImageData processFile(File file) throws Exception;
}
