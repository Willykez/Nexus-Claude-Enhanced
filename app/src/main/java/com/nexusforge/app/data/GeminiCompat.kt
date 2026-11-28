package com.nexusforge.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Everything Gemini 3.x / 4.x needs that other OpenAI-compatible providers don't.
 *
 * 1. thought_signature: Gemini attaches `extra_content.google.thought_signature` to a streamed
 *    tool call. It MUST be replayed untouched on the assistant message in the next request, or the
 *    endpoint answers 400. (Only the first call of a parallel batch carries one.)
 * 2. Thinking: `reasoning_effort` is how the OpenAI shim exposes `thinkingLevel`. Sent only to
 *    Gemini 3+/4+, because OpenAI-style providers reject unknown parameters.
 * 3. Endpoint: Gemini 3 models live on /v1beta, not /v1.
 */
object GeminiCompat {

    /** Google's documented bypass for history that has no real signature (old sessions). */
    const val SKIP_SIGNATURE = "skip_thought_signature_validator"

    /** thinkingLevel for the agent. "minimal" keeps flash-lite fast; raise to "low"/"medium" if needed. */
    const val THINKING_LEVEL = "minimal"

    fun isGemini(baseUrl: String): Boolean = baseUrl.contains("generativelanguage.googleapis.com", ignoreCase = true)

    private fun isThinkingLevelModel(model: String): Boolean {
        val m = model.trim().lowercase().removePrefix("models/")
        return m.startsWith("gemini-3") || m.startsWith("gemini-4")
    }

    fun reasoningEffort(config: ProviderConfig): String? =
        if (isGemini(config.baseUrl) && isThinkingLevelModel(config.model)) THINKING_LEVEL else null

    /** Fixes saved profiles that still point at the legacy v1 / v1main Gemini endpoints. */
    fun normalizeBaseUrl(baseUrl: String): String {
        if (!isGemini(baseUrl)) return baseUrl
        return baseUrl
            .replace("/v1main/", "/v1beta/")
            .replace(Regex("/v1(/openai)", RegexOption.IGNORE_CASE), "/v1beta$1")
    }

    fun signatureExtra(signature: String): JsonObject = buildJsonObject {
        put("google", buildJsonObject { put("thought_signature", JsonPrimitive(signature)) })
    }

    private fun hasSignature(call: ToolCall): Boolean {
        val google = call.extraContent?.get("google") as? JsonObject ?: return false
        val sig = google["thought_signature"] as? JsonPrimitive ?: return false
        return sig.content.isNotBlank()
    }

    /**
     * A copy of the history that is safe to send. Real signatures are left exactly as received.
     * For Gemini only: an assistant turn whose first tool call has no signature (a session saved
     * before signatures were captured) gets the documented skip marker on that first call.
     * The stored history itself is never modified.
     */
    fun prepareMessages(messages: List<ChatMessage>, baseUrl: String): List<ChatMessage> {
        if (!isGemini(baseUrl)) return messages
        return messages.map { msg ->
            val calls = msg.toolCalls
            if (msg.role != "assistant" || calls.isNullOrEmpty() || hasSignature(calls.first())) msg
            else msg.copy(
                toolCalls = listOf(calls.first().copy(extraContent = signatureExtra(SKIP_SIGNATURE))) + calls.drop(1)
            )
        }
    }
}
