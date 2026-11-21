package com.bypass.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalQueueTest {
    private fun commands() = listOf(
        ApprovalQueue.Command("1", "file", "A.kt"),
        ApprovalQueue.Command("2", "command", "", "QUEUED"),
        ApprovalQueue.Command("3", "fetch", "docs", "DONE")
    )

    @Test
    fun onlyPendingCommandsAreApplicable() {
        assertEquals(listOf("1"), ApprovalQueue.applicable(commands()).map { it.id })
    }

    @Test
    fun decisionsOnlyChangePendingCommands() {
        val approved = ApprovalQueue.decide(commands(), ApprovalQueue.Decision.APPROVE)
        assertEquals("QUEUED", approved.first { it.id == "1" }.status)
        assertEquals("QUEUED", approved.first { it.id == "2" }.status)
        assertEquals("DONE", approved.first { it.id == "3" }.status)

        val kept = ApprovalQueue.decide(commands(), ApprovalQueue.Decision.KEEP)
        assertEquals("KEPT", kept.first { it.id == "1" }.status)

        val rejected = ApprovalQueue.decide(commands(), ApprovalQueue.Decision.REJECT)
        assertEquals("REJECTED", rejected.first { it.id == "1" }.status)
    }

    @Test
    fun executionStatusAndCanExecuteAreCorrect() {
        val commands = commands()
        assertTrue(ApprovalQueue.canExecute(commands.first { it.id == "1" }))
        assertTrue(ApprovalQueue.canExecute(commands.first { it.id == "2" }))
        assertFalse(ApprovalQueue.canExecute(commands.first { it.id == "3" }))

        val done = ApprovalQueue.statusAfterExecution(commands, "1", true)
        assertEquals("DONE", done.first { it.id == "1" }.status)
        val failed = ApprovalQueue.statusAfterExecution(commands, "1", false)
        assertEquals("FAILED", failed.first { it.id == "1" }.status)
    }

    @Test
    fun summaryUsesStableOrder() {
        assertEquals(
            "PENDING=1 QUEUED=1 DONE=1",
            ApprovalQueue.summary(commands())
        )
    }
}
