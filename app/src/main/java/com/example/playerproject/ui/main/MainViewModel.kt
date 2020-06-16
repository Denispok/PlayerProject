package com.example.playerproject.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import com.example.playerproject.di.Injector
import com.example.playerproject.service.PlayerService
import com.example.playerproject.ui.base.BaseViewModel

class MainViewModel : BaseViewModel() {

    private val context = Injector.context
    private val fileManager = Injector.fileManager

    private var serviceConnection: ServiceConnection? = null
    private var playerServiceBinder: PlayerService.PlayerServiceBinder? = null

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

    fun onFolderChosen(treeUri: Uri) {
        val list = fileManager.getAudioFilesFromTreeUri(treeUri)
        if (list != null) {
            playerServiceBinder?.setPlaylist(list)
        } else {
            // todo: show error
        }
    }

    private inner class PlayerServiceConnection : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            playerServiceBinder = service as PlayerService.PlayerServiceBinder
        }

        override fun onServiceDisconnected(name: ComponentName) {
            playerServiceBinder = null
        }
    }
}
