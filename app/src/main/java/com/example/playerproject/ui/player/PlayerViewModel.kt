package com.example.playerproject.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.playerproject.di.Injector
import com.example.playerproject.service.PlayerService
import com.example.playerproject.ui.base.BaseViewModel

class PlayerViewModel : BaseViewModel() {

    private val context = Injector.context

    private var serviceConnection: ServiceConnection? = null
    private var playerServiceBinder: PlayerService.PlayerServiceBinder? = null
    private var mediaController: MediaControllerCompat? = null

    private val _metadata = MutableLiveData<MediaMetadataCompat?>()
    val metadata: LiveData<MediaMetadataCompat?> get() = _metadata

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> get() = _isPlaying

    private val _position = MutableLiveData<PlayPosition>()
    val position: LiveData<PlayPosition> get() = _position

    override fun onCreate() {
        super.onCreate()
        val newServiceConnection = PlayerServiceConnection()
        if (context.bindService(Intent(context, PlayerService::class.java), newServiceConnection, Context.BIND_AUTO_CREATE)) {
            serviceConnection = newServiceConnection
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceConnection?.also { context.unbindService(it) }
        serviceConnection = null
        playerServiceBinder = null
    }

    fun onPlayPauseClick() {
        if (mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            mediaController?.transportControls?.pause()
        } else {
            mediaController?.transportControls?.play()
        }
    }

    fun onNextClick() {
        mediaController?.transportControls?.skipToNext()
    }

    fun onPreviousClick() {
        mediaController?.transportControls?.skipToPrevious()
    }

    fun onSeek(progress: Int) {
        _metadata.value?.also { meta ->
            val duration = meta.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
            mediaController?.transportControls?.seekTo(duration / 100 * progress)
        }
    }

    private fun setPlaybackState(state: PlaybackStateCompat) {
        _isPlaying.postValue(state.state == PlaybackStateCompat.STATE_PLAYING)

        _metadata.value?.also { meta ->
            val duration = meta.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)

            val position = if (duration > 0) (state.position.toFloat() / duration * 100).toInt() else 0
            val currentTime = if (state.position != PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN) state.position.toString()
            else 0.toString()
            val remainingTime = if (duration > 0) (duration - position).toString() else 0.toString()

            _position.postValue(PlayPosition(position, currentTime, remainingTime))
        }
    }

    private fun setMetadata(metadata: MediaMetadataCompat?) {
        _metadata.postValue(metadata)
    }

    private inner class PlayerServiceConnection : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            playerServiceBinder = service as PlayerService.PlayerServiceBinder

            try {
                val newMediaController = MediaControllerCompat(context, playerServiceBinder!!.getMediaSessionToken())
                newMediaController.playbackState?.also { setPlaybackState(it) }
                setMetadata(newMediaController.metadata)
                newMediaController.registerCallback(object : MediaControllerCompat.Callback() {

                    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                        state?.also { setPlaybackState(it) }
                    }

                    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                        setMetadata(metadata)
                    }
                })
                mediaController = newMediaController
            } catch (e: RemoteException) {
                mediaController = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            playerServiceBinder = null
            mediaController = null
        }
    }

    data class PlayPosition(
        /** in percents from 0 to 100*/
        val position: Int,
        val currentTime: String,
        val remainingTime: String
    )
}