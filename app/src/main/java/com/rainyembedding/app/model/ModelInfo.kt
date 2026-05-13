package com.rainyembedding.app.model

/**
 * 模型元数据
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val url: String,
    val mirrorUrl: String = "",
    val sha256: String,
    val format: String = "tflite",
    val description: String = ""
) {
    val sizeGb: String get() = "%.2f GB".format(sizeBytes / (1024.0 * 1024.0 * 1024.0))
    val sizeMb: String get() = "%.0f MB".format(sizeBytes / (1024.0 * 1024.0))

    companion object {
        val EmbeddingGemma300M = ModelInfo(
            id = "embeddinggemma-300m",
            name = "EmbeddingGemma 300M",
            sizeBytes = 179_000_000L, // ~179MB .tflite (seq512 mixed-precision)
            url = "https://huggingface.co/litert-community/embeddinggemma-300m/resolve/main/embeddinggemma-300M_seq512_mixed-precision.tflite",
            mirrorUrl = "https://hf-mirror.com/litert-community/embeddinggemma-300m/resolve/main/embeddinggemma-300M_seq512_mixed-precision.tflite",
            sha256 = "",  // HF LFS 文件不提供稳定 SHA256，可通过 ModelValidator 验证
            description = "Google EmbeddingGemma 300M，Matryoshka 768→128d，179MB，推荐使用（需先在 HF 上 Accept Gemma License）"
        )

        val EmbeddingGemmaTokenizer = ModelInfo(
            id = "embeddinggemma-tokenizer",
            name = "SentencePiece Tokenizer",
            sizeBytes = 4_900_000L, // ~4.68MB
            url = "https://huggingface.co/litert-community/embeddinggemma-300m/resolve/main/sentencepiece.model",
            mirrorUrl = "https://hf-mirror.com/litert-community/embeddinggemma-300m/resolve/main/sentencepiece.model",
            sha256 = "",
            description = "EmbeddingGemma 分词器模型（sentencepiece.model，4.7MB）"
        )

        val EmbeddingGemmaQualcommSM8750 = ModelInfo(
            id = "embeddinggemma-300m-sm8750",
            name = "EmbeddingGemma 300M (骁龙 NPU)",
            sizeBytes = 184_000_000L,
            url = "https://huggingface.co/litert-community/embeddinggemma-300m/resolve/main/embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8750.tflite",
            mirrorUrl = "https://hf-mirror.com/litert-community/embeddinggemma-300m/resolve/main/embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8750.tflite",
            sha256 = "",
            description = "Qualcomm SM8750 (骁龙 8 Elite) NPU 优化版，K80 Pro 推荐"
        )

        /**
         * 预置模型列表
         */
        val PRESET_MODELS = listOf(
            EmbeddingGemma300M,
            EmbeddingGemmaQualcommSM8750
        )
    }
}