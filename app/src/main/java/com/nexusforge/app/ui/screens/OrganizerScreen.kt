package com.nexusforge.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.Project
import com.nexusforge.app.data.ProjectSource
import com.nexusforge.app.data.organizer.IssueSource
import com.nexusforge.app.data.organizer.ProjectAnalysis
import com.nexusforge.app.data.organizer.ProjectKind
import com.nexusforge.app.data.organizer.Severity
import com.nexusforge.app.data.organizer.StructureIssue
import com.nexusforge.app.data.organizer.WizardPhase
import com.nexusforge.app.ui.components.ProjectKindBadge
import com.nexusforge.app.ui.components.ThinkingIndicator
import com.nexusforge.app.ui.components.formatBytes
import com.nexusforge.app.ui.components.kindColor
import com.nexusforge.app.ui.theme.ErrorRed
import com.nexusforge.app.ui.theme.MonoFamily
import com.nexusforge.app.ui.theme.SuccessGreen
import com.nexusforge.app.viewmodel.AppUiState
import com.nexusforge.app.viewmodel.OrganizerPhase

/** Everything the Organizer can ask the app to do, bundled so the screen has one parameter. */
class OrganizerActions(
    val selectProject: (Project) -> Unit,
    val rescan: () -> Unit,
    val aiReview: () -> Unit,
    val startWizard: () -> Unit,
    val wizardBegin: () -> Unit,
    val wizardDecide: (Boolean) -> Unit,
    val wizardBack: () -> Unit,
    val wizardToggle: (String) -> Unit,
    val closeWizard: () -> Unit,
    val applyWizard: () -> Unit,
    val undo: () -> Unit,
    val importDump: (String) -> Unit,
    val viewWorkspace: (Project) -> Unit
)

/**
 * Organizer = understand a project, then tidy it.
 *   Overview : size, code-health metrics, language mix, and every structural finding
 *   Cleanup  : the interactive wizard that fixes findings after the user confirms
 *   Import   : turn a pasted source dump into real files
 */
