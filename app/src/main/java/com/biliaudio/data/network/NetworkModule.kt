package com.biliaudio.data.network

import android.content.Context
import com.biliaudio.data.BiliConstants
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

object NetworkModule {

    @Volatile
    private var cookieJar: BiliCookieJar? = null

    @Volatile
    private var wbiSigner: WbiSigner? = null

    @Volatile
    private var api: BiliApi? = null

    @Volatile
    private var passportApi: BiliPassportApi? = null

    fun init(context: Context) {
        if (cookieJar == null) {
            synchronized(this) {
                if (cookieJar == null) {
                    cookieJar = BiliCookieJar(context.applicationContext)
                }
            }
        }
        if (wbiSigner == null) {
            synchronized(this) {
                if (wbiSigner == null) {
                    wbiSigner = WbiSigner()
                }
            }
        }
    }

    fun provideCookieJar(): BiliCookieJar {
        checkNotNull(cookieJar) { "NetworkModule.init() must be called first" }
        return cookieJar!!
    }

    fun provideWbiSigner(): WbiSigner {
        checkNotNull(wbiSigner) { "NetworkModule.init() must be called first" }
        return wbiSigner!!
    }

    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val headerInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val request = originalRequest.newBuilder()
                .header("User-Agent", BiliConstants.USER_AGENT)
                .header("Referer", BiliConstants.WEB_BASE_URL + "/")
                .header("Origin", BiliConstants.WEB_BASE_URL)
                .build()
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .cookieJar(provideCookieJar())
            .addInterceptor(headerInterceptor)
            .addInterceptor(WbiSignInterceptor(provideWbiSigner()))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        coerceInputValues = true
    }

    fun provideBiliApi(): BiliApi {
        api?.let { return it }
        synchronized(this) {
            api ?: run {
                val contentType = "application/json; charset=utf-8".toMediaType()
                api = Retrofit.Builder()
                    .baseUrl(BiliConstants.BASE_URL)
                    .client(provideOkHttpClient())
                    .addConverterFactory(json.asConverterFactory(contentType))
                    .build()
                    .create(BiliApi::class.java)
            }
        }
        return api!!
    }

    fun providePassportApi(): BiliPassportApi {
        passportApi?.let { return it }
        synchronized(this) {
            passportApi ?: run {
                val contentType = "application/json; charset=utf-8".toMediaType()
                passportApi = Retrofit.Builder()
                    .baseUrl(BiliConstants.PASSPORT_BASE_URL)
                    .client(provideOkHttpClient())
                    .addConverterFactory(json.asConverterFactory(contentType))
                    .build()
                    .create(BiliPassportApi::class.java)
            }
        }
        return passportApi!!
    }
}
