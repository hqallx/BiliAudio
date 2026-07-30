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
 * 持久化 CookieJar（参考 BBPlayer 的持久化策略）。
 *
 * 设计要点（解决「划掉后台登录失效」）：
 * 1. Cookie 是登录态的**唯一**数据源——不依赖网络验证决定登录态。
 * 2. 所有写盘都用 [SharedPreferences.commit]（同步），保证进程被强杀时数据已落盘。
 *    （BBPlayer 使用同步的 MMKV；这里用 commit() 达到同等效果。）
 * 3. 持久化/加载时过滤已过期的 Cookie，避免恢复死 Cookie 导致后续请求 -101。
 * 4. [hasLoginCookiesFromDisk] 直接读磁盘，作为登录态判定的权威来源，
 *    不依赖内存 store（消除任何内存与磁盘不一致的时序问题）。
 *
 * 内部使用 ConcurrentHashMap 保证线程安全——OkHttp 的 loadForRequest /
 * saveFromResponse 可能在多线程并发调用。
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
            prefs.edit().remove("cookies").commit()
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
        // 合并：同 name 的 cookie 覆盖旧值；
        // 过滤已过期的 incoming cookie（expiresAt <= now 视为删除指令，移除旧值不写入新值）
        val now = System.currentTimeMillis()
        for (incoming in cookies) {
            store.removeAll { it.name == incoming.name }
            if (incoming.expiresAt > now) {
                store.add(incoming)
            }
        }
        cookieStore[host] = store
        // 同步落盘：避免 apply() 异步写盘未完成时进程被杀导致 Cookie 丢失
        persist(sync = true)
        onCookiesUpdated?.invoke(getAllCookies())
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val now = System.currentTimeMillis()
        // 所有 bilibili.com 子域共享同一组 Cookie，
        // 因为登录成功后 Set-Cookie 的 Domain 通常是 .bilibili.com。
        // 过滤已过期 Cookie，避免发送失效凭证触发 -101。
        return if (host.endsWith("bilibili.com")) {
            getAllCookies().filter { it.expiresAt > now }.distinctBy { it.name }
        } else {
            (cookieStore[host] ?: emptyList()).filter { it.expiresAt > now }
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

    /**
     * 直接从磁盘 SharedPreferences 读取并判断是否含有登录 Cookie。
     *
     * 这是登录态判定的**权威来源**：不依赖内存 cookieStore，
     * 消除「内存有但磁盘没有」或「进程重启内存未恢复」的时序问题。
     * 只要磁盘上存有 SESSDATA + DedeUserID，即视为已登录（参考 BBPlayer）。
     */
    fun hasLoginCookiesFromDisk(): Boolean {
        val cookieString = prefs.getString("cookies", "") ?: ""
        if (cookieString.isEmpty()) return false
        val cookies = CookieHelper.parseCookies(cookieString)
        val has = CookieHelper.hasLoginCookies(cookies)
        DebugLogger.d("CookieJar", "hasLoginCookiesFromDisk: $has, diskNames=${cookies.map { it.name }}")
        return has
    }

    @Synchronized
    fun clearCookies() {
        cookieStore.clear()
        prefs.edit().remove("cookies").commit()
        // 清除登录态后仍保留 buvid3，避免风控
        ensureBuvid3()
        onCookiesUpdated?.invoke(getAllCookies())
    }

    /**
     * 从 SharedPreferences 恢复之前保存的 Cookie（同步操作，安全在主线程调用）。
     * 恢复时过滤已过期的 Cookie，避免恢复死 Cookie。
     */
    private fun restore() {
        val cookieString = prefs.getString("cookies", "") ?: ""
        if (cookieString.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val cookies = CookieHelper.parseCookies(cookieString).filter { it.expiresAt > now }
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
            prefs.edit().putString("buvid3", generated).commit()
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
     * 持久化时过滤已过期的 Cookie，避免存入死 Cookie。
     *
     * @param sync 始终使用 commit() 同步写盘，保证进程被强杀时数据不丢。
     *             （保留参数仅为向后兼容，内部一律 commit。）
     */
    private fun persist(sync: Boolean = true) {
        val now = System.currentTimeMillis()
        val cookieString = CookieHelper.cookiesToString(
            getAllCookies().filter { it.name != "buvid3" && it.expiresAt > now }
        )
        prefs.edit().putString("cookies", cookieString).commit()
    }
}
