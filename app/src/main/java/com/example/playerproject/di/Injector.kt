package com.example.playerproject.di

import com.example.playerproject.App
import com.example.playerproject.manager.FileManager

object Injector {

    val context by lazy { App.instance }

    val fileManager by lazy { FileManager(context) }
}
