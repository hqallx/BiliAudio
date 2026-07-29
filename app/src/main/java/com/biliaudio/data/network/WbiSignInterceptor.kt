package com.biliaudio.data.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp 拦截器：对需要 WBI 签名的接口自动附加 `wts` 与 `w_rid` 参数。
 *
 * 触发条件：
 *  1. 请求路径匹配 [WBI_REQUIRED_PATHS] 中的任一前缀
 *  2. [WbiSigner] 已缓存签名密钥（由 nav 接口成功后写入）
 *
 * 若密钥尚未就绪（如 nav 接口未成功），请求原样发出，由服务端返回 -403，
 * 上层可据此提示用户稍后重试。此设计避免了拦截器内做网络请求导致的递归问题。
 */
@Singleton
class WbiSignInterceptor @Inject constructor(
    private val wbiSigner: WbiSigner
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val path = url.encodedPath

        val needsWbi = WBI_REQUIRED_PATHS.any { path.contains(it) }
        if (!needsWbi || !wbiSigner.hasKey()) {
            return chain.proceed(request)
        }

        // 收集 Retrofit 已拼好的查询参数（@Query 注入的值）。
        // OkHttp 的 queryParameterName/queryParameterValue 返回 String?，需判空。
        val params = LinkedHashMap<String, String>(url.querySize)
        for (i in 0 until url.querySize) {
            val name = url.queryParameterName(i) ?: continue
            val value = url.queryParameterValue(i) ?: ""
            if (name.isNotEmpty()) {
                params[name] = value
            }
        }

        val signed = wbiSigner.sign(params)
        val newUrl = buildSignedUrl(url, signed)
        val newRequest = request.newBuilder().url(newUrl).build()
        return chain.proceed(newRequest)
    }

    /**
     * 用签名后的参数重建 URL。
     * OkHttp 的 `setQueryParameter` 会对已存在的 key 做替换、对新 key 做追加，
     * 因此先清空原查询参数再用签名结果覆盖，避免残留旧值导致签名不匹配。
     */
    private fun buildSignedUrl(original: HttpUrl, signed: Map<String, String>): HttpUrl {
        val builder = original.newBuilder()
        // 先移除原有查询参数
        val originalNames = (0 until original.querySize)
            .mapNotNull { original.queryParameterName(it) }
            .distinct()
        for (name in originalNames) {
            builder.removeAllQueryParameters(name)
        }
        // 写入签名后的完整参数（含 wts / w_rid）
        for ((name, value) in signed) {
            builder.addQueryParameter(name, value)
        }
        return builder.build()
    }

    companion object {
        /**
         * 需要附加 WBI 签名的接口路径片段。
         * 仅对路径含 /wbi/ 的接口签名（B站惯例：WBI 接口路径带 wbi 段）。
         * seasons_series / seasons_archives_list 等 polymer 接口路径不含 /wbi/，
         * 其 w_rid 参数为「可选」，不签名也能正常访问；强制签名反而会在
         * WBI 密钥未就绪（冷启动竞态）时导致请求失败。
         */
        private val WBI_REQUIRED_PATHS = arrayOf(
            "wbi/playurl"
        )
    }
}
