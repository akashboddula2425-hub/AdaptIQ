package com.adaptiq.tutor.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * MNN inference configuration matching the native engine's expected parameters.
 * Serialized to JSON and written to the model directory before loading.
 */
@Serializable
data class InferenceConfig(
    val modelDir: String = "",
    val useMmap: Boolean = true,
    val reuseKv: Boolean = true,
    val attentionMode: Int = 10,    // INT8 KV cache
    val precision: String = "low",
    val threadNum: Int = 6,
    val maxNewTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repetitionPenalty: Float = 1.1f,
) {
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun fromJson(jsonString: String): InferenceConfig = json.decodeFromString(jsonString)
    }
}
