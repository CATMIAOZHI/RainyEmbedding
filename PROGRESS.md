# 📋 RainyEmbedding 开发进度追踪

> ⚠️ **AI 执行规则（必读）**：
> 1. **禁止自作主张删除功能**。任何涉及删除文件、移除功能、省略特性的操作，必须先向用户确认。
> 2. **拿不准就问用户**。技术选型不确定、两种方案难抉择、代码意图不清楚时，列出选项让用户决定。
> 3. **删 vs 改 vs 新建**：优先「改」现有文件以适配新需求，其次「新建」，最后「删」（需确认）。
> 4. **删除清单仅 5 个文件**：`LlmEngine.kt`、`RequestParser.kt`、`SseFormatter.kt`、`ChatTestScreen.kt`、`KeepAliveService.kt`。其他不要擅自删除。

> 格式说明：`- [ ]` 待完成 | `- [~]` 进行中 | `- [x]` 已完成 | `- [-]` 已跳过  
> 每个任务完成后，在行尾追加 `<!-- 完成日期 YYYY-MM-DD HH:MM -->`

---

## 🏗️ 阶段 1：骨架改造（从 RainyLLM 删减）

### 1.1 包名与项目配置
- [x] 1.1.1 修改 `settings.gradle.kts`：rootProject.name = "RainyEmbedding" <!-- 完成日期 2026-05-13 11:16 -->
- [x] 1.1.2 修改 `app/build.gradle.kts`：namespace = "com.rainyembedding.app" <!-- 完成日期 2026-05-13 11:16 -->
- [x] 1.1.3 修改 `app/build.gradle.kts`：applicationId = "com.rainyembedding.app" <!-- 完成日期 2026-05-13 11:17 -->
- [x] 1.1.4 修改 `app/src/main/res/values/strings.xml`：app_name = "雨晴向量" <!-- 完成日期 2026-05-13 11:17 -->
- [x] 1.1.5 重命名源码目录：`com/rainyllm/app` → `com/rainyembedding/app` <!-- 完成日期 2026-05-13 11:18 -->
- [x] 1.1.6 更新所有 Kotlin 文件中的 `package` 声明为新包名 <!-- 完成日期 2026-05-13 11:22 -->
- [x] 1.1.7 更新 `AndroidManifest.xml` 中的 Application/Service/Theme 名称 <!-- 完成日期 2026-05-13 11:23 -->

### 1.2 依赖更新
- [x] 1.2.1 删除 `litertlm-android` 依赖（不再需要 LLM 推理） <!-- 完成日期 2026-05-13 11:17 -->
- [x] 1.2.2 新增 `com.google.ai.edge.litert:litert:2.1.4`（LiteRT CompiledModel） <!-- 完成日期 2026-05-13 11:17 -->
- [x] 1.2.3 新增 `com.google.sentencepiece:sentencepiece-android:0.2.0`（Tokenizer） <!-- 完成日期 2026-05-13 11:17 -->
- [x] 1.2.4 更新 `gradle/libs.versions.toml` 声明所有新版本 <!-- 完成日期 2026-05-13 11:17 -->

### 1.3 删除 LLM 专属代码（仅 5 个，不要多删）

- [x] 1.3.1 删除 `engine/LlmEngine.kt`（LLM 推理引擎，Embedding 不需要） <!-- 完成日期 2026-05-13 11:22 -->
- [x] 1.3.2 删除 `server/RequestParser.kt`（解析 /v1/chat/completions 复杂请求体） <!-- 完成日期 2026-05-13 11:22 -->
- [x] 1.3.3 删除 `server/SseFormatter.kt`（SSE 流式格式化，Embedding 无流式） <!-- 完成日期 2026-05-13 11:22 -->
- [x] 1.3.4 删除 `ui/screen/ChatTestScreen.kt`（聊天 UI，53KB） <!-- 完成日期 2026-05-13 11:22 -->
- [x] 1.3.5 删除 `service/KeepAliveService.kt`（供 OpenAIServer 用，EmbeddingServerService 自带通知） <!-- 完成日期 2026-05-13 11:22 -->

