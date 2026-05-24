# 🔢 RainyEmbedding — 本地向量模型方案

> *"在手机上跑 EmbeddingGemma，让 AI 聊天软件的记忆库完全离线做语义搜索"* 🐱☁️

---

## 🔬 调研核实（2026-05-13）

在撰写本方案前，已完成以下关键信息的上网核实：

| 核实项 | 结论 | 来源 |
|--------|------|------|
| EmbeddingGemma 是否有 TFLite？ | ✅ `litert-community/embeddinggemma-300m` 提供现成 `.tflite`，**无需自己转换** | HF 页面 |
| LiteRT 最新版本？ | `com.google.ai.edge.litert:litert:2.1.4`（最新）；实际使用 **2.1.0** | ai.google.dev |
| TensorFlow Lite 重命名？ | ✅ Google 已将 TFLite 重命名为 **LiteRT**，旧坐标 `org.tensorflow:tensorflow-lite` 已过时 | Google AI Edge 文档 |
| SentencePiece Android 依赖？ | `com.google.sentencepiece:sentencepiece-android:0.2.0`（Google Maven） | Maven Central |
| CPU 推理实测速度？ | 66ms@256 tokens, 169ms@512 tokens（S25 Ultra, XNNPACK 4线程） | HF 模型页 |
| 模型大小/内存？ | 179MB / 110MB（CPU, mixed precision） | HF 基准数据 |
| Google AI Edge RAG Library？ | 存在（`localagents-rag`），但包含完整 RAG，仅需 embedding 时直接用 LiteRT 更轻量 | Maven |

---

## 📌 方案概要

RainyEmbedding 是一个独立的 Android App，仅负责文本向量化。通过 NanoHTTPd 暴露 `/v1/embeddings` OpenAI 兼容端点，集成 LiteRT CompiledModel API 运行 EmbeddingGemma 300M 模型，使 Operit 的记忆向量化完全在手机本地完成。

```
Operit 记忆库
     │
     │ POST /v1/embeddings
     │ {"model":"embedding-gemma","input":"我买了100股茅台"}
     ▼
┌──────────────────────────────────────┐
│          RainyEmbedding               │
│                                      │
│  NanoHTTPd — 127.0.0.1:${port}      │
│  ┌────────────────────────────────┐  │
│  │ /v1/embeddings       (核心)    │  │
│  │ /health               (监控)   │  │
│  └───────────┬────────────────────┘  │
│              │                       │
│  ┌───────────▼────────────────────┐  │
│  │   EmbeddingEngine              │  │
│  │   · LiteRT CompiledModel API   │  │
│  │   · EmbeddingGemma 300M        │  │
│  │   · Matryoshka 维度截断        │  │
│  └────────────────────────────────┘  │
│                                      │
│  Compose UI · Foreground Service     │
└──────────────────────────────────────┘

姐妹项目：RainyLLM 负责对话推理，RainyEmbedding 负责向量化，各司其职。
```

---

## 🧩 需求分析

### Operit 的 CloudEmbeddingService 期望格式

**请求**（发送到 `/v1/embeddings`）：
```json
{
  "model": "embedding-gemma",
  "input": "需要向量化的文本"
}
```

**响应**（关键字段来自 `parseEmbedding()`）：
```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.123, 0.456, ...]
    }
  ],
  "model": "embedding-gemma",
  "usage": {
    "prompt_tokens": 5,
    "total_tokens": 5
  }
}
```

> Operit 解析路径：`root.optJSONArray("data") → [0] → optJSONArray("embedding") → FloatArray`

### Operit endpoint 自动补全逻辑

```kotlin
// 来自 CloudEmbeddingService.completeEmbeddingsEndpoint()
endpoint = "http://127.0.0.1:8080"     → "http://127.0.0.1:8080/v1/embeddings"
endpoint = "http://127.0.0.1:8080/v1"  → "http://127.0.0.1:8080/v1/embeddings"
```

> ✅ 无需改动 Operit 配置，只需在 RainyEmbedding 中实现 `/v1/embeddings` 路由。

---

## 🔧 技术选型

