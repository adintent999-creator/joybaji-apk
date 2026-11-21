package com.bypass.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPromptBuilderTest {
    @Test
    fun extractsFactsWithStableKeys() {
        val facts = MemoryPromptBuilder.extractFacts(
            "ok<memory_save>  Use  Gemini  </memory_save><memory_save>Keep .ttf fonts</memory_save>",
            1234L
        )
        assertEquals(2, facts.size)
        assertEquals("auto_1234_0", facts[0].key)
        assertEquals("Use Gemini", facts[0].value)
        assertEquals("Keep .ttf fonts", facts[1].value)
    }

    @Test
    fun sanitizesControlCharactersAndWhitespace() {
        assertEquals("a b", MemoryPromptBuilder.sanitize("a\t\u0007b"))
        assertEquals("x", MemoryPromptBuilder.stripTags("x<memory_save>hidden</memory_save>").trim())
    }

    @Test
    fun buildsMemoryBlockAndEmptyState() {
        assertEquals("No persistent memory yet.", MemoryPromptBuilder.build(emptyList()))
        val built = MemoryPromptBuilder.build(listOf("k1" to "v1", "k2" to "v2"))
        assertTrue(built.startsWith("Persistent memory:"))
        assertTrue(built.contains("- k1: v1"))
        assertTrue(built.contains("- k2: v2"))
    }
}
