package com.bypass.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchParserTest {
    @Test
    fun decodesAndStripsEntities() {
        assertEquals("A & B \"C\"", WebSearchParser.decode("A &amp; B &quot;C&quot;"))
        assertEquals("hello world", WebSearchParser.stripTags("<b>hello</b> <i>world</i>"))
    }

    @Test
    fun parsesDuckDuckGoResults() {
        val html = """
            <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa">Example A</a>
            <a class="result__snippet">First snippet</a>
            <a class="result__a" href="https://example.com/b">Example B</a>
        """.trimIndent()
        val results = WebSearchParser.parseDuckDuckGo(html, 5)
        assertEquals(2, results.size)
        assertEquals("Example A", results[0].title)
        assertEquals("https://example.com/a", results[0].url)
        assertEquals("First snippet", results[0].snippet)
        assertEquals("https://example.com/b", results[1].url)
    }

    @Test
    fun parsesBingResults() {
        val html = """
            <li class="b_algo">
              <h2><a href="https://bing.example/page">Bing Page</a></h2>
              <p>Bing snippet</p>
            </li>
        """.trimIndent()
        val results = WebSearchParser.parseBing(html, 5)
        assertEquals(1, results.size)
        assertEquals("Bing Page", results[0].title)
        assertEquals("https://bing.example/page", results[0].url)
        assertEquals("Bing snippet", results[0].snippet)
    }

    @Test
    fun parsesNewsRss() {
        val xml = """
            <rss><channel>
              <item><title>News one</title><pubDate>Mon, 01 Jan 2026</pubDate></item>
              <item><title>News two</title><pubDate>Tue, 02 Jan 2026</pubDate></item>
            </channel></rss>
        """.trimIndent()
        val results = WebSearchParser.parseNewsRss(xml, 4)
        assertEquals(2, results.size)
        assertEquals("News one" to "Mon, 01 Jan 2026", results[0])
        assertTrue(results[1].first == "News two")
    }
}