| 候选方案 | 优点 | 缺点 | 评分 |
|---------|------|------|:---:|
| **LiteRT CompiledModel** | Android 原生、GPU/NPU 统一加速、API 简洁 | Google AI Edge 生态依赖 | ⭐⭐⭐⭐⭐ |
| ONNX Runtime | 通用性强、模型生态好 | 额外依赖 ~10MB | ⭐⭐⭐ |
| llama.cpp JNI | 成熟社区、支持 GGUF | JNI 层维护成本高 | ⭐⭐ |
| LiteRT-LM Embedding API | 与对话引擎统一 | ❌ 当前不支持 embedding 任务 | ⭐ |

### 🏆 选择：LiteRT CompiledModel API

理由：
1. `com.google.ai.edge.litert:litert:2.1.0`（实际使用；调研时最新 2.1.4）Google 官方 Maven，Android 原生
2. EmbeddingGemma 提供现成 `.tflite` 格式（`litert-community/embeddinggemma-300m`）
3. CPU 66ms@256tokens，NPU 7.8ms@256tokens（S25 Ultra 实测）
4. 模型 179MB + 运行时 ~110MB，独立 App 内存绰绰有余
5. 支持 CompiledModel API 的 GPU/NPU 加速，后续可按需开启

---

## 📁 新增/修改文件清单

```
RainyEmbedding/
├── app/build.gradle.kts                        ← 🔧 新增 litert 依赖
├── gradle/libs.versions.toml                   ← 🔧 新增版本声明
│
├── app/src/main/java/com/rainyembedding/app/
│   │
│   ├── engine/
│   │   └── EmbeddingEngine.kt                  ← 🆕 向量化引擎封装
│   │
│   ├── server/
│   │   └── EmbeddingServer.kt                  ← 🆕 NanoHTTPd 服务器
│   │
│   ├── model/
│   │   ├── ModelInfo.kt                        ← ✏️ 新增 EmbeddingGemma 模型信息
│   │   └── ModelDownloader.kt                  ← ✏️ 修改下载描述
│   │   ├── ModelRepository.kt                  ← ✏️ 扩展名 .tflite
│   │   └── ModelValidator.kt                   ← 🟢 不动
│   │
│   ├── service/
│   │   └── EmbeddingServerService.kt           ← 🆕 Foreground Service
│   │
│   ├── ui/screen/
│   │   └── SettingsScreen.kt                   ← ✏️ 新增 embedding 配置项
│   │
│   └── data/
│       └── AppPreferences.kt                   ← ✏️ 新增 embedding 偏好
```

**预估新增代码量**：~500 行 Kotlin

---

## 🧠 核心设计

### 1. EmbeddingEngine.kt（向量化引擎）

```kotlin
// engine/EmbeddingEngine.kt
package com.rainyembedding.app.engine

import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地 Embedding 向量化引擎
 *
 * 基于 LiteRT CompiledModel API + EmbeddingGemma 300M
 * 模型来源：HuggingFace `litert-community/embeddinggemma-300m`
 *
 * 性能参考 (S25 Ultra, CPU/XNNPACK):
 *  - Init: ~18ms（一次性）
 *  - Inference: 66ms@256tokens, 169ms@512tokens
 *  - Memory: ~110MB
 *  - Model: 179MB (.tflite)
 */
class EmbeddingEngine(
    private val modelPath: String,
    private val outputDimension: Int = 128  // Matryoshka 目标维度
) {
    companion object {
        private const val TAG = "EmbeddingEngine"
        private const val EMBEDDINGGEMMA_NATIVE_DIM = 768
        private const val MAX_SEQ_LENGTH = 512
    }

    private var compiledModel: CompiledModel? = null

    @Volatile
    var isInitialized: Boolean = false
        private set

    /**
     * 初始化 LiteRT CompiledModel（需在后台线程调用）
     */
    suspend fun initialize(accelerator: Accelerator = Accelerator.CPU) = withContext(Dispatchers.IO) {
        val modelFile = java.io.File(modelPath)
        if (!modelFile.exists()) {
            throw EmbeddingInitException("Embedding 模型不存在: $modelPath")
        }

        val options = CompiledModel.Options(accelerator)
        compiledModel = CompiledModel.create(modelPath, options)
        isInitialized = true
        Log.i(TAG, "✅ Embedding 引擎初始化成功 (dim=$outputDimension, accelerator=$accelerator)")
    }

    /**
     * 对单段文本做向量化
     * @return FloatArray，长度为 outputDimension
     */
    fun embed(text: String): FloatArray {
        val model = compiledModel
            ?: throw EmbeddingInitException("引擎未初始化")

        // 1. Tokenize → IntArray（SentencePiece BPE）
        val tokenIds = EmbeddingTokenizer.encode(text, MAX_SEQ_LENGTH)

        // 2. 创建输入 buffer（需要根据模型签名确定）
        val inputBuffers = model.createInputBuffers()
        inputBuffers.get(0).writeInt(tokenIds)

        // 3. 创建输出 buffer
        val outputBuffers = model.createOutputBuffers()

        // 4. 推理
        model.run(inputBuffers, outputBuffers)

        // 5. 读取完整 768 维向量
        val fullVector = outputBuffers.get(0).readFloat(EMBEDDINGGEMMA_NATIVE_DIM)

        // 6. Matryoshka 截断到目标维度（取前 outputDimension 维 + L2 normalize）
        return normalize(fullVector.copyOf(outputDimension))
    }

    /**
     * 批量向量化
     */
    fun embedBatch(texts: List<String>): List<FloatArray> {
        return texts.map { embed(it) }
    }

    fun close() {
        compiledModel?.close()
        compiledModel = null
        isInitialized = false
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val norm = kotlin.math.sqrt(vector.fold(0.0) { sum, v -> sum + v * v })
        if (norm > 0) {
            for (i in vector.indices) {
                vector[i] = (vector[i] / norm).toFloat()
            }
        }
        return vector
    }
}

class EmbeddingInitException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
```

