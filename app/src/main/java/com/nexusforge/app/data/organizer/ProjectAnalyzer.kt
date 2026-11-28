package com.nexusforge.app.data.organizer

import com.nexusforge.app.data.FileNode
import java.security.MessageDigest

/**
 * Reads a project and works out (1) how big/healthy it is and (2) what is structurally wrong
 * with it. Entirely deterministic and offline: the AI review is an optional extra on top.
 *
 * It never changes anything. It only produces [StructureIssue]s whose [ReorgAction]s the Cleanup
 * Wizard shows and the user confirms.
 */
object ProjectAnalyzer {

    private val IGNORED_DIRS = setOf(
        ".git", ".gradle", "build", "node_modules", ".idea", "__pycache__", ".venv", "venv",
        "dist", "out", ".dart_tool", ".next", "captures", ".cxx", ".externalNativeBuild"
    )
    private const val MAX_READ_FILES = 1200
    private const val MAX_FILE_BYTES = 400_000L

    private val LANGUAGES = mapOf(
        "kt" to "Kotlin", "kts" to "Kotlin Script", "java" to "Java", "py" to "Python",
        "js" to "JavaScript", "mjs" to "JavaScript", "jsx" to "JavaScript",
        "ts" to "TypeScript", "tsx" to "TypeScript", "html" to "HTML", "htm" to "HTML",
        "css" to "CSS", "scss" to "CSS", "xml" to "XML", "json" to "JSON", "md" to "Markdown",
        "sh" to "Shell", "sql" to "SQL", "gradle" to "Gradle", "yml" to "YAML", "yaml" to "YAML",
        "toml" to "TOML", "properties" to "Properties", "go" to "Go", "rs" to "Rust",
        "swift" to "Swift", "dart" to "Dart", "cs" to "C#", "c" to "C", "h" to "C", "cpp" to "C++",
        "hpp" to "C++", "php" to "PHP", "rb" to "Ruby", "txt" to "Text"
    )
    private val CODE_LANGS = setOf(
        "Kotlin", "Kotlin Script", "Java", "Python", "JavaScript", "TypeScript", "HTML", "CSS",
        "Shell", "SQL", "Gradle", "Go", "Rust", "Swift", "Dart", "C#", "C", "C++", "PHP", "Ruby"
    )
    private val C_STYLE = setOf(
        "Kotlin", "Kotlin Script", "Java", "JavaScript", "TypeScript", "CSS", "Gradle", "Go", "Rust",
        "Swift", "Dart", "C#", "C", "C++", "PHP"
    )
    private val HASH_STYLE = setOf("Python", "Shell", "Ruby", "YAML", "TOML", "Properties")

    private val KT_CLASS = Regex("(?m)^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|private|internal|protected|open|abstract|final|sealed|data|enum|annotation|inner|value|inline|expect|actual|fun)\\s+)*(?:class|interface|object)\\s+([A-Z]\\w*)")
    private val JAVA_CLASS = Regex("(?m)^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|private|protected|static|final|abstract|sealed|strictfp)\\s+)*(?:class|interface|enum|record)\\s+(\\w+)")
    private val PY_CLASS = Regex("(?m)^class\\s+(\\w+)")
    private val JS_CLASS = Regex("(?m)^\\s*(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?(?:class|interface)\\s+(\\w+)")
    private val GENERIC_CLASS = Regex("(?m)^\\s*(?:(?:public|private|internal|open|final|abstract|static)\\s+)*(?:class|struct|interface|enum)\\s+(\\w+)")

    private val KT_FUN = Regex("\\bfun\\s+")
    private val PY_FUN = Regex("(?m)^\\s*(?:async\\s+)?def\\s+\\w+")
    private val JS_FUN = Regex("\\bfunction\\b")
    private val JAVA_FUN = Regex("(?m)^\\s*(?:(?:public|private|protected|static|final|abstract|synchronized)\\s+)+[\\w<>\\[\\],.? ]+\\s+\\w+\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w., ]+)?\\s*\\{")
    private val GO_FUN = Regex("(?m)^func\\s")
    private val RUST_FUN = Regex("\\bfn\\s+\\w+")
    private val SWIFT_FUN = Regex("\\bfunc\\s+\\w+")

