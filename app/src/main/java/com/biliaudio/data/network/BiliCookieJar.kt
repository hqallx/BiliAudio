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
        val host = url.host
        // 所有 bilibili.com 子域共享同一组 Cookie，
        // 因为登录成功后 Set-Cookie 的 Domain 通常是 .bilibili.com。
        return if (host.endsWith("bilibili.com")) {
            getAllCookies().distinctBy { it.name }
        } else {
            cookieStore[host] ?: emptyList()
        }
    }

    fun setCookies(cookies: List<Cookie>) {
        cookieStore.clear()
        cookieStore["bilibili.com"] = cookies.toMutableList()
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
