package com.nexusforge.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.AppSettings
import com.nexusforge.app.data.Project
import com.nexusforge.app.data.ProjectSource
import com.nexusforge.app.data.ProviderProfile
import com.nexusforge.app.data.SettingsStore
import com.nexusforge.app.data.ThemeMode
import com.nexusforge.app.ui.components.NexusMark
import com.nexusforge.app.ui.components.StatusPill
import com.nexusforge.app.ui.theme.SuccessGreen

@Composable
fun SettingsScreen(
    settings: AppSettings?,
    activeProject: Project?,
    providerProfiles: List<ProviderProfile>,
    activeProviderProfileId: String?,
    onSaveCapabilities: (Boolean, Boolean) -> Unit,
    onSaveGeneration: (Float, Int) -> Unit,
    onOpenProjectPicker: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSelectProvider: (ProviderProfile) -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (ProviderProfile) -> Unit,
    onDeleteProvider: (ProviderProfile) -> Unit
) {
    if (settings == null) return

    var filesEnabled by remember(settings.capabilities) { mutableStateOf(settings.capabilities.fileReadWriteEnabled) }
    var zipEnabled by remember(settings.capabilities) { mutableStateOf(settings.capabilities.zipEnabled) }
    var temperature by remember(settings.temperature) { mutableStateOf(settings.temperature) }
    var maxTokens by remember(settings.maxOutputTokens) { mutableStateOf(settings.maxOutputTokens.toString()) }
    val activeProvider = providerProfiles.find { it.id == activeProviderProfileId }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // ---- header
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NexusMark(size = 52.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Nexus Forge", style = MaterialTheme.typography.titleLarge)
                        Text("AI coding agent. Your keys and projects stay on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusPill(
                            activeProvider?.let { "${it.name} · ${it.model}" } ?: "No provider selected",
                            ready = activeProvider != null && (activeProvider.apiKey.isNotBlank() || SettingsStore.isKeylessLocal(activeProvider.baseUrl))
                        )
                    }
                }
            }
        }

        // ---- appearance
        item {
            SettingsCard("Appearance", Icons.Default.Palette) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeTile("System", Icons.Default.BrightnessAuto, settings.themeMode == ThemeMode.SYSTEM, Modifier.weight(1f)) { onSetThemeMode(ThemeMode.SYSTEM) }
                    ThemeTile("Light", Icons.Default.LightMode, settings.themeMode == ThemeMode.LIGHT, Modifier.weight(1f)) { onSetThemeMode(ThemeMode.LIGHT) }
                    ThemeTile("Dark", Icons.Default.DarkMode, settings.themeMode == ThemeMode.DARK, Modifier.weight(1f)) { onSetThemeMode(ThemeMode.DARK) }
                }
            }
        }

        // ---- project
        item {
            SettingsCard("Active project", Icons.Default.Folder, "The project the chat reads and writes. Browse and manage all of them in the Workspace tab.") {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenProjectPicker).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(activeProject?.name ?: "No project selected", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (activeProject?.source) {
                                is ProjectSource.AttachedFolder -> "Attached folder"
                                is ProjectSource.Sandbox -> "Sandbox"
                                else -> "Tap to choose a project"
                            },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Switch project", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ---- providers
        item {
            SettingsCard("Providers", Icons.Default.Key, "Every key is saved in full, so switching providers is a tap, never a re-paste.") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (profile in providerProfiles) {
                        ProviderRow(
                            profile = profile, isActive = profile.id == activeProviderProfileId,
                            onClick = { onSelectProvider(profile) }, onEdit = { onEditProvider(profile) }, onDelete = { onDeleteProvider(profile) }
                        )
                    }
                    OutlinedButton(onClick = onAddProvider, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Text("  Add a provider")
                    }
                }
            }
        }

        // ---- capabilities
        item {
            SettingsCard("Capabilities", Icons.Default.Security) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow("File read / write", "Turn off to make the agent chat-only. It can talk, but never touch a file.", filesEnabled) {
                        filesEnabled = it; onSaveCapabilities(it, zipEnabled)
                    }
                    ToggleRow("ZIP export", "Lets the agent package the project into an archive you can share.", zipEnabled) {
                        zipEnabled = it; onSaveCapabilities(filesEnabled, it)
                    }
                }
            }
        }

        // ---- generation
        item {
            SettingsCard("Generation", Icons.Default.Tune) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Temperature", style = MaterialTheme.typography.bodyLarge)
                            Text("Lower is more precise, higher is more creative.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                            Text("%.2f".format(temperature), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    Slider(
                        value = temperature, onValueChange = { temperature = it }, valueRange = 0f..1f,
                        onValueChangeFinished = { onSaveGeneration(temperature, maxTokens.toIntOrNull() ?: 8192) }
                    )
                    OutlinedTextField(
                        maxTokens, { maxTokens = it.filter(Char::isDigit) },
                        label = { Text("Max output tokens") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { onSaveGeneration(temperature, maxTokens.toIntOrNull() ?: 8192) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("Save generation settings")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingsCard(title: String, icon: ImageVector, subtitle: String? = null, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ThemeTile(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) primary else MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProviderRow(profile: ProviderProfile, isActive: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val isReady = profile.apiKey.isNotBlank() || SettingsStore.isKeylessLocal(profile.baseUrl)
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Bolt, null, tint = if (isActive) primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isActive) FontWeight.SemiBold else null, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isActive) Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = primary)
                }
                Text(
                    "${SettingsStore.providerLabel(profile.baseUrl)} · ${profile.model}" + if (!isReady) " · needs a key" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (isReady) Box(Modifier.size(8.dp).background(SuccessGreen, CircleShape))
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Provider actions", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}
