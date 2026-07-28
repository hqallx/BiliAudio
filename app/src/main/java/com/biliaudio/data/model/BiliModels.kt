package com.biliaudio.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BiliResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null
)

@Serializable
data class UserInfo(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String = "",
    val level: Int = 0
)

@Serializable
data class FavoriteFolder(
    val id: Long,
    val fid: Long,
    val mid: Long,
    val title: String,
    val cover: String = "",
    @SerialName("media_count")
    val mediaCount: Int = 0,
    @SerialName("intro")
    val intro: String = ""
)

@Serializable
data class FavoriteListResponse(
    val list: List<FavoriteFolder>,
    val count: Int = 0
)

@Serializable
data class VideoItem(
    val id: Long,
    val type: Int = 0,
    val title: String,
    val cover: String = "",
    val intro: String = "",
    val duration: Int = 0,
    val upper: Upper? = null,
    val attr: Int = 0,
    val bvid: String = "",
    val aid: Long = 0
)

@Serializable
data class Upper(
    val mid: Long,
    val name: String,
    val face: String = ""
)

@Serializable
data class FavoriteResourceResponse(
    val medias: List<VideoItem>,
    val has_more: Boolean = false,
    val total: Int = 0
)

@Serializable
data class VideoStreamResponse(
    @SerialName("accept_quality")
    val acceptQuality: List<Int> = emptyList(),
    val dash: DashData? = null
)

@Serializable
data class DashData(
    val duration: Int = 0,
    val audio: List<AudioItem> = emptyList()
)

@Serializable
data class AudioItem(
    val id: Int,
    val baseUrl: String = "",
    @SerialName("base_url")
    val baseUrlAlt: String = "",
    val mimeType: String = "",
    @SerialName("mime_type")
    val mimeTypeAlt: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: String = "",
    @SerialName("frame_rate")
    val frameRateAlt: String = "",
    val sar: String = "",
    val startWithSap: Int = 0,
    @SerialName("start_with_sap")
    val startWithSapAlt: Int = 0,
    @SerialName("SegmentBase")
    val segmentBase: SegmentBase? = null,
    @SerialName("segment_base")
    val segmentBaseAlt: SegmentBase? = null
) {
    val url: String
        get() = baseUrl.ifEmpty { baseUrlAlt }
}

@Serializable
data class SegmentBase(
    val initialization: String = "",
    val indexRange: String = ""
)

@Serializable
data class QrCodeResponse(
    val url: String,
    @SerialName("qrcode_key")
    val qrcodeKey: String
)

@Serializable
data class QrCodeStatusResponse(
    val code: Int,
    val message: String,
    val data: QrCodeData? = null
)

@Serializable
data class QrCodeData(
    val url: String = "",
    val refresh_token: String = "",
    val timestamp: Long = 0,
    val code: Int = 0,
    val message: String = ""
)

@Serializable
data class QrCodeStatusData(
    val code: Int,
    val message: String,
    val url: String? = null,
    val refresh_token: String? = null,
    val timestamp: Long? = null
)

// ============ 密码登录相关 ============

/**
 * /x/passport-login/web/key 返回。
 * hash 是用于拼接密码的盐值，key 是 X.509 SubjectPublicKeyInfo 的 Base64 字符串。
 */
@Serializable
data class WebKeyResponse(
    val hash: String,
    val key: String
)

/**
 * /x/passport-login/captcha 返回。
 * bilibili 的验证码响应字段命名在不同版本下略有差异，
 * 这里同时兼容两种结构，运行时通过 gt / challenge 计算属性统一访问。
 */
@Serializable
data class CaptchaResponse(
    @SerialName("recaptcha_token")
    val recaptchaToken: String = "",
    val type: String = "",
    @SerialName("geetest_gt")
    val geetestGtAlt: String? = null,
    @SerialName("geetest_challenge")
    val geetestChallengeAlt: String? = null,
    val geetest: GeetestInfo? = null
) {
    val gt: String
        get() = geetestGtAlt?.takeIf { it.isNotEmpty() } ?: geetest?.gt ?: ""
    val challenge: String
        get() = geetestChallengeAlt?.takeIf { it.isNotEmpty() } ?: geetest?.challenge ?: ""
}

@Serializable
data class GeetestInfo(
    val gt: String = "",
    val challenge: String = ""
)

/**
 * /x/passport-login/web/v6/login 返回。
 * 注意：成功登录的 Cookie 通过 Set-Cookie 响应头下发，
 * 由 OkHttp + BiliCookieJar 自动捕获，无需从 data 字段提取。
 */
@Serializable
data class LoginResponse(
    val status: Int = 0,
    val message: String = "",
    val isLogin: Boolean = false,
    @SerialName("token_info")
    val tokenInfo: LoginTokenInfo? = null
)

@Serializable
data class LoginTokenInfo(
    val mid: Long = 0,
    @SerialName("access_token")
    val accessToken: String = ""
)
