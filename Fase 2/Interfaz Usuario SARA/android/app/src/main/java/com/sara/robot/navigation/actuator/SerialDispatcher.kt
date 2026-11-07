package com.sara.robot.navigation.actuator

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import com.sara.robot.core.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Despachador de comunicación serial USB-OTG a 10 Hz hacia el microcontrolador del chasis (Capa 5).
 * Implementación 100% nativa Android USB Host (sin librerías externas) con soporte de permisos
 * y configuración CDC/CH340 a 115200 bps.
 */
class SerialDispatcher(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val ACTION_USB_PERMISSION = "com.sara.robot.USB_PERMISSION"
    private var usbManager: UsbManager? = null
    private var connection: UsbDeviceConnection? = null
    private var endpointOut: UsbEndpoint? = null
    private var usbInterface: UsbInterface? = null

    private var latestCommand: String = "6\n"
    private var dispatchJob: Job? = null
    var onCommandDispatched: ((String) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ACTION_USB_PERMISSION == intent?.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { openAndConfigureDevice(it) }
                    } else {
                        AppLogger.error("SerialDispatcher", "Permiso USB denegado por el usuario")
                    }
                }
            }
        }
    }

    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        initUsbConnection()
        startDispatchLoop()
    }

    private fun initUsbConnection() {
        val manager = usbManager ?: return
        val deviceList = manager.deviceList
        val targetDevice = deviceList.values.firstOrNull()

        if (targetDevice != null) {
            AppLogger.info("SerialDispatcher", "Dispositivo USB detectado: ${targetDevice.deviceName}")
            if (manager.hasPermission(targetDevice)) {
                openAndConfigureDevice(targetDevice)
            } else {
                AppLogger.info("SerialDispatcher", "Solicitando permiso USB en pantalla...")
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0
                val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
                manager.requestPermission(targetDevice, permissionIntent)
            }
        } else {
            AppLogger.info("SerialDispatcher", "Modo Virtual/Emulador activo (sin hardware USB físico conectado)")
        }
    }

    private fun openAndConfigureDevice(targetDevice: UsbDevice) {
        val manager = usbManager ?: return
        try {
            for (i in 0 until targetDevice.interfaceCount) {
                val intf = targetDevice.getInterface(i)
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        endpointOut = ep
                        usbInterface = intf
                        break
                    }
                }
                if (endpointOut != null) break
            }

            if (endpointOut != null && usbInterface != null) {
                connection = manager.openDevice(targetDevice)
                if (connection != null) {
                    connection?.claimInterface(usbInterface, true)

                    // Configurar DTR/RTS (activar canal serie en Arduino Uno)
                    connection?.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 100)

                    // Configurar Line Coding a 115200 bps, 8N1 (0x0001C200 = 115200 en little-endian)
                    val lineCoding = byteArrayOf(
                        0x00.toByte(), 0xC2.toByte(), 0x01.toByte(), 0x00.toByte(),
                        0x00.toByte(), // 1 bit de parada
                        0x00.toByte(), // Sin paridad
                        0x08.toByte()  // 8 bits de datos
                    )
                    connection?.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 100)

                    AppLogger.success("SerialDispatcher", "Conexión USB-OTG establecida a 115200 bps")
                } else {
                    AppLogger.warn("SerialDispatcher", "No se pudo abrir la conexión USB")
                }
            }
        } catch (e: Exception) {
            AppLogger.warn("SerialDispatcher", "No se pudo reclamar interfaz USB física: ${e.message}")
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

                if (connection != null && endpointOut != null) {
                    try {
                        connection?.bulkTransfer(endpointOut, bytes, bytes.size, 100)
                    } catch (e: Exception) {
                        AppLogger.warn("SerialDispatcher", "Error enviando bytes USB: ${e.message}")
                    }
                }

                onCommandDispatched?.invoke(commandToSend)
                delay(100) // Despacho periódico a 10 Hz
            }
        }
    }

    fun shutdown() {
        dispatchJob?.cancel()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Ignorar si ya estaba desregistrado
        }
        try {
            if (usbInterface != null && connection != null) {
                connection?.releaseInterface(usbInterface)
            }
            connection?.close()
        } catch (e: Exception) {
            // Ignorar al cerrar
        }
    }
}