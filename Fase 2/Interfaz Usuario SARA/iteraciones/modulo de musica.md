# Guía Técnica: Módulo de Reproducción de Música por Voz para Sara (Kotlin + YouTube + ExoPlayer)

Esta guía detalla los pasos necesarios para integrar un sistema de control multimedia por comandos de voz en tu aplicación de Android (Kotlin) utilizando **Gemini (Function Calling)**, la **YouTube Data API (v3)** y **Media3 ExoPlayer**.

---

## Requisitos Previos
1. Un proyecto en Android Studio configurado con Kotlin.
2. Una cuenta en Google Cloud Console para generar tu API Key de YouTube.

---

## Paso 1: Obtener y Asegurar tu API Key de Google Cloud
1. Entra a [Google Cloud Console](https://console.cloud.google.com/).
2. Crea un nuevo proyecto (ej. `SaraMultimedia`).
3. Ve a **APIs y servicios > Biblioteca**, busca **YouTube Data API v3** y haz clic en **Habilitar**.
4. Ve a **APIs y servicios > Credenciales > Crear credenciales > Clave de API**.
5. *(Opcional pero recomendado)* Restringe la API Key seleccionando "Restricciones de Android" e ingresa el nombre de tu paquete (`com.tuorganizacion.sara`) y tu huella SHA-1 para mayor seguridad.

---

## Paso 2: Agregar Dependencias en `build.gradle.kts`
Asegúrate de incluir las librerías necesarias para ExoPlayer (Media3) y las herramientas de red que prefieras (Retrofit o Ktor para las peticiones HTTP):

```kotlin
dependencies {
    // ExoPlayer (Media3) para reproducción de audio en segundo plano
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")

    // Librería de red (ej. Retrofit o Gson para parsear el JSON de YouTube)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}
```

---

## Paso 3: Configurar Gemini con Function Calling (Tools)
Define la herramienta en tu lógica de Gemini para que la IA extraiga la intención de reproducción cuando le hables al robot:

```kotlin
val reproductorTool = FunctionDeclaration.newBuilder()
    .setName("controlar_multimedia")
    .setDescription("Reproduce música, pausa o busca canciones según la petición del usuario.")
    .setParameters(
        Schema.newBuilder()
            .setType(Type.OBJECT)
            .putProperties("accion", Schema.newBuilder().setType(Type.STRING).addEnum("reproducir").addEnum("pausar").build())
            .putProperties("query", Schema.newBuilder().setType(Type.STRING).setDescription("Nombre de la canción, artista o género a buscar").build())
            .addRequired("accion")
            .build()
    )
    .build()
```

---

## Paso 4: Implementar la Búsqueda en la YouTube Data API
Crea una función en Kotlin para consultar el ID del video usando tu API Key:

```kotlin
import retrofit2.http.GET
import retrofit2.http.Query

data *class* YouTubeSearchResponse(val items: List<VideoItem>)
data *class* VideoItem(val id: VideoId)
data *class* VideoId(val videoId: String)

interface YouTubeApiService {
    @GET("youtube/v3/search")
    suspend fun buscarVideo(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}
```

*Nota:* Para obtener la URL directa de streaming de audio a partir del `videoId`, puedes apoyarte en un pequeño servicio intermediario o extractor ligero compatible con tu arquitectura.

---

## Paso 5: Configurar y Reproducir con ExoPlayer
Inicializa ExoPlayer en tu app con una configuración de búfer optimizada para reducir la latencia de inicio:

```kotlin
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

class ReproductorManager(context: Context) {

    // Búfer optimizado para menor latencia (inicio rápido)
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(500, 1500, 250, 500)
        .build()

    private val player = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .build()

    fun reproducirAudio(audioUrl: String) {
        val mediaItem = MediaItem.fromUri(audioUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun liberar() {
        player.release()
    }
}
```

---

## Resumen del Flujo
1. **Comando de voz:** Dices *"Sara, pon música de jazz"*.
2. **Gemini:** Devuelve el JSON con la acción `reproducir` y el query `"jazz"`.
3. **YouTube API:** Busca el primer resultado y extrae el identificador.
4. **ExoPlayer:** Recibe el enlace de audio optimizado y lo reproduce de forma inmediata (latencia estimada de 1.5 a 3 segundos).