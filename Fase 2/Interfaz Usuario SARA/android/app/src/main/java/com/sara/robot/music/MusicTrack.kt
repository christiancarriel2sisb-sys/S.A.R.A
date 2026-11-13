package com.sara.robot.music

/**
 * Representa una pista de audio lista para ser reproducida por ExoPlayer.
 */
data class MusicTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0L,
    val isLiveStream: Boolean = false
)
