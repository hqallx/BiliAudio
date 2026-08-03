package com.biliaudio.data.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cookie 拦截器（照搬 BBPlayer 的 `credentials: 'omit'` + 手动 Cookie 头策略）。
 *
 * - 请求时：从 [BiliCookieStore] 读取 cookie 字符串，手动注入 Cookie 请求头。
 *   （等价于 BBPlayer ApiClient 中 `headers.set('Cookie', cookie)`）
 * - 响应时：不在此处理 Set-Cookie（由 OkHttp CookieJar 的 saveFromResponse 自动捕获，
 *   并委托给 BiliCookieStore 持久化）。
 *
 * 这样不依赖 OkHttp loadForRequest 的返回值（CookieJar.loadForRequest 返回空列表），
 * 完全自己管理 Cookie 注入，避免 domain/path 匹配导致的 cookie 丢失问题。
 */
@Singleton
class CookieInterceptor @Inject constructor(
    private val cookieJar: BiliCookieJar
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cookieHeader = cookieJar.store.getCookieHeader()

        val newRequest = if (cookieHeader.isNotEmpty()) {
            request.newBuilder()
                .header("Cookie", cookieHeader)
                .build()
        } else {
            request
        }

        return chain.proceed(newRequest)
    }
}
