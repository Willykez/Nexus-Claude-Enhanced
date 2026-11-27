package com.nexusforge.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** Colours for code, tuned separately for dark and light so code stays readable in both. */
data class CodePalette(
    val surface: Color,
    val header: Color,
    val gutter: Color,
    val text: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val type: Color,
    val function: Color,
    val annotation: Color,
    val property: Color,
    val tag: Color
)

val DarkCodePalette = CodePalette(
    surface = Color(0xFF0D0F14), header = Color(0xFF14171D), gutter = Color(0xFF4B515C),
    text = Color(0xFFD4D4D8), keyword = Color(0xFFC792EA), string = Color(0xFF9FD38A),
    number = Color(0xFFF2A65A), comment = Color(0xFF6B7280), type = Color(0xFFE8C26A),
    function = Color(0xFF7AB7F5), annotation = Color(0xFFEF8A93), property = Color(0xFF5CC8D6),
    tag = Color(0xFFEF7C86)
)

val LightCodePalette = CodePalette(
    surface = Color(0xFFF7F7F9), header = Color(0xFFEDEDF1), gutter = Color(0xFFA1A1AA),
    text = Color(0xFF27272A), keyword = Color(0xFF7C3AED), string = Color(0xFF15803D),
    number = Color(0xFFC2410C), comment = Color(0xFF71717A), type = Color(0xFFB45309),
    function = Color(0xFF1D4ED8), annotation = Color(0xFFBE185D), property = Color(0xFF0E7490),
    tag = Color(0xFFBE123C)
)

/** Beyond this size we skip colouring: a plain Text is cheap, a huge span list is not. */
private const val MAX_HIGHLIGHT_CHARS = 80_000

private enum class Family { C_LIKE, HASH, SQL, JSON, MARKUP, NONE }

private fun familyOf(language: String?): Family = when (language?.lowercase()?.trim()) {
    "kotlin", "kt", "kts", "java", "javascript", "js", "mjs", "typescript", "ts", "tsx", "jsx",
    "c", "h", "cpp", "c++", "hpp", "cc", "csharp", "cs", "go", "rust", "rs", "swift", "dart",
    "php", "scala", "groovy", "gradle", "css", "scss", "less" -> Family.C_LIKE
    "python", "py", "bash", "sh", "shell", "zsh", "yaml", "yml", "toml", "ruby", "rb",
    "properties", "dockerfile", "makefile", "ini", "conf" -> Family.HASH
    "sql" -> Family.SQL
    "json", "jsonc" -> Family.JSON
    "html", "htm", "xml", "svg", "xhtml" -> Family.MARKUP
    else -> Family.NONE
}

private val C_KEYWORDS = setOf(
    "abstract", "as", "async", "await", "break", "case", "catch", "class", "const", "constructor",
    "continue", "data", "default", "defer", "do", "else", "enum", "export", "extends", "extern",
    "false", "final", "finally", "fn", "for", "fun", "func", "function", "go", "if", "implements",
    "import", "in", "inline", "instanceof", "interface", "internal", "is", "lateinit", "let", "match",
    "mod", "mut", "namespace", "new", "null", "nil", "object", "open", "operator", "override",
    "package", "private", "protected", "pub", "public", "return", "sealed", "self", "static",
    "struct", "super", "suspend", "switch", "this", "throw", "throws", "trait", "true", "try",
    "typealias", "typeof", "undefined", "use", "using", "val", "var", "void", "when", "where",
    "while", "with", "yield", "init", "companion", "by", "out", "readonly", "type", "declare",
    "from", "of", "delete", "synchronized", "volatile", "native", "echo", "print"
)

private val HASH_KEYWORDS = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif",
    "else", "except", "false", "finally", "for", "from", "global", "if", "import", "in", "is",
    "lambda", "none", "nonlocal", "not", "or", "pass", "raise", "return", "self", "true", "try",
    "while", "with", "yield", "fi", "then", "do", "done", "esac", "case", "function", "export",
    "local", "echo", "end", "module", "require", "begin", "unless", "elsif", "nil",
    "True", "False", "None"
)

private val SQL_KEYWORDS = setOf(
    "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create",
    "table", "drop", "alter", "add", "column", "index", "primary", "key", "foreign", "references",
    "join", "left", "right", "inner", "outer", "full", "on", "group", "by", "order", "having",
    "limit", "offset", "as", "and", "or", "not", "null", "is", "in", "like", "between", "distinct",
    "union", "all", "case", "when", "then", "else", "end", "default", "unique", "exists", "true", "false"
)

