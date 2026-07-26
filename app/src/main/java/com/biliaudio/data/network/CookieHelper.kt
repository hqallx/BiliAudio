package com.biliaudio.data.network

import okhttp3.Cookie
import okhttp3.HttpUrl

object CookieHelper {

    fun parseCookies(cookieString: String): List<Cookie> {
        if (cookieString.isEmpty()) return emptyList()
        
        val cookies = mutableListOf<Cookie>()
        val cookiePairs = cookieString.split(";").map { it.trim() }
        
        for (pair in cookiePairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                val cookie = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain("bilibili.com")
                    .build()
                cookies.add(cookie)
            }
        }
        
        return cookies
    }

    fun cookiesToString(cookies: List<Cookie>): String {
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }
}
