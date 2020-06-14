package com.example.playerproject.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.playerproject.di.Injector

class MainViewModel : ViewModel() {

    private val fileManager = Injector.fileManager

    fun onFolderChosen(treeUri: Uri) {
        val list = fileManager.getAudioFilesFromTreeUri(treeUri)
    }
}
