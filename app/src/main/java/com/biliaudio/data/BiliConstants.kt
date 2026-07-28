package com.biliaudio.data

object BiliConstants {
    const val BASE_URL = "https://api.bilibili.com/"
    const val PASSPORT_BASE_URL = "https://passport.bilibili.com/"
    const val WEB_BASE_URL = "https://www.bilibili.com"

    const val DEFAULT_PAGE_SIZE = 20
    const val DEFAULT_QUALITY = 64
    const val DEFAULT_FNVAL = 16
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
    }
}
