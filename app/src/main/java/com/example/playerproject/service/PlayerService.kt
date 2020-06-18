package com.example.playerproject.service

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.example.playerproject.MainActivity
import com.example.playerproject.R
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import java.util.*

class PlayerService : MediaBrowserServiceCompat() {

    // todo: player error, media browsing

    companion object {
        private const val EMPTY_MEDIA_ROOT_ID = "empty_root_id"
        private const val NOTIFICATION_ID = 1
        private const val POSITION_UPDATE_TIME_MILLIS = 500L
        private const val PLAYBACK_SPEED = 1f
        private const val DUCK_VOLUME = 0.2f
    }

    private lateinit var mainHandler: Handler

    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener

    @RequiresApi(Build.VERSION_CODES.O)
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var noisyBroadcastReceiver: BroadcastReceiver? = null

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var metadataBuilder: MediaMetadataCompat.Builder

    private lateinit var player: ExoPlayer
    private var currentPlayerUri: Uri? = null
    private var positionTimer: Timer? = null
    private var playerPlaylist = PlayerPlaylist()

    override fun onCreate() {
        super.onCreate()
        mainHandler = Handler(Looper.getMainLooper())

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusChangeListener = getAudioFocusChangeListener()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = getAudioFocusRequest()
        }

        mediaSession = MediaSessionCompat(this, "PlayerServiceMediaSession").apply {
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

            val activityIntent = Intent(this@PlayerService, MainActivity::class.java)
            setSessionActivity(PendingIntent.getActivity(this@PlayerService, 0, activityIntent, 0))

            setCallback(getMediaSessionCallback())
            setSessionToken(sessionToken)
        }

        metadataBuilder = MediaMetadataCompat.Builder()
        player = ExoPlayerFactory.newSimpleInstance(this)
        player.addListener(object : Player.EventListener {

            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (playerPlaylist.hasNext()) {
                        mediaSession.controller.transportControls.skipToNext()
                    } else {
                        mediaSession.controller.transportControls.stop()
                    }
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
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
                startService(Intent(this@PlayerService, PlayerService::class.java))

                mediaSession.setMetadata(parseMetadata(currentTrack))
                mediaSession.isActive = true

                val dataSourceFactory = DefaultDataSourceFactory(this@PlayerService, Util.getUserAgent(this@PlayerService, "PlayerApp"))
                player.prepare(ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(currentTrack.uri))
            }

            if (noisyBroadcastReceiver == null) {
                noisyBroadcastReceiver = getNoisyBroadcastReceiver()
                registerReceiver(noisyBroadcastReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
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

            updateNotification(PlaybackStateCompat.STATE_PLAYING)
        }

        override fun onPause() {
            noisyBroadcastReceiver?.also {
                unregisterReceiver(it)
                noisyBroadcastReceiver = null
            }

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

            updateNotification(PlaybackStateCompat.STATE_PAUSED)
        }

        override fun onStop() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }

            noisyBroadcastReceiver?.also {
                unregisterReceiver(it)
                noisyBroadcastReceiver = null
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

            updateNotification(PlaybackStateCompat.STATE_STOPPED)
            stopSelf()
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

    private fun getNoisyBroadcastReceiver() = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                mediaSession.controller.transportControls.pause()
            }
        }
    }

    private fun startPositionLooper() {
        positionTimer?.cancel()
        val timerTask = object : TimerTask() {

            override fun run() {
                if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING)
                    mainHandler.post {
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

    fun updateNotification(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                startForeground(NOTIFICATION_ID, getNotification(playbackState))
            }

            PlaybackStateCompat.STATE_PAUSED -> {
                NotificationManagerCompat.from(this@PlayerService).notify(NOTIFICATION_ID, getNotification(playbackState))
                stopForeground(false)
            }

            else -> {
                stopForeground(true)
            }
        }
    }

    private fun getNotification(playbackState: Int): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
        else NotificationCompat.Builder(this)

        builder.setContentIntent(mediaSession.controller.sessionActivity)
            .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous, getString(R.string.player_previous),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                )
            )
            .addAction(
                if (playbackState == PlaybackStateCompat.STATE_PLAYING)
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_pause, getString(R.string.player_pause),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)
                    )
                else
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_play, getString(R.string.player_play),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)
                    )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next, getString(R.string.player_next),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
            )
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)

        setNotificationDescription(builder, mediaSession.controller.metadata.description)
        return builder.build()
    }

    private fun setNotificationDescription(builder: NotificationCompat.Builder, description: MediaDescriptionCompat) {
        builder.setContentTitle(description.title)
            .setContentText(description.subtitle)
            .setSubText(description.description)
            .setLargeIcon(description.iconBitmap)
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
            playerPlaylist.setPlaylist(this@PlayerService, list)
            mediaSession.controller.transportControls.play()
        }
    }
}
