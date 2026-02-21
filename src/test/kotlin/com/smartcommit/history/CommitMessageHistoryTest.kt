package com.smartcommit.history

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CommitMessageHistoryTest {

    private lateinit var history: CommitMessageHistory

    @Before
    fun setUp() {
        history = CommitMessageHistory()
    }

    @Test
    fun `empty history returns empty list`() {
        assertTrue(history.getAll().isEmpty())
        assertEquals(0, history.size())
        assertNull(history.latest())
    }

    @Test
    fun `add stores message`() {
        history.add("feat: add login")
        assertEquals(1, history.size())
        assertEquals("feat: add login", history.latest())
    }

    @Test
    fun `most recent message is first`() {
        history.add("first commit")
        history.add("second commit")
        assertEquals("second commit", history.latest())
        assertEquals(listOf("second commit", "first commit"), history.getAll())
    }

    @Test
    fun `duplicate moves to top`() {
        history.add("first")
        history.add("second")
        history.add("first")
        assertEquals(2, history.size())
        assertEquals("first", history.latest())
        assertEquals(listOf("first", "second"), history.getAll())
    }

    @Test
    fun `blank messages are ignored`() {
        history.add("")
        history.add("   ")
        assertEquals(0, history.size())
    }

    @Test
    fun `respects max history size`() {
        for (i in 1..60) {
            history.add("message $i")
        }
        assertEquals(CommitMessageHistory.MAX_HISTORY_SIZE, history.size())
        assertEquals("message 60", history.latest())
    }

    @Test
    fun `clear removes all`() {
        history.add("one")
        history.add("two")
        history.clear()
        assertEquals(0, history.size())
        assertTrue(history.getAll().isEmpty())
    }

    @Test
    fun `messages are trimmed`() {
        history.add("  feat: add login  ")
        assertEquals("feat: add login", history.latest())
    }

    @Test
    fun `state persistence round-trip`() {
        history.add("first")
        history.add("second")

        val state = history.state
        val restored = CommitMessageHistory()
        restored.loadState(state)

        assertEquals(listOf("second", "first"), restored.getAll())
    }
}
