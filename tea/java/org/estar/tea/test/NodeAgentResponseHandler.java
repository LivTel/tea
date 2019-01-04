package org.estar.tea.test;

import org.estar.rtml.*;

/** Interface which should be implemented by classed which wish to be informed of
 * responses received from a TEA.
 */
public interface NodeAgentResponseHandler {

    /** Handle the supplied response document.*/
    public void handleResponse(RTMLDocument document);


}
