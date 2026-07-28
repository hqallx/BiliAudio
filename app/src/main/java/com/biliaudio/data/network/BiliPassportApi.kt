package com.biliaudio.data.network

import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.CaptchaResponse
import com.biliaudio.data.model.LoginResponse
import com.biliaudio.data.model.QrCodeResponse
import com.biliaudio.data.model.QrCodeStatusData
import com.biliaudio.data.model.WebKeyResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 登录相关接口，使用 passport.bilibili.com 域名。
 *
 * 之前的二维码接口被误放到 api.bilibili.com 下，导致 HTTP 404；
 * bilibili 的所有 passport-login 系列接口都位于 passport.bilibili.com。
 * Cookie 由共享的 BiliCookieJar 自动持久化与跨子域共享。
 */
interface BiliPassportApi {

    // ============ 二维码登录 ============

    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(): BiliResponse<QrCodeResponse>

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun checkQrCodeStatus(@Query("qrcode_key") qrcodeKey: String): BiliResponse<QrCodeStatusData>

    // ============ 密码登录 ============

    /** 获取 RSA 公钥与 hash，用于密码加密。 */
    @GET("x/passport-login/web/key")
    suspend fun getWebKey(): BiliResponse<WebKeyResponse>

    /** 获取 GeeTest 滑块验证码信息。 */
    @GET("x/passport-login/captcha")
    suspend fun getCaptcha(): BiliResponse<CaptchaResponse>

    /**
     * 提交密码登录。
     * 成功时 Cookie 通过 Set-Cookie 响应头下发，BiliCookieJar 会自动捕获。
     */
    @FormUrlEncoded
    @POST("x/passport-login/web/v6/login")
    suspend fun loginWithPassword(
        @Field("username") username: String,
        @Field("password") encryptedPassword: String,
        @Field("key") keyHash: String,
        @Field("recaptcha_token") recaptchaToken: String,
        @Field("gee_seccode") geeSeccode: String,
        @Field("gee_validate") geeValidate: String,
        @Field("gee_challenge") geeChallenge: String,
        @Field("go_url") goUrl: String = "https://www.bilibili.com",
        @Field("Local_id") localId: Int = 0
    ): BiliResponse<LoginResponse>
}
