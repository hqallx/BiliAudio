package com.biliaudio.data.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 持久化 CookieJar：使用 SharedPreferences 同步读写 Cookie，
 * 避免在主线程使用 runBlocking 读取 DataStore 导致崩溃。
 */
class BiliCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bili_cookies", Context.MODE_PRIVATE)

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    var onCookiesUpdated: ((List<Cookie>) -> Unit)? = null

    init {
        restore()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val store = cookieStore.getOrPut(host) { mutableListOf() }
        // 合并：同 name 的 cookie 覆盖旧值
        for (incoming in cookies) {
            store.removeAll { it.name == incoming.name }
            store.add(incoming)
        }
        cookieStore[host] = store
        persist()
        onCookiesUpdated?.invoke(getAllCookies())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // bilibili.com 和 api.bilibili.com 共享同一组 cookie
        val host = url.host
        val direct = cookieStore[host] ?: emptyList()
        val shared = cookieStore["bilibili.com"] ?: emptyList()
        return (direct + shared).distinctBy { it.name }
    }

    fun setCookies(cookies: List<Cookie>) {
        cookieStore.clear()
        cookieStore["bilibili.com"] = cookies.toMutableList()
        cookieStore["api.bilibili.com"] = cookies.toMutableList()
        persist()
        onCookiesUpdated?.invoke(cookies)
    }

    fun getAllCookies(): List<Cookie> = cookieStore.values.flatten()

    fun clearCookies() {
        cookieStore.clear()
        prefs.edit().remove("cookies").apply()
        onCookiesUpdated?.invoke(emptyList())
    }

    /**
     * 从 SharedPreferences 恢复之前保存的 Cookie（同步操作，安全在主线程调用）。
     */
    private fun restore() {
        val cookieString = prefs.getString("cookies", "") ?: ""
        if (cookieString.isNotEmpty()) {
            val cookies = CookieHelper.parseCookies(cookieString)
            cookieStore["bilibili.com"] = cookies.toMutableList()
            cookieStore["api.bilibili.com"] = cookies.toMutableList()
        }
    }

    /**
     * 将 Cookie 持久化到 SharedPreferences（同步操作，安全在任何线程调用）。
     */
    private fun persist() {
        val cookieString = CookieHelper.cookiesToString(getAllCookies())
        prefs.edit().putString("cookies", cookieString).apply()
    }
}
