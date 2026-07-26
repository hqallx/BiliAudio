package com.biliaudio.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

object NetworkModule {

    private var cookieJar: BiliCookieJar? = null

    fun provideCookieJar(): BiliCookieJar {
        if (cookieJar == null) {
            cookieJar = BiliCookieJar()
        }
        return cookieJar!!
    }

    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val headerInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val request = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .header("Origin", "https://www.bilibili.com")
                .build()
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .cookieJar(provideCookieJar())
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        coerceInputValues = true
    }

    fun provideBiliApi(): BiliApi {
        val contentType = "application/json; charset=utf-8".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(provideOkHttpClient())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(BiliApi::class.java)
    }
}

class BiliCookieJar : CookieJar {

    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    var onCookiesUpdated: ((List<Cookie>) -> Unit)? = null

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        cookieStore[host] = cookies
        onCookiesUpdated?.invoke(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        return cookieStore[host] ?: emptyList()
    }

    fun setCookies(cookies: List<Cookie>) {
        cookieStore["bilibili.com"] = cookies
        cookieStore["api.bilibili.com"] = cookies
    }

    fun getCookies(): List<Cookie> {
        return cookieStore.values.flatten()
    }

    fun clearCookies() {
        cookieStore.clear()
    }
}
