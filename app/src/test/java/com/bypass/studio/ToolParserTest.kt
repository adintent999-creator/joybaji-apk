package com.bypass.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolParserTest {
    @Test
    fun parsesToolCallsAndPaths() {
        val text = """
            Short answer.
            <file path="src/Main.kt">fun main() {}</file>
            <edit_file path="src/App.kt">changed</edit_file>
            <command>ls -la</command>
            <memory_save>Remember API base URL</memory_save>
        """.trimIndent()

        val calls = ToolParser.parse(text)
        assertEquals(3, calls.size)
        assertEquals("file", calls[0].type)
        assertEquals("src/Main.kt", calls[0].path)
        assertEquals("fun main() {}", calls[0].body)
        assertEquals("edit_file", calls[1].type)
        assertEquals("command", calls[2].type)
        assertTrue(ToolParser.hasTools(text))
        assertEquals("Short answer.", ToolParser.clean(text))
    }

    @Test
    fun attributeHandlesSpacesAndMissingValues() {
        assertEquals("a/b.txt", ToolParser.attribute(" path = \"a/b.txt\" lang=\"en\"", "path"))
        assertEquals("", ToolParser.attribute("lang=\"en\"", "path"))
    }
}
