package com.nexusforge.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusforge.app.ui.theme.ErrorRed
import com.nexusforge.app.ui.theme.MonoFamily
import com.nexusforge.app.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

/** While code is being written we only show this many of the newest lines, like Claude does. */
private const val LIVE_LINES = 5

/** A finished block longer than this is previewed (first lines) and can be expanded. */
private const val PREVIEW_LINES = 8

private val CODE_FONT = 12.sp
private val CODE_LINE_HEIGHT = 18.sp
private val EXPANDED_MAX_HEIGHT = 380.dp

@Composable
fun rememberCodePalette(): CodePalette =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkCodePalette else LightCodePalette

/**
 * The one way code is shown in the app, whether the agent wrote it to a file or it sits in a
 * fenced block of a reply.
 *
 *  - [live] = still being written: a small fixed window of the newest lines, auto-following.
 *  - finished and short: shown in full.
 *  - finished and long: first lines as a preview, with an expand button for the rest.
 *
 * [verb] ("Created", "Edited", "Writing"...) is only set for file operations; it also switches the
 * header glyph from a plain code icon to a status check.
 */
@Composable
fun CodeCard(
    code: String,
    language: String?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    verb: String? = null,
    live: Boolean = false,
    isError: Boolean = false,
    errorText: String? = null,
    showShare: Boolean = false
) {
    val palette = rememberCodePalette()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var expanded by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    val lineCount = remember(code) { countLines(code) }
    val canExpand = !live && lineCount > PREVIEW_LINES
    val showFull = !live && (expanded || !canExpand)

    val borderColor = when {
        live -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        isError -> ErrorRed.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline
    }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "code_chevron")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.animateContentSize()) {
            // ---- header ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.header)
                    .clickable(enabled = canExpand) { expanded = !expanded }
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    .heightIn(min = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    live -> PulsingDot(MaterialTheme.colorScheme.primary)
                    isError -> Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    verb != null -> Icon(Icons.Default.Check, "Done", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    else -> Box(Modifier.size(7.dp).background(palette.comment.copy(alpha = 0.6f), CircleShape))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (verb != null) {
                            Text(
                                verb,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    live -> MaterialTheme.colorScheme.primary
                                    isError -> ErrorRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Text(
                            title,
                            fontFamily = MonoFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            fontFamily = MonoFamily,
                            fontSize = 10.5.sp,
                            color = palette.gutter,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (lineCount > 0) {
                    Text(
                        if (lineCount == 1) "1 line" else "$lineCount lines",
                        fontFamily = MonoFamily,
                        fontSize = 10.5.sp,
                        color = palette.gutter,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
                if (!live && code.isNotEmpty()) {
                    HeaderAction(
                        icon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        description = if (copied) "Copied" else "Copy code",
                        tint = if (copied) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    if (showShare) {
                        HeaderAction(Icons.Default.Share, "Share code", MaterialTheme.colorScheme.onSurfaceVariant) {
                            val uri = saveSnippetForSharing(context, language, code)
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share code"))
                            }
                        }
                    }
                }
                if (canExpand) {
                    HeaderAction(
                        icon = Icons.Default.ExpandMore,
                        description = if (expanded) "Collapse code" else "Expand code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevron)
                    ) { expanded = !expanded }
                }
            }

            if (isError && !errorText.isNullOrBlank()) {
                Text(
                    errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // ---- body ----
            when {
                live -> LiveTail(code, language, palette)
                code.isEmpty() -> Unit
                showFull -> FullCode(code, language, palette, lineCount, scrollable = expanded)
                else -> PreviewCode(code, language, palette, onClick = { expanded = true })
            }

            // ---- footer (only for long finished code) ----
            if (canExpand) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.header)
                        .clickable { expanded = !expanded }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "Collapse" else "Show all $lineCount lines",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.ExpandMore, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp).size(16.dp).rotate(chevron)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Bodies
// ---------------------------------------------------------------------------------------------

/** Fixed-height window on the newest few lines while the model is writing. */
@Composable
private fun LiveTail(code: String, language: String?, palette: CodePalette) {
    val lineHeightDp = with(LocalDensity.current) { CODE_LINE_HEIGHT.toDp() }
    val tail = remember(code) { newestLines(code, LIVE_LINES) }
    val annotated = remember(tail.text, language, palette) { highlightCode(tail.text, language, palette) }
    val gutter = remember(tail.firstLineNumber, tail.lineCount) {
        (tail.firstLineNumber until tail.firstLineNumber + tail.lineCount).joinToString("\n")
    }
    val digits = (tail.firstLineNumber + tail.lineCount).toString().length

    Box(
        Modifier
            .fillMaxWidth()
            .height(lineHeightDp * LIVE_LINES + 20.dp)
            .background(palette.surface)
    ) {
        if (code.isEmpty()) {
            Text(
                "Starting…",
                fontFamily = MonoFamily, fontSize = CODE_FONT, color = palette.gutter,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    gutter,
                    fontFamily = MonoFamily, fontSize = CODE_FONT, lineHeight = CODE_LINE_HEIGHT,
                    color = palette.gutter, textAlign = TextAlign.End, softWrap = false,
                    modifier = Modifier.width((digits * 8).dp).padding(end = 0.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    annotated,
                    fontFamily = MonoFamily, fontSize = CODE_FONT, lineHeight = CODE_LINE_HEIGHT,
                    color = palette.text, softWrap = false, maxLines = LIVE_LINES,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f)
                )
            }
            // Older lines fade out at the top so new ones feel like they are scrolling in.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Brush.verticalGradient(listOf(palette.surface, Color.Transparent)))
            )
        }
    }
}

/** First few lines of a long, finished block, fading out where the rest is hidden. */
@Composable
private fun PreviewCode(code: String, language: String?, palette: CodePalette, onClick: () -> Unit) {
    val preview = remember(code) { firstLines(code, PREVIEW_LINES) }
    val annotated = remember(preview, language, palette) { highlightCode(preview, language, palette) }
    val gutter = remember { (1..PREVIEW_LINES).joinToString("\n") }
    val digits = remember(code) { countLines(code).toString().length }

    Box(
        Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                gutter,
                fontFamily = MonoFamily, fontSize = CODE_FONT, lineHeight = CODE_LINE_HEIGHT,
                color = palette.gutter, textAlign = TextAlign.End, softWrap = false,
                modifier = Modifier.width((digits * 8).dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                annotated,
                fontFamily = MonoFamily, fontSize = CODE_FONT, lineHeight = CODE_LINE_HEIGHT,
                color = palette.text, softWrap = false, maxLines = PREVIEW_LINES,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, palette.surface)))
        )
    }
}

/**
 * The whole file. When [scrollable] (the user expanded a long block) it gets a bounded height and
 * scrolls inside itself. That bound is required: this sits in a LazyColumn, and a verticalScroll
 * with no finite height inside one crashes.
 */
@Composable
private fun FullCode(code: String, language: String?, palette: CodePalette, lineCount: Int, scrollable: Boolean) {
    val annotated = remember(code, language, palette) { highlightCode(code, language, palette) }
    val gutter = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    val outer = Modifier
        .fillMaxWidth()
        .background(palette.surface)
        .let { if (scrollable) it.heightIn(max = EXPANDED_MAX_HEIGHT).verticalScroll(vScroll) else it }

    Box(outer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                gutter,
                fontFamily = MonoFamily, fontSize = CODE_FONT, lineHeight = CODE_LINE_HEIGHT,
                color = palette.gutter, textAlign = TextAlign.End, softWrap = false,
                modifier = Modifier.width((lineCount.toString().length * 8).dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                SelectionContainer {
                    Text(
                        annotated,
                        fontFamily = MonoFamily, fontSize = CODE_FONT, lineHeight = CODE_LINE_HEIGHT,
                        color = palette.text, softWrap = false
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = modifier.size(17.dp))
    }
}

@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse_alpha"
    )
    Box(modifier.size(8.dp).background(color.copy(alpha = alpha), CircleShape))
}

// ---------------------------------------------------------------------------------------------
// Text helpers
// ---------------------------------------------------------------------------------------------

private fun countLines(code: String): Int {
    if (code.isEmpty()) return 0
    val trimmed = code.trimEnd('\n', '\r')
    if (trimmed.isEmpty()) return 1
    var n = 1
    for (c in trimmed) if (c == '\n') n++
    return n
}

private fun firstLines(code: String, n: Int): String {
    var idx = -1
    repeat(n) {
        idx = code.indexOf('\n', idx + 1)
        if (idx < 0) return code.trimEnd('\n', '\r')
    }
    return code.substring(0, idx)
}

private class Tail(val text: String, val firstLineNumber: Int, val lineCount: Int)

/** The newest [n] lines of [code] and the line number of the first of them. */
private fun newestLines(code: String, n: Int): Tail {
    val trimmed = code.trimEnd('\n', '\r')
    if (trimmed.isEmpty()) return Tail("", 1, 1)
    var start = 0
    var pos = trimmed.length
    var found = 0
    while (found < n) {
        val prev = if (pos <= 0) -1 else trimmed.lastIndexOf('\n', pos - 1)
        if (prev < 0) {
            start = 0
            break
        }
        pos = prev
        found++
        if (found == n) start = pos + 1
    }
    val text = trimmed.substring(start)
    var inTail = 1
    for (c in text) if (c == '\n') inTail++
    var total = 1
    for (c in trimmed) if (c == '\n') total++
    return Tail(text, total - inTail + 1, inTail)
}

// ---------------------------------------------------------------------------------------------
// Share
// ---------------------------------------------------------------------------------------------

/** Writes a snippet to cache and exposes it through the app's existing FileProvider. */
private fun saveSnippetForSharing(context: Context, language: String?, code: String): Uri? {
    return try {
        val directory = java.io.File(context.cacheDir, "snippets").apply { mkdirs() }
        val file = java.io.File(directory, "snippet_${System.currentTimeMillis()}.${extensionFor(language)}")
        file.writeText(code)
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) {
        null
    }
}
