package com.biliaudio.data.repository

import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.ApiActionResponse
import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.CollectedListResponse
import com.biliaudio.data.model.FavoriteListResponse
import com.biliaudio.data.model.FavoriteResourceResponse
import com.biliaudio.data.model.HistoryResponse
import com.biliaudio.data.model.SeasonListResponse
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.network.BiliCookieJar
import com.biliaudio.data.resultOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
    private val api: BiliApi,
    private val cookieJar: BiliCookieJar
) {

    suspend fun getFavoriteFolders(mid: Long): Result<BiliResponse<FavoriteListResponse>> = resultOf {
        api.getFavoriteFolders(mid)
    }

    suspend fun getFavoriteResources(
        mediaId: Long,
        page: Int = 1,
        pageSize: Int = BiliConstants.DEFAULT_PAGE_SIZE,
        keyword: String = "",
        order: String = BiliConstants.Order.MTIME
    ): Result<BiliResponse<FavoriteResourceResponse>> = resultOf {
        api.getFavoriteResources(mediaId, page, pageSize, keyword, order)
    }

    // ============ 合集 ============

    /**
     * 获取当前登录用户追更的合集与收藏夹列表。
     * 仅返回 attr==0 的追更视频合集（season），过滤掉订阅的他人收藏夹(attr=22)和已失效项。
     */
    suspend fun getCollectedSeasons(
        mid: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<BiliResponse<CollectedListResponse>> = resultOf {
        api.getCollectedList(mid, page, pageSize)
    }

    /**
     * 获取指定合集内的视频列表。
     */
    suspend fun getSeasonVideos(
        seasonId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<BiliResponse<SeasonListResponse>> = resultOf {
        api.getSeasonList(seasonId, page, pageSize)
    }

    // ============ 播放历史 ============

    suspend fun getHistory(
        pageSize: Int = 20,
        max: Long = 0,
        viewAt: Long = 0
    ): Result<BiliResponse<HistoryResponse>> = resultOf {
        api.getHistory(pageSize = pageSize, max = max, viewAt = viewAt)
    }

    // ============ 删除操作 ============

    /**
     * 删除收藏夹。
     * csrf 从 cookieJar 的 bili_jct cookie 获取。
     */
    suspend fun deleteFolder(mediaId: Long): Result<ApiActionResponse> {
        val csrf = cookieJar.store.getCsrfToken() ?: ""
        if (csrf.isEmpty()) return Result.Error(Exception("未登录"), "请先登录")
        return resultOf { api.deleteFavFolder(mediaIds = mediaId.toString(), csrf = csrf) }
    }

    /**
     * 删除收藏夹内的单个视频。
     * @param mediaId 收藏夹 id
     * @param avid 视频 avid（VideoItem.id）
     */
    suspend fun deleteResource(mediaId: Long, avid: Long): Result<ApiActionResponse> {
        val csrf = cookieJar.store.getCsrfToken() ?: ""
        if (csrf.isEmpty()) return Result.Error(Exception("未登录"), "请先登录")
        // resources 格式: "avid,type"，type=2 为视频稿件
        return resultOf { api.deleteFavResource(resources = "$avid,2", mediaId = mediaId, csrf = csrf) }
    }

    /**
     * 取消追更合集。
     * @param seasonId 合集 id
     */
    suspend fun unfollowSeason(seasonId: Long): Result<ApiActionResponse> {
        val csrf = cookieJar.store.getCsrfToken() ?: ""
        if (csrf.isEmpty()) return Result.Error(Exception("未登录"), "请先登录")
        return resultOf { api.unfollowSeason(seasonId = seasonId, csrf = csrf) }
    }
}
