package org.estar.tea;

import org.estar.rtml.*;
import org.estar.astrometry.*;

import ngat.phase2.*;
import ngat.util.*;

import java.util.*;
import java.io.*;


public class TestCreateGroup {


    public static void main(String args[]) {
	try {
	    
	    RTMLParser parser = new RTMLParser();
	    parser.init(false);
	    
	    FileInputStream fin = new FileInputStream(args[0]);
	    
	    RTMLDocument doc = parser.parse(fin);
	    
	    System.err.println("Parsed document: "+doc);
	    
	} catch (Exception e) {
	    System.err.println("An error has occurred");
	}
    }

}
