package com.sara.robot.music

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Servicio Retrofit para la YouTube Data API v3.
 * Según la especificación del Paso 4 de modulo de musica.md.
 */
interface YouTubeApiService {

    @GET("youtube/v3/search")
    suspend fun buscarVideo(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 1,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}
