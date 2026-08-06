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
 * /x/space/myinfo 返回的当前登录用户详细信息。
 * 仅凭 Cookie 即可获取，比 nav 接口更稳定（不易被风控返回 -101）。
 * 照搬 BBPlayer 的 getUserInfo 实现。
 */
@Serializable
data class MyInfo(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int = 0
) {
    fun toUserInfo(): UserInfo? {
        if (mid == 0L) return null
        return UserInfo(
            mid = mid,
            name = name,
            face = face.toHttpsUrl(),
            sign = sign,
            level = level
        )
    }
}

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
    // 必须有默认值：B 站空收藏夹返回 "list": null，
    // 无默认值时 coerceInputValues 不生效 → JsonDecodingException 崩溃
    val list: List<FavoriteFolder> = emptyList(),
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
    val aid: Long = 0,
    val cid: Long = 0
)

@Serializable
data class Upper(
    val mid: Long,
    val name: String,
    val face: String = ""
)

@Serializable
data class FavoriteResourceResponse(
    // 必须有默认值：B 站空收藏夹返回 "medias": null，
    // 无默认值时 coerceInputValues 不生效 → JsonDecodingException 崩溃
    val medias: List<VideoItem> = emptyList(),
    val has_more: Boolean = false,
    val total: Int = 0
)

@Serializable
data class VideoStreamResponse(
    @SerialName("accept_quality")
    val acceptQuality: List<Int> = emptyList(),
    val dash: DashData? = null,
    val durl: List<DurlItem>? = null
)

/**
 * 旧的 FLV/MP4 流式地址（fnval 未返回 dash 时使用）。
 * B站对部分老视频或不支持 DASH 的内容会返回 durl，取首段作为音频源兜底。
 */
@Serializable
data class DurlItem(
    val order: Int = 0,
    val url: String = "",
    @SerialName("backup_url")
    val backupUrl: List<String> = emptyList()
)

/**
 * /x/web-interface/view 返回的视频信息。
 * 主要用于获取 cid（playurl 接口的必需参数）。
 * req_user 含当前登录用户对此视频的互动状态（是否已点赞）。
 */
@Serializable
data class VideoInfoResponse(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val owner: Upper? = null,
    val stat: VideoStat? = null,
    @SerialName("req_user")
    val reqUser: ReqUser? = null
)

/**
 * 当前登录用户对视频的互动状态。
 * @property like 1=已点赞，0=未点赞
 */
@Serializable
data class ReqUser(
    val like: Int = 0,
    val coin: Int = 0,
    val favorite: Int = 0
) {
    val isLiked: Boolean get() = like == 1
}

@Serializable
data class DashData(
    val duration: Int = 0,
    val audio: List<AudioItem> = emptyList(),
    val dolby: DolbyData? = null,
    val flac: FlacData? = null
)

/**
 * 杜比全景声数据。
 * 参考 BBPlayer：dash.dolby.audio 为杜比音频流，优先级最高。
 */
@Serializable
data class DolbyData(
    val type: Int = 0,
    val audio: List<AudioItem> = emptyList()
)

/**
 * Hi-Res 无损音频数据。
 * 参考 BBPlayer：dash.flac.audio 为 Hi-Res 无损音频流，优先级仅次于杜比。
 */
