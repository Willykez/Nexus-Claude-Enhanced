package com.nexusforge.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.templates.ProjectTemplate
import com.nexusforge.app.data.templates.ProjectTemplates
import com.nexusforge.app.ui.theme.MonoFamily

/** Pick a name and a template; the template's files are scaffolded into the new project. */
@Composable
fun NewProjectSheet(onCreate: (String, ProjectTemplate) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(ProjectTemplates.COMPOSE_APP) }
    val effectiveName = name.trim().ifBlank { selected.title }
    val preview = remember(selected, effectiveName) { selected.files(effectiveName).keys.toList() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("New project", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) },
                label = { Text("Project name") }, placeholder = { Text(selected.title) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Text("Template", style = MaterialTheme.typography.titleMedium)
            for (template in ProjectTemplates.all) {
                val isSelected = template.id == selected.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().clickable { selected = template }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProjectKindBadge(template.kind, size = 42.dp)
                        Column(Modifier.weight(1f)) {
                            Text(template.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(template.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                if (preview.isEmpty()) "Creates an empty project." else "Creates ${preview.size} files:",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (preview.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (path in preview.take(10)) Text(path, fontFamily = MonoFamily, style = MaterialTheme.typography.labelSmall)
                        if (preview.size > 10) Text("… and ${preview.size - 10} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Button(onClick = { onCreate(effectiveName, selected) }, modifier = Modifier.fillMaxWidth()) {
                Text("Create project")
            }
        }
    }
}
