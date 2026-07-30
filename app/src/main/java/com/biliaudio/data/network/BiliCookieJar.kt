package com.biliaudio.data.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import com.biliaudio.util.DebugLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 持久化 CookieJar：使用 SharedPreferences 同步读写 Cookie，
 * 避免在主线程使用 runBlocking 读取 DataStore 导致崩溃。
 *
 * 内部使用 ConcurrentHashMap 保证线程安全——OkHttp 的 loadForRequest /
 * saveFromResponse 可能在多线程并发调用（如同时发起 getUserInfo 和
 * getFavoriteFolders），非线程安全 Map 会抛 ConcurrentModificationException
 * 导致应用闪退（尤见于「登录后杀进程重进」场景）。
 */
class BiliCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bili_cookies", Context.MODE_PRIVATE)

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    var onCookiesUpdated: ((List<Cookie>) -> Unit)? = null

    init {
        try {
            restore()
            DebugLogger.d("CookieJar", "init restore 完成，cookie 数=${getAllCookies().size}, names=${getAllCookies().map { it.name }}")
        } catch (e: Throwable) {
            // Cookie 恢复失败不应阻塞应用启动。
            // 清除损坏的 Cookie 数据，避免下次启动再次崩溃。
            DebugLogger.e("CookieJar", "init restore 失败", e)
            e.printStackTrace()
            prefs.edit().remove("cookies").apply()
            cookieStore.clear()
        }
        try {
            ensureBuvid3()
        } catch (e: Throwable) {
            // buvid3 生成失败不应阻塞应用启动
            DebugLogger.e("CookieJar", "ensureBuvid3 失败", e)
            e.printStackTrace()
        }
    }

    @Synchronized
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

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        // 所有 bilibili.com 子域共享同一组 Cookie，
        // 因为登录成功后 Set-Cookie 的 Domain 通常是 .bilibili.com。
        return if (host.endsWith("bilibili.com")) {
            getAllCookies().distinctBy { it.name }
        } else {
            cookieStore[host]?.toList() ?: emptyList()
        }
    }

    @Synchronized
    fun setCookies(cookies: List<Cookie>) {
        cookieStore.clear()
        cookieStore["bilibili.com"] = cookies.toMutableList()
        ensureBuvid3()
        persist(sync = true)
        onCookiesUpdated?.invoke(getAllCookies())
    }

    /**
     * 合并 Cookie（不清除已有的，同 name 覆盖）。
     * 用于从二维码登录 URL 或 WebView CookieManager 同步 Cookie 到 OkHttp CookieJar。
     *
     * 登录成功是关键路径，使用同步落盘（commit），避免应用被强杀时
     * apply() 异步写盘未完成导致 Cookie 丢失、下次启动显示未登录。
     */
    @Synchronized
    fun mergeCookies(cookies: List<Cookie>) {
        val store = cookieStore.getOrPut("bilibili.com") { mutableListOf() }
        for (incoming in cookies) {
            store.removeAll { it.name == incoming.name }
            store.add(incoming)
        }
        persist(sync = true)
        onCookiesUpdated?.invoke(getAllCookies())
    }

    @Synchronized
    fun getAllCookies(): List<Cookie> = cookieStore.values.flatten()

    @Synchronized
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
     * 将 Cookie 持久化到 SharedPreferences。
     * 注意：buvid3 单独存储在 "buvid3" key，不写入 "cookies"，避免 clearCookies 时丢失。
     *
     * @param sync true 时用 commit() 同步写盘（登录等关键路径，防强杀丢失）；
     *             false 时用 apply() 异步写盘（高频的 saveFromResponse，保性能）。
     */
    private fun persist(sync: Boolean = false) {
        val cookieString = CookieHelper.cookiesToString(
            getAllCookies().filter { it.name != "buvid3" }
        )
        val editor = prefs.edit().putString("cookies", cookieString)
        if (sync) editor.commit() else editor.apply()
    }
}