    private val PACKAGE = Regex("(?m)^\\s*package\\s+([\\w.]+)")
    private val TOP_TYPE = Regex("(?m)^(?:(?:public|private|internal|protected|open|abstract|final|sealed|data|enum|annotation|value|inline|expect|actual|fun|static)\\s+)*(?:class|interface|object|enum|record)\\s+([A-Z]\\w*)")
    private val TOP_CALLABLE = Regex("(?m)^(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|private|internal|inline|suspend|const|operator|infix|tailrec|expect|actual|lateinit|override)\\s+)*(?:fun|val|var|typealias)\\b")
    /** Case-sensitive on purpose: "FooTest.kt" is a test, "Latest.kt" and "Contest.kt" are not. */
    private val JVM_TEST_NAME = Regex("^.+Tests?\\.(kt|java)$")
    private val TODO = Regex("\\b(?:TODO|FIXME)\\b")

    private val JUNK_NAMES = setOf(".ds_store", "thumbs.db", "desktop.ini")
    private val JUNK_EXT = setOf("tmp", "bak", "orig", "swp", "swo", "rej")
    private val COPY_NAME = Regex("(?i)(\\bcopy\\b|\\(\\d+\\)|[_ -]copy\\b|[_-]old\\b|[_-]bak\\b|backup|[_-]\\d+$)")
    private val KEEP_AT_ROOT_DOCS = setOf(
        "readme.md", "changelog.md", "license.md", "contributing.md", "code_of_conduct.md",
        "security.md", "claude.md", "agents.md"
    )
    private val KEEP_AT_ROOT_SCRIPTS = setOf("gradlew", "gradlew.bat", "mvnw", "mvnw.cmd")

    // ------------------------------------------------------------------ helpers

    fun isIgnored(path: String): Boolean = path.split('/').any { it in IGNORED_DIRS }

    fun score(issues: List<StructureIssue>): Int = (100 - issues.sumOf { it.weight }).coerceIn(0, 100)

    private fun hasFiles(node: FileNode): Boolean = node.children.any { !it.isDirectory || hasFiles(it) }

    private fun join(vararg parts: String): String =
        parts.filter { it.isNotBlank() }.joinToString("/") { it.trim('/') }

    private fun dirOf(path: String) = path.substringBeforeLast('/', "")
    private fun nameOf(path: String) = path.substringAfterLast('/')
    private fun extOf(path: String) = nameOf(path).substringAfterLast('.', "").lowercase()

    private fun isTestPath(path: String): Boolean {
        val segs = path.split('/')
        val name = segs.last()
        if (segs.dropLast(1).any { it.equals("test", true) || it.equals("tests", true) || it == "androidTest" || it == "__tests__" }) return true
        return JVM_TEST_NAME.matches(name) ||
            Regex("(?i)^test_.*\\.py$|.*_test\\.py$").matches(name) ||
            Regex("(?i).*\\.(test|spec)\\.(js|jsx|ts|tsx)$").matches(name)
    }

    private fun listSome(items: List<String>, max: Int = 4): String =
        items.take(max).joinToString(", ") + if (items.size > max) " and ${items.size - max} more" else ""

    private fun defaultRoot(kind: ProjectKind): String = when (kind) {
        ProjectKind.COMPOSE, ProjectKind.ANDROID -> "app/src/main/java"
        ProjectKind.JAVA -> "src/main/java"
        else -> "src/main/kotlin"
    }

    private fun testRootFor(mainRoot: String): String =
        if (mainRoot.contains("src/main")) mainRoot.replace("src/main", "src/test") else "src/test/kotlin"

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private class Info(
        val path: String,
        val lang: String,
        val lines: Int,
        val pkg: String?,
        val types: List<String>,
        val hasTopCallables: Boolean,
        val hash: String?
    ) {
        val name get() = nameOf(path)
        val dir get() = dirOf(path)
        val ext get() = extOf(path)
    }

    // ------------------------------------------------------------------ analysis

