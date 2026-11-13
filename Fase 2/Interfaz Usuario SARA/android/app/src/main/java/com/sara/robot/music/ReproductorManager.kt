package com.sara.robot.music

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sara.robot.core.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado reactivo del reproductor de música para la interfaz de Jetpack Compose.
 */
data class MusicPlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentThumbnailUrl: String = "",
    val currentVideoId: String = "",
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val errorMessage: String? = null
)

/**
 * Gestor del reproductor de audio optimizado con Media3 ExoPlayer.
 * Gestiona el estado de reproducción y atenuación de volumen (Audio Ducking)
 * respetando estrictamente la canción solicitada por el usuario sin sustituciones automáticas.
 */
class ReproductorManager(context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    // Búfer optimizado para menor latencia (inicio rápido de 0.5s a 1.5s) según la guía técnica
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(500, 1500, 250, 500)
        .build()

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8000)
        .setReadTimeoutMs(15000)
        .setUserAgent("Mozilla/5.0 (Linux; Android 14; SaraRobot) AppleWebKit/537.36")

    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(httpDataSourceFactory)

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .build()

    private val _playerState = MutableStateFlow(MusicPlayerState())
    val playerState: StateFlow<MusicPlayerState> = _playerState.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _playerState.update { it.copy(isBuffering = true) }
                    }
                    Player.STATE_READY -> {
                        val duration = if (player.duration > 0) player.duration else 0L
                        _playerState.update {
                            it.copy(
                                isBuffering = false,
                                isPlaying = player.isPlaying,
                                durationMs = duration,
                                errorMessage = null
                            )
                        }
                        startProgressTracker()
                    }
                    Player.STATE_ENDED -> {
                        _playerState.update { it.copy(isPlaying = false, isBuffering = false) }
                        stopProgressTracker()
                    }
                    Player.STATE_IDLE -> {
                        _playerState.update { it.copy(isPlaying = false, isBuffering = false) }
                        stopProgressTracker()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val detail = error.cause?.message ?: error.message ?: error.errorCodeName
                AppLogger.error("ExoPlayer", "Error de reproducción: $detail")
                _playerState.update { it.copy(isPlaying = false, isBuffering = false, errorMessage = detail) }
            }
        })
    }

    /**
     * Carga y activa la reproducción de la pista seleccionada por el usuario.
     */
    fun reproducirPista(track: MusicTrack) {
        _playerState.update {
            it.copy(
                currentTitle = track.title,
                currentArtist = track.artist,
                currentThumbnailUrl = track.thumbnailUrl,
                currentVideoId = track.videoId,
                isPlaying = true,
                isBuffering = false,
                errorMessage = null
            )
        }

        AppLogger.success("ReproductorManager", "Pista asignada: '${track.title}' (VideoId: ${track.videoId})")

        // Si se dispone de un enlace de audio directo, alimentar a ExoPlayer
        if (track.audioUrl.isNotBlank()) {
            reproducirAudio(track.audioUrl, track.title, track.artist)
        } else {
            // Detener streams anteriores de ExoPlayer para ceder el canal al reproductor de YouTube
            player.stop()
            player.clearMediaItems()
        }
    }

    fun reproducirAudio(audioUrl: String, title: String = "", artist: String = "") {
        if (audioUrl.isBlank()) return
        AppLogger.info("ExoPlayer", "Iniciando audio: '$title' ($audioUrl)")
        try {
            val mediaItem = MediaItem.fromUri(audioUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            _playerState.update {
                it.copy(
                    currentTitle = if (title.isNotBlank()) title else it.currentTitle,
                    currentArtist = if (artist.isNotBlank()) artist else it.currentArtist,
                    isBuffering = true
                )
            }
        } catch (e: Exception) {
            AppLogger.error("ExoPlayer", "Fallo al iniciar ExoPlayer: ${e.message}")
        }
    }

    fun pausar() {
        player.pause()
        _playerState.update { it.copy(isPlaying = false) }
        AppLogger.info("ReproductorManager", "Reproducción pausada.")
    }

    fun reanudar() {
        if (player.playbackState == Player.STATE_READY) {
            player.play()
        }
        _playerState.update { it.copy(isPlaying = true) }
        AppLogger.info("ReproductorManager", "Reproducción reanudada.")
    }

    fun detener() {
        player.stop()
        player.clearMediaItems()
        _playerState.update {
            it.copy(
                isPlaying = false,
                isBuffering = false,
                currentPositionMs = 0L,
                currentVideoId = ""
            )
        }
        stopProgressTracker()
        AppLogger.info("ReproductorManager", "Reproducción detenida.")
    }

    fun setVolumen(volumen: Float) {
        val clamped = volumen.coerceIn(0f, 1f)
        player.volume = clamped
        _playerState.update { it.copy(volume = clamped) }
    }

    fun atenuarParaVoz(reducir: Boolean) {
        val targetVolume = if (reducir) 0.15f else 1.0f
        setVolumen(targetVolume)
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0L, player.duration.coerceAtLeast(0L)))
    }

    fun liberar() {
        stopProgressTracker()
        player.release()
        AppLogger.info("ReproductorManager", "Recursos de audio liberados.")
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _playerState.update {
                        it.copy(
                            currentPositionMs = player.currentPosition,
                            durationMs = if (player.duration > 0) player.duration else it.durationMs
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
