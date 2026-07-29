package com.biliaudio.data.network

import com.biliaudio.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query

interface BiliApi {

    /**
     * 获取当前登录用户信息（基于 Cookie，无需 WBI 签名和 mid 参数）。
     *
     * 使用 x/web-interface/nav 而非 x/space/wbi/acc/info：
     * - 后者需要 WBI 签名 + mid 参数，缺失会返回错误导致误判「未登录」
     * - nav 接口仅凭 Cookie 即可返回当前用户信息，适合登录态校验
     */
    @GET("x/web-interface/nav")
    suspend fun getNavInfo(): BiliResponse<NavInfo>

    @GET("x/v3/fav/folder/created/list")
    suspend fun getFavoriteFolders(
        @Query("up_mid") mid: Long,
        @Query("type") type: Int = 0,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20
    ): BiliResponse<FavoriteListResponse>

    @GET("x/v3/fav/resource/list")
    suspend fun getFavoriteResources(
        @Query("media_id") mediaId: Long,
        @Query("pn") page: Int = 1,
        @Query("ps") pageSize: Int = 20,
        @Query("keyword") keyword: String = "",
        @Query("order") order: String = "mtime"
    ): BiliResponse<FavoriteResourceResponse>

    /**
     * 获取视频信息（含 cid）。
     * 接口：x/web-interface/view
     * cid 是 playurl 接口的必需参数，必须先通过本接口获取。
     */
    @GET("x/web-interface/view")
    suspend fun getVideoInfo(
        @Query("bvid") bvid: String = "",
        @Query("aid") aid: Long = 0
    ): BiliResponse<VideoInfoResponse>

    @GET("x/player/wbi/playurl")
    suspend fun getVideoStream(
        @Query("bvid") bvid: String = "",
        @Query("aid") aid: Long = 0,
        @Query("cid") cid: Long = 0,
        @Query("qn") qn: Int = 64,
        @Query("fnval") fnval: Int = 16,
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1
    ): BiliResponse<VideoStreamResponse>

    // ============ 合集（seasons） ============
    // 参考 BBPlayer：用 collected/list 获取追更合集，season/list 获取合集内视频。
    // 这两个接口均无需 WBI 签名。

    /**
     * 获取当前登录用户追更/订阅的合集与收藏夹列表。
     * 接口：x/v3/fav/folder/collected/list
     *
     * 注意：不同于 seasons_series_list（UP主自建合集，需WBI），本接口返回
     * 「我追更的合集」，更符合用户预期。用 attr 字段区分合集(0)与收藏夹(22)。
     */
    @GET("x/v3/fav/folder/collected/list")
    suspend fun getCollectedList(
        @Query("up_mid") mid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("platform") platform: String = "web"
    ): BiliResponse<CollectedListResponse>

    /**
     * 获取指定合集的详情与视频列表。
     * 接口：x/space/fav/season/list
     * 非登录亦可访问，返回 info + medias 数组。
     */
    @GET("x/space/fav/season/list")
    suspend fun getSeasonList(
        @Query("season_id") seasonId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20
    ): BiliResponse<SeasonListResponse>

    // ============ 播放历史 ============

    /**
     * 获取当前登录用户的播放历史（游标分页）。
     * 接口：x/web-interface/history/cursor
     * 基于 Cookie 认证，type=archive 仅返回稿件视频（适合音频播放）。
     *
     * @param max 上一次返回最后一条的 oid，用于翻页
     * @param viewAt 上一次返回最后一条的 view_at，用于翻页
     */
    @GET("x/web-interface/history/cursor")
    suspend fun getHistory(
        @Query("type") type: String = "archive",
        @Query("ps") pageSize: Int = 20,
        @Query("max") max: Long = 0,
        @Query("view_at") viewAt: Long = 0
    ): BiliResponse<HistoryResponse>
}
