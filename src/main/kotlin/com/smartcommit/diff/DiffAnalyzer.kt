package com.smartcommit.diff

import com.smartcommit.diff.model.DiffSummary
import com.smartcommit.diff.model.FileDiff

/**
 * Abstraction for extracting structured diff data from VCS changes.
 *
 * This interface exists to decouple pure logic (generators, classifiers)
 * from the IntelliJ VCS API. The production implementation reads from
 * [com.intellij.openapi.vcs.changes.Change] objects; test implementations
 * can return canned data.
 */
interface DiffAnalyzer {

    /**
     * Extract structured [FileDiff] objects from the current set of changes.
     *
     * @return List of file diffs, one per changed file. Never null; empty list
     *         if there are no changes.
     */
    fun extractFileDiffs(): List<FileDiff>

    /**
     * Build a complete [DiffSummary] including classification of each file.
     * This is a convenience method that calls [extractFileDiffs] and then
     * classifies each diff via [ChangeClassifier].
     *
     * @return Aggregated summary of all changes.
     */
    fun analyze(): DiffSummary {
        val diffs = extractFileDiffs()
        val classifications = ChangeClassifier.classifyAll(diffs)
        return DiffSummary(fileDiffs = diffs, classifications = classifications)
    }
}
