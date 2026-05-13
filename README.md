# 🔢 RainyEmbedding (雨晴向量)

> ⚠️ **开发规则**：禁止删功能不先问、拿不准不确认。优先改代码而非删除。

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

## 🏗️ 技术栈

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
│   │   └── TokenEstimator.kt             # Token 估算 fallback（保留简化版）
│   ├── server/
│   │   └── EmbeddingServer.kt            # NanoHTTPd (/v1/embeddings + /health)
│   ├── model/
│   │   ├── ModelInfo.kt                  # 模型元数据（EmbeddingGemma 300M + NPU 优化版）
│   │   ├── ModelRepository.kt            # 模型扫描/切换/导入
│   │   ├── ModelDownloader.kt            # DownloadManager 下载
│   │   └── ModelValidator.kt             # SHA256 校验（通用）
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
│       ├── navigation/
│       │   └── Screen.kt                # 3 页面路由
│       └── theme/
│           ├── Color.kt / Theme.kt / Type.kt
└── gradle/libs.versions.toml
```

**26 个 Kotlin 源文件，~3000 行代码。**

---

## 🔒 安全

- ✅ 127.0.0.1 绑定，仅限本机
- ✅ allowBackup="false"
- ✅ 纯离线，零网络请求（模型下载除外）
- ✅ 隐私数据（财务、持仓等）不离开手机

---

## 🐱 关于

「雨晴系列」工具之一：

- [RainyLLM](https://github.com/CATMIAOZHI/RainyLLM) — 本地 LLM 推理服务器
- **RainyEmbedding** — 本地文本向量化服务器（本项目）
- [RainyScanner](https://github.com/CATMIAOZHI/RainyScanner) — 扫码工具
- [Rainy2FA](https://github.com/CATMIAOZHI/Rainy2FA) — TOTP 验证器

---

## 📄 License

MIT License © 2026

---

*Made with 🔢 and 🐱 paws*