private val JSON_KEYWORDS = setOf("true", "false", "null")

private val COMMENT_C = "//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z)"
private val COMMENT_HASH = "#[^\\n]*"
private val COMMENT_SQL = "--[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z)"
private const val TQ = "\"\"\""
private val STRING_RE =
    "$TQ[\\s\\S]*?(?:$TQ|\\z)|\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?|`(?:\\\\.|[^`\\\\])*`?"
private val NUMBER_RE = "\\b0[xX][0-9a-fA-F_]+\\b|\\b\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFLuUdD]*\\b"
private val IDENT_RE = "[A-Za-z_][A-Za-z0-9_]*"

private fun tokenRegex(comment: String, annotation: Boolean): Regex {
    val ann = if (annotation) "|(@[A-Za-z_][A-Za-z0-9_.]*)" else "|(\\u0000)"
    return Regex("($comment)|($STRING_RE)$ann|($NUMBER_RE)|($IDENT_RE)")
}

private val C_REGEX = tokenRegex(COMMENT_C, annotation = true)
private val HASH_REGEX = tokenRegex(COMMENT_HASH, annotation = false)
private val SQL_REGEX = tokenRegex(COMMENT_SQL, annotation = false)
private val JSON_REGEX = tokenRegex("\\u0000", annotation = false)

private val MARKUP_REGEX = Regex(
    "(<!--[\\s\\S]*?(?:-->|\\z))|(</?[A-Za-z][A-Za-z0-9:._-]*)|(\"[^\"\\n]*\"?|'[^'\\n]*'?)|([A-Za-z_:][A-Za-z0-9:._-]*)(?==)"
)

/** Colours [code] for [language]. Unknown languages come back plain rather than mis-coloured. */
fun highlightCode(code: String, language: String?, p: CodePalette): AnnotatedString {
    val family = familyOf(language)
    if (family == Family.NONE || code.length > MAX_HIGHLIGHT_CHARS) {
        return AnnotatedString(code)
    }
    return when (family) {
        Family.MARKUP -> highlightMarkup(code, p)
        else -> highlightTokens(code, language?.lowercase().orEmpty(), family, p)
    }
}

private fun highlightMarkup(code: String, p: CodePalette): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (m in MARKUP_REGEX.findAll(code)) {
        if (m.range.first > cursor) append(code.substring(cursor, m.range.first))
        val style = when {
            m.groups[1] != null -> SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)
            m.groups[2] != null -> SpanStyle(color = p.tag)
            m.groups[3] != null -> SpanStyle(color = p.string)
            else -> SpanStyle(color = p.property)
        }
        pushStyle(style); append(m.value); pop()
        cursor = m.range.last + 1
    }
    if (cursor < code.length) append(code.substring(cursor))
}

private fun highlightTokens(code: String, language: String, family: Family, p: CodePalette): AnnotatedString {
    val regex = when (family) {
        Family.C_LIKE -> C_REGEX
        Family.HASH -> HASH_REGEX
        Family.SQL -> SQL_REGEX
        else -> JSON_REGEX
    }
    val keywords = when (family) {
        Family.C_LIKE -> C_KEYWORDS
        Family.HASH -> HASH_KEYWORDS
        Family.SQL -> SQL_KEYWORDS
        else -> JSON_KEYWORDS
    }
    val keyColoured = language in setOf("yaml", "yml", "toml", "css", "scss", "less", "ini", "properties")

    return buildAnnotatedString {
        var cursor = 0
        for (m in regex.findAll(code)) {
            if (m.range.first > cursor) append(code.substring(cursor, m.range.first))
            val token = m.value
            val after = nextNonSpace(code, m.range.last + 1)
            val style: SpanStyle? = when {
                m.groups[1] != null && m.groups[1]!!.value.isNotEmpty() ->
                    SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)
                m.groups[2] != null && m.groups[2]!!.value.isNotEmpty() ->
                    if (family == Family.JSON && after == ':') SpanStyle(color = p.property)
                    else SpanStyle(color = p.string)
                m.groups[3] != null && m.groups[3]!!.value.isNotEmpty() && token[0] == '@' ->
                    SpanStyle(color = p.annotation)
                m.groups[4] != null && m.groups[4]!!.value.isNotEmpty() ->
                    SpanStyle(color = p.number)
                else -> identStyle(token, after, keywords, family == Family.SQL, keyColoured, p)
            }
            if (style != null) {
                pushStyle(style); append(token); pop()
            } else append(token)
            cursor = m.range.last + 1
        }
        if (cursor < code.length) append(code.substring(cursor))
    }
}

