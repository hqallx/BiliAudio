package com.biliaudio.data.network

import com.biliaudio.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query

interface BiliApi {

    @GET("x/space/wbi/acc/info")
    suspend fun getUserInfo(): BiliResponse<UserInfo>

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