> **关于输入/输出 Buffer 的类型**：EmbeddingGemma 模型的 TFLite 签名定义了确切的输入输出张量。CompiledModel API 的 `writeInt()`/`readFloat()` 是需要根据模型签名适配的。实际实现时需要通过 `compiledModel.signatures` 获取张量信息。

### 2. EmbeddingTokenizer.kt（分词器）

```kotlin
// engine/EmbeddingTokenizer.kt
package com.rainyembedding.app.engine

import com.google.sentencepiece.SentencePieceProcessor
import android.util.Log

/**
 * EmbeddingGemma 的 SentencePiece BPE Tokenizer
 *
 * 依赖：com.google.sentencepiece:sentencepiece-android:0.2.0
 * 模型文件：tokenizer.model（从 google/embeddinggemma-300m 下载，~4MB）
 */
object EmbeddingTokenizer {
    private const val TAG = "EmbeddingTokenizer"
    private var processor: SentencePieceProcessor? = null

    fun load(modelPath: String) {
        processor = SentencePieceProcessor(modelPath)
        Log.i(TAG, "Tokenizer 加载完成: vocab=${processor?.vocabSize()}")
    }

    fun encode(text: String, maxLength: Int = 512): IntArray {
        val p = processor
        if (p != null) {
            val ids = p.encode(text)
            return if (ids.size > maxLength) ids.copyOf(maxLength) else ids
        }
        // 降级：白空格分词 + hash ID（准确度极低，仅用于测试）
        Log.w(TAG, "Tokenizer 未加载，使用降级分词")
        val tokens = text.split(Regex("\\s+"))
            .map { it.hashCode() and 0x7FFFFFFF }
        return tokens.take(maxLength).toIntArray()
    }

    fun isLoaded(): Boolean = processor != null
}
```

> **SentencePiece 依赖可用性**：`com.google.sentencepiece:sentencepiece-android:0.2.0` 在 Google Maven 上可直接获取，不需要额外编译。模型文件 (`tokenizer.model`) 与标准 Gemma tokenizer 格式相同。

### 3. OpenAIServer.kt 新增路由

