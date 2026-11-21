package com.bypass.studio

import java.net.URLDecoder

object WebSearchParser {
    data class Result(val title: String, val url: String, val snippet: String)

    fun decode(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

    fun stripTags(value: String): String =
        decode(Regex("""<[^>]+>""").replace(value, " "))
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun href(attrs: String): String {
        val match = Regex("href\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(attrs)
            ?: return ""
        var url = decode(match.groupValues[1])
        if (url.startsWith("//")) url = "https:$url"
        val uddg = Regex("""[?&]uddg=([^&]+)""").find(url)
        if (uddg != null) {
            url = runCatching { URLDecoder.decode(uddg.groupValues[1], "UTF-8") }
                .getOrDefault(uddg.groupValues[1])
        }
        return url
    }

    fun parseDuckDuckGo(html: String, limit: Int = 5): List<Result> {
        val anchors = Regex("""<a\s+([^>]*)>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        val results = mutableListOf<Result>()
        anchors.findAll(html).forEach { anchor ->
            val attrs = anchor.groupValues[1]
            if (!attrs.contains("result__a", ignoreCase = true)) return@forEach
            val url = href(attrs)
            val title = stripTags(anchor.groupValues[2])
            if (url.isBlank() || title.isBlank()) return@forEach
            val snippet = anchors.findAll(html).mapNotNull { candidate ->
                val candAttrs = candidate.groupValues[1]
                if (candAttrs.contains("result__snippet", ignoreCase = true)) {
                    stripTags(candidate.groupValues[2]).takeIf { it.isNotBlank() }
                } else null
            }.firstOrNull().orEmpty()
            results.add(Result(title, url, snippet))
            if (results.size >= limit) return@forEach
        }
        return results
    }

    fun parseBing(html: String, limit: Int = 5): List<Result> {
        val blocks = Regex("""<li[^>]+class="[^"]*b_algo[^"]*"[^>]*>([\s\S]*?)</li>""", RegexOption.IGNORE_CASE)
        val results = mutableListOf<Result>()
        blocks.findAll(html).forEach { block ->
            val body = block.groupValues[1]
            val link = Regex("""<a\s+([^>]*)>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
                .findAll(body).firstOrNull { it.groupValues[1].contains("href", ignoreCase = true) }
                ?: return@forEach
            val url = href(link.groupValues[1])
            val title = stripTags(link.groupValues[2])
            if (url.isBlank() || title.isBlank()) return@forEach
            val snippet = Regex("""<p[^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
                .find(body)?.let { stripTags(it.groupValues[1]) }.orEmpty()
            results.add(Result(title, url, snippet))
            if (results.size >= limit) return@forEach
        }
        return results
    }

    fun parseNewsRss(xml: String, limit: Int = 4): List<Pair<String, String>> =
        Regex("""<item>([\s\S]*?)</item>""", RegexOption.IGNORE_CASE)
            .findAll(xml)
            .mapNotNull { item ->
                val body = item.groupValues[1]
                val title = Regex("""<title>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
                    .find(body)?.let { stripTags(it.groupValues[1]) }.orEmpty()
                val date = Regex("""<pubDate>([\s\S]*?)</pubDate>""", RegexOption.IGNORE_CASE)
                    .find(body)?.let { stripTags(it.groupValues[1]) }.orEmpty()
                title.takeIf { it.isNotBlank() }?.to(date)
            }
            .take(limit)
            .toList()
}
