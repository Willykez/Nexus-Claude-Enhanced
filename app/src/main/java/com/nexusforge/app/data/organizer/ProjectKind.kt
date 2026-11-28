package com.nexusforge.app.data.organizer

/** What kind of project a folder is. Drives list icons, default source roots and cleanup rules. */
enum class ProjectKind(val label: String) {
    COMPOSE("Compose App"),
    ANDROID("Android App"),
    KOTLIN_LIB("Kotlin Library"),
    KOTLIN("Kotlin"),
    JAVA("Java"),
    WEB("Web"),
    NODE("Node.js"),
    PYTHON("Python"),
    DOCS("Docs"),
    BLANK("Empty"),
    OTHER("Project");

    val usesGradle: Boolean get() = this == COMPOSE || this == ANDROID || this == KOTLIN_LIB || this == JAVA
    val isJvm: Boolean get() = this == COMPOSE || this == ANDROID || this == KOTLIN_LIB || this == KOTLIN || this == JAVA

    companion object {
        fun fromKey(key: String?): ProjectKind = entries.firstOrNull { it.name == key } ?: OTHER

        /**
         * Looks at file names first (cheap) and only reads a few build files when it has to tell
         * Compose from plain Android, or a web app from a Node service.
         */
        suspend fun detect(filePaths: List<String>, readText: suspend (String) -> String?): ProjectKind {
            if (filePaths.isEmpty()) return BLANK
            val names = filePaths.map { it.substringAfterLast('/') }
            val ext = filePaths.groupingBy { it.substringAfterLast('.', "").lowercase() }.eachCount()
            val kt = ext["kt"] ?: 0
            val java = ext["java"] ?: 0
            val py = ext["py"] ?: 0
            val html = (ext["html"] ?: 0) + (ext["htm"] ?: 0)
            val hasGradle = names.any { it in GRADLE_FILES }
            val hasManifest = names.any { it == "AndroidManifest.xml" }

            if (hasGradle || hasManifest) {
                var android = hasManifest
                var compose = false
                for (path in filePaths.filter { it.substringAfterLast('/') in BUILD_FILES }.take(4)) {
                    val text = readText(path)?.lowercase() ?: continue
                    if ("com.android" in text || "android {" in text) android = true
                    if ("compose" in text) compose = true
                }
                if (android && !compose) {
                    compose = filePaths.filter { it.endsWith(".kt") }.take(6).any {
                        readText(it)?.contains("androidx.compose") == true
                    }
                }
                return when {
                    android && compose -> COMPOSE
                    android -> ANDROID
                    kt > 0 -> KOTLIN_LIB
                    java > 0 -> JAVA
                    else -> OTHER
                }
            }
            if ("package.json" in names) {
                val pkg = filePaths.firstOrNull { it == "package.json" }?.let { readText(it)?.lowercase() }.orEmpty()
                val webish = listOf("react", "vue", "svelte", "vite", "next", "webpack", "angular").any { it in pkg }
                return if (webish || html > 0) WEB else NODE
            }
            return when {
                html > 0 -> WEB
                py > 0 || "requirements.txt" in names || "pyproject.toml" in names -> PYTHON
                kt > 0 -> KOTLIN
                java > 0 -> JAVA
                filePaths.all { it.substringAfterLast('.', "").lowercase() in setOf("md", "txt", "rst") } -> DOCS
                else -> OTHER
            }
        }

        private val GRADLE_FILES = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")
        private val BUILD_FILES = setOf("build.gradle", "build.gradle.kts")
    }
}
