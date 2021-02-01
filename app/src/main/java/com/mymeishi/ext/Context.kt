package com.mymeishi.ext

import android.content.Context
import android.net.ConnectivityManager

fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = this.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager?
        ?: return false
    if (connectivityManager.activeNetworkInfo == null) {
        return false
    }
    return connectivityManager.activeNetworkInfo.isConnected
}