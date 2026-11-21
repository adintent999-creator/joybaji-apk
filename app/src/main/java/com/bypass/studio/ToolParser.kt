package com.bypass.studio

object ToolParser {
    data class ToolCall(
        val type: String,
        val attrs: String,
        val body: String,
        val path: String
    )

    val tagRegex = Regex("""<(file|edit_file|mkdir|delete_file|command|fetch)([^>]*)>([\s\S]*?)</\1>""")
    val memoryRegex = Regex("""<memory_save>([\s\S]*?)</memory_save>""")

    fun parse(text: String): List<ToolCall> = tagRegex.findAll(text).map { match ->
        val attrs = match.groupValues[2]
        ToolCall(
            type = match.groupValues[1],
            attrs = attrs,
            body = match.groupValues[3].trim(),
            path = attribute(attrs, "path")
        )
    }.toList()

    fun attribute(attrs: String, name: String): String {
        val regex = Regex("$name\\s*=\\s*\"([^\"]+)\"")
        return regex.find(attrs)?.groupValues?.get(1).orEmpty()
    }

    fun clean(text: String): String =
        memoryRegex.replace(tagRegex.replace(text, ""), "").trim().ifBlank { "Done." }

    fun hasTools(text: String): Boolean = tagRegex.containsMatchIn(text)
}
