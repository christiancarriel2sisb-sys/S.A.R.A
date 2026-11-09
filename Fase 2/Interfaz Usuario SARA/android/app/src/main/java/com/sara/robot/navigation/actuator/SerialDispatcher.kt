package com.sara.robot.navigation.actuator

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
//Librería usb-serial-for-android agregada en Gradle
import com.hoho.android.usbserial.driver.UsbSerialPort 
//Detector automático de drivers Arduino (CDC / CH340 / FTDI / CP210x)
import com.hoho.android.usbserial.driver.UsbSerialProber 
import com.sara.robot.core.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Despachador de comunicación serial USB-OTG a 10 Hz hacia el microcontrolador del chasis (Capa 5).
 * Utiliza la librería usb-serial-for-android a 115200 bps sincronizado con prueba_sara_v3.ino.
 */
class SerialDispatcher(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val ACTION_USB_PERMISSION = "com.sara.robot.USB_PERMISSION"
    private var usbManager: UsbManager? = null
    //Puerto serial gestionado por la librería usb-serial-for-android
    private var serialPort: UsbSerialPort? = null 

    private var latestCommand: String = "6\n"
    private var dispatchJob: Job? = null
    var onCommandDispatched: ((String) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            //Escucha permiso USB y conexión/desconexión del cable OTG en tiempo real
            when (intent?.action) { 
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            AppLogger.info("SerialDispatcher", "Permiso USB concedido. Abriendo puerto con librería USB-Serial...")
                            initUsbConnection()
                        } else {
                            AppLogger.error("SerialDispatcher", "Permiso USB denegado por el usuario")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    AppLogger.info("SerialDispatcher", "Arduino conectado por USB-OTG. Iniciando enlace...")
                    initUsbConnection()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    AppLogger.warn("SerialDispatcher", "Arduino desconectado del puerto USB-OTG.")
                    closePort()
                }
            }
        }
    }

    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        //Registro de eventos para reconectar automáticamente si se desenchufa y vuelve a enchufar el cable
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        initUsbConnection()
        startDispatchLoop()
    }

    private fun initUsbConnection() {
        if (serialPort != null) return
        val manager = usbManager ?: return

        //UsbSerialProber detecta cualquier placa Arduino (oficial o CH340) usando la librería de JitPack
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            AppLogger.info("SerialDispatcher", "Modo Virtual/Emulador activo (sin hardware USB físico conectado)")
            return
        }

        val driver = availableDrivers[0]
        val targetDevice: UsbDevice = driver.device

        if (manager.hasPermission(targetDevice)) {
            openAndConfigurePort(driver.ports[0], targetDevice)
        } else {
            AppLogger.info("SerialDispatcher", "Solicitando permiso USB en pantalla para: ${targetDevice.deviceName}")
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

    //Configuración del puerto a 115200 bps, 8N1 y activación DTR/RTS mediante usb-serial-for-android
    private fun openAndConfigurePort(port: UsbSerialPort, targetDevice: UsbDevice) {
        val manager = usbManager ?: return
        val connection = manager.openDevice(targetDevice)
        if (connection == null) {
            AppLogger.warn("SerialDispatcher", "No se pudo abrir la conexión USB")
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
            AppLogger.success("SerialDispatcher", "Conexión USB-OTG establecida a 115200 bps con usb-serial-for-android")
        } catch (e: Exception) {
            AppLogger.warn("SerialDispatcher", "Error configurando puerto USB-Serial: ${e.message}")
            closePort()
        }
    }

    fun postCommand(frame: String) {
        latestCommand = frame
    }

    private fun startDispatchLoop() {
        dispatchJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val commandToSend = latestCommand
                val bytes = commandToSend.toByteArray(Charsets.US_ASCII)

                val port = serialPort
                if (port != null) {
                    try {
                        //Envío de trama ('1\n', '2\n', '3\n', '6\n') por la librería USB-Serial
                        port.write(bytes, 100)
                    } catch (e: Exception) {
                        AppLogger.warn("SerialDispatcher", "Error enviando bytes USB: ${e.message}")
                        closePort()
                    }
                } else {
                    //Si se conectó el cable después de abrir la app, enlaza automáticamente
                    if (usbManager?.deviceList?.isNotEmpty() == true) {
                        initUsbConnection()
                    }
                }

                onCommandDispatched?.invoke(commandToSend)
                delay(100) // Despacho periódico a 10 Hz
            }
        }
    }

    private fun closePort() {
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
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Ignorar si ya estaba desregistrado
        }
        closePort()
    }
}