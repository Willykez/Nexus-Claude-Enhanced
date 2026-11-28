package com.nexusforge.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.nexusforge.app.data.Project
import com.nexusforge.app.data.ProjectSource
import com.nexusforge.app.data.organizer.ProjectKind
import com.nexusforge.app.ui.components.CodeViewer
import com.nexusforge.app.ui.components.DateRange
import com.nexusforge.app.ui.components.ProjectFileTree
import com.nexusforge.app.ui.components.ProjectKindBadge
import com.nexusforge.app.ui.components.languageFromPath
import com.nexusforge.app.ui.components.matchesDateQuery
import com.nexusforge.app.ui.components.relativeTime
import com.nexusforge.app.ui.theme.MonoFamily
import com.nexusforge.app.viewmodel.AppUiState

@Composable
fun WorkspaceScreen(
    state: AppUiState,
    onOpenProject: (Project) -> Unit,
    onCloseProject: () -> Unit,
    onUseInChat: (Project) -> Unit,
    onOrganize: (Project) -> Unit,
    onNewProject: () -> Unit,
    onAttachFolder: () -> Unit,
    onRenameProject: (Project, String) -> Unit,
    onDeleteProject: (Project) -> Unit,
    onRefresh: () -> Unit,
    onOpenFile: (String) -> Unit,
    onClosePreview: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onZip: () -> Unit
) {
    val open = state.browseProject
    if (open == null) {
        ProjectListView(state, onOpenProject, onNewProject, onAttachFolder, onRenameProject, onDeleteProject)
    } else {
        ProjectFolderView(state, open, onCloseProject, onUseInChat, onOrganize, onRefresh, onOpenFile, onClosePreview, onRename, onDelete, onZip)
    }
}

// ---------------------------------------------------------------------------------------------
// Level 1: every project, as a folder
// ---------------------------------------------------------------------------------------------

@Composable
private fun ProjectListView(
    state: AppUiState,
    onOpenProject: (Project) -> Unit,
    onNewProject: () -> Unit,
    onAttachFolder: () -> Unit,
    onRenameProject: (Project, String) -> Unit,
    onDeleteProject: (Project) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var range by remember { mutableStateOf(DateRange.ANY) }
    var sortByName by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }

    val now = System.currentTimeMillis()
    val filtered = remember(state.projects, query, range, sortByName) {
        val q = query.trim()
        state.projects
            .filter { range.contains(it.lastModified, now) }
            .filter { p ->
                q.isEmpty() || p.name.contains(q, ignoreCase = true) ||
                    ProjectKind.fromKey(p.kind).label.contains(q, ignoreCase = true) ||
                    matchesDateQuery(q, p.lastModified, now)
            }
            .let { list -> if (sortByName) list.sortedBy { it.name.lowercase() } else list.sortedByDescending { it.lastModified } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Workspace", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${state.projects.size} project${if (state.projects.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onAttachFolder) { Icon(Icons.Default.FolderOpen, contentDescription = "Attach a real folder") }
            Button(onClick = onNewProject) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Text("  New")
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search by name, type or date (today, nov 2026…)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear search") }
            },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            for (r in DateRange.entries) {
                FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) })
            }
            FilterChip(
                selected = sortByName, onClick = { sortByName = !sortByName },
                label = { Text(if (sortByName) "A–Z" else "Recent") },
                leadingIcon = { Icon(if (sortByName) Icons.Default.SortByAlpha else Icons.Default.Schedule, null, modifier = Modifier.size(16.dp)) }
            )
        }

        when {
            state.projects.isEmpty() -> EmptyNote("No projects yet", "Create one from a template or attach a real folder.")
            filtered.isEmpty() -> EmptyNote("No matches", "Nothing matches \"$query\"${if (range != DateRange.ANY) " in ${range.label.lowercase()}" else ""}.")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        isActive = project.id == state.activeProject?.id,
                        onClick = { onOpenProject(project) },
                        onRename = { renameTarget = project; renameText = project.name },
                        onDelete = { deleteTarget = project }
                    )
                }
            }
        }
    }

    renameTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename project") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRenameProject(project, renameText); renameTarget = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${project.name}\"?") },
            text = {
                Text(
                    if (project.source is ProjectSource.Sandbox) "This permanently deletes every file in this sandbox project. This can't be undone."
                    else "This only removes it from Nexus Forge. The real folder and its files are untouched."
                )
            },
            confirmButton = { TextButton(onClick = { onDeleteProject(project); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyNote(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ProjectCard(project: Project, isActive: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val kind = if (project.kind.isBlank()) null else ProjectKind.fromKey(project.kind)
    val isFolder = project.source is ProjectSource.AttachedFolder

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProjectKindBadge(kind ?: ProjectKind.OTHER, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        project.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                            Text(
                                "ACTIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    if (kind == null) "Scanning…" else "${kind.label} · ${project.fileCount} files · ${project.folderCount} folders",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        if (isFolder) Icons.Default.FolderOpen else Icons.Default.PhoneAndroid, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp)
                    )
                    Text(
                        "${if (isFolder) "Attached folder" else "Sandbox"} · edited ${relativeTime(project.lastModified)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "Project actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Level 2: one project's files and folders
// ---------------------------------------------------------------------------------------------

@Composable
private fun ProjectFolderView(
    state: AppUiState,
    project: Project,
    onClose: () -> Unit,
    onUseInChat: (Project) -> Unit,
    onOrganize: (Project) -> Unit,
    onRefresh: () -> Unit,
    onOpenFile: (String) -> Unit,
    onClosePreview: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onZip: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val kind = if (project.kind.isBlank()) ProjectKind.OTHER else ProjectKind.fromKey(project.kind)
    val isActive = project.id == state.activeProject?.id
    val selectedFile = state.selectedFile
    val zipPath = state.lastExportedZip

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selectedFile != null) onClosePreview() else onClose() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            ProjectKindBadge(kind, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${kind.label} · ${project.fileCount} files · ${project.folderCount} folders",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
            IconButton(onClick = onZip) { Icon(Icons.Default.FolderZip, "Zip project") }
        }

        if (selectedFile == null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if (isActive) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.TaskAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("Active in chat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    Button(onClick = { onUseInChat(project) }) { Text("Use in chat") }
                }
                OutlinedButton(onClick = { onOrganize(project) }) { Text("Organize") }
            }
        }
        HorizontalDivider()

        if (zipPath != null && selectedFile == null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Archive ready: ${java.io.File(zipPath).name}", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", java.io.File(zipPath))
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share archive"))
                    }) { Icon(Icons.Default.Share, "Share archive") }
                }
            }
        }

        val tree = state.browseTree
        when {
            selectedFile != null -> FilePreview(selectedFile, state.selectedFileContent)
            tree == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> ProjectFileTree(
                root = tree,
                highlightedPath = null,
                onOpenFile = { onOpenFile(it.path) },
                onRename = { renameTarget = it.path; renameText = it.name },
                onDelete = { deleteTarget = it.path },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    renameTarget?.let { path ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(path, renameText); renameTarget = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { path ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this file?") },
            text = { Text("$path will be permanently deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(path); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FilePreview(path: String, content: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            path, fontFamily = MonoFamily, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        CodeViewer(content, languageFromPath(path), Modifier.padding(horizontal = 12.dp).padding(bottom = 16.dp))
    }
}
