package com.bypass.studio

object MemoryPromptBuilder {
    data class Fact(val key: String, val value: String)
    private val memoryRegex = Regex("""<memory_save>([\s\S]*?)</memory_save>""")

    fun extractFacts(text: String, nowMillis: Long): List<Fact> =
        memoryRegex.findAll(text).mapIndexed { index, match ->
            Fact(
                key = "auto_${nowMillis}_$index",
                value = sanitize(match.groupValues[1].trim())
            )
        }.filter { it.value.isNotBlank() }.toList()

    fun sanitize(value: String): String = value
        .map { ch -> if (ch.isISOControl() && ch !in listOf('\n', '\t')) ' ' else ch }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(2000)

    fun build(rows: List<Pair<String, String>>): String {
        if (rows.isEmpty()) return "No persistent memory yet."
        return "Persistent memory:\n" + rows.take(40).joinToString("\n") { (key, value) ->
            "- ${sanitize(key).take(120)}: ${sanitize(value)}"
        }
    }

    fun stripTags(text: String): String = memoryRegex.replace(text, "")
}