```kotlin
// server/OpenAIServer.kt — 新增部分

class OpenAIServer(
    // ... 原有参数
    private val embeddingEngine: EmbeddingEngine? = null,  // 🆕
    private val embeddingModelId: String = "embedding-gemma"  // 🆕
) : NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        // ... 原有路由
        return try {
            val response = when {
                // ... 原有 case
                uri == "/v1/embeddings" && method == Method.POST -> {
                    handleEmbedding(session)  // 🆕
                }
                else -> handleNotFound()
            }
            // ...
        }
    }

    // 🆕 /v1/embeddings 端点
    private fun handleEmbedding(session: IHTTPSession): Response {
        if (embeddingEngine == null) {
            return jsonResponse(
                Response.Status.SERVICE_UNAVAILABLE,
                """{"error":{"message":"Embedding engine not loaded"}}"""
            )
        }

        val bodyJson = parseBodyUtf8(session)
        if (bodyJson.isBlank()) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                """{"error":{"message":"Empty request body"}}"""
            )
        }

        val request = try {
            org.json.JSONObject(bodyJson)
        } catch (e: Exception) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                """{"error":{"message":"Invalid JSON"}}"""
            )
        }

        val input = request.optString("input", "")
        val model = request.optString("model", embeddingModelId)

        if (input.isBlank()) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                """{"error":{"message":"input is required"}}"""
            )
        }

        return try {
            val startTime = System.currentTimeMillis()
            val vector = embeddingEngine.embed(input)
            val elapsed = System.currentTimeMillis() - startTime

            val responseJson = buildEmbeddingResponseJson(
                vector = vector,
                model = model,
                promptTokens = TokenEstimator.estimatePromptTokens(input)
            )

            Log.i(TAG, "Embedding: ${vector.size}d, ${elapsed}ms")
            jsonResponse(Response.Status.OK, responseJson)
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed: ${e.message}", e)
            jsonResponse(
                Response.Status.INTERNAL_ERROR,
                """{"error":{"message":"Embedding failed: ${e.message}}"""
            )
        }
    }

    /** 构建 OpenAI 兼容的 embedding 响应 */
    private fun buildEmbeddingResponseJson(
        vector: FloatArray,
        model: String,
        promptTokens: Int
    ): String {
        // 直接用字符串拼接，避免 JSONArray 开销
        val sb = StringBuilder()
        sb.append("[")
        vector.forEachIndexed { i, v ->
            if (i > 0) sb.append(",")
            sb.append(v.toString())
        }
        sb.append("]")
        val embeddingArray = sb.toString()

        return """{"object":"list","data":[{"object":"embedding","index":0,"embedding":$embeddingArray}],"model":"$model","usage":{"prompt_tokens":$promptTokens,"total_tokens":$promptTokens}}"""
    }
}
```

### 4. Gradle 依赖变更

```toml
# gradle/libs.versions.toml — 新增
[versions]
# ... 现有
litert = "2.1.0"               # ← 实际使用版本（调研时最新为2.1.4）
sentencepiece = "0.2.0"

[libraries]
# ... 现有
litert = { group = "com.google.ai.edge.litert", name = "litert", version.ref = "litert" }
sentencepiece = { group = "com.google.sentencepiece", name = "sentencepiece-android", version.ref = "sentencepiece" }
```

```kotlin
// app/build.gradle.kts — 新增
dependencies {
    // ... 现有
    implementation(libs.litert)           // LiteRT CompiledModel API
    implementation(libs.sentencepiece)    // SentencePiece tokenizer
}
```

> **为什么用 `litert:2.1.0` 而不是 `tensorflow-lite:2.17.0`**：Google 已将 TensorFlow Lite 重命名为 LiteRT。v2.x 提供 CompiledModel API（支持 CPU/GPU/NPU 统一加速），v1.x 仅提供 Interpreter API（向后兼容）。推荐 v2.x。

### 5. LlmServerService.kt 变更

```kotlin
// service/LlmServerService.kt — 关键变更

class LlmServerService : Service() {
    // ... 原有字段
    private var embeddingEngine: EmbeddingEngine? = null  // 🆕

    private fun initializeEngine(...) {
        // ... 原有初始化

        // 🆕 初始化 Embedding 引擎
        val embedModelPath = "${filesDir.path}/models/embedding_gemma_300m.tflite"
        val embedTokenizerPath = "${filesDir.path}/models/embedding_gemma_tokenizer.model"

        try {
            EmbeddingTokenizer.load(embedTokenizerPath)
            embeddingEngine = EmbeddingEngine(embedModelPath, outputDimension = 128)
            kotlinx.coroutines.runBlocking {
                embeddingEngine!!.initialize()
            }
            Log.i(TAG, "✅ Embedding 引擎就绪")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Embedding 引擎加载失败（语义搜索将不可用）: ${e.message}")
            embeddingEngine = null
        }

        val server = OpenAIServer(
            port, engine, modelId, samplerConfig,
            embeddingEngine = embeddingEngine,    // 🆕
            embeddingModelId = "embedding-gemma", // 🆕
            samplerConfigSupplier = { ... }
        )
        // ...
    }

    private fun stopAll() {
        // ... 原有清理
        embeddingEngine?.close()  // 🆕
        embeddingEngine = null   // 🆕
    }
}
```

---

### 模型获取

#### 🏆 选项 A：EmbeddingGemma 300M（强烈推荐）

