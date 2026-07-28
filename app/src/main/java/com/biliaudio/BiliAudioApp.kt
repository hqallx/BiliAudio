package com.biliaudio

import android.app.Application
import android.util.Log
import com.biliaudio.data.network.NetworkModule
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BiliAudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化网络模块，恢复持久化的 Cookie。
        // 任何失败都不应阻塞应用启动，否则会出现「打开就闪退」。
        try {
            NetworkModule.init(this)
        } catch (t: Throwable) {
            Log.e("BiliAudioApp", "NetworkModule init failed", t)
        }
    }
}
