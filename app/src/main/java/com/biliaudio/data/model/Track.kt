package com.biliaudio.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val audioUrl: String,
    val duration: Long = 0,
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0
)
