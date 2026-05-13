# 🔢 RainyEmbedding (雨晴向量)

> *"在手机上跑 EmbeddingGemma，让任何 AI 聊天软件都能完全离线做语义搜索"* 🐱☁️

一个纯离线的 Android 本地文本向量化服务器。集成 LiteRT CompiledModel API 运行 EmbeddingGemma 300M 模型，通过 NanoHTTPd 在 `127.0.0.1` 广播 OpenAI 兼容 Embedding API，可供任何支持 OpenAI `/v1/embeddings` 格式的客户端调用。

---

## ✨ 功能特性

| 特性 | 说明 |
|------|------|
| 🧠 **本地向量化** | LiteRT + EmbeddingGemma 300M，CPU 66ms@256tokens |
| 🌐 **OpenAI 兼容 API** | `/v1/embeddings` · `/health` |
| 📐 **Matryoshka 维度** | 768→512→256→128 可调，平衡精度与存储 |
| 🔒 **纯本地 · 零联网** | 127.0.0.1 绑定，隐私数据不出手机 |
| 🔑 **免 Key 免鉴权** | API Key 可填任意值，Model 可填任意值 |
| 📊 **实时统计** | 请求日志、向量化耗时、Token 用量 |
| 📥 **双通道获取模型** | 内置下载 + 手动导入 `.tflite` |
| 🔌 **后台保活** | Foreground Service + WakeLock + WiFi Lock + 心跳 |
| 🎨 **Material Design 3** | Jetpack Compose · 元气猫系粉色主题 |

---

## 📦 模型

