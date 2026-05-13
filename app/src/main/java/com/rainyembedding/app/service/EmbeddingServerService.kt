package com.rainyembedding.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.Environment
import com.rainyembedding.app.data.AppPreferences
import com.rainyembedding.app.engine.EmbeddingEngine
import com.rainyembedding.app.engine.EmbeddingInitException
import com.rainyembedding.app.engine.EmbeddingTokenizer
import com.rainyembedding.app.server.EmbeddingServer
import kotlinx.coroutines.flow.first

/**
 * Embedding 推理服务器前台服务
 *
 * 在后台线程初始化 EmbeddingEngine + EmbeddingTokenizer，
 * 启动 EmbeddingServer 监听 127.0.0.1，
 * 通过前台通知 + WakeLock 防止被系统清理。
 */
class EmbeddingServerService : Service() {

    companion object {
        private const val TAG = "EmbServerService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "rainyembedding_server"
        private const val CHANNEL_NAME = "RainyEmbedding 服务器"

        const val ACTION_START_SERVER = "com.rainyembedding.app.START_SERVER"
        const val ACTION_STOP_SERVER = "com.rainyembedding.app.STOP_SERVER"
        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_TOKENIZER_PATH = "tokenizer_path"
        const val EXTRA_PORT = "port"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_DIMENSION = "dimension"
        const val EXTRA_ACCELERATOR = "accelerator"

        @Volatile
        var lastInitError: String? = null
            private set
    }

    private var engine: EmbeddingEngine? = null
    private var server: EmbeddingServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null

    @Volatile private var initThread: Thread? = null
    @Volatile private var isInitializing: Boolean = false

