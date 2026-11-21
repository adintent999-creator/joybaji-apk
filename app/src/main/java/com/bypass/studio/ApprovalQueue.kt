package com.bypass.studio

object ApprovalQueue {
    enum class Decision { APPROVE, KEEP, REJECT }

    data class Command(
        val id: String,
        val type: String,
        val path: String,
        val status: String = "PENDING"
    )

    fun applicable(commands: List<Command>): List<Command> =
        commands.filter { it.status == "PENDING" }

    fun decide(commands: List<Command>, decision: Decision): List<Command> {
        val targetStatus = when (decision) {
            Decision.APPROVE -> "QUEUED"
            Decision.KEEP -> "KEPT"
            Decision.REJECT -> "REJECTED"
        }
        return commands.map { command ->
            if (command.status == "PENDING") command.copy(status = targetStatus) else command
        }
    }

    fun statusAfterExecution(commands: List<Command>, id: String, ok: Boolean): List<Command> =
        commands.map { command ->
            if (command.id == id) command.copy(status = if (ok) "DONE" else "FAILED") else command
        }

    fun canExecute(command: Command): Boolean =
        command.status in setOf("PENDING", "QUEUED", "KEPT")

    fun summary(commands: List<Command>): String {
        val counts = commands.groupingBy { it.status }.eachCount()
        return listOf("PENDING", "QUEUED", "RUNNING", "DONE", "KEPT", "REJECTED", "FAILED")
            .filter { counts.containsKey(it) }
            .joinToString(" ") { "$it=${counts[it]}" }
    }
}
