#!/usr/bin/env python3
"""
DJI RC Pro 2 摇杆数据转发到 UDP
直接在电脑上运行，通过 ADB 读取摇杆数据并发送 UDP 给 Unity

使用方法:
    python3 joystick_to_udp.py [目标IP] [目标端口]

示例:
    python3 joystick_to_udp.py 127.0.0.1 9999

数据格式 (逗号分隔):
    左摇杆水平,左摇杆垂直,右摇杆水平,右摇杆垂直,左拨轮,右拨轮
    范围: -660 到 660 (摇杆), 0-255 (拨轮)

控制映射 (美国手):
    左摇杆: 油门(上下) + 航向(左右)
    右摇杆: 俯仰(上下) + 横滚(左右)
"""

import subprocess
import socket
import sys
import signal
import os
import time

# ADB 路径
ADB_PATH = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
EVENT_DEVICE = "/dev/input/event4"

# 默认配置
DEFAULT_IP = "127.0.0.1"
DEFAULT_PORT = 9999
SEND_RATE = 50  # 发送频率 Hz

class JoystickReader:
    def __init__(self):
        # 摇杆原始值 (范围: -32768 到 32767)
        self.left_x = 0
        self.left_y = 0
        self.right_x = 0
        self.right_y = 0
        self.left_z = 127
        self.right_z = 127

        self.process = None
        self.running = False

    def find_device(self):
        """查找连接的 DJI RC Pro 2 设备"""
        try:
            result = subprocess.run(
                [ADB_PATH, "devices", "-l"],
                capture_output=True, text=True, timeout=5
            )
            lines = result.stdout.strip().split('\n')[1:]  # 跳过标题行

            for line in lines:
                if 'device' in line and 'unauthorized' not in line:
                    device_id = line.split()[0]
                    print(f"找到设备: {device_id}")
                    return device_id

            print("错误: 未找到已连接的 Android 设备")
            print("请确保:")
            print("  1. RC Pro 2 通过 USB 连接到电脑")
            print("  2. 已启用 USB 调试")
            print("  3. 已授权此电脑调试")
            return None
        except Exception as e:
            print(f"查找设备失败: {e}")
            return None

    def start(self, device_id):
        """启动读取摇杆数据"""
        cmd = [ADB_PATH, "-s", device_id, "shell", f"getevent -l {EVENT_DEVICE}"]
        self.process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )
        self.running = True
        return True

    def read_line(self):
        """读取一行数据"""
        if self.process and self.process.poll() is None:
            return self.process.stdout.readline()
        return None

    def parse_line(self, line):
        """解析 getevent 输出"""
        if not line or not line.startswith("EV_ABS"):
            return False

        parts = line.split()
        if len(parts) < 3:
            return False

        axis = parts[1]
        value = self._parse_hex_signed(parts[2])

        if axis == "ABS_X":
            self.left_x = value
        elif axis == "ABS_Y":
            self.left_y = value
        elif axis == "ABS_RX":
            self.right_x = value
        elif axis == "ABS_RY":
            self.right_y = value
        elif axis == "ABS_Z":
            self.left_z = value
        elif axis == "ABS_RZ":
            self.right_z = value
        else:
            return False

        return True

    def _parse_hex_signed(self, hex_str):
        """解析有符号十六进制"""
        value = int(hex_str, 16)
        if value > 0x7FFFFFFF:
            value -= 0x100000000
        return value

    def get_dji_values(self):
        """获取 DJI 范围的摇杆值 (-660 到 660)"""
        def convert(raw):
            return max(-660, min(660, int(raw * 660 / 32767)))

        return {
            'left_h': convert(self.left_x),
            'left_v': -convert(self.left_y),   # Y轴反转
            'right_h': convert(self.right_x),
            'right_v': -convert(self.right_y), # Y轴反转
            'left_dial': self.left_z,
            'right_dial': self.right_z
        }

    def stop(self):
        """停止读取"""
        self.running = False
        if self.process:
            self.process.terminate()
            self.process = None


def main():
    # 解析命令行参数
    target_ip = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_IP
    target_port = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_PORT

    print("=" * 50)
    print("  DJI RC Pro 2 摇杆 → UDP 转发")
    print("=" * 50)
    print(f"  目标地址: {target_ip}:{target_port}")
    print(f"  发送频率: {SEND_RATE} Hz")
    print("=" * 50)
    print()

    # 初始化
    reader = JoystickReader()

    # 查找设备
    device_id = reader.find_device()
    if not device_id:
        sys.exit(1)

    # 创建 UDP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    # 信号处理
    def signal_handler(sig, frame):
        print("\n\n停止转发...")
        reader.stop()
        sock.close()
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)

    # 启动读取
    if not reader.start(device_id):
        print("启动失败")
        sys.exit(1)

    print("正在读取摇杆数据... (按 Ctrl+C 停止)")
    print()

    packet_count = 0
    last_send_time = time.time()
    send_interval = 1.0 / SEND_RATE

    try:
        while reader.running:
            line = reader.read_line()
            if line:
                line = line.strip()
                if reader.parse_line(line):
                    # 控制发送频率
                    current_time = time.time()
                    if current_time - last_send_time >= send_interval:
                        values = reader.get_dji_values()

                        # 格式: LSH,LSV,RSH,RSV,LD,RD,C1,C2,C3,GH (与Unity DJIRemoteReceiver兼容)
                        message = f"{values['left_h']},{values['left_v']},{values['right_h']},{values['right_v']},{values['left_dial']},{values['right_dial']},0,0,0,0"
                        sock.sendto(message.encode(), (target_ip, target_port))

                        packet_count += 1
                        last_send_time = current_time

                        # 显示状态
                        if packet_count % 50 == 0:
                            print(f"\r[{packet_count:6d}] 左:({values['left_h']:4d},{values['left_v']:4d}) 右:({values['right_h']:4d},{values['right_v']:4d}) 拨轮:({values['left_dial']:3d},{values['right_dial']:3d})", end="", flush=True)

    except Exception as e:
        print(f"\n错误: {e}")
    finally:
        reader.stop()
        sock.close()
        print(f"\n\n共发送 {packet_count} 个数据包")


if __name__ == "__main__":
    main()
