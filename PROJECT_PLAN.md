# 🔢 RainyEmbedding — 完整技术规划

> ⚠️ **AI 执行规则（必读）**：
> 1. **禁止自作主张删除功能**。任何删除文件、移除功能的操作必须先问用户。
> 2. **拿不准就问用户**。不确定的技术选择列出选项让用户拍板。
> 3. **「改」优先于「删」**：现有代码能改就改，不要重新造轮子。
> 4. **确认清单**：以下 5 个文件确定删除（`LlmEngine`/`RequestParser`/`SseFormatter`/`ChatTestScreen`/`KeepAliveService`），其余保留或修改。新增的不在此列。

> *"在手机上跑 EmbeddingGemma，让 Operit 记忆库完全离线做语义搜索"* 🐱☁️

一个纯离线的 Android 本地文本向量化服务器。集成 LiteRT CompiledModel API 运行 EmbeddingGemma 300M 模型，通过 NanoHTTPd 在 `127.0.0.1` 广播 OpenAI 兼容 Embedding API，供 Operit AI 助手等客户端调用。

---

## 🎯 项目定位

| 维度 | 说明 |
|------|------|
| **做什么** | Android 手机跑 EmbeddingGemma，开本地 HTTP 服务，暴露 `/v1/embeddings` 端点 |
| **给谁用** | Operit AI 助手（记忆库语义搜索）+ 任何需要本地 Embedding 的客户端 |
| **核心价值** | 纯离线向量化、零费用、隐私安全（收入/持仓数据不出手机） |
| **对标** | 手机版 Embedding 微服务（基于 LiteRT + EmbeddingGemma） |
| **与 RainyLLM 关系** | 姊妹项目。RainyLLM 负责对话推理，RainyEmbedding 负责文本向量化，各司其职 |

---

## 🏗️ 整体架构

```
┌──────────────────────────────────────────────────┐
│              RainyEmbedding App                    │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │        Compose UI（单页主控台）               │ │
│  │  服务开关 · 模型状态 · 向量统计 · 请求日志    │ │
│  └────────────────────┬─────────────────────────┘ │
│                       │                            │
│  ┌────────────────────▼─────────────────────────┐ │
│  │      HTTP Server (NanoHTTPd 2.3.1)            │ │
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

---

## 📊 技术栈

### 推理引擎

| 项目 | 值 |
|------|-----|
| 框架 | **LiteRT CompiledModel API** |
| 依赖坐标 | `com.google.ai.edge.litert:litert:2.1.4` |
| Maven 仓库 | Google Maven（`google()`） |
| 推荐模型 | EmbeddingGemma 300M |
| 模型来源 | [litert-community/embeddinggemma-300m](https://huggingface.co/litert-community/embeddinggemma-300m) |
| 模型格式 | `.tflite`（mixed precision，~179MB） |
| 加速后端 | CPU (XNNPACK) / GPU / NPU |
| CPU 性能 | 66ms@256tokens, 169ms@512tokens (S25 Ultra) |

### HTTP 服务器

| 项目 | 值 |
|------|-----|
| 框架 | **NanoHTTPd** |
| 版本 | 2.3.1 |
| Gradle 坐标 | `org.nanohttpd:nanohttpd:2.3.1` |

### Android 基础

| 项目 | 值 |
|------|-----|
| 语言 | Kotlin 100% |
| UI | Jetpack Compose + Material Design 3 |
| 架构 | MVVM + Repository |
| 异步 | Kotlin Coroutines + Flow |
| 数据存储 | DataStore（偏好设置） |
| 后台运行 | Foreground Service + WakeLock |

---

## 📁 目标项目结构

```
RainyEmbedding/
├── app/
│   ├── src/main/java/com/rainyembedding/app/
│   │   │
│   │   ├── RainyEmbeddingApp.kt              ← Application 类
│   │   │
│   │   ├── ui/
│   │   │   ├── MainActivity.kt               ← 唯一 Activity
│   │   │   ├── screen/
│   │   │   │   ├── DashboardScreen.kt         ← 主控台：服务开关+状态+统计+日志
│   │   │   │   ├── ModelManagerScreen.kt      ← 模型下载/切换/删除/导入
│   │   │   │   ├── PerformanceScreen.kt       ← 系统监控（内存/CPU/磁盘）
│   │   │   │   └── SettingsScreen.kt          ← 端口/维度/加速后端设置
│   │   │   ├── component/
│   │   │   │   ├── ServerStatusCard.kt        ← 服务状态卡片
│   │   │   │   ├── ModelDownloadCard.kt       ← 下载进度卡片
│   │   │   │   ├── TokenStatsChart.kt         ← 向量化请求趋势图
│   │   │   │   ├── LogViewer.kt              ← 请求日志
│   │   │   │   └── DebugCard.kt              ← 诊断面板
│   │   │   └── theme/
│   │   │       ├── Color.kt / Theme.kt / Type.kt
│   │   │
│   │   │   ├── navigation/
│   │   │   │   └── Screen.kt                  ← 3 页面路由
│   │   │
│   │   ├── server/
│   │   │   └── EmbeddingServer.kt            ← NanoHTTPd 服务器（/v1/embeddings + /health）
│   │   │
│   │   ├── engine/
│   │   │   ├── EmbeddingEngine.kt            ← LiteRT CompiledModel 封装
│   │   │   ├── EmbeddingTokenizer.kt         ← SentencePiece 分词器
│   │   │   └── TokenEstimator.kt             ← Token 估算 fallback（简化版）
│   │   │
│   │   ├── model/
│   │   │   ├── ModelInfo.kt                    ← 模型元数据（修改预设列表）
│   │   │   ├── ModelRepository.kt              ← 本地模型扫描/切换（改扩展名）
│   │   │   ├── ModelDownloader.kt              ← 下载管理器（改描述字符串）
│   │   │   └── ModelValidator.kt               ← SHA256 校验（不动）
│   │   │
│   │   ├── service/
│   │   │   └── EmbeddingServerService.kt      ← Foreground Service 保活
│   │   │
│   │   └── data/
│   │       ├── AppPreferences.kt              ← DataStore 偏好存储
│   │       └── StatsRepository.kt            ← 向量化统计记录
│   │
│   └── src/main/AndroidManifest.xml
│
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

