package com.biliaudio.data.repository

import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.FavoriteFolder
import com.biliaudio.data.model.FavoriteListResponse
import com.biliaudio.data.model.FavoriteResourceResponse
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
}
