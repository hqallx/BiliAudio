package com.biliaudio.data

/**
 * 把 http:// 开头的 URL 升级为 https://。
 *
 * B站接口返回的封面 / 音频流地址常为明文 http://（如 http://i0.hdslb.com/...），
 * 部分设备即使配置了 networkSecurityConfig 也会因明文流量被拦截导致加载失败。
 * B站 CDN 已全面支持 HTTPS，统一升级可避免明文相关问题。
 */
fun String.toHttpsUrl(): String {
    return if (startsWith("http://")) {
        "https://" + substring(7)
    } else {
        this
    }
}
