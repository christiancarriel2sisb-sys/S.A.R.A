package com.sara.robot.data

import android.content.Context
import com.sara.robot.core.AppLogger
import com.sara.robot.data.local.database.DatabaseSeeder
import com.sara.robot.data.local.database.SaraAppDatabase
import com.sara.robot.data.local.entity.ChatEntity
import com.sara.robot.data.local.entity.ContactoEmergenciaEntity
import com.sara.robot.data.local.entity.PacienteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Repositorio del Chat Privado y Contactos Familiares para SARA.
 * Gestiona la inserción y lectura de mensajes entre el Paciente y sus Familiares
 * con persistencia 100% local en SQLite (Room).
 */
class ChatRepository(context: Context) {

    private val db = SaraAppDatabase.getDatabase(context)
    private val chatDao = db.chatDao()
    private val contactoDao = db.contactoEmergenciaDao()
    private val pacienteDao = db.pacienteDao()

    /**
     * Envía un mensaje dictado por voz desde el Paciente hacia un familiar.
     */
    suspend fun enviarMensajeDesdePaciente(
        contactoNombre: String,
        texto: String
    ): Result<ChatEntity> = withContext(Dispatchers.IO) {
        val cleanName = contactoNombre.trim()
        val cleanText = texto.trim()

        if (cleanText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("El mensaje no puede estar vacío"))
        }

        // Buscar contacto de forma inteligente (insensible a tildes, soporta nombre, apellido o nombre completo)
        val contacto = buscarContactoPorNombre(cleanName)
            ?: return@withContext Result.failure(NoSuchElementException("No se encontró a '$cleanName' en los contactos familiares"))

        val nuevoMensaje = ChatEntity(
            emisor = "PACIENTE",
            mensaje = cleanText,
            fecha_hora = System.currentTimeMillis(),
            leido = true, // El paciente ya conoce su propio mensaje
            Paciente_id_paciente = contacto.Paciente_Id_paciente,
            Contacto_emergencia_id_contacto = contacto.id_contacto
        )

        val idInsertado = chatDao.insertarMensaje(nuevoMensaje)
        val mensajeGuardado = nuevoMensaje.copy(id_chat = idInsertado)
        AppLogger.success("ChatRepo", "Mensaje del Paciente guardado para '${contacto.nombre}': \"$cleanText\" (ID: $idInsertado)")
        Result.success(mensajeGuardado)
    }

    /**
     * Envía un mensaje escrito desde la vista del Familiar hacia el Paciente.
     */
    suspend fun enviarMensajeDesdeFamiliar(
        contactoId: Long,
        texto: String
    ): Result<ChatEntity> = withContext(Dispatchers.IO) {
        val cleanText = texto.trim()
        if (cleanText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("El mensaje no puede estar vacío"))
        }

        val contacto = contactoDao.getContactoById(contactoId)
            ?: return@withContext Result.failure(NoSuchElementException("Contacto no encontrado"))

        val nuevoMensaje = ChatEntity(
            emisor = "FAMILIAR",
            mensaje = cleanText,
            fecha_hora = System.currentTimeMillis(),
            leido = false, // Pendiente de que Sara lo lea al paciente
            Paciente_id_paciente = contacto.Paciente_Id_paciente,
            Contacto_emergencia_id_contacto = contacto.id_contacto
        )

        val idInsertado = chatDao.insertarMensaje(nuevoMensaje)
        val mensajeGuardado = nuevoMensaje.copy(id_chat = idInsertado)
        AppLogger.success("ChatRepo", "Mensaje del Familiar '${contacto.nombre}' guardado: \"$cleanText\" (ID: $idInsertado)")
        Result.success(mensajeGuardado)
    }

    fun getMensajesPorContacto(contactoId: Long): Flow<List<ChatEntity>> {
        return chatDao.getMensajesPorContacto(contactoId)
    }

    fun getMensajesNoLeidosParaPaciente(): Flow<List<ChatEntity>> {
        return chatDao.getMensajesNoLeidosParaPaciente()
    }

    suspend fun getMensajesNoLeidosParaPacienteDirecto(): List<ChatEntity> = withContext(Dispatchers.IO) {
        chatDao.getMensajesNoLeidosParaPacienteDirecto()
    }

    suspend fun marcarMensajeComoLeido(idChat: Long) = withContext(Dispatchers.IO) {
        chatDao.marcarComoLeido(idChat)
        AppLogger.info("ChatRepo", "Mensaje ID $idChat marcado como leído")
    }

    fun getContactos(): Flow<List<ContactoEmergenciaEntity>> {
        return contactoDao.getAllContactos()
    }

    private fun normalizeText(input: String): String {
        return Normalizer.normalize(input.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun getContactosDirecto(): List<ContactoEmergenciaEntity> = withContext(Dispatchers.IO) {
        contactoDao.getAllContactosDirecto()
    }

    suspend fun buscarContactoPorNombre(nombre: String): ContactoEmergenciaEntity? = withContext(Dispatchers.IO) {
        val target = normalizeText(nombre)
        if (target.isBlank()) return@withContext null

        val todos = contactoDao.getAllContactosDirecto()
        // 1. Coincidencia exacta por nombre completo, nombre o apellido
        todos.firstOrNull { c ->
            val nom = normalizeText(c.nombre)
            val ape = normalizeText(c.apellido)
            val full = "$nom $ape".trim()
            target == full || target == nom || target == ape
        }?.let { return@withContext it }

        // 2. Coincidencia por inclusión (ej. "mi hija claudia perez" -> contiene "claudia")
        val targetWords = target.split(" ").filter { it.length >= 3 }
        todos.firstOrNull { c ->
            val nom = normalizeText(c.nombre)
            val ape = normalizeText(c.apellido)
            val full = "$nom $ape".trim()
            target.contains(full) || full.contains(target) ||
                    (nom.isNotBlank() && nom in targetWords) ||
                    (ape.isNotBlank() && ape in targetWords)
        }?.let { return@withContext it }

        // 3. Fallback a consulta SQL
        contactoDao.findContactoByName(nombre.trim())
    }

    suspend fun getContactoById(id: Long): ContactoEmergenciaEntity? = withContext(Dispatchers.IO) {
        contactoDao.getContactoById(id)
    }

    suspend fun getPacienteById(id: Long): PacienteEntity? = withContext(Dispatchers.IO) {
        pacienteDao.getPacienteById(id)
    }

    suspend fun getPaciente(): PacienteEntity? = withContext(Dispatchers.IO) {
        pacienteDao.getPacienteById(1L) ?: pacienteDao.getFirstActivePaciente()
    }

    /**
     * Inicializa el conjunto completo de datos en SQLite si la base de datos está vacía
     * (50 pacientes, 50 familiares, 10 recordatorios, 5 juegos y 125 sesiones de estimulación).
     */
    suspend fun seedDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        DatabaseSeeder.seedIfNeeded(db)
    }
}
