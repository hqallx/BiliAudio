package com.biliaudio.data.repository

import com.biliaudio.data.Result
import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.CaptchaResponse
import com.biliaudio.data.model.QrCodeResponse
import com.biliaudio.data.model.QrCodeStatusData
import com.biliaudio.data.model.SmsLoginResponse
import com.biliaudio.data.model.SmsSendResponse
import com.biliaudio.data.model.UserInfo
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.network.BiliCookieJar
import com.biliaudio.data.network.BiliPassportApi
import com.biliaudio.data.network.CookieHelper
import com.biliaudio.data.resultOf
import com.biliaudio.ui.components.GeeTestResult
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

    // ============ 短信登录 ============

    suspend fun getCaptcha(): Result<BiliResponse<CaptchaResponse>> = resultOf {
        passportApi.getCaptcha(timestamp = System.currentTimeMillis())
    }

    suspend fun sendSmsCode(
        cid: String,
        tel: String,
        captcha: CaptchaResponse,
        geeTestResult: GeeTestResult
    ): Result<BiliResponse<SmsSendResponse>> = resultOf {
        passportApi.sendSmsCode(
            cid = cid,
            tel = tel,
            recaptchaToken = captcha.recaptchaToken,
            challenge = geeTestResult.challenge,
            validate = geeTestResult.validate,
            seccode = geeTestResult.seccode
        )
    }

    suspend fun loginWithSms(
        cid: String,
        tel: String,
        code: String,
        captchaKey: String
    ): Result<BiliResponse<SmsLoginResponse>> = resultOf {
        passportApi.loginWithSms(
            cid = cid,
            tel = tel,
            code = code,
            captchaKey = captchaKey
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
