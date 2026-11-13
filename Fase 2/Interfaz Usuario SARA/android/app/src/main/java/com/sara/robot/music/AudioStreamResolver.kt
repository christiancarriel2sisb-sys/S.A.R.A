package com.sara.robot.music

import com.sara.robot.core.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolutor de streaming multimedia para SARA.
 * Dado que las instancias públicas de scraping (Piped/Invidious) sufren bloqueos de IP
 * y caídas constantes (provocando errores DNS como pipedapi.nosebs.ru y 502 Bad Gateway),
 * la reproducción de canciones de YouTube se realiza de forma directa y oficial a través
 * del reproductor embebido (WebView con YouTube IFrame API).
 *
 * Se eliminaron todas las llamadas a servidores caídos y se suprimieron por completo
 * las pistas de prueba preconfiguradas (jazz y piano), garantizando que se reproduzca
 * única y exclusivamente la canción solicitada por el usuario.
 */
class AudioStreamResolver {

    /**
     * Retorna null de forma inmediata para que la reproducción se ejecute de manera
     * nativa y oficial en el reproductor de YouTube mediante el videoId obtenido
     * de la YouTube Data API v3, evitando demoras de red y fallos de hosts externos.
     */
    suspend fun resolveAudioUrl(videoId: String, queryHint: String = ""): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        AppLogger.info("AudioResolver", "VideoId '$videoId' preparado para reproducción oficial sin servidores intermediarios.")
        return@withContext null
    }
}