private fun identStyle(
    token: String, after: Char?, keywords: Set<String>, ignoreCase: Boolean,
    keyColoured: Boolean, p: CodePalette
): SpanStyle? {
    val word = if (ignoreCase) token.lowercase() else token
    return when {
        word in keywords ->
            SpanStyle(color = p.keyword, fontWeight = FontWeight.Medium)
        keyColoured && after == ':' -> SpanStyle(color = p.property)
        token.length > 1 && token[0].isUpperCase() -> SpanStyle(color = p.type)
        after == '(' -> SpanStyle(color = p.function)
        else -> null
    }
}

private fun nextNonSpace(s: String, from: Int): Char? {
    var i = from
    while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
    return if (i < s.length) s[i] else null
}

/** Language key for a file path, used to colour files the agent writes. */
fun languageFromPath(path: String?): String? {
    val name = path?.substringAfterLast('/')?.lowercase() ?: return null
    if (name == "dockerfile") return "dockerfile"
    if (name == "makefile") return "makefile"
    return when (name.substringAfterLast('.', "")) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js", "mjs", "cjs" -> "js"
        "ts" -> "ts"
        "tsx" -> "tsx"
        "jsx" -> "jsx"
        "py" -> "python"
        "html", "htm" -> "html"
        "xml", "svg" -> "xml"
        "css" -> "css"
        "scss" -> "scss"
        "json" -> "json"
        "yaml", "yml" -> "yaml"
        "toml" -> "toml"
        "sh", "bash", "zsh" -> "bash"
        "sql" -> "sql"
        "c", "h" -> "c"
        "cpp", "cc", "hpp" -> "cpp"
        "cs" -> "csharp"
        "go" -> "go"
        "rs" -> "rust"
        "swift" -> "swift"
        "dart" -> "dart"
        "php" -> "php"
        "rb" -> "ruby"
        "gradle" -> "gradle"
        "md", "markdown" -> "markdown"
        "properties" -> "properties"
        else -> null
    }
}

/** Human label for a language key ("kt" -> "Kotlin"). */
internal fun displayLanguage(language: String?): String {
    return when (language?.lowercase()?.trim()) {
        null, "" -> "Code"
        "kotlin", "kt", "kts" -> "Kotlin"
        "js", "javascript", "mjs" -> "JavaScript"
        "ts", "typescript" -> "TypeScript"
        "py", "python" -> "Python"
        "sh", "bash", "shell", "zsh" -> "Shell"
        "yml", "yaml" -> "YAML"
        "md", "markdown" -> "Markdown"
        "cpp", "c++" -> "C++"
        "cs", "csharp" -> "C#"
        "html", "htm" -> "HTML"
        "css" -> "CSS"
        "json" -> "JSON"
        "sql" -> "SQL"
        "xml" -> "XML"
        "rs", "rust" -> "Rust"
        "rb", "ruby" -> "Ruby"
        "tsx" -> "TSX"
        "jsx" -> "JSX"
        else -> (language ?: "").trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

internal fun extensionFor(language: String?): String = when (language?.lowercase()?.trim()) {
    "kotlin", "kt" -> "kt"
    "java" -> "java"
    "python", "py" -> "py"
    "javascript", "js" -> "js"
    "typescript", "ts" -> "ts"
    "tsx" -> "tsx"
    "jsx" -> "jsx"
    "html", "htm" -> "html"
    "css" -> "css"
    "scss" -> "scss"
    "json" -> "json"
    "xml" -> "xml"
    "bash", "sh", "shell" -> "sh"
    "sql" -> "sql"
    "yaml", "yml" -> "yaml"
    "markdown", "md" -> "md"
    "c" -> "c"
    "cpp", "c++" -> "cpp"
    "csharp", "cs" -> "cs"
    "go" -> "go"
    "rust", "rs" -> "rs"
    "swift" -> "swift"
    "dart" -> "dart"
    "php" -> "php"
    "ruby", "rb" -> "rb"
    else -> "txt"
}
