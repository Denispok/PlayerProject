package com.example.playerproject.ui.base

import androidx.lifecycle.ViewModel

abstract class BaseViewModel : ViewModel() {

    open fun onCreate() {
    }

    open fun onStart() {
    }

    open fun onStop() {
    }

    open fun onDestroy() {
    }
}
