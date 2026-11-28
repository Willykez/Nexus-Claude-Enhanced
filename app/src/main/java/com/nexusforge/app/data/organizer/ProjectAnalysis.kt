package com.nexusforge.app.data.organizer

enum class Severity { INFO, WARNING }
enum class IssueSource { RULES, AI }

/** One concrete change the Cleanup Wizard may apply. Nothing runs until the user confirms. */
sealed interface ReorgAction {
    data class Move(val from: String, val to: String) : ReorgAction
    data class Create(val path: String, val content: String) : ReorgAction
    data class Delete(val path: String, val isDirectory: Boolean) : ReorgAction
}

/** A structural problem and (when it can be fixed automatically) exactly how. */
data class StructureIssue(
    val id: String,
    val title: String,
    val detail: String,
    val severity: Severity = Severity.WARNING,
    val actions: List<ReorgAction> = emptyList(),
    val source: IssueSource = IssueSource.RULES,
    /** Points taken off the health score. */
    val weight: Int = 4
) {
    val fixable: Boolean get() = actions.isNotEmpty()
}

data class LanguageStat(val language: String, val files: Int, val lines: Int)
data class FileMetric(val path: String, val lines: Int)

/** What was learned about one source file; also what the AI structure review is shown. */
data class FileFact(
    val path: String,
    val lines: Int,
    val packageName: String?,
    val types: List<String>
)

data class ProjectMetrics(
    val files: Int,
    val folders: Int,
    val bytes: Long,
    val sourceFiles: Int,
    val testFiles: Int,
    val codeLines: Int,
    val commentLines: Int,
    val blankLines: Int,
    val classes: Int,
    val functions: Int,
    val composables: Int,
    val todos: Int,
    val maxDepth: Int,
    val languages: List<LanguageStat>,
    val largest: List<FileMetric>
)

data class ProjectAnalysis(
    val projectName: String,
    val kind: ProjectKind,
    val metrics: ProjectMetrics,
    val issues: List<StructureIssue>,
    val facts: List<FileFact>,
    val analyzedAt: Long = System.currentTimeMillis()
) {
    val healthScore: Int get() = ProjectAnalyzer.score(issues)
    val fixableIssues: List<StructureIssue> get() = issues.filter { it.fixable }
}

enum class WizardPhase { CLOSED, INTRO, STEP, REVIEW, APPLYING, DONE }

data class WizardState(
    val phase: WizardPhase = WizardPhase.CLOSED,
    val index: Int = 0,
    val accepted: Set<String> = emptySet(),
    val healthBefore: Int? = null,
    val applied: Int = 0,
    val failed: Int = 0
)
