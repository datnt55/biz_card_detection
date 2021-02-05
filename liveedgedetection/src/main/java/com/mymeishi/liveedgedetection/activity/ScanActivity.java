package com.mymeishi.liveedgedetection.activity;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.transition.TransitionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.mymeishi.liveedgedetection.R;
import com.mymeishi.liveedgedetection.constants.ScanConstants;
import com.mymeishi.liveedgedetection.interfaces.IScanner;
import com.mymeishi.liveedgedetection.util.ScanUtils;
import com.mymeishi.liveedgedetection.view.Quadrilateral;
import com.mymeishi.liveedgedetection.view.PolygonPoints;
import com.mymeishi.liveedgedetection.view.PolygonView;
import com.mymeishi.liveedgedetection.view.ScanSurfaceView;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import timber.log.Timber;

import static android.view.View.GONE;

/**
 * This class initiates camera and detects edges on live view
 */
public class ScanActivity extends AppCompatActivity implements IScanner, View.OnClickListener {
    private static final String TAG = ScanActivity.class.getSimpleName();

    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 101;

    private ViewGroup containerScan;
    private FrameLayout cameraPreviewLayout;
    private ScanSurfaceView mImageSurfaceView;
    private boolean isPermissionNotGranted;
    private static final String mOpenCvLibrary = "opencv_java3";

