package com.biliaudio.data.network

import android.net.Uri
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

object CookieHelper {

    private val VALID_COOKIE_KEYS = setOf(
        "SESSDATA", "bili_jct", "DedeUserID", "sid",
        "buvid3", "b_lsid", "_uuid", "CURRENT_FNVAL",
        "rpdid", "LIVE_BUVID"
    )

    /**
     * 二维码登录成功 URL 中需要提取的 Cookie 名称。
     * bilibili 的 crossDomain URL 中包含这些查询参数，对应登录 Cookie。
     */
    private val QR_LOGIN_COOKIE_NAMES = listOf(
        "DedeUserID",
        "DedeUserID__ckMd5",
        "SESSDATA",
        "bili_jct"
    )

    fun parseCookies(cookieString: String): List<Cookie> {
        if (cookieString.isEmpty()) return emptyList()

        val cookies = mutableListOf<Cookie>()
        val cookiePairs = cookieString.split(";").map { it.trim() }.filter { it.isNotEmpty() }

        for (pair in cookiePairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                if (name.isNotEmpty()) {
                    try {
                        val cookie = Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(".bilibili.com")
                            .path("/")
                            .build()
                        cookies.add(cookie)
                    } catch (e: Exception) {
                        // 跳过无法解析的 Cookie，避免单个坏值导致整体恢复失败
                        e.printStackTrace()
                    }
                }
            }
        }

        return cookies
    }

    /**
     * 从二维码登录成功后返回的 crossDomain URL 中提取登录 Cookie。
     *
     * URL 形如：
     * https://passport.biligame.com/x/passport-login/web/crossDomain?DedeUserID=xxx&SESSDATA=xxx&bili_jct=xxx&...
     */
    fun extractCookiesFromUrl(url: String): List<Cookie> {
        if (url.isEmpty()) return emptyList()

        val uri = Uri.parse(url)
        val cookies = mutableListOf<Cookie>()

        for (name in QR_LOGIN_COOKIE_NAMES) {
            val value = uri.getQueryParameter(name)
            if (!value.isNullOrEmpty()) {
                try {
                    val cookie = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(".bilibili.com")
                        .path("/")
                        .build()
                    cookies.add(cookie)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return cookies
    }

    fun cookiesToString(cookies: List<Cookie>): String {
        return cookies
            .distinctBy { it.name }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun getCookieValue(cookies: List<Cookie>, name: String): String? {
        return cookies.firstOrNull { it.name == name }?.value
    }

    /**
     * 检查是否包含登录所需的关键 Cookie。
     */
    fun hasLoginCookies(cookies: List<Cookie>): Boolean {
        val names = cookies.map { it.name }.toSet()
        return "SESSDATA" in names && "DedeUserID" in names
    }

    fun extractUserId(cookies: List<Cookie>): Long? {
        return getCookieValue(cookies, "DedeUserID")?.toLongOrNull()
    }
}
