package com.biliaudio.player.download

import android.content.Context
import android.os.Environment
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

class AudioCacheManager(context: Context) {
    private val cacheDir = File(context.filesDir, "audio_cache")
    private val maxCacheSize = 500L * 1024 * 1024

    val simpleCache: SimpleCache by lazy {
        cacheDir.mkdirs()
        SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(maxCacheSize), StandaloneDatabaseProvider(context))
    }

    fun getCacheSize(): Long {
        var size = 0L
        cacheDir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }

    fun clearCache() {
        simpleCache.release()
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }
}