    public final static Stack<PolygonPoints> allDraggedPointsStack = new Stack<>();
    private PolygonView polygonView;
    private ImageView cropImageView;
    private Bitmap copyBitmap;
    private FrameLayout cropLayout;
    private ImageView captureBtn;
    private Button backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        init();
    }

    private void init() {
        containerScan = findViewById(R.id.container_scan);
        cameraPreviewLayout = findViewById(R.id.camera_preview);
        polygonView = findViewById(R.id.polygon_view);
        cropImageView = findViewById(R.id.crop_image_view);
        View cropAcceptBtn = findViewById(R.id.crop_accept_btn);
        View cropRejectBtn = findViewById(R.id.crop_reject_btn);
        cropLayout = findViewById(R.id.crop_layout);
        captureBtn = findViewById(R.id.btn_capture);
        backBtn = findViewById(R.id.back_btn);

        cropAcceptBtn.setOnClickListener(this);
        cropRejectBtn.setOnClickListener(this);
        captureBtn.setOnClickListener(this);
        backBtn.setOnClickListener(this);

        checkCameraPermissions();
    }

    private void checkCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            isPermissionNotGranted = true;
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);
            }
        } else {
            if (!isPermissionNotGranted) {
                mImageSurfaceView = new ScanSurfaceView(ScanActivity.this, this);
                cameraPreviewLayout.addView(mImageSurfaceView);
            } else {
                isPermissionNotGranted = false;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA:
                onRequestCamera(grantResults);
                break;
            default:
                break;
        }
    }

    private void onRequestCamera(int[] grantResults) {
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isPermissionNotGranted = false;
                            mImageSurfaceView = new ScanSurfaceView(ScanActivity.this, ScanActivity.this);
                            cameraPreviewLayout.addView(mImageSurfaceView);
                        }
                    });
                }
            }, 500);

        } else {
            Toast.makeText(this, getString(R.string.camera_activity_permission_denied_toast), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onPictureClicked(final Bitmap bitmap) {
        try {
            copyBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            int height = getWindow().findViewById(Window.ID_ANDROID_CONTENT).getHeight();
            int width = getWindow().findViewById(Window.ID_ANDROID_CONTENT).getWidth();

            copyBitmap = ScanUtils.resizeToScreenContentSize(copyBitmap, width, height);
            Mat originalMat = new Mat(copyBitmap.getHeight(), copyBitmap.getWidth(), CvType.CV_8UC1);
            Utils.bitmapToMat(copyBitmap, originalMat);
            ArrayList<PointF> points;
            Map<Integer, PointF> pointFs = new HashMap<>();
            Point[] Qpoints;
            try {
                Qpoints = ScanUtils.detectLargestQuadrilateral(originalMat, false);
                if (Qpoints.length == 0)
                    Qpoints = ScanUtils.detectLargestQuadrilateral(originalMat, true);
                if (Qpoints.length > 0) {
                    points = new ArrayList<>();
                    points.add(new PointF((float) Qpoints[0].x, (float) Qpoints[0].y));
                    points.add(new PointF((float) Qpoints[1].x, (float) Qpoints[1].y));
                    points.add(new PointF((float) Qpoints[3].x, (float) Qpoints[3].y));
                    points.add(new PointF((float) Qpoints[2].x, (float) Qpoints[2].y));
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Notice")
                            .setMessage("We don't recognize any card. Please try again")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    mImageSurfaceView.setPreviewCallback();
                                    dialog.dismiss();
                                }
                            })

                            // A null listener allows the button to dismiss the dialog and take no further action.
                            .setNegativeButton(android.R.string.no, null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                    points = ScanUtils.getPolygonDefaultPoints(copyBitmap);

                    return;
                }
//                else {
//                    quad = ScanUtils.detectHoughQuadrilateral(originalMat, true);
//                    if (null != quad) {
//                        double resultArea = Math.abs(Imgproc.contourArea(quad.contour));
//                        double previewArea = originalMat.rows() * originalMat.cols();
//                        if (resultArea > previewArea * 0.08) {
//                            points = new ArrayList<>();
//                            points.add(new PointF((float) quad.points[0].x, (float) quad.points[0].y));
//                            points.add(new PointF((float) quad.points[1].x, (float) quad.points[1].y));
//                            points.add(new PointF((float) quad.points[3].x, (float) quad.points[3].y));
//                            points.add(new PointF((float) quad.points[2].x, (float) quad.points[2 ].y));
//                        }
//                        else {
//                            quad = ScanUtils.detectHoughQuadrilateral(originalMat, false);
//                            if (null != quad) {
//                                points = new ArrayList<>();
//                                points.add(new PointF((float) quad.points[0].x, (float) quad.points[0].y));
//                                points.add(new PointF((float) quad.points[1].x, (float) quad.points[1].y));
//                                points.add(new PointF((float) quad.points[3].x, (float) quad.points[3].y));
//                                points.add(new PointF((float) quad.points[2].x, (float) quad.points[2].y));
//                            }
//                            else {
//                                quad = ScanUtils.detectHoughQuadrilateral(originalMat, true);
//                                if (null != quad) {
//                                    points = new ArrayList<>();
//                                    points.add(new PointF((float) quad.points[0].x, (float) quad.points[0].y));
//                                    points.add(new PointF((float) quad.points[1].x, (float) quad.points[1].y));
//                                    points.add(new PointF((float) quad.points[3].x, (float) quad.points[3].y));
//                                    points.add(new PointF((float) quad.points[2].x, (float) quad.points[2].y));
//                                }
//                            }
//                        }
//
//                    }

                int index = -1;
                for (PointF pointF : points) {
                    pointFs.put(++index, pointF);
                }

                polygonView.setPoints(pointFs);
                int padding = (int) getResources().getDimension(R.dimen.scan_padding);
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(copyBitmap.getWidth() + 2 * padding, copyBitmap.getHeight() + 2 * padding);
                layoutParams.gravity = Gravity.CENTER;
                polygonView.setLayoutParams(layoutParams);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                    TransitionManager.beginDelayedTransition(containerScan);
                cropLayout.setVisibility(View.VISIBLE);
                captureBtn.setVisibility(GONE);
                backBtn.setVisibility(GONE);

                cropImageView.setImageBitmap(copyBitmap);
                cropImageView.setScaleType(ImageView.ScaleType.FIT_XY);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onPreviewCropped(Bitmap bitmap) {
        ImageView meishiPreview = findViewById(R.id.meishi_preview);
        meishiPreview.setImageBitmap(bitmap);
    }

    static {
        System.loadLibrary(mOpenCvLibrary);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.crop_accept_btn) {
            Map<Integer, PointF> points = polygonView.getPoints();

            Bitmap croppedBitmap;

            if (ScanUtils.isScanPointsValid(points)) {
                Point point1 = new Point(points.get(0).x, points.get(0).y);
                Point point2 = new Point(points.get(1).x, points.get(1).y);
                Point point3 = new Point(points.get(2).x, points.get(2).y);
                Point point4 = new Point(points.get(3).x, points.get(3).y);
                croppedBitmap = ScanUtils.enhanceReceipt(copyBitmap, point1, point2, point3, point4, true);
            } else {
                croppedBitmap = copyBitmap;
            }

            String path = ScanUtils.saveToInternalMemory(croppedBitmap, ScanConstants.IMAGE_DIR,
                    ScanConstants.IMAGE_NAME, ScanActivity.this, 90)[0];
            setResult(Activity.RESULT_OK, new Intent().putExtra(ScanConstants.SCANNED_RESULT, path));

            System.gc();
            finish();
        } else if (id == R.id.crop_reject_btn) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                TransitionManager.beginDelayedTransition(containerScan);
            cropLayout.setVisibility(GONE);
            captureBtn.setVisibility(View.VISIBLE);
            backBtn.setVisibility(View.VISIBLE);
            mImageSurfaceView.setPreviewCallback();
        } else if (id == R.id.btn_capture) {
            Log.d("ScanActivity", "" + isPermissionNotGranted);
            if (!isPermissionNotGranted)
                mImageSurfaceView.manualCapture();
        } else if (id == R.id.back_btn) {
            onBackPressed();
        }
    }
}
