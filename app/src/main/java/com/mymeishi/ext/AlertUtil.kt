package com.mymeishi.ext

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.mymeishi.R

class AlertUtil {
    companion object {
        fun showSingleAlertDialog(
            context: Context,
            title: String,
            message: String = "",
            positiveAction: DialogInterface.OnClickListener? = null
        ) {

            val builder = AlertDialog.Builder(context)
            builder.setTitle(title)
            if (message.isNotEmpty()) {
                builder.setMessage(message)
            }
            builder.setPositiveButton(R.string.eng_ok, positiveAction)
            builder.setCancelable(true)

            val dialog: AlertDialog = builder.create()
            dialog.show()
        }

        fun showAlertDialog(context: Context,
                            title: String = "", message: String = "",
                            negativeCaptionId: Int = R.string.cancel, positiveCaptionId: Int = R.string.eng_ok,
                            enableNegativeButton: Boolean = false, enablePositiveButton: Boolean = false,
                            negativeAction: DialogInterface.OnClickListener? = null, positiveAction: DialogInterface.OnClickListener? = null) {

            val builder = AlertDialog.Builder(context)
            builder.setTitle(title)
            if (message.isNotEmpty()) { builder.setMessage(message) }
            if (enablePositiveButton) {
                builder.setPositiveButton(positiveCaptionId, positiveAction)
            }
            if (enableNegativeButton) {
                builder.setNegativeButton(negativeCaptionId, negativeAction)
            }
            builder.setCancelable(true)

            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }
}