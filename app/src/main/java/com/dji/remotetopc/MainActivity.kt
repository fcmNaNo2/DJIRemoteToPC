package com.dji.remotetopc

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.dji.remotetopc.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), JoystickReader.JoystickCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var udpSender: UDPSender
    private lateinit var joystickReader: JoystickReader

    private var targetIP = "192.168.1.100"
    private var targetPort = 9999
    private var isSending = false
    private val handler = Handler(Looper.getMainLooper())
    private var updateCount = 0

    companion object {
        private const val TAG = "DJIRemoteToPC"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        udpSender = UDPSender()
        joystickReader = JoystickReader(this)

        setupUI()
        startJoystickReader()

        // 使根视图可聚焦，以便接收输入事件
        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
    }

    private fun setupUI() {
        binding.etTargetIP.setText(targetIP)
        binding.etTargetPort.setText(targetPort.toString())

        binding.btnStartStop.setOnClickListener {
            if (isSending) {
                stopSending()
            } else {
                startSending()
            }
        }

        updateStatus("准备就绪...")
    }

    private fun startJoystickReader() {
        Log.d(TAG, "启动摇杆读取器")
        updateStatus("正在启动摇杆读取...")

        val started = joystickReader.start()
        val deviceName = joystickReader.getDeviceName()
        val port = joystickReader.getTcpPort()

        if (started) {
            updateStatus("摇杆: $deviceName")
            binding.tvC1.text = "设备: $deviceName"
        } else {
            updateStatus("摇杆: $deviceName (需ADB)")
            binding.tvC1.text = "TCP端口: $port"
        }

        // 显示 ADB 命令提示
        binding.tvC2.text = "ADB: getevent -> TCP:$port"

        // 列出所有输入设备
        listInputDevices()
    }

    private fun listInputDevices() {
        val sb = StringBuilder()
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id)
            if (device != null) {
                val isJoystick = (device.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                sb.append("${device.name} (js=$isJoystick)\n")
            }
        }
        binding.tvC3.text = "设备:\n$sb"
        Log.d(TAG, "Input devices:\n$sb")
    }

    // 捕获所有 Generic Motion Events (包括摇杆)
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        Log.d(TAG, "dispatchGenericMotionEvent: action=${event.action}, source=${event.source}")

        if (joystickReader.processMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // 也尝试捕获 onGenericMotionEvent
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        Log.d(TAG, "onGenericMotionEvent: action=${event.action}, source=${event.source}")

        if (joystickReader.processMotionEvent(event)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onJoystickUpdate(leftX: Int, leftY: Int, rightX: Int, rightY: Int, leftZ: Int, rightZ: Int) {
        updateCount++

        // 转换为 DJI 范围 (-660 到 660)
        val lh = joystickReader.toDjiRange(leftX)
        val lv = joystickReader.toDjiRange(leftY)
        val rh = joystickReader.toDjiRange(rightX)
        val rv = joystickReader.toDjiRange(rightY)

        // 更新 UDP 发送器
        udpSender.leftStickH = lh
        udpSender.leftStickV = -lv  // Y轴需要反转
        udpSender.rightStickH = rh
        udpSender.rightStickV = -rv
        udpSender.leftDial = leftZ
        udpSender.rightDial = rightZ

        // 更新 UI
        handler.post {
            binding.tvLeftStick.text = "左: ($lh, ${-lv})"
            binding.tvRightStick.text = "右: ($rh, ${-rv})"
            binding.tvLeftDial.text = "拨轮L: $leftZ"
            binding.tvRightDial.text = "拨轮R: $rightZ"
            binding.tvC2.text = "更新: $updateCount"
        }

        // 日志 (每100次)
        if (updateCount % 100 == 0) {
            Log.d(TAG, "摇杆[$updateCount] L($lh,$lv) R($rh,$rv)")
        }
    }

    override fun onJoystickEvent(event: MotionEvent): Boolean {
        return joystickReader.processMotionEvent(event)
    }

    private fun startSending() {
        targetIP = binding.etTargetIP.text.toString()
        targetPort = binding.etTargetPort.text.toString().toIntOrNull() ?: 9999

        udpSender.start(targetIP, targetPort, 50)
        isSending = true
        binding.btnStartStop.text = "停止发送"
        updateStatus("正在发送到 $targetIP:$targetPort")
    }

    private fun stopSending() {
        udpSender.stop()
        isSending = false
        binding.btnStartStop.text = "开始发送"
        updateStatus("已停止发送")
    }

    private fun updateStatus(status: String) {
        binding.tvStatus.text = "状态: $status"
    }

    override fun onDestroy() {
        super.onDestroy()
        joystickReader.stop()
        udpSender.destroy()
    }
}
