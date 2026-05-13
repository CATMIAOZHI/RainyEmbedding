package com.rainyembedding.app

import android.app.Application
import android.util.Log
import com.rainyembedding.app.data.StatsRepository
import kotlinx.coroutines.*

/**
 * RainyEmbedding Application 类
 * 应用级初始化
 */
class RainyEmbeddingApp : Application() {

    companion object {
        private const val TAG = "RainyEmbedding"
        lateinit var instance: RainyEmbeddingApp
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        StatsRepository.init(this)
        Log.i(TAG, "🐱☁️ RainyEmbedding 应用启动")
    }

    /** 模型存储目录（外部应用专属目录，DownloadManager 可直接写入） */
    val modelsDir: java.io.File
        get() {
            val externalDir = getExternalFilesDir(null)
                ?: filesDir
            return java.io.File(externalDir, "models").also { it.mkdirs() }
        }
}