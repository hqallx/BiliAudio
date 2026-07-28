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

    /**
     * 二维码登录成功后，从返回的 url 中提取登录 Cookie 并保存。
     *
     * bilibili 的二维码 poll 接口在登录成功时，返回的 data.url 是一个
     * crossDomain 跳转链接，形如：
     * https://passport.biligame.com/x/passport-login/web/crossDomain?DedeUserID=xxx&SESSDATA=xxx&bili_jct=xxx&...
     * 登录所需的 Cookie（SESSDATA / DedeUserID / bili_jct 等）以查询参数形式
     * 附带在 URL 中，而非通过 Set-Cookie 响应头下发。
     * 如果不主动提取并保存，登录态实际上没有建立，后续所有需要登录的接口都会失败。
     */
    fun saveQrLoginCookies(url: String) {
        val cookies = CookieHelper.extractCookiesFromUrl(url)
        if (cookies.isNotEmpty()) {
            cookieJar.setCookies(cookies)
        }
    }

    // ============ 短信登录 ============

    suspend fun getCaptcha(): Result<BiliResponse<CaptchaResponse>> = resultOf {
        passportApi.getCaptcha()
    }

    suspend fun sendSmsCode(
        cid: String,
        tel: String,
        captcha: CaptchaResponse,
        geeTestResult: GeeTestResult
    ): Result<BiliResponse<SmsSendResponse>> = resultOf {
        val buvid3 = CookieHelper.getCookieValue(cookieJar.getAllCookies(), "buvid3") ?: ""
        passportApi.sendSmsCode(
            cid = cid,
            tel = tel,
            recaptchaToken = captcha.recaptchaToken,
            geeSeccode = geeTestResult.seccode,
            geeValidate = geeTestResult.validate,
            geeChallenge = geeTestResult.challenge,
            buvid3 = buvid3
        )
    }

    suspend fun loginWithSms(
        cid: String,
        tel: String,
        code: String,
        captchaKey: String,
        captcha: CaptchaResponse,
        geeTestResult: GeeTestResult
    ): Result<BiliResponse<SmsLoginResponse>> = resultOf {
        val buvid3 = CookieHelper.getCookieValue(cookieJar.getAllCookies(), "buvid3") ?: ""
        passportApi.loginWithSms(
            cid = cid,
            tel = tel,
            code = code,
            captchaKey = captchaKey,
            recaptchaToken = captcha.recaptchaToken,
            geeSeccode = geeTestResult.seccode,
            geeValidate = geeTestResult.validate,
            geeChallenge = geeTestResult.challenge,
            buvid3 = buvid3
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
