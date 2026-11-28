package com.nexusforge.app.data.organizer

import com.nexusforge.app.data.AiClient
import com.nexusforge.app.data.ChatMessage
import com.nexusforge.app.data.ChatRequest
import com.nexusforge.app.data.ChatResponseChunk
import com.nexusforge.app.data.FileNode
import com.nexusforge.app.data.GeminiCompat
import com.nexusforge.app.data.ProviderConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Optional second opinion: shows the model the project's real structure (paths, line counts,
 * packages, declared classes) and asks for FILE MOVES only. Whatever it answers is untrusted:
 * every move is validated against the actual project before it can reach the wizard, and nothing
 * is applied without the user's confirmation anyway.
 */
class StructureAdvisor(private val config: ProviderConfig) {

    sealed interface Outcome {
        data class Ok(val issues: List<StructureIssue>) : Outcome
        data class Failed(val message: String) : Outcome
    }

    @Serializable private data class AiMove(val from: String = "", val to: String = "")
    @Serializable private data class AiIssue(val title: String = "", val detail: String = "", val moves: List<AiMove> = emptyList())
    @Serializable private data class AiReview(val issues: List<AiIssue> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun review(analysis: ProjectAnalysis, tree: FileNode): Outcome {
        val files = mutableListOf<String>()
        fun walk(n: FileNode) {
            for (c in n.children) {
                if (ProjectAnalyzer.isIgnored(c.path)) continue
                if (c.isDirectory) walk(c) else files += c.path
            }
        }
        walk(tree)
        if (files.isEmpty()) return Outcome.Ok(emptyList())
        val factsByPath = analysis.facts.associateBy { it.path }

        val listing = buildString {
            for (path in files.take(400)) {
                val f = factsByPath[path]
                if (f == null) appendLine(path)
                else appendLine("$path | ${f.lines} lines | package=${f.packageName ?: "-"} | types=${f.types.joinToString(",").ifBlank { "-" }}")
            }
            if (files.size > 400) appendLine("… and ${files.size - 400} more files")
        }
        val system = """
            You review the folder structure of a software project and suggest how to reorganize its
            files so it is cleaner, more readable and easier to navigate.

            Respond with ONLY a JSON object, no prose, no markdown fences:
            {"issues":[{"title":"short title","detail":"why, one or two sentences","moves":[{"from":"existing/path.ext","to":"new/path.ext"}]}]}

            Rules:
            - Only suggest MOVING existing files (a move may also rename). Never create, delete or edit.
            - "from" must be a path from the list; "to" must not already exist. Keep the file extension.
            - Kotlin/Java: the new folder MUST match the file's package declaration.
            - Never move build files, settings.gradle*, AndroidManifest.xml, anything in res/, gradle/, .github/, or lock files.
            - Group related files (e.g. ui/, data/, util/) only when it clearly improves navigation.
            - Prefer few, high-value issues (at most 6). If the structure is already good, return {"issues":[]}.
        """.trimIndent()
        val user = "Project: ${analysis.projectName} (${analysis.kind.label})\nFiles (path | lines | package | types):\n$listing"

        val client = AiClient(config)
        val text = StringBuilder()
        try {
            val request = ChatRequest(
                model = config.model,
                messages = listOf(ChatMessage(role = "system", content = system), ChatMessage(role = "user", content = user)),
                tools = null, stream = true, temperature = 0.1, maxTokens = 4096,
                reasoningEffort = GeminiCompat.reasoningEffort(config)
            )
            client.streamChatCompletion(request).collect { raw ->
                val chunk = json.decodeFromString(ChatResponseChunk.serializer(), raw)
                chunk.choices.firstOrNull()?.delta?.content?.let { text.append(it) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Outcome.Failed(e.message ?: "Couldn't reach the provider.")
        } finally {
            client.close()
        }

        val body = text.toString().let { t ->
            val start = t.indexOf('{'); val end = t.lastIndexOf('}')
            if (start >= 0 && end > start) t.substring(start, end + 1) else t
        }
        val parsed = try {
            json.decodeFromString(AiReview.serializer(), body)
        } catch (e: Exception) {
            return Outcome.Failed("The model's answer wasn't usable. Try again.")
        }

        val existing = files.toSet()
        val claimed = mutableSetOf<String>()
        val issues = parsed.issues.take(6).mapIndexedNotNull { index, issue ->
            val moves = issue.moves.take(40).mapNotNull { m -> validate(m.from, m.to, existing, claimed, factsByPath) }
            if (moves.isEmpty() || issue.title.isBlank()) null
            else StructureIssue(
                id = "ai:$index", title = issue.title.trim().take(80), detail = issue.detail.trim().take(300),
                actions = moves, source = IssueSource.AI, weight = minOf(8, 3 + moves.size / 2)
            )
        }
        return Outcome.Ok(issues)
    }

    private val protectedNames = setOf(
        "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties",
        "androidmanifest.xml", "gradlew", "gradlew.bat", "proguard-rules.pro", "package.json",
        "package-lock.json", "yarn.lock", "dockerfile", "requirements.txt", "pyproject.toml", ".gitignore", "readme.md"
    )

    private fun validate(
        rawFrom: String, rawTo: String, existing: Set<String>, claimed: MutableSet<String>,
        facts: Map<String, FileFact>
    ): ReorgAction.Move? {
        val from = rawFrom.trim().replace('\\', '/').trimStart('/')
        val to = rawTo.trim().replace('\\', '/').trimStart('/')
        if (from.isBlank() || to.isBlank() || from == to) return null
        if (from !in existing || to in existing || to in claimed) return null
        val toSegs = to.split('/')
        if (toSegs.any { it.isBlank() || it == "." || it == ".." } || ProjectAnalyzer.isIgnored(to)) return null
        val name = from.substringAfterLast('/').lowercase()
        if (name in protectedNames || from.startsWith("res/") || from.contains("/res/") ||
            from.startsWith("gradle/") || from.startsWith(".github/")
        ) return null
        if (to.startsWith("res/") || to.contains("/res/") || to.startsWith("gradle/")) return null
        if (from.substringAfterLast('.', "") != to.substringAfterLast('.', "")) return null
        facts[from]?.packageName?.let { pkg ->
            if (!to.substringBeforeLast('/', "").endsWith(pkg.replace('.', '/'))) return null
        }
        claimed += to
        return ReorgAction.Move(from, to)
    }
}
