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

    /**
     * 获取用户空间的合集与系列列表。
     * 接口：x/polymer/web-space/home/seasons_series
     * 返回 items 数组，每个 item.meta 包含合集元数据（标题、封面、视频数）。
     */
    @GET("x/polymer/web-space/home/seasons_series")
    suspend fun getSeasonsSeries(
        @Query("mid") mid: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): BiliResponse<SeasonsSeriesResponse>

    /**
     * 获取指定合集内的视频列表。
     * 接口：x/polymer/web-space/seasons_archives_list
     * 返回 archives 数组（aid/bvid/title/pic/duration）。
     */
    @GET("x/polymer/web-space/seasons_archives_list")
    suspend fun getSeasonArchives(
        @Query("mid") mid: Long,
        @Query("season_id") seasonId: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("sort_reverse") sortReverse: Boolean = false
    ): BiliResponse<SeasonArchivesResponse>

    /**
     * 获取指定系列内的视频列表。
     * 接口：x/polymer/web-space/series/archives
     * 系列与合集是两种内容组织形式，视频加载接口不同，但返回结构一致（archives 数组）。
     */
    @GET("x/polymer/web-space/series/archives")
    suspend fun getSeriesArchives(
        @Query("mid") mid: Long,
        @Query("series_id") seriesId: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 30
    ): BiliResponse<SeasonArchivesResponse>

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
