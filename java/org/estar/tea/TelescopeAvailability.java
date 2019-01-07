package org.estar.tea;

import java.io.*;
import java.util.*;

public class TelescopeAvailability implements Serializable {
    
    private long startTime;
    private long endTime;
    private double prediction;

    public TelescopeAvailability(long startTime, long endTime, double prediction) {
	this.startTime = startTime;
	this.endTime = endTime;
	this.prediction = prediction;
    }
    
    
    public long getStartTime() { return startTime; }
    
    public long getEndTime() { return endTime; }
    
    public double getPrediction() { return prediction; }
    
    public String toString() {
	return "Availability [from "+(new Date(startTime)).toGMTString()+
	    " to "+(new Date(endTime)).toGMTString()+
	    " value = "+prediction;
    }
    
}
