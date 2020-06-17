package com.example.playerproject.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.media.MediaBrowserServiceCompat
import com.example.playerproject.MainActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import java.util.*

class PlayerService : MediaBrowserServiceCompat() {

    // todo: notifications, player callbacks, media browsing

    companion object {
        private const val EMPTY_MEDIA_ROOT_ID = "empty_root_id"
        private const val POSITION_UPDATE_TIME_MILLIS = 500L
        private const val PLAYBACK_SPEED = 1f
        private const val DUCK_VOLUME = 0.2f
    }

    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener

    @RequiresApi(Build.VERSION_CODES.O)
    private lateinit var audioFocusRequest: AudioFocusRequest

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var metadataBuilder: MediaMetadataCompat.Builder

    private lateinit var player: ExoPlayer
    private var currentPlayerUri: Uri? = null
    private var positionTimer: Timer? = null
    private var playerPlaylist = PlayerPlaylist()

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusChangeListener = getAudioFocusChangeListener()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = getAudioFocusRequest()
        }

        mediaSession = MediaSessionCompat(applicationContext, "PlayerServiceMediaSession").apply {
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            or PlaybackStateCompat.ACTION_PAUSE
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                            or PlaybackStateCompat.ACTION_SEEK_TO
                            or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            setPlaybackState(stateBuilder.build())

            val activityIntent = Intent(applicationContext, MainActivity::class.java)
            setSessionActivity(PendingIntent.getActivity(applicationContext, 0, activityIntent, 0))

            setCallback(getMediaSessionCallback())
            setSessionToken(sessionToken)
        }

        metadataBuilder = MediaMetadataCompat.Builder()
        player = ExoPlayerFactory.newSimpleInstance(applicationContext)

        // todo
        /*val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, applicationContext, MediaButtonReceiver::class.java)
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(applicationContext, 0, mediaButtonIntent, 0));*/
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        player.release()
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(null)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot(EMPTY_MEDIA_ROOT_ID, null)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return PlayerServiceBinder()
    }

    private fun getAudioFocusChangeListener() = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaSession.controller.transportControls.play()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player.audioComponent?.volume = DUCK_VOLUME
            }

            else -> {
                mediaSession.controller.transportControls.pause()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getAudioFocusRequest(): AudioFocusRequest {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    private fun getMediaSessionCallback() = object : MediaSessionCompat.Callback() {

        override fun onPlay() {
            val audioFocus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.requestAudioFocus(audioFocusRequest)
            } else {
                audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
            if (audioFocus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return

            val currentTrack = playerPlaylist.getCurrent() ?: return

            if (currentPlayerUri != currentTrack.uri) {
                currentPlayerUri = currentTrack.uri

                mediaSession.setMetadata(parseMetadata(currentTrack))
                mediaSession.isActive = true

                val dataSourceFactory = DefaultDataSourceFactory(applicationContext, Util.getUserAgent(applicationContext, "PlayerApp"))
                player.prepare(ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(currentTrack.uri))
            }

            player.playWhenReady = true
            player.audioComponent?.volume = 1f
            startPositionLooper()

            mediaSession.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    player.currentPosition,
                    PLAYBACK_SPEED
                ).build()
            )
        }

        override fun onPause() {
            positionTimer?.cancel()
            positionTimer = null
            player.playWhenReady = false

            mediaSession.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    player.currentPosition,
                    PLAYBACK_SPEED
                ).build()
            )
        }

        override fun onStop() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }

            currentPlayerUri = null

            mediaSession.isActive = false

            positionTimer?.cancel()
            positionTimer = null
            player.playWhenReady = false

            mediaSession.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_STOPPED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    PLAYBACK_SPEED
                ).build()
            )
        }

        override fun onSeekTo(pos: Long) {
            player.seekTo(pos)
        }

        override fun onSkipToNext() {
            val nextTrack = playerPlaylist.next()
            if (nextTrack != null) {
                mediaSession.controller.transportControls.play()
            }
        }

        override fun onSkipToPrevious() {
            val previousTrack = playerPlaylist.previous()
            if (previousTrack != null) {
                mediaSession.controller.transportControls.play()
            }
        }
    }

    private fun startPositionLooper() {
        positionTimer?.cancel()
        val timerTask = object : TimerTask() {

            override fun run() {
                if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING) { // todo current state?
                    mediaSession.setPlaybackState(
                        stateBuilder
                            .setState(PlaybackStateCompat.STATE_PLAYING, player.currentPosition, 1f)
                            .build()
                    )
                }
            }
        }
        positionTimer = Timer()
        positionTimer?.scheduleAtFixedRate(timerTask, POSITION_UPDATE_TIME_MILLIS, POSITION_UPDATE_TIME_MILLIS)
    }

    private fun parseMetadata(track: PlayerPlaylist.Track): MediaMetadataCompat {
        return metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, track.getArtwork())
            .build()
    }

    inner class PlayerServiceBinder : Binder() {

        fun getMediaSessionToken(): MediaSessionCompat.Token {
            return mediaSession.sessionToken
        }

        fun setPlaylist(list: List<Uri>) {
            playerPlaylist.setPlaylist(applicationContext, list)
            mediaSession.controller.transportControls.play()
        }
    }
}
