package com.biliaudio.data.repository

import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.FavoriteFolder
import com.biliaudio.data.model.FavoriteListResponse
import com.biliaudio.data.model.FavoriteResourceResponse
import com.biliaudio.data.model.HistoryResponse
import com.biliaudio.data.model.SeasonArchivesResponse
import com.biliaudio.data.model.SeasonsSeriesResponse
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.resultOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
    private val api: BiliApi
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

    suspend fun getSeasonsSeries(
        mid: Long,
        pageNum: Int = 1,
        pageSize: Int = 20
    ): Result<BiliResponse<SeasonsSeriesResponse>> = resultOf {
        api.getSeasonsSeries(mid, pageNum, pageSize)
    }

    suspend fun getSeasonArchives(
        mid: Long,
        seasonId: Long,
        pageNum: Int = 1,
        pageSize: Int = 30
    ): Result<BiliResponse<SeasonArchivesResponse>> = resultOf {
        api.getSeasonArchives(mid, seasonId, pageNum, pageSize)
    }

    // ============ 播放历史 ============

    suspend fun getHistory(
        pageSize: Int = 20,
        max: Long = 0,
        viewAt: Long = 0
    ): Result<BiliResponse<HistoryResponse>> = resultOf {
        api.getHistory(pageSize = pageSize, max = max, viewAt = viewAt)
    }
}
