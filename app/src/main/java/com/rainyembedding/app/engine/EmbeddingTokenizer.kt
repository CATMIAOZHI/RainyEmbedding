package com.rainyembedding.app.engine

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * EmbeddingGemma 的 Pure Kotlin SentencePiece BPE Tokenizer
 *
 * 解析 tokenizer.model 文件（protobuf 格式），实现 BPE 编码。
 * 零原生依赖 — 直接从 SentencePiece protobuf 二进制文件中读取词表并执行编码。
 *
 * 基于 IliyaBrook/InstantVoiceTranslate 的 SentencePieceBpe 改编，
 * 适配 EmbeddingGemma 的 tokenizer.model 格式。
 */
object EmbeddingTokenizer {
    private const val TAG = "EmbeddingTokenizer"

    /** SentencePiece whitespace marker (U+2581 LOWER ONE EIGHTH BLOCK). */
    private const val SPACE_MARKER = '\u2581'
    private const val SPACE_MARKER_STR = "\u2581"

    private data class VocabPiece(
        val piece: String,
        val score: Float,
    )

    private var pieces = emptyList<VocabPiece>()
    private var pieceToId = emptyMap<String, Int>()
    private var unkId = 0

    @Volatile
    private var loaded = false
    private val loadLock = Any()

    /** Tokenizer 是否已成功加载 */
    fun isLoaded(): Boolean = loaded

    /** 词表大小 */
    fun vocabSize(): Int = pieces.size

    /**
     * 加载 tokenizer.model 文件
     *
     * @param modelPath tokenizer.model 的绝对路径
     */
    fun load(modelPath: String) {
        synchronized(loadLock) {
            if (loaded) {
                Log.w(TAG, "Tokenizer 已加载，跳过重复加载")
                return
            }

            val file = File(modelPath)
            if (!file.exists()) {
                Log.e(TAG, "Tokenizer 模型文件不存在: $modelPath")
                return
            }

            try {
                val bytes = file.readBytes()
                pieces = parseModelProto(bytes)
                pieceToId = HashMap<String, Int>(pieces.size * 2).also { map ->
                    pieces.forEachIndexed { index, p -> map[p.piece] = index }
                }
                unkId = pieceToId["<unk>"] ?: 0
                loaded = true
                Log.i(TAG, "Tokenizer 加载完成: vocab=${pieces.size}, size=${file.length() / 1024}KB")
            } catch (e: Exception) {
                Log.e(TAG, "Tokenizer 加载失败: ${e.message}", e)
                pieces = emptyList()
                pieceToId = emptyMap()
                loaded = false
            }
        }
    }

    /**
     * 将文本编码为 token ID 序列
     *
     * @param text 输入文本
     * @param maxLength 最大序列长度（默认 512，与 EmbeddingGemma 一致）
     * @return token ID 数组，超出 maxLength 会被截断
     */
    fun encode(text: String, maxLength: Int = 512): IntArray {
        if (text.isEmpty()) return intArrayOf()

        if (!loaded) {
            Log.w(TAG, "Tokenizer 未加载，使用降级分词")
            return fallbackEncode(text, maxLength)
        }

        return try {
            // SentencePiece normalization: prepend ▁ and replace spaces with ▁
            val normalized = SPACE_MARKER_STR + text.replace(" ", SPACE_MARKER_STR)

            // Start with individual characters
            val symbols = ArrayList<String>(normalized.length)
            for (ch in normalized) {
                symbols.add(ch.toString())
            }

            // BPE: iteratively merge the highest-scoring adjacent pair
            while (symbols.size > 1) {
                var bestScore = Float.NEGATIVE_INFINITY
                var bestIdx = -1

                for (i in 0 until symbols.size - 1) {
                    val merged = symbols[i] + symbols[i + 1]
                    val id = pieceToId[merged]
                    if (id != null) {
                        val score = pieces[id].score
                        if (score > bestScore) {
                            bestScore = score
                            bestIdx = i
                        }
                    }
                }

                if (bestIdx == -1) break
                symbols[bestIdx] = symbols[bestIdx] + symbols[bestIdx + 1]
                symbols.removeAt(bestIdx + 1)
            }

            val ids = IntArray(symbols.size) { i -> pieceToId[symbols[i]] ?: unkId }
            if (ids.size > maxLength) ids.copyOf(maxLength) else ids
        } catch (e: Exception) {
            Log.w(TAG, "BPE 编码失败，使用降级分词: ${e.message}")
            fallbackEncode(text, maxLength)
        }
    }

    /**
     * 降级分词：Unicode 分字 + 正数 hash
     */
    private fun fallbackEncode(text: String, maxLength: Int): IntArray {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch.isWhitespace()) { i++; continue }
            if (isCJK(ch)) {
                tokens.add(ch.toString()); i++
            } else {
                val start = i
                while (i < text.length && !text[i].isWhitespace() && !isCJK(text[i])) i++
                tokens.add(text.substring(start, i))
            }
        }
        return tokens.map { it.hashCode() and 0x7FFFFFFF }.take(maxLength).toIntArray()
    }

    private fun isCJK(ch: Char): Boolean =
        ch in '\u4e00'..'\u9fff' || ch in '\u3400'..'\u4dbf' ||
        ch in '\uf900'..'\ufaff' || ch in '\u3040'..'\u309f' ||
        ch in '\u30a0'..'\u30ff' || ch in '\uac00'..'\ud7af'

    fun close() {
        synchronized(loadLock) {
            pieces = emptyList()
            pieceToId = emptyMap()
            loaded = false
            Log.i(TAG, "Tokenizer 已释放")
        }
    }

    // ── Protobuf 解析 ────────────────────────────────────────

    private fun parseModelProto(data: ByteArray): List<VocabPiece> {
        val result = mutableListOf<VocabPiece>()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        while (buf.hasRemaining()) {
            val tag = readVarint(buf)
            val fieldNumber = (tag shr 3).toInt()
            val wireType = (tag and 0x7).toInt()

            if (fieldNumber == 1 && wireType == 2) {
                val len = readVarint(buf).toInt()
                val pieceData = ByteArray(len)
                buf.get(pieceData)
                result.add(parseSentencePiece(pieceData))
            } else {
                skipField(buf, wireType)
            }
        }
        return result
    }

    private fun parseSentencePiece(data: ByteArray): VocabPiece {
        var piece = ""
        var score = 0f
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        while (buf.hasRemaining()) {
            val tag = readVarint(buf)
            val fieldNumber = (tag shr 3).toInt()
            val wireType = (tag and 0x7).toInt()

            when {
                fieldNumber == 1 && wireType == 2 -> {
                    val len = readVarint(buf).toInt()
                    val strBytes = ByteArray(len)
                    buf.get(strBytes)
                    piece = String(strBytes, Charsets.UTF_8)
                }
                fieldNumber == 2 && wireType == 5 -> {
                    score = buf.float
                }
                else -> skipField(buf, wireType)
            }
        }
        return VocabPiece(piece, score)
    }

    private fun readVarint(buf: ByteBuffer): Long {
        var result = 0L
        var shift = 0
        while (buf.hasRemaining()) {
            val b = buf.get().toLong() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0L) break
            shift += 7
        }
        return result
    }

    private fun skipField(buf: ByteBuffer, wireType: Int) {
        when (wireType) {
            0 -> readVarint(buf)
            1 -> buf.position(buf.position() + 8)
            2 -> { val len = readVarint(buf).toInt(); buf.position(buf.position() + len) }
            5 -> buf.position(buf.position() + 4)
        }
    }
}