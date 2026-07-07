package com.imanhaikal.memo.data.receipt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// --- Request ---

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart> = emptyList(),
    val role: String? = null
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String // base64-encoded image bytes
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String = "application/json",
    val responseSchema: JsonObject,
    val thinkingConfig: ThinkingConfig? = ThinkingConfig("MINIMAL"),
    val temperature: Double? = 0.0
)

@Serializable
data class ThinkingConfig(
    val thinkingLevel: String
)

// --- Response ---

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

// --- Extraction payload & domain types ---

@Serializable
data class ReceiptExtraction(
    val total: String = "",
    val note: String = "",
    val confidence: Double? = null
)

enum class ScanFailureReason { NETWORK, API_ERROR, PARSE, UNREADABLE }

sealed interface ScanOutcome {
    data class Success(val amountCents: Long, val note: String) : ScanOutcome
    data class Failure(val reason: ScanFailureReason) : ScanOutcome
}
