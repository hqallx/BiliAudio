package com.biliaudio.di

import android.content.Context
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.network.BiliCookieJar
import com.biliaudio.data.network.BiliPassportApi
import com.biliaudio.data.network.NetworkModule
import com.biliaudio.data.network.WbiSigner
import com.biliaudio.data.preferences.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideCookieJar(@ApplicationContext context: Context): BiliCookieJar {
        NetworkModule.init(context)
        return NetworkModule.provideCookieJar()
    }

    /**
     * 返回与 OkHttp 客户端共享的同一个 [WbiSigner] 实例，
     * 保证 AuthRepository 写入的 WBI 密钥与拦截器读取的是同一份缓存。
     */
    @Provides
    @Singleton
    fun provideWbiSigner(@ApplicationContext context: Context): WbiSigner {
        NetworkModule.init(context)
        return NetworkModule.provideWbiSigner()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: BiliCookieJar): OkHttpClient {
        return NetworkModule.provideOkHttpClient()
    }

    @Provides
    @Singleton
    fun provideBiliApi(client: OkHttpClient): BiliApi {
        return NetworkModule.provideBiliApi()
    }

    @Provides
    @Singleton
    fun provideBiliPassportApi(client: OkHttpClient): BiliPassportApi {
        return NetworkModule.providePassportApi()
    }
}
