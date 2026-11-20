package com.sara.robot.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sara.robot.core.AppLogger
import com.sara.robot.core.ModuleMonitorStatus
import com.sara.robot.core.ModuleOperationalState
import com.sara.robot.core.SaraModule
import com.sara.robot.data.local.database.SaraAppDatabase
import com.sara.robot.data.local.entity.ContactoEmergenciaEntity
import com.sara.robot.data.local.entity.PacienteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AdminSidebarTool(val title: String, val icon: ImageVector) {
    PACIENTES("Pacientes", Icons.Default.PersonalInjury),
    FAMILIARES("Familiares", Icons.Default.FamilyRestroom),
    RECORDATORIOS("Recordatorios", Icons.Default.Alarm),
    MUSICA("Música", Icons.Default.MusicNote),
    CONTROL_SARA("Control de Sara", Icons.Default.MonitorHeart)
}

/**
 * ============================================================================
 * MÓDULO REUTILIZABLE DE BÚSQUEDA DE PACIENTES (POR RUT / ID, NOMBRE O APELLIDO)
 * ============================================================================
 * Se utiliza tanto en la gestión de Pacientes (para ver ficha, modificar o inactivar)
 * como en la gestión de Familiares (para asociar y filtrar contactos de emergencia).
 */
@Composable
fun ReusablePatientSearchModule(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    matchingPatients: List<PacienteEntity>,
    selectedPatient: PacienteEntity?,
    onSelectPatient: (PacienteEntity) -> Unit,
    compactMode: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = {
                Text(
                    text = "🔍 Buscar Paciente por RUT (ID), Nombre o Apellido...",
                    fontSize = 11.sp
                )
            },
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color.Gray)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00F0FF),
                unfocusedBorderColor = Color(0xFF3A3A5A)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (compactMode) {
            // En modo compacto (pestaña Familiares), muestra chips horizontales seleccionables con los pacientes encontrados
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resultados (${matchingPatients.size}):",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(matchingPatients, key = { it.Id_paciente }) { pac ->
                        val isSel = (selectedPatient?.Id_paciente == pac.Id_paciente)
                        Surface(
                            color = if (isSel) Color(0x3300F0FF) else Color(0xFF151528),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSel) Color(0xFF00F0FF) else Color(0x22FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPatient(pac) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "👤 ${pac.Nombre} ${pac.ape_p} ${pac.ape_m}",
                                    color = if (isSel) Color(0xFF00F0FF) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "RUT/ID: ${pac.Id_paciente}",
                                    color = Color(0xFFFFD32A),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ============================================================================
 * PANEL INTERACTIVO DEL ADMINISTRADOR (MONITOREO DE PACIENTES, FAMILIARES Y CONTROL DE SARA)
 * ============================================================================
 */
@Composable
fun AdminDashboardScreen(
    viewModel: SaraViewModel,
    onExitToRoleSelection: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { SaraAppDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()

    var currentTool by remember { mutableStateOf(AdminSidebarTool.PACIENTES) }
    var searchQuery by remember { mutableStateOf("") }

    // Lista reactiva de pacientes activos (estado == true) filtrados por RUT, Nombre o Apellido
    val activePatients by remember(searchQuery) {
        if (searchQuery.isBlank()) {
            db.pacienteDao().getAllPacientes()
        } else {
            db.pacienteDao().buscarPacientesActivos(searchQuery.trim())
        }
    }.collectAsState(initial = emptyList())

    var selectedPatient by remember { mutableStateOf<PacienteEntity?>(null) }

    // Sincronizar paciente seleccionado cuando cambia la lista
    LaunchedEffect(activePatients) {
        if (selectedPatient != null) {
            selectedPatient = activePatients.firstOrNull { it.Id_paciente == selectedPatient?.Id_paciente }
                ?: activePatients.firstOrNull()
        } else {
            selectedPatient = activePatients.firstOrNull()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070712))
    ) {
        // --- BARRA LATERAL IZQUIERDA (SIDEBAR DE HERRAMIENTAS DEL ADMIN) ---
        Surface(
            color = Color(0xFF0E0E1F),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier
                .width(170.dp)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Cabecera del Panel Admin
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFF00F0FF),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "S.A.R.A ADMIN",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Monitoreo Clínico",
                                color = Color(0xFF00F0FF),
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF))
                    Spacer(modifier = Modifier.height(2.dp))

                    // Los 5 Módulos de la Barra Lateral
                    for (tool in AdminSidebarTool.entries) {
                        val isSelected = (currentTool == tool)
                        Surface(
                            color = if (isSelected) Color(0x2800F0FF) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFF00F0FF)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentTool = tool }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = if (isSelected) Color(0xFF00F0FF) else Color.LightGray,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = tool.title,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                // Botón inferior para salir a selección de perfiles
                OutlinedButton(
                    onClick = onExitToRoleSelection,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4757)),
                    border = BorderStroke(1.dp, Color(0xFFFF4757).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cambiar Perfil", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- ÁREA DE CONTENIDO PRINCIPAL SEGÚN LA HERRAMIENTA SELECCIONADA ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (currentTool) {
                AdminSidebarTool.PACIENTES -> {
                    AdminPacientesSection(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        patients = activePatients,
                        selectedPatient = selectedPatient,
                        onSelectPatient = { selectedPatient = it },
                        onSaveNewPatient = { nuevo ->
                            coroutineScope.launch(Dispatchers.IO) {
                                db.pacienteDao().insertPaciente(nuevo)
                                AppLogger.success("AdminPacientes", "Nuevo paciente registrado en BD: RUT=${nuevo.Id_paciente} (${nuevo.Nombre} ${nuevo.ape_p})")
                                withContext(Dispatchers.Main) {
                                    selectedPatient = nuevo
                                    Toast.makeText(context, "Paciente agregado a la base de datos", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onUpdatePatient = { editado ->
                            coroutineScope.launch(Dispatchers.IO) {
                                db.pacienteDao().updatePaciente(editado)
                                AppLogger.success("AdminPacientes", "Paciente actualizado en BD: RUT=${editado.Id_paciente} (${editado.Nombre} ${editado.ape_p})")
                                withContext(Dispatchers.Main) {
                                    selectedPatient = editado
                                    Toast.makeText(context, "Datos del paciente actualizados", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeactivatePatient = { pac ->
                            coroutineScope.launch(Dispatchers.IO) {
                                db.pacienteDao().desactivarPaciente(pac.Id_paciente)
                                AppLogger.warn("AdminPacientes", "Paciente RUT=${pac.Id_paciente} (${pac.Nombre}) pasado a estado INACTIVO (estado=false). Sus datos se conservan en BD.")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Paciente '${pac.Nombre}' dejado en estado INACTIVO", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                AdminSidebarTool.FAMILIARES -> {
                    AdminFamiliaresSection(
                        db = db,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        matchingPatients = activePatients,
                        selectedPatient = selectedPatient,
                        onSelectPatient = { selectedPatient = it }
                    )
                }

                AdminSidebarTool.RECORDATORIOS -> {
                    ReminderScreen(
                        onSwitchToRobotFace = { currentTool = AdminSidebarTool.CONTROL_SARA },
                        onSwitchToVision = { currentTool = AdminSidebarTool.CONTROL_SARA },
                        onSwitchToNavigation = { currentTool = AdminSidebarTool.CONTROL_SARA },
                        onTestSaraVoice = { msg -> viewModel.hablarRecordatorio(msg) },
                        recordatorioRepository = viewModel.recordatorioRepository
                    )
                }

                AdminSidebarTool.MUSICA -> {
                    MusicScreen(
                        viewModel = viewModel,
                        onSwitchToRobotFace = { currentTool = AdminSidebarTool.CONTROL_SARA },
                        onSwitchToVision = { currentTool = AdminSidebarTool.CONTROL_SARA },
                        onSwitchToNavigation = { currentTool = AdminSidebarTool.CONTROL_SARA },
                        onSwitchToReminders = { currentTool = AdminSidebarTool.RECORDATORIOS }
                    )
                }

                AdminSidebarTool.CONTROL_SARA -> {
                    AdminControlSaraSection()
                }
            }
        }
    }
}

/**
 * ============================================================================
 * 1. SECCIÓN DE GESTIÓN DE PACIENTES (BÚSQUEDA POR RUT/NOMBRE/APELLIDO + FICHA + CRUD LÓGICO)
 * ============================================================================
 */
@Composable
private fun AdminPacientesSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    patients: List<PacienteEntity>,
    selectedPatient: PacienteEntity?,
    onSelectPatient: (PacienteEntity) -> Unit,
    onSaveNewPatient: (PacienteEntity) -> Unit,
    onUpdatePatient: (PacienteEntity) -> Unit,
    onDeactivatePatient: (PacienteEntity) -> Unit
) {
    val context = LocalContext.current
    // formMode: null = cerrado, "ADD" = agregar nuevo, "EDIT" = modificar existente
    var formMode by remember { mutableStateOf<String?>(null) }

    var formRut by remember { mutableStateOf("") }
    var formNombre by remember { mutableStateOf("") }
    var formApeP by remember { mutableStateOf("") }
    var formApeM by remember { mutableStateOf("") }
    var formFechaNac by remember { mutableStateOf("1950-05-15") }
    var formEdad by remember { mutableStateOf("75") }
    var formGenero by remember { mutableStateOf("Masculino") }

    fun abrirFormularioAgregar() {
        formRut = ""
        formNombre = ""
        formApeP = ""
        formApeM = ""
        formFechaNac = "1950-05-15"
        formEdad = "75"
        formGenero = "Masculino"
        formMode = "ADD"
    }

    fun abrirFormularioEditar(pac: PacienteEntity) {
        formRut = pac.Id_paciente.toString()
        formNombre = pac.Nombre
        formApeP = pac.ape_p
        formApeM = pac.ape_m
        formFechaNac = pac.Fech_nac
        formEdad = pac.edad.toString()
        formGenero = pac.genero
        formMode = "EDIT"
    }

    Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Módulo de Búsqueda de Paciente (RUT, Nombre, Apellido)
            ReusablePatientSearchModule(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                matchingPatients = patients,
                selectedPatient = selectedPatient,
                onSelectPatient = onSelectPatient,
                compactMode = false
            )

            // Barra de los 3 Botones arriba de la lista: Agregar | Modificar | Eliminar (Inactivar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { abrirFormularioAgregar() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ED573)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar Paciente", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        val target = selectedPatient
                        if (target == null) {
                            Toast.makeText(context, "Busca y selecciona un paciente de la lista primero", Toast.LENGTH_SHORT).show()
                        } else {
                            abrirFormularioEditar(target)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Modificar Paciente", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        val target = selectedPatient
                        if (target == null) {
                            Toast.makeText(context, "Selecciona un paciente para dejarlo inactivo", Toast.LENGTH_SHORT).show()
                        } else {
                            onDeactivatePatient(target)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PersonOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eliminar (Inactivar)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // Vista Dividida: Izquierda = Lista de Pacientes Inscritos | Derecha = Ficha Clínica del Paciente Seleccionado
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Lista de Pacientes Activos
                Surface(
                    color = Color(0xFF0D0D1D),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        Text(
                            text = "PACIENTES ACTIVOS INSCRITOS (${patients.size})",
                            color = Color(0xFF00F0FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (patients.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No se encontraron pacientes activos con ese criterio.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(patients, key = { it.Id_paciente }) { pac ->
                                    val isSelected = (selectedPatient?.Id_paciente == pac.Id_paciente)
                                    Surface(
                                        color = if (isSelected) Color(0x2800F0FF) else Color(0xFF15152A),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFF00F0FF) else Color(0x22FFFFFF)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectPatient(pac) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "${pac.Nombre} ${pac.ape_p} ${pac.ape_m}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "RUT (ID): ${pac.Id_paciente} • ${pac.edad} años",
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Surface(
                                                color = Color(0x222ED573),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVO",
                                                    color = Color(0xFF2ED573),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Ficha Clínica Detallada del Paciente Seleccionado
                Surface(
                    color = Color(0xFF0D0D1D),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0x4400F0FF)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (selectedPatient == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Selecciona un paciente de la lista para ver su ficha técnica",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        val p = selectedPatient
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = Color(0xFF00F0FF),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "FICHA DE PACIENTE EN BASE DE DATOS",
                                    color = Color(0xFF00F0FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            HorizontalDivider(color = Color(0x22FFFFFF))

                            FichaRow("RUT (Id_paciente):", p.Id_paciente.toString())
                            FichaRow("Nombre:", p.Nombre)
                            FichaRow("Apellido Paterno:", p.ape_p)
                            FichaRow("Apellido Materno:", p.ape_m)
                            FichaRow("Fecha de Nacimiento:", p.Fech_nac)
                            FichaRow("Edad:", "${p.edad} años")
                            FichaRow("Género:", p.genero)
                            FichaRow("Estado en Plataforma:", if (p.estado) "ACTIVO (Habilitado en Telemetría)" else "INACTIVO")
                        }
                    }
                }
            }
        }

        // Modal Formulario Agregar / Modificar Paciente
        if (formMode != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF121226),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF00F0FF)),
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (formMode == "ADD") "AGREGAR NUEVO PACIENTE A SQLITE" else "MODIFICAR DATOS DEL PACIENTE",
                            color = Color(0xFF00F0FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = formRut,
                                onValueChange = { formRut = it.filter { ch -> ch.isDigit() } },
                                label = { Text("RUT / ID Numérico (ej: 12345678)", fontSize = 11.sp) },
                                enabled = (formMode == "ADD"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    disabledTextColor = Color.LightGray
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = formNombre,
                                onValueChange = { formNombre = it },
                                label = { Text("Nombre", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = formApeP,
                                onValueChange = { formApeP = it },
                                label = { Text("Apellido Paterno", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = formApeM,
                                onValueChange = { formApeM = it },
                                label = { Text("Apellido Materno", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = formFechaNac,
                                onValueChange = { formFechaNac = it },
                                label = { Text("Fecha Nac (YYYY-MM-DD)", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = formEdad,
                                onValueChange = { formEdad = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Edad", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(0.6f)
                            )
                            OutlinedTextField(
                                value = formGenero,
                                onValueChange = { formGenero = it },
                                label = { Text("Género", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { formMode = null },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar", color = Color.LightGray)
                            }
                            Button(
                                onClick = {
                                    val rutLong = formRut.toLongOrNull()
                                    val edadInt = formEdad.toIntOrNull() ?: 70
                                    if (rutLong == null || formNombre.isBlank() || formApeP.isBlank()) {
                                        Toast.makeText(context, "Completa RUT numérico, Nombre y Apellido Paterno", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val entity = PacienteEntity(
                                        Id_paciente = rutLong,
                                        Nombre = formNombre.trim(),
                                        ape_p = formApeP.trim(),
                                        ape_m = formApeM.trim(),
                                        Fech_nac = formFechaNac.trim(),
                                        edad = edadInt,
                                        genero = formGenero.trim(),
                                        estado = true
                                    )
                                    if (formMode == "ADD") {
                                        onSaveNewPatient(entity)
                                    } else {
                                        onUpdatePatient(entity)
                                    }
                                    formMode = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ED573)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Guardar en Base de Datos", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FichaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF15152A), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.LightGray, fontSize = 11.5.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/**
 * ============================================================================
 * 2. SECCIÓN DE GESTIÓN DE FAMILIARES (USA MÓDULO DE BÚSQUEDA DE PACIENTE + CRUD LÓGICO)
 * ============================================================================
 */
@Composable
private fun AdminFamiliaresSection(
    db: SaraAppDatabase,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    matchingPatients: List<PacienteEntity>,
    selectedPatient: PacienteEntity?,
    onSelectPatient: (PacienteEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val contactosList by remember(selectedPatient) {
        val pacId = selectedPatient?.Id_paciente ?: -1L
        db.contactoEmergenciaDao().getContactosByPaciente(pacId)
    }.collectAsState(initial = emptyList())

    var selectedContacto by remember { mutableStateOf<ContactoEmergenciaEntity?>(null) }

    LaunchedEffect(contactosList) {
        selectedContacto = contactosList.firstOrNull { it.id_contacto == selectedContacto?.id_contacto }
            ?: contactosList.firstOrNull()
    }

    // formMode: null, "ADD", "EDIT"
    var contactFormMode by remember { mutableStateOf<String?>(null) }
    var formNombre by remember { mutableStateOf("") }
    var formApellido by remember { mutableStateOf("") }
    var formTelefono by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Arriba: Módulo reutilizable de búsqueda de paciente
            Surface(
                color = Color(0xFF101022),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0x3300F0FF))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReusablePatientSearchModule(
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        matchingPatients = matchingPatients,
                        selectedPatient = selectedPatient,
                        onSelectPatient = onSelectPatient,
                        compactMode = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedPatient != null) {
                                "PACIENTE SELECCIONADO: ${selectedPatient.Nombre} ${selectedPatient.ape_p} (RUT: ${selectedPatient.Id_paciente})"
                            } else {
                                "Ningún paciente seleccionado"
                            },
                            color = Color(0xFF00FFCC),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Los 3 Botones de acción para Contactos de Emergencia del paciente seleccionado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (selectedPatient == null) {
                            Toast.makeText(context, "Busca y selecciona primero un paciente arriba", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        formNombre = ""
                        formApellido = ""
                        formTelefono = ""
                        contactFormMode = "ADD"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ED573)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar Contacto", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        val c = selectedContacto
                        if (c == null) {
                            Toast.makeText(context, "Selecciona un contacto de emergencia del cuadro", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        formNombre = c.nombre
                        formApellido = c.apellido
                        formTelefono = c.numero_tel.toString()
                        contactFormMode = "EDIT"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Modificar Contacto", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        val c = selectedContacto
                        if (c == null) {
                            Toast.makeText(context, "Selecciona un contacto para dejarlo inactivo", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            db.contactoEmergenciaDao().desactivarContacto(c.id_contacto)
                            AppLogger.warn("AdminFamiliares", "Contacto '${c.nombre} ${c.apellido}' pasado a INACTIVO (estado=false). Datos preservados en BD.")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Contacto '${c.nombre}' dejado en estado INACTIVO", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eliminar (Inactivar)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // Cuadro de Contactos de Emergencia Asociados al Paciente
            Surface(
                color = Color(0xFF0D0D1D),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0x448A7DFF)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text(
                        text = "CONTACTOS DE EMERGENCIA ACTIVOS ASOCIADOS (${contactosList.size})",
                        color = Color(0xFF8A7DFF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (contactosList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Este paciente no tiene contactos de emergencia activos registrados.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(contactosList, key = { it.id_contacto }) { contacto ->
                                val isSel = (selectedContacto?.id_contacto == contacto.id_contacto)
                                Surface(
                                    color = if (isSel) Color(0x288A7DFF) else Color(0xFF15152A),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSel) Color(0xFF8A7DFF) else Color(0x22FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedContacto = contacto }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContactPhone,
                                                contentDescription = null,
                                                tint = Color(0xFF8A7DFF)
                                            )
                                            Column {
                                                Text(
                                                    text = "${contacto.nombre} ${contacto.apellido}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "Teléfono: +56 ${contacto.numero_tel} • ID Contacto: #${contacto.id_contacto}",
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                        Surface(
                                            color = Color(0x222ED573),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVO",
                                                color = Color(0xFF2ED573),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Modal Formulario Agregar / Modificar Contacto de Emergencia
        if (contactFormMode != null && selectedPatient != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF121226),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF8A7DFF)),
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (contactFormMode == "ADD") {
                                "AGREGAR CONTACTO PARA ${selectedPatient.Nombre.uppercase()}"
                            } else {
                                "MODIFICAR CONTACTO DE EMERGENCIA"
                            },
                            color = Color(0xFF8A7DFF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        OutlinedTextField(
                            value = formNombre,
                            onValueChange = { formNombre = it },
                            label = { Text("Nombre del Familiar", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = formApellido,
                            onValueChange = { formApellido = it },
                            label = { Text("Apellido", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = formTelefono,
                            onValueChange = { formTelefono = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Número de Teléfono (ej: 987654321)", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { contactFormMode = null },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar", color = Color.LightGray)
                            }

                            Button(
                                onClick = {
                                    val telLong = formTelefono.toLongOrNull()
                                    if (formNombre.isBlank() || formApellido.isBlank() || telLong == null) {
                                        Toast.makeText(context, "Completa Nombre, Apellido y Teléfono numérico", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    coroutineScope.launch(Dispatchers.IO) {
                                        if (contactFormMode == "ADD") {
                                            val nuevo = ContactoEmergenciaEntity(
                                                nombre = formNombre.trim(),
                                                apellido = formApellido.trim(),
                                                numero_tel = telLong,
                                                Paciente_Id_paciente = selectedPatient.Id_paciente,
                                                estado = true
                                            )
                                            db.contactoEmergenciaDao().insertContacto(nuevo)
                                            AppLogger.success("AdminFamiliares", "Contacto '${nuevo.nombre}' agregado al paciente RUT=${selectedPatient.Id_paciente}")
                                        } else {
                                            val actual = selectedContacto ?: return@launch
                                            val editado = actual.copy(
                                                nombre = formNombre.trim(),
                                                apellido = formApellido.trim(),
                                                numero_tel = telLong,
                                                estado = true
                                            )
                                            db.contactoEmergenciaDao().updateContacto(editado)
                                            AppLogger.success("AdminFamiliares", "Contacto ID=${editado.id_contacto} actualizado en BD")
                                        }
                                        withContext(Dispatchers.Main) {
                                            contactFormMode = null
                                            Toast.makeText(context, "Contacto guardado en SQLite", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ED573)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Guardar Contacto", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ============================================================================
 * 5. SECCIÓN CONTROL DE SARA (MONITOR DE MÓDULOS POR LOGS INDEPENDIENTES + GENERADOR .MD)
 * ============================================================================
 */
@Composable
private fun AdminControlSaraSection() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val moduleStatuses by AppLogger.moduleStatuses.collectAsState()

    var inspectingModule by remember { mutableStateOf<SaraModule?>(null) }
    var generatedMdReport by remember { mutableStateOf<Triple<SaraModule, String, String>?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cabecera compacta de Telemetría y Leyenda de Luces (altura fija para no desplazar la grilla de módulos)
            Surface(
                color = Color(0xFF101022),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "CONTROL Y ESTADO DE TODOS LOS MÓDULOS DE S.A.R.A (${SaraModule.entries.size} MÓDULOS)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Monitoreo O(1) Activo • Cada cuadro muestra el estado y log independiente de su módulo",
                            color = Color(0xFF00FFCC),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Leyenda de luces solicitada por el usuario
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusLegendDot(Color(0xFF2ED573), "Operando")
                        StatusLegendDot(Color(0xFFB0BEC5), "Dormido")
                        StatusLegendDot(Color(0xFFFF4757), "Detenido")
                    }
                }
            }

            // Cuadrícula de Tarjetas: Muestra TODOS los módulos de Sara (Conversación, Visión, Recordatorios, Música, Chat, Navegación, Red Jev, Base de Datos)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(SaraModule.entries.toList(), key = { it.name }) { mod ->
                    val status = moduleStatuses[mod] ?: ModuleMonitorStatus(module = mod)
                    ModuleHealthCard(
                        status = status,
                        onViewModuleLogs = { inspectingModule = mod },
                        onNotifyErrorMd = {
                            val (file, content) = AppLogger.generarInformeErrorMarkdown(context, mod)
                            generatedMdReport = Triple(mod, file.absolutePath, content)
                            Toast.makeText(context, "Informe .md generado: ${file.name}", Toast.LENGTH_LONG).show()
                        },
                        onSimulateError = {
                            AppLogger.log(
                                tag = mod.shortCode,
                                message = "Fallo crítico detectado en ${mod.displayName}: Conexión interrumpida / Error de servicio",
                                level = com.sara.robot.core.LogLevel.ERROR,
                                explicitModule = mod
                            )
                        },
                        onResetModule = {
                            AppLogger.resetModuleErrorState(mod)
                            AppLogger.log(
                                tag = mod.shortCode,
                                message = "${mod.displayName} reiniciado y operativo exitosamente",
                                level = com.sara.robot.core.LogLevel.SUCCESS,
                                explicitModule = mod
                            )
                        }
                    )
                }
            }
        }

        // Modal Visor del Informe .md generado
        if (generatedMdReport != null) {
            val (mod, filePath, mdText) = generatedMdReport!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE000000))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF101022),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFFF4757)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "📄 INFORME DE ERROR GENERADO (.MD) • ${mod.displayName.uppercase()}",
                                    color = Color(0xFFFF4757),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Archivo guardado en: $filePath",
                                    color = Color(0xFF00FFCC),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(mdText))
                                        Toast.makeText(context, "Informe Markdown (.md) copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ED573))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copiar .md", color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(onClick = { generatedMdReport = null }) {
                                    Text("Cerrar", color = Color.White)
                                }
                            }
                        }

                        Surface(
                            color = Color(0xFF080814),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = mdText,
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }

        // Modal Visor del Log Independiente de un Módulo Específico
        if (inspectingModule != null) {
            val mod = inspectingModule!!
            val status = moduleStatuses[mod] ?: ModuleMonitorStatus(module = mod)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF101022),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF00F0FF)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LOG INDEPENDIENTE • ${mod.displayName.uppercase()} (${status.logs.size} eventos)",
                                color = Color(0xFF00F0FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Button(
                                onClick = { inspectingModule = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                            ) {
                                Text("Cerrar", color = Color.White)
                            }
                        }

                        Surface(
                            color = Color(0xFF080814),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (status.logs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Este módulo aún no registra eventos en su log independiente.", color = Color.Gray)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(status.logs, key = { it.id }) { entry ->
                                        val entryColor = when (entry.level) {
                                            com.sara.robot.core.LogLevel.ERROR -> Color(0xFFFF4757)
                                            com.sara.robot.core.LogLevel.WARN -> Color(0xFFFFA502)
                                            com.sara.robot.core.LogLevel.SUCCESS -> Color(0xFF2ED573)
                                            com.sara.robot.core.LogLevel.INFO -> Color.White
                                        }
                                        Text(
                                            text = "[${entry.timestamp}] [${entry.level}] [${entry.tag}] ${entry.message}",
                                            color = entryColor,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLegendDot(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = text, color = Color.LightGray, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModuleHealthCard(
    status: ModuleMonitorStatus,
    onViewModuleLogs: () -> Unit,
    onNotifyErrorMd: () -> Unit,
    onSimulateError: () -> Unit,
    onResetModule: () -> Unit
) {
    // Luz según estado: Verde = Operando, Plateado = Dormido, Rojo = Detenido
    val ledColor = when (status.state) {
        ModuleOperationalState.OPERANDO -> Color(0xFF2ED573)
        ModuleOperationalState.DORMIDO -> Color(0xFFB0BEC5)
        ModuleOperationalState.DETENIDO -> Color(0xFFFF4757)
    }

    val isStoppedWithError = (status.state == ModuleOperationalState.DETENIDO)

    Surface(
        color = if (isStoppedWithError) Color(0xFF220E14) else Color(0xFF0E0E1F),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isStoppedWithError) 1.8.dp else 1.dp,
            color = if (isStoppedWithError) Color(0xFFFF4757) else ledColor.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Fila Superior: Nombre del Módulo + Luz LED + Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        text = status.module.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = status.module.description,
                        color = Color.Gray,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Indicador Luminoso (LED) y Etiqueta de Estado
                Surface(
                    color = ledColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, ledColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ledColor)
                        )
                        Text(
                            text = status.state.label.uppercase(),
                            color = ledColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Caja de resumen del log independiente del módulo
            Surface(
                color = Color(0xFF080812),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val lastLogText = when {
                    isStoppedWithError && status.lastErrorSummary != null -> "✖ ERROR: ${status.lastErrorSummary}"
                    status.logs.isNotEmpty() -> {
                        val last = status.logs.last()
                        "[${last.timestamp}] ${last.message}"
                    }
                    else -> "[Sin actividad reciente • Módulo en espera]"
                }
                Text(
                    text = lastLogText,
                    color = if (isStoppedWithError) Color(0xFFFF6B81) else Color.LightGray,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            // Botones de Acción del Cuadro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Si está DETENIDO (Rojo), se activa el botón "Notificar Error (.md)"
                if (isStoppedWithError) {
                    Button(
                        onClick = onNotifyErrorMd,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(30.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "NOTIFICAR ERROR (.MD)",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedButton(
                        onClick = onResetModule,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, Color(0xFF2ED573)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Reactivar", color = Color(0xFF2ED573), fontSize = 9.5.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onSimulateError,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, Color(0x44FF4757)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Simular Fallo", color = Color(0xFFFF6B81), fontSize = 9.5.sp)
                    }
                }

                OutlinedButton(
                    onClick = onViewModuleLogs,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, Color(0x4400F0FF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                ) {
                    Text(
                        text = "Ver Log (${status.logs.size})",
                        color = Color(0xFF00F0FF),
                        fontSize = 9.5.sp
                    )
                }
            }
        }
    }
}