@Composable
fun OrganizerScreen(state: AppUiState, actions: OrganizerActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val project = state.projects.find { it.id == state.organizerProjectId } ?: state.activeProject

    LaunchedEffect(state.wizard.phase) { if (state.wizard.phase != WizardPhase.CLOSED) tab = 1 }

    if (project == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Create or pick a project in the Workspace first.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ProjectSelector(project, state.projects, state.analyzing, actions)
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            listOf("Overview", "Cleanup", "Import").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        when (tab) {
            0 -> OverviewTab(state, actions)
            1 -> CleanupTab(state, project, actions)
            else -> ImportTab(state, project, actions)
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun ProjectSelector(project: Project, projects: List<Project>, analyzing: Boolean, actions: OrganizerActions) {
    var open by remember { mutableStateOf(false) }
    val kind = if (project.kind.isBlank()) ProjectKind.OTHER else ProjectKind.fromKey(project.kind)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().clickable { open = true }
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProjectKindBadge(kind, size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Organizing · ${kind.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (p in projects) {
                    DropdownMenuItem(
                        text = { Text(p.name) },
                        leadingIcon = { ProjectKindBadge(if (p.kind.isBlank()) ProjectKind.OTHER else ProjectKind.fromKey(p.kind), size = 28.dp) },
                        onClick = { open = false; actions.selectProject(p) }
                    )
                }
            }
        }
        IconButton(onClick = actions.rescan, enabled = !analyzing) {
            if (analyzing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, "Scan again")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------------------------

@Composable
private fun OverviewTab(state: AppUiState, actions: OrganizerActions) {
    val analysis = state.analysis
    when {
        analysis == null && state.analysisError != null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(32.dp))
            Text(state.analysisError, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = actions.rescan) { Text("Try again") }
        }
        analysis == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
            Text("Reading the project…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { HealthCard(analysis, state, actions) }
            item { MetricsGrid(analysis) }
            if (analysis.metrics.languages.isNotEmpty()) item { LanguageCard(analysis) }
            if (analysis.metrics.largest.isNotEmpty()) item { LargestFilesCard(analysis) }
            item { Text("Findings", style = MaterialTheme.typography.titleMedium) }
            if (analysis.issues.isEmpty()) item { Text("No structural problems found. This project is tidy.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(analysis.issues, key = { it.id }) { IssueCard(it) }
        }
    }
}

private fun healthLabel(score: Int) = when {
    score >= 90 -> "Excellent"
    score >= 75 -> "Good"
    score >= 50 -> "Needs work"
    else -> "Messy"
}

@Composable
private fun HealthRing(score: Int, size: Dp) {
    val color = when {
        score >= 75 -> SuccessGreen
        score >= 50 -> Color(0xFFFBBF24)
        else -> ErrorRed
    }
    val sweep by animateFloatAsState(score / 100f, tween(700), label = "health")
    val track = MaterialTheme.colorScheme.outline
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("/ 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HealthCard(analysis: ProjectAnalysis, state: AppUiState, actions: OrganizerActions) {
    val score = analysis.healthScore
    val fixable = analysis.fixableIssues.size
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HealthRing(score, 92.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Structure health", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(healthLabel(score), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (analysis.issues.isEmpty()) "No findings" else "${analysis.issues.size} finding${if (analysis.issues.size == 1) "" else "s"} · $fixable fixable",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = actions.startWizard, enabled = fixable > 0, modifier = Modifier.fillMaxWidth()) {
                Icon(if (fixable > 0) Icons.Default.AutoFixHigh else Icons.Default.TaskAlt, null, modifier = Modifier.size(18.dp))
                Text(if (fixable > 0) "  Start cleanup wizard" else "  Nothing to fix")
            }
            OutlinedButton(onClick = actions.aiReview, enabled = !state.aiReviewRunning, modifier = Modifier.fillMaxWidth()) {
                if (state.aiReviewRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Text(if (state.aiReviewRunning) "  Reviewing structure…" else "  Ask AI to review the structure")
            }
            state.aiReviewNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun MetricsGrid(analysis: ProjectAnalysis) {
    val m = analysis.metrics
    val tiles = buildList {
        add(m.files.toString() to "Files")
        add(m.folders.toString() to "Folders")
        add(compact(m.codeLines) to "Lines of code")
        add(m.classes.toString() to "Classes")
        add(m.functions.toString() to "Functions")
        add("${m.testFiles}/${m.sourceFiles}" to "Test / source files")
        if (m.composables > 0) add(m.composables.toString() to "Composables")
        add(m.todos.toString() to "TODO / FIXME")
        add(formatBytes(m.bytes) to "Total size")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in tiles.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in row) Tile(value, label, Modifier.weight(1f))
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        val total = (m.codeLines + m.commentLines).coerceAtLeast(1)
        Text(
            "Comments are ${m.commentLines * 100 / total}% of code lines · deepest file is ${m.maxDepth} folder${if (m.maxDepth == 1) "" else "s"} down",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun compact(n: Int) = if (n >= 10_000) "${"%.1f".format(n / 1000f)}k" else n.toString()

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val LANG_COLORS = listOf(Color(0xFFA78BFA), Color(0xFF60A5FA), Color(0xFFFB923C), Color(0xFF34D399), Color(0xFFF472B6), Color(0xFFFACC15), Color(0xFF94A3B8))

@Composable
private fun LanguageCard(analysis: ProjectAnalysis) {
    val langs = analysis.metrics.languages.take(6)
    val total = langs.sumOf { it.lines }.coerceAtLeast(1)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Languages", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.outline, CircleShape)) {
                langs.forEachIndexed { i, l ->
                    if (l.lines > 0) Box(Modifier.weight(l.lines.toFloat()).fillMaxSize().background(LANG_COLORS[i % LANG_COLORS.size]))
                }
            }
            langs.forEachIndexed { i, l ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(LANG_COLORS[i % LANG_COLORS.size], CircleShape))
                    Text(l.language, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${l.files} files · ${l.lines} lines · ${l.lines * 100 / total}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LargestFilesCard(analysis: ProjectAnalysis) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Largest files", style = MaterialTheme.typography.titleMedium)
            for (f in analysis.metrics.largest) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f.path, fontFamily = MonoFamily, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${f.lines} lines", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: StructureIssue) {
    val tint = if (issue.severity == Severity.WARNING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (issue.severity == Severity.WARNING) Icons.Default.Warning else Icons.Default.Info, null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(issue.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(issue.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    buildString {
                        append(if (issue.fixable) "Auto-fix available (${issue.actions.size})" else "Note only")
                        if (issue.source == IssueSource.AI) append(" · AI suggestion")
                    },
                    style = MaterialTheme.typography.labelSmall, color = if (issue.fixable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cleanup
// ---------------------------------------------------------------------------------------------

@Composable
private fun CleanupTab(state: AppUiState, project: Project, actions: OrganizerActions) {
    if (state.wizard.phase != WizardPhase.CLOSED) {
        CleanupWizard(state, project.source is ProjectSource.AttachedFolder, project.name, actions)
        return
    }
    val analysis = state.analysis
    val fixable = analysis?.fixableIssues?.size ?: 0
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Icon(if (fixable > 0) Icons.Default.AutoFixHigh else Icons.Default.TaskAlt, null, tint = if (fixable > 0) MaterialTheme.colorScheme.primary else SuccessGreen, modifier = Modifier.size(40.dp))
        Text(
            when {
                analysis == null -> "Scan the project first"
                fixable > 0 -> "$fixable fixable issue${if (fixable == 1) "" else "s"}"
                else -> "Nothing to clean up"
            },
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            when {
                analysis == null -> "The wizard needs to read the project before it can suggest changes."
                fixable > 0 -> "Walk through each one, choose what to apply, and confirm before anything changes."
                else -> "The structure looks good. You can still ask the AI for a second opinion from the Overview tab."
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (fixable > 0) Button(onClick = actions.startWizard) { Text("Start cleanup wizard") }
        if (analysis == null) OutlinedButton(onClick = actions.rescan) { Text("Scan now") }
        if (state.canUndoCleanup) OutlinedButton(onClick = actions.undo) { Text("Undo last cleanup") }
    }
}

// ---------------------------------------------------------------------------------------------
// Import (paste a dump -> files)
// ---------------------------------------------------------------------------------------------

@Composable
private fun ImportTab(state: AppUiState, project: Project, actions: OrganizerActions) {
    var dump by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Import a project dump", style = MaterialTheme.typography.titleLarge)
        Text(
            "Paste a whole project's source as one blob, labeled or not. It's split back into real files inside ${project.name}.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedTextField(
                value = dump, onValueChange = { dump = it },
                modifier = Modifier.fillMaxWidth().height(170.dp),
                placeholder = { Text("Paste your project's source here…") }
            )
            IconButton(onClick = { clipboard.getText()?.text?.let { dump = it } }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
            }
        }
        if (dump.isNotEmpty()) Text("${dump.length} characters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { actions.importDump(dump) }, enabled = dump.isNotBlank() && !state.organizerRunning, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.organizerRunning) "Organizing…" else "Organize into files")
        }
        when (state.organizerPhase) {
            OrganizerPhase.THINKING -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThinkingIndicator()
                if (state.organizerThinkingChars > 0) Text("Restructuring… (${state.organizerThinkingChars} characters so far)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OrganizerPhase.WRITING, OrganizerPhase.FAILED -> ImportLog(state, Modifier.weight(1f))
            OrganizerPhase.DONE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ImportLog(state, Modifier.weight(1f, fill = false))
                Surface(color = SuccessGreen.copy(alpha = 0.10f), border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.TaskAlt, null, tint = SuccessGreen)
                        Text(state.organizerResultSummary ?: "Done.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { actions.viewWorkspace(project) }) { Text("View") }
                    }
                }
            }
            OrganizerPhase.IDLE -> Unit
        }
    }
}

@Composable
private fun ImportLog(state: AppUiState, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(state.organizerLog) { line -> LogRow(line) }
    }
}
