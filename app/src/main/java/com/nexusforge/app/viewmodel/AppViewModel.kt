package com.nexusforge.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusforge.app.data.AiClient
import com.nexusforge.app.data.AppSettings
import com.nexusforge.app.data.AppTab
import com.nexusforge.app.data.CapabilityFlags
import com.nexusforge.app.data.ChatMessage
import com.nexusforge.app.data.ChatRequest
import com.nexusforge.app.data.ChatResponseChunk
import com.nexusforge.app.data.FileNode
import com.nexusforge.app.data.FunctionCall
import com.nexusforge.app.data.Project
import com.nexusforge.app.data.PartialJson
import com.nexusforge.app.data.ProjectSource
import com.nexusforge.app.data.ProjectStore
import com.nexusforge.app.data.ProviderProfile
import com.nexusforge.app.data.ProviderProfileStore
import com.nexusforge.app.data.SettingsStore
import com.nexusforge.app.data.ThemeMode
import com.nexusforge.app.data.ToolCall
import com.nexusforge.app.data.ToolChip
import com.nexusforge.app.data.ToolRegistry
import com.nexusforge.app.data.ToolStatus
import com.nexusforge.app.data.UiChatMessage
import com.nexusforge.app.data.WorkspaceStats
import com.nexusforge.app.data.history.ChatHistoryStore
import com.nexusforge.app.data.history.ChatSession
import com.nexusforge.app.data.history.ChatSessionSummary
import com.nexusforge.app.data.history.PersistedMessage
import com.nexusforge.app.data.history.PersistedToolChip
import com.nexusforge.app.data.organizer.LogLine
import com.nexusforge.app.data.organizer.ProjectDumpEngine
import com.nexusforge.app.data.workspace.RealFolderWorkspaceEngine
import com.nexusforge.app.data.workspace.SandboxWorkspaceEngine
import com.nexusforge.app.data.workspace.WorkspaceEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.util.UUID

// A real multi-file build can easily need more than a handful of tool calls (list, then write
// each file, then maybe a zip) — 8 was cutting off legitimate work partway through and forcing
// the user to say "continue". This is a safety ceiling against a genuinely stuck loop, not a
// budget for normal work, so it can afford to be generous.
private const val MAX_AGENT_ROUNDS = 25

data class AppUiState(
    val settingsLoaded: Boolean = false,
    val settings: AppSettings? = null,
    val currentTab: AppTab = AppTab.CHAT,

    // project
    val activeProject: Project? = null,
    val projects: List<Project> = emptyList(),
    val showProjectPicker: Boolean = false,

    // provider profiles — see "Additional fix" thread: fully-saved credentials, switch by name
    val activeProviderProfile: ProviderProfile? = null,
    val providerProfiles: List<ProviderProfile> = emptyList(),
    val showProviderPicker: Boolean = false,
    val providerFormEditing: ProviderProfile? = null,
    val showProviderForm: Boolean = false,

    // chat
    val sessionId: String = UUID.randomUUID().toString(),
    val messages: List<UiChatMessage> = emptyList(),
    val isAgentRunning: Boolean = false,
    val statusLabel: String = "Idle",
    val error: String? = null,

    // workspace
    val fileTree: FileNode = FileNode("", "project", true, 0),
    val projectFilePaths: List<String> = emptyList(), // flattened, for @ mentions
    val highlightedPath: String? = null,
    val selectedFile: String? = null,
    val selectedFileContent: String = "",
    val workspaceStats: WorkspaceStats? = null,
    val lastExportedZip: String? = null,
    val showFileTreeSheet: Boolean = false,

    // history
    val sessionSummaries: List<ChatSessionSummary> = emptyList(),
    val showHistorySidebar: Boolean = false,

    // organizer
    val organizerLog: List<LogLine> = emptyList(),
    val organizerRunning: Boolean = false,
    val organizerPhase: OrganizerPhase = OrganizerPhase.IDLE,
    val organizerThinkingChars: Int = 0,
    val organizerResultSummary: String? = null
)

enum class OrganizerPhase { IDLE, THINKING, WRITING, DONE, FAILED }

