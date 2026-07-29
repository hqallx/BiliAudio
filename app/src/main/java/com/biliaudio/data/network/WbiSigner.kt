package com.biliaudio.data.network

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B站 WBI 签名工具。
 *
 * B站的 `x/player/wbi/playurl`、`x/polymer/web-space/...` 等「wbi」接口要求
 * 在请求参数中附加 `wts`（时间戳）和 `w_rid`（参数签名），否则返回 -403/-509。
 *
 * 签名密钥由 `x/web-interface/nav` 接口的 `data.wbi_img` 字段提供：
 *  - `img_url` 末段（去掉扩展名）= img_key
 *  - `sub_url` 末段（去掉扩展名）= sub_key
 *  - `raw = img_key + sub_key`，经固定混淆表重排后取前 32 字符 = mixin_key
 *
 * 签名算法：
 *  1. 在原始参数中加入 `wts = 当前秒级时间戳`
 *  2. 按 key 字典序排序
 *  3. 拼成 `k1=v1&k2=v2&...`（值需做 WBI 风格 URL 编码，过滤空值）
 *  4. `w_rid = MD5(query + mixin_key)`
 *
 * 密钥缓存策略：由 [updateKeys] 在 nav 接口成功后写入，进程内常驻。
 * B站密钥不频繁轮换，进程生命周期内无需主动刷新。
 */
@Singleton
class WbiSigner @Inject constructor() {

    @Volatile
    private var mixinKey: String? = null

    fun hasKey(): Boolean = !mixinKey.isNullOrEmpty()

    /**
     * 从 nav 接口的 wbi_img 字段更新签名密钥。
     */
    fun updateKeys(imgUrl: String, subUrl: String) {
        val imgKey = extractKey(imgUrl)
        val subKey = extractKey(subUrl)
        if (imgKey.isNotEmpty() && subKey.isNotEmpty()) {
            mixinKey = computeMixinKey(imgKey + subKey)
        }
    }

    /**
     * 对请求参数进行 WBI 签名，返回带 `wts` 和 `w_rid` 的完整参数表。
     * 若密钥未就绪，原样返回（调用方需自行处理可能的 -403）。
     */
    fun sign(params: Map<String, String>): Map<String, String> {
        val key = mixinKey ?: return params
        val wts = System.currentTimeMillis() / 1000L
        val withWts = params.toMutableMap().apply {
            put("wts", wts.toString())
        }
        // 按 key 字典序排序后拼接查询串（与 Python 端 sorted() 行为一致）
        val query = withWts.entries
            .sortedBy { it.key }
            .filter { it.value.isNotEmpty() }
            .joinToString("&") { "${wbiEncode(it.key)}=${wbiEncode(it.value)}" }
        val wRid = md5(query + key)
        return withWts.toMap() + ("w_rid" to wRid)
    }

    private fun extractKey(url: String): String {
        if (url.isEmpty()) return ""
        // img_url 形如 https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png
        // 取最后一段路径并去掉扩展名
        val lastSegment = url.substringAfterLast('/').substringBeforeLast('.')
        return lastSegment
    }

    /**
     * WBI 风格的 URL 编码。
     * 与 Python `urllib.parse.quote(s, safe='')` 等价：
     * 字母、数字及 `_.-~` 不编码，其余字符按 UTF-8 字节做 %XX。
     */
    private fun wbiEncode(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c.isLetterOrDigit() || c == '_' || c == '.' || c == '-' || c == '~' -> {
                    sb.append(c)
                }
                else -> {
                    for (b in c.toString().toByteArray(Charsets.UTF_8)) {
                        sb.append('%')
                        sb.append(HEX_UPPER[(b.toInt() ushr 4) and 0x0F])
                        sb.append(HEX_UPPER[b.toInt() and 0x0F])
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX_LOWER[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX_LOWER[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    companion object {
        private val HEX_UPPER = "0123456789ABCDEF".toCharArray()
        private val HEX_LOWER = "0123456789abcdef".toCharArray()

        /**
         * B站固定的密钥混淆表（64 项），用于把 raw_key 的 64 个字符按给定顺序重排，
         * 取前 32 字符作为实际签名密钥。
         * 表值来自 bilibili-API-collect 官方文档（SocialSisterYi/bilibili-API-collect）。
         */
        private val MIXIN_KEY_ENC_TAB = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
        )

        fun computeMixinKey(raw: String): String {
            val sb = StringBuilder(32)
            for (i in MIXIN_KEY_ENC_TAB) {
                if (i < raw.length) {
                    sb.append(raw[i])
                }
            }
            return sb.toString().take(32)
        }
    }
}
