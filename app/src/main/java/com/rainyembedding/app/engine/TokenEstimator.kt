package com.rainyembedding.app.engine

import kotlin.math.roundToInt

/**
 * Token 计数估算器（简化版）
 *
 * 工作模式：启发式估算，基于字符/token 比例。
 * 当 SentencePiece tokenizer 不可用时作为 fallback。
 *
 * 文本估算针对 EmbeddingGemma 校准：
 *   - 中文/日韩文 ≈ 2.0 字符/token
 *   - 英文/ASCII ≈ 3.5 字符/token
 *   - 数字序列 ≈ 2.5 字符/token
 *   - 换行 ≈ 1 token/个
 */
object TokenEstimator {

    // ── 文本估算常量 ──────────────────────────────

    private const val ASCII_CHARS_PER_TOKEN = 3.5
    private const val CJK_CHARS_PER_TOKEN = 2.0
    private const val DIGIT_CHARS_PER_TOKEN = 2.5

    private val CJK_PATTERN = Regex(
        "[\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff\\u2f800-\\u2fa1f" +
        "\\u3000-\\u303f\\uff00-\\uffef\\u2e80-\\u2eff\\u31c0-\\u31ef" +
        "\\u3040-\\u309f\\u30a0-\\u30ff\\uac00-\\ud7af\\u1100-\\u11ff\\u3130-\\u318f]"
    )

    private val NEWLINE_PATTERN = Regex("\\n")
    private val DIGIT_PATTERN = Regex("\\d")

    // ── 估算方法 ──────────────────────────────────

    /**
     * 估算文本的 prompt token 数
     */
    fun estimatePromptTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return estimateTextTokens(text)
    }

    /**
     * 估算补全输出的 token 数
     */
    fun estimateCompletionTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return estimateTextTokens(text)
    }

    /**
     * 简单估算（不区分语言，仅按平均字符/token比例）
     */
    fun estimateSimple(text: String, charsPerToken: Double = 3.0): Int {
        if (text.isEmpty()) return 0
        return (text.length / charsPerToken).roundToInt().coerceAtLeast(1)
    }

    // ── 文本估算核心 ──────────────────────────────

    private fun estimateTextTokens(text: String): Int {
        val totalLen = text.length
        val cjkChars = CJK_PATTERN.findAll(text).count()
        val digitChars = DIGIT_PATTERN.findAll(text).count()
        val newlineCount = NEWLINE_PATTERN.findAll(text).count()
        // 修复：remaining 需排除已单独计数的换行符，避免被 ASCII 分支重复计数
        val remaining = (totalLen - cjkChars - digitChars - newlineCount).coerceAtLeast(0)

        val cjkTokens = cjkChars / CJK_CHARS_PER_TOKEN
        val digitTokens = digitChars / DIGIT_CHARS_PER_TOKEN
        val asciiTokens = remaining / ASCII_CHARS_PER_TOKEN
        val newlineTokens = newlineCount.toDouble()

        return (cjkTokens + digitTokens + asciiTokens + newlineTokens).roundToInt().coerceAtLeast(1)
    }
}