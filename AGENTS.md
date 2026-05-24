# RainyEmbedding — AI 开发指南

> Android 本地文本向量化服务器。LiteRT + EmbeddingGemma 300M，NanoHTTPd 在 127.0.0.1 暴露 OpenAI 兼容 `/v1/embeddings` API。

## 🚫 红线

1. **这是 Embedding 项目，不是 LLM**。不要加聊天/对话/流式生成相关功能。路由只有 `/health` + `/v1/embeddings`。
2. **禁止擅自删除功能/文件**。任何涉及删除的操作必须先问用户。PROGRESS.md 中明确标注了可删清单（仅 5 个文件，已处理）。
3. **拿不准就问用户**。技术选型不确定时列出选项让用户拍板。
4. **「改」优先于「删」**：现有代码能改就改，不重新造轮子。
5. **不要碰 `ModelValidator.kt`** — 纯 SHA256，零改动，100% 通用。

## ⚡ 关键速查

| 项 | 值 |
|----|-----|
| 包名 | `com.rainyembedding.app` |
| 默认端口 | 8081（避免与 RainyLLM 8080 冲突） |
| minSdk | **31**（NPU 要求，非 24） |
| targetSdk | 35 |
| 架构 | **仅 arm64-v8a** |
| 语言 | Kotlin 100% |
| UI | Jetpack Compose + Material 3 |
| 架构模式 | MVVM + Coroutines + DataStore |
| 构建 | `./gradlew assembleDebug` |

## 📦 核心依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `com.google.ai.edge.litert:litert` | **2.1.0** | LiteRT CompiledModel API |
| `org.nanohttpd:nanohttpd` | 2.3.1 | HTTP 服务器 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | 协程 |
| Compose BOM | 2026.01.01 | UI |
| AGP | 9.0.0 | 构建 |
| Kotlin | 2.3.10 | 语言 |

## 🏗️ 项目结构

```
app/src/main/java/com/rainyembedding/app/
├── RainyEmbeddingApp.kt          # Application
├── MainActivity.kt               # 唯一 Activity（3 页导航）
├── engine/
│   ├── EmbeddingEngine.kt        # LiteRT CompiledModel 封装（核心）
│   ├── EmbeddingTokenizer.kt     # 纯 Kotlin SentencePiece BPE（零原生依赖）
│   └── TokenEstimator.kt         # Token 估算 fallback
├── server/
│   └── EmbeddingServer.kt        # NanoHTTPd（/health + /v1/embeddings）
├── model/
│   ├── ModelInfo.kt              # 预置模型元数据
│   ├── ModelRepository.kt        # 模型扫描/切换/导入
│   ├── ModelDownloader.kt        # DownloadManager 下载
│   └── ModelValidator.kt         # SHA256 校验（不动！）
├── service/
│   └── EmbeddingServerService.kt # Foreground Service + WakeLock + WiFi Lock
├── data/
│   ├── AppPreferences.kt         # DataStore 偏好
│   └── StatsRepository.kt        # 向量化统计
└── ui/
    ├── navigation/Screen.kt      # 3 页面路由
    ├── screen/
    │   ├── DashboardScreen.kt    # 主控台
    │   ├── ModelManagerScreen.kt # 模型管理
    │   ├── PerformanceScreen.kt  # 系统监控
    │   └── SettingsScreen.kt     # 端口/维度/后端设置
    └── component/                # 复用组件
```

**26 个 Kotlin 源文件，~3000 行。**

## 🧠 关键实现细节

### EmbeddingEngine
- **Buffer 缓存**：`cachedInputs`/`cachedOutputs` 在 `initialize()` 时预创建，每次 `embed()` 复用。不要在每次推理时重新 `createInputBuffers()`，否则 LiteRT C++ 层报错。
- **Zero-padding**：模型期望固定 `MAX_SEQ_LENGTH=512` 的输入。tokenize 结果不足时必须填 0。
- **NPU 初始化**：需要通过 `Environment.create(BuiltinNpuAcceleratorProvider(context))` 创建 env，传入 `EmbeddingEngine` 构造函数。NPU 不可用时自动降级 CPU。
- **QNN 库**：`tools/npu_runtime_jit/` + `app/src/main/jniLibs/` 包含 Qualcomm QNN .so 文件。

### EmbeddingTokenizer
- **纯 Kotlin BPE**：自己解析 protobuf 格式的 `tokenizer.model`，零原生依赖。不是 Google 的 `sentencepiece-android` 库。
- **降级分词**：Tokenizer 未加载时使用 Unicode 分字 + hash ID（仅测试用）。

### EmbeddingServer
- NanoHTTPd 绑定 `127.0.0.1`，`SOCKET_READ_TIMEOUT=5000`
- CORS 全放通（本地服务，安全可接受）
- 请求日志上限 1000 条
- 推理使用 `ReentrantLock` 串行化（单线程安全）

### EmbeddingServerService
- `START_STICKY` 模式，含心跳（30 秒间隔防 HyperOS cgroup 冻结）
- WiFi Lock + WakeLock（10 分钟超时）
- 前台通知 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE | DATA_SYNC`

## 🛠️ 常用命令

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug

# 运行测试
./gradlew test

# 运行 Lint
./gradlew lint

# 在 Proot 环境初始化（ARM64 Linux）
bash setup_android_env.sh
```

## ⚠️ 已知踩坑

1. **Proot 环境下 AGP strip 工具链不兼容** — `app/build.gradle.kts` 中 `keepDebugSymbols.add("**/*.so")` 禁止 strip native 库。
2. **SentencePiece 官方依赖不可用** — 换成了纯 Kotlin BPE tokenizer（见 PROGRESS.md「已解决问题」）。
3. **HyperOS/MIUI 冻结后台服务** — 需要引导用户关闭电池优化 + 心跳保活。
4. **HuggingFace 需要 Accept Gemma License** — 模型下载前用户必须先在 HF 上同意许可。

## 📚 深入文档

| 文档 | 内容 |
|------|------|
| [README.md](README.md) | 项目介绍、API 用法、快速开始 |
| [PROGRESS.md](PROGRESS.md) | 115 项任务清单与完成状态 |
| [EMBEDDING_DESIGN.md](EMBEDDING_DESIGN.md) | 技术方案、设计决策、调研记录 |
| [PROJECT_PLAN.md](PROJECT_PLAN.md) | 原始规划（设计阶段产物，已全部执行） |