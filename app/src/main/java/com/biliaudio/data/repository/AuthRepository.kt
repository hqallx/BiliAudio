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
import com.biliaudio.data.network.WbiSigner
import com.biliaudio.data.resultOf
import com.biliaudio.ui.components.GeeTestResult
import com.biliaudio.util.DebugLogger
import okhttp3.Cookie
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: BiliApi,
    private val passportApi: BiliPassportApi,
    private val cookieJar: BiliCookieJar,
    private val wbiSigner: WbiSigner
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
     * 使用 x/web-interface/nav 接口（基于 Cookie，无需 WBI 签名）。
     *
     * 返回 [NavResult] 以区分三种状态，便于上层决定是否登出：
     * - [NavResult.LoggedIn]：接口明确返回已登录，附带完整 UserInfo
     * - [NavResult.NotLoggedIn]：接口明确返回未登录（code=-101 或 isLogin=false），
     *   说明 Cookie 已失效，应当 logout
     * - [NavResult.Failed]：网络/解析错误，Cookie 可能仍有效，不应 logout
     */
    suspend fun getUserInfo(): NavResult = try {
        val resp = api.getNavInfo()
        // 同步提取并缓存 WBI 签名密钥（nav 接口始终返回 wbi_img，不依赖登录态）。
        // 后续 playurl 等 WBI 接口需要用到。
        resp.data?.wbi_img?.let { wbi ->
            wbiSigner.updateKeys(wbi.img_url, wbi.sub_url)
        }
        when {
            // code=0 且 isLogin=true：正常已登录
            resp.code == 0 && resp.data != null && resp.data.isLogin -> {
                val info = resp.data.toUserInfo()
                if (info != null) NavResult.LoggedIn(info) else NavResult.NotLoggedIn
            }
            // code=-101：账号未登录（Cookie 失效）
            // isLogin=false：服务端明确告知未登录
            resp.code == -101 || (resp.code == 0 && resp.data?.isLogin == false) -> NavResult.NotLoggedIn
            // 其他 code（如 -352 风控、-509 限流、-403 等）：
            // Cookie 可能仍有效，不应据此登出，交给上层按 Failed 处理（保留登录态）。
            else -> NavResult.Failed("nav 接口返回 code=${resp.code}: ${resp.message}")
        }
    } catch (e: Exception) {
        NavResult.Failed(e.message ?: "获取用户信息失败")
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

    /**
     * 判断是否已登录。
     *
     * 参考 BBPlayer：登录态**仅由磁盘 Cookie 决定**，不做网络验证。
     * 直接读 SharedPreferences（同步），不依赖内存 cookieStore，
     * 消除进程重启后内存未恢复的时序问题。
     */
    fun isLoggedIn(): Boolean {
        return cookieJar.hasLoginCookiesFromDisk()
    }

    fun getCurrentUserId(): Long? {
        // 优先直接从磁盘 BiliCookieStore 读取（同步、权威），
        // 避免 getAllCookies() 构造 Cookie 对象的额外开销与时序问题。
        val mid = cookieJar.store.getUserId()
        DebugLogger.d("AuthRepo", "getCurrentUserId: mid=$mid, cookieNames=${cookieJar.store.getCookieMap().keys}")
        return mid
    }

    /** 调试用：返回当前内存中 Cookie 的简要快照（name=value 列表）。 */
    fun cookieSnapshot(): String {
        return cookieJar.getAllCookies().joinToString(", ") { "${it.name}=${it.value.take(8)}" }
    }
}

/**
 * nav 接口查询结果的三态封装。
 *
 * 为什么不用 Result<UserInfo?>：因为 null 既可能是「接口明确未登录」
 * 也可能是「网络错误」，二者处理方式不同（前者应 logout，后者不应）。
 */
sealed class NavResult {
    /** 接口明确返回已登录，附带完整用户信息。 */
    data class LoggedIn(val userInfo: UserInfo) : NavResult()
    /** 接口明确返回未登录（code=-101 或 isLogin=false），Cookie 已失效。 */
    data object NotLoggedIn : NavResult()
    /** 网络/解析错误，Cookie 可能仍有效，不应据此登出。 */
    data class Failed(val message: String) : NavResult()
}
