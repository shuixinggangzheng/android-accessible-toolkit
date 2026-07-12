# Android 无障碍辅助工具

一个面向听障、语障及老年人群体的 Android 无障碍辅助工具，提供实时字幕、语音辅助、服药提醒、紧急呼叫等功能。

## 功能特性

### 听障字幕
- 实时语音转文字
- VAD 人声检测
- 悬浮窗字幕显示
- 一键暂停/恢复

### 语障辅助
- 文字转语音
- 预设常用短语
- 语速/音调调节

### 老年人辅助
- 服药提醒
- 一键呼叫紧急联系人
- 悬浮球快捷操作

### 跨设备通信
- WebSocket 实时广播
- PC 浏览器查看字幕
- 局域网内工作

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34

### 克隆项目

```bash
git clone https://github.com/your-org/android-accessible-toolkit.git
cd android-accessible-toolkit
```

### Vosk 模型配置

本项目使用 Vosk 进行本地语音识别，需要下载中文模型：

1. 访问 [Vosk Models](https://alphacephei.com/vosk/models)
2. 下载 `vosk-model-small-cn-0.22`（推荐，约 50MB）
3. 解压到 `app/src/main/assets/` 目录
4. 重命名为 `model`

### 构建项目

```bash
# 命令行构建
./gradlew assembleDebug

# 或使用 Android Studio 打开项目并运行
```

### 权限说明

首次使用需要授予以下权限：

- **录音权限**：用于语音识别和人声检测
- **悬浮窗权限**：用于显示字幕悬浮窗
- **无障碍服务**：用于获取界面元素标签

## 项目结构

```
android-accessible-toolkit/
├── core/
│   ├── engine/          # 核心接口和数据模型
│   ├── vosk/            # Vosk ASR 实现
│   └── vad/             # VAD 人声检测
├── service/
│   └── accessibility/   # 无障碍服务
├── feature/
│   ├── subtitle/        # 听障字幕模块
│   ├── voice/           # 语障 TTS 模块
│   ├── elder/           # 老年人辅助模块
│   └── bridge/          # 跨设备通信模块
├── app/                 # 主应用模块
├── ARCHITECTURE.md      # 架构文档
├── PRIVACY.md           # 隐私政策
└── README.md            # 项目说明
```

## 使用说明

### 听障字幕

1. 打开应用，点击"听障字幕"
2. 授予录音和悬浮窗权限
3. 说话时会自动显示字幕
4. 点击暂停按钮可暂停字幕

### 语障辅助

1. 打开应用，点击"语障辅助"
2. 输入文字或选择预设短语
3. 点击播报按钮朗读

### 老年人辅助

1. 打开应用，点击"服药提醒"
2. 设置服药时间和药物名称
3. 到时间会自动提醒并朗读

### 跨设备字幕

1. 确保手机和电脑在同一局域网
2. 打开应用，点击"跨设备字幕"
3. 在电脑浏览器中打开显示的地址
4. 手机上的语音会实时显示在电脑上

## 技术栈

- **语言**：Kotlin + Java
- **最低 SDK**：Android 10 (API 29)
- **目标 SDK**：Android 14 (API 34)
- **构建系统**：Gradle Kotlin DSL
- **语音识别**：Vosk Android SDK
- **跨设备通信**：Java-WebSocket

## 隐私保护

本项目严格遵循隐私保护原则：

- **本地处理**：所有语音识别在设备本地完成
- **即时丢弃**：转写结果仅在内存中保留
- **用户可控**：用户可随时启动/停止服务

详见 [PRIVACY.md](PRIVACY.md)

## 架构设计

采用分层模块化架构，详见 [ARCHITECTURE.md](ARCHITECTURE.md)

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本项目
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证，详见 [LICENSE](LICENSE) 文件

## 中文模型微调

本项目使用 [Vosk](https://alphacephei.com/vosk/) 进行离线语音识别。如果你需要更高的识别准确率，可以使用自己的数据对中文模型进行微调。

### 微调步骤概述

1. **准备训练数据**
   - 收集中文语音数据（推荐使用 Common Voice、AISHELL 等开源数据集）
   - 数据格式：音频文件（WAV, 16kHz, 16bit, mono）+ 转写文本
   - 建议数据量：至少 10 小时以上以获得明显效果

2. **获取基础模型**
   - 下载 Vosk 中文基础模型：`vosk-model-small-cn-0.22`
   - 该模型基于 Common Voice 中文数据集训练

3. **使用 Vosk 模型训练工具**
   ```bash
   # 克隆 Vosk 训练工具
   git clone https://github.com/alphacep/vosk-api
   
   # 进入训练目录
   cd vosk-api/src
   
   # 准备数据（转换为 Kaldi 格式）
   # 具体步骤参考：https://github.com/alphacep/vosk-api/tree/master/src
   
   # 运行训练脚本
   # 训练过程可能需要数小时到数天，取决于数据量和硬件
   ```

4. **转换模型格式**
   ```bash
   # 将训练好的 Kaldi 模型转换为 Vosk 格式
   # 使用 vosk-api 提供的转换工具
   ```

5. **导入自定义模型**
   ```kotlin
   // 在代码中加载自定义模型
   val asrEngine = VoskAsrEngine(context)
   asrEngine.importAndLoadModel(
       sourcePath = "/path/to/your/custom/model",
       modelName = "my_custom_model"
   )
   
   // 或者直接设置模型路径
   asrEngine.setModelPath("/path/to/your/custom/model")
   ```

### 微调注意事项

- **数据质量**：确保转写文本准确，音频清晰无噪音
- **领域适应**：如果针对特定领域（如医疗、法律），收集该领域的语音数据效果更好
- **增量训练**：可以在现有模型基础上继续训练，而不是从头开始
- **评估效果**：使用 Word Error Rate (WER) 评估微调效果

### 推荐数据集

- [Common Voice](https://commonvoice.mozilla.org/zh-CN) - Mozilla 开源中文语音数据集
- [AISHELL](http://www.aishelltech.com/) - 中文普通话数据集
- [THCHS-30](http://www.theths.net/) - 清华大学中文语音数据集

### 相关资源

- [Vosk 官方文档](https://alphacephei.com/vosk/)
- [Vosk GitHub](https://github.com/alphacep/vosk-api)
- [Kaldi](https://kaldi-asr.org/) - Vosk 底层使用的语音识别工具包

## 联系方式

- GitHub Issues: [项目地址]
- Email: [联系邮箱]