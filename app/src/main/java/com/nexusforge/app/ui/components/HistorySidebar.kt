package com.nexusforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.history.ChatSessionSummary

/**
 * The chat-history drawer: brand header, a prominent New chat button, search (title or project),
 * chats grouped by day with the open one highlighted, and a footer with provider status + settings.
 */
@Composable
fun HistorySidebarContent(
    sessions: List<ChatSessionSummary>,
    providerStatusLabel: String,
    isProviderReady: Boolean,
    onNewChat: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    activeSessionId: String? = null
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        val q = query.trim()
        if (q.isEmpty()) sessions
        else sessions.filter { it.title.contains(q, ignoreCase = true) || it.projectLabel.contains(q, ignoreCase = true) }
    }
    val groups = remember(filtered) { groupByDay(filtered, { it.updatedAt }) }

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NexusMark(size = 40.dp)
                Column {
                    Text("Nexus Forge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("AI coding agent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Text("  New chat")
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search chats or projects") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear search") } },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
                if (groups.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "No chats yet. Start one and it will show up here." else "No chats match \"$query\".",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                for ((label, group) in groups) {
                    item(key = "header-$label") { SessionGroupHeader(label) }
                    items(group, key = { it.id }) { session ->
                        SessionRow(
                            session = session, isActive = session.id == activeSessionId,
                            onOpen = { onOpenSession(session.id) }, onDelete = { onDeleteSession(session.id) }
                        )
                    }
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(providerStatusLabel, isProviderReady, Modifier.weight(1f, fill = false))
                Text("", Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
            }
        }
    }
}
