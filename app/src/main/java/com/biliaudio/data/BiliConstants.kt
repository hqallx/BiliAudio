package com.biliaudio.data

object BiliConstants {
    const val BASE_URL = "https://api.bilibili.com/"
    const val PASSPORT_BASE_URL = "https://passport.bilibili.com/"
    const val WEB_BASE_URL = "https://www.bilibili.com"

    const val DEFAULT_PAGE_SIZE = 20
    const val DEFAULT_QUALITY = 64
    const val DEFAULT_FNVAL = 16
    const val DEFAULT_FOURK = 1

    const val QR_CODE_POLL_INTERVAL_MS = 3000L
    const val PROGRESS_UPDATE_INTERVAL_MS = 500L

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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
