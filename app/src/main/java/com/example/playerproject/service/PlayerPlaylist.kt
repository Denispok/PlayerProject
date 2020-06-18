package com.example.playerproject.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.playerproject.di.Injector

class PlayerPlaylist {

    private var tracks: List<Track>? = null
    private var currentTrackIndex = 0
    private val metadataRetriever = MediaMetadataRetriever()

    // todo empty list state
    fun setPlaylist(context: Context, list: List<Uri>) {
        tracks = list.map {
            metadataRetriever.setDataSource(context, it)
            val title = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: -1
            Track(it, title, artist, duration)
        }
        currentTrackIndex = 0
    }

    fun getCurrent(): Track? {
        return tracks?.get(currentTrackIndex)
    }

    fun hasPrevious(): Boolean {
        return tracks?.let { currentTrackIndex > 0 } ?: false
    }

    fun previous(): Track? {
        return if (hasPrevious()) tracks?.get(--currentTrackIndex)
        else null
    }

    fun hasNext(): Boolean {
        return tracks?.let { currentTrackIndex + 1 < it.size } ?: false
    }

    fun next(): Track? {
        return if (hasNext()) tracks?.get(++currentTrackIndex)
        else null
    }

    class Track(
        val uri: Uri,
        val title: String?,
        val artist: String?,
        val duration: Long
    ) {
        fun getArtwork(): Bitmap? {
            val metadataRetriever = MediaMetadataRetriever() // todo reusable retriever and default artwork
            metadataRetriever.setDataSource(Injector.context, uri)
            val byteArray = metadataRetriever.embeddedPicture ?: return null
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }
    }
}
