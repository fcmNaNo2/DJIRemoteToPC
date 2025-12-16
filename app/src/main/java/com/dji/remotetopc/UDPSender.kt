package com.dji.remotetopc

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * UDP 数据发送器
 * 将遥控器数据发送到 PC 端
 */
class UDPSender(
    private val targetIP: String = "192.168.1.100",  // PC 的 IP 地址
    private val targetPort: Int = 9999               // PC 端监听端口
) {
    private var socket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // 遥控器数据
    @Volatile var leftStickH: Int = 0      // 左摇杆水平 [-660, 660]
    @Volatile var leftStickV: Int = 0      // 左摇杆垂直 [-660, 660]
    @Volatile var rightStickH: Int = 0     // 右摇杆水平 [-660, 660]
    @Volatile var rightStickV: Int = 0     // 右摇杆垂直 [-660, 660]
    @Volatile var leftDial: Int = 0        // 左拨轮 [-660, 660]
    @Volatile var rightDial: Int = 0       // 右拨轮 [-660, 660]
    @Volatile var c1Button: Boolean = false
    @Volatile var c2Button: Boolean = false
    @Volatile var c3Button: Boolean = false
    @Volatile var goHomeButton: Boolean = false

    /**
     * 启动 UDP 发送
     * @param ip 目标 IP 地址
     * @param port 目标端口
     * @param intervalMs 发送间隔（毫秒）
     */
    fun start(ip: String = targetIP, port: Int = targetPort, intervalMs: Long = 20) {
        if (isRunning) return

        isRunning = true
        scope.launch {
            try {
                socket = DatagramSocket()
                val address = InetAddress.getByName(ip)

                while (isRunning) {
                    val data = buildDataPacket()
                    val buffer = data.toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(buffer, buffer.size, address, port)
                    socket?.send(packet)
                    delay(intervalMs)  // 50Hz 发送频率
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 停止 UDP 发送
     */
    fun stop() {
        isRunning = false
        socket?.close()
        socket = null
    }

    /**
     * 构建数据包
     * 格式: LSH,LSV,RSH,RSV,LD,RD,C1,C2,C3,GH
     */
    private fun buildDataPacket(): String {
        return buildString {
            append(leftStickH).append(",")
            append(leftStickV).append(",")
            append(rightStickH).append(",")
            append(rightStickV).append(",")
            append(leftDial).append(",")
            append(rightDial).append(",")
            append(if (c1Button) 1 else 0).append(",")
            append(if (c2Button) 1 else 0).append(",")
            append(if (c3Button) 1 else 0).append(",")
            append(if (goHomeButton) 1 else 0)
        }
    }

    /**
     * 更新左摇杆数据
     */
    fun updateLeftStick(horizontal: Int, vertical: Int) {
        leftStickH = horizontal
        leftStickV = vertical
    }

    /**
     * 更新右摇杆数据
     */
    fun updateRightStick(horizontal: Int, vertical: Int) {
        rightStickH = horizontal
        rightStickV = vertical
    }

    /**
     * 发送单条消息（用于按钮等事件）
     */
    fun sendOnce(message: String, ip: String = targetIP, port: Int = targetPort) {
        scope.launch {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(ip)
                val buffer = message.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(buffer, buffer.size, address, port)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
