package com.biliaudio.data.network

import android.content.Context
import android.content.SharedPreferences
import com.biliaudio.util.DebugLogger
import java.util.UUID

/**
 * Cookie 持久化存储（照搬 BBPlayer 的 cookie 管理策略）。
 *
 * 设计要点（与 BBPlayer useAppStore + MMKV 一致）：
 * 1. Cookie 是登录态的**唯一**数据源，存为 name->value 的简单映射。
 * 2. 所有读写都用 [SharedPreferences.commit]（同步），等价于 BBPlayer 的同步 MMKV。
 * 3. 不依赖 OkHttp CookieJar 的自动机制（domain/path/expires 等），
 *    仅存 name->value，请求时由 [CookieInterceptor] 手动注入 Cookie 头
 *    （等价于 BBPlayer 的 `credentials: 'omit'` + 手动 Cookie 头）。
 * 4. 登录态判断 [hasLoginCookies] 直接读磁盘，不做网络验证。
 *
 * 解决「划掉后台登录失效」：同步落盘保证进程被强杀时数据已持久化；
 * 简单 name->value 存储避免 OkHttp Cookie 对象的 domain/expires 时序问题。
 */
class BiliCookieStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bili_cookies", Context.MODE_PRIVATE)

    /**
     * 从磁盘读取所有 cookie，返回 name->value 映射。
     * 同步操作，安全在主线程调用。
     */
    fun getCookieMap(): Map<String, String> {
        val cookieString = prefs.getString(KEY_COOKIES, "") ?: ""
        return parseCookieString(cookieString)
    }

    /**
     * 获取用于 HTTP 请求头的 cookie 字符串（name=value; name=value）。
     */
    fun getCookieHeader(): String {
        val map = getCookieMap()
        if (map.isEmpty()) return ""
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * 从 Set-Cookie 响应头合并更新 cookie（照搬 BBPlayer updateBilibiliCookie）。
     *
     * @param setCookieHeaders Set-Cookie 响应头的值列表（一个响应可能有多个 Set-Cookie 头）
     */
    @Synchronized
    fun mergeFromSetCookieHeaders(setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        val current = getCookieMap().toMutableMap()
        for (header in setCookieHeaders) {
            val parsed = parseSetCookieHeader(header)
            for ((name, value) in parsed) {
                if (value.isNotEmpty()) {
                    current[name] = value
                } else {
                    current.remove(name)
                }
            }
        }
        saveCookieMap(current)
        DebugLogger.d("CookieStore", "mergeFromSetCookieHeaders: 保存 ${current.size} 个 cookie, names=${current.keys}")
    }

    /**
     * 直接设置 cookie 映射（用于二维码登录从 crossDomain URL 提取的 cookie）。
     * 同步落盘。
     */
    @Synchronized
    fun setCookieMap(cookies: Map<String, String>) {
        val merged = getCookieMap().toMutableMap()
        for ((k, v) in cookies) {
            merged[k] = v
        }
        saveCookieMap(merged)
        DebugLogger.d("CookieStore", "setCookieMap: 保存 ${merged.size} 个 cookie, names=${merged.keys}")
    }

    /**
     * 直接读取磁盘判断是否含有登录 Cookie（SESSDATA + DedeUserID）。
     * 这是登录态判定的权威来源，不做网络验证。
     */
    fun hasLoginCookies(): Boolean {
        val map = getCookieMap()
        val has = map.containsKey("SESSDATA") && map.containsKey("DedeUserID")
        DebugLogger.d("CookieStore", "hasLoginCookies: $has, diskNames=${map.keys}")
        return has
    }

    /** 获取 CSRF token（bili_jct），用于需要 POST 的接口。 */
    fun getCsrfToken(): String? = getCookieMap()["bili_jct"]

    /** 获取当前用户 ID（DedeUserID）。 */
    fun getUserId(): Long? = getCookieMap()["DedeUserID"]?.toLongOrNull()

    /** 清除所有登录 Cookie（退出登录）。保留 buvid3。 */
    @Synchronized
    fun clearLoginCookies() {
        // 退出登录无强一致需求，用 apply 异步落盘避免阻塞主线程
        prefs.edit().remove(KEY_COOKIES).apply()
        ensureBuvid3()
        DebugLogger.d("CookieStore", "clearLoginCookies: 已清除登录 cookie")
    }

    /**
     * 确保 buvid3 存在（风控需要）。单独存储，不随登录 Cookie 一起清除。
     */
    @Synchronized
    fun ensureBuvid3() {
        val current = getCookieMap()
        if (current.containsKey("buvid3") && current["buvid3"]!!.isNotEmpty()) return

        val buvid3 = prefs.getString(KEY_BUVID3, null) ?: run {
            val generated = UUID.randomUUID().toString().uppercase()
            // buvid3 非登录态关键数据，用 apply 异步落盘
            prefs.edit().putString(KEY_BUVID3, generated).apply()
            generated
        }

        val merged = current.toMutableMap()
        merged["buvid3"] = buvid3
        saveCookieMap(merged)
    }

    private fun saveCookieMap(map: Map<String, String>) {
        val cookieString = map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        prefs.edit().putString(KEY_COOKIES, cookieString).commit()
    }

    companion object {
        private const val KEY_COOKIES = "cookies"
        private const val KEY_BUVID3 = "buvid3"

        /**
         * 解析 cookie 字符串（name=value; name=value）为映射。
         */
        fun parseCookieString(cookieString: String): Map<String, String> {
            if (cookieString.isEmpty()) return emptyMap()
            val map = LinkedHashMap<String, String>()
            for (pair in cookieString.split(";")) {
                val trimmed = pair.trim()
                if (trimmed.isEmpty()) continue
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val name = trimmed.substring(0, eq).trim()
                    val value = trimmed.substring(eq + 1).trim()
                    if (name.isNotEmpty()) {
                        map[name] = value
                    }
                }
            }
            return map
        }

        /**
         * 解析单个 Set-Cookie 响应头值为 name->value 映射。
         * Set-Cookie: SESSDATA=xxx; Path=/; Domain=.bilibili.com; HttpOnly
         * 只提取 name=value，忽略属性（Path/Domain/Expires 等）。
         */
        fun parseSetCookieHeader(header: String): Map<String, String> {
            if (header.isEmpty()) return emptyMap()
            val map = LinkedHashMap<String, String>()
            // Set-Cookie 头可能包含多个 cookie（用逗号分隔，但日期中也含逗号，需小心）
            // 简单处理：只取第一个分号前的 name=value
            val firstPart = header.substringBefore(";").trim()
            val eq = firstPart.indexOf('=')
            if (eq > 0) {
                val name = firstPart.substring(0, eq).trim()
                val value = firstPart.substring(eq + 1).trim()
                if (name.isNotEmpty()) {
                    map[name] = value
                }
            }
            return map
        }
    }
}
