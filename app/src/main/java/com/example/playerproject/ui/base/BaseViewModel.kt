package com.example.playerproject.ui.base

import androidx.lifecycle.ViewModel

abstract class BaseViewModel : ViewModel() {

    val errors = SingleLiveEvent<String>()

    open fun onCreate() {
    }

    open fun onStart() {
    }

    open fun onStop() {
    }

    open fun onDestroy() {
    }
}
