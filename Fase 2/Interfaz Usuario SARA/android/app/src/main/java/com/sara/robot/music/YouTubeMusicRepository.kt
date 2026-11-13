package com.sara.robot.music

import com.sara.robot.core.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Repositorio de música que consulta la YouTube Data API v3 con la clave de Google Cloud del usuario.
 * Obtiene la canción real solicitada por el usuario sin alterar el resultado con canciones de prueba.
 */
class YouTubeMusicRepository(
    private var apiKey: String = "",
    private val audioResolver: AudioStreamResolver = AudioStreamResolver()
) {

    private var apiService: YouTubeApiService = createRetrofitService()

    fun updateApiKey(newApiKey: String) {
        this.apiKey = newApiKey.trim()
        this.apiService = createRetrofitService()
        AppLogger.info("YouTubeRepo", "API Key de Google Cloud / YouTube actualizada.")
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    private fun createRetrofitService(): YouTubeApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(YouTubeApiService::class.java)
    }

    /**
     * Busca la canción en la YouTube Data API v3 y prepara la pista con el videoId real.
     */
    suspend fun buscarYPreparar(query: String): MusicTrack? = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext null

        AppLogger.info("YouTubeRepo", "Consultando YouTube Data API v3 para: '$cleanQuery'...")

        if (apiKey.isBlank()) {
            AppLogger.error("YouTubeRepo", "GOOGLE_CLOUD_API_KEY no encontrada en .env.")
            return@withContext null
        }

        try {
            val response = apiService.buscarVideo(
                part = "snippet",
                query = cleanQuery,
                type = "video",
                maxResults = 1,
                apiKey = apiKey
            )

            val item = response.items.firstOrNull()
            if (item != null) {
                val videoId = item.id.videoId
                val snippet = item.snippet
                val title = snippet?.title ?: cleanQuery
                val artist = snippet?.channelTitle ?: "YouTube"
                val thumbnailUrl = snippet?.thumbnails?.high?.url
                    ?: snippet?.thumbnails?.medium?.url
                    ?: snippet?.thumbnails?.default?.url
                    ?: ""

                AppLogger.success("YouTubeRepo", "Canción encontrada en YouTube: '$title' (ID: $videoId)")

                // Intentar obtener URL de stream de audio si está disponible; de lo contrario se reproducirá por el reproductor nativo de YouTube
                val audioUrl = audioResolver.resolveAudioUrl(videoId, cleanQuery) ?: ""

                return@withContext MusicTrack(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    audioUrl = audioUrl,
                    thumbnailUrl = thumbnailUrl
                )
            } else {
                AppLogger.warn("YouTubeRepo", "YouTube Data API no retornó resultados para: '$cleanQuery'")
                return@withContext null
            }
        } catch (e: Exception) {
            AppLogger.error("YouTubeRepo", "Error al consultar YouTube Data API v3: ${e.message}")
            return@withContext null
        }
    }
}
