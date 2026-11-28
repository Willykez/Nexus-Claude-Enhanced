package com.nexusforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.FileNode

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
    else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
}

private fun fileLook(name: String): Pair<ImageVector, Color> = when (name.substringAfterLast('.', "").lowercase()) {
    "kt", "kts" -> Icons.Default.Code to Color(0xFFA78BFA)
    "java" -> Icons.Default.Code to Color(0xFFF87171)
    "py" -> Icons.Default.Code to Color(0xFF60A5FA)
    "js", "jsx", "mjs" -> Icons.Default.Code to Color(0xFFFACC15)
    "ts", "tsx" -> Icons.Default.Code to Color(0xFF38BDF8)
    "html", "htm" -> Icons.Default.Code to Color(0xFFFB923C)
    "css", "scss" -> Icons.Default.Code to Color(0xFF818CF8)
    "xml" -> Icons.Default.Code to Color(0xFF4ADE80)
    "gradle" -> Icons.Default.Code to Color(0xFF2DD4BF)
    "json", "yml", "yaml", "toml", "properties" -> Icons.Default.Description to Color(0xFFFBBF24)
    "md", "txt" -> Icons.Default.Description to Color(0xFF94A3B8)
    "png", "jpg", "jpeg", "webp", "gif", "svg" -> Icons.Default.Image to Color(0xFFF472B6)
    else -> Icons.Default.InsertDriveFile to Color(0xFF94A3B8)
}

/**
 * One project's files and folders as a scrolling tree. Folders start collapsed so a project opens
 * as a short, tidy list you drill into, never one long wall of every file.
 */
@Composable
fun ProjectFileTree(
    root: FileNode,
    highlightedPath: String?,
    onOpenFile: (FileNode) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = remember(root.path) { mutableStateMapOf<String, Boolean>() }
    val rows = ArrayList<Pair<FileNode, Int>>()
    fun flatten(node: FileNode, depth: Int) {
        for (c in node.children) {
            rows += c to depth
            if (c.isDirectory && expanded[c.path] == true) flatten(c, depth + 1)
        }
    }
    flatten(root, 0)

    if (root.children.isEmpty()) {
        Text(
            "This project is empty. Ask the agent to build something, or start from a template.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(20.dp)
        )
        return
    }

    LazyColumn(modifier) {
        items(rows, key = { it.first.path }) { (node, depth) ->
            var menuOpen by remember { mutableStateOf(false) }
            val isOpen = expanded[node.path] == true
            val look = fileLook(node.name)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (node.path == highlightedPath) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { if (node.isDirectory) expanded[node.path] = !isOpen else onOpenFile(node) }
                    .padding(start = (10 + depth * 18).dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (node.isDirectory) {
                    Icon(
                        Icons.Default.ExpandMore, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp).rotate(if (isOpen) 0f else -90f)
                    )
                    Icon(
                        if (isOpen) Icons.Default.FolderOpen else Icons.Default.Folder, null,
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp)
                    )
                } else {
                    Box(Modifier.size(18.dp))
                    Icon(look.first, null, tint = look.second, modifier = Modifier.size(18.dp))
                }
                Text(
                    node.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 11.dp)
                )
                if (node.isDirectory) {
                    Text("${node.children.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
                } else {
                    Text(formatBytes(node.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, "File actions", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                onClick = { menuOpen = false; onRename(node) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menuOpen = false; onDelete(node) }
                            )
                        }
                    }
                }
            }
        }
    }
}