    suspend fun analyze(projectName: String, root: FileNode, readText: suspend (String) -> String?): ProjectAnalysis {
        val files = mutableListOf<FileNode>()
        val dirs = mutableListOf<FileNode>()
        fun walk(node: FileNode) {
            for (c in node.children) {
                if (isIgnored(c.path)) continue
                if (c.isDirectory) { dirs += c; walk(c) } else files += c
            }
        }
        walk(root)

        val kind = ProjectKind.detect(files.map { it.path }, readText)

        // ---- pass 1: read + measure
        val infos = mutableListOf<Info>()
        val langFiles = mutableMapOf<String, Int>()
        val langLines = mutableMapOf<String, Int>()
        var codeLines = 0; var commentLines = 0; var blankLines = 0
        var classes = 0; var functions = 0; var composables = 0; var todos = 0
        var sourceFiles = 0; var testFiles = 0
        var reads = 0

        for (f in files) {
            val lang = LANGUAGES[extOf(f.path)] ?: continue
            langFiles[lang] = (langFiles[lang] ?: 0) + 1
            val isCode = lang in CODE_LANGS
            if (isCode) {
                sourceFiles++
                if (isTestPath(f.path)) testFiles++
            }
            if (f.sizeBytes > MAX_FILE_BYTES || reads >= MAX_READ_FILES) continue
            val text = readText(f.path) ?: continue
            reads++

            var code = 0; var comment = 0; var blank = 0
            var inBlock = false
            for (raw in text.lineSequence()) {
                val l = raw.trim()
                if (l.isEmpty()) { blank++; continue }
                if (lang in C_STYLE) {
                    if (inBlock) { comment++; if (l.contains("*/")) inBlock = false; continue }
                    if (l.startsWith("//")) { comment++; continue }
                    if (l.startsWith("/*")) {
                        comment++
                        if (!l.substring(2).contains("*/")) inBlock = true
                        continue
                    }
                } else if (lang in HASH_STYLE && l.startsWith("#")) { comment++; continue }
                code++
            }
            langLines[lang] = (langLines[lang] ?: 0) + code + comment
            if (isCode) {
                codeLines += code; commentLines += comment; blankLines += blank
                todos += TODO.findAll(text).count()
                val classRegex = when (lang) {
                    "Kotlin" -> KT_CLASS
                    "Java" -> JAVA_CLASS
                    "Python" -> PY_CLASS
                    "JavaScript", "TypeScript" -> JS_CLASS
                    else -> GENERIC_CLASS
                }
                classes += classRegex.findAll(text).count()
                val funRegex = when (lang) {
                    "Kotlin" -> KT_FUN
                    "Java" -> JAVA_FUN
                    "Python" -> PY_FUN
                    "JavaScript", "TypeScript" -> JS_FUN
                    "Go" -> GO_FUN
                    "Rust" -> RUST_FUN
                    "Swift" -> SWIFT_FUN
                    else -> null
                }
                if (funRegex != null) functions += funRegex.findAll(text).count()
                if (lang == "Kotlin") composables += text.split("@Composable").size - 1
            }
            val isJvmSource = lang == "Kotlin" || lang == "Java"
            infos += Info(
                path = f.path, lang = lang, lines = code + comment,
                pkg = if (isJvmSource) PACKAGE.find(text)?.groupValues?.get(1) else null,
                types = if (isJvmSource) TOP_TYPE.findAll(text).map { it.groupValues[1] }.toList() else emptyList(),
                hasTopCallables = lang == "Kotlin" && TOP_CALLABLE.containsMatchIn(text),
                hash = if (isCode && code + comment >= 5) sha256(text) else null
            )
        }

        val metrics = ProjectMetrics(
            files = files.size, folders = dirs.size, bytes = files.sumOf { it.sizeBytes },
            sourceFiles = sourceFiles, testFiles = testFiles,
            codeLines = codeLines, commentLines = commentLines, blankLines = blankLines,
            classes = classes, functions = functions, composables = composables, todos = todos,
            maxDepth = files.maxOfOrNull { it.path.count { c -> c == '/' } } ?: 0,
            languages = langFiles.keys
                .map { LanguageStat(it, langFiles[it] ?: 0, langLines[it] ?: 0) }
                .sortedWith(compareByDescending<LanguageStat> { it.lines }.thenByDescending { it.files }),
            largest = infos.filter { it.lang in CODE_LANGS }.sortedByDescending { it.lines }.take(5)
                .map { FileMetric(it.path, it.lines) }
        )

        val issues = findIssues(projectName, kind, root, files, dirs, infos, metrics)
        val facts = infos.filter { it.lang == "Kotlin" || it.lang == "Java" || it.lang == "Python" || it.lang == "JavaScript" || it.lang == "TypeScript" }
            .map { FileFact(it.path, it.lines, it.pkg, it.types) }
        return ProjectAnalysis(projectName, kind, metrics, issues, facts)
    }

    // ------------------------------------------------------------------ rules

