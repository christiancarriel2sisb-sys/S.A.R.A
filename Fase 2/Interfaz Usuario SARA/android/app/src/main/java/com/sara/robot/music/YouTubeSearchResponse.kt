package com.sara.robot.music

import com.google.gson.annotations.SerializedName

/**
 * Modelos de datos para la respuesta de la YouTube Data API v3 (Search endpoint).
 * Definidos según el Paso 4 de la guía técnica modulo de musica.md.
 */
data class YouTubeSearchResponse(
    @SerializedName("items")
    val items: List<VideoItem> = emptyList()
)

data class VideoItem(
    @SerializedName("id")
    val id: VideoId = VideoId(),

    @SerializedName("snippet")
    val snippet: VideoSnippet? = null
)

data class VideoId(
    @SerializedName("videoId")
    val videoId: String = ""
)

data class VideoSnippet(
    @SerializedName("title")
    val title: String = "",

    @SerializedName("description")
    val description: String = "",

    @SerializedName("channelTitle")
    val channelTitle: String = "",

    @SerializedName("thumbnails")
    val thumbnails: Thumbnails? = null
)

data class Thumbnails(
    @SerializedName("default")
    val default: Thumbnail? = null,

    @SerializedName("medium")
    val medium: Thumbnail? = null,

    @SerializedName("high")
    val high: Thumbnail? = null
)

data class Thumbnail(
    @SerializedName("url")
    val url: String = ""
)
