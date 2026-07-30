package com.biliaudio.data

object BiliConstants {
    const val BASE_URL = "https://api.bilibili.com/"
    const val PASSPORT_BASE_URL = "https://passport.bilibili.com/"
    const val WEB_BASE_URL = "https://www.bilibili.com"

    const val DEFAULT_PAGE_SIZE = 20
    const val DEFAULT_QUALITY = 64
    const val DEFAULT_FNVAL = 4048
    const val DEFAULT_FOURK = 1

    const val QR_CODE_POLL_INTERVAL_MS = 2000L
    const val PROGRESS_UPDATE_INTERVAL_MS = 500L

    // 使用 B站 App 移动端 UA（照搬 BBPlayer），passport 接口对此有风控依赖
    const val USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 BiliApp/6.66.0"

    object QrCodeStatus {
        const val WAITING_FOR_SCAN = 86101
        const val SCANNED_WAITING_CONFIRM = 86090
        const val EXPIRED = 86038
        const val SUCCESS = 0
    }

    object Order {
        const val MTIME = "mtime"
        const val VIEW = "view"
        const val PUBTIME = "pubtime"
    }

    object Cache {
        const val AUDIO_URL_CACHE_SIZE = 50
        const val AUDIO_URL_CACHE_TTL_MS = 3600_000L // 1 hour

        // 播放字节级 LRU 缓存（参考 BBPlayer 的流式缓存）。
        // 跨曲目复用：已播放/预加载的音频字节缓存在本地，再次播放命中缓存秒开。
        const val MEDIA_CACHE_MAX_BYTES = 256L * 1024 * 1024 // 256MB
        const val MEDIA_CACHE_DIR = "media_cache"
    }

    /**
     * B站 DASH 音频流质量 id。
     * 参考 BBPlayer resolveAudioUrl 的音频流选择策略与默认质量。
     */
    object AudioQuality {
        const val AAC_192K = 30280 // 192K AAC（默认首选，音质/体积均衡）
        const val AAC_132K = 30232 // 132K AAC
        const val AAC_64K = 30216  // 64K AAC（最低，弱网兜底）
    }

    /**
     * 懒解析占位 URI 的 scheme 与 host。
     * Track.audioUrl 在创建时填入占位 URI，ExoPlayer 加载时由
     * ResolvingDataSource 拦截并解析为真实音频地址，使播放列表创建瞬时完成。
     * 参考 BBPlayer 的 orpheus://bilibili 占位方案。
     */
    object LazyUri {
        const val SCHEME = "biliaudio"
        const val HOST = "resolve"
    }
}
