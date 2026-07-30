package com.biliaudio.data.network

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import com.biliaudio.util.DebugLogger

/**
 * CookieJar 适配器（照搬 BBPlayer 的 cookie 管理策略）。
 *
 * 实际存储委托给 [BiliCookieStore]（name->value 简单映射，同步落盘到 SharedPreferences）。
 *
 * 本类仅用于兼容 OkHttp CookieJar 接口——OkHttp 仍会调用 saveFromResponse/loadForRequest，
 * 但实际的 Cookie 头注入由 [CookieInterceptor] 手动完成（等价于 BBPlayer 的
 * `credentials: 'omit'` + 手动 Cookie 头），不依赖 loadForRequest 的返回值。
 *
 * 保留 CookieJar 仅为让 OkHttp 不报错（CookieJar 是可选的，但有些内部逻辑可能检查它的存在）。
 */
class BiliCookieJar(context: Context) : CookieJar {

    val store: BiliCookieStore = BiliCookieStore(context.applicationContext)

    var onCookiesUpdated: ((List<Cookie>) -> Unit)? = null

    init {
        try {
            store.ensureBuvid3()
            DebugLogger.d("CookieJar", "init: cookieNames=${store.getCookieMap().keys}")
        } catch (e: Throwable) {
            DebugLogger.e("CookieJar", "init 失败", e)
            e.printStackTrace()
        }
    }

    /**
     * OkHttp 收到 Set-Cookie 响应头时回调。
     * 委托给 [BiliCookieStore] 合并持久化。
     */
    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val setCookieHeaders = cookies.map { "${it.name}=${it.value}" }
        store.mergeFromSetCookieHeaders(setCookieHeaders)
        notifyUpdated()
    }

    /**
     * OkHttp 发起请求时回调。
     * 注意：实际 Cookie 注入由 [CookieInterceptor] 完成，这里返回空列表
     * 避免重复注入（OkHttp 会把 loadForRequest 的结果加到 Cookie 头）。
     */
    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return emptyList()
    }

    /** 合并 cookie（从二维码登录 URL 提取后调用）。 */
    @Synchronized
    fun mergeCookies(cookies: List<Cookie>) {
        val map = cookies.associate { it.name to it.value }
        store.setCookieMap(map)
        notifyUpdated()
    }

    /** 合并 cookie（name->value 映射，照搬 BBPlayer updateBilibiliCookie）。 */
    @Synchronized
    fun mergeCookieMap(cookies: Map<String, String>) {
        store.setCookieMap(cookies)
        notifyUpdated()
    }

    /** 从 Set-Cookie 响应头合并 cookie（照搬 BBPlayer 二维码登录流程）。 */
    @Synchronized
    fun mergeFromSetCookieHeaders(headers: List<String>) {
        store.mergeFromSetCookieHeaders(headers)
        notifyUpdated()
    }

    fun getAllCookies(): List<Cookie> {
        return store.getCookieMap().map { (name, value) ->
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(".bilibili.com")
                .path("/")
                .build()
        }
    }

    fun clearCookies() {
        store.clearLoginCookies()
        notifyUpdated()
    }

    fun hasLoginCookiesFromDisk(): Boolean = store.hasLoginCookies()

    fun getCurrentUserId(): Long? = store.getUserId()

    private fun notifyUpdated() {
        onCookiesUpdated?.invoke(getAllCookies())
    }
}
