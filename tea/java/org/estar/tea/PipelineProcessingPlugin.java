package org.estar.tea;

import org.estar.rtml.*;

import java.io.*;

/** Quickly thrown together...*/
public interface PipelineProcessingPlugin {

    /** Process the file using baseDir to dump results into.*/
   public RTMLImageData processFile(File file, File baseDir) throws Exception;

}
