package com.biliaudio

import android.app.Application
import com.biliaudio.data.network.NetworkModule
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BiliAudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化网络模块，恢复持久化的 Cookie
        NetworkModule.init(this)
    }
}