@Serializable
data class FlacData(
    val display: Boolean = false,
    val audio: AudioItem? = null
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
// 参考 BBPlayer (https://github.com/bbplayer-app/BBPlayer) 的合集方案：
// - 列表用 /x/v3/fav/folder/collected/list 获取「当前登录用户追更的合集/收藏夹」，
//   而非 /x/polymer/web-space/seasons_series_list（后者是某 UP 主自建的合集，需 WBI 签名，
//   且追更视角下根本拿不到）。用 attr 字段区分合集(attr=0)与订阅收藏夹(attr=22)。
// - 合集内视频用 /x/space/fav/season/list + season_id，无需 WBI，非登录亦可访问。

/**
 * /x/v3/fav/folder/collected/list 返回。
 * 当前登录用户追更/订阅的合集与收藏夹列表。
 */
@Serializable
data class CollectedListResponse(
    val list: List<CollectionItem> = emptyList(),
    val count: Int = 0,
    val has_more: Boolean = false
)

/**
 * 追更合集/收藏夹列表中的单项。
 * @property attr 0=追更视频合集(season)，22=关注的他人收藏夹，1=已失效
 * @property state 0=正常，1=已失效
 */
@Serializable
data class CollectionItem(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "",
    val upper: Upper? = null,
    @SerialName("media_count")
    val mediaCount: Int = 0,
    val ctime: Long = 0,
    val intro: String = "",
    val attr: Int = 0,
    val state: Int = 0
) {
    /** 是否为追更视频合集（season），用 season/list 接口加载内容。 */
    val isSeason: Boolean
        get() = attr == 0

    /** 是否已失效。 */
    val isInvalid: Boolean
        get() = state == 1

    /** 转为 UI 层使用的 SeasonMeta，复用现有展示组件。 */
    fun toSeasonMeta(): SeasonMeta = SeasonMeta(
        season_id = id.toString(),
        name = title,
        cover = cover,
        description = intro,
        total = mediaCount,
        mid = upper?.mid ?: 0L
    )
}

/**
 * /x/space/fav/season/list 返回的合集详情与视频列表。
 */
@Serializable
data class SeasonListResponse(
    val info: SeasonInfo = SeasonInfo(),
    val medias: List<SeasonMedia>? = null
)

@Serializable
data class SeasonInfo(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "",
    val upper: Upper? = null,
    @SerialName("media_count")
    val mediaCount: Int = 0,
    val intro: String = ""
)

@Serializable
data class SeasonMedia(
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val duration: Int = 0,
    val pubtime: Long = 0,
    val upper: Upper? = null
) {
    /**
     * 合集内视频字段名与 VideoItem 不同（cover vs pic），在此做映射。
     * 注意：season/list 接口返回的 medias[].id 即为稿件 aid（与 fav/resource/list 一致），
     * 因此 VideoItem.id 与 VideoItem.aid 取同值；cid 留空，由播放时 ResolvingDataSource
     * 通过 getVideoInfo(bvid) 解析补齐。
     */
    fun toVideoItem(): VideoItem = VideoItem(
        id = id,
        aid = id,
        bvid = bvid,
        title = title,
        cover = cover,
        duration = duration,
        upper = upper
    )
}

// ============ 以下为旧合集模型（保留 SeasonMeta 供 UI 复用，其余已废弃） ============

/**
 * 合集元数据。UI 层（LibraryScreen/SeasonsTab/FolderCard）依赖此模型展示。
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

    /** 统一业务 id：合集取 season_id。 */
    val businessId: Long
        get() = seasonIdLong

    /** 新方案下均为合集（season），走 season/list 接口。 */
    val isSeries: Boolean
        get() = false
}

@Serializable
data class SeasonPage(
    val page_num: Int = 1,
    val page_size: Int = 20,
    val total: Int = 0
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
            duration = duration,
            cid = detail.cid
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
// ============ 互动：点赞 & 评论 ============

/** 视频统计信息（点赞数、评论数、播放数等） */
@Serializable
data class VideoStat(
    val aid: Long = 0,
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    val favorite: Long = 0,
    val coin: Long = 0,
    val share: Long = 0,
    val like: Long = 0
)

/**
 * 点赞/发送评论等操作的通用响应。
 * @property data has/like 接口返回 0(未赞)/1(已赞)；其他写操作多为 null。
 *           使用 JsonElement 以兼容多种 data 形态（数字/对象/null）。
 */
@Serializable
data class ApiActionResponse(
    val code: Int = 0,
    val message: String = "",
    val data: kotlinx.serialization.json.JsonElement? = null
)

/** 评论列表响应 */
@Serializable
data class ReplyListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ReplyData? = null
)

/**
 * /x/v2/reply/main 响应数据。
 * 参考 BBPlayer BilibiliCommentsResponse：reply/main 用 cursor 游标分页，
 * 而 reply/reply（楼中楼）用 page 分页。二者 replies 字段同名，故统一在此模型。
 */
@Serializable
data class ReplyData(
    val replies: List<ReplyItem> = emptyList(),
    val page: ReplyPage? = null,
    val cursor: ReplyCursor? = null
)

/** reply/main 的游标分页信息。 */
@Serializable
data class ReplyCursor(
    val is_end: Boolean = false,
    val next: Int = 0,
    val all_count: Int = 0,
    val prev: Int = 0
)

@Serializable
data class ReplyPage(
    val count: Int = 0,
    val num: Int = 0,
    val size: Int = 0
)

@Serializable
data class ReplyItem(
    val rpid: Long = 0,
    val mid: Long = 0,
    val ctime: Long = 0,
    val like: Long = 0,
    val content: ReplyContent? = null,
    val member: ReplyMember? = null,
    val replies: List<ReplyItem>? = null // 楼中楼回复
)

@Serializable
data class ReplyContent(
    val message: String = ""
)

@Serializable
data class ReplyMember(
    val mid: Long = 0,
    val uname: String = "",
    val avatar: String = ""
)