**预估规模**：约 26 个 Kotlin 源文件，~3000 行代码。

---

## 📋 文件迁移对照（RainyLLM → RainyEmbedding）

| 原文件 | 操作 | 目标文件/变更 |
|--------|:---:|------|
| `engine/LlmEngine.kt` | ❌ 删 | — |
| `server/RequestParser.kt` | ❌ 删 | — |
| `server/SseFormatter.kt` | ❌ 删 | — |
| `ui/screen/ChatTestScreen.kt` | ❌ 删 | — |
| `service/KeepAliveService.kt` | ❌ 删 | — |
| `model/ModelValidator.kt` | 🟢 不动 | 纯 SHA256，零改动 |
| `ui/theme/Color.kt` | 🟢 不动 | — |
| `ui/theme/Theme.kt` | 🟢 不动 | — |
| `ui/theme/Type.kt` | 🟢 不动 | — |
| `ui/component/LogViewer.kt` | 🟢 不动 | — |
| `ui/component/DebugCard.kt` | 🟢 不动 | — |
| `ui/component/ServerStatusCard.kt` | ✏️ 改 | 字段名适配（端口→模型→维度） |
| `data/StatsRepository.kt` | 🟢 不动 | — |
| `model/ModelInfo.kt` | ✏️ 改 | 换 `PRESET_MODELS` 为 Embedding 模型 |
| `model/ModelRepository.kt` | ✏️ 改 | `.litertlm` → `.tflite` |
| `model/ModelDownloader.kt` | ✏️ 改 | 描述字符串改 "RainyEmbedding" |
| `engine/TokenEstimator.kt` | ✏️ 改 | 删多模态，保留 `estimateSimple()` |
| `ui/screen/PerformanceScreen.kt` | ✏️ 改 | 引用 `RainyEmbeddingApp.modelsDir` |
| `ui/component/ModelDownloadCard.kt` | ✏️ 改 | 微调文案/emoji |
| `ui/component/TokenStatsChart.kt` | ✏️ 改 | 标签 "向量化请求趋势" |
| `data/AppPreferences.kt` | ✏️ 改 | 删 LLM 偏好，加 dimension/accelerator |
| `ui/navigation/Screen.kt` | ✏️ 改 | 路由精简为 3 个 |
| `MainActivity.kt` | ✏️ 重写 | 3 页导航 |
| `RainyLLMApp.kt` | ✏️ 改名 | → `RainyEmbeddingApp.kt` |
| `server/OpenAIServer.kt` | ✏️ 重写 | → `EmbeddingServer.kt`（/health + /v1/embeddings） |
| `service/LlmServerService.kt` | ✏️ 重写 | → `EmbeddingServerService.kt` |
| `ui/screen/DashboardScreen.kt` | ✏️ 重写 | Embedding 版主控台 |
| `ui/screen/ModelManagerScreen.kt` | ✏️ 重写 | Embedding 版模型管理 |
| `ui/screen/SettingsScreen.kt` | ✏️ 重写 | Embedding 版设置 |
| — | 🆕 新建 | `engine/EmbeddingEngine.kt` |
| — | 🆕 新建 | `engine/EmbeddingTokenizer.kt` |

---

## 🔧 核心技术预览

