package org.estar.tea;

/** Represents a mapping from an obsid to an image file.*/
public class ImageInfo {

    /** Numeric ID of the observation within the group (doc) for which the image was taken.*/
    private int obsId;

    /** The image file name.*/
    private String imageFileName;

    /** Create an ImageInfo.*/
    public ImageInfo(int obsId, String imageFileName) {
	this.obsId = obsId;
	this.imageFileName = imageFileName;
    }

    /** Returns the obsid.*/
    public int getObsId() { return obsId; }

    /** Return the image file name.*/
    public String getImageFileName() { return imageFileName; }

}
