package com.nexusforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.ToolChip
import com.nexusforge.app.data.ToolRegistry
import com.nexusforge.app.data.ToolStatus
import com.nexusforge.app.ui.theme.ErrorRed
import com.nexusforge.app.ui.theme.MonoFamily
import com.nexusforge.app.ui.theme.SuccessGreen

/**
 * One tool call in an assistant turn. A file write is shown as its code (live while it's being
 * written, then a preview you can expand); anything else is a compact one-line row, so a long
 * agent run reads like a clean activity log instead of a stack of heavy cards.
 */
@Composable
fun ActionCard(chip: ToolChip, modifier: Modifier = Modifier) {
    if (chip.toolName == ToolRegistry.WRITE_FILE) {
        WriteFileCard(chip, modifier)
    } else {
        ToolLine(chip, modifier)
    }
}

@Composable
private fun WriteFileCard(chip: ToolChip, modifier: Modifier) {
    val path = chip.path.orEmpty()
    val name = path.substringAfterLast('/').ifBlank { "file" }
    val dir = path.substringBeforeLast('/', "").ifBlank { null }
    val writing = chip.status == ToolStatus.RUNNING && chip.isStreaming
    val verb = when {
        chip.status == ToolStatus.ERROR -> "Failed"
        writing -> "Writing"
        chip.status == ToolStatus.RUNNING -> "Saving"
        chip.isNewFile == false -> "Edited"
        else -> "Created"
    }
    CodeCard(
        code = chip.content.orEmpty(),
        language = languageFromPath(path),
        title = name,
        subtitle = dir,
        verb = verb,
        live = writing,
        isError = chip.status == ToolStatus.ERROR,
        errorText = chip.detail,
        showShare = false, // already saved in the project
        modifier = modifier
    )
}

private fun iconFor(toolName: String): ImageVector = when (toolName) {
    ToolRegistry.READ_FILE -> Icons.Default.Visibility
    ToolRegistry.LIST_FILES -> Icons.Default.ListAlt
    ToolRegistry.ZIP_PROJECT -> Icons.Default.FolderZip
    else -> Icons.Default.Description
}

private fun labelFor(toolName: String, status: ToolStatus): String {
    val done = status != ToolStatus.RUNNING
    return when (toolName) {
        ToolRegistry.READ_FILE -> if (done) "Read" else "Reading"
        ToolRegistry.LIST_FILES -> if (done) "Listed" else "Listing"
        ToolRegistry.ZIP_PROJECT -> if (done) "Zipped" else "Zipping"
        else -> toolName.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun ToolLine(chip: ToolChip, modifier: Modifier) {
    val tint = when (chip.status) {
        ToolStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ToolStatus.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
        ToolStatus.ERROR -> ErrorRed
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(iconFor(chip.toolName), null, tint = tint, modifier = Modifier.size(15.dp))
                Text(
                    labelFor(chip.toolName, chip.status),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    chip.path.orEmpty().ifBlank { if (chip.toolName == ToolRegistry.LIST_FILES) "project" else "" },
                    fontFamily = MonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                when (chip.status) {
                    ToolStatus.RUNNING -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp), strokeWidth = 1.6.dp, color = tint
                    )
                    ToolStatus.DONE -> Icon(Icons.Default.Check, "Done", tint = SuccessGreen, modifier = Modifier.size(15.dp))
                    ToolStatus.ERROR -> Icon(Icons.Default.Close, "Failed", tint = ErrorRed, modifier = Modifier.size(15.dp))
                }
            }
            if (!chip.detail.isNullOrBlank()) {
                Text(
                    chip.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chip.status == ToolStatus.ERROR) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 23.dp, top = 2.dp)
                )
            }
        }
    }
}