> ⚠️ 以下不删：ModelValidator(纯SHA256)、ModelDownloader(纯下载器)、ModelRepository(scan/import通用)、ModelInfo(保留data class改预设)、TokenEstimator(保留简化版)、PerformanceScreen(系统监控)、TokenStatsChart(折线图组件)、ModelDownloadCard(下载进度卡片)

### 1.4 保留但需修改的文件（改包名 + 改适配）

- [x] 1.4.1 `model/ModelInfo.kt` — 换 `PRESET_MODELS` 为 EmbeddingGemma + all-MiniLM <!-- 完成日期 2026-05-13 11:28 -->
- [x] 1.4.2 `model/ModelRepository.kt` — `.litertlm` → `.tflite`，适配新 PRESET_MODELS <!-- 完成日期 2026-05-13 11:28 -->
- [x] 1.4.3 `model/ModelDownloader.kt` — 描述字符串 "RainyLLM" → "RainyEmbedding" <!-- 完成日期 2026-05-13 11:24 -->
- [x] 1.4.4 `model/ModelValidator.kt` — **不改**（100% 通用） <!-- 完成日期 2026-05-13 11:18 -->
- [x] 1.4.5 `engine/TokenEstimator.kt` — 删多模态相关，保留 `estimateSimple()` 做 fallback <!-- 完成日期 2026-05-13 11:28 -->
- [x] 1.4.6 `ui/screen/PerformanceScreen.kt` — `RainyLLMApp.modelsDir` → `RainyEmbeddingApp.modelsDir` <!-- 完成日期 2026-05-13 11:24 -->
- [x] 1.4.7 `ui/component/ModelDownloadCard.kt` — 微调文案（"🔢" emoji、MB 显示） <!-- 完成日期 2026-05-13 11:29 -->
- [x] 1.4.8 `ui/component/TokenStatsChart.kt` — 标签 "Token 使用趋势" → "向量化请求趋势" <!-- 完成日期 2026-05-13 11:29 -->
- [x] 1.4.9 `server/OpenAIServer.kt` → 改为 `EmbeddingServer.kt`，路由精简为 /health + /v1/embeddings <!-- 完成日期 2026-05-13 11:58 -->
- [x] 1.4.10 `service/LlmServerService.kt` → 改为 `EmbeddingServerService.kt`，去掉 LlmEngine 依赖 <!-- 完成日期 2026-05-13 12:10 -->
- [x] 1.4.11 `data/AppPreferences.kt` — 删 LLM 偏好（temperature/topK等），加 dimension/accelerator <!-- 完成日期 2026-05-13 12:08 -->
- [x] 1.4.12 `RainyLLMApp.kt` → 改名为 `RainyEmbeddingApp.kt` <!-- 完成日期 2026-05-13 11:19 -->
- [x] 1.4.13 `MainActivity.kt` — 适配新导航（3 页）和新类名 <!-- 完成日期 2026-05-13 12:12 -->
- [x] 1.4.14 `ui/navigation/Screen.kt` — 路由精简为 3 个（主控台、模型管理、设置） <!-- 完成日期 2026-05-13 12:11 -->

### 1.5 构建配置更新
- [x] 1.5.1 确认 `minSdk` ≥ 23（LiteRT 要求） — 当前 minSdk=24 ✅ <!-- 完成日期 2026-05-13 11:24 -->
- [x] 1.5.2 确认 `compileSdk` / `targetSdk` 适配当前最新 — 35 ✅ <!-- 完成日期 2026-05-13 11:24 -->
- [x] 1.5.3 更新 `AndroidManifest.xml`：删除 `libvndksupport.so` 声明（GPU shader 依赖，LLM 专用） <!-- 完成日期 2026-05-13 11:23 -->

---

## 🧠 阶段 2：Embedding 引擎集成

### 2.1 EmbeddingEngine
- [x] 2.1.1 创建 `engine/EmbeddingEngine.kt`：LiteRT CompiledModel 封装 <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.1.2 实现 `initialize(accelerator)` 异步初始化 <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.1.3 实现 `embed(text): FloatArray` 单文本向量化 <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.1.4 实现 Matryoshka 维度截断（768→128/256/512） <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.1.5 实现 L2 归一化 <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.1.6 实现 `embedBatch(texts): List<FloatArray>` 批量向量化 <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.1.7 实现 `close()` 资源释放 <!-- 完成日期 2026-05-13 11:32 -->

