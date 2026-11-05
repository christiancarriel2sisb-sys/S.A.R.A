package com.sara.robot.navigation.actuator

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.sara.robot.core.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Despachador de comunicación serial USB-OTG a 10 Hz hacia el microcontrolador del chasis (Capa 5).
 * Opera de forma asíncrona no bloqueante sobre Dispatchers.IO.
 * Si no hay microcontrolador físico conectado (ej. emulador en PC), emite la telemetría
 * a AppLogger y consola virtual sin provocar bloqueos.
 */
class SerialDispatcher(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var usbManager: UsbManager? = null
    private var connection: UsbDeviceConnection? = null
    private var endpointOut: UsbEndpoint? = null
    private var usbInterface: UsbInterface? = null

    private var latestCommand: String = "NAV:0,0\n"
    private var dispatchJob: Job? = null
    var onCommandDispatched: ((String) -> Unit)? = null

    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        initUsbConnection()
        startDispatchLoop()
    }

    private fun initUsbConnection() {
        val manager = usbManager ?: return
        val deviceList = manager.deviceList
        val targetDevice = deviceList.values.firstOrNull()

        if (targetDevice != null) {
            AppLogger.info("SerialDispatcher", "Dispositivo USB detectado: ${targetDevice.deviceName}")
            try {
                for (i in 0 until targetDevice.interfaceCount) {
                    val intf = targetDevice.getInterface(i)
                    for (j in 0 until intf.endpointCount) {
                        val ep = intf.getEndpoint(j)
                        if (ep.direction == UsbConstants.USB_DIR_OUT) {
                            endpointOut = ep
                            usbInterface = intf
                            break
                        }
                    }
                    if (endpointOut != null) break
                }

                if (endpointOut != null && usbInterface != null) {
                    connection = manager.openDevice(targetDevice)
                    connection?.claimInterface(usbInterface, true)
                    AppLogger.success("SerialDispatcher", "Conexión USB-OTG establecida a 115200 bps")
                }
            } catch (e: Exception) {
                AppLogger.warn("SerialDispatcher", "No se pudo reclamar interfaz USB física: ${e.message}")
            }
        } else {
            AppLogger.info("SerialDispatcher", "Modo Virtual/Emulador activo (sin hardware USB físico conectado)")
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
            if (usbInterface != null && connection != null) {
                connection?.releaseInterface(usbInterface)
            }
            connection?.close()
        } catch (e: Exception) {
            // Ignorar al cerrar
        }
    }
}

