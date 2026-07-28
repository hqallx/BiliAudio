package com.biliaudio.data.repository

import com.biliaudio.data.Result
import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.CaptchaResponse
import com.biliaudio.data.model.LoginResponse
import com.biliaudio.data.model.QrCodeResponse
import com.biliaudio.data.model.QrCodeStatusData
import com.biliaudio.data.model.UserInfo
import com.biliaudio.data.model.WebKeyResponse
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.network.BiliCookieJar
import com.biliaudio.data.network.BiliPassportApi
import com.biliaudio.data.network.CookieHelper
import com.biliaudio.data.resultOf
import com.biliaudio.ui.components.GeeTestResult
import com.biliaudio.util.RSAUtil
import okhttp3.Cookie
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: BiliApi,
    private val passportApi: BiliPassportApi,
    private val cookieJar: BiliCookieJar
) {

    var onCookiesUpdated: ((List<Cookie>) -> Unit)?
        get() = cookieJar.onCookiesUpdated
        set(value) { cookieJar.onCookiesUpdated = value }

    // ============ 二维码登录 ============

    suspend fun generateQrCode(): Result<BiliResponse<QrCodeResponse>> = resultOf {
        passportApi.generateQrCode()
    }

    suspend fun checkQrCodeStatus(qrcodeKey: String): Result<BiliResponse<QrCodeStatusData>> = resultOf {
        passportApi.checkQrCodeStatus(qrcodeKey)
    }

    // ============ 密码登录 ============

    suspend fun getWebKey(): Result<BiliResponse<WebKeyResponse>> = resultOf {
        passportApi.getWebKey()
    }

    suspend fun getCaptcha(): Result<BiliResponse<CaptchaResponse>> = resultOf {
        passportApi.getCaptcha()
    }

    suspend fun loginWithPassword(
        username: String,
        password: String,
        webKey: WebKeyResponse,
        captcha: CaptchaResponse,
        geeTestResult: GeeTestResult
    ): Result<BiliResponse<LoginResponse>> = resultOf {
        val encryptedPassword = RSAUtil.encryptPassword(password, webKey.hash, webKey.key)
            ?: throw IllegalStateException("密码加密失败")
        passportApi.loginWithPassword(
            username = username,
            encryptedPassword = encryptedPassword,
            keyHash = webKey.hash,
            recaptchaToken = captcha.recaptchaToken,
            geeSeccode = geeTestResult.seccode,
            geeValidate = geeTestResult.validate,
            geeChallenge = geeTestResult.challenge
        )
    }

    // ============ 通用 ============

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