### 2.2 EmbeddingTokenizer
- [x] 2.2.1 创建 `engine/EmbeddingTokenizer.kt`：SentencePiece BPE 封装 <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.2.2 实现 `load(modelPath)` 加载 tokenizer.model <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.2.3 实现 `encode(text, maxLength): IntArray` <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.2.4 实现降级分词（无 tokenizer 时的 fallback） <!-- 完成日期 2026-05-13 11:32 -->
- [x] 2.2.5 实现 `isLoaded()` 状态查询 <!-- 完成日期 2026-05-13 11:32 -->

---

## 🌐 阶段 3：HTTP API 服务

### 3.1 EmbeddingServer
- [x] 3.1.1 创建 `server/EmbeddingServer.kt`：继承 NanoHTTPd（全新独立文件，299 行） <!-- 完成日期 2026-05-13 11:34 -->
- [x] 3.1.2 实现 `/health` 端点：返回模型状态、维度、运行时长 <!-- 完成日期 2026-05-13 11:34 -->
- [x] 3.1.3 实现 `/v1/embeddings` 端点：解析请求、调用引擎、返回 OpenAI 格式 <!-- 完成日期 2026-05-13 11:34 -->
- [x] 3.1.4 构建符合 Operit 解析格式的响应 JSON（`data[0].embedding`） <!-- 完成日期 2026-05-13 11:34 -->
- [x] 3.1.5 添加 CORS 头 + OPTIONS 预检 <!-- 完成日期 2026-05-13 11:34 -->
- [x] 3.1.6 实现请求日志记录 <!-- 完成日期 2026-05-13 11:34 -->
- [x] 3.1.7 实现统计计数（总请求数、总 tokens、平均耗时） <!-- 完成日期 2026-05-13 11:34 -->

---

## 📦 阶段 4：模型下载与管理（基于现有 model/ 文件修改）

### 4.1 模型元数据
- [x] 4.1.1 修改 `model/ModelInfo.kt`：`PRESET_MODELS` 替换为 EmbeddingGemma 300M + all-MiniLM-L6-v2（阶段 1 已完成）
- [x] 4.1.2 预置 EmbeddingGemma 300M 模型信息（id/name/size/sha256/url）— 阶段 1 已完成
- [x] 4.1.3 预置 all-MiniLM-L6-v2 模型信息（轻量备选）— 阶段 1 已完成

### 4.2 模型下载
- [x] 4.2.1 修改 `model/ModelDownloader.kt`：描述字符串 "RainyLLM" → "RainyEmbedding" — 阶段 1 已完成
- [x] 4.2.2 保持内置下载逻辑（DownloadManager 复用） <!-- 完成日期 2026-05-13 11:36 -->
- [x] 4.2.3 实现存储空间检查（剩余 > 模型 + 500MB） <!-- 完成日期 2026-05-13 11:36 -->
- [x] 4.2.4 ⚠️ HuggingFace 认证：下载需在 ModelDownloader 请求中添加 `Authorization: Bearer <HF_TOKEN>` header <!-- 完成日期 2026-05-13 11:36 -->

### 4.3 手动导入
- [x] 4.3.1 实现文件选择器：选择 `.tflite` 模型文件 <!-- 完成日期 2026-05-13 11:37 -->
- [x] 4.3.2 实现文件选择器：选择 `tokenizer.model` <!-- 完成日期 2026-05-13 11:37 -->
- [x] 4.3.3 复制到 `filesDir/models/` 并注册（复用 `ModelRepository.importModelFromStream`） <!-- 完成日期 2026-05-13 11:37 -->

### 4.4 模型校验
- [x] 4.4.1 `model/ModelValidator.kt` — **不动**，通用 SHA256 — 阶段 1 已确认
- [x] 4.4.2 使用时传入 `sha256` 即可，无需改代码 — 阶段 1 已确认

