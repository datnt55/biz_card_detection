package com.mymeishi.liveedgedetection.constants;

/**
 * This class defines constants
 */

public class ScanConstants {
    public static final String SCANNED_RESULT = "scannedResult";
    public static final String IMAGE_NAME = "image";
    public static final String IMAGE_DIR = "imageDir";
    public static final int HIGHER_SAMPLING_THRESHOLD = 2200;

    public static int KSIZE_BLUR = 3;
    public static int KSIZE_CLOSE = 10;
    public static final int CANNY_THRESH_L1 = 15*3;
    public static final int CANNY_THRESH_U1 = 15;
    public static final int CANNY_THRESH_L2 = 30;
    public static final int CANNY_THRESH_U2 = 150;
    public static final int CANNY_APERTURESIZE = 3;
    public static final boolean CANNY_L2GRADIENT = false;
    public static final int HOUGH_THRESHOLD = 30;
    public static final int HOUGH_MINLINESIZE = 3;
    public static final int HOUGH_MAXLINEGAP = 0;
    public static final int TRUNC_THRESH = 150;
    public static final int CUTOFF_THRESH = 155;
}
