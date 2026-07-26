package com.biliaudio.data.network

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import com.biliaudio.data.preferences.PreferencesManager

class CookieInterceptor(private val context: Context) : Interceptor {

    private val preferencesManager by lazy { PreferencesManager(context) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val cookieString = runBlocking {
            preferencesManager.cookies.first()
        }
        
        return if (cookieString.isNotEmpty()) {
            val request = originalRequest.newBuilder()
                .header("Cookie", cookieString)
                .build()
            chain.proceed(request)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
