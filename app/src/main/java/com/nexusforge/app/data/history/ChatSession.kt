package com.nexusforge.app.data.history

import com.nexusforge.app.data.ChatMessage
import kotlinx.serialization.Serializable

/** One rendered turn, kept simple enough to redraw a session instantly on resume. */
@Serializable
data class PersistedMessage(
    val role: String, // "user" | "assistant"
    val text: String,
    val toolSummaries: List<String> = emptyList(), // legacy: "write_file Task.kt"; kept for old sessions
    val isError: Boolean = false,
    val toolChips: List<PersistedToolChip> = emptyList(),
    val errorMessage: String? = null
)

/** A tool call as shown in the chat, including the code it wrote, so history reopens identically. */
@Serializable
data class PersistedToolChip(
    val name: String,
    val path: String? = null,
    val status: String = "DONE",
    val content: String? = null,
    val isNewFile: Boolean? = null,
    val detail: String? = null
)

/**
 * A full, resumable conversation: the raw OpenAI-wire history (so the model keeps its context
 * on resume) plus a lightweight rendered form (so the UI doesn't need to replay tool-call
 * reconstruction just to show past bubbles).
 */
@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val providerLabel: String,
    val projectLabel: String,
    val projectId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val apiMessages: List<ChatMessage>,
    val renderedMessages: List<PersistedMessage>
)

@Serializable
data class ChatSessionSummary(
    val id: String,
    val title: String,
    val providerLabel: String,
    val projectLabel: String,
    val updatedAt: Long
)
