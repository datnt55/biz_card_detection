@file:Suppress("DEPRECATION")

package com.mymeishi.liveedgedetection.view

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.shapes.PathShape
import android.hardware.Camera
import android.hardware.Camera.*
import android.media.AudioManager
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import com.mymeishi.liveedgedetection.constants.ScanConstants
import com.mymeishi.liveedgedetection.enums.ScanHint
import com.mymeishi.liveedgedetection.interfaces.IScanner
import com.mymeishi.liveedgedetection.util.ScanUtils
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


/**
 * This class previews the live images from the camera
 */
@Suppress("DEPRECATION")
class
ScanSurfaceView(context: Context?, iScanner: IScanner) : FrameLayout(context!!), SurfaceHolder.Callback {

    companion object {
        private val TAG: String = "ScanSurfaceView"
    }

    var mSurfaceView: SurfaceView = SurfaceView(context)
    private val scanCanvasView: ScanCanvasView
    private var vWidth = 0
    private var vHeight = 0
    private var mContext: Context? = null
    private var camera: Camera? = null
    private val iScanner: IScanner
    private var isAutoCaptureScheduled = false
    private var previewSize: Camera.Size? = null
    private var isCapturing = false
    var scanHint: ScanHint = ScanHint.NO_MESSAGE
    private val proxySchedule: Scheduler
    private val executor: ExecutorService
    private var busy: Boolean = false
    private var angle: Int = 0
    private var curentRectange : ArrayList<Point> = arrayListOf()

    init {
        addView(mSurfaceView)
        this.mContext = context
        scanCanvasView = ScanCanvasView(context)
        addView(scanCanvasView)
        val surfaceHolder = mSurfaceView.holder
        surfaceHolder.addCallback(this)
        this.iScanner = iScanner
        executor = Executors.newSingleThreadExecutor()
        proxySchedule = Schedulers.from(executor)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            requestLayout()
            openCamera()
            camera?.setPreviewDisplay(holder)

        } catch (e: IOException) {
            Log.e(TAG, e.message, e)
        }
    }

    private fun setCameraDisplayOrientation() {
        val info = CameraInfo()
        getCameraInfo(0, info)
        val rotation = (context as Activity).windowManager.defaultDisplay
            .rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            angle = (info.orientation + degrees) % 360
            angle = (360 - angle) % 360 // compensate the mirror
        } else {  // back-facing
            angle = (info.orientation - degrees + 360) % 360
        }
        camera?.setDisplayOrientation(angle)
    }

    private fun clearAndInvalidateCanvas() {
        scanCanvasView.clear()
        scanCanvasView.invalidate()
    }

    private fun invalidateCanvas() {
        scanCanvasView.invalidate()
    }

    private fun openCamera() {
        if (camera == null) {
            val info = CameraInfo()
            var defaultCameraId = 0

            for (i in 0..getNumberOfCameras()-1) {
                getCameraInfo(i, info)
                if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                    defaultCameraId = i
                }
            }
            camera = open(defaultCameraId)
            val cameraParams = camera?.parameters
            val flashModes = cameraParams?.supportedFlashModes
            if (null != flashModes && flashModes.contains(Parameters.FLASH_MODE_AUTO)) {
                cameraParams.flashMode = Parameters.FLASH_MODE_AUTO
            }
            camera?.parameters = cameraParams
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (vWidth == vHeight) {
            return
        }
        if (previewSize == null) previewSize = ScanUtils.getOptimalPreviewSize(camera, vWidth, vHeight)
        val parameters = camera!!.parameters
        setCameraDisplayOrientation()
        parameters.setPreviewSize(previewSize!!.width, previewSize!!.height)
        if (parameters.supportedFocusModes != null
            && parameters.supportedFocusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.focusMode = Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
        } else if (parameters.supportedFocusModes != null
            && parameters.supportedFocusModes.contains(Parameters.FOCUS_MODE_AUTO)) {
            parameters.focusMode = Parameters.FOCUS_MODE_AUTO
        }
        val size = ScanUtils.determinePictureSize(camera, parameters.previewSize)
        parameters.setPictureSize(size.width, size.height)
        parameters.pictureFormat = ImageFormat.JPEG
        camera!!.parameters = parameters
        requestLayout()
        setPreviewCallback()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopPreviewAndFreeCamera()
    }

    private fun stopPreviewAndFreeCamera() {
        if (camera != null) { // Call stopPreview() to stop updating the preview surface.
            camera!!.stopPreview()
            camera!!.setPreviewCallback(null)
            // Important: Call release() to release the camera for use by other
            // applications. Applications should release the camera immediately
            // during onPause() and re-open() it during onResume()).
            camera!!.release()
            camera = null
        }
    }

    fun setPreviewCallback() {
        camera!!.startPreview()
        camera!!.setPreviewCallback(previewCallback)
    }

    private fun drawLargestRect(points: List<Point>, stdSize: Size, previewArea: Int) {
        if (points.isEmpty())
            return
        if (curentRectange.isNotEmpty()){
            val distanceA = ScanUtils.getSpacePointToPoint(curentRectange[0], points[0])
            val distanceB = ScanUtils.getSpacePointToPoint(curentRectange[1], points[1])
            val distanceC = ScanUtils.getSpacePointToPoint(curentRectange[2], points[2])
            val distanceD = ScanUtils.getSpacePointToPoint(curentRectange[3], points[3])

            if (distanceA < ScanUtils.dp2px(context, 5f)
                && distanceB < ScanUtils.dp2px(context, 5f)
                && distanceC < ScanUtils.dp2px(context, 5f)
                && distanceD < ScanUtils.dp2px(context, 5f) )
                return
        }
        if (points.isNotEmpty()){
            curentRectange.clear()
            curentRectange.addAll(points)
        }
        val path = Path()
        // ATTENTION: axis are swapped
        val previewWidth = stdSize.height.toFloat()
        val previewHeight = stdSize.width.toFloat()

        path.moveTo(previewWidth - points[0].y.toFloat(), points[0].x.toFloat())
        for (i in points.indices) {
            path.lineTo(previewWidth - points[i].y.toFloat(), points[i].x.toFloat())
        }

        //Points are drawn in anticlockwise direction
//
//        path.lineTo(previewWidth - points[1].y.toFloat(), points[1].x.toFloat())
//        path.lineTo(previewWidth - points[2].y.toFloat(), points[2].x.toFloat())
//        path.lineTo(previewWidth - points[3].y.toFloat(), points[3].x.toFloat())
        path.close()

        //val area = Math.abs(Imgproc.contourArea(approx))

        val newBox = PathShape(path, previewWidth, previewHeight)
        val paint = Paint()
        val border = Paint()
        //Height calculated on Y axis
//        var resultHeight = points[1].x - points[0].x
//        val bottomHeight = points[2].x - points[3].x
//        if (bottomHeight > resultHeight) resultHeight = bottomHeight
//        //Width calculated on X axis
//        var resultWidth = points[3].y - points[0].y
//        val bottomWidth = points[2].y - points[1].y
//        if (bottomWidth > resultWidth) resultWidth = bottomWidth

        //Log.i(TAG, "resultWidth: $resultWidth")
        //Log.i(TAG, "resultHeight: $resultHeight")

//        val imgDetectionPropsObj = ImageDetectionProperties(
//                previewWidth.toDouble(), previewHeight.toDouble(), resultWidth, resultHeight,
//                previewArea.toDouble(), area, points[0], points[1], points[2], points[3]
//        )
//
//        if (imgDetectionPropsObj.isDetectedAreaBeyondLimits()) {
//            scanHint = ScanHint.FIND_RECT
//            cancelAutoCapture()
//        } else if (imgDetectionPropsObj.isDetectedAreaBelowLimits()) {
//            cancelAutoCapture()
//            scanHint = if (imgDetectionPropsObj.isEdgeTouching()) {
//                ScanHint.MOVE_AWAY
//            } else {
//                ScanHint.MOVE_CLOSER
//            }
//        } else if (imgDetectionPropsObj.isDetectedHeightAboveLimit()) {
//            cancelAutoCapture()
//            scanHint = ScanHint.MOVE_AWAY
//        } else if (imgDetectionPropsObj.isDetectedWidthAboveLimit() || imgDetectionPropsObj.isDetectedAreaAboveLimit()) {
//            cancelAutoCapture()
//            scanHint = ScanHint.MOVE_AWAY
//        } else {
//            if (imgDetectionPropsObj.isEdgeTouching()) {
//                cancelAutoCapture()
//                scanHint = ScanHint.MOVE_AWAY
//            } else if (imgDetectionPropsObj.isAngleNotCorrect(approx)) {
//                cancelAutoCapture()
//                scanHint = ScanHint.ADJUST_ANGLE
//            } else {
//                scanHint = ScanHint.CAPTURING_IMAGE
                clearAndInvalidateCanvas()
//            }
//        }

        scanHint =  ScanHint.MOVE_CLOSER
        border.strokeWidth = 12f
        setPaintAndBorder(ScanHint.MOVE_CLOSER, paint, border)
        scanCanvasView.clear()
        scanCanvasView.addShape(newBox, paint, border)
        invalidateCanvas()

    }

    fun manualCapture() {
        autoCapture(scanHint)
    }

    private fun autoCapture(scanHint: ScanHint) {
        if (isCapturing) return

        try {
            isCapturing = true
            camera!!.takePicture(mShutterCallBack, null, pictureCallback)
            camera!!.setPreviewCallback(null)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelAutoCapture() {
        if (isAutoCaptureScheduled) {
            isAutoCaptureScheduled = false
        }
    }

    private fun setPaintAndBorder(scanHint: ScanHint, paint: Paint, border: Paint) {
        var paintColor = 0
        var borderColor = 0

        when (scanHint) {
            ScanHint.MOVE_CLOSER, ScanHint.MOVE_AWAY, ScanHint.ADJUST_ANGLE -> {
                paintColor = Color.argb(30, 255, 38, 0)
                borderColor = Color.rgb(255, 38, 0)
            }
            ScanHint.FIND_RECT -> {
                paintColor = Color.argb(0, 0, 0, 0)
                borderColor = Color.argb(0, 0, 0, 0)
            }
            ScanHint.CAPTURING_IMAGE -> {
                paintColor = Color.argb(30, 38, 216, 76)
                borderColor = Color.rgb(38, 216, 76)
            }
        }

        paint.color = paintColor
        border.color = borderColor
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { // We purposely disregard child measurements because act as a
// wrapper to a SurfaceView that centers the camera preview instead
// of stretching it.
        vWidth = View.resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        vHeight = View.resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(vWidth, vHeight)
        previewSize = ScanUtils.getOptimalPreviewSize(camera, vWidth, vHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childCount > 0) {
            val width = r - l
            val height = b - t
            var previewWidth = width
            var previewHeight = height
            if (previewSize != null) {
                previewWidth = previewSize!!.width
                previewHeight = previewSize!!.height
                val displayOrientation = ScanUtils.configureCameraAngle(context as Activity?)
                if (displayOrientation == 90 || displayOrientation == 270) {
                    previewWidth = previewSize!!.height
                    previewHeight = previewSize!!.width
                }
            }
            val nW: Int
            val nH: Int
            val top: Int
            val left: Int
            val scale = 1.0f
            // Center the child SurfaceView within the parent.
            if (width * previewHeight < height * previewWidth) {
                val scaledChildWidth = (previewWidth * height / previewHeight * scale).toInt()
                nW = (width + scaledChildWidth) / 2
                nH = (height * scale).toInt()
                top = 0
                left = (width - scaledChildWidth) / 2
            } else {
                val scaledChildHeight = (previewHeight * width / previewWidth * scale).toInt()
                nW = (width * scale).toInt()
                nH = (height + scaledChildHeight) / 2
                top = (height - scaledChildHeight) / 2
                left = 0
            }
            mSurfaceView.layout(left, top, nW, nH)
            scanCanvasView.layout(left, top, nW, nH)
        }
    }

    private fun updatePreview(bitmap: Bitmap?) {
        if (scanHint != ScanHint.NO_MESSAGE && bitmap != null) {
            iScanner.onPreviewCropped(bitmap)
        }
    }

    private fun getPreviewImage(yuv: Mat, pointsOriginal: List<Point>): Bitmap? {

        val pointFs = HashMap<Int, PointF>()
        val points: ArrayList<PointF>
        try {
            val mRgba = Mat()
            Imgproc.cvtColor(yuv, mRgba, Imgproc.COLOR_YUV2BGRA_NV12, 4)
            val bmp = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mRgba, bmp)


//        String path = Environment.getExternalStorageDirectory().toString();
//        SimpleDateFormat s = new SimpleDateFormat("ddMMyyyyhhmmss");
//        File dest = new File(path, s.format(new Date())+".png");
//        Bitmap bmp = Bitmap.createBitmap(inputMat.cols(), inputMat.rows(), Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(inputMat, bmp);
//            val canvas = Canvas(bmp)
//            val paint = Paint()
//            paint.textSize = 40f
//            paint.color = Color.rgb(255, 0, 0)
//            paint.strokeWidth = 10f
//            for (i in pointsOriginal.indices) {
//                canvas.drawText(
//                    (i).toString(),
//                    pointsOriginal[i].x.toFloat(),
//                    pointsOriginal[i].y.toFloat(),
//                    paint
//                )
//            }
//
//            val path = Environment.getExternalStorageDirectory().toString()
//            val s = SimpleDateFormat("ddMMyyyyhhmmss")
//            val dest = File(path, s.format(Date()) + ".png")
//            try {
//                FileOutputStream(dest).use { out ->
//                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out) // bmp is your Bitmap instance
//                }
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }

            if (bmp.width == 0 || bmp.height == 0)
                return null

            if (pointsOriginal.isEmpty())
                return null

            points = ArrayList()
            points.add(PointF(pointsOriginal[0].x.toFloat(), pointsOriginal[0].y.toFloat()))
            points.add(PointF(pointsOriginal[1].x.toFloat(), pointsOriginal[1].y.toFloat()))
            points.add(PointF(pointsOriginal[3].x.toFloat(), pointsOriginal[3].y.toFloat()))
            points.add(PointF(pointsOriginal[2].x.toFloat(), pointsOriginal[2].y.toFloat()))

            var index = -1
            for (pointF in points) {
                pointFs.put(++index, pointF)
            }

            val bitmap =  if (ScanUtils.isScanPointsValid(pointFs)) {
                val point1 = Point(pointFs[0]!!.x.toDouble(), pointFs[0]!!.y.toDouble())
                val point2 = Point(pointFs[1]!!.x.toDouble(), pointFs[1]!!.y.toDouble())
                val point3 = Point(pointFs[2]!!.x.toDouble(), pointFs[2]!!.y.toDouble())
                val point4 = Point(pointFs[3]!!.x.toDouble(), pointFs[3]!!.y.toDouble())
                ScanUtils.enhanceReceipt(bmp, point1, point2, point3, point4, false)
//        }
            } else {
                bmp
            }
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }
        return null
    }

    private val pictureCallback =
        PictureCallback { data, camera ->
            camera.stopPreview()
            clearAndInvalidateCanvas()
            var bitmap = ScanUtils.decodeBitmapFromByteArray(
                    data,
                    ScanConstants.HIGHER_SAMPLING_THRESHOLD, ScanConstants.HIGHER_SAMPLING_THRESHOLD
            )
            val matrix = Matrix()
            matrix.postRotate(90f)
            bitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
            )
            iScanner.onPictureClicked(bitmap)
            postDelayed({ isCapturing = false }, 3000)
        }

    private val previewCallback =
        PreviewCallback { data, camera ->
            if (busy) {
                return@PreviewCallback
            }

            if ((null != camera) && (data != null)) {
                busy = true
                Observable.just(data)
                    .subscribeOn(proxySchedule)
                    .subscribe {
                        try {
                            val pictureSize = camera.parameters.previewSize
                            val yuv = Mat(Size(pictureSize.width.toDouble(), pictureSize.height * 1.5), CvType.CV_8UC1)
                            yuv.put(0, 0, data)
                            val mat = Mat(Size(480.0, 480.0 * pictureSize.height / pictureSize.width), CvType.CV_8U)
                            Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2BGR_NV21, 4)

                            val originalPreviewSize = mat.size()
                            val originalPreviewArea = mat.rows() * mat.cols()

                            var largestQuad =
                                ScanUtils.detectLargestQuadrilateral(mat, false)
                            if (largestQuad.isEmpty()) largestQuad =
                                ScanUtils.detectLargestQuadrilateral(mat, true)
//                            if (largestQuad == null) largestQuad =
//                                ScanUtils.detectHoughQuadrilateral(mat, false)
//                            if (largestQuad == null) largestQuad =
//                                ScanUtils.detectHoughQuadrilateral(mat, true)

                            val previewBitmap = getPreviewImage(yuv, largestQuad.toList())

                            yuv.release()
                            mat.release()

                            Observable.create<Pair<List<Point>, Bitmap?>> {
                                it.onNext(Pair(largestQuad.toList(), previewBitmap))
                                busy = false
                            }
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    if (null != it) {
                                        drawLargestRect(it.first, originalPreviewSize, originalPreviewArea)
                                        updatePreview(it.second)
                                    }
                                }
                        } catch (e: Exception) {
                            busy = false
                        }
                    }.let {

                    }
            }
            else {
                busy = false
            }
        }

    private val mShutterCallBack = ShutterCallback {
        if (context != null) {
            val mAudioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mAudioManager?.playSoundEffect(AudioManager.FLAG_PLAY_SOUND)
        }
    }
}