### 4.5 模型管理
- [x] 4.5.1 修改 `model/ModelRepository.kt`：扩展名 `.litertlm` → 支持 `.tflite` — 阶段 1 已完成
- [x] 4.5.2 扫描逻辑复用（`scanDownloadedModels`） — 阶段 1 已完成
- [x] 4.5.3 模型删除逻辑复用 — 阶段 1 已完成
- [x] 4.5.4 当前模型持久化复用（DataStore） — 阶段 1 已确认
- [x] 4.5.5 模型切换逻辑复用 — 阶段 1 已确认

### 4.6 模型管理 UI
- [x] 4.6.1 重写 `ModelManagerScreen.kt`：适配 Embedding 模型（下载+导入+切换+删除） <!-- 完成日期 2026-05-13 11:37 -->
- [x] 4.6.2 实现下载按钮 + 进度条（复用 ModelDownloadCard） <!-- 完成日期 2026-05-13 11:37 -->
- [x] 4.6.3 实现手动导入按钮（.tflite + tokenizer.model 双文件） <!-- 完成日期 2026-05-13 11:37 -->
- [x] 4.6.4 实现模型选中/切换 UI <!-- 完成日期 2026-05-13 11:37 -->
- [x] 4.6.5 实现删除确认对话框 <!-- 完成日期 2026-05-13 11:37 -->

---

## ⚙️ 阶段 5：后台服务与保活

### 5.1 EmbeddingServerService
- [x] 5.1.1 创建 `service/EmbeddingServerService.kt`：继承 Service（257 行） <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.1.2 在初始化线程中加载 `EmbeddingEngine` + `EmbeddingTokenizer`（异步） <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.1.3 引擎初始化后启动 `EmbeddingServer` <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.1.4 在 `onDestroy()` 中停止服务器、关闭引擎、释放 tokenizer <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.1.5 创建通知渠道（`rainyembedding_server`） <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.1.6 构建前台通知："🔢 RainyEmbedding 运行中 | 端口: 8081" <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.1.7 调用 `startForeground()` <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.1.8 实现初始化失败时的 self-stop 保护 <!-- 完成日期 2026-05-13 12:10 -->

### 5.2 WakeLock
- [x] 5.2.1 获取 `PARTIAL_WAKE_LOCK` <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.2.2 推理时保持，10 分钟超时自动释放 <!-- 完成日期 2026-05-13 12:10 -->

### 5.3 服务控制
- [x] 5.3.1 通知栏显示运行状态 + 端口 + 维度 <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.3.2 `ACTION_START_SERVER` / `ACTION_STOP_SERVER` 意图控制 <!-- 完成日期 2026-05-13 12:10 -->
- [x] 5.3.3 EXTRA 传递模型路径、分词器路径、端口、维度、加速后端 <!-- 完成日期 2026-05-13 12:10 -->

---

## 🎨 阶段 6：UI 完善

### 6.1 DashboardScreen（重写）
- [x] 6.1.1 服务状态卡片：端口、模型、维度、运行时长 <!-- 完成日期 2026-05-13 12:14 -->
- [x] 6.1.2 快捷操作：启动/停止按钮（使用 EmbeddingServerService） <!-- 完成日期 2026-05-13 12:14 -->
- [x] 6.1.3 向量统计：总请求数、总 tokens、平均耗时 <!-- 完成日期 2026-05-13 12:14 -->
- [x] 6.1.4 请求日志（复用 LogViewer） <!-- 完成日期 2026-05-13 12:14 -->

### 6.2 SettingsScreen（重写）
- [x] 6.2.1 端口号配置（默认 8081） <!-- 完成日期 2026-05-13 12:13 -->
- [x] 6.2.2 输出维度选择（128 / 256 / 512 / 768） <!-- 完成日期 2026-05-13 12:13 -->
- [x] 6.2.3 加速后端选择（CPU / GPU / NPU） <!-- 完成日期 2026-05-13 12:13 -->
- [x] 6.2.4 空闲超时时间配置 <!-- 完成日期 2026-05-13 12:13 -->
- [x] 6.2.5 DataStore 持久化所有设置 <!-- 完成日期 2026-05-13 12:13 -->

