package com.rainyembedding.app.server

import android.util.Log
import com.rainyembedding.app.engine.EmbeddingEngine
import com.rainyembedding.app.engine.EmbeddingTokenizer
import com.rainyembedding.app.engine.TokenEstimator
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.*
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class EmbeddingServer(
    private val port: Int,
    private val engine: EmbeddingEngine,
    private val modelId: String = "embedding-gemma"
) : NanoHTTPD("127.0.0.1", port) {

    companion object {
        private const val TAG = "EmbeddingServer"
        private const val SOCKET_READ_TIMEOUT = 5000
        @Volatile var currentInstance: EmbeddingServer? = null
    }

    data class LogEntry(
        val timestamp: Long, val method: String, val path: String,
        val statusCode: Int, val elapsedMs: Long,
        val requestBody: String = "", val responseSummary: String = "",
        val promptTokens: Int = 0
    )

    class ServerStats {
        @Volatile var totalRequests = 0
        @Volatile var totalTokens = 0L
        @Volatile var startTime = System.currentTimeMillis()
        @Synchronized fun addRequest(tokens: Int) { totalRequests++; totalTokens += tokens }
    }

    private var isRunning = false
    private val stats = ServerStats()
    private val requestLog = CopyOnWriteArrayList<LogEntry>()
    private val nextLogIndex = AtomicInteger(0)
    private val inferenceLock = java.util.concurrent.locks.ReentrantLock()

    @Volatile var lastErrorDetail: String? = null; private set
    @Volatile private var pendingRequestBody: String? = null
    @Volatile private var pendingResponseSummary: String? = null
    @Volatile private var pendingPromptTokens: Int = 0

    val serverPort: Int get() = port
    val isServerRunning: Boolean get() = isRunning

    override fun start() {
        try { start(SOCKET_READ_TIMEOUT, false); isRunning = true; currentInstance = this; stats.startTime = System.currentTimeMillis(); Log.i(TAG, "✅ 已启动: 127.0.0.1:$port") }
        catch (e: Exception) { isRunning = false; currentInstance = null; Log.e(TAG, "❌ 启动失败: ${e.message}", e); throw e }
    }
    override fun stop() { isRunning = false; currentInstance = null; super.stop(); Log.i(TAG, "🛑 已停止") }

    override fun serve(session: IHTTPSession): Response {
        val t0 = System.currentTimeMillis(); val method = session.method; val uri = session.uri
        return try {
            val resp = when {
                method == Method.OPTIONS -> corsPreflight()
                uri == "/health" || uri == "/" -> handleHealthCheck()
                uri == "/v1/embeddings" && method == Method.POST -> handleEmbedding(session)
                else -> handleNotFound()
            }
            val elapsed = System.currentTimeMillis() - t0
            log(method.name, uri, resp.status.requestStatus, elapsed, pendingRequestBody ?: "", pendingResponseSummary ?: "", pendingPromptTokens)
            pendingRequestBody = null; pendingResponseSummary = null; pendingPromptTokens = 0
            if (method != Method.OPTIONS) addCors(resp)
            resp
        } catch (e: Exception) {
            lastErrorDetail = "请求异常: ${e.message}\n${e.stackTraceToString().take(800)}"
            Log.e(TAG, lastErrorDetail, e)
            log(method.name, uri, 500, System.currentTimeMillis() - t0)
            json(Response.Status.INTERNAL_ERROR, """{"error":{"message":"${e.message?.replace("\"","\\\"")}"}}""")
        }
    }

    private fun handleHealthCheck(): Response {
        val j = JSONObject().apply {
            put("status", if (engine.isInitialized) "ok" else "loading"); put("model", modelId)
            put("dimension", engine.getDimension()); put("tokenizer_loaded", EmbeddingTokenizer.isLoaded())
            put("uptime_seconds", (System.currentTimeMillis() - stats.startTime) / 1000)
            put("total_requests", stats.totalRequests); put("total_tokens", stats.totalTokens)
        }
        return json(Response.Status.OK, j.toString())
    }

    private fun handleEmbedding(session: IHTTPSession): Response {
        if (!engine.isInitialized)
            return json(Response.Status.SERVICE_UNAVAILABLE, """{"error":{"message":"Embedding engine not initialized"}}""")
        val body = parseBody(session)
        if (body.isBlank()) return json(Response.Status.BAD_REQUEST, """{"error":{"message":"Empty request body"}}""")
        val req = try { JSONObject(body) } catch (_: Exception) { return json(Response.Status.BAD_REQUEST, """{"error":{"message":"Invalid JSON"}}""") }
        val model = req.optString("model", modelId)

        val inputs: List<String> = try {
            when (val f = req.get("input")) {
                is JSONArray -> (0 until f.length()).map { f.getString(it) }.filter { it.isNotBlank() }
                else -> listOf(f.toString()).filter { it.isNotBlank() }
            }
        } catch (_: Exception) { return json(Response.Status.BAD_REQUEST, """{"error":{"message":"Invalid input field"}}""") }
        if (inputs.isEmpty()) return json(Response.Status.BAD_REQUEST, """{"error":{"message":"input is required"}}""")

        return try {
            val t0 = System.currentTimeMillis()
            inferenceLock.lock()
            val vecs: List<FloatArray> = try { engine.embedBatch(inputs) } finally { inferenceLock.unlock() }
            val elapsed = System.currentTimeMillis() - t0
            val tokens = inputs.sumOf { TokenEstimator.estimatePromptTokens(it) }
            stats.addRequest(tokens)

            val respJson: String
            if (vecs.size == 1) {
                Log.i(TAG, "Embedding: ${vecs[0].size}d, ${elapsed}ms, ${tokens}t")
                val sb = StringBuilder("["); vecs[0].forEachIndexed { i, x -> if (i > 0) sb.append(","); sb.append(x.toString()) }; sb.append("]")
                respJson = """{"object":"list","data":[{"object":"embedding","index":0,"embedding":$sb}],"model":"$model","usage":{"prompt_tokens":$tokens,"total_tokens":$tokens}}"""
                pendingRequestBody = inputs[0].take(200)
                pendingResponseSummary = "向量(${vecs[0].size}d) · ${elapsed}ms · 前3维:[${"%.4f".format(vecs[0].getOrElse(0){0f})},${"%.4f".format(vecs[0].getOrElse(1){0f})},${"%.4f".format(vecs[0].getOrElse(2){0f})}]"
            } else {
                Log.i(TAG, "Batch: ${vecs.size}×${vecs[0].size}d, ${elapsed}ms, ${tokens}t")
                val sb = StringBuilder("""{"object":"list","data":[""")
                vecs.forEachIndexed { idx, v ->
                    if (idx > 0) sb.append(","); sb.append("""{"object":"embedding","index":$idx,"embedding":[""")
                    v.forEachIndexed { i, x -> if (i > 0) sb.append(","); sb.append(x.toString()) }; sb.append("]}")
                }
                respJson = sb.append(""","model":"$model","usage":{"prompt_tokens":$tokens,"total_tokens":$tokens}}""").toString()
                pendingRequestBody = "批量请求(${inputs.size}条)".take(200)
                pendingResponseSummary = "${vecs.size}×${vecs[0].size}d · ${elapsed}ms"
            }
            pendingPromptTokens = tokens
            json(Response.Status.OK, respJson)
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed: ${e.message}", e)
            lastErrorDetail = "Embedding 推理失败: ${e.message}\n${e.stackTraceToString().take(800)}"
            json(Response.Status.INTERNAL_ERROR, """{"error":{"message":"Embedding failed: ${e.message?.replace("\"","\\\"")}"}}""")
        }
    }

    private fun handleNotFound() = json(Response.Status.NOT_FOUND, """{"error":{"message":"Not found"}}""")

    private fun corsPreflight(): Response = newFixedLengthResponse(Response.Status.OK, "text/plain", "").also {
        it.addHeader("Access-Control-Allow-Origin", "*"); it.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        it.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization"); it.addHeader("Access-Control-Max-Age", "86400")
    }
    private fun addCors(r: Response) { r.addHeader("Access-Control-Allow-Origin", "*"); r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS"); r.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization") }

    private fun json(status: Response.Status, body: String): Response {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return newFixedLengthResponse(status, "application/json; charset=utf-8", ByteArrayInputStream(bytes), bytes.size.toLong())
    }

    private fun parseBody(session: IHTTPSession): String = try {
        val cl = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (cl > 0) String(session.inputStream.readNBytes(cl), Charsets.UTF_8)
        else { val b = session.inputStream.readBytes(); if (b.isEmpty()) "" else String(b, Charsets.UTF_8) }
    } catch (e: Exception) { Log.w(TAG, "parseBody failed: ${e.message}"); "" }

    fun getStats() = stats
    fun getRequestLog(): List<LogEntry> = requestLog.toList()
    fun clearRequestLog() { requestLog.clear() }
    fun getDebugInfo(): String = buildString {
        appendLine("=== Embedding 服务器诊断 ===")
        appendLine("运行状态: ${if (isRunning) "✅ 运行中" else "⚫ 已停止"}"); appendLine("端口: $port")
        appendLine("模型ID: $modelId"); appendLine("引擎就绪: ${if (engine.isInitialized) "✅" else "❌"}")
        appendLine("Tokenizer: ${if (EmbeddingTokenizer.isLoaded()) "✅" else "⚠️ 未加载"}")
        appendLine("输出维度: ${engine.getDimension()}"); appendLine("加速后端: ${engine.getAccelerator().uppercase()}")
        appendLine("请求总数: ${stats.totalRequests}")
        lastErrorDetail?.let { appendLine(); appendLine("=== 最近错误 ==="); appendLine(it) }
    }

    private fun log(method: String, path: String, code: Int, elapsed: Long, body: String = "", summary: String = "", tokens: Int = 0): Int {
        requestLog.add(LogEntry(System.currentTimeMillis(), method, path, code, elapsed, body, summary, tokens))
        if (requestLog.size > 1000) requestLog.removeAt(0)
        return nextLogIndex.getAndIncrement()
    }
}

private val Response.Status.requestStatus: Int get() = when (this) {
    Response.Status.OK -> 200; Response.Status.CREATED -> 201; Response.Status.BAD_REQUEST -> 400
    Response.Status.NOT_FOUND -> 404; Response.Status.SERVICE_UNAVAILABLE -> 503; Response.Status.INTERNAL_ERROR -> 500; else -> 200
}