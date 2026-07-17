# MC DLSS Prototype

简体中文 | [English](README.md)

这是一个面向 Minecraft Java Edition 1.21.1 的 Windows 实验性渲染桥接项目。项目尝试把 Iris/Sodium 的渲染目标接入 D3D12 超分辨率后端，探索 NVIDIA DLSS Super Resolution、AMD FidelityFX Super Resolution 3 和帧生成在 Minecraft Java 中的可行性。

本项目目前属于研究原型，不是成熟的性能优化 Mod。测试前请备份存档，并保留一个未安装本项目的正常游戏实例。

## 当前进度

- 当前主要集成目标为 NeoForge 21.1.x。
- Fabric 模块保留了较早的渲染接入和时序输入实现。
- 已通过 Mixin 获取 Iris 1.8.x 与 Sodium 0.8.x 的渲染目标。
- 已建立 OpenGL 与 D3D12 共享资源路径，用于传递颜色、深度和运动信息。
- 原生层可选择接入 NVIDIA Streamline DLSS SR 或 AMD FidelityFX FSR3。
- 同窗口 DXGI 呈现路径仍为实验功能，必须通过 `-DmcDlss.experimentalDxgiPresentation=true` 显式开启。
- 独立的 same-HWND DXGI 探针已在 RTX 4070 Laptop GPU 上通过。
- 游戏内帧生成仍处于实验阶段，不能保证画面正确性、性能收益或光影兼容性。
- 项目自编译的原生 DLL 暂未获得公开可信签名，Windows App Control 可能阻止其加载。

## 项目结构

- `mc-dlss-core`：共享 Java 配置、后端协议和 JNI 接口。
- `mc-dlss-debug`：运行状态、诊断信息和屏幕叠加层模型。
- `mc-dlss-fabric`：Fabric 渲染接入与时序输入实验。
- `mc-dlss-neoforge`：当前主要的 NeoForge/Iris 接入和设置界面。
- `mc-dlss-native`：C++ JNI、OpenGL/D3D12 互操作、Streamline、FSR3 和独立探针。
- `scripts`：本地构建、验证、探针和签名辅助脚本。
- `docs`：设计文档、实验计划和阶段性结果。

## 构建要求

- Windows 10 或 Windows 11 x64
- JDK 21
- Visual Studio 2022 Build Tools，并安装“使用 C++ 的桌面开发”
- CMake

运行不依赖专有 SDK 的 Java 检查：

```powershell
.\scripts\verify-java.ps1
.\scripts\verify-loader-metadata.ps1
```

构建基础原生模块：

```powershell
.\scripts\configure-native.ps1
```

构建加载器模块：

```powershell
.\gradlew.bat build --console=plain --no-daemon --no-parallel
```

Iris/Sodium 开发依赖以及 NVIDIA、AMD 的 SDK 和运行库不会提交到本仓库。可选后端的配置方式见[构建文档](docs/BUILDING.md)。

## 实验版启动

NeoForge 开发客户端示例：

```powershell
.\gradlew.bat :mc-dlss-neoforge:runClient `
  -PmcDlssUseIrisRuntime -PmcDlssUseFsr3Native `
  --console=plain --no-daemon --no-parallel
```

普通启动器还需要加入以下 JVM 参数，才会允许实验性的 DXGI 呈现接管：

```text
-DmcDlss.experimentalDxgiPresentation=true
```

未安装正确签名的原生模块时，不应开启该参数。

## 已知风险

实验性呈现接管可能造成错误帧、闪烁、遮罩错位、实体位置错误、驱动设备丢失或游戏崩溃。默认情况下该路径不会自动开启。对闪烁画面敏感的用户不应测试实验构建。

提交问题时请说明 Minecraft、NeoForge/Fabric、Iris、Sodium、显卡、驱动版本和所选后端，并附上必要的 `latest.log` 片段。上传前必须删除访问令牌、用户名、个人路径和世界存档数据。

## 许可证与第三方组件

本项目自行编写的源代码采用 [MIT License](LICENSE)。Minecraft、NeoForge、Fabric、Iris、Sodium、NVIDIA Streamline/DLSS 和 AMD FidelityFX 保留各自的许可证和商标。第三方说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

在发布包含第三方运行库的二进制文件之前，必须确认对应版本的再分发条款，并记录来源、许可证、数字签名和 SHA-256。

本项目与 Mojang Studios、Microsoft、NVIDIA、AMD、Iris 项目及 Sodium 项目不存在隶属或官方认可关系。

## 参与开发

请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 和 [SECURITY.md](SECURITY.md)。当前最需要帮助的方向包括：

- Iris 光影包兼容性测试和渲染目标语义确认。
- 深度、运动矢量、抖动和相机重置数据的正确性验证。
- D3D12 呈现生命周期、窗口缩放和资源释放问题。
- FSR3 帧生成的 HUD 分离、帧节奏和延迟验证。
- Windows Authenticode 发布签名与可复现打包流程。