    var isEngineReady: Boolean = false
        private set
    var serverPort: Int = 8081
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "服务创建")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> {
                val modelPath = intent.getStringExtra(EXTRA_MODEL_PATH)
                    ?: return START_STICKY
                val tokenizerPath = intent.getStringExtra(EXTRA_TOKENIZER_PATH)
                    ?: "${getExternalFilesDir(null)?.path ?: filesDir.path}/models/tokenizer.model"
                val port = intent.getIntExtra(EXTRA_PORT, 8081)
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: "embeddinggemma-300m"
                val dimension = intent.getIntExtra(EXTRA_DIMENSION, 128)
                val acceleratorStr = intent.getStringExtra(EXTRA_ACCELERATOR) ?: "cpu"

                startForegroundNotification()
                initializeEngine(modelPath, tokenizerPath, port, modelId, dimension, acceleratorStr)
            }
            ACTION_STOP_SERVER -> {
                stopAll()
                stopSelf()
            }
            null -> {
                Log.w(TAG, "服务重建，尝试恢复；若模型路径未知则等待显式启动")
                // START_STICKY 下系统重建服务时 intent 可能为 null，不自杀
                lastInitError = "服务被系统重建（OOM/冻结恢复），请手动启动"
                // 不清除状态；等待用户通过 UI 显式启动
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
        Log.i(TAG, "服务销毁")
    }

    // ── 引擎初始化 ──────────────────────────────────────────

    private fun initializeEngine(
        modelPath: String,
        tokenizerPath: String,
        port: Int,
        modelId: String,
        dimension: Int,
        acceleratorStr: String
    ) {
        if (isInitializing) {
            Log.w(TAG, "引擎已在初始化中，忽略重复启动")
            return
        }
        isInitializing = true
        lastInitError = null

        stopAll()
        isInitializing = true

        val thread = Thread {
            var newEngine: EmbeddingEngine? = null
            try {
                updateNotification("🔢 正在加载 Embedding 模型…")

                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "RainyEmbedding:Inference"
                ).apply {
                    acquire(10 * 60 * 1000L)
                }

                // WiFi Lock：防止屏幕关闭后 WiFi 进入省电模式导致 socket accept 延迟
                try {
                    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    wifiLock = wifiManager.createWifiLock(
                        android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "RainyEmbedding:Server"
                    ).apply { acquire() }
                    Log.i(TAG, "✅ WiFi Lock 已获取")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ WiFi Lock 获取失败（设备可能无 WiFi）: ${e.message}")
                }

                val accelerator = when (acceleratorStr.lowercase()) {
                    "gpu" -> Accelerator.GPU
                    "npu" -> Accelerator.NPU
                    else -> Accelerator.CPU
                }
                Log.i(TAG, "加速后端: $acceleratorStr → $accelerator")

                if (Thread.currentThread().isInterrupted) return@Thread

                // 加载 Tokenizer
                EmbeddingTokenizer.load(tokenizerPath)
                Log.i(TAG, "Tokenizer: ${if (EmbeddingTokenizer.isLoaded()) "✅ 已加载" else "⚠️ 未加载（使用 fallback）"}")

                // 初始化 Engine — NPU 通过 jniLibs 中的 QNN .so 文件自动发现
                val npuEnv: Environment? = if (accelerator == Accelerator.NPU) {
                    try {
                        val env = Environment.create(BuiltinNpuAcceleratorProvider(this@EmbeddingServerService))
                        val available = env.getAvailableAccelerators()
                        if (Accelerator.NPU in available) {
                            Log.i(TAG, "✅ NPU 加速器已注册 (SM8750/Hexagon v79): $available")
                            env
                        } else {
                            Log.e(TAG, "❌ NPU 不在可用列表: $available — QNN dispatch 库可能未正确打包")
                            lastInitError = "NPU 加速器未注册 (可用: $available)。请确认 jniLibs 中包含 Qualcomm QNN .so 文件"
                            env.close()
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ NPU Environment 创建失败: ${e.message}", e)
                        lastInitError = "NPU 初始化失败: ${e.message}"
                        null
                    }
                } else {
                    null
                }
                if (accelerator == Accelerator.NPU && npuEnv == null) {
                    throw EmbeddingInitException(lastInitError ?: "NPU 不可用")
                }
                newEngine = EmbeddingEngine(modelPath, outputDimension = dimension, env = npuEnv)
                try {
                    kotlinx.coroutines.runBlocking {
                        newEngine!!.initialize(accelerator = accelerator)
                    }
                } catch (e: Exception) {
                    if (accelerator != Accelerator.CPU) {
                        Log.w(TAG, "⚠️ ${acceleratorStr.uppercase()} 初始化失败，降级到 CPU: ${e.message}")
                        kotlinx.coroutines.runBlocking {
                            newEngine!!.initialize(accelerator = Accelerator.CPU)
                        }
                    } else {
                        throw e
                    }
                }
                engine = newEngine
                Log.i(TAG, "✅ Embedding 引擎初始化完成")

                if (Thread.currentThread().isInterrupted) return@Thread

                // 启动 HTTP 服务器
                val newServer = EmbeddingServer(port, newEngine, modelId)
                newServer.start()
                server = newServer

                serverPort = port
                isEngineReady = true

                updateNotification("🔢 RainyEmbedding 运行中 | 端口: $port | ${dimension}d")
                Log.i(TAG, "✅ 服务器已启动: 127.0.0.1:$port")

                // 启动心跳防止 HyperOS 进程冻结
                startHeartbeat()

            } catch (e: InterruptedException) {
                Log.i(TAG, "初始化线程被中断")
                newEngine?.close()
            } catch (e: Exception) {
                Log.e(TAG, "初始化失败: ${e.message}", e)
                isEngineReady = false
                lastInitError = e.message ?: "未知错误"
                updateNotification("⚠️ 加载失败: ${lastInitError}")
                newEngine?.close()
                stopAll()
                stopSelf()
            } finally {
                isInitializing = false
            }
        }
        initThread = thread
        thread.start()
    }

    private fun stopAll() {
        stopHeartbeat()
        val oldThread = initThread
        if (oldThread != null && oldThread.isAlive) {
            oldThread.interrupt()
            try { oldThread.join(3000L) } catch (_: InterruptedException) {}
        }
        try {
            initThread = null
            server?.stop()
            engine?.close()
            EmbeddingTokenizer.close()
        } catch (e: Exception) {
            Log.w(TAG, "停止时异常: ${e.message}")
        } finally {
            server = null
            engine = null
            wakeLock?.let { try { it.release() } catch (_: Exception) {} }; wakeLock = null
            wifiLock?.let { try { it.release() } catch (_: Exception) {} }; wifiLock = null
            isEngineReady = false
        }
    }

    // ── 通知管理 ──────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "RainyEmbedding 向量化服务器运行状态"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification("🔢 RainyEmbedding 启动中…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                // 保持进程活跃，防止 HyperOS cgroup 冻结
                if (isEngineReady) {
                    Log.v(TAG, "💓 heartbeat")
                }
                heartbeatHandler.postDelayed(this, 30_000L)
            }
        }
        heartbeatHandler.post(heartbeatRunnable!!)
        Log.i(TAG, "💓 心跳已启动 (每30秒)")
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = Intent(this, com.rainyembedding.app.MainActivity::class.java).let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RainyEmbedding")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RainyEmbedding")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }
}