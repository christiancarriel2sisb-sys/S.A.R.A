package com.sara.robot.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import com.sara.robot.core.AppLogger
import com.sara.robot.data.local.database.SaraAppDatabase
import com.sara.robot.data.local.entity.BiometriaEntity
import com.sara.robot.data.local.entity.PacienteEntity
import com.sara.robot.vision.MobileFaceNetHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Perfil biométrico cargado en RAM para comparación instantánea (<0.05 ms) en tiempo real.
 */
data class RegisteredPersonProfile(
    val paciente: PacienteEntity,
    val biometria: BiometriaEntity,
    val embedding: FloatArray
)

/**
 * Repositorio de Biometría Facial y Consentimiento Informado.
 * - Orquesta la creación de personas y su vector biométrico (192-d) en SQLite (relación 1:1).
 * - No asume ni adivina nombres por defecto: todo rostro desconocido es "Sin registro"
 *   hasta que otorga su consentimiento e ingresa su nombre real.
 * - Mantiene los perfiles registrados en RAM para diferenciar múltiples rostros simultáneos.
 */
class BiometriaRepository(
    private val context: Context,
    val mobileFaceNetHelper: MobileFaceNetHelper = MobileFaceNetHelper(context)
) {

    private val db = SaraAppDatabase.getDatabase(context)
    private val biometriaDao = db.biometriaDao()
    private val pacienteDao = db.pacienteDao()

    companion object {
        private const val PREF_KEY_CLEAN_START_V2 = "biometria_clean_start_v2"
    }

    /**
     * Observa en tiempo real todos los perfiles que tienen biometría registrada en SQLite,
     * pre-convirtiendo sus vectores de 192 floats para identificación multirrostro instantánea.
     */
    fun observePerfilesRegistrados(): Flow<List<RegisteredPersonProfile>> {
        return combine(
            biometriaDao.observeAllBiometrias(),
            pacienteDao.getAllPacientes()
        ) { biometrias, pacientes ->
            val pacientesMap = pacientes.associateBy { it.Id_paciente }
            biometrias.mapNotNull { bio ->
                val pac = pacientesMap[bio.id_paciente]
                val vec = mobileFaceNetHelper.stringToEmbedding(bio.vector_biometrico)
                if (pac != null && vec.size == MobileFaceNetHelper.EMBEDDING_SIZE) {
                    RegisteredPersonProfile(
                        paciente = pac,
                        biometria = bio,
                        embedding = vec
                    )
                } else {
                    null
                }
            }
        }
    }

    fun observeBiometria(pacienteId: Long): Flow<BiometriaEntity?> {
        return biometriaDao.observeBiometriaPorPaciente(pacienteId)
    }

    suspend fun getBiometria(pacienteId: Long): BiometriaEntity? = withContext(Dispatchers.IO) {
        biometriaDao.getBiometriaPorPaciente(pacienteId)
    }

    /**
     * Limpia registros pre-sembrados antiguos una única vez para que el módulo no adivine
     * ningún nombre inicial ("Christian" / "Juan") y todo rostro nuevo inicie como "Sin registro".
     */
    suspend fun asegurarInicioSinNombresPreestablecidos(prefs: SharedPreferences) = withContext(Dispatchers.IO) {
        if (!prefs.getBoolean(PREF_KEY_CLEAN_START_V2, false)) {
            biometriaDao.deleteAll()
            prefs.edit().putBoolean(PREF_KEY_CLEAN_START_V2, true).apply()
            AppLogger.info("Biometria", "Base biométrica inicializada limpia (0 rostros pre-asumidos). Todo rostro desconocido iniciará como 'Sin registro'.")
        }
    }

    /**
     * Registra una nueva persona desde un rostro "Sin registro":
     * 1. Extrae el vector de 192 floats con MobileFaceNet.
     * 2. Crea o actualiza el registro en la tabla Paciente con el nombre ingresado.
     * 3. Inserta el vector en la tabla biometria_paciente (relación 1:1 con Id_paciente).
     */
    suspend fun registrarNuevoPacienteConBiometria(
        nombre: String,
        apellido: String = "",
        faceBitmap: Bitmap
    ): Result<PacienteEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanNombre = nombre.trim()
            val cleanApellido = apellido.trim().ifEmpty { "Registrado" }
            if (cleanNombre.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("El nombre no puede estar vacío"))
            }

            AppLogger.info("Biometria", "Iniciando registro biométrico para nueva persona: '$cleanNombre $cleanApellido'...")

            // 1. Inferencia con MobileFaceNet
            val embedding = mobileFaceNetHelper.extractEmbedding(faceBitmap)
                ?: return@withContext Result.failure(Exception("MobileFaceNet no pudo generar el vector facial"))

            AppLogger.info(
                "Biometria",
                "Vector biométrico (192-d) extraído exitosamente con MobileFaceNet (Norma L2 = 1.0)"
            )

            // 2. Crear entidad Paciente con un ID único en SQLite
            val pacientesExistentes = pacienteDao.getAllPacientes().first()
            val nuevoId = (pacientesExistentes.maxOfOrNull { it.Id_paciente } ?: 0L) + 1L

            val nuevoPaciente = PacienteEntity(
                Id_paciente = nuevoId,
                Nombre = cleanNombre,
                ape_p = cleanApellido,
                ape_m = "",
                Fech_nac = "1990-01-01",
                edad = 30,
                genero = "N/A"
            )
            pacienteDao.insertPaciente(nuevoPaciente)
            AppLogger.info(
                "Biometria",
                "Paciente creado en tabla Paciente: ID=$nuevoId | Nombre='$cleanNombre $cleanApellido'"
            )

            // 3. Serializar vector a CSV y guardar en biometria_paciente (Relación 1 a 1)
            val embeddingCsv = mobileFaceNetHelper.embeddingToString(embedding)
            val bioEntity = BiometriaEntity(
                id_paciente = nuevoId,
                vector_biometrico = embeddingCsv,
                consentimiento_otorgado = true,
                fecha_registro = System.currentTimeMillis()
            )

            val idBiometria = biometriaDao.insertBiometria(bioEntity)
            AppLogger.success(
                "Biometria",
                "Datos biométricos almacenados en la base de datos: ID_Biometria=$idBiometria | Paciente_ID=$nuevoId ($cleanNombre) | 192 floats persistidos"
            )

            Result.success(nuevoPaciente)
        } catch (e: Exception) {
            AppLogger.error("Biometria", "Error al registrar persona y biometría en BD: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun registrarBiometria(
        pacienteId: Long,
        faceBitmap: Bitmap
    ): Result<BiometriaEntity> = withContext(Dispatchers.IO) {
        try {
            AppLogger.info("Biometria", "Iniciando extracción biométrica para Paciente ID=$pacienteId...")

            val embedding = mobileFaceNetHelper.extractEmbedding(faceBitmap)
                ?: return@withContext Result.failure(Exception("MobileFaceNet no pudo generar el vector facial"))

            val embeddingCsv = mobileFaceNetHelper.embeddingToString(embedding)

            val entity = BiometriaEntity(
                id_paciente = pacienteId,
                vector_biometrico = embeddingCsv,
                consentimiento_otorgado = true,
                fecha_registro = System.currentTimeMillis()
            )

            val idInsertado = biometriaDao.insertBiometria(entity)
            AppLogger.success(
                "Biometria",
                "Datos biométricos almacenados en la base de datos: ID_Biometria=$idInsertado | Paciente_ID=$pacienteId | 192 floats persistidos"
            )

            Result.success(entity.copy(id_biometria = idInsertado))
        } catch (e: Exception) {
            AppLogger.error("Biometria", "Error al almacenar datos biométricos en BD: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun eliminarBiometria(pacienteId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = biometriaDao.deleteBiometriaPorPaciente(pacienteId)
            AppLogger.info("Biometria", "Registro biométrico eliminado de la base de datos para Paciente ID=$pacienteId (filas: $rows)")
            rows > 0
        } catch (e: Exception) {
            AppLogger.error("Biometria", "Error eliminando registro biométrico: ${e.message}")
            false
        }
    }

    suspend fun eliminarTodasLasBiometrias(): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = biometriaDao.deleteAll()
            AppLogger.info("Biometria", "Todos los registros biométricos fueron eliminados de SQLite (filas: $rows)")
            rows > 0
        } catch (e: Exception) {
            AppLogger.error("Biometria", "Error limpiando tabla biométrica: ${e.message}")
            false
        }
    }
}
