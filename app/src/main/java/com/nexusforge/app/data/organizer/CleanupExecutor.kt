package com.nexusforge.app.data.organizer

import com.nexusforge.app.data.workspace.WorkspaceEngine

/**
 * Applies the changes the user approved in the Cleanup Wizard, one at a time, so one failure
 * never aborts the rest. Safety rules:
 *  - never overwrites: a move/create whose destination exists is skipped and reported;
 *  - folders are only removed when empty;
 *  - every successful move/create is journaled so it can be undone.
 * (Deleted junk files can't be restored; the wizard says so before confirming.)
 */
class CleanupExecutor(private val engine: WorkspaceEngine) {

    data class Result(val journal: List<ReorgAction>, val applied: Int, val failed: Int)

    private fun dirOf(path: String) = path.substringBeforeLast('/', "")

    suspend fun apply(actions: List<ReorgAction>, onLog: (LogLine) -> Unit): Result {
        val journal = mutableListOf<ReorgAction>()
        var applied = 0
        var failed = 0
        val touchedDirs = linkedSetOf<String>()

        // Creates and moves first, then file deletes, then (deepest-first) folder deletes.
        val ordered = actions.sortedBy {
            when (it) {
                is ReorgAction.Move -> 0
                is ReorgAction.Create -> 1
                is ReorgAction.Delete -> if (it.isDirectory) 3 else 2
            }
        }
        for (action in ordered) {
            try {
                when (action) {
                    is ReorgAction.Move -> {
                        engine.moveFile(action.from, action.to)
                        journal += action
                        dirOf(action.from).takeIf { it.isNotEmpty() }?.let { touchedDirs += it }
                        onLog(LogLine("Moved ${action.from}  →  ${action.to}"))
                    }
                    is ReorgAction.Create -> {
                        if (engine.fileExists(action.path)) throw IllegalStateException("${action.path} already exists")
                        engine.writeFile(action.path, action.content)
                        journal += action
                        onLog(LogLine("Created ${action.path}"))
                    }
                    is ReorgAction.Delete -> {
                        if (action.isDirectory) engine.deleteEmptyDirectory(action.path) else engine.deleteFile(action.path)
                        onLog(LogLine("Deleted ${action.path}${if (action.isDirectory) "/" else ""}"))
                    }
                }
                applied++
            } catch (e: Exception) {
                failed++
                onLog(LogLine("Skipped ${describe(action)}: ${e.message}", isError = true))
            }
        }
        pruneEmpty(touchedDirs, onLog)
        return Result(journal, applied, failed)
    }

    /** Reverses journaled moves and creates, newest first. */
    suspend fun undo(journal: List<ReorgAction>, onLog: (LogLine) -> Unit): Result {
        var applied = 0
        var failed = 0
        val touchedDirs = linkedSetOf<String>()
        for (action in journal.asReversed()) {
            try {
                when (action) {
                    is ReorgAction.Move -> {
                        if (engine.fileExists(action.from)) throw IllegalStateException("${action.from} exists again")
                        engine.moveFile(action.to, action.from)
                        dirOf(action.to).takeIf { it.isNotEmpty() }?.let { touchedDirs += it }
                        onLog(LogLine("Restored ${action.from}"))
                    }
                    is ReorgAction.Create -> {
                        engine.deleteFile(action.path)
                        onLog(LogLine("Removed ${action.path}"))
                    }
                    is ReorgAction.Delete -> Unit
                }
                applied++
            } catch (e: Exception) {
                failed++
                onLog(LogLine("Couldn't undo ${describe(action)}: ${e.message}", isError = true))
            }
        }
        pruneEmpty(touchedDirs, onLog)
        return Result(emptyList(), applied, failed)
    }

    private suspend fun pruneEmpty(dirs: Set<String>, onLog: (LogLine) -> Unit) {
        for (start in dirs.sortedByDescending { it.count { c -> c == '/' } }) {
            var dir = start
            while (dir.isNotEmpty()) {
                val removed = runCatching { engine.deleteEmptyDirectory(dir) }.isSuccess
                if (!removed) break
                onLog(LogLine("Removed empty folder $dir/"))
                dir = dirOf(dir)
            }
        }
    }

    private fun describe(a: ReorgAction) = when (a) {
        is ReorgAction.Move -> "move of ${a.from}"
        is ReorgAction.Create -> "creating ${a.path}"
        is ReorgAction.Delete -> "deleting ${a.path}"
    }
}