    private fun findIssues(
        projectName: String, kind: ProjectKind, root: FileNode,
        files: List<FileNode>, dirs: List<FileNode>, infos: List<Info>, metrics: ProjectMetrics
    ): List<StructureIssue> {
        val issues = mutableListOf<StructureIssue>()
        val reserved = files.map { it.path }.toMutableSet() // existing paths + already-claimed targets
        val moved = mutableSetOf<String>()

        fun claim(from: String, to: String): Boolean {
            if (from == to || to in reserved || from in moved) return false
            reserved += to; moved += from
            return true
        }

        val jvm = infos.filter { it.ext == "kt" || it.ext == "java" }

        // Where do well-placed sources live? (most common root of files whose folder matches the package)
        val roots = jvm.filter { it.pkg != null && !isTestPath(it.path) && it.dir.endsWith(it.pkg.replace('.', '/')) }
            .map { it.dir.removeSuffix(it.pkg!!.replace('.', '/')).trimEnd('/') }
        val sourceRoot = roots.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: defaultRoot(kind)

        // 1. Junk files
        val junk = files.filter {
            val n = nameOf(it.path).lowercase()
            n in JUNK_NAMES || extOf(it.path) in JUNK_EXT || n.endsWith("~")
        }.map { it.path }
        if (junk.isNotEmpty()) issues += StructureIssue(
            id = "junk", title = "Leftover junk files (${junk.size})",
            detail = "Temporary and OS files that don't belong in a project: ${listSome(junk)}.",
            actions = junk.take(200).map { ReorgAction.Delete(it, isDirectory = false) },
            weight = minOf(10, 2 + junk.size / 5)
        )

        // 2. Empty folders (deepest first so parents become empty and can go too)
        val empty = dirs.filter { !hasFiles(it) }.map { it.path }.sortedByDescending { it.count { c -> c == '/' } }
        if (empty.isNotEmpty()) issues += StructureIssue(
            id = "empty-dirs", title = "Empty folders (${empty.size})",
            detail = "Folders with nothing in them add noise: ${listSome(empty.map { "$it/" })}.",
            actions = empty.take(100).map { ReorgAction.Delete(it, isDirectory = true) },
            weight = minOf(6, 1 + empty.size / 2)
        )

        // 3. Tests living in production code
        if (kind.isJvm) {
            val tests = jvm.filter {
                JVM_TEST_NAME.matches(it.name) && !isTestPath(it.dir + "/x") && !it.dir.contains("test", true)
            }
            val moves = mutableListOf<ReorgAction>()
            for (t in tests) {
                val target = when {
                    t.dir.split('/').contains("main") ->
                        join(t.dir.split('/').joinToString("/") { if (it == "main") "test" else it }, t.name)
                    else -> join(testRootFor(sourceRoot), t.pkg?.replace('.', '/') ?: "", t.name)
                }
                if (claim(t.path, target)) moves += ReorgAction.Move(t.path, target)
            }
            if (moves.isNotEmpty()) issues += StructureIssue(
                id = "tests-misplaced", title = "Tests mixed into production code (${moves.size})",
                detail = "Test classes should live under the test source set so they aren't shipped with the app.",
                actions = moves, weight = minOf(10, 4 + moves.size)
            )
        } else if (kind == ProjectKind.PYTHON) {
            val moves = mutableListOf<ReorgAction>()
            for (t in infos.filter { it.ext == "py" && Regex("(?i)^test_.*\\.py$|.*_test\\.py$").matches(it.name) && !isTestPath(it.dir + "/x") }) {
                val target = join("tests", t.name)
                if (claim(t.path, target)) moves += ReorgAction.Move(t.path, target)
            }
            if (moves.isNotEmpty()) issues += StructureIssue(
                id = "tests-misplaced", title = "Test files outside tests/ (${moves.size})",
                detail = "Gathering test files under tests/ keeps them out of the way of application code.",
                actions = moves, weight = minOf(8, 3 + moves.size)
            )
        }

        // 4. Source files dumped at the project root
        if (kind.isJvm) {
            val moves = mutableListOf<ReorgAction>()
            for (f in jvm.filter { it.dir.isEmpty() && it.path !in moved }) {
                val target = join(sourceRoot, f.pkg?.replace('.', '/') ?: "", f.name)
                if (claim(f.path, target)) moves += ReorgAction.Move(f.path, target)
            }
            if (moves.isNotEmpty()) issues += StructureIssue(
                id = "root-source", title = "Source files at the project root (${moves.size})",
                detail = "Code belongs in a source folder (here: $sourceRoot/), arranged by package, not next to build files.",
                actions = moves, weight = minOf(18, 6 + 2 * moves.size)
            )

            // 5. Folder doesn't match the package declaration (one issue per folder)
            val mismatched = jvm.filter { it.dir.isNotEmpty() && it.pkg != null && it.path !in moved && !it.dir.endsWith(it.pkg.replace('.', '/')) }
            for ((dir, group) in mismatched.groupBy { it.dir }.entries.take(12)) {
                val moves2 = mutableListOf<ReorgAction>()
                for (f in group) {
                    val segs = f.dir.split('/')
                    val rootIdx = segs.indexOfLast { it == "kotlin" || it == "java" }
                    val prefix = if (rootIdx >= 0) segs.take(rootIdx + 1).joinToString("/") else sourceRoot
                    val target = join(prefix, f.pkg!!.replace('.', '/'), f.name)
                    if (claim(f.path, target)) moves2 += ReorgAction.Move(f.path, target)
                }
                if (moves2.isNotEmpty()) issues += StructureIssue(
                    id = "pkg-mismatch:$dir", title = "Folder doesn't match package: $dir/",
                    detail = "${moves2.size} file(s) declare a package that differs from the folder they sit in. " +
                        "Moving them makes the tree mirror the packages, so they're easy to find.",
                    actions = moves2, weight = minOf(12, 4 + moves2.size)
                )
            }

            // 6. File name doesn't match the one class it holds
            val renames = mutableListOf<ReorgAction>()
            for (f in jvm.filter { it.path !in moved && it.types.size == 1 && !it.hasTopCallables }) {
                val base = f.name.substringBeforeLast('.')
                if (base != f.types.first() && !base.equals("package-info", true)) {
                    val target = join(f.dir, f.types.first() + "." + f.ext)
                    if (claim(f.path, target)) renames += ReorgAction.Move(f.path, target)
                }
            }
            if (renames.isNotEmpty()) issues += StructureIssue(
                id = "naming", title = "File names don't match their class (${renames.size})",
                detail = "A file holding one class should be named after it, so you can find a class by its file name.",
                actions = renames, weight = minOf(8, 2 + renames.size)
            )
        }

        // 7. Loose docs / scripts at the root
        val looseDocs = files.filter { dirOf(it.path).isEmpty() && extOf(it.path) == "md" && nameOf(it.path).lowercase() !in KEEP_AT_ROOT_DOCS }
        if (looseDocs.size >= 2) {
            val moves = looseDocs.mapNotNull { f -> val to = join("docs", f.name); if (claim(f.path, to)) ReorgAction.Move(f.path, to) else null }
            if (moves.isNotEmpty()) issues += StructureIssue(
                id = "docs", title = "Loose documents at the root (${moves.size})",
                detail = "Notes and docs are easier to browse together in docs/.", actions = moves, weight = 4
            )
        }
        val looseScripts = files.filter {
            dirOf(it.path).isEmpty() && extOf(it.path) in setOf("sh", "bat", "cmd") && nameOf(it.path) !in KEEP_AT_ROOT_SCRIPTS
        }
        if (looseScripts.size >= 2) {
            val moves = looseScripts.mapNotNull { f -> val to = join("scripts", f.name); if (claim(f.path, to)) ReorgAction.Move(f.path, to) else null }
            if (moves.isNotEmpty()) issues += StructureIssue(
                id = "scripts", title = "Scripts at the root (${moves.size})",
                detail = "Helper scripts are tidier in scripts/.", actions = moves, weight = 3
            )
        }

        // 8. Duplicates
        val dupGroups = infos.filter { it.hash != null && it.path !in moved }.groupBy { it.hash!! }.values.filter { it.size > 1 }
        if (dupGroups.isNotEmpty()) {
            val deletions = mutableListOf<ReorgAction>()
            val unresolved = mutableListOf<String>()
            for (g in dupGroups) {
                val copies = g.filter { COPY_NAME.containsMatchIn(it.name.substringBeforeLast('.')) }
                if (copies.isNotEmpty() && copies.size < g.size) copies.forEach { deletions += ReorgAction.Delete(it.path, false) }
                else unresolved += g.joinToString(" = ") { it.path }
            }
            if (deletions.isNotEmpty()) issues += StructureIssue(
                id = "duplicates", title = "Duplicate copies (${deletions.size})",
                detail = "Identical files whose names mark them as copies (\"copy\", \"(1)\", \"old\"). The original is kept.",
                actions = deletions, weight = minOf(8, 3 + deletions.size)
            )
            if (unresolved.isNotEmpty()) issues += StructureIssue(
                id = "duplicates-info", title = "Identical files (${unresolved.size} groups)",
                detail = "These files are byte-for-byte the same, so you may want to keep only one: ${listSome(unresolved, 3)}.",
                severity = Severity.INFO, weight = 3
            )
        }

        // 9. Project hygiene
        if (files.isNotEmpty() && files.none { dirOf(it.path).isEmpty() && nameOf(it.path).equals("README.md", true) }) {
            issues += StructureIssue(
                id = "readme", title = "No README",
                detail = "A README tells anyone opening the project what it is. A starter one is generated from what was found.",
                actions = listOf(ReorgAction.Create("README.md", readmeContent(projectName, kind, root))), weight = 5
            )
        }
        if (files.isNotEmpty() && kind != ProjectKind.DOCS && files.none { it.path == ".gitignore" }) {
            issues += StructureIssue(
                id = "gitignore", title = "No .gitignore",
                detail = "Keeps build output and local files out of version control.",
                actions = listOf(ReorgAction.Create(".gitignore", gitignoreContent(kind))), weight = 5
            )
        }

        // 10. Notes (no automatic fix)
        val huge = infos.filter { it.lang in CODE_LANGS && it.lines > 700 }.sortedByDescending { it.lines }
        if (huge.isNotEmpty()) issues += StructureIssue(
            id = "large-files", title = "Very large files (${huge.size})",
            detail = "Files this long are hard to navigate; consider splitting: " +
                listSome(huge.map { "${it.path} (${it.lines} lines)" }, 3) + ".",
            severity = Severity.INFO, weight = 3
        )
        if (metrics.maxDepth > 9) issues += StructureIssue(
            id = "deep", title = "Deeply nested folders",
            detail = "Some files sit ${metrics.maxDepth} folders deep, which makes paths hard to read.",
            severity = Severity.INFO, weight = 2
        )
        if ((kind.isJvm || kind == ProjectKind.PYTHON || kind == ProjectKind.NODE) && metrics.sourceFiles >= 5 && metrics.testFiles == 0) {
            issues += StructureIssue(
                id = "no-tests", title = "No tests found",
                detail = "Add a test folder so changes can be checked automatically.",
                severity = Severity.INFO, weight = 3
            )
        }
        return issues
    }