### 6.3 组件复用
- [x] 6.3.1 ServerStatusCard — 直接复用 <!-- 完成日期 2026-05-13 12:14 -->
- [x] 6.3.2 ModelDownloadCard — 已适配 Embedding 模型（阶段 4） <!-- 完成日期 2026-05-13 12:14 -->
- [x] 6.3.3 LogViewer — 直接复用 <!-- 完成日期 2026-05-13 12:14 -->
- [x] 6.3.4 DebugCard — 直接复用 <!-- 完成日期 2026-05-13 12:14 -->
- [x] 6.3.5 Theme（Color/Theme/Type）— 保持，已去掉 "LLM" 元素 <!-- 完成日期 2026-05-13 12:14 -->

---

## 🧹 阶段 7：打磨与构建

### 7.1 启动流程
- [x] 7.1.1 `RainyEmbeddingApp.kt`：Application 类（已精简，移除 KeepAlive） <!-- 完成日期 2026-05-13 12:10 -->
- [x] 7.1.2 初始化 modelsDir <!-- 完成日期 2026-05-13 11:19 -->
- [x] 7.1.3 更新 AndroidManifest 注册 Application 类 <!-- 完成日期 2026-05-13 11:23 -->

### 7.2 错误处理
- [x] 7.2.1 引擎未初始化时调用 API → EmbeddingServer 返回 503 <!-- 完成日期 2026-05-13 11:58 -->
- [x] 7.2.2 模型文件/Tokenizer 缺失时的引导流程 <!-- 完成日期 2026-05-13 12:14 -->
- [x] 7.2.3 后端不可用时自动降级 CPU <!-- 完成日期 2026-05-13 12:10 -->
- [x] 7.2.4 存储不足警告 → ModelManagerScreen 已实现 <!-- 完成日期 2026-05-13 11:37 -->

### 7.3 性能优化
- [x] 7.3.1 引擎预热完成后通知 UI <!-- 完成日期 2026-05-13 12:10 -->
- [x] 7.3.2 模型加载进度 UI（通知栏状态提示） <!-- 完成日期 2026-05-13 12:10 -->
- [x] 7.3.3 请求队列管理（单线程串行，无并发问题） <!-- 完成日期 2026-05-13 11:58 -->

### 7.4 构建配置
- [x] 7.4.1 配置 ProGuard/R8 混淆规则（保护 LiteRT + NanoHTTPd） <!-- 完成日期 2026-05-13 12:24 -->
- [x] 7.4.2 更新 `build.gradle.kts`：versionCode = 1, versionName = "1.0.0" <!-- 完成日期 2026-05-13 11:24 -->
- [x] 7.4.3 `allowBackup="false"` <!-- 完成日期 2026-05-13 11:23 -->
- [x] 7.4.4 验证：`./gradlew assembleDebug` → **BUILD SUCCESSFUL in 9s** <!-- 完成日期 2026-05-13 12:30 -->

---

## ✅ 已解决问题

| 问题 | 解决方案 |
|------|------|
| **SentencePiece 依赖不可用** | 集成纯 Kotlin SentencePiece BPE tokenizer（基于 IliyaBrook/InstantVoiceTranslate），解析 protobuf `.model` 文件，零原生依赖 |

---

## 📊 任务统计

| 阶段 | 任务数 | 状态 |
|------|:-----:|:----:|
| 阶段 1：骨架改造 | 33 | ✅ 基础完成（23/33，5 项延后） |
| 阶段 2：Embedding 引擎 | 12 | ✅ 已完成 |
| 阶段 3：HTTP API 服务 | 7 | ✅ 已完成 |
| 阶段 4：模型下载与管理 | 22 | ✅ 已完成 |
| 阶段 5：后台服务与保活 | 13 | ✅ 已完成 |
| 阶段 6：UI 完善 | 14 | ✅ 已完成 |
| 阶段 7：打磨与构建 | 14 | ✅ 已完成 |
| **总计** | **115** | |

---

## 🔗 快速导航

- [PROJECT_PLAN.md](PROJECT_PLAN.md) — 完整技术规划
- [EMBEDDING_DESIGN.md](EMBEDDING_DESIGN.md) — 详细技术方案
- [README.md](README.md) — 项目介绍

---

*文件创建日期：2026-05-13*