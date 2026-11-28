package com.nexusforge.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.ToolStatus
import com.nexusforge.app.data.UiChatMessage
import com.nexusforge.app.ui.markdown.MarkdownText
import com.nexusforge.app.ui.markdown.UserMessageText
import com.nexusforge.app.ui.markdown.containsCodeFence
import com.nexusforge.app.ui.theme.ErrorRed
import com.nexusforge.app.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Turns
// ---------------------------------------------------------------------------------------------

/**
 * The agent loop streams one assistant message per round (comment + tool calls, comment + tool
 * calls, ..., final summary). Shown as separate messages they looked like a dozen replies with a
 * dozen copy buttons and big gaps. A "turn" glues all consecutive assistant messages back into
 * the single reply the user actually perceives.
 */
sealed interface ChatTurn {
    val key: String
}

data class UserTurn(val message: UiChatMessage) : ChatTurn {
    override val key: String get() = message.id
}

data class AssistantGroup(val messages: List<UiChatMessage>) : ChatTurn {
    override val key: String get() = messages.first().id
}

fun groupTurns(messages: List<UiChatMessage>): List<ChatTurn> {
    val turns = mutableListOf<ChatTurn>()
    var run = mutableListOf<UiChatMessage>()
    for (m in messages) {
        if (m.role == "user") {
            if (run.isNotEmpty()) {
                turns += AssistantGroup(run)
                run = mutableListOf()
            }
            turns += UserTurn(m)
        } else {
            run += m
        }
    }
    if (run.isNotEmpty()) turns += AssistantGroup(run)
    return turns
}

// ---------------------------------------------------------------------------------------------
// Sender bubble
// ---------------------------------------------------------------------------------------------

/**
 * The user's message: a right-aligned bubble with a soft accent gradient and a squared-off tail.
 * It hugs short text instead of always stretching to 86% width, widens to full width when the
 * message contains pasted code (shown as a real code card), and picks out @file mentions.
 */
@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    val hasCode = remember(text) { containsCodeFence(text) }
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Read maxWidth here: inside Row { } the BoxWithConstraints scope is no longer reachable.
        val available = maxWidth
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = if (hasCode) available else available * 0.88f)
                    .then(if (hasCode) Modifier.fillMaxWidth() else Modifier)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.20f), primary.copy(alpha = 0.09f))))
                    .border(1.dp, primary.copy(alpha = 0.28f), shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                UserMessageText(text)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Assistant turn
// ---------------------------------------------------------------------------------------------

/**
 * One assistant reply, however many rounds the agent took. Everything in it is tightly spaced
 * (6dp): the short comment the AI makes before a file, the file's code card, the next comment...
 * Comments are plain text with no copy button; a single Copy / Retry row appears once at the end
 * of the turn.
 *
 * [messages] may be empty: that's the instant between the user sending and the first token.
 */
@Composable
fun AssistantTurn(
    messages: List<UiChatMessage>,
    isActive: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val last = messages.lastOrNull()
    val hasError = messages.any { it.isError }
    val stopped = messages.any { it.isStopped }
    val copyText = messages.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

    // "Thinking…" is only for dead air. Streaming text or a running tool already shows progress.
    val visibleProgress = last != null &&
        (last.toolChips.any { it.status == ToolStatus.RUNNING } || (last.isStreaming && last.text.isNotBlank()))
    val showThinking = isActive && !visibleProgress

    Column(
        modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AssistantHeader(isActive)

        for (m in messages) {
            if (m.text.isNotBlank()) {
                // Text that leads into tool calls is a "comment" (e.g. "Creating the cart model"):
                // slightly softer than a final answer so the code cards stay the focus.
                val isComment = m.toolChips.isNotEmpty()
                val textColor = if (isComment) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                else MaterialTheme.colorScheme.onSurface
                CompositionLocalProvider(LocalContentColor provides textColor) {
                    MarkdownText(m.text, isStreaming = m.isStreaming)
                }
            }
            for (chip in m.toolChips) ActionCard(chip)
            if (m.isError) ErrorCard(m.errorMessage)
        }

        if (stopped && !isActive) StoppedNote()
        if (showThinking) ThinkingIndicator()

        if (!isActive && messages.isNotEmpty()) {
            TurnActions(
                copyText = copyText,
                canRetry = canRetry,
                emphasizeRetry = hasError || stopped,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun AssistantHeader(isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
        }
        Text("Nexus", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (isActive) PulsingDot(MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorCard(message: String?) {
    Surface(
        color = ErrorRed.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Something went wrong", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ErrorRed)
                Text(
                    message?.takeIf { it.isNotBlank() } ?: "The request didn't finish. You can try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StoppedNote() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.Stop, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text("Stopped", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------
// End-of-turn actions
// ---------------------------------------------------------------------------------------------

@Composable
private fun TurnActions(copyText: String, canRetry: Boolean, emphasizeRetry: Boolean, onRetry: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (copyText.isNotBlank()) {
            ActionPill(
                icon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                label = if (copied) "Copied" else "Copy",
                tint = if (copied) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                clipboard.setText(AnnotatedString(copyText))
                copied = true
            }
        }
        if (canRetry) {
            ActionPill(
                icon = Icons.Default.Refresh,
                label = if (emphasizeRetry) "Try again" else "Retry",
                tint = if (emphasizeRetry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                emphasized = emphasizeRetry,
                onClick = onRetry
            )
        }
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = if (emphasized) tint.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (emphasized) BorderStroke(1.dp, tint.copy(alpha = 0.45f)) else null,
        modifier = Modifier.clip(shape).clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Thinking indicator (also used by the Organizer screen)
// ---------------------------------------------------------------------------------------------

private val THINKING_PHRASES = listOf(
    "Thinking…", "Reading the project…", "Planning the approach…", "Working through it…", "Considering the details…"
)

/** Rotating phrases + typing dots, so a slow model never looks frozen. */
@Composable
fun ThinkingIndicator() {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1700)
            index = (index + 1) % THINKING_PHRASES.size
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TypingDots()
        Text(THINKING_PHRASES[index], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), CircleShape))
        }
    }
}
