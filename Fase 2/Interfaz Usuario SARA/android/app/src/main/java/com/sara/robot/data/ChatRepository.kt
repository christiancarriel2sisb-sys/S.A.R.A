package com.sara.robot.data

import android.content.Context
import com.sara.robot.core.AppLogger
import com.sara.robot.data.local.database.SaraAppDatabase
import com.sara.robot.data.local.entity.ChatEntity
import com.sara.robot.data.local.entity.ContactoEmergenciaEntity
import com.sara.robot.data.local.entity.PacienteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

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

        // Buscar contacto por coincidencia de nombre (ej. "Claudia")
        val contacto = contactoDao.findContactoByName(cleanName)
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

    suspend fun buscarContactoPorNombre(nombre: String): ContactoEmergenciaEntity? = withContext(Dispatchers.IO) {
        contactoDao.findContactoByName(nombre)
    }

    suspend fun getContactoById(id: Long): ContactoEmergenciaEntity? = withContext(Dispatchers.IO) {
        contactoDao.getContactoById(id)
    }

    suspend fun getPaciente(): PacienteEntity? = withContext(Dispatchers.IO) {
        pacienteDao.getPacienteById(1L)
    }

    /**
     * Inicializa datos base si la base de datos está vacía,
     * para asegurar que la app sea inmediatamente operable para pruebas de chat.
     */
    suspend fun seedDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val pacienteExistente = pacienteDao.getPacienteById(1L)
            if (pacienteExistente == null) {
                val pacienteDefecto = PacienteEntity(
                    Id_paciente = 1L,
                    Nombre = "Christian",
                    ape_p = "Carriel",
                    ape_m = "González",
                    Fech_nac = "1948-05-14",
                    edad = 78,
                    genero = "Masculino"
                )
                pacienteDao.insertPaciente(pacienteDefecto)
                AppLogger.success("ChatRepo", "Paciente base creado: Christian Carriel")

                val contacto1 = ContactoEmergenciaEntity(
                    id_contacto = 1L,
                    nombre = "Claudia",
                    apellido = "Pérez",
                    numero_tel = 987654321L,
                    Paciente_Id_paciente = 1L
                )
                val contacto2 = ContactoEmergenciaEntity(
                    id_contacto = 2L,
                    nombre = "Pedro",
                    apellido = "Pérez",
                    numero_tel = 912345678L,
                    Paciente_Id_paciente = 1L
                )
                contactoDao.insertContacto(contacto1)
                contactoDao.insertContacto(contacto2)
                AppLogger.success("ChatRepo", "Contactos familiares creados: Claudia Pérez y Pedro Pérez")
            }
        } catch (e: Exception) {
            AppLogger.warn("ChatRepo", "Aviso en inicialización de datos: ${e.message}")
        }
    }
}