| 属性 | 值 |
|------|-----|
| HuggingFace | `litert-community/embeddinggemma-300m` |
| 参数量 | 308M |
| 原生维度 | 768（Matryoshka 可截断） |
| 量化 | Mixed Precision (e4_a8_f4_p4) |
| 模型文件 | `.tflite`，~179MB |
| CPU 推理 (256 tokens) | **66ms**（S25 Ultra, XNNPACK 4线程） |
| CPU 推理 (512 tokens) | **169ms** |
| 内存占用 | ~110MB（CPU） |
| 初始化时间 | ~18ms（一次性） |
| NPU 推理 (256 tokens) | **7.8ms** |
| 许可证 | Gemma License（需在 HF 上 Accept） |

**文件列表**（从 HuggingFace 下载）：
- `embeddinggemma-300m.tflite` — 模型主体
- `tokenizer.model` — SentencePiece 分词器（单独从 `google/embeddinggemma-300m` 获取）

> **下载方式**：用户在 HuggingFace 上 Accept Gemma License → RainyEmbedding 内置下载器或手动放置到 `filesDir/models/`

#### 选项 B：all-MiniLM-L6-v2（轻量备选）

| 属性 | 值 |
|------|-----|
| 维度 | 384 |
| 模型大小 | ~90MB |
| CPU 推理 | <5ms |
| 来源 | HuggingFace（有现成 TFLite） |

---

## 🔄 部署流程

```
1. 在 RainyEmbedding 中下载 Embedding 模型
   ├── 打开「模型管理」→ 选择 EmbeddingGemma
   └── 下载 ~179MB 模型文件 + tokenizer.model (~4MB)

2. 启动服务
   ├── EmbeddingServerService 初始化 EmbeddingEngine + EmbeddingTokenizer
   └── 通知栏显示 "🔢 RainyEmbedding 运行中 | 端口: 8081"

3. 配置 Operit
├── 记忆搜索设置 → 云端 Embedding
├── 开启开关
├── Endpoint → http://127.0.0.1:8081
   ├── API Key → (留空，本地不需要)
   └── Model → embedding-gemma

4. 测试
├── curl -X POST http://127.0.0.1:8081/v1/embeddings \
   │     -H "Content-Type: application/json" \
   │     -d '{"model":"embedding-gemma","input":"你好世界"}'
   └── 返回 {"data":[{"embedding":[0.123,-0.456,...]}]}
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
│  RainyLLM (可选，负责对话)                       │
│  127.0.0.1:8081 · Gemma4 · /v1/chat/completions│
│                                                 │
│  🔒 所有数据完全离线，零网络传输                    │
└────────────────────────────────────────────────┘
```

---

## ⚠️ 关键注意事项

| 事项 | 说明 | 核实状态 |
|------|------|:---:|
| 📦 **依赖坐标** | `com.google.ai.edge.litert:litert:2.1.0`（实际；最新 2.1.4）| ✅ 官方文档核实 |
| 🧠 **模型格式** | `litert-community/embeddinggemma-300m` 提供现成 `.tflite`，**不需要自己转换** | ✅ HF 页面核实 |
| 🔤 **Tokenizer** | SentencePiece `tokenizer.model` 需单独下载（~4MB），Maven 依赖 `com.google.sentencepiece:sentencepiece-android:0.2.0` | ✅ 官方指南 |
| 📐 **维度选择** | 128 维用于节省 Operit 存储，256 维精度更佳。Settings 可调 | ✅ Matryoshka 原生支持 |
| 🔒 **隐私** | embedding 请求走 127.0.0.1，完全离线 | ✅ |
| 💾 **内存** | EmbeddingGemma 运行时约 110MB，手机 RAM 4GB+ 即可 | ✅ 基准数据 |
| 🔌 **独立运行** | 不需要 RainyLLM。Embedding 引擎不依赖对话引擎 | ✅ |
| 📦 **APK 体积** | 新增 LiteRT (~2MB) + SentencePiece (~1MB)，模型 179MB 需单独下载 | ✅ |
| 📥 **模型许可** | 需在 HuggingFace 上 Accept Gemma License 才能下载 | ⚠️ 必须 |
| 🚀 **NPU 加速** | 可选。S25 Ultra NPU 仅 7.8ms（256 tokens），但需适配 | 未来优化 |

---

*方案设计日期：2026-05-13*
*RainyEmbedding 独立 App 方案*
