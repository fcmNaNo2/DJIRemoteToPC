package com.dji.remotetopc

import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 摇杆读取器
 * 方案1: 通过 Android MotionEvent (如果系统支持)
 * 方案2: 通过 TCP 端口接收数据 (从 ADB shell getevent)
 */
class JoystickReader(private val callback: JoystickCallback) {

    interface JoystickCallback {
        fun onJoystickUpdate(leftX: Int, leftY: Int, rightX: Int, rightY: Int, leftZ: Int, rightZ: Int)
        fun onJoystickEvent(event: MotionEvent): Boolean
    }

    private var running = AtomicBoolean(false)
    private var serverThread: Thread? = null
    private var serverSocket: ServerSocket? = null
    private var joystickDevice: InputDevice? = null
    private var deviceId = -1

    // 当前摇杆值 (范围: -32768 到 32767)
    @Volatile var leftX = 0
    @Volatile var leftY = 0
    @Volatile var rightX = 0
    @Volatile var rightY = 0
    @Volatile var leftZ = 127
    @Volatile var rightZ = 127

    companion object {
        private const val TAG = "JoystickReader"
        private const val TCP_PORT = 19999  // 本地 TCP 端口
    }

    fun start(): Boolean {
        if (running.get()) {
            Log.d(TAG, "Already running")
            return true
        }

        // 查找 DJI joystick 设备
        findJoystickDevice()

        running.set(true)

        // 启动 TCP 服务器接收数据
        startTcpServer()

        return joystickDevice != null
    }

    private fun findJoystickDevice() {
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id)
            if (device != null) {
                Log.d(TAG, "Found device: ${device.name} (id=$id, sources=${device.sources})")

                if (device.name.contains("DJI", ignoreCase = true) &&
                    device.name.contains("joystick", ignoreCase = true)) {
                    joystickDevice = device
                    deviceId = id
                    Log.d(TAG, "Selected DJI joystick: ${device.name}")
                    break
                }
            }
        }

        if (joystickDevice == null) {
            Log.e(TAG, "DJI joystick not found")
        }
    }

    private fun startTcpServer() {
        serverThread = thread(start = true, name = "JoystickTcpServer") {
            try {
                serverSocket = ServerSocket(TCP_PORT)
                Log.d(TAG, "TCP server started on port $TCP_PORT")
                Log.d(TAG, "Run this on your computer to send joystick data:")
                Log.d(TAG, "adb shell 'getevent -l /dev/input/event4' | nc localhost $TCP_PORT")

                while (running.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        Log.d(TAG, "Client connected from ${client.inetAddress}")
                        handleClient(client)
                    } catch (e: Exception) {
                        if (running.get()) {
                            Log.e(TAG, "Error accepting client: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP server error: ${e.message}")
            }
        }
    }

    private fun handleClient(client: Socket) {
        thread(start = true, name = "JoystickClient") {
            try {
                val reader = BufferedReader(InputStreamReader(client.inputStream))
                var line: String?

                while (running.get() && client.isConnected) {
                    line = reader.readLine() ?: break
                    parseLine(line)
                }

                reader.close()
                client.close()
                Log.d(TAG, "Client disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Client error: ${e.message}")
            }
        }
    }

    private fun parseLine(line: String) {
        // 解析格式: EV_ABS       ABS_X                fffff969
        try {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3 && parts[0] == "EV_ABS") {
                val axisName = parts[1]
                val valueHex = parts[2]
                val value = valueHex.toLong(16).toInt()

                when (axisName) {
                    "ABS_X" -> leftX = value
                    "ABS_Y" -> leftY = value
                    "ABS_Z" -> leftZ = value
                    "ABS_RX" -> rightX = value
                    "ABS_RY" -> rightY = value
                    "ABS_RZ" -> rightZ = value
                }
                callback.onJoystickUpdate(leftX, leftY, rightX, rightY, leftZ, rightZ)
            }
        } catch (e: Exception) {
            // 忽略解析错误
        }
    }

    /**
     * 处理来自 Activity 的 MotionEvent
     */
    fun processMotionEvent(event: MotionEvent): Boolean {
        if (!running.get()) return false

        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return false
        }

        leftX = (event.getAxisValue(MotionEvent.AXIS_X) * 32767).toInt()
        leftY = (event.getAxisValue(MotionEvent.AXIS_Y) * 32767).toInt()
        rightX = (event.getAxisValue(MotionEvent.AXIS_RX) * 32767).toInt()
        rightY = (event.getAxisValue(MotionEvent.AXIS_RY) * 32767).toInt()
        leftZ = ((event.getAxisValue(MotionEvent.AXIS_Z) + 1) * 127.5).toInt()
        rightZ = ((event.getAxisValue(MotionEvent.AXIS_RZ) + 1) * 127.5).toInt()

        callback.onJoystickUpdate(leftX, leftY, rightX, rightY, leftZ, rightZ)
        return true
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverThread?.interrupt()
        serverThread = null
        Log.d(TAG, "Stopped")
    }

    fun getDeviceName(): String {
        return joystickDevice?.name ?: "Not found"
    }

    fun getDeviceId(): Int = deviceId

    fun getTcpPort(): Int = TCP_PORT

    /**
     * 将原始值 (-32768 到 32767) 转换为 DJI 范围 (-660 到 660)
     */
    fun toDjiRange(rawValue: Int): Int {
        return (rawValue.toLong() * 660 / 32767).toInt().coerceIn(-660, 660)
    }
}
