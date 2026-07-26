package com.biliaudio.data.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

object CookieHelper {

    private val VALID_COOKIE_KEYS = setOf(
        "SESSDATA", "bili_jct", "DedeUserID", "sid",
        "buvid3", "b_lsid", "_uuid", "CURRENT_FNVAL",
        "rpdid", "LIVE_BUVID"
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
                    val cookie = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(".bilibili.com")
                        .path("/")
                        .httpOnly()
                        .secure()
                        .build()
                    cookies.add(cookie)
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