| 模型 | 大小 | 来源 |
|------|------|------|
| **EmbeddingGemma 300M** | 179MB (.tflite) | [litert-community](https://huggingface.co/litert-community/embeddinggemma-300m) |
| SentencePiece Tokenizer | ~4MB (.model) | [google/embeddinggemma-300m](https://huggingface.co/google/embeddinggemma-300m) |

> ⚠️ 需先在 HuggingFace 上 Accept Gemma License。

---

## 🔌 API 使用

### 健康检查

```bash
curl http://127.0.0.1:8081/health
# → {"status":"ok","model":"embedding-gemma","dimension":128}
```

### 向量化

```bash
curl -X POST http://127.0.0.1:8081/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"任意值","input":"你好世界"}'
```

### 客户端配置

适用于任何支持 OpenAI `/v1/embeddings` 格式的 AI 聊天软件：

```yaml
Endpoint: http://127.0.0.1:8081    # 或你的自定义端口
API Key:  任意值（本地不校验）    # 如 "sk-local"
Model:    任意值                  # 如 "embedding-gemma"
```

> ⚠️ **端口说明**：默认 8081（RainyLLM 用 8080）。可在设置中修改。

---

## 🏗️ 技术架构

```
┌──────────────────────────────────────────────────┐
│                  Android App                       │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │          Compose UI（3 标签页）               │ │
│  │  主控台 · 模型管理 · 设置                     │ │
│  └────────────────────┬─────────────────────────┘ │
│                       │                            │
│  ┌────────────────────▼─────────────────────────┐ │
│  │          HTTP Server (NanoHTTPd 2.3.1)        │ │
│  │  POST /v1/embeddings  ← OpenAI 兼容          │ │
│  │  GET  /health          ← 健康检查             │ │
│  └────────────────────┬─────────────────────────┘ │
│                       │                            │
│  ┌────────────────────▼─────────────────────────┐ │
│  │    EmbeddingEngine (LiteRT CompiledModel)     │ │
│  │  · EmbeddingGemma 300M (.tflite, ~179MB)     │ │
│  │  · SentencePiece Tokenizer                    │ │
│  │  · Matryoshka 维度截断 (768→128/256/512)      │ │
│  │  · 性能: CPU 66ms@256t, NPU 7.8ms@256t       │ │
│  └────────────────────┬─────────────────────────┘ │
│                       │                            │
│  ┌────────────────────▼─────────────────────────┐ │
│  │        模型存储 + 下载管理                      │ │
│  │  · 内置下载（HF litert-community）            │ │
│  │  · 手动导入（用户放置 .tflite + tokenizer）   │ │
│  │  · SHA256 校验                                │ │
│  └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

### 技术栈

| 组件 | 技术 |
|------|------|
| 推理引擎 | LiteRT CompiledModel API (`com.google.ai.edge.litert:litert:2.1.0`) |
| HTTP 服务器 | NanoHTTPd 2.3.1 |
| Tokenizer | 纯 Kotlin SentencePiece BPE（零原生依赖） |
| NPU 加速 | Qualcomm QNN HTP (SM8450+) · MediaTek NeuroPilot |
| UI | Jetpack Compose + Material Design 3 |
| 架构 | MVVM + Coroutines + DataStore |

---

## 📁 项目结构

```
RainyEmbedding/
├── app/src/main/java/com/rainyembedding/app/
│   ├── RainyEmbeddingApp.kt              # Application 类
│   ├── MainActivity.kt                   # 唯一 Activity（3 页导航）
│   ├── engine/
│   │   ├── EmbeddingEngine.kt            # LiteRT CompiledModel 封装
│   │   ├── EmbeddingTokenizer.kt         # SentencePiece 分词器
│   │   └── TokenEstimator.kt             # Token 估算 fallback
│   ├── server/
│   │   └── EmbeddingServer.kt            # NanoHTTPd (/v1/embeddings + /health)
│   ├── model/
│   │   ├── ModelInfo.kt                  # 模型元数据
│   │   ├── ModelRepository.kt            # 模型扫描/切换/导入
│   │   ├── ModelDownloader.kt            # DownloadManager 下载
│   │   └── ModelValidator.kt             # SHA256 校验
│   ├── service/
│   │   └── EmbeddingServerService.kt     # Foreground Service + WakeLock
│   ├── data/
│   │   ├── AppPreferences.kt             # DataStore
│   │   └── StatsRepository.kt            # 向量化统计记录
│   └── ui/
│       ├── screen/
│       │   ├── DashboardScreen.kt        # 主控台
│       │   ├── ModelManagerScreen.kt     # 模型管理
│       │   ├── PerformanceScreen.kt      # 系统监控
│       │   └── SettingsScreen.kt         # 端口/维度/后端设置
│       ├── component/
│       │   ├── ServerStatusCard.kt       # 服务状态卡片
│       │   ├── ModelDownloadCard.kt      # 下载进度卡片
│       │   ├── TokenStatsChart.kt        # 向量化趋势图
│       │   ├── LogViewer.kt             # 请求日志
│       │   └── DebugCard.kt             # 诊断面板
│       └── theme/
│           ├── Color.kt / Theme.kt / Type.kt
├── .github/workflows/
│   └── release.yml                       # GitHub Actions 自动构建 Release
├── gradle/libs.versions.toml             # Version Catalog 依赖管理
└── tools/
    └── npu_runtime_jit/                  # NPU 运行时库（多芯片支持）
```

**26 个 Kotlin 源文件，~3000 行代码。**

---

## 🛠️ 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17+（必需） |
| Android Studio | 推荐 |
| Android SDK | compileSdk 35, minSdk 31 |
| RAM | 推荐 4GB+ |
| 架构 | arm64-v8a（唯一支持） |

### 方式一：Android Studio（推荐）

1. Clone 仓库：`git clone https://github.com/CATMIAOZHI/RainyEmbedding.git`
2. 用 Android Studio 打开项目
3. 同步 Gradle，连接设备，Run ▶️

### 方式二：命令行

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release 签名 APK
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

<details>
<summary>🔧 ARM64 环境说明（非必需）</summary>

在 ARM64 Linux 环境（如 Operit）下，Gradle 从 Google Maven 下载的 AAPT2 可能不可直接使用。执行以下脚本一键修复：

```bash
chmod +x ./setup_android_env.sh
./setup_android_env.sh
```
</details>

### APK 输出位置

| 构建类型 | 路径 |
|---------|------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release.apk` |

### 首次使用

1. 打开 App，进入「模型管理」
2. 下载 EmbeddingGemma 300M 模型（~179MB）
3. 切回「主控台」，点击「启动服务」
4. 服务运行在 `http://127.0.0.1:8081`

---

## 📦 依赖管理

项目使用 Gradle Version Catalog (`gradle/libs.versions.toml`) 统一管理依赖。

| 依赖 | 用途 |
|------|------|
| `com.google.ai.edge.litert:litert` | LiteRT CompiledModel API |
| `org.nanohttpd:nanohttpd:2.3.1` | 轻量 HTTP 服务器 |
| `androidx.compose:compose-bom` | Jetpack Compose BOM |
| `androidx.navigation:navigation-compose` | 页面导航 |
| `androidx.datastore:datastore-preferences` | 偏好存储 |
| `io.coil-kt:coil-compose` | 图片加载 |

---

## 🔒 安全说明

- ✅ 127.0.0.1 绑定，仅限本机
- ✅ allowBackup="false"
- ✅ 纯离线，零网络请求（模型下载除外）
- ✅ 隐私数据（财务、持仓等）不离开手机

---

## 🎨 自定义

### 修改应用名

编辑 `app/src/main/res/values/strings.xml`：

```xml
<string name="app_name">你的应用名</string>
```

### 修改主题色

编辑 `app/src/main/java/com/rainyembedding/app/ui/theme/Color.kt`

### 修改包名

1. 更新 `app/build.gradle.kts` 中的 `namespace` 和 `applicationId`
2. 重命名 `java/com/rainyembedding/app` 目录结构
3. 更新 `AndroidManifest.xml` 中的包名引用

---

## 🐱 关于

RainyEmbedding 由雨晴喵与水晴共同打造，属于「雨晴系列」工具之一：

- [RainyLLM](https://github.com/CATMIAOZHI/RainyLLM) — 本地 LLM 推理服务器
- **RainyEmbedding** — 本地文本向量化服务器（本项目）
- [RainyScanner](https://github.com/CATMIAOZHI/RainyScanner) — 扫码工具
- [Rainy2FA](https://github.com/CATMIAOZHI/Rainy2FA) — TOTP 验证器

---

## 📄 License

MIT License © 2026 Rainy

---

*Made with 🔢 and 🐱 paws*