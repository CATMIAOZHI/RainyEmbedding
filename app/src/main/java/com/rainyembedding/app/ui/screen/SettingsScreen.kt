package com.rainyembedding.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rainyembedding.app.data.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }
    val scrollState = rememberScrollState()

    var port by remember { mutableIntStateOf(8081) }
    var accelerator by remember { mutableStateOf("cpu") }
    var dimension by remember { mutableIntStateOf(128) }
    var idleTimeout by remember { mutableIntStateOf(5) }
    var hfToken by remember { mutableStateOf("") }

    var portText by remember { mutableStateOf("8081") }
    var idleTimeoutText by remember { mutableStateOf("5") }
    var hfTokenText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        portText = prefs.serverPort.first().toString(); port = portText.toIntOrNull() ?: 8081
        accelerator = prefs.accelerator.first()
        dimension = prefs.outputDimension.first()
        idleTimeout = prefs.idleTimeoutMin.first(); idleTimeoutText = idleTimeout.toString()
        hfToken = prefs.hfToken.first(); hfTokenText = hfToken
    }

    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { focusManager.clearFocus() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("⚙️ 雨晴的设置", style = MaterialTheme.typography.headlineSmall)

            // 服务器
            SettingsSection("🌐 服务器") {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { v ->
                        portText = v
                        v.toIntOrNull()?.coerceIn(1024, 65535)?.let {
                            port = it; scope.launch { prefs.setServerPort(it) }
                        }
                    },
                    label = { Text("端口号") },
                    supportingText = { Text("范围: 1024–65535，默认 8081") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 向量化设置
            SettingsSection("🧠 向量化设置") {
                // 维度选择
                Text("输出维度", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(128, 256, 512, 768).forEach { dim ->
                        FilterChip(
                            selected = dimension == dim,
                            onClick = {
                                dimension = dim
                                scope.launch { prefs.setOutputDimension(dim) }
                            },
                            label = { Text("${dim}d") }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "💡 低维度更省存储（128d 推荐），高维度精度更佳（768d 全精度）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Spacer(Modifier.height(16.dp))

                // 加速后端
                Text("加速后端", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("cpu" to "CPU", "gpu" to "GPU", "npu" to "NPU").forEach { (key, label) ->
                        FilterChip(
                            selected = accelerator == key,
                            onClick = {
                                accelerator = key
                                scope.launch { prefs.setAccelerator(key) }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "💡 CPU 兼容性最好；GPU 通过 OpenCL 加速；NPU 需要 Qualcomm Hexagon (SM8450+) 或 MediaTek 设备",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // 高级
            SettingsSection("⏱️ 高级") {
                OutlinedTextField(
                    value = idleTimeoutText,
                    onValueChange = { v ->
                        idleTimeoutText = v
                        v.toIntOrNull()?.coerceIn(1, 60)?.let {
                            idleTimeout = it; scope.launch { prefs.setIdleTimeoutMin(it) }
                        }
                    },
                    label = { Text("空闲超时（分钟）") },
                    supportingText = { Text("1–60 分钟，超时后自动释放引擎") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // HuggingFace Token
            SettingsSection("🔑 HuggingFace") {
                OutlinedTextField(
                    value = hfTokenText,
                    onValueChange = { v ->
                        hfTokenText = v
                        hfToken = v
                        scope.launch { prefs.setHfToken(v) }
                    },
                    label = { Text("HF API Token") },
                    supportingText = { Text("在 hf.co/settings/tokens 生成，需先 Accept Gemma License") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "💡 下载 EmbeddingGemma 需要授权。登录 HuggingFace → Accept Gemma License → 生成 Token → 粘贴至此",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // 关于
            val uriHandler = LocalUriHandler.current
            SettingsSection("ℹ️ 关于 RainyEmbedding") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AboutRow("版本", "1.0.0")
                    AboutRow("推理引擎", "LiteRT CompiledModel (Google AI Edge)")
                    AboutRow("模型", "EmbeddingGemma 300M")
                    AboutRow("HTTP 服务", "NanoHTTPd 2.3.1")
                    AboutRow("UI 框架", "Jetpack Compose + Material 3")
                    AboutRow("维度", "Matryoshka 768→128/256/512")
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            "GitHub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(80.dp)
                        )
                        Text(
                            "CATMIAOZHI/RainyEmbedding",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                uriHandler.openUri("https://github.com/CATMIAOZHI/RainyEmbedding")
                            }
                        )
                    }
                    AboutRow("🐱", "Made with 🔢 by Rainy & 水晴")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}