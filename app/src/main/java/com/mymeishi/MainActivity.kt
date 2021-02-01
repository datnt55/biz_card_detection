package com.mymeishi

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.mymeishi.ext.AlertUtil
import com.mymeishi.ext.isNetworkAvailable
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), MeishiFragment.CallBackListener {

    companion object {
        val TAG = "MainActivity"
    }
    var mWebView: WebView? = null

    private val INPUT_FILE_REQUEST_CODE: Int = 1
    private val FILECHOOSER_RESULTCODE = 1
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var mUploadMessage: ValueCallback<Uri>? = null
    private var mCameraPhotoPath: String? = null
    private var mCapturedImageURI: Uri? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
        }
        setContentView(R.layout.activity_main)
        mWebView = findViewById<View>(R.id.webView) as WebView

        MeishiFragment.callBackListener = this

        with(mWebView!!) {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url?.let {
                        if (url.contains(GlobalConstant.PRINT_SERVICE_PAGE_URL) || url.contains(GlobalConstant.COMPANY_PAGE_URL) ) {
                            goToUrl(url)
                            return true
                        }
                        next(it)
                    }

                    return true
                }
            }

            context?.let { context ->
                if(!context.isNetworkAvailable()) {
                    AlertUtil.showSingleAlertDialog(context, getString(R.string.alert_offline_title), getString(R.string.alert_offline_msg))
                    settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
                else {
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                }
            }

            webChromeClient = MyWebChromeClient()
            if (Build.VERSION.SDK_INT >= 19) {
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            else if(Build.VERSION.SDK_INT in 11..18) {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(WebAppInterface(context), "nativeApp")
        }

        init()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mWebView!!.canGoBack()) {
                    mWebView!!.goBack()
                } else {
                    finish()
                }
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun init() {
        webView?.loadUrl(GlobalConstant.BASE_URL)
    }

    private fun next(url: String) {
            if (url == GlobalConstant.SELECT_MEISHI_PAGE_URL) {
                webView?.loadUrl(GlobalConstant.APP_SELECT_MEISHI_PAGE_URL)
            }
            else {
                webView?.loadUrl(url)
            }
    }

    private fun goToUrl(url: String) {
        val uriUrl: Uri = Uri.parse(url)
        val launchBrowser = Intent(Intent.ACTION_VIEW, uriUrl)
        startActivity(launchBrowser)
    }

    override fun onDismiss(base64Img: String) {
        val fileName = System.currentTimeMillis().toString() + "_meishi.jpg"
        val data = "{\"image\":\"data:image/png;base64, $base64Img\",\"name\":\"$fileName\"}"

        val script = "${GlobalConstant.RECEIVE_IMG_METHOD}('${data}')"
        webView?.evaluateJavascript(
            script
        ) { s ->
            Log.d(TAG, "Result: $s")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode !== INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            var results: Array<Uri>? = null
            // Check that the response is a good one
            if (resultCode === Activity.RESULT_OK) {
                if (data == null) {
                    // If there is not data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = arrayOf(Uri.parse(mCameraPhotoPath))
                    }
                } else {
                    val dataString = data.dataString
                    if (dataString != null) {
                        results = arrayOf(Uri.parse(dataString))
                    }
                }
            }
            mFilePathCallback!!.onReceiveValue(results)
            mFilePathCallback = null
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            if (requestCode !== FILECHOOSER_RESULTCODE || mUploadMessage == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            if (requestCode === FILECHOOSER_RESULTCODE) {
                if (null == this.mUploadMessage) {
                    return
                }
                var result: Uri? = null
                try {
                    result = if (resultCode !== Activity.RESULT_OK) {
                        null
                    } else {
                        // retrieve from the private variable if the intent is null
                        if (data == null) mCapturedImageURI else data.data
                    }
                } catch (e: Exception) {

                }
                mUploadMessage!!.onReceiveValue(result)
                mUploadMessage = null
            }
        }
        return
    }

    class WebAppInterface
    /**
     * Instantiate the interface and set the context
     */ internal constructor(var mContext: Context) {
        /**
         * Show a toast from the web page
         */
        @JavascriptInterface
        fun startCamera() {
            val meishiFragment = MeishiFragment.newInstance()
            meishiFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogFragmentTheme)
            meishiFragment.show((mContext as MainActivity).supportFragmentManager, TAG)
        }

    }

    inner class MyWebChromeClient : WebChromeClient() {
        // For Android 5.0


        override fun onShowFileChooser(
            view: WebView,
            filePath: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            // Double check that we don't have any existing callbacks
            if (mFilePathCallback != null) {
                mFilePathCallback?.onReceiveValue(null)
            }
            mFilePathCallback = filePath
            var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent?.resolveActivity(packageManager) != null) {
                // Create the File where the photo should go
                var photoFile: File? = null
                try {
                    photoFile = createImageFile()
                    takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath)
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                }
                // Continue only if the File was successfully created
                if (photoFile != null) {
                    mCameraPhotoPath = "file:" + photoFile.absolutePath
                    takePictureIntent.putExtra(
                        MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photoFile)
                    )
                } else {
                    takePictureIntent = null
                }
            }
            val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
            contentSelectionIntent.type = "image/*"
            val intentArray: Array<Intent>?
            intentArray = takePictureIntent?.let { arrayOf(it) }
            val chooserIntent = Intent(Intent.ACTION_CHOOSER)
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser")
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
            startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE)
            return true
        }

        // openFileChooser for Android 3.0+
        // openFileChooser for Android < 3.0
        @JvmOverloads
        fun openFileChooser(
            uploadMsg: ValueCallback<Uri>,
            acceptType: String? = ""
        ) {
            mUploadMessage = uploadMsg
            // Create AndroidExampleFolder at sdcard
            // Create AndroidExampleFolder at sdcard
            val imageStorageDir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                , "AndroidExampleFolder"
            )
            if (!imageStorageDir.exists()) {
                // Create AndroidExampleFolder at sdcard
                imageStorageDir.mkdirs()
            }
            // Create camera captured image file path and name
            val file = File(
                imageStorageDir.toString() + File.separator + "IMG_"
                        + System.currentTimeMillis().toString() + ".jpg"
            )
            mCapturedImageURI = Uri.fromFile(file)
            // Camera capture image intent
            val captureIntent = Intent(
                MediaStore.ACTION_IMAGE_CAPTURE
            )
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI)
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            // Create file chooser intent
            val chooserIntent = Intent.createChooser(i, "Image Chooser")
            // Set camera intent to file chooser
            chooserIntent.putExtra(
                Intent.EXTRA_INITIAL_INTENTS
                , arrayOf<Parcelable>(captureIntent)
            )
            // On select image call onActivityResult method of activity
            startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE)
        }

        //openFileChooser for other Android versions
        fun openFileChooser(
            uploadMsg: ValueCallback<Uri>,
            acceptType: String?,
            capture: String?
        ) {
            openFileChooser(uploadMsg, acceptType)
        }

        private fun createImageFile(): File? {
            // Create an image file name
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val imageFileName = "JPEG_" + timeStamp + "_"
            val storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",  /* suffix */
                storageDir /* directory */
            )
        }
    }
}
