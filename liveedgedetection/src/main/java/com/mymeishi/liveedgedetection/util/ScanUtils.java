package com.mymeishi.liveedgedetection.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.hardware.Camera;
import android.os.Build;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.mymeishi.liveedgedetection.constants.ScanConstants;
import com.mymeishi.liveedgedetection.view.Quadrilateral;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * This class provides utilities for camera.
 */
public class ScanUtils {
    private static final String TAG = ScanUtils.class.getSimpleName();

    public static boolean compareFloats(double left, double right) {
        double epsilon = 0.00000001;
        return Math.abs(left - right) < epsilon;
    }

    public static Camera.Size determinePictureSize(Camera camera, Camera.Size previewSize) {
        if (camera == null) return null;
        Camera.Parameters cameraParams = camera.getParameters();
        List<Camera.Size> pictureSizeList = cameraParams.getSupportedPictureSizes();
        Collections.sort(pictureSizeList, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size size1, Camera.Size size2) {
                Double h1 = Math.sqrt(size1.width * size1.width + size1.height * size1.height);
                Double h2 = Math.sqrt(size2.width * size2.width + size2.height * size2.height);
                return h2.compareTo(h1);
            }
        });
        Camera.Size retSize = null;

        // if the preview size is not supported as a picture size
        float reqRatio = ((float) previewSize.width) / previewSize.height;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        for (Camera.Size size : pictureSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
            if (ScanUtils.compareFloats(deltaRatio, 0)) {
                break;
            }
        }

        return retSize;
    }

    public static Camera.Size getOptimalPreviewSize(Camera camera, int w, int h) {
        if (camera == null) return null;
        final double targetRatio = (double) h / w;
        Camera.Parameters cameraParams = camera.getParameters();
        List<Camera.Size> previewSizeList = cameraParams.getSupportedPreviewSizes();
        Collections.sort(previewSizeList, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size size1, Camera.Size size2) {
                double ratio1 = (double) size1.width / size1.height;
                double ratio2 = (double) size2.width / size2.height;
                Double ratioDiff1 = Math.abs(ratio1 - targetRatio);
                Double ratioDiff2 = Math.abs(ratio2 - targetRatio);
                if (ScanUtils.compareFloats(ratioDiff1, ratioDiff2)) {
                    Double h1 = Math.sqrt(size1.width * size1.width + size1.height * size1.height);
                    Double h2 = Math.sqrt(size2.width * size2.width + size2.height * size2.height);
                    return h2.compareTo(h1);
                }
                return ratioDiff1.compareTo(ratioDiff2);
            }
        });

        return previewSizeList.get(0);
    }

    public static int getDisplayOrientation(Activity activity, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        DisplayMetrics dm = new DisplayMetrics();

        Camera.getCameraInfo(cameraId, info);
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int displayOrientation;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (info.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (info.orientation - degrees + 360) % 360;
        }
        return displayOrientation;
    }

    public static Camera.Size getOptimalPictureSize(Camera camera, final int width, final int height, final Camera.Size previewSize) {
        if (camera == null) return null;
        Camera.Parameters cameraParams = camera.getParameters();
        List<Camera.Size> supportedSizes = cameraParams.getSupportedPictureSizes();

        Camera.Size size = camera.new Size(width, height);

        // convert to landscape if necessary
        if (size.width < size.height) {
            int temp = size.width;
            size.width = size.height;
            size.height = temp;
        }

        Camera.Size requestedSize = camera.new Size(size.width, size.height);

        double previewAspectRatio = (double) previewSize.width / (double) previewSize.height;

        if (previewAspectRatio < 1.0) {
            // reset ratio to landscape
            previewAspectRatio = 1.0 / previewAspectRatio;
        }

        double aspectTolerance = 0.1;
        double bestDifference = Double.MAX_VALUE;

        for (int i = 0; i < supportedSizes.size(); i++) {
            Camera.Size supportedSize = supportedSizes.get(i);

            // Perfect match
            if (supportedSize.equals(requestedSize)) {
                return supportedSize;
            }

            double difference = Math.abs(previewAspectRatio - ((double) supportedSize.width / (double) supportedSize.height));

            if (difference < bestDifference - aspectTolerance) {
                // better aspectRatio found
                if ((width != 0 && height != 0) || (supportedSize.width * supportedSize.height < 2048 * 1024)) {
                    size.width = supportedSize.width;
                    size.height = supportedSize.height;
                    bestDifference = difference;
                }
            } else if (difference < bestDifference + aspectTolerance) {
                // same aspectRatio found (within tolerance)
                if (width == 0 || height == 0) {
                    // set highest supported resolution below 2 Megapixel
                    if ((size.width < supportedSize.width) && (supportedSize.width * supportedSize.height < 2048 * 1024)) {
                        size.width = supportedSize.width;
                        size.height = supportedSize.height;
                    }
                } else {
                    // check if this pictureSize closer to requested width and height
                    if (Math.abs(width * height - supportedSize.width * supportedSize.height) < Math.abs(width * height - size.width * size.height)) {
                        size.width = supportedSize.width;
                        size.height = supportedSize.height;
                    }
                }
            }
        }

        return size;
    }

    public static Camera.Size getOptimalPreviewSize(int displayOrientation, List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio = (double) h / w;
        }

        if (sizes == null) {
            return null;
        }

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        return optimalSize;
    }


    public static int configureCameraAngle(Activity activity) {
        int angle;

        Display display = activity.getWindowManager().getDefaultDisplay();
        switch (display.getRotation()) {
            case Surface.ROTATION_0: // This is display orientation
                angle = 90; // This is camera orientation
                break;
            case Surface.ROTATION_90:
                angle = 0;
                break;
            case Surface.ROTATION_180:
                angle = 270;
                break;
            case Surface.ROTATION_270:
                angle = 180;
                break;
            default:
                angle = 90;
                break;
        }

        return angle;
    }


    public static double getMaxCosine(double maxCosine, Point[] approxPoints) {
        for (int i = 2; i < 5; i++) {
            double cosine = Math.abs(angle(approxPoints[i % 4], approxPoints[i - 2], approxPoints[i - 1]));
            maxCosine = Math.max(cosine, maxCosine);
        }
        return maxCosine;
    }

    private static double angle(Point p1, Point p2, Point p0) {
        double dx1 = p1.x - p0.x;
        double dy1 = p1.y - p0.y;
        double dx2 = p2.x - p0.x;
        double dy2 = p2.y - p0.y;
        return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
    }

    private static Point[] sortPoints(Point[] src) {
        ArrayList<Point> srcPoints = new ArrayList<>(Arrays.asList(src));
        Point[] result = {null, null, null, null};

        Comparator<Point> sumComparator = new Comparator<Point>() {
            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.valueOf(lhs.y + lhs.x).compareTo(rhs.y + rhs.x);
            }
        };

        Comparator<Point> diffComparator = new Comparator<Point>() {

            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.valueOf(lhs.y - lhs.x).compareTo(rhs.y - rhs.x);
            }
        };

        // top-left corner = minimal sum
        result[0] = Collections.min(srcPoints, sumComparator);
        // bottom-right corner = maximal sum
        result[2] = Collections.max(srcPoints, sumComparator);
        // top-right corner = minimal difference
        result[1] = Collections.min(srcPoints, diffComparator);
        // bottom-left corner = maximal difference
        result[3] = Collections.max(srcPoints, diffComparator);

        return result;
    }

    private static double calcDistance(double[] line1, double[] line2) {
        double distance = 0;

        double[] first = new double[2];
        first[0] = (line2[0] + line2[2]) / 2 - (line1[0] + line1[2]) / 2;
        first[1] = (line2[1] + line2[3]) / 2 - (line1[1] + line1[3]) / 2;
        double firstLen = Math.sqrt(Math.pow(first[0], 2) + Math.pow(first[1], 2));

        double[] second = new double[2];
        second[0] = line1[2] - line1[0];
        second[1] = line1[3] - line1[1];
        double secondLen = Math.sqrt(Math.pow(second[0], 2) + Math.pow(second[1], 2));

        double dotProduct = first[0] * second[0] + first[1] * second[1];
        double cosine = Math.abs(dotProduct / (firstLen * secondLen));

        if (1 - Math.pow(cosine, 2) > 0)
            distance = firstLen * Math.sqrt(1 - Math.pow(cosine, 2));

        return distance;
    }

    private static Point crossPt(double[] a, double[] b, Mat mat) {
        Point pt = new Point();
        double a1, b1, a2, b2;

        if (a[0] == a[2]) {
            if (b[1] == b[3]) {
                pt.x = a[0];
                pt.y = b[1];
            } else {
                a2 = (b[1] - b[3]) / (b[0] - b[2]);
                b2 = b[1] - a2 * b[0];
                pt.x = a[0];
                pt.y = a[0] * a2 + b2;
            }
        } else if (a[1] == a[3]) {
            if (b[0] == b[2]) {
                pt.x = b[0];
                pt.y = a[1];
            } else {
                a2 = (b[1] - b[3]) / (b[0] - b[2]);
                b2 = b[1] - a2 * b[0];
                pt.x = (a[1] - b2) / a2;
                pt.y = a[1];
            }
        } else if (b[0] == b[2]) {
            if (a[1] == a[3]) {
                pt.x = b[0];
                pt.y = a[1];
            } else {
                a1 = (a[1] - a[3]) / (a[0] - a[2]);
                b1 = a[1] - a1 * a[0];
                pt.x = b[0];
                pt.y = b[0] * a1 + b1;
            }
        } else if (b[1] == b[3]) {
            if (a[0] == a[2]) {
                pt.x = a[0];
                pt.y = b[1];
            } else {
                a1 = (a[1] - a[3]) / (a[0] - a[2]);
                b1 = a[1] - a1 * a[0];
                pt.x = (b[1] - b1) / a1;
                pt.y = b[1];
            }
        } else {
            a1 = (a[1] - a[3]) / (a[0] - a[2]);
            b1 = a[1] - a1 * a[0];
            a2 = (b[1] - b[3]) / (b[0] - b[2]);
            b2 = b[1] - a2 * b[0];
            pt.x = (b2 - b1) / (a1 - a2);
            pt.y = (a1 * b2 - a2 * b1) / (a1 - a2);
        }
        if (pt.x < 0) pt.x = 0;
        if (pt.y < 0) pt.y = 0;
        if (pt.x > mat.cols()) pt.x = mat.cols();
        if (pt.y > mat.rows()) pt.y = mat.rows();
        return pt;
    }

    private static Mat morph_kernel = new Mat(new Size(ScanConstants.KSIZE_CLOSE, ScanConstants.KSIZE_CLOSE), CvType.CV_8UC1, new Scalar(255));


    //Method
    /*
     *  1. Split the image into 3 channels
     *  2. Blur each channel image,
     *  3. Detect edges from each channel using Canny and merge them,
     *  4. Find contours and choose the biggest one and take the minAreaRect,
     *  5. If the minAreaRect is not good, detect lines by using hough transform.
     *  6. Choose the best square.
     */

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static Point[] detectLargestQuadrilateral(Mat originMat, boolean flag) {
        Mat originalMat = originMat.clone();
        Imgproc.resize(originalMat, originalMat, new Size(), 0.5, 0.5);

        Mat imageProc = new Mat();
        Mat _imageProc = new Mat();
        List<Mat> channels = new ArrayList<Mat>();
        Core.split(originalMat, channels);

//        int canny_thresh_l = ScanConstants.CANNY_THRESH_L1;
//        int canny_thresh_u = ScanConstants.CANNY_THRESH_U1;
        int canny_thresh_u = 60;
        int canny_thresh_l = canny_thresh_u*2;

        if (flag) {
            canny_thresh_l = 30;
            canny_thresh_u = 80;
        }

        Imgproc.cvtColor(originalMat,imageProc, Imgproc.COLOR_BGR2GRAY);

        Imgproc.GaussianBlur(imageProc, imageProc, new Size(3,3),5,5);

        //Imgproc.erode(imageProc, imageProc,new Mat(), new Point(-1,-1),3,1,new Scalar(1));
        Imgproc.Canny(imageProc, imageProc, canny_thresh_u, canny_thresh_l, ScanConstants.CANNY_APERTURESIZE, ScanConstants.CANNY_L2GRADIENT);
//        for (int i = 1; i < 3; i++) {
//            _imageProc = channels.get(i).clone();
//            Imgproc.Canny(_imageProc, _imageProc, canny_thresh_u, canny_thresh_l, ScanConstants.CANNY_APERTURESIZE, ScanConstants.CANNY_L2GRADIENT);
//            Core.add(imageProc, _imageProc, imageProc);
//        }

        if (!flag) {

        }

        Imgproc.dilate(imageProc, imageProc, new Mat(), new Point(-1, -1), 3,1,new Scalar(1));

        Imgproc.resize(imageProc, imageProc, new Size(), 2, 2);

        List<Point> result =  findLargestContours1(imageProc, 10);
        if (result.isEmpty())
            return new Point[0];

        Point[] stockArr = new Point[result.size()];
        stockArr = result.toArray(stockArr);

        return sortPoints(stockArr);

//        if (null != largestContour) {
//            Quadrilateral mLargestRect = findQuadrilateral(largestContour);
//            if (mLargestRect != null)
//                return mLargestRect;
//        }
        //return null;
    }

    public static Point[] detectHoughQuadrilateral(Mat originMat, boolean flag) {
        Mat originalMat = originMat.clone();
        Imgproc.resize(originalMat, originalMat, new Size(), 0.5, 0.5);
        Mat imageProc = new Mat();
        Mat _imageProc = new Mat();
        List<Mat> channels = new ArrayList<Mat>();
        Core.split(originalMat, channels);

        int canny_thresh_l = ScanConstants.CANNY_THRESH_L1;
        int canny_thresh_u = ScanConstants.CANNY_THRESH_U1;
        if (flag) {
            canny_thresh_l = ScanConstants.CANNY_THRESH_L2;
            canny_thresh_u = ScanConstants.CANNY_THRESH_U2;
        }

//        imageProc = channels.get(0).clone();
//        Imgproc.Canny(imageProc, imageProc, canny_thresh_u, canny_thresh_l, ScanConstants.CANNY_APERTURESIZE, ScanConstants.CANNY_L2GRADIENT);
//        for (int i = 1; i < 3; i++) {
//            _imageProc = channels.get(i).clone();
//            Imgproc.Canny(_imageProc, _imageProc, canny_thresh_u, canny_thresh_l, ScanConstants.CANNY_APERTURESIZE, ScanConstants.CANNY_L2GRADIENT);
//            Core.add(imageProc, _imageProc, imageProc);
//        }
        Imgproc.cvtColor(originalMat,imageProc, Imgproc.COLOR_BGR2GRAY);

        Imgproc.GaussianBlur(imageProc, imageProc, new Size(3,3),2,2);

        Imgproc.Canny(imageProc, imageProc, canny_thresh_u, canny_thresh_l, ScanConstants.CANNY_APERTURESIZE, ScanConstants.CANNY_L2GRADIENT);

        Imgproc.dilate(imageProc, imageProc, new Mat(), new Point(-1, -1), 1);

        Mat lines = new Mat();
        Imgproc.HoughLinesP(imageProc, lines, 1, Math.PI / 280, ScanConstants.HOUGH_THRESHOLD, ScanConstants.HOUGH_MINLINESIZE, ScanConstants.HOUGH_MAXLINEGAP);

        double[] vLine1 = new double[4];
        double[] vLine2 = new double[4];
        double[] pLine1 = new double[4];
        double[] pLine2 = new double[4];

        ArrayList<Point> pointsOrdered = new ArrayList<Point>(4);
        boolean isChecked = false;
        int i = 0;

        while (!isChecked && i < lines.rows()) {
            double[] vec1 = lines.get(i, 0);
            pLine1 = lines.get(i, 0);
            pLine2 = lines.get(i, 0);
            double[] first = new double[2];
            boolean isFirst = true;
            first[0] = vec1[0] - vec1[2];
            first[1] = vec1[1] - vec1[3];
            double firstLen = Math.sqrt(Math.pow(first[0], 2) + Math.pow(first[1], 2));
            for (int j = i + 1; j < lines.rows(); j++) {
                double[] vec2 = lines.get(j, 0);
                double[] second = new double[2];
                second[0] = vec2[0] - vec2[2];
                second[1] = vec2[1] - vec2[3];
                double secondLen = Math.sqrt(Math.pow(second[0], 2) + Math.pow(second[1], 2));
                double dotProduct = first[0] * second[0] + first[1] * second[1];
                if (Math.abs(dotProduct / (firstLen * secondLen)) < 0.1) {
                    if (isFirst) {
                        vLine1 = lines.get(j, 0);
                        vLine2 = lines.get(j, 0);
                        isFirst = false;
                        continue;
                    } else {
                        if (calcDistance(vLine1, vec2) > calcDistance(vLine2, vec2)) {
                            if (calcDistance(vLine1, vec2) > calcDistance(vLine1, vec2))
                                vLine2 = vec2;
                        } else {
                            if (calcDistance(vLine2, vec2) > calcDistance(vLine2, vLine1))
                                vLine1 = vec2;
                        }
                    }
                }
                if (Math.abs(dotProduct / (firstLen * secondLen)) > 0.99) {
                    if (calcDistance(pLine1, vec2) > calcDistance(pLine2, vec2)) {
                        if (calcDistance(pLine1, vec2) > calcDistance(pLine1, pLine2))
                            pLine2 = vec2;
                    } else {
                        if (calcDistance(pLine2, vec2) > calcDistance(pLine2, pLine1))
                            pLine1 = vec2;
                    }
                }
            }

            pointsOrdered.add(new Point(crossPt(pLine1, vLine1, originalMat).x * 2, crossPt(pLine1, vLine1, originalMat).y * 2));
            pointsOrdered.add(new Point(crossPt(pLine1, vLine2, originalMat).x * 2, crossPt(pLine1, vLine2, originalMat).y * 2));
            pointsOrdered.add(new Point(crossPt(pLine2, vLine2, originalMat).x * 2, crossPt(pLine2, vLine2, originalMat).y * 2));
            pointsOrdered.add(new Point(crossPt(pLine2, vLine1, originalMat).x * 2, crossPt(pLine2, vLine1, originalMat).y * 2));

            Point w = new Point(pointsOrdered.get(1).x - pointsOrdered.get(0).x, pointsOrdered.get(1).y - pointsOrdered.get(0).y);
            Point h = new Point(pointsOrdered.get(2).x - pointsOrdered.get(1).x, pointsOrdered.get(2).y - pointsOrdered.get(1).y);
            double wLen = Math.sqrt(w.dot(w));
            double hLen = Math.sqrt(h.dot(h));
            if (wLen < hLen) {
                double temp = wLen;
                wLen = hLen;
                hLen = temp;
            }
            double imgSquare = originalMat.rows() * originalMat.cols() * 4;
            double detectedSquare = wLen * hLen;
            if (detectedSquare > imgSquare * 0.1 && wLen / hLen > 1.3 && wLen / hLen < 2) {
                isChecked = true;
                break;
            }
            i += 1;
            //Log.d("icount;" , String.valueOf(i));
            pointsOrdered = new ArrayList<Point>(4);
        }

        if (!isChecked)
            return null;

        MatOfPoint2f contour = new MatOfPoint2f();
        contour.fromList(pointsOrdered);

        Point[] points = contour.toArray();
        Point[] foundPoints = sortPoints(points);

        return foundPoints;
    }

    private static MatOfPoint hull2Points(MatOfInt hull, MatOfPoint contour) {
        List<Integer> indexes = hull.toList();
        List<Point> points = new ArrayList<>();
        List<Point> ctrList = contour.toList();
        for (Integer index : indexes) {
            points.add(ctrList.get(index));
        }
        MatOfPoint point = new MatOfPoint();
        point.fromList(points);
        return point;
    }

    // Calculate the angle pt1 pt0 pt2 of the middle point from three points
    private static double getAngle(Point pt1, Point pt2, Point pt0)
    {
        double dx1 = pt1.x - pt0.x;
        double dy1 = pt1.y - pt0.y;
        double dx2 = pt2.x - pt0.x;
        double dy2 = pt2.y - pt0.y;
        return (dx1*dx2 + dy1*dy2)/Math.sqrt((dx1*dx1 + dy1*dy1)*(dx2*dx2 + dy2*dy2) + 1e-10);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private static List<Point> findLargestContours1(Mat inputMat, int NUM_TOP_CONTOURS) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(inputMat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find quadrilateral fit of contour to convex hull
        List<MatOfPoint> squares = new ArrayList<>();
        List<MatOfPoint> hulls = new ArrayList<>();
        MatOfInt hull = new MatOfInt();
        MatOfPoint2f approx = new MatOfPoint2f();
        approx.convertTo(approx, CvType.CV_32F);

        for (MatOfPoint contour: contours) {
            // Convex hull of border
            Imgproc.convexHull(contour, hull);

            // Calculating new outline points with convex hull
            Point[] contourPoints = contour.toArray();
            int[] indices = hull.toArray();
            List<Point> newPoints = new ArrayList<>();
            for (int index : indices) {
                newPoints.add(contourPoints[index]);
            }
            MatOfPoint2f contourHull = new MatOfPoint2f();
            contourHull.fromList(newPoints);

            // Polygon fitting convex hull border (less accurate fitting at this point)
            Imgproc.approxPolyDP(contourHull, approx, Imgproc.arcLength(contourHull, true)*0.02, true);

            // A convex quadrilateral with an area greater than a certain threshold and a quadrilateral with angles close to right angles is selected
            MatOfPoint approxf1 = new MatOfPoint();
            approx.convertTo(approxf1, CvType.CV_32S);
            if (approx.rows() == 4 && Math.abs(Imgproc.contourArea(approx)) > 40000 &&
                    Imgproc.isContourConvex(approxf1)) {
                double maxCosine = 0;
                for (int j = 2; j < 5; j++) {
                    double cosine = Math.abs(getAngle(approxf1.toArray()[j%4], approxf1.toArray()[j-2], approxf1.toArray()[j-1]));
                    maxCosine = Math.max(maxCosine, cosine);
                }
                // The angle is about 72 degrees
                if (maxCosine < 0.3) {
                    MatOfPoint tmp = new MatOfPoint();
                    contourHull.convertTo(tmp, CvType.CV_32S);
                    squares.add(approxf1);
                    hulls.add(tmp);
                }
            }
        }

        // Find the largest quadrilateral of an outer rectangle
        int index = findLargestSquare(squares);
        if (index == -1)
            return  new ArrayList<>();
        MatOfPoint largest_square = squares.get(index);

        MatOfPoint contourHull = hulls.get(index);
        MatOfPoint2f tmp = new MatOfPoint2f();
        contourHull.convertTo(tmp, CvType.CV_32F);
        Imgproc.approxPolyDP(tmp, approx, 3, true);
        List<Point> newPointList = new ArrayList<>();
        double maxL = Imgproc.arcLength(approx, true) * 0.02;

        // Find the vertices whose mid-distance of the vertices obtained by high-precision fitting is less than the four vertices maxL obtained by low-precision fitting, excluding the interference of some vertices
        for (Point p : approx.toArray()) {
            if (!(getSpacePointToPoint(p, largest_square.toList().get(0)) > maxL &&
                    getSpacePointToPoint(p, largest_square.toList().get(1)) > maxL &&
                    getSpacePointToPoint(p, largest_square.toList().get(2)) > maxL &&
                    getSpacePointToPoint(p, largest_square.toList().get(3)) > maxL)) {
                newPointList.add(p);
            }
        }

        // Find the remaining vertex links with four edges larger than 2 * maxL as the four edges of a quadrilateral object
        List<double[]> lines = new ArrayList<>();
        for (int i = 0; i < newPointList.size(); i++) {
            Point p1 = newPointList.get(i);
            Point p2 = newPointList.get((i+1) % newPointList.size());
            if (getSpacePointToPoint(p1, p2) > 2 * maxL) {
                lines.add(new double[]{p1.x, p1.y, p2.x, p2.y});
            }
        }

        // Calculates the intersection of two adjacent edges of the four edges, the four vertices of the object
        List<Point> corners = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Point corner = computeIntersect(lines.get(i),lines.get((i+1) % lines.size()));
            corners.add(corner);
        }

//        String path = Environment.getExternalStorageDirectory().toString();
//        SimpleDateFormat s = new SimpleDateFormat("ddMMyyyyhhmmss");
//        File dest = new File(path, s.format(new Date())+".png");
//        Bitmap bmp = Bitmap.createBitmap(inputMat.cols(), inputMat.rows(), Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(inputMat, bmp);
//        Canvas canvas = new Canvas(bmp);
//        Paint paint = new Paint();
//        paint.setColor(Color.rgb(255, 0, 0));
//        paint.setStrokeWidth(10);
//        for (int i = 0 ; i< corners.size(); i++) {
//            canvas.drawLine((float)corners.get(i).x, (float)corners.get(i).y, (float)corners.get((i+1)%corners.size()).x, (float)corners.get((i+1)%corners.size()).y,paint);
//        }
//
//        try (FileOutputStream out = new FileOutputStream(dest)) {
//            bmp.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
//            // PNG is a lossless format, the compression factor (100) is ignored
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        return corners;
    }

    // Point-to-point distance
    public static double getSpacePointToPoint(Point p1, Point p2) {
        double a = p1.x - p2.x;
        double b = p1.y - p2.y;
        return Math.sqrt(a * a + b * b);
    }

    // The intersection of two straight lines
    private static Point computeIntersect(double[] a, double[] b) {
        if (a.length != 4 || b.length != 4)
            throw new ClassFormatError();
        double x1 = a[0], y1 = a[1], x2 = a[2], y2 = a[3], x3 = b[0], y3 = b[1], x4 = b[2], y4 = b[3];
        double d = ((x1 - x2) * (y3 - y4)) - ((y1 - y2) * (x3 - x4));
        if (d != 0) {
            Point pt = new Point();
            pt.x = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / d;
            pt.y = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / d;
            return pt;
        }
        else
            return new Point(-1, -1);
    }

    // Find the largest square contour
    private static int findLargestSquare(List<MatOfPoint> squares) {
        if (squares.size() == 0)
            return -1;
        int max_width = 0;
        int max_height = 0;
        int max_square_idx = 0;
        int currentIndex = 0;
        for (MatOfPoint square : squares) {
            Rect rectangle = Imgproc.boundingRect(square);
            if (rectangle.width >= max_width && rectangle.height >= max_height) {
                max_width = rectangle.width;
                max_height = rectangle.height;
                max_square_idx = currentIndex;
            }
            currentIndex++;
        }
        return max_square_idx;
    }

    private static List<MatOfPoint> findLargestContours(Mat inputMat, int NUM_TOP_CONTOURS) {
        Mat mHierarchy = new Mat();
        List<MatOfPoint> mContourList = new ArrayList<>();
        //finding contours - as we are sorting by area anyway, we can use RETR_LIST - faster than RETR_EXTERNAL.
        Imgproc.findContours(inputMat, mContourList, mHierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // Convert the contours to their Convex Hulls i.e. removes minor nuances in the contour
        List<MatOfPoint> mHullList = new ArrayList<>();
        MatOfInt tempHullIndices = new MatOfInt();
        for (int i = 0; i < mContourList.size(); i++) {
            Imgproc.convexHull(mContourList.get(i), tempHullIndices);
            mHullList.add(hull2Points(tempHullIndices, mContourList.get(i)));
        }
        // Release mContourList as its job is done
        for (MatOfPoint c : mContourList)
            c.release();
        tempHullIndices.release();
        mHierarchy.release();

        if (mHullList.size() != 0) {
            Collections.sort(mHullList, new Comparator<MatOfPoint>() {
                @Override
                public int compare(MatOfPoint lhs, MatOfPoint rhs) {
                    return Double.compare(Imgproc.contourArea(rhs), Imgproc.contourArea(lhs));
                }
            });
            return mHullList.subList(0, Math.min(mHullList.size(), NUM_TOP_CONTOURS));
        }
        return null;
    }

    private static Quadrilateral findQuadrilateral(List<MatOfPoint> mContourList) {
        for (MatOfPoint c : mContourList) {
            MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
            double peri = Imgproc.arcLength(c2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true);
            Point[] points = approx.toArray();
            // select biggest 4 angles polygon
            if (approx.rows() == 4) {
                Point[] foundPoints = sortPoints(points);
                return new Quadrilateral(approx, foundPoints);
            }
        }
        return null;
    }

    public static Bitmap enhanceReceipt(Bitmap image, Point topLeft, Point topRight, Point bottomLeft, Point bottomRight, boolean transform) {
        int resultWidth = (int) (topRight.x - topLeft.x);
        int bottomWidth = (int) (bottomRight.x - bottomLeft.x);
        if (bottomWidth > resultWidth)
            resultWidth = bottomWidth;

        int resultHeight = (int) (bottomLeft.y - topLeft.y);
        int bottomHeight = (int) (bottomRight.y - topRight.y);
        if (bottomHeight > resultHeight)
            resultHeight = bottomHeight;

        if (!transform){
            return Bitmap.createBitmap(image,(int)topLeft.x, (int)topLeft.y, resultWidth, resultHeight);
        }

        Mat inputMat = new Mat(image.getHeight(), image.getHeight(), CvType.CV_8UC1);
        Utils.bitmapToMat(image, inputMat);
        Mat outputMat = new Mat(resultWidth, resultHeight, CvType.CV_8UC1);

        List<Point> source = new ArrayList<>();
        source.add(topLeft);
        source.add(topRight);
        source.add(bottomLeft);
        source.add(bottomRight);
        Mat startM = Converters.vector_Point2f_to_Mat(source);

        Point ocvPOut1 = new Point(0, 0);
        Point ocvPOut2 = new Point(resultWidth, 0);
        Point ocvPOut3 = new Point(0, resultHeight);
        Point ocvPOut4 = new Point(resultWidth, resultHeight);
        List<Point> dest = new ArrayList<>();
        dest.add(ocvPOut1);
        dest.add(ocvPOut2);
        dest.add(ocvPOut3);
        dest.add(ocvPOut4);
        Mat endM = Converters.vector_Point2f_to_Mat(dest);

        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(startM, endM);

        Imgproc.warpPerspective(inputMat, outputMat, perspectiveTransform, new Size(resultWidth, resultHeight));

        Bitmap output = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outputMat, output);
        return output;
    }

    public static String[] saveToInternalMemory(Bitmap bitmap, String mFileDirectory, String
            mFileName, Context mContext, int mQuality) {

        String[] mReturnParams = new String[2];
        File mDirectory = getBaseDirectoryFromPathString(mFileDirectory, mContext);
        File mPath = new File(mDirectory, mFileName);
        try {
            FileOutputStream mFileOutputStream = new FileOutputStream(mPath);
            //Compress method used on the Bitmap object to write  image to output stream
            bitmap.compress(Bitmap.CompressFormat.JPEG, mQuality, mFileOutputStream);
            mFileOutputStream.close();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        mReturnParams[0] = mDirectory.getAbsolutePath();
        mReturnParams[1] = mFileName;
        return mReturnParams;
    }

    private static File getBaseDirectoryFromPathString(String mPath, Context mContext) {

        ContextWrapper mContextWrapper = new ContextWrapper(mContext);

        return mContextWrapper.getDir(mPath, Context.MODE_PRIVATE);
    }

    public static Bitmap decodeBitmapFromFile(String path, String imageName) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        return BitmapFactory.decodeFile(new File(path, imageName).getAbsolutePath(),
                options);
    }

    /*
     * This method converts the dp value to px
     * @param context context
     * @param dp value in dp
     * @return px value
     */
    public static int dp2px(Context context, float dp) {
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
        return Math.round(px);
    }


    public static Bitmap decodeBitmapFromByteArray(byte[] data, int reqWidth, int reqHeight) {
        // Raw height and width of image
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        // Calculate inSampleSize
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        options.inSampleSize = inSampleSize;

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    @Deprecated
    public static Bitmap loadEfficientBitmap(byte[] data, int width, int height) {
        Bitmap bmp;

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, width, height);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        return bmp;
    }

    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap resize(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > 1) {
                finalWidth = (int) ((float) maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float) maxWidth / ratioBitmap);
            }

            image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
            return image;
        } else {
            return image;
        }
    }

    public static Bitmap resizeToScreenContentSize(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    public static ArrayList<PointF> getPolygonDefaultPoints(Bitmap bitmap) {
        ArrayList<PointF> points;
        points = new ArrayList<>();
        points.add(new PointF(bitmap.getWidth() * (0.14f), (float) bitmap.getHeight() * (0.13f)));
        points.add(new PointF(bitmap.getWidth() * (0.84f), (float) bitmap.getHeight() * (0.13f)));
        points.add(new PointF(bitmap.getWidth() * (0.14f), (float) bitmap.getHeight() * (0.83f)));
        points.add(new PointF(bitmap.getWidth() * (0.84f), (float) bitmap.getHeight() * (0.83f)));
        return points;
    }

    public static boolean isScanPointsValid(Map<Integer, PointF> points) {
        return points.size() == 4;
    }
}