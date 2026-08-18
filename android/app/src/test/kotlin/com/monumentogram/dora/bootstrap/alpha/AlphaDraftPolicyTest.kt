package com.monumentogram.dora.bootstrap.alpha

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaDraftPolicyTest {
    @Test
    fun enforcesUtf8ByteLimitsWithoutAcceptingInvalidUnicode() {
        assertTrue(AlphaDraftPolicy.acceptsTitle("x".repeat(AlphaWorkspaceCodec.MAX_TITLE_BYTES)))
        assertFalse(
            AlphaDraftPolicy.acceptsTitle("x".repeat(AlphaWorkspaceCodec.MAX_TITLE_BYTES + 1))
        )
        assertFalse(AlphaDraftPolicy.acceptsTitle("я".repeat(AlphaWorkspaceCodec.MAX_TITLE_BYTES)))
        assertFalse(AlphaDraftPolicy.acceptsNotes("invalid-\uD800-text"))
    }

    @Test
    fun taskDraftIsBoundedByCountPerLineAndAggregateSize() {
        assertTrue(AlphaDraftPolicy.acceptsTaskLines("Первая\nВторая"))
        assertFalse(AlphaDraftPolicy.acceptsTaskLines((1..101).joinToString("\n") { "task-$it" }))
        assertFalse(
            AlphaDraftPolicy.acceptsTaskLines(
                "x".repeat(AlphaWorkspaceCodec.MAX_TASK_TEXT_BYTES + 1)
            )
        )
        assertFalse(AlphaDraftPolicy.acceptsTaskLines("x".repeat(65_537)))
    }

    @Test
    fun editorDraftEnforcesOneAggregateLimitAcrossAllFields() {
        assertTrue(
            AlphaDraftPolicy.acceptsEditorDraft(
                title = "Title",
                notes = "x".repeat(40_000),
                summary = "x".repeat(20_000),
                taskLines = "Task",
            )
        )
        assertFalse(
            AlphaDraftPolicy.acceptsEditorDraft(
                title = "Title",
                notes = "x".repeat(40_000),
                summary = "x".repeat(25_532),
                taskLines = "Task",
            )
        )
    }
}
