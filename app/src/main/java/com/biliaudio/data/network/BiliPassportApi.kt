package com.biliaudio.data.network

import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.CaptchaResponse
import com.biliaudio.data.model.QrCodeResponse
import com.biliaudio.data.model.QrCodeStatusData
import com.biliaudio.data.model.SmsLoginResponse
import com.biliaudio.data.model.SmsSendResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 登录相关接口，使用 passport.bilibili.com 域名（照搬 BBPlayer 实现）。
 *
 * Cookie 由共享的 BiliCookieJar 自动持久化与跨子域共享。
 * 二维码/短信登录成功时，Set-Cookie 响应头由 OkHttp CookieJar 自动捕获。
 */
interface BiliPassportApi {

    // ============ 二维码登录 ============

    /** 生成二维码：返回登录链接 + qrcode_key。url 是登录链接（非图片），需本地编码成二维码图片。 */
    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(): BiliResponse<QrCodeResponse>

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun checkQrCodeStatus(@Query("qrcode_key") qrcodeKey: String): BiliResponse<QrCodeStatusData>

    // ============ 短信登录 ============

    /** 获取 GeeTest 滑块验证码信息，发送短信前必须先完成滑块验证。 */
    @GET("x/passport-login/captcha")
    suspend fun getCaptcha(
        @Query("source") source: String = "main_web",
        @Query("t") timestamp: Long
    ): BiliResponse<CaptchaResponse>

    /**
     * 发送短信验证码。需要先通过 GeeTest 滑块验证拿到 seccode/validate/challenge。
     * 返回 captcha_key，用于下一步登录接口校验。
     *
     * 字段名使用 challenge/validate/seccode（不带 gee_ 前缀），与 BBPlayer 一致。
     */
    @FormUrlEncoded
    @POST("x/passport-login/web/sms/send")
    suspend fun sendSmsCode(
        @Field("cid") cid: String,
        @Field("tel") tel: String,
        @Field("source") source: String = "main_mini_login",
        @Field("token") recaptchaToken: String,
        @Field("challenge") challenge: String,
        @Field("validate") validate: String,
        @Field("seccode") seccode: String
    ): BiliResponse<SmsSendResponse>

    /**
     * 用手机号 + 短信验证码登录。
     * 成功时 Cookie 通过 Set-Cookie 响应头下发，BiliCookieJar 会自动捕获。
     *
     * 登录步骤不需要再次传 GeeTest 参数，只需 captcha_key + keep=1（照搬 BBPlayer）。
     */
    @FormUrlEncoded
    @POST("x/passport-login/web/login/sms")
    suspend fun loginWithSms(
        @Field("cid") cid: String,
        @Field("tel") tel: String,
        @Field("code") code: String,
        @Field("source") source: String = "main_mini_login",
        @Field("captcha_key") captchaKey: String,
        @Field("keep") keep: String = "1"
    ): BiliResponse<SmsLoginResponse>
}