private fun flattenFiles(node: FileNode, acc: MutableList<String> = mutableListOf()): List<String> {
    if (!node.isDirectory && node.path.isNotBlank()) acc.add(node.path)
    node.children.forEach { flattenFiles(it, acc) }
    return acc
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val historyStore = ChatHistoryStore(application)
    private val projectStore = ProjectStore(application)
    private val providerProfileStore = ProviderProfileStore(application)
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var workspaceEngine: WorkspaceEngine? = null
    private var apiMessages = mutableListOf<ChatMessage>()
    private var agentJob: Job? = null
    private var runGeneration = 0

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                _state.update { it.copy(settingsLoaded = true, settings = settings) }
            }
        }
        viewModelScope.launch { initializeActiveProject() }
        viewModelScope.launch { initializeActiveProvider() }
        refreshSessions()
    }

    private suspend fun initializeActiveProject() {
        val projects = projectStore.list()
        _state.update { it.copy(projects = projects) }
        if (projects.isEmpty()) {
            val created = projectStore.createSandbox("My Sandbox")
            selectProject(created)
            return
        }
        // Wait one tick for settingsFlow's first emission so lastActiveProjectId is available.
        val lastId = _state.value.settings?.lastActiveProjectId
        val target = projects.find { it.id == lastId } ?: projects.first()
        selectProject(target, persist = false)
    }

    private suspend fun initializeActiveProvider() {
        val profiles = providerProfileStore.list()
        _state.update { it.copy(providerProfiles = profiles) }
        if (profiles.isEmpty()) return // no default auto-created — nothing to guess a key for
        val lastId = _state.value.settings?.lastActiveProviderProfileId
        val target = profiles.find { it.id == lastId } ?: profiles.first()
        _state.update { it.copy(activeProviderProfile = target) }
    }

    private fun engineFor(source: ProjectSource): WorkspaceEngine = when (source) {
        is ProjectSource.Sandbox -> SandboxWorkspaceEngine(getApplication(), source.projectId)
        is ProjectSource.AttachedFolder -> RealFolderWorkspaceEngine(getApplication(), Uri.parse(source.treeUri), source.displayName)
    }

    // ---------- Navigation ----------

    fun selectTab(tab: AppTab) = _state.update { it.copy(currentTab = tab) }

    fun openHistorySidebar() = _state.update { it.copy(showHistorySidebar = true) }
    fun dismissHistorySidebar() = _state.update { it.copy(showHistorySidebar = false) }

    fun openFileTreeSheet() = _state.update { it.copy(showFileTreeSheet = true) }
    fun dismissFileTreeSheet() = _state.update { it.copy(showFileTreeSheet = false) }

    // ---------- Projects ----------

    fun openProjectPicker() {
        viewModelScope.launch { _state.update { it.copy(projects = projectStore.list(), showProjectPicker = true) } }
    }

    fun dismissProjectPicker() = _state.update { it.copy(showProjectPicker = false) }

    fun selectProject(project: Project, persist: Boolean = true) {
        workspaceEngine = engineFor(project.source)
        _state.update {
            it.copy(
                activeProject = project, showProjectPicker = false,
                selectedFile = null, selectedFileContent = "", highlightedPath = null
            )
        }
        refreshWorkspace()
        if (persist) {
            viewModelScope.launch {
                settingsStore.saveLastActiveProject(project.id)
                projectStore.touch(project.id)
                _state.update { it.copy(projects = projectStore.list()) }
            }
        }
    }

    fun createSandboxProject(name: String) {
        viewModelScope.launch {
            val project = projectStore.createSandbox(name)
            selectProject(project)
        }
    }

    fun attachFolderAsProject(treeUri: Uri, displayName: String) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        viewModelScope.launch {
            val project = projectStore.attachFolder(treeUri.toString(), displayName)
            selectProject(project)
            _snackbar.emit("Attached folder: $displayName")
        }
    }

    fun renameProject(project: Project, newName: String) {
        viewModelScope.launch {
            projectStore.rename(project.id, newName)
            val projects = projectStore.list()
            _state.update { it.copy(projects = projects) }
            if (_state.value.activeProject?.id == project.id) {
                _state.update { it.copy(activeProject = projects.find { p -> p.id == project.id }) }
            }
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectStore.delete(project.id)
            val projects = projectStore.list()
            _state.update { it.copy(projects = projects) }
            if (_state.value.activeProject?.id == project.id) {
                val replacement = projects.firstOrNull() ?: projectStore.createSandbox("My Sandbox")
                selectProject(replacement)
            }
            _snackbar.emit("Deleted \"${project.name}\"")
        }
    }

    // ---------- Provider profiles ----------

    fun openProviderPicker() {
        viewModelScope.launch { _state.update { it.copy(providerProfiles = providerProfileStore.list(), showProviderPicker = true) } }
    }

    fun dismissProviderPicker() = _state.update { it.copy(showProviderPicker = false) }

    fun selectProviderProfile(profile: ProviderProfile) {
        _state.update { it.copy(activeProviderProfile = profile, showProviderPicker = false) }
        viewModelScope.launch {
            settingsStore.saveLastActiveProviderProfile(profile.id)
            providerProfileStore.touch(profile.id)
            _state.update { it.copy(providerProfiles = providerProfileStore.list()) }
        }
    }

    fun requestAddProvider() = _state.update { it.copy(providerFormEditing = null, showProviderForm = true, showProviderPicker = false) }
    fun requestEditProvider(profile: ProviderProfile) = _state.update { it.copy(providerFormEditing = profile, showProviderForm = true, showProviderPicker = false) }
    fun dismissProviderForm() = _state.update { it.copy(showProviderForm = false) }

    fun saveProviderProfile(name: String, baseUrl: String, apiKey: String, model: String) {
        if (baseUrl.isBlank() || model.isBlank()) {
            viewModelScope.launch { _snackbar.emit("Base URL and model are required") }
            return
        }
        val editing = _state.value.providerFormEditing
        viewModelScope.launch {
            if (editing != null) {
                providerProfileStore.update(editing.id, name, baseUrl, apiKey, model)
            } else {
                val created = providerProfileStore.create(name, baseUrl, apiKey, model)
                selectProviderProfile(created)
            }
            val profiles = providerProfileStore.list()
            _state.update {
                it.copy(
                    providerProfiles = profiles, showProviderForm = false,
                    activeProviderProfile = if (editing != null && it.activeProviderProfile?.id == editing.id)
                        profiles.find { p -> p.id == editing.id } else it.activeProviderProfile
                )
            }
            _snackbar.emit(if (editing != null) "Provider updated" else "Provider saved and activated")
        }
    }

    fun deleteProviderProfile(profile: ProviderProfile) {
        viewModelScope.launch {
            providerProfileStore.delete(profile.id)
            val profiles = providerProfileStore.list()
            _state.update {
                it.copy(
                    providerProfiles = profiles,
                    activeProviderProfile = if (it.activeProviderProfile?.id == profile.id) profiles.firstOrNull() else it.activeProviderProfile
                )
            }
            _snackbar.emit("Deleted \"${profile.name}\"")
        }
    }

    // ---------- Chat ----------

    /** Reads any @path mentions that match a real project file and folds their content into the
     *  outgoing prompt, so the model genuinely has the file rather than just seeing its name. */
    private suspend fun expandMentions(rawText: String, engine: WorkspaceEngine): String {
        val knownPaths = _state.value.projectFilePaths.toSet()
        if (knownPaths.isEmpty()) return rawText
        val mentioned = Regex("@([\\w\\-./]+)").findAll(rawText).map { it.groupValues[1] }.distinct()
            .filter { it in knownPaths }.toList()
        if (mentioned.isEmpty()) return rawText
        val attached = StringBuilder()
        for (path in mentioned) {
            val content = runCatching { engine.readFile(path) }.getOrNull() ?: continue
            attached.append("\n\n[Attached: $path]\n```\n$content\n```")
        }
        return if (attached.isEmpty()) rawText else rawText + attached
    }

    fun sendMessage(rawText: String) {
        val prompt = rawText.trim()
        if (prompt.isBlank() || _state.value.isAgentRunning) return
        val engine = workspaceEngine
        val project = _state.value.activeProject
        if (engine == null || project == null) {
            viewModelScope.launch { _snackbar.emit("Pick a project first.") }
            openProjectPicker()
            return
        }
        val providerProfile = _state.value.activeProviderProfile
        if (providerProfile == null) {
            viewModelScope.launch { _snackbar.emit("Add or pick a provider first.") }
            openProviderPicker()
            return
        }

        _state.update { it.copy(messages = it.messages + UiChatMessage(role = "user", text = prompt), error = null) }

        agentJob = viewModelScope.launch {
            val expandedPrompt = expandMentions(prompt, engine)
            if (apiMessages.isEmpty()) {
                val settings = _state.value.settings
                apiMessages.add(ChatMessage(role = "system", content = ToolRegistry.systemPrompt(
                    settings?.capabilities ?: CapabilityFlags(), project.source
                )))
            }
            apiMessages.add(ChatMessage(role = "user", content = expandedPrompt))
            runAgentLoop(engine, providerProfile)
        }
    }

    fun stopAgent() {
        agentJob?.cancel()
        agentJob = null
        _state.update { it.copy(isAgentRunning = false, statusLabel = "Stopped") }
    }

    /**
     * Re-runs the last turn from the user's most recent message. Drops everything the assistant
     * produced after it (UI and model history) so the model starts that turn fresh. Files written
     * by the earlier attempt stay in the project, and the model sees them through its tools.
     * Uses whichever provider is active now, so switching provider then retrying works.
     */
    fun retryLastTurn() {
        if (_state.value.isAgentRunning) return
        val engine = workspaceEngine
        val providerProfile = _state.value.activeProviderProfile
        if (engine == null || _state.value.activeProject == null) {
            viewModelScope.launch { _snackbar.emit("Pick a project first.") }
            openProjectPicker()
            return
        }
        if (providerProfile == null) {
            viewModelScope.launch { _snackbar.emit("Add or pick a provider first.") }
            openProviderPicker()
            return
        }
        val uiIdx = _state.value.messages.indexOfLast { it.role == "user" }
        val apiIdx = apiMessages.indexOfLast { it.role == "user" }
        if (uiIdx < 0 || apiIdx < 0) return
        while (apiMessages.size > apiIdx + 1) apiMessages.removeAt(apiMessages.lastIndex)
        _state.update { it.copy(messages = it.messages.take(uiIdx + 1), error = null) }
        agentJob = viewModelScope.launch { runAgentLoop(engine, providerProfile) }
    }

    fun newChat() {
        persistCurrentSessionIfNeeded()
        apiMessages = mutableListOf()
        _state.update {
            it.copy(sessionId = UUID.randomUUID().toString(), messages = emptyList(), statusLabel = "Idle", error = null)
        }
    }

    fun loadSession(id: String) {
        viewModelScope.launch {
            val session = historyStore.loadSession(id) ?: run {
                _snackbar.emit("That conversation is no longer available.")
                return@launch
            }
            apiMessages = session.apiMessages.toMutableList()
            val restored = session.renderedMessages.map { pm ->
                var text = pm.text
                var errorMessage = pm.errorMessage
                if (pm.isError && errorMessage == null && text.contains("⚠")) {
                    // Older sessions appended the error to the text; split it back out.
                    errorMessage = text.substringAfter("⚠").trim().ifBlank { null }
                    text = text.substringBefore("⚠").trim()
                    if (text == "Connection error.") text = ""
                }
                UiChatMessage(
                    role = pm.role, text = text, isError = pm.isError, errorMessage = errorMessage,
                    toolChips = if (pm.toolChips.isNotEmpty()) pm.toolChips.map { pc ->
                        val st = runCatching { ToolStatus.valueOf(pc.status) }.getOrDefault(ToolStatus.DONE)
                        ToolChip(
                            toolName = pc.name, path = pc.path,
                            status = if (st == ToolStatus.RUNNING) ToolStatus.DONE else st,
                            content = pc.content, isNewFile = pc.isNewFile, detail = pc.detail
                        )
                    } else pm.toolSummaries.map { summary ->
                        ToolChip(
                            toolName = summary.substringBefore(' '),
                            path = summary.substringAfter(' ', "").ifBlank { null },
                            status = ToolStatus.DONE
                        )
                    }
                )
            }
            val project = session.projectId?.let { projectStore.get(it) }
            if (project != null && project.id != _state.value.activeProject?.id) {
                selectProject(project, persist = false)
            } else if (session.projectId != null && project == null) {
                _snackbar.emit("This conversation's original project is gone — staying on the current one.")
            }
            _state.update {
                it.copy(sessionId = session.id, messages = restored, currentTab = AppTab.CHAT, showHistorySidebar = false)
            }
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            historyStore.deleteSession(id)
            refreshSessions()
            if (_state.value.sessionId == id) newChat()
        }
    }

    private fun refreshSessions() {
        viewModelScope.launch {
            _state.update { it.copy(sessionSummaries = historyStore.listSessions()) }
        }
    }

    private fun persistCurrentSessionIfNeeded() {
        val current = _state.value
        val firstUser = current.messages.firstOrNull { it.role == "user" }?.text
        if (firstUser == null) return // nothing to save
        val providerProfile = current.activeProviderProfile
        val project = current.activeProject
        viewModelScope.launch {
            historyStore.saveSession(
                ChatSession(
                    id = current.sessionId,
                    title = ChatHistoryStore.titleFrom(firstUser),
                    providerLabel = providerProfile?.name ?: "—",
                    projectLabel = project?.name ?: "Unknown project",
                    projectId = project?.id,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    apiMessages = apiMessages.toList(),
                    renderedMessages = current.messages.map { m ->
                        PersistedMessage(
                            role = m.role, text = m.text, isError = m.isError,
                            toolSummaries = m.toolChips.map { chip -> "${chip.toolName}${chip.path?.let { " $it" } ?: ""}" },
                            toolChips = m.toolChips.map { c ->
                                PersistedToolChip(
                                    name = c.toolName, path = c.path, status = c.status.name,
                                    content = c.content, isNewFile = c.isNewFile, detail = c.detail
                                )
                            },
                            errorMessage = m.errorMessage
                        )
                    }
                )
            )
            refreshSessions()
        }
    }

    private suspend fun runAgentLoop(engine: WorkspaceEngine, providerProfile: ProviderProfile) {
        val settings = _state.value.settings ?: return
        val client = AiClient(providerProfile.toConfig())
        val generation = ++runGeneration
        val runMessageIds = mutableSetOf<String>()
        var stopped = false
        _state.update { it.copy(isAgentRunning = true, statusLabel = "Thinking…") }

        try {
            var round = 0
            while (round < MAX_AGENT_ROUNDS) {
                round++
                val assistant = UiChatMessage(role = "assistant", isStreaming = true)
                runMessageIds += assistant.id
                _state.update { it.copy(messages = it.messages + assistant) }

                val text = StringBuilder()
                val calls = linkedMapOf<Int, MutablePendingCall>()
                var streamError: String? = null
                val knownPaths = _state.value.projectFilePaths.toSet()
                var lastChipPublish = 0L

                // Rebuilds the chips from the streamed arguments so far. For write_file this is
                // what carries the code that's being written, so the UI can show it live. Chips
                // are rebuilt from `calls` (keyed by the stream's own index) every time, because a
                // call's name can arrive blank in its first chunk and fill in a moment later.
                fun publishChips(streamActive: Boolean) {
                    val ordered = calls.entries.sortedBy { it.key }
                    if (ordered.isEmpty()) return
                    val lastKey = ordered.last().key
                    val live = ordered.map { (key, c) ->
                        val args = c.arguments.toString()
                        val name = c.name.ifBlank { "unknown_tool" }
                        val path = PartialJson.string(args, "path") ?: PartialJson.string(args, "archiveName")
                        ToolChip(
                            toolName = name,
                            path = path,
                            status = ToolStatus.RUNNING,
                            callKey = key,
                            content = if (name == ToolRegistry.WRITE_FILE) PartialJson.string(args, "content") else null,
                            isNewFile = if (name == ToolRegistry.WRITE_FILE && path != null) path !in knownPaths else null,
                            isStreaming = streamActive && key == lastKey
                        )
                    }
                    updateMessage(assistant.id) { it.copy(toolChips = live) }
                }

                val request = ChatRequest(
                    model = providerProfile.model,
                    messages = apiMessages.toList(),
                    tools = ToolRegistry.activeTools(settings.capabilities),
                    stream = true,
                    temperature = settings.temperature.toDouble(),
                    maxTokens = settings.maxOutputTokens
                )

                try {
                    client.streamChatCompletion(request).collect { raw ->
                        val chunk = lenientJson.decodeFromString(ChatResponseChunk.serializer(), raw)
                        val delta = chunk.choices.firstOrNull()?.delta ?: return@collect
                        delta.content?.let { token ->
                            text.append(token)
                            updateMessage(assistant.id) { it.copy(text = text.toString()) }
                        }
                        delta.toolCalls?.forEach { part ->
                            val holder = calls.getOrPut(part.index) { MutablePendingCall() }
                            // Only ever accept a non-blank id/name: some OpenAI-compatible servers
                            // send continuation chunks with an EXPLICIT empty string, which would
                            // otherwise wipe out the value captured from the first chunk.
                            part.id?.takeIf { it.isNotBlank() }?.let { holder.id = it }
                            part.function?.name?.takeIf { it.isNotBlank() }?.let { holder.name = it }
                            part.function?.arguments?.let { holder.arguments.append(it) }
                        }
                        if (!delta.toolCalls.isNullOrEmpty()) {
                            // Decoding the growing JSON on every token would be wasteful; ~20 fps is plenty.
                            val now = System.currentTimeMillis()
                            if (now - lastChipPublish >= 50) {
                                lastChipPublish = now
                                publishChips(true)
                            }
                            if (_state.value.statusLabel != "Running…") {
                                _state.update { it.copy(statusLabel = "Running…") }
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e // stopAgent() cancelling this job must actually stop it, not surface as a stream error
                } catch (e: Exception) {
                    streamError = e.message ?: "Connection error."
                }

                publishChips(false) // final, complete content for every call
                updateMessage(assistant.id) { it.copy(isStreaming = false) }

                if (streamError != null) {
                    val message: String = streamError
                    updateMessage(assistant.id) { it.copy(isError = true, errorMessage = message) }
                    _state.update { it.copy(statusLabel = "Error", error = message) }
                    _snackbar.emit(message)
                    break
                }

                // A blank id or name means the provider's streamed tool-call chunks were malformed
                // (a real quirk of some endpoints), NOT that no tool was called. Falling back to a
                // synthetic id/name keeps the call in the loop so it either works or comes back as
                // a clear "unsupported tool" error the model can see and recover from.
                val keyed = calls.entries.sortedBy { it.key }.mapIndexed { position, (index, c) ->
                    index to ToolCall(
                        id = c.id.ifBlank { "call_${index}_$position" },
                        type = "function",
                        function = FunctionCall(c.name.ifBlank { "unknown_tool" }, c.arguments.toString())
                    )
                }
                val toolCalls = keyed.map { it.second }

                apiMessages.add(ChatMessage(role = "assistant", content = text.toString().ifBlank { null }, toolCalls = toolCalls.ifEmpty { null }))

                if (toolCalls.isEmpty()) {
                    _state.update { it.copy(statusLabel = "Idle") }
                    break
                }

                for ((key, call) in keyed) {
                    val result = executeTool(engine, call, settings.capabilities)
                    apiMessages.add(ChatMessage(role = "tool", content = result, toolCallId = call.id, name = call.function.name))
                    val failed = result.startsWith("Error")
                    val detail = when {
                        failed -> result.removePrefix("Error:").trim().take(300)
                        call.function.name == ToolRegistry.ZIP_PROJECT -> result
                        else -> null
                    }
                    updateMessage(assistant.id) { msg ->
                        msg.copy(toolChips = msg.toolChips.map { chip ->
                            if (chip.callKey == key) chip.copy(
                                status = if (failed) ToolStatus.ERROR else ToolStatus.DONE,
                                isStreaming = false,
                                detail = detail
                            ) else chip
                        })
                    }
                }
                refreshWorkspace()

                if (round == MAX_AGENT_ROUNDS) {
                    updateMessage(assistant.id) { it.copy(text = it.text.ifBlank { "" } + "\n\n_I've hit my step limit for this turn and stopped rather than keep going — tell me what's still missing and I'll continue._") }
                    _state.update { it.copy(statusLabel = "Stopped — step limit reached") }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            stopped = true
            throw e
        } finally {
            client.close()
            // Nothing from this run may stay "streaming" or "running" — a cancelled run used to
            // leave a spinner and a live code window on screen forever.
            finalizeRunMessages(runMessageIds, stopped)
            // A newer run may already be going (stop, then immediately retry/send); don't clobber it.
            if (generation == runGeneration) {
                _state.update { it.copy(isAgentRunning = false) }
                if (_state.value.statusLabel.startsWith("Running") || _state.value.statusLabel == "Thinking…") {
                    _state.update { it.copy(statusLabel = "Idle") }
                }
                agentJob = null
                persistCurrentSessionIfNeeded()
            }
        }
    }

    private fun finalizeRunMessages(ids: Set<String>, stopped: Boolean) {
        if (ids.isEmpty()) return
        _state.update { state ->
            state.copy(messages = state.messages.map { m ->
                if (m.id !in ids) m else {
                    val hadActivity = m.isStreaming || m.toolChips.any { it.status == ToolStatus.RUNNING }
                    m.copy(
                        isStreaming = false,
                        isStopped = m.isStopped || (stopped && hadActivity),
                        toolChips = m.toolChips.map { c ->
                            if (c.status == ToolStatus.RUNNING) c.copy(
                                status = ToolStatus.ERROR,
                                isStreaming = false,
                                detail = c.detail ?: if (stopped) "Stopped" else "Interrupted"
                            ) else c
                        }
                    )
                }
            })
        }
    }

    private suspend fun executeTool(engine: WorkspaceEngine, call: ToolCall, flags: CapabilityFlags): String {
        val args = runCatching { JSONObject(call.function.arguments.ifBlank { "{}" }) }.getOrNull()
        return try {
            when (call.function.name) {
                ToolRegistry.READ_FILE -> {
                    if (!flags.fileReadWriteEnabled) return "Error: file access is disabled in Settings capability toggles"
                    engine.readFile(args?.optString("path").orEmpty())
                }
                ToolRegistry.WRITE_FILE -> {
                    if (!flags.fileReadWriteEnabled) return "Error: file access is disabled in Settings capability toggles"
                    val path = args?.optString("path").orEmpty()
                    val content = args?.optString("content").orEmpty()
                    val result = engine.writeFile(path, content)
                    _state.update { it.copy(highlightedPath = path) }
                    result
                }
                ToolRegistry.LIST_FILES -> engine.listFilesAsText(args?.optString("path").orEmpty())
                ToolRegistry.ZIP_PROJECT -> {
                    if (!flags.zipEnabled) return "Error: ZIP export is disabled in Settings capability toggles"
                    val name = args?.optString("archiveName")?.ifBlank { null } ?: "workspace"
                    val zip = engine.zipProject(name)
                    _state.update { it.copy(lastExportedZip = zip.absolutePath) }
                    "Archive created at ${zip.name} (${zip.length()} bytes)"
                }
                else -> "Error: unsupported tool ${call.function.name}"
            }
        } catch (e: Exception) {
            "Error executing ${call.function.name}: ${e.message}"
        }
    }

    private fun updateMessage(id: String, transform: (UiChatMessage) -> UiChatMessage) {
        _state.update { state -> state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it }) }
    }

    private class MutablePendingCall {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    // ---------- Workspace ----------

    fun refreshWorkspace() {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            val tree = runCatching { engine.fileTree() }.getOrNull()
            val stats = runCatching { engine.stats() }.getOrNull()
            if (tree != null) _state.update { it.copy(fileTree = tree, workspaceStats = stats, projectFilePaths = flattenFiles(tree)) }
        }
    }

    fun openFile(path: String) {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            val content = runCatching { engine.readFile(path) }.getOrElse { "Error reading file: ${it.message}" }
            _state.update { it.copy(selectedFile = path, selectedFileContent = content) }
        }
    }

    fun closeFilePreview() {
        _state.update { it.copy(selectedFile = null, selectedFileContent = "") }
    }

    fun renameFile(path: String, newName: String) {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            val result = runCatching { engine.renameFile(path, newName) }.getOrElse { "Error: ${it.message}" }
            _snackbar.emit(result)
            refreshWorkspace()
        }
    }

    fun deleteFile(path: String) {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            val result = runCatching { engine.deleteFile(path) }.getOrElse { "Error: ${it.message}" }
            _snackbar.emit(result)
            refreshWorkspace()
        }
    }

    fun zipWorkspace() {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            try {
                val zip = engine.zipProject("export_${System.currentTimeMillis()}")
                _state.update { it.copy(lastExportedZip = zip.absolutePath) }
                _snackbar.emit("ZIP ready: ${zip.name}")
            } catch (e: Exception) {
                _snackbar.emit(e.message ?: "Failed to create ZIP archive")
            }
        }
    }

    /** For the file-tree bottom sheet's inline expand-to-view — reads without leaving the sheet. */
    suspend fun readFileContent(path: String): String {
        val engine = workspaceEngine ?: return "No project selected."
        return runCatching { engine.readFile(path) }.getOrElse { "Error reading file: ${it.message}" }
    }

    // ---------- Organizer (paste-a-whole-project mode) ----------

    fun organizeDump(rawDump: String) {
        val providerProfile = _state.value.activeProviderProfile
        val engine = workspaceEngine ?: return
        if (providerProfile == null) {
            viewModelScope.launch { _snackbar.emit("Add or pick a provider first.") }
            openProviderPicker()
            return
        }
        if (rawDump.isBlank() || _state.value.organizerRunning) return
        _state.update {
            it.copy(
                organizerRunning = true, organizerLog = emptyList(), organizerPhase = OrganizerPhase.THINKING,
                organizerThinkingChars = 0, organizerResultSummary = null
            )
        }
        viewModelScope.launch {
            ProjectDumpEngine(providerProfile.toConfig()).organize(rawDump, engine).collect { progress ->
                when (progress) {
                    is ProjectDumpEngine.Progress.Thinking ->
                        _state.update { it.copy(organizerThinkingChars = progress.charsReceived) }
                    is ProjectDumpEngine.Progress.Parsed ->
                        _state.update {
                            it.copy(
                                organizerPhase = OrganizerPhase.WRITING,
                                organizerLog = listOf(LogLine("Found ${progress.fileCount} files for project \"${progress.projectName}\" — writing…"))
                            )
                        }
                    is ProjectDumpEngine.Progress.Writing ->
                        _state.update { it.copy(organizerLog = it.organizerLog + progress.log) }
                    is ProjectDumpEngine.Progress.Failed -> {
                        _state.update {
                            it.copy(
                                organizerLog = it.organizerLog + LogLine(progress.message, isError = true),
                                organizerRunning = false, organizerPhase = OrganizerPhase.FAILED
                            )
                        }
                        _snackbar.emit(progress.message)
                    }
                    ProjectDumpEngine.Progress.Done -> {
                        val fileCount = _state.value.organizerLog.count { !it.isError && it.text.startsWith("Wrote ") }
                        _state.update {
                            it.copy(
                                organizerRunning = false, organizerPhase = OrganizerPhase.DONE,
                                organizerResultSummary = "Organized $fileCount file${if (fileCount == 1) "" else "s"} into the active project."
                            )
                        }
                        refreshWorkspace()
                    }
                }
            }
        }
    }

    // ---------- Settings ----------

    fun saveCapabilities(fileReadWrite: Boolean, zip: Boolean) {
        viewModelScope.launch { settingsStore.saveCapabilities(fileReadWrite, zip) }
    }

    fun saveGenerationParams(temperature: Float, maxTokens: Int) {
        viewModelScope.launch { settingsStore.saveGenerationParams(temperature, maxTokens) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.saveThemeMode(mode) }
    }

    fun currentZipFile(): File? = _state.value.lastExportedZip?.let { File(it) }
}
