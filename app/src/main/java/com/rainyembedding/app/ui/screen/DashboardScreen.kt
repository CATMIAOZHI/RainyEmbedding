package com.rainyembedding.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rainyembedding.app.RainyEmbeddingApp
import com.rainyembedding.app.data.AppPreferences
import com.rainyembedding.app.data.StatsRepository
import com.rainyembedding.app.model.ModelRepository
import com.rainyembedding.app.server.EmbeddingServer
import com.rainyembedding.app.service.EmbeddingServerService
import com.rainyembedding.app.ui.component.DebugCard
import com.rainyembedding.app.ui.component.LogViewer
import com.rainyembedding.app.ui.component.ServerStatusCard
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun DashboardScreen(isVisible: Boolean = true) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prefs = remember { AppPreferences(context) }

    var port by remember { mutableIntStateOf(8081) }
    var selectedModel by remember { mutableStateOf("embeddinggemma-300m") }
    var dimension by remember { mutableIntStateOf(128) }
    var accelerator by remember { mutableStateOf("cpu") }
    LaunchedEffect(Unit) { prefs.serverPort.collect { port = it } }
    LaunchedEffect(Unit) { prefs.selectedModel.collect { selectedModel = it } }
    LaunchedEffect(Unit) { prefs.outputDimension.collect { dimension = it } }
    LaunchedEffect(Unit) { prefs.accelerator.collect { accelerator = it } }

    var isServerRunning by remember { mutableStateOf(false) }
    var isEngineReady by remember { mutableStateOf(false) }
    var uptimeSec by remember { mutableLongStateOf(0L) }
    var requestLog by remember { mutableStateOf(listOf<com.rainyembedding.app.ui.component.LogEntry>()) }
    var statsSummary by remember { mutableStateOf(StatsRepository.StatsSummary()) }
    var debugText by remember { mutableStateOf("") }
    var cachedLog by remember { mutableStateOf(listOf<com.rainyembedding.app.ui.component.LogEntry>()) }

    val logFile = remember { java.io.File(context.filesDir, "request_logs.json") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                if (logFile.exists()) {
                    val json = logFile.readText()
                    val arr = JSONArray(json)
                    val loaded = (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        com.rainyembedding.app.ui.component.LogEntry(
                            timestamp = obj.getLong("timestamp"),
                            method = obj.getString("method"),
                            path = obj.getString("path"),
                            statusCode = obj.getInt("statusCode"),
                            elapsedMs = obj.getLong("elapsedMs"),
                            requestBody = obj.optString("requestBody", ""),
                            responseSummary = obj.optString("responseSummary", ""),
                            promptTokens = obj.optInt("promptTokens", 0)
                        )
                    }
                    if (loaded.isNotEmpty()) cachedLog = loaded.takeLast(1000)
                }
            } catch (_: Exception) {}
        }
    }

    var initError by remember { mutableStateOf<String?>(null) }
    var isStarting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        while (true) {
            val server = EmbeddingServer.currentInstance
            isServerRunning = server?.isServerRunning == true
            isEngineReady = server?.isServerRunning == true
            initError = EmbeddingServerService.lastInitError
            if (isServerRunning || initError != null) isStarting = false

            statsSummary = StatsRepository.instance?.getSummary()
                ?: StatsRepository.StatsSummary()

            if (isServerRunning && server != null) {
                uptimeSec = (System.currentTimeMillis() - server.getStats().startTime) / 1000
                debugText = server.getDebugInfo()
                val serverLogs = server.getRequestLog().map { entry ->
                    com.rainyembedding.app.ui.component.LogEntry(
                        timestamp = entry.timestamp,
                        method = entry.method,
                        path = entry.path,
                        statusCode = entry.statusCode,
                        elapsedMs = entry.elapsedMs,
                        requestBody = entry.requestBody,
                        responseSummary = entry.responseSummary,
                        promptTokens = entry.promptTokens
                    )
                }
                requestLog = serverLogs
                val serverMap = serverLogs.associateBy { "${it.timestamp}_${it.method}_${it.path}" }
                val merged = mutableListOf<com.rainyembedding.app.ui.component.LogEntry>()
                for (cached in cachedLog) {
                    val key = "${cached.timestamp}_${cached.method}_${cached.path}"
                    val updated = serverMap[key]?.let { server ->
                        if (server.elapsedMs > cached.elapsedMs || server.responseSummary.isNotEmpty()) {
                            cached.copy(
                                elapsedMs = server.elapsedMs,
                                requestBody = server.requestBody,
                                responseSummary = server.responseSummary,
                                promptTokens = server.promptTokens
                            )
                        } else cached
                    } ?: cached
                    merged.add(updated)
                }
                val existingKeys = merged.map { "${it.timestamp}_${it.method}_${it.path}" }.toSet()
                for (s in serverLogs) {
                    if ("${s.timestamp}_${s.method}_${s.path}" !in existingKeys) merged.add(s)
                }
                cachedLog = merged.takeLast(1000)
                scope.launch(Dispatchers.IO) {
                    try {
                        val arr = JSONArray()
                        cachedLog.forEach { entry ->
                            val obj = JSONObject()
                            obj.put("timestamp", entry.timestamp)
                            obj.put("method", entry.method)
                            obj.put("path", entry.path)
                            obj.put("statusCode", entry.statusCode)
                            obj.put("elapsedMs", entry.elapsedMs)
                            obj.put("requestBody", entry.requestBody)
                            obj.put("responseSummary", entry.responseSummary)
                            obj.put("promptTokens", entry.promptTokens)
                            arr.put(obj)
                        }
                        logFile.writeText(arr.toString())
                    } catch (_: Exception) {}
                }
                statsSummary = StatsRepository.instance?.getSummary()
                    ?: StatsRepository.StatsSummary()
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🔢 雨晴向量主控台", style = MaterialTheme.typography.headlineSmall)

        ServerStatusCard(
            isRunning = isServerRunning,
            port = port,
            uptimeSec = uptimeSec,
            isEngineReady = isEngineReady
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isServerRunning) {
                Button(
                    onClick = {
                        isStarting = true
                        val repo = ModelRepository(RainyEmbeddingApp.instance.modelsDir)
                        val modelFile = repo.findModelFile(selectedModel)
                            ?: run {
                                val fallbackPath = java.io.File("${RainyEmbeddingApp.instance.modelsDir}/${selectedModel}.tflite")
                                if (fallbackPath.exists()) fallbackPath else null
                            }
                            ?: repo.scanDownloadedModels().firstOrNull()?.let {
                                Log.w("Dashboard", "未找到 $selectedModel，临时回退到 ${it.modelInfo.id}")
                                it.file
                            }
                            ?: java.io.File("${RainyEmbeddingApp.instance.modelsDir}/embeddinggemma-300m.tflite")
                        val intent = Intent(context, EmbeddingServerService::class.java).apply {
                            action = EmbeddingServerService.ACTION_START_SERVER
                            putExtra(EmbeddingServerService.EXTRA_MODEL_PATH, modelFile.absolutePath)
                            putExtra(EmbeddingServerService.EXTRA_TOKENIZER_PATH, "${RainyEmbeddingApp.instance.modelsDir}/tokenizer.model")
                            putExtra(EmbeddingServerService.EXTRA_PORT, port)
                            putExtra(EmbeddingServerService.EXTRA_MODEL_ID, selectedModel)
                            putExtra(EmbeddingServerService.EXTRA_DIMENSION, dimension)
                            putExtra(EmbeddingServerService.EXTRA_ACCELERATOR, accelerator)
                        }
                        context.startForegroundService(intent)
                        uptimeSec = 0
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isStarting
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("启动中…")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("启动服务")
                    }
                }
            } else {
                Button(
                    onClick = {
                        val intent = Intent(context, EmbeddingServerService::class.java).apply {
                            action = EmbeddingServerService.ACTION_STOP_SERVER
                        }
                        context.startService(intent)
                        isServerRunning = false
                        isEngineReady = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("停止服务")
                }
            }
        }

        if (!isServerRunning && initError != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("启动失败", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(initError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                    }
                }
            }
        }

        StatsSummaryCard(statsSummary)

        DebugCard(
            isRunning = isServerRunning,
            isEngineReady = isEngineReady,
            port = port,
            modelId = selectedModel,
            accelerator = accelerator,
            debugText = debugText
        )

        LogViewer(
            entries = cachedLog,
            onClearLog = {
                cachedLog = emptyList()
                scope.launch(Dispatchers.IO) {
                    try { logFile.writeText("[]") } catch (_: Exception) {}
                }
                EmbeddingServer.currentInstance?.clearRequestLog()
            }
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text(
                text = "💡 API 配置指南（兼容 OpenAI /v1/embeddings）:\n" +
                       "   Endpoint: http://127.0.0.1:$port\n" +
                       "   API Key: 任意值即可（本地服务不校验）\n" +
                       "   Model: 任意值即可（如 $selectedModel）",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        // HyperOS / MIUI 后台保活引导
        if (isServerRunning) {
            BatteryOptimizationHint()
        }
    }
}

@Composable
private fun StatsSummaryCard(summary: StatsRepository.StatsSummary) {
    var showResetConfirm by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📊 累计统计", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (summary.totalRequests > 0) {
                    IconButton(onClick = { showResetConfirm = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "重置", modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("总请求", summary.totalRequests.toString())
                StatItem("Prompt Tokens", formatNumber(summary.totalPromptTokens))
                StatItem("Comp Tokens", formatNumber(summary.totalCompletionTokens))
                StatItem("平均耗时", "${summary.avgDurationMs}ms")
            }
        }
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置统计") },
            text = { Text("确定要清空所有历史统计数据吗？\n此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { StatsRepository.instance?.clear(); showResetConfirm = false }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatNumber(n: Long): String = if (n >= 1000) "${n / 1000}K" else n.toString()

@Composable
private fun BatteryOptimizationHint() {
    val context = LocalContext.current
    val isXiaomi = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
    val isHyperOS = isXiaomi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 检查是否已被加入电池白名单
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else true

    // 只在 HyperOS/MIUI 且未加入白名单时显示
    if (!isHyperOS || isIgnoringBattery) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    "后台保活提示",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "HyperOS 可能会在 App 进入后台后冻结服务，导致无法响应请求。\n" +
                "建议关闭电池优化以保证后台可用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("关闭电池优化")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "或在 设置→应用→RainyEmbedding→省电策略 手动设为「无限制」",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}