### Gradle 依赖

```kotlin
// app/build.gradle.kts
dependencies {
    // LiteRT CompiledModel API
    implementation("com.google.ai.edge.litert:litert:2.1.4")

    // HTTP 服务器
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // SentencePiece Tokenizer
    implementation("com.google.sentencepiece:sentencepiece-android:0.2.0")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

### EmbeddingEngine 核心

```kotlin
class EmbeddingEngine(
    private val modelPath: String,
    private val outputDimension: Int = 128  // Matryoshka 截断维度
) {
    private var compiledModel: CompiledModel? = null

    suspend fun initialize(accelerator: Accelerator = Accelerator.CPU) {
        compiledModel = CompiledModel.create(
            modelPath, CompiledModel.Options(accelerator))
    }

    fun embed(text: String): FloatArray {
        val tokenIds = EmbeddingTokenizer.encode(text, 512)
        val inputBuffers = compiledModel!!.createInputBuffers()
        inputBuffers.get(0).writeInt(tokenIds)
        val outputBuffers = compiledModel!!.createOutputBuffers()
        compiledModel!!.run(inputBuffers, outputBuffers)
        val full = outputBuffers.get(0).readFloat(768)
        return normalize(full.copyOf(outputDimension))
    }
}
```

### EmbeddingServer 路由

```kotlin
class EmbeddingServer(port: Int, engine: EmbeddingEngine) : NanoHTTPd("127.0.0.1", port) {
    override fun serve(session: IHTTPSession): Response = when {
        session.uri == "/health" -> handleHealthCheck()
        session.uri == "/v1/embeddings" && session.method == Method.POST ->
            handleEmbedding(session)
        else -> notFound()
    }
}
```

---

## 📐 开发路线图

```
阶段 1：骨架改造            阶段 2：引擎集成           阶段 3：API 服务           阶段 4：打磨
┌──────────────┐       ┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│ 删 LLM 代码   │       │ EmbeddingEngine│       │ EmbeddingServer│      │ Foreground   │
│ 改包名        │  →    │ Tokenizer     │  →    │ /v1/embeddings│  →   │ Service      │
│ 精简导航      │       │ 模型下载管理  │       │ 请求日志      │       │ UI 打磨      │
│ 更新依赖      │       │ 手动导入功能  │       │ 统计记录      │       │ 性能优化     │
└──────────────┘       └──────────────┘       └──────────────┘       └──────────────┘
```

---

## 🎯 最终效果

```
┌────────────────────────────────────────────────┐
│                 你的 Android 手机                │
│                                                 │
│  ┌──────────┐  POST /v1/embeddings              │
│  │ Operit   │───────┐                           │
│  │ AI 助手  │       │   ┌──────────────────┐    │
│  │          │       ├──▶│ RainyEmbedding    │    │
│  │ 记忆库   │       │   │ 127.0.0.1:8081   │    │
│  │ 语义搜索 │───────┘   │                   │    │
│  │          │           │ EmbeddingGemma   │    │
│  │ "昨天买   │──────────▶│ 300M · 128d      │    │
│  │  了茅台"  │  [0.12,   │                   │    │
│  │          │   -0.45,  │ CPU: 66ms        │    │
│  │          │    ...]    │ Memory: 110MB    │    │
│  └──────────┘           └──────────────────┘    │
│                                                 │
│  RainyLLM (可选)                                │
│  127.0.0.1:8080 · Gemma4 · /v1/chat/completions│
│                                                 │
│  🔒 端口可配，默认 8081（避免与 RainyLLM 冲突）    │
└────────────────────────────────────────────────┘
```

---

## 📥 模型获取

| 模型 | 大小 | 来源 | 方式 |
|------|------|------|------|
| EmbeddingGemma 300M | 179MB (.tflite) | [litert-community](https://huggingface.co/litert-community/embeddinggemma-300m) | 内置下载 / 手动导入 |
| SentencePiece Tokenizer | ~4MB (.model) | [google/embeddinggemma-300m](https://huggingface.co/google/embeddinggemma-300m) | 随模型一同获取 |

> ⚠️ EmbeddingGemma 需要先在 HuggingFace 上 Accept Gemma License。

---

## 📚 参考资源

| 资源 | 链接 |
|------|------|
| LiteRT Android 文档 | https://ai.google.dev/edge/litert/android |
| EmbeddingGemma HuggingFace | https://huggingface.co/litert-community/embeddinggemma-300m |
| LiteRT GitHub | https://github.com/google-ai-edge/LiteRT |
| NanoHTTPd | https://github.com/NanoHttpd/nanohttpd |
| SentencePiece | https://github.com/google/sentencepiece |
| Operit GitHub | https://github.com/AAswordman/Operit |

---

## 📄 License

MIT License © 2026