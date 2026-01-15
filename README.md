<div align="center">

# DJIRemoteToPC

### 🚁 DJI RC Pro 2 摇杆数据转发到 PC via UDP

直接在电脑上运行，通过 ADB 读取摇杆数据并发送 UDP 给 Unity

[![Python](https://img.shields.io/badge/Python-3.8+-blue.svg)](https://www.python.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

</div>

---

## 功能特点

- 🎮 **实时数据转发** - 通过 ADB 读取 DJI RC Pro 2 摇杆数据
- 📡 **UDP 传输** - 低延迟发送到 PC 端应用
- 🎯 **Unity 兼容** - 数据格式与 Unity DJIRemoteReceiver 兼容
- ⚡ **高频率** - 50Hz 发送频率，保证流畅控制

---

## 工作原理

```
DJI RC Pro 2 → ADB → Python脚本 → UDP → Unity/其他应用
```

直接通过 ADB `getevent` 读取输入事件，无需在遥控器上安装任何应用。

---

## 前置要求

1. **Python 3.8+**
2. **ADB 工具** - Android SDK Platform Tools
3. **DJI RC Pro 2** - 已启用 USB 调试
4. **数据线** - 连接遥控器到电脑

---

## 安装 ADB

### macOS
```bash
brew install android-platform-tools
```

### Windows
从 [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) 下载并解压

### Linux
```bash
sudo apt install android-tools-adb
```

---

## 使用方法

### 1. 连接遥控器

用数据线将 DJI RC Pro 2 连接到电脑，确保已授权 USB 调试

### 2. 验证连接
```bash
adb devices
```

### 3. 运行脚本

**使用 Shell 脚本 (推荐):**
```bash
chmod +x start_joystick.sh
./start_joystick.sh [目标IP] [目标端口]
```

**直接使用 Python:**
```bash
python3 joystick_to_udp.py [目标IP] [目标端口]
```

**默认值:** `127.0.0.1:9999`

---

## 数据格式

### UDP 消息格式 (逗号分隔)
```
左摇杆水平,左摇杆垂直,右摇杆水平,右摇杆垂直,左拨轮,右拨轮,C1,C2,C3,GH
```

### 数值范围
- **摇杆:** -660 到 660
- **拨轮:** 0 到 255

### 控制映射 (美国手 Mode 2)
| 摇杆 | 轴 | 功能 |
|------|-----|------|
| 左 | X | 航向 (Yaw) |
| 左 | Y | 油门 (Throttle) |
| 右 | X | 横滚 (Roll) |
| 右 | Y | 俯仰 (Pitch) |

---

## Unity 端接收代码示例

```csharp
using System.Net;
using System.Net.Sockets;

void Start() {
    UdpClient receiver = new UdpClient(9999);
}

void Update() {
    if (receiver.Available > 0) {
        IPEndPoint sender = new IPEndPoint(IPAddress.Any, 0);
        byte[] data = receiver.Receive(ref sender);
        string message = Encoding.UTF8.GetString(data);
        
        // 解析: "LH,LY,RH,RY,LD,RD,C1,C2,C3,GH"
        string[] values = message.Split(',');
        
        float leftHorizontal = float.Parse(values[0]) / 660f;
        float leftVertical = float.Parse(values[1]) / 660f;
        // ... 其他轴
    }
}
```

---

## 故障排除

### 找不到设备
```bash
# 检查 ADB 连接
adb devices -l

# 重启 ADB 服务
adb kill-server
adb start-server
```

### 权限问题 (Linux)
```bash
# 添加 udev 规则
echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="????", MODE="0666"' | sudo tee /etc/udev/rules.d/51-android.rules
```

### 检查输入设备
```bash
# 在遥控器上列出输入设备
adb shell ls -l /dev/input/

# 测试事件
adb shell getevent -l
```

---

## 项目结构

```
DJIRemoteToPC/
├── joystick_to_udp.py    # 主程序
├── start_joystick.sh      # 启动脚本
├── app/                   # Android 应用 (保留)
├── gradle/                # Gradle 配置 (保留)
└── README.md
```

---

## 许可证

MIT License

---

<div align="center">

**Made with ❤️ for UAV developers**

</div>
