#!/bin/bash
# DJI RC Pro 2 摇杆数据转发脚本
# 直接用 Python 读取 ADB 数据发送 UDP，不需要 Android 应用

cd "$(dirname "$0")"

# 默认参数
TARGET_IP="${1:-127.0.0.1}"
TARGET_PORT="${2:-9999}"

echo "=== DJI RC Pro 2 摇杆 → UDP ==="
echo "目标: $TARGET_IP:$TARGET_PORT"
echo ""

python3 joystick_to_udp.py "$TARGET_IP" "$TARGET_PORT"
