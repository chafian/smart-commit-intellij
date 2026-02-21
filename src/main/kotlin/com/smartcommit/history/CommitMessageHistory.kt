package com.smartcommit.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persistent storage for previously generated commit messages.
 *
 * Stores up to [MAX_HISTORY_SIZE] messages in a FIFO queue.
 * Most recent message is at index 0.
 *
 * Registered as an applicationService in plugin.xml.
 */
@State(
    name = "SmartCommitHistory",
    storages = [Storage("smartCommitHistory.xml")]
)
class CommitMessageHistory : PersistentStateComponent<CommitMessageHistory.HistoryState> {

    private var state = HistoryState()

    override fun getState(): HistoryState = state

    override fun loadState(state: HistoryState) {
        this.state = state
    }

    /**
     * Add a generated message to history.
     * Deduplicates: if the same message already exists, it moves to the top.
     */
    fun add(message: String) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return

        // Remove duplicate if exists
        state.messages.remove(trimmed)

        // Add to front (most recent first)
        state.messages.add(0, trimmed)

        // Trim to max size
        while (state.messages.size > MAX_HISTORY_SIZE) {
            state.messages.removeAt(state.messages.lastIndex)
        }
    }

    /**
     * Get all stored messages, most recent first.
     */
    fun getAll(): List<String> = state.messages.toList()

    /**
     * Get the most recent message, or null if history is empty.
     */
    fun latest(): String? = state.messages.firstOrNull()

    /**
     * Number of stored messages.
     */
    fun size(): Int = state.messages.size

    /**
     * Clear all history.
     */
    fun clear() {
        state.messages.clear()
    }

    data class HistoryState(
        var messages: MutableList<String> = mutableListOf()
    )

    companion object {
        const val MAX_HISTORY_SIZE = 50

        fun instance(): CommitMessageHistory {
            return ApplicationManager.getApplication().getService(CommitMessageHistory::class.java)
        }
    }
}
