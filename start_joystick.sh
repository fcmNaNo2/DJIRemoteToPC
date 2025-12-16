#!/bin/bash
# DJI RC Pro 2 摇杆数据转发脚本
# 使用方法: ./start_joystick.sh

ADB=~/Library/Android/sdk/platform-tools/adb
DEVICE="7ZWXN9G00233W3"

echo "=== DJI RC Pro 2 摇杆数据转发 ==="

# 检查设备连接
if ! $ADB -s $DEVICE get-state > /dev/null 2>&1; then
    echo "错误: 设备 $DEVICE 未连接"
    echo "请用 USB 连接 RC Pro 2 并启用 USB 调试"
    exit 1
fi

# 启动应用
echo "启动应用..."
$ADB -s $DEVICE shell am start -n com.dji.remotetopc/.MainActivity

# 等待应用启动
sleep 2

# 启动摇杆数据转发
echo "启动摇杆数据转发..."
echo "按 Ctrl+C 停止"
echo ""

$ADB -s $DEVICE shell "getevent -l /dev/input/event4 | nc 127.0.0.1 19999"
