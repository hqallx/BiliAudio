package com.biliaudio

import android.app.Application
import com.biliaudio.data.preferences.PreferencesManager

class BiliAudioApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
    }
}
