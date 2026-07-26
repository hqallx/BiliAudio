package com.biliaudio.di

import android.content.Context
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.network.BiliCookieJar
import com.biliaudio.data.network.NetworkModule
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
}
