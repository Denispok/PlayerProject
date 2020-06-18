package com.example.playerproject.utils

object TextUtil {

    fun formatTrackTime(millis: Long): String {
        val minutes = millis / (60 * 1000)
        val seconds = millis / 1000 % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    fun formatRemainingTrackTime(currentMillis: Long, durationMillis: Long): String {
        val remainingTime = durationMillis - currentMillis
        return '-' + formatTrackTime(remainingTime)
    }
}
