package com.mymeishi

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.mymeishi.ext.AlertUtil
import com.mymeishi.liveedgedetection.activity.ScanActivity
import com.mymeishi.liveedgedetection.constants.ScanConstants
import com.mymeishi.liveedgedetection.util.ScanUtils
import java.io.*

class MeishiFragment : DialogFragment(), View.OnClickListener {

    companion object {
        private const val REQUEST_CODE = 101
        var callBackListener: CallBackListener? = null
        fun newInstance() = MeishiFragment()
    }

    interface CallBackListener {
        fun onDismiss(base64Img: String)
    }

    private var scannedImageView: ImageView? = null
    private var baseBitmap: Bitmap? = null
    private var parentLayout: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_meishi, container, false)
        view.findViewById<View>(R.id.btn_again_camera)?.setOnClickListener(this)
        view.findViewById<View>(R.id.btn_set_meishi)?.setOnClickListener(this)
        parentLayout = view.findViewById<LinearLayout>(R.id.layout_parent)
        scannedImageView = view.findViewById(R.id.scanned_image)
        Log.d("MeishiFragment", (scannedImageView == null).toString())
        startScan()

        return view
    }

    private fun startScan() {
        val intent = Intent(context, ScanActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                parentLayout?.visibility = View.VISIBLE

                if (null != data && null != data.extras) {
                    val filePath =
                        data.extras!!.getString(ScanConstants.SCANNED_RESULT)
                    baseBitmap = ScanUtils.decodeBitmapFromFile(filePath, ScanConstants.IMAGE_NAME)
                    if (baseBitmap!!.width < baseBitmap!!.height) {
                        val matrix = Matrix()
                        matrix.postRotate(270f)
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            baseBitmap!!,
                            baseBitmap!!.width,
                            baseBitmap!!.height,
                            true
                        )
                        if (scaledBitmap != null) {
                            baseBitmap = Bitmap.createBitmap(
                                scaledBitmap,
                                0,
                                0,
                                scaledBitmap.width,
                                scaledBitmap.height,
                                matrix,
                                true
                            )
                        }
                    }
                    Log.d("MeishiFragment", (scannedImageView == null).toString())
                    scannedImageView?.scaleType = ImageView.ScaleType.FIT_CENTER
                    scannedImageView?.setImageBitmap(baseBitmap)
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                dismiss()
            }
        }
    }

    private fun toBase64(baseBitmap: Bitmap?): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        baseBitmap!!.compress(Bitmap.CompressFormat.JPEG, 75, byteArrayOutputStream)
        val byteArrayImage = byteArrayOutputStream.toByteArray()

        return String(Base64.encode(byteArrayImage, Base64.NO_WRAP))
    }

    override fun onClick(v: View) {
        if (v.id == R.id.btn_again_camera) {
            startScan()
        } else if (v.id == R.id.btn_set_meishi) {
            AlertUtil.showAlertDialog(
                context!!,
                getString(R.string.save_alert_title),
                getString(R.string.save_alert_content),
                positiveCaptionId = R.string.eng_ok,
                enablePositiveButton = true,
                negativeCaptionId = R.string.cancel,
                enableNegativeButton = true,
                positiveAction = DialogInterface.OnClickListener { dialog, _ ->
                    if (baseBitmap == null) return@OnClickListener
                    dialog.dismiss()
                    callBackListener?.onDismiss(toBase64(baseBitmap))
                    dismiss()
                },
                negativeAction = DialogInterface.OnClickListener {dialog, _ ->
                    dialog.dismiss()
                }
            )

        }
    }
}
