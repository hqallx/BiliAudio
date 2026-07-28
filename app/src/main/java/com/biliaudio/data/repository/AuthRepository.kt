package com.biliaudio.data.repository

import com.biliaudio.data.Result
import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.CaptchaResponse
import com.biliaudio.data.model.NavInfo
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

    /**
     * 获取当前登录用户信息。
     * 使用 x/web-interface/nav 接口（基于 Cookie，无需 WBI 签名），
     * 返回 NavInfo 并映射为 UserInfo。
     *
     * @return Result 包含 UserInfo（登录成功时）或 null（未登录/接口返回 isLogin=false）
     */
    suspend fun getUserInfo(): Result<UserInfo?> = resultOf {
        val resp = api.getNavInfo()
        if (resp.code == 0 && resp.data != null) {
            resp.data.toUserInfo()
        } else {
            null
        }
    }

    fun clearCookies() {
        cookieJar.clearCookies()
    }

    /**
     * 从二维码登录成功后返回的 crossDomain URL 中提取登录 Cookie 并保存。
     *
     * WEB 二维码流程（/x/passport-login/web/qrcode/poll）登录成功时，
     * 登录 Cookie（SESSDATA、DedeUserID、bili_jct 等）位于 data.url 的
     * 查询参数中，而非 Set-Cookie 响应头。必须手动提取才能完成登录态持久化。
     */
    fun saveCookiesFromLoginUrl(url: String) {
        val cookies = CookieHelper.extractCookiesFromUrl(url)
        if (cookies.isNotEmpty()) {
            cookieJar.mergeCookies(cookies)
        }
    }

    fun isLoggedIn(): Boolean {
        return CookieHelper.hasLoginCookies(cookieJar.getAllCookies())
    }

    fun getCurrentUserId(): Long? {
        return CookieHelper.extractUserId(cookieJar.getAllCookies())
    }
}
