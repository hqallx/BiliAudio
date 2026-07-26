package com.biliaudio.data.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val audioUrl: String,
    val duration: Long = 0,
    val bvid: String = "",
    val aid: Long = 0
)
