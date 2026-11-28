package com.nexusforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.history.ChatSessionSummary
import com.nexusforge.app.ui.components.SessionGroupHeader
import com.nexusforge.app.ui.components.SessionRow
import com.nexusforge.app.ui.components.groupByDay

@Composable
fun HistoryScreen(
    sessions: List<ChatSessionSummary>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    activeSessionId: String? = null
) {
    if (sessions.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Text("No chats yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text("Your conversations are saved here automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        val q = query.trim()
        if (q.isEmpty()) sessions else sessions.filter { it.title.contains(q, true) || it.projectLabel.contains(q, true) }
    }
    val groups = remember(filtered) { groupByDay(filtered, { it.updatedAt }) }

    Column(Modifier.fillMaxSize()) {
        Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search chats or projects") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear search") } },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(Modifier.fillMaxSize()) {
            for ((label, group) in groups) {
                item(key = "header-$label") { SessionGroupHeader(label) }
                items(group, key = { it.id }) { s ->
                    SessionRow(s, isActive = s.id == activeSessionId, onOpen = { onOpen(s.id) }, onDelete = { onDelete(s.id) })
                }
            }
        }
    }
}
