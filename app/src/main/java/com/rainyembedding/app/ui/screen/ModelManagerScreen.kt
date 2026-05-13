package com.rainyembedding.app.ui.screen

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rainyembedding.app.RainyEmbeddingApp
import com.rainyembedding.app.data.AppPreferences
import com.rainyembedding.app.model.DownloadedModel
import com.rainyembedding.app.model.ModelDownloader
import com.rainyembedding.app.model.ModelInfo
import com.rainyembedding.app.model.ModelRepository
import com.rainyembedding.app.model.ModelValidator
import com.rainyembedding.app.ui.component.ModelDownloadCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModelManagerScreen(isVisible: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }

    val modelsDir = RainyEmbeddingApp.instance.modelsDir
    val repo = remember { ModelRepository(modelsDir) }
    val downloader = remember { ModelDownloader(context) }

    var models by remember { mutableStateOf(repo.getAllModels()) }
    var selectedModelId by remember { mutableStateOf("embeddinggemma-300m") }
    var hfToken by remember { mutableStateOf("") }

    // 从 DataStore 加载
    LaunchedEffect(Unit) {
        prefs.selectedModel.collect { selectedModelId = it }
    }
    LaunchedEffect(Unit) {
        prefs.hfToken.collect { hfToken = it }
    }
    var downloadProgresses by remember { mutableStateOf(mapOf<String, Int>()) }
    var downloadingIds by remember { mutableStateOf(setOf<String>()) }
    var downloadIdsMap by remember { mutableStateOf(mapOf<String, Long>()) }

    // 存储空间
    var storageWarning by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    // ── 文件选择器：导入 .tflite 模型 ──
    val importModelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val fileName = try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) it.getString(idx) else "imported.tflite"
                    } else "imported.tflite"
                } ?: "imported.tflite"
            } catch (_: Exception) { "imported.tflite" }

            if (!fileName.endsWith(".tflite")) {
                importMessage = "❌ 只支持 .tflite 格式的模型文件喵~"
                return@launch
            }

            importMessage = "⏳ 正在导入 $fileName …"
            val result = withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext null
                repo.importModelFromStream(inputStream, fileName)
            }
            importMessage = if (result != null) {
                models = repo.getAllModels()
                // 导入成功后自动选中
                val downloaded = repo.scanDownloadedModels()
                    .find { it.file.name == fileName }
                if (downloaded != null) {
                    selectedModelId = downloaded.modelInfo.id
                    scope.launch { prefs.setSelectedModel(downloaded.modelInfo.id) }
                }
                "✅ 导入成功！${result.name}"
            } else {
                "❌ 导入失败，请检查文件是否完整喵~"
            }
        }
    }

    // ── 文件选择器：导入 tokenizer.model ──
    val importTokenizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val fileName = try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) it.getString(idx) else "tokenizer.model"
                    } else "tokenizer.model"
                } ?: "tokenizer.model"
            } catch (_: Exception) { "tokenizer.model" }

            importMessage = "⏳ 正在导入 $fileName …"
            val result = withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext null
                repo.importModelFromStream(inputStream, fileName)
            }
            importMessage = if (result != null) {
                "✅ 分词器导入成功！${result.name}"
            } else {
                "❌ 分词器导入失败喵~"
            }
        }
    }

    // ── 文件创建器：导出 ──
    var exportModelId by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val modelId = exportModelId ?: return@rememberLauncherForActivityResult
        exportModelId = null
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            importMessage = "⏳ 正在导出…"
            val ok = withContext(Dispatchers.IO) {
                try {
                    val sourceFile = repo.getModelFile(modelId)
                    if (!sourceFile.exists()) return@withContext false
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            importMessage = if (ok) "✅ 导出成功！" else "❌ 导出失败喵~"
        }
    }

    // ── 分词器下载状态 ──
    val tokenizerFile = remember { java.io.File(modelsDir, "tokenizer.model") }
    var tokenizerExists by remember { mutableStateOf(tokenizerFile.exists()) }
    var tokenizerDownloading by remember { mutableStateOf(false) }
    var tokenizerDownloadId by remember { mutableStateOf<Long>(-1L) }
    var tokenizerProgress by remember { mutableIntStateOf(0) }

    // 修复：仅在 Tab 可见或有下载任务时轮询进度
    LaunchedEffect(isVisible, downloadingIds.size) {
        if (!isVisible && downloadingIds.isEmpty()) return@LaunchedEffect
        while (true) {
            downloadingIds.forEach { modelId ->
                val downloadId = downloadIdsMap[modelId] ?: return@forEach
                val progress = downloader.queryProgress(downloadId)
                if (progress >= 100) {
                    // 下载完成——规范化文件名（处理 Content-Disposition 覆盖问题）
                    val expectedFile = repo.getModelFile(modelId)
                    val actualFile = downloader.normalizeDownloadedFile(downloadId, expectedFile)
                    val validatedFile = actualFile?.takeIf { it.exists() } ?: expectedFile.takeIf { it.exists() }
                    if (validatedFile == null) {
                        Log.w("ModelManager", "下载完成后文件不存在: $modelId")
                        downloadingIds = downloadingIds - modelId
                        storageWarning = "❌ 下载完成但找不到文件喵~"
                        return@forEach
                    }

                    // 校验
                    val modelInfo = models.find { it.modelInfo.id == modelId }?.modelInfo
                    val validation = if (modelInfo != null) {
                        ModelValidator.validate(validatedFile, modelInfo.sha256)
                    } else null

                    downloadingIds = downloadingIds - modelId
                    // 刷新模型列表（此时新文件名应该能被 scanDownloadedModels 匹配）
                    models = repo.getAllModels()
                    downloadProgresses = downloadProgresses - modelId
                    // 清除警告
                    storageWarning = null

                    if (validation is com.rainyembedding.app.model.ValidationResult.Mismatch) {
                        storageWarning = "⚠️ 哎呀喵！校验失败啦，麻烦主人重新下载一下嘛~"
                    } else {
                        // ✅ 下载校验成功后，自动切换到新模型
                        selectedModelId = modelId
                        scope.launch { prefs.setSelectedModel(modelId) }
                        importMessage = "✅ ${modelInfo?.name ?: modelId} 已下载并自动选中喵~"
                    }
                } else if (progress < 0) {
                    downloadingIds = downloadingIds - modelId
                    storageWarning = "❌ 下载失败惹，是不是网线被雨晴踩断了...请重试喵！"
                } else {
                    downloadProgresses = downloadProgresses + (modelId to progress)
                }
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    // 分词器下载进度轮询
    LaunchedEffect(tokenizerDownloading) {
        if (!tokenizerDownloading) return@LaunchedEffect
        while (tokenizerDownloading) {
            val progress = downloader.queryProgress(tokenizerDownloadId)
            when {
                progress >= 100 -> {
                    val actualFile = downloader.normalizeDownloadedFile(tokenizerDownloadId, tokenizerFile)
                    tokenizerExists = actualFile?.exists() == true || tokenizerFile.exists()
                    tokenizerDownloading = false
                    tokenizerProgress = 100
                    importMessage = if (tokenizerExists) "✅ 分词器已下载！" else "❌ 分词器下载完成但文件未找到喵~"
                }
                progress < 0 -> {
                    tokenizerDownloading = false
                    importMessage = "❌ 分词器下载失败，请检查网络喵~"
                }
                else -> tokenizerProgress = progress
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("📦 雨晴的模型小仓库", style = MaterialTheme.typography.headlineSmall)

        // 操作栏：重新扫描 + 导入按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                models = repo.getAllModels()
                importMessage = "🔍 已重新扫描模型列表喵~"
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "重新扫描")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    importModelLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                }) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入模型")
                }
                OutlinedButton(onClick = {
                    importTokenizerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                }) {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入分词器")
                }
            }
        }

        if (storageWarning != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = storageWarning!!,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 导入/导出状态消息
        if (importMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (importMessage!!.startsWith("✅"))
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else if (importMessage!!.startsWith("❌"))
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = importMessage!!,
                    modifier = Modifier.padding(12.dp),
                    color = if (importMessage!!.startsWith("✅"))
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else if (importMessage!!.startsWith("❌"))
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // ── 分词器状态卡片 ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (tokenizerExists)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (tokenizerExists) "🔤 分词器已就绪" else "🔤 SentencePiece 分词器",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            if (tokenizerExists) "tokenizer.model (${tokenizerFile.length() / 1024}KB)"
                            else if (tokenizerDownloading) "下载中 ${tokenizerProgress}%…"
                            else "sentencepiece.model · ~4.7MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (tokenizerDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }

                if (!tokenizerExists && !tokenizerDownloading) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                val minBytes = 10_000_000L
                                if (downloader.checkStorageSpace(minBytes) < 0) {
                                    importMessage = "❌ 存储空间不足喵~"
                                    return@TextButton
                                }
                                tokenizerDownloading = true
                                tokenizerProgress = 0
                                val id = downloader.startDownload(
                                    ModelInfo.EmbeddingGemmaTokenizer, tokenizerFile,
                                    ModelInfo.EmbeddingGemmaTokenizer.mirrorUrl,
                                    hfToken.takeIf { it.isNotBlank() }
                                )
                                tokenizerDownloadId = id
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("国内镜像", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = {
                                val minBytes = 10_000_000L
                                if (downloader.checkStorageSpace(minBytes) < 0) {
                                    importMessage = "❌ 存储空间不足喵~"
                                    return@TextButton
                                }
                                tokenizerDownloading = true
                                tokenizerProgress = 0
                                val id = downloader.startDownload(
                                    ModelInfo.EmbeddingGemmaTokenizer, tokenizerFile,
                                    ModelInfo.EmbeddingGemmaTokenizer.url,
                                    hfToken.takeIf { it.isNotBlank() }
                                )
                                tokenizerDownloadId = id
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("海外原链", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (tokenizerDownloading) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { tokenizerProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            downloader.removeDownload(tokenizerDownloadId)
                            tokenizerDownloading = false
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("取消", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // ── 模型列表 ──
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(models) { model ->
                ModelDownloadCard(
                    model = model,
                    downloadProgress = downloadProgresses[model.modelInfo.id] ?: 0,
                    isDownloading = model.modelInfo.id in downloadingIds,
                    isSelected = model.modelInfo.id == selectedModelId && model.isDownloaded,
                    onDownload = {
                        val minBytes = model.modelInfo.sizeBytes + 500_000_000L
                        val available = downloader.checkStorageSpace(minBytes)
                        if (available < 0) {
                            storageWarning = "⚠️ 手机肚肚装不下啦！还需要 ${model.modelInfo.sizeMb} + 500MB 的空间哦喵~"
                            return@ModelDownloadCard
                        }
                        storageWarning = null
                        val file = repo.getModelFile(model.modelInfo.id)
                        val downloadId = downloader.startDownload(model.modelInfo, file, model.modelInfo.url, hfToken.takeIf { it.isNotBlank() })
                        downloadIdsMap = downloadIdsMap + (model.modelInfo.id to downloadId)
                        downloadingIds = downloadingIds + model.modelInfo.id
                    },
                    onDownloadMirror = {
                        val minBytes = model.modelInfo.sizeBytes + 500_000_000L
                        val available = downloader.checkStorageSpace(minBytes)
                        if (available < 0) {
                            storageWarning = "⚠️ 手机肚肚装不下啦！还需要 ${model.modelInfo.sizeMb} + 500MB 的空间哦喵~"
                            return@ModelDownloadCard
                        }
                        storageWarning = null
                        val file = repo.getModelFile(model.modelInfo.id)
                        val downloadId = downloader.startDownload(model.modelInfo, file, model.modelInfo.mirrorUrl, hfToken.takeIf { it.isNotBlank() })
                        downloadIdsMap = downloadIdsMap + (model.modelInfo.id to downloadId)
                        downloadingIds = downloadingIds + model.modelInfo.id
                    },
                    onCancel = {
                        val downloadId = downloadIdsMap[model.modelInfo.id]
                        if (downloadId != null) {
                            downloader.removeDownload(downloadId)
                            // 清理可能的不完整文件
                            val partialFile = repo.getModelFile(model.modelInfo.id)
                            if (partialFile.exists() && partialFile.length() < model.modelInfo.sizeBytes) {
                                partialFile.delete()
                            }
                        }
                        downloadingIds = downloadingIds - model.modelInfo.id
                    },
                    onDelete = {
                        repo.deleteModel(model.modelInfo.id)
                        if (selectedModelId == model.modelInfo.id) {
                            // 如果删除的是当前选中的模型，回退到默认模型
                            selectedModelId = "embeddinggemma-300m"
                            scope.launch { prefs.setSelectedModel("embeddinggemma-300m") }
                        }
                        models = repo.getAllModels()
                    },
                    onSelect = {
                        selectedModelId = model.modelInfo.id
                        scope.launch { prefs.setSelectedModel(model.modelInfo.id) }
                    },
                    onExport = {
                        exportModelId = model.modelInfo.id
                        exportLauncher.launch("${model.modelInfo.id}.tflite")
                    }
                )
            }
        }
    }
}