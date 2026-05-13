package com.rainyembedding.app.engine

import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import java.io.File

class EmbeddingEngine(
    private val modelPath: String,
    private val outputDimension: Int = 128,
    private val env: Environment? = null  // NPU 加速需要带 BuiltinNpuAcceleratorProvider 的 Environment
) {
    companion object {
        private const val TAG = "EmbeddingEngine"
        const val EMBEDDINGGEMMA_NATIVE_DIM = 768
        const val MAX_SEQ_LENGTH = 512
    }

    private var compiledModel: CompiledModel? = null
    private val initLock = Any()
    @Volatile private var currentAccelerator: String = "none"
    // 复用 input/output buffer，避免每次推理重新创建
    // 关键：必须预创建 output buffers 并传给 run()，否则 LiteRT C++ 层会抛 litert_compiled_model 错误
    private var cachedInputs: List<TensorBuffer>? = null
    private var cachedOutputs: List<TensorBuffer>? = null
    // 模型期望的输入 token 数量（EmbeddingGemma 固定 seq_len=512）
    private val modelInputSize: Int = MAX_SEQ_LENGTH

    @Volatile var isInitialized: Boolean = false; private set

    suspend fun initialize(accelerator: Accelerator = Accelerator.CPU): Unit = withContext(Dispatchers.IO) {
        synchronized(initLock) {
            if (isInitialized) { Log.w(TAG, "引擎已初始化，跳过"); return@withContext }
            val modelFile = java.io.File(modelPath)
            if (!modelFile.exists()) throw EmbeddingInitException("模型不存在: $modelPath")
            try {
                compiledModel = CompiledModel.create(modelPath, CompiledModel.Options(accelerator), env)
                // 预创建并缓存 input + output buffers（必须配对传给 run()）
                cachedInputs = compiledModel!!.createInputBuffers()
                cachedOutputs = compiledModel!!.createOutputBuffers()
                isInitialized = true
                currentAccelerator = accelerator.toString().removePrefix("Accelerator.")
                Log.i(TAG, "✅ 初始化成功 (dim=$outputDimension, inputSize=$modelInputSize, accel=$currentAccelerator, ${modelFile.length() / 1024 / 1024}MB)")
            } catch (e: Exception) { throw EmbeddingInitException("CompiledModel 失败: ${e.message}", e) }
        }
    }

    fun embed(text: String): FloatArray {
        val model = compiledModel ?: throw EmbeddingInitException("引擎未初始化")
        val tokenIds = EmbeddingTokenizer.encode(text, MAX_SEQ_LENGTH)
        if (tokenIds.isEmpty()) throw EmbeddingInferenceException("Tokenize 结果为空")

        return try {
            // Zero-padding：模型期望固定长度输入，tokenize 结果可能不足，必须填 0
            val paddedIds = if (tokenIds.size < modelInputSize) {
                IntArray(modelInputSize) { i -> if (i < tokenIds.size) tokenIds[i] else 0 }
            } else {
                tokenIds.copyOf(modelInputSize)
            }
            cachedInputs!![0].writeInt(paddedIds)

            // 关键修复：必须传入预创建的 output buffers，否则 LiteRT C++ 层出错
            model.run(cachedInputs!!, cachedOutputs!!)
            val fullVector = cachedOutputs!![0].readFloat()
            normalize(fullVector.copyOf(outputDimension))
        } catch (e: EmbeddingInitException) { throw e }
        catch (e: Exception) { throw EmbeddingInferenceException("推理失败: ${e.message}", e) }
    }

    fun embedBatch(texts: List<String>): List<FloatArray> {
        require(isInitialized) { "引擎未初始化" }
        return texts.map { embed(it) }
    }

    fun close() {
        synchronized(initLock) {
            try { compiledModel?.close() } catch (e: Exception) { Log.w(TAG, "关闭异常: ${e.message}") }
            finally { compiledModel = null; cachedInputs = null; cachedOutputs = null; isInitialized = false; Log.i(TAG, "🧹 已关闭") }
        }
    }

    fun getDimension(): Int = outputDimension
    fun getAccelerator(): String = currentAccelerator

    private fun normalize(vector: FloatArray): FloatArray {
        val squaredSum = vector.fold(0.0) { sum, v -> sum + v * v }
        val norm = sqrt(squaredSum)
        if (norm > 0) for (i in vector.indices) vector[i] = (vector[i] / norm).toFloat()
        return vector
    }
}

class EmbeddingInitException(message: String, cause: Throwable? = null) : Exception(message, cause)
class EmbeddingInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)