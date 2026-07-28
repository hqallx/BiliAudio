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

    @GET("x/v3/fav/folder/created/list-all")
    suspend fun getFavoriteFolders(@Query("up_mid") mid: Long): BiliResponse<FavoriteListResponse>

    @GET("x/v3/fav/resource/list")
    suspend fun getFavoriteResources(
        @Query("media_id") mediaId: Long,
        @Query("pn") page: Int = 1,
        @Query("ps") pageSize: Int = 20,
        @Query("keyword") keyword: String = "",
        @Query("order") order: String = "mtime"
    ): BiliResponse<FavoriteResourceResponse>

    @GET("x/player/wbi/playurl")
    suspend fun getVideoStream(
        @Query("bvid") bvid: String = "",
        @Query("aid") aid: Long = 0,
        @Query("qn") qn: Int = 64,
        @Query("fnval") fnval: Int = 16,
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1
    ): BiliResponse<VideoStreamResponse>
}
