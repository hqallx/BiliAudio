package com.biliaudio.data.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.UUID

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
        try {
            restore()
        } catch (e: Throwable) {
            // Cookie 恢复失败不应阻塞应用启动。
            // 清除损坏的 Cookie 数据，避免下次启动再次崩溃。
            e.printStackTrace()
            prefs.edit().remove("cookies").apply()
            cookieStore.clear()
        }
        try {
            ensureBuvid3()
        } catch (e: Throwable) {
            // buvid3 生成失败不应阻塞应用启动
            e.printStackTrace()
        }
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
        ensureBuvid3()
        persist()
        onCookiesUpdated?.invoke(getAllCookies())
    }

    fun getAllCookies(): List<Cookie> = cookieStore.values.flatten()

    fun clearCookies() {
        cookieStore.clear()
        prefs.edit().remove("cookies").apply()
        // 清除登录态后仍保留 buvid3，避免风控
        ensureBuvid3()
        onCookiesUpdated?.invoke(getAllCookies())
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
     * 确保存在 buvid3 Cookie。bilibili 的登录接口（二维码生成、短信发送等）
     * 越来越依赖 buvid3 做风控，缺失会导致请求被风控拦截。
     * 本地生成一个 UUID 格式的 buvid3 即可被服务端接受。
     */
    private fun ensureBuvid3() {
        val existing = getAllCookies().firstOrNull { it.name == "buvid3" }
        if (existing != null && existing.value.isNotEmpty()) return

        val buvid3 = prefs.getString("buvid3", null) ?: run {
            // 生成 UUID 格式 buvid3（小写带连字符）
            val generated = UUID.randomUUID().toString().uppercase()
            prefs.edit().putString("buvid3", generated).apply()
            generated
        }

        val cookie = Cookie.Builder()
            .name("buvid3")
            .value(buvid3)
            .domain(".bilibili.com")
            .path("/")
            .build()

        val store = cookieStore.getOrPut("bilibili.com") { mutableListOf() }
        store.removeAll { it.name == "buvid3" }
        store.add(cookie)
    }

    /**
     * 将 Cookie 持久化到 SharedPreferences（同步操作，安全在任何线程调用）。
     * 注意：buvid3 单独存储在 "buvid3" key，不写入 "cookies"，避免 clearCookies 时丢失。
     */
    private fun persist() {
        val cookieString = CookieHelper.cookiesToString(
            getAllCookies().filter { it.name != "buvid3" }
        )
        prefs.edit().putString("cookies", cookieString).apply()
    }
}
