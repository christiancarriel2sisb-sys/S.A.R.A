package com.sara.robot.navigation.actuator

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.sara.robot.core.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


data class RobotHardwareStatus(
    val batteryPercent: Int = 100,               // CU-17: Porcentaje actual de la batería
    val isBatteryWarning30: Boolean = false,     // CU-17: Indica si llegó al 30% de batería
    val isLowBatteryCutoff: Boolean = false,     // CU-17: Indica si cayó a <= 10% (motores bloqueados)
    val isUsbActuatorConnected: Boolean = false, // CU-21: true si la placa Arduino está conectada por USB-OTG
    val isUltrasonicSensorOk: Boolean = true,    // CU-21: true si el sensor HC-SR04 responde sin fallas físicas
    val isCameraHardwareOk: Boolean = true,      // CU-21: true si el módulo de cámara está operativo
    val isAudioHardwareOk: Boolean = true        // CU-21: true si la placa de audio (mic/altavoz) está operativa
    val calibratedSafetyDistCm: Int = 20,        // CU-23: Sensibilidad calibrada del sensor ultrasónico
    val calibratedTractionPwm: Int = 255,        // CU-23: Límite calibrado de tracción de motores (PWM)
)

class SerialDispatcher(
    private val context: Context,
    private val scope: CoroutineScope 
) {
    private val ACTION_USB_PERMISSION = "com.sara.robot.USB_PERMISSION"
    private var usbManager: UsbManager? = null
    private var serialPort: UsbSerialPort? = null
    private var isRequestingPermission = false //Candado para no pedir conexión USB repetidas veces

    private var latestCommand: String = "6\n"
    private var dispatchJob: Job? = null

    var onCommandDispatched: ((String) -> Unit)? = null

    private val _hardwareStatus = MutableStateFlow(RobotHardwareStatus())
    val hardwareStatus: StateFlow<RobotHardwareStatus> = _hardwareStatus.asStateFlow()

    //Candados para avisar UNA SOLA VEZ al llegar al 30% y al caer a <= 10% 
    private var aviso30Registrado = false
    private var corte10Registrado = false

    @Volatile
    private var pendingSystemFrame: String? = null
    private val readBuffer = StringBuilder()

    //RECEPTOR DE EVENTOS DE HARDWARE Y BATERÍA
    // Solo actúa cuando ocurre un evento físico real (enchufar cable, desenchufar o cambio de %)
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        isRequestingPermission = false
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            AppLogger.info("SerialDispatcher", "Permiso USB concedido por el usuario.")
                            initUsbConnection()
                        } else {
                            AppLogger.error("SerialDispatcher", "Permiso USB denegado para actuadores del chasis")
                        }
                    }
                }
                //Se ejecuta 1 sola vez en el momento en que enchufas el cable OTG
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    AppLogger.info("SerialDispatcher", "Conexion por USB.")
                    initUsbConnection()
                }
                //Se ejecuta 1 sola vez en el momento en que se desconecta el cable OTG
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    closePort()
                    _hardwareStatus.value = _hardwareStatus.value.copy(isUsbActuatorConnected = false)
                    AppLogger.error("SerialDispatcher", "Desconexión por USB.")
                }
                //Escucha cambios en el nivel de batería para avisar al 30% y cortar motores al <= 10%
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        val batteryPct = (level * 100) / scale
                        evaluarNivelBateria(batteryPct)
                    }
                }
            }
        }
    }

    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(systemReceiver, filter)
        }

        verificarHardwareCamaraYAudio() //Chequeo único al iniciar
        initUsbConnection()             // Chequeo único al abrir la app por si ya estaba enchufado
        startDispatchLoop()
    }

    //LÓGICA DE AVISO AL 30% Y CORTE DE MOTORES AL <= 10%
    private fun evaluarNivelBateria(batteryPct: Int) {
        val activarCorte = batteryPct <= 10
        val enUmbral30 = batteryPct <= 30

        _hardwareStatus.value = _hardwareStatus.value.copy(
            batteryPercent = batteryPct,
            isBatteryWarning30 = enUmbral30,
            isLowBatteryCutoff = activarCorte
        )

        // 1. Aviso único cuando la batería baja al 30%
        if (batteryPct == 30 && !aviso30Registrado) {
            aviso30Registrado = true
            AppLogger.warn("SerialDispatcher", "Batería al 30%. Se recomienda cargar pronto.")
        } else if (batteryPct > 35) {
            aviso30Registrado = false
        }

        // 2. Corte físico de motores cuando la batería cae a <= 10% 
        if (activarCorte && !corte10Registrado) {
            corte10Registrado = true
            latestCommand = "6\n"
            pendingSystemFrame = "B$batteryPct\n" // Ordena al Arduino bloquear motores
            AppLogger.error("SerialDispatcher", "Batería crítica ($batteryPct% <= 10%). Corte de motores activado.")
        } else if (batteryPct > 15 && corte10Registrado) {
            corte10Registrado = false
            pendingSystemFrame = "B$batteryPct\n" // Rehabilita motores en Arduino
            AppLogger.info("SerialDispatcher", "Batería conectada ($batteryPct%). Motores rehabilitados.")
        }
    }

    //DETECCIÓN FÍSICA DE FALLA EN CÁMARA O PLACA DE AUDIO
    fun verificarHardwareCamaraYAudio() {
        val cameraOk = try {
            val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            hasFeature && (camManager?.cameraIdList?.isNotEmpty() == true)
        } catch (e: Exception) {
            false
        }

        val audioOk = try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val inputs = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty()
            val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            inputs.any { it.type != AudioDeviceInfo.TYPE_UNKNOWN } &&
                outputs.any { it.type != AudioDeviceInfo.TYPE_UNKNOWN }
        } catch (e: Exception) {
            false
        }

        _hardwareStatus.value = _hardwareStatus.value.copy(
            isCameraHardwareOk = cameraOk,
            isAudioHardwareOk = audioOk
        )

        if (!cameraOk) AppLogger.error("SerialDispatcher", "Desconexión detectada en la Cámara")
        if (!audioOk) AppLogger.error("SerialDispatcher", "Desconexión detectada en la Placa de Audio")
    }


    //CONEXIÓN CON ARDUINO 
    private fun initUsbConnection() {
        // Si ya está conectado o esperando a que acepte el permiso en pantalla, no hace nada
        if (serialPort != null || isRequestingPermission) return
        val manager = usbManager ?: return

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            _hardwareStatus.value = _hardwareStatus.value.copy(isUsbActuatorConnected = false)
            AppLogger.info("SerialDispatcher", "Modo Virtual activo (sin USB conectado)")
            return
        }

        val driver = availableDrivers[0]
        val targetDevice: UsbDevice = driver.device

        if (manager.hasPermission(targetDevice)) {
            openAndConfigurePort(driver.ports[0], targetDevice)
        } else {
            isRequestingPermission = true
            AppLogger.info("SerialDispatcher", "Solicitando permiso USB en pantalla: ${targetDevice.deviceName}")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                flags
            )
            manager.requestPermission(targetDevice, permissionIntent)
        }
    }

    private fun openAndConfigurePort(port: UsbSerialPort, targetDevice: UsbDevice) {
        val manager = usbManager ?: return
        val connection = manager.openDevice(targetDevice)
        if (connection == null) {
            AppLogger.error("SerialDispatcher", "No se pudo abrir la conexión USB con la placa de actuadores")
            return
        }

        try {
            port.open(connection)
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            try {
                port.dtr = true
                port.rts = true
            } catch (ignored: Exception) {}

            serialPort = port
            _hardwareStatus.value = _hardwareStatus.value.copy(isUsbActuatorConnected = true)
            AppLogger.success("SerialDispatcher", "Conexión USB establecida a 115200 bps")

            // [CU-23 y CU-17]: Sincroniza calibración y batería una sola vez al abrir el puerto
            val st = _hardwareStatus.value
            pendingSystemFrame = "D${st.calibratedSafetyDistCm}\nT${st.calibratedTractionPwm}\nB${st.batteryPercent}\n"
        } catch (e: Exception) {
            AppLogger.error("SerialDispatcher", "Error configurando puerto USB-Serial: ${e.message}")
            closePort()
        }
    }

    fun postCommand(frame: String) {
        if (_hardwareStatus.value.isLowBatteryCutoff && !frame.startsWith("6")) {
            latestCommand = "6\n"
            return
        }
        latestCommand = frame
    }

    //FUNCIONES DE CALIBRACIÓN DE SENSORES Y TRACCIÓN
    fun calibrateProximityThresholdCm(distanceCm: Int) {
        val safeCm = distanceCm.coerceIn(10, 100)
        _hardwareStatus.value = _hardwareStatus.value.copy(calibratedSafetyDistCm = safeCm)
        pendingSystemFrame = "D$safeCm\n"
        AppLogger.info("SerialDispatcher", "Sensibilidad de sensor de proximidad calibrada a $safeCm cm")
    }

    fun calibrateTractionLimitPwm(maxPwm: Int) {
        val safePwm = maxPwm.coerceIn(0, 255)
        _hardwareStatus.value = _hardwareStatus.value.copy(calibratedTractionPwm = safePwm)
        pendingSystemFrame = "T$safePwm\n"
        AppLogger.info("SerialDispatcher", "Límite de tracción de motores calibrado a $safePwm PWM")
    }

    // BUCLE DE DESPACHO A 10 Hz (Solo envía comandos si el puerto ya está abierto)
    private fun startDispatchLoop() {
        dispatchJob = scope.launch(Dispatchers.IO) {
            val readBytes = ByteArray(128)
            while (isActive) {
                val commandToSend = if (_hardwareStatus.value.isLowBatteryCutoff) "6\n" else latestCommand

                //Solo escribe/lee si serialPort existe; ya NO llama a initUsbConnection() cada 100ms!!!!
                val port = serialPort
                if (port != null) {
                    try {
                        pendingSystemFrame?.let { sysCmd ->
                            port.write(sysCmd.toByteArray(Charsets.US_ASCII), 100)
                            pendingSystemFrame = null
                        }

                        port.write(commandToSend.toByteArray(Charsets.US_ASCII), 100)

                        val numRead = port.read(readBytes, 15)
                        if (numRead > 0) {
                            val chunk = String(readBytes, 0, numRead, Charsets.US_ASCII)
                            procesarRespuestaArduino(chunk)
                        }
                    } catch (e: Exception) {
                        closePort()
                        _hardwareStatus.value = _hardwareStatus.value.copy(isUsbActuatorConnected = false)
                        AppLogger.error("SerialDispatcher", "Falla de transmisión en bus serie USB: ${e.message}")
                    }
                }

                onCommandDispatched?.invoke(commandToSend)
                delay(100) // 10 Hz
            }
        }
    }

    private fun procesarRespuestaArduino(data: String) {
        readBuffer.append(data)
        var newlineIdx = readBuffer.indexOf("\n")
        while (newlineIdx != -1) {
            val line = readBuffer.substring(0, newlineIdx).trim()
            readBuffer.delete(0, newlineIdx + 1)
            when {
                line.startsWith("ERR:") -> {
                    AppLogger.error("SerialDispatcher", "Alerta Hardware Arduino $line")
                    if (line.contains("CU21_FALLA_SENSOR_PROXIMIDAD")) {
                        _hardwareStatus.value = _hardwareStatus.value.copy(isUltrasonicSensorOk = false)
                    }
                }
                line.startsWith("ACK:") -> {
                    AppLogger.info("SerialDispatcher", "Confirmación Hardware Arduino $line")
                }
            }
            newlineIdx = readBuffer.indexOf("\n")
        }
    }

    private fun closePort() {
        isRequestingPermission = false
        try {
            serialPort?.close()
        } catch (ignored: Exception) {
        } finally {
            serialPort = null
        }
    }

    fun shutdown() {
        dispatchJob?.cancel()
        try {
            context.unregisterReceiver(systemReceiver)
        } catch (e: Exception) {
            //Ignorar si ya estaba desregistrado
        }
        closePort()
    }
}