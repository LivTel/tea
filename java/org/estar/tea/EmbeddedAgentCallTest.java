package org.estar.tea;

import org.estar.rtml.*;
import org.estar.astrometry.*;

import ngat.phase2.*;
import java.io.*;
import java.rmi.*;

public class EmbeddedAgentCallTest {

    public static void main(String args[]) {

        try {

            RTMLParser parser = new RTMLParser();
            parser.init(false);

            FileInputStream fin = new FileInputStream(args[0]);

            RTMLDocument doc = parser.parse(fin);

            System.err.println("Parsed document: "+doc);

            EmbeddedAgentRequestHandler ear = (EmbeddedAgentRequestHandler)Naming.lookup("EARequestHandler");

            System.err.println("Got the EAR: "+ear);

            ear.handleRequest(doc);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
