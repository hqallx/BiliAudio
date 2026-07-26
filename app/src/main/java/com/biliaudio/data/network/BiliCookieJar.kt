package com.biliaudio.data.network

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import com.biliaudio.data.preferences.PreferencesManager

/**
 * 持久化 CookieJar：登录 Cookie 自动持久化到 DataStore，
 * 应用启动时通过 [restore] 从 DataStore 恢复，避免每次重启都要重新登录。
 */
class BiliCookieJar(private val context: Context) : CookieJar {

    private val preferencesManager by lazy { PreferencesManager(context) }

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
        persistAsync()
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
        persistAsync()
        onCookiesUpdated?.invoke(cookies)
    }

    fun getAllCookies(): List<Cookie> = cookieStore.values.flatten()

    fun clearCookies() {
        cookieStore.clear()
        runBlocking { preferencesManager.saveCookies("") }
        onCookiesUpdated?.invoke(emptyList())
    }

    /**
     * 从 DataStore 恢复之前保存的 Cookie。
     */
    private fun restore() {
        val cookieString = runBlocking { preferencesManager.cookies.first() }
        if (cookieString.isNotEmpty()) {
            val cookies = CookieHelper.parseCookies(cookieString)
            cookieStore["bilibili.com"] = cookies.toMutableList()
            cookieStore["api.bilibili.com"] = cookies.toMutableList()
        }
    }

    private fun persistAsync() {
        val cookieString = CookieHelper.cookiesToString(getAllCookies())
        runBlocking { preferencesManager.saveCookies(cookieString) }
    }
}
