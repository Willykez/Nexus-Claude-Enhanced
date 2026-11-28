package com.nexusforge.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.organizer.IssueSource
import com.nexusforge.app.data.organizer.ReorgAction
import com.nexusforge.app.data.organizer.Severity
import com.nexusforge.app.data.organizer.StructureIssue
import com.nexusforge.app.data.organizer.WizardPhase
import com.nexusforge.app.data.organizer.LogLine
import com.nexusforge.app.ui.theme.ErrorRed
import com.nexusforge.app.ui.theme.MonoFamily
import com.nexusforge.app.ui.theme.SuccessGreen
import com.nexusforge.app.viewmodel.AppUiState

/**
 * The interactive Cleanup Wizard. It walks through each fixable structural issue, shows exactly
 * what would change, lets the user include or skip it, then asks for a final confirmation before
 * a single file is touched.
 *
 *   Intro  ->  one step per issue  ->  Review & confirm  ->  Applying  ->  Done (with Undo)
 */
@Composable
fun CleanupWizard(state: AppUiState, isRealFolder: Boolean, projectName: String, actions: OrganizerActions) {
    val issues = state.analysis?.fixableIssues.orEmpty()
    val w = state.wizard
    when (w.phase) {
        WizardPhase.INTRO -> WizardIntro(issues, isRealFolder, projectName, actions)
        WizardPhase.STEP -> {
            val issue = issues.getOrNull(w.index)
            if (issue != null) WizardStep(issue, w.index, issues.size, issue.id in w.accepted, actions)
        }
        WizardPhase.REVIEW -> WizardReview(issues, w.accepted, isRealFolder, projectName, actions)
        WizardPhase.APPLYING, WizardPhase.DONE -> WizardProgress(state, actions)
        WizardPhase.CLOSED -> Unit
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun WizardIntro(issues: List<StructureIssue>, isRealFolder: Boolean, projectName: String, actions: OrganizerActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary)
                Text("Cleanup Wizard", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Text(
                "I found ${issues.size} thing${if (issues.size == 1) "" else "s"} to tidy in $projectName. " +
                    "You'll review each one, choose what to apply, and confirm before anything changes.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(issues, key = { it.id }) { issue ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(issue.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${issue.actions.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (isRealFolder) {
            item { RealFolderWarning() }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.wizardBegin, modifier = Modifier.weight(1f)) { Text("Start review") }
                OutlinedButton(onClick = actions.closeWizard) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun RealFolderWarning() {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(
                "This project is a real folder on your device, so changes apply to the actual files. " +
                    "Moves and new files can be undone afterwards; deleted files can't.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun WizardStep(issue: StructureIssue, index: Int, total: Int, included: Boolean, actions: OrganizerActions) {
    var showAll by remember(issue.id) { mutableStateOf(false) }
    val shown = if (showAll) issue.actions else issue.actions.take(6)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Step ${index + 1} of $total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (index + 1f) / total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (issue.source == IssueSource.AI) Tag("AI suggestion", MaterialTheme.colorScheme.secondary)
                if (issue.severity == Severity.WARNING) Tag("Structure", MaterialTheme.colorScheme.primary) else Tag("Note", MaterialTheme.colorScheme.onSurfaceVariant)
                if (included) Tag("Included", SuccessGreen)
            }
            Text(issue.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(issue.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        item { Text("Proposed changes (${issue.actions.size})", style = MaterialTheme.typography.titleMedium) }
        items(shown) { action -> ActionLine(action) }
        if (issue.actions.size > 6) {
            item { TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer" else "Show all ${issue.actions.size}") } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = actions.wizardBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Text(" Back")
                }
                Text("", Modifier.weight(1f))
                OutlinedButton(onClick = { actions.wizardDecide(false) }) { Text("Skip") }
                Button(onClick = { actions.wizardDecide(true) }) {
                    Text(if (included) "Keep" else "Include")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

@Composable
private fun ActionLine(action: ReorgAction) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (action) {
                is ReorgAction.Move -> {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(action.from, fontFamily = MonoFamily, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(action.to, fontFamily = MonoFamily, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                is ReorgAction.Create -> {
                    Icon(Icons.Default.Add, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    Text("Create ${action.path}", fontFamily = MonoFamily, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                }
                is ReorgAction.Delete -> {
                    Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    Text("Delete ${action.path}${if (action.isDirectory) "/" else ""}", fontFamily = MonoFamily, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun WizardReview(issues: List<StructureIssue>, accepted: Set<String>, isRealFolder: Boolean, projectName: String, actions: OrganizerActions) {
    var confirming by remember { mutableStateOf(false) }
    val chosen = issues.filter { it.id in accepted }
    val all = chosen.flatMap { it.actions }
    val moves = all.count { it is ReorgAction.Move }
    val creates = all.count { it is ReorgAction.Create }
    val deletes = all.count { it is ReorgAction.Delete }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Review & confirm", style = MaterialTheme.typography.titleLarge)
            Text("Switch off anything you don't want. Nothing has been changed yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        items(issues, key = { it.id }) { issue ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(issue.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${issue.actions.size} change${if (issue.actions.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = issue.id in accepted, onCheckedChange = { actions.wizardToggle(issue.id) })
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (all.isEmpty()) "Nothing selected." else "$moves move${if (moves == 1) "" else "s"} · $creates new file${if (creates == 1) "" else "s"} · $deletes deletion${if (deletes == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp)
                )
            }
        }
        if (isRealFolder) item { RealFolderWarning() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = actions.wizardBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Text(" Back")
                }
                Text("", Modifier.weight(1f))
                OutlinedButton(onClick = actions.closeWizard) { Text("Cancel") }
                Button(onClick = { confirming = true }, enabled = all.isNotEmpty()) { Text("Apply ${all.size}") }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(Icons.Default.AutoFixHigh, null) },
            title = { Text("Apply ${all.size} changes?") },
            text = {
                Text(
                    "This will make $moves move${if (moves == 1) "" else "s"}, create $creates file${if (creates == 1) "" else "s"} and delete $deletes item${if (deletes == 1) "" else "s"} in $projectName." +
                        (if (isRealFolder) " These are real files on your device." else "") +
                        " Moves and new files can be undone afterwards."
                )
            },
            confirmButton = { TextButton(onClick = { confirming = false; actions.applyWizard() }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Not yet") } }
        )
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun WizardProgress(state: AppUiState, actions: OrganizerActions) {
    val w = state.wizard
    val running = w.phase == WizardPhase.APPLYING
    val listState = rememberLazyListState()
    LaunchedEffect(state.cleanupLog.size) {
        if (state.cleanupLog.isNotEmpty()) listState.animateScrollToItem(state.cleanupLog.size - 1)
    }
    val now = state.analysis?.healthScore

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (running) {
            Text("Applying changes…", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (w.failed == 0) Icons.Default.TaskAlt else Icons.Default.Warning, null, tint = if (w.failed == 0) SuccessGreen else MaterialTheme.colorScheme.primary)
                Text("Cleanup finished", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "${w.applied} change${if (w.applied == 1) "" else "s"} done" + if (w.failed > 0) ", ${w.failed} skipped" else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (w.healthBefore != null && now != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = SuccessGreen.copy(alpha = 0.10f), border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
                    Text("Structure health ${w.healthBefore} → $now", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp))
                }
            }
        }
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.cleanupLog) { line -> LogRow(line) }
        }
        if (!running) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canUndoCleanup) OutlinedButton(onClick = actions.undo, modifier = Modifier.weight(1f)) { Text("Undo") }
                Button(onClick = actions.closeWizard, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

@Composable
internal fun LogRow(line: LogLine) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (line.isError) Icons.Default.ErrorOutline else Icons.Default.Check, null,
            tint = if (line.isError) ErrorRed else SuccessGreen, modifier = Modifier.padding(top = 2.dp).size(15.dp)
        )
        Text(
            line.text, fontFamily = MonoFamily, style = MaterialTheme.typography.labelSmall,
            color = if (line.isError) ErrorRed else MaterialTheme.colorScheme.onSurface
        )
    }
}