    // ------------------------------------------------------------------ generated files

    private fun readmeContent(name: String, kind: ProjectKind, root: FileNode): String = buildString {
        appendLine("# $name")
        appendLine()
        appendLine("${kind.label} project.")
        appendLine()
        val top = root.children.filter { !isIgnored(it.path) }
        if (top.isNotEmpty()) {
            appendLine("## Structure")
            appendLine()
            for (c in top.take(20)) appendLine(if (c.isDirectory) "- `${c.name}/`" else "- `${c.name}`")
            appendLine()
        }
        appendLine("## Getting started")
        appendLine()
        appendLine(
            when (kind) {
                ProjectKind.COMPOSE, ProjectKind.ANDROID -> "Open the project in Android Studio and run the `app` configuration, or build from a terminal with `./gradlew assembleDebug`."
                ProjectKind.KOTLIN_LIB, ProjectKind.JAVA -> "Build with `./gradlew build` and run the tests with `./gradlew test`."
                ProjectKind.WEB -> "Open `index.html` in a browser."
                ProjectKind.NODE -> "Install dependencies with `npm install`, then start with `npm start`."
                ProjectKind.PYTHON -> "Create a virtual environment, install `requirements.txt` if present, then run the entry script."
                else -> "Describe how to build and run this project here."
            }
        )
    }

    fun gitignoreContent(kind: ProjectKind): String = when {
        kind.usesGradle || kind == ProjectKind.KOTLIN -> "build/\n.gradle/\n.idea/\n*.iml\nlocal.properties\ncaptures/\n.cxx/\n.DS_Store\n"
        kind == ProjectKind.WEB || kind == ProjectKind.NODE -> "node_modules/\ndist/\n.env\n.DS_Store\n*.log\n"
        kind == ProjectKind.PYTHON -> "__pycache__/\n*.pyc\n.venv/\nvenv/\n.env\n.pytest_cache/\n.DS_Store\n"
        else -> ".DS_Store\nThumbs.db\n*.log\n*.tmp\n"
    }
}
