package org.estar.tea.test;

import org.estar.tea.*;

import java.rmi.*;

/** Test the SystemTest mechanism.*/
public class SystemTestTest {

    /** Run a test.*/
    public static void main(String args[]) {

	try {

	    EmbeddedAgentTestHarness th = (EmbeddedAgentTestHarness)Naming.lookup("rmi://localhost/EARequestHandler");

	    th.testThroughput();

	    System.err.println("Test seems to have worked...");

	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

}
