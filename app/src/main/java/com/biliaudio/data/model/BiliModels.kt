package com.biliaudio.data.model

import com.biliaudio.data.toHttpsUrl
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

/**
 * /x/web-interface/nav 返回的当前登录用户信息。
 * 仅凭 Cookie 即可获取，无需 WBI 签名和 mid 参数。
 */
@Serializable
data class NavInfo(
    val isLogin: Boolean = false,
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val sign: String = "",
    val level_info: NavLevelInfo? = null,
    val wbi_img: WbiImg? = null
) {
    fun toUserInfo(): UserInfo? {
        if (!isLogin || mid == 0L) return null
        return UserInfo(
            mid = mid,
            name = uname,
            face = face.toHttpsUrl(),
            sign = sign,
            level = level_info?.currentLevel ?: 0
        )
    }
}

@Serializable
data class NavLevelInfo(
    @SerialName("current_level")
    val currentLevel: Int = 0
)

/**
 * nav 接口返回的 WBI 签名密钥信息。
 * img_url 和 sub_url 的路径末段（去掉扩展名）分别为 img_key 与 sub_key，
 * 二者拼接后经混淆表处理得到 mixin_key，用于对 WBI 接口请求参数签名。
 */
@Serializable
data class WbiImg(
    val img_url: String = "",
    val sub_url: String = ""
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

// ============ 短信登录相关 ============

/**
 * /x/passport-login/captcha 返回。
 * bilibili 的验证码响应字段命名在不同版本下略有差异，
 * 这里同时兼容两种结构，运行时通过 gt / challenge 计算属性统一访问。
 */
@Serializable
data class CaptchaResponse(
    // bilibili captcha 接口返回字段名为 "token"（不是 recaptcha_token）。
    // 参考BBPlayer BilibiliCaptchaTokenData: { token, geetest: { gt, challenge } }
    @SerialName("token")
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

// ============ 短信登录相关 ============

/**
 * /x/passport-login/web/sms/send 返回。
 * captcha_key 用于下一步 /x/passport-login/web/login/sms 提交校验。
 */
@Serializable
data class SmsSendResponse(
    @SerialName("captcha_key")
    val captchaKey: String = "",
    val recaptcha: String = ""
)

/**
 * /x/passport-login/web/login/sms 返回。
 * 成功登录的 Cookie 通过 Set-Cookie 响应头下发，
 * 由 OkHttp + BiliCookieJar 自动捕获，无需从 data 字段提取。
 */
@Serializable
data class SmsLoginResponse(
    val status: Int = 0,
    val message: String = "",
    @SerialName("isLogin")
    val isLogin: Boolean = false
)

// ============ 合集（seasons）相关 ============
// 接口：x/polymer/web-space/home/seasons_series
// 注意：该接口返回 data.items_lists，内含 seasons_list（合集）与 series_list（系列）。
// B站空间里合集与系列在同一页面展示，这里一并解析后合并展示。

@Serializable
data class SeasonsSeriesResponse(
    val items_lists: SeasonItemsLists = SeasonItemsLists()
)

@Serializable
data class SeasonItemsLists(
    val seasons_list: List<SeasonListItem> = emptyList(),
    val series_list: List<SeasonListItem> = emptyList(),
    val page: SeasonPage = SeasonPage()
)

@Serializable
data class SeasonListItem(
    val meta: SeasonMeta? = null
)

/**
 * 合集/系列元数据。
 * 合集用 season_id，系列用 series_id；通过 [businessId] / [isSeries] 统一访问。
 *
 * 注意：B站接口对 season_id / series_id 的返回类型不统一——有时是数字、
 * 有时是字符串。这里统一用 [String] 接收（isLenient 模式下数字会自动转字符串），
 * 再在 [businessId] 中按需 toLong，避免类型不匹配导致反序列化失败、
 * 合集被静默过滤掉（表现为「无法检测到合集」）。
 */
@Serializable
data class SeasonMeta(
    val season_id: String = "",
    val series_id: String = "",
    val mid: Long = 0,
    val name: String = "",
    val cover: String = "",
    val description: String = "",
    val total: Int = 0,
    val ptime: Long = 0
) {
    /** season_id 转为 Long（无法解析或为 "0" 时返回 0）。 */
    val seasonIdLong: Long
        get() = season_id.toLongOrNull() ?: 0L

    /** series_id 转为 Long（无法解析或为 "0" 时返回 0）。 */
    val seriesIdLong: Long
        get() = series_id.toLongOrNull() ?: 0L

    /** 统一业务 id：合集取 season_id，系列取 series_id。 */
    val businessId: Long
        get() = if (seasonIdLong != 0L) seasonIdLong else seriesIdLong

    /** 是否为系列（而非合集），决定视频列表走哪个接口。 */
    val isSeries: Boolean
        get() = seasonIdLong == 0L && seriesIdLong != 0L
}

@Serializable
data class SeasonPage(
    val page_num: Int = 1,
    val page_size: Int = 20,
    val total: Int = 0
)

// 接口：x/polymer/web-space/seasons_archives_list
@Serializable
data class SeasonArchivesResponse(
    val archives: List<SeasonArchive> = emptyList(),
    val meta: SeasonMeta? = null,
    val page: SeasonPage = SeasonPage()
)

@Serializable
data class SeasonArchive(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val stat: SeasonStat? = null
) {
    /** 合集视频字段名与 VideoItem 不同（pic vs cover），在此做映射。 */
    fun toVideoItem(): VideoItem = VideoItem(
        id = aid,
        aid = aid,
        bvid = bvid,
        title = title,
        cover = pic,
        duration = duration
    )
}

@Serializable
data class SeasonStat(
    val view: Int = 0
)

// ============ 播放历史相关 ============
// 接口：x/web-interface/history/cursor

@Serializable
data class HistoryResponse(
    val list: List<HistoryItem> = emptyList(),
    val cursor: HistoryCursor = HistoryCursor()
)

@Serializable
data class HistoryItem(
    val title: String = "",
    val cover: String = "",
    val view_at: Long = 0,
    val progress: Int = 0,
    val duration: Int = 0,
    val history: HistoryDetail? = null
) {
    /**
     * 映射为 VideoItem，仅处理稿件(archive)类型的历史记录。
     * 番剧/直播/文章等类型不适用于音频播放，跳过。
     */
    fun toVideoItem(): VideoItem? {
        val detail = history ?: return null
        if (detail.business != "archive") return null
        val aid = detail.oid
        if (aid == 0L) return null
        return VideoItem(
            id = aid,
            aid = aid,
            bvid = detail.bvid,
            title = title,
            cover = cover,
            duration = duration
        )
    }
}

@Serializable
data class HistoryDetail(
    val oid: Long = 0,
    val bvid: String = "",
    val business: String = "",
    val cid: Long = 0
)

@Serializable
data class HistoryCursor(
    val max: Long = 0,
    val business: String = "",
    val view_at: Long = 0
)
