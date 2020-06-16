package com.example.playerproject.utils

import android.util.Log

object LogUtil {

    fun logTime(message: String, action: () -> Unit) {
        val startTime = System.currentTimeMillis()
        action.invoke()
        val actionTime = System.currentTimeMillis() - startTime
        Log.d("TIME", "$message $actionTime millis")
    }
}
