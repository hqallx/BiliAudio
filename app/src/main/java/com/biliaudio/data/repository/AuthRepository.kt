package com.biliaudio.data.repository

import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.*
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.network.BiliCookieJar
import com.biliaudio.data.network.CookieHelper
import com.biliaudio.data.resultOf
import okhttp3.Cookie
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: BiliApi,
    private val cookieJar: BiliCookieJar
) {

    var onCookiesUpdated: ((List<Cookie>) -> Unit)?
        get() = cookieJar.onCookiesUpdated
        set(value) { cookieJar.onCookiesUpdated = value }

    suspend fun generateQrCode(): Result<BiliResponse<QrCodeResponse>> = resultOf {
        api.generateQrCode()
    }

    suspend fun checkQrCodeStatus(qrcodeKey: String): Result<BiliResponse<QrCodeStatusData>> = resultOf {
        api.checkQrCodeStatus(qrcodeKey)
    }

    suspend fun getUserInfo(): Result<BiliResponse<UserInfo>> = resultOf {
        api.getUserInfo()
    }

    fun clearCookies() {
        cookieJar.clearCookies()
    }

    fun isLoggedIn(): Boolean {
        return CookieHelper.hasLoginCookies(cookieJar.getAllCookies())
    }

    fun getCurrentUserId(): Long? {
        return CookieHelper.extractUserId(cookieJar.getAllCookies())
    }
}
