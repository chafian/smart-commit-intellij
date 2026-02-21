package com.smartcommit.convention

import org.junit.Assert.*
import org.junit.Test

class ConventionTypeTest {

    @Test
    fun `GITMOJI creates GitmojiConvention`() {
        val convention = ConventionType.GITMOJI.createConvention()
        assertTrue(convention is GitmojiConvention)
        assertEquals("Gitmoji", convention.displayName)
    }

    @Test
    fun `CONVENTIONAL creates ConventionalCommitsConvention`() {
        val convention = ConventionType.CONVENTIONAL.createConvention()
        assertTrue(convention is ConventionalCommitsConvention)
        assertEquals("Conventional Commits", convention.displayName)
    }

    @Test
    fun `FREEFORM creates FreeFormConvention`() {
        val convention = ConventionType.FREEFORM.createConvention()
        assertTrue(convention is FreeFormConvention)
        assertEquals("Free-form", convention.displayName)
    }

    @Test
    fun `all enum values have display names`() {
        for (type in ConventionType.entries) {
            assertTrue(
                "Display name for $type should not be blank",
                type.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun `all enum values create valid conventions`() {
        for (type in ConventionType.entries) {
            val convention = type.createConvention()
            assertNotNull("Convention for $type should not be null", convention)
            assertTrue(
                "Convention prompt hint for $type should not be blank",
                convention.promptHint().isNotBlank()
            )
        }
    }
}
