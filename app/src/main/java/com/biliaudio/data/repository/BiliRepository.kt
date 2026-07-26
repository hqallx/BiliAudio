package com.biliaudio.data.repository

import com.biliaudio.data.model.*
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.network.NetworkModule
import com.biliaudio.data.network.QrCodeStatusData

class BiliRepository {

    private val api: BiliApi = NetworkModule.provideBiliApi()
    private val cookieJar = NetworkModule.provideCookieJar()

    suspend fun generateQrCode(): BiliResponse<QrCodeResponse> {
        return api.generateQrCode()
    }

    suspend fun checkQrCodeStatus(qrcodeKey: String): BiliResponse<QrCodeStatusData> {
        return api.checkQrCodeStatus(qrcodeKey)
    }

    suspend fun getUserInfo(): BiliResponse<UserInfo> {
        return api.getUserInfo()
    }

    suspend fun getFavoriteFolders(mid: Long): BiliResponse<FavoriteListResponse> {
        return api.getFavoriteFolders(mid)
    }

    suspend fun getFavoriteResources(
        mediaId: Long,
        page: Int = 1,
        pageSize: Int = 20,
        keyword: String = "",
        order: String = "mtime"
    ): BiliResponse<FavoriteResourceResponse> {
        return api.getFavoriteResources(mediaId, page, pageSize, keyword, order)
    }

    suspend fun getVideoStream(
        bvid: String = "",
        aid: Long = 0
    ): BiliResponse<VideoStreamResponse> {
        return api.getVideoStream(bvid = bvid, aid = aid)
    }

    fun setCookieListener(listener: (List<okhttp3.Cookie>) -> Unit) {
        cookieJar.onCookiesUpdated = listener
    }

    fun clearCookies() {
        cookieJar.clearCookies()
    }

    fun getCookieString(): String {
        return cookieJar.getCookies().joinToString("; ") { "${it.name}=${it.value}" }
    }
}
