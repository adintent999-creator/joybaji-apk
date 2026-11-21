package com.bypass.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class Provider(val label: String) {
    OPENROUTER("OpenRouter"),
    GEMINI("Google Gemini"),
    CUSTOM("Custom / Ollama")
}

data class AiConfig(
    val provider: Provider = Provider.OPENROUTER,
    val apiKey: String = "",
    val model: String = "google/gemini-2.0-flash-001",
    val endpoint: String = "https://openrouter.ai/api/v1/chat/completions",
    val budget: Int = 6000
)

object AiClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun stream(
        cfg: AiConfig,
        messages: List<Pair<String, String>>,
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val request = buildRequest(cfg, messages)
        val result = StringBuilder()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${raw.take(400)}")
            }
            raw.lineSequence().forEach { line ->
                if (!line.startsWith("data:")) return@forEach
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") return@forEach
                val chunk = extractChunk(cfg.provider, data)
                if (chunk.isNotEmpty()) {
                    result.append(chunk)
                    withContext(Dispatchers.Main) { onChunk(chunk) }
                }
            }
        }
        result.toString()
    }

    private fun buildRequest(cfg: AiConfig, messages: List<Pair<String, String>>): Request {
        val body = when (cfg.provider) {
            Provider.GEMINI -> {
                val contents = JSONArray()
                messages.forEach { (role, text) ->
                    val mapped = if (role == "assistant") "model" else "user"
                    contents.put(
                        JSONObject()
                            .put("role", mapped)
                            .put("parts", JSONArray().put(JSONObject().put("text", text)))
                    )
                }
                JSONObject()
                    .put("contents", contents)
                    .put("generationConfig", JSONObject().put("maxOutputTokens", cfg.budget))
                    .toString()
            }
            Provider.OPENROUTER, Provider.CUSTOM -> {
                val arr = JSONArray()
                messages.forEach { (role, text) ->
                    arr.put(JSONObject().put("role", role).put("content", text))
                }
                JSONObject()
                    .put("model", cfg.model)
                    .put("stream", true)
                    .put("messages", arr)
                    .put("max_tokens", cfg.budget)
                    .let {
                        if (cfg.provider == Provider.OPENROUTER) {
                            it.put("reasoning", JSONObject().put("effort", budgetLabel(cfg.budget)))
                        } else it
                    }
                    .toString()
            }
        }

        val url = if (cfg.provider == Provider.GEMINI) {
            "https://generativelanguage.googleapis.com/v1beta/models/${cfg.model}:streamGenerateContent?alt=sse&key=${cfg.apiKey}"
        } else cfg.endpoint

        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMedia))
            .header("Accept", "text/event-stream")
        if (cfg.apiKey.isNotBlank() && cfg.provider != Provider.GEMINI) {
            builder.header("Authorization", "Bearer ${cfg.apiKey}")
        }
        if (cfg.provider == Provider.OPENROUTER) {
            builder.header("HTTP-Referer", "https://bypass.studio.local")
                .header("X-Title", "Bypass Studio")
        }
        return builder.build()
    }

    private fun budgetLabel(budget: Int) = when {
        budget >= 12000 -> "high"
        budget >= 6000 -> "medium"
        budget >= 2000 -> "low"
        else -> "none"
    }

    private fun extractChunk(provider: Provider, data: String): String {
        return try {
            val root = JSONObject(data)
            when (provider) {
                Provider.GEMINI -> {
                    val candidates = root.optJSONArray("candidates") ?: return ""
                    val parts = candidates.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts") ?: return ""
                    parts.optJSONObject(0)?.optString("text", "") ?: ""
                }
                Provider.OPENROUTER, Provider.CUSTOM -> {
                    val choices = root.optJSONArray("choices") ?: return ""
                    val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return ""
                    delta.optString("content", delta.optString("reasoning_content", ""))
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun geminiGroundedAnswer(query: String, apiKey: String, model: String): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext "Gemini API key missing."
            val safeModel = model.ifBlank { "gemini-2.0-flash" }
            val body = JSONObject()
                .put("contents", JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", query)))
                ))
                .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 4096))
                .toString()
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$safeModel:generateContent?key=$apiKey"
            val req = Request.Builder().url(url).post(body.toRequestBody(jsonMedia)).build()
            try {
                client.newCall(req).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@withContext "Gemini grounding failed: HTTP ${response.code} ${raw.take(200)}"
                    val root = JSONObject(raw)
                    val text = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", "")
                        .orEmpty()
                    val meta = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("groundingMetadata")
                    val sb = StringBuilder(text.ifBlank { "No grounded answer." })
                    val chunks = meta?.optJSONArray("groundingChunks")
                    if (chunks != null && chunks.length() > 0) {
                        sb.append("\n\nGrounded sources:")
                        for (i in 0 until chunks.length()) {
                            val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                            sb.append("\n- ").append(web.optString("title"))
                                .append(" — ").append(web.optString("uri"))
                        }
                    }
                    sb.toString()
                }
            } catch (e: Exception) {
                "Gemini grounding error: ${e.message}"
            }
        }

    suspend fun webSearch(query: String, cfg: AiConfig? = null): String = withContext(Dispatchers.IO) {
        if (cfg?.provider == Provider.GEMINI && cfg.apiKey.isNotBlank()) {
            val grounded = geminiGroundedAnswer(query, cfg.apiKey, cfg.model)
            if (!grounded.startsWith("Gemini ")) return@withContext grounded
        }
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val sb = StringBuilder("Live web results for: ${query.trim()}\n\n")
        val urls = LinkedHashSet<String>()

        try {
            val html = Jsoup.connect("https://html.duckduckgo.com/html/?q=$q")
                .userAgent("Mozilla/5.0 BypassStudio/2.1")
                .timeout(15000).get().html()
            WebSearchParser.parseDuckDuckGo(html).forEach { result ->
                sb.append("- ").append(result.title).append(": ").append(result.snippet).append('\n')
                if (result.url.startsWith("http")) urls.add(result.url)
            }
        } catch (e: Exception) {
            sb.append("DuckDuckGo unavailable: ${e.message}\n")
        }

        try {
            val html = Jsoup.connect("https://www.bing.com/search?q=$q")
                .userAgent("Mozilla/5.0 BypassStudio/2.1")
                .timeout(15000).get().html()
            WebSearchParser.parseBing(html).forEach { result ->
                sb.append("- ").append(result.title).append(": ").append(result.snippet).append('\n')
                if (result.url.startsWith("http")) urls.add(result.url)
            }
        } catch (e: Exception) {
            sb.append("Bing unavailable: ${e.message}\n")
        }

        try {
            val xml = Jsoup.connect("https://news.google.com/rss/search?q=$q&hl=en-US&gl=US&ceid=US:en")
                .userAgent("Mozilla/5.0 BypassStudio/2.1")
                .parser(Parser.xmlParser())
                .timeout(15000).get().html()
            val items = WebSearchParser.parseNewsRss(xml)
            if (items.isNotEmpty()) {
                sb.append("\nRecent news:\n")
                items.forEach { (title, date) ->
                    sb.append("- ").append(title).append(" (").append(date).append(")\n")
                }
            }
        } catch (e: Exception) {
            sb.append("News RSS unavailable: ${e.message}\n")
        }

        urls.take(2).forEachIndexed { index, url ->
            try {
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 BypassStudio/2.0")
                    .timeout(15000).get()
                val text = doc.select("article").text().ifBlank { doc.body().text() }
                    .replace(Regex("\\s+"), " ").trim()
                sb.append("\nSource ${index + 1} (${url.take(160)}):\n")
                    .append(text.take(2200)).append('\n')
            } catch (e: Exception) {
                sb.append("\nSource fetch failed (${url.take(120)}): ${e.message}\n")
            }
        }
        sb.toString()
    }

    suspend fun fetchUrl(url: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 BypassStudio/2.0")
                .timeout(20000).get()
            val title = doc.title().trim()
            val text = doc.body().text().replace(Regex("\\s+"), " ").trim()
            "Fetched: $title\n${text.take(6000)}"
        } catch (e: Exception) {
            "Fetch error: ${e.message}"
        }
    }

    suspend fun discoverModels(cfg: AiConfig): List<String> = withContext(Dispatchers.IO) {
        when (cfg.provider) {
            Provider.OPENROUTER -> {
                if (cfg.apiKey.isBlank()) return@withContext listOf("google/gemini-2.0-flash-001", "deepseek/deepseek-chat-v3-0324:free")
                val req = Request.Builder()
                    .url("https://openrouter.ai/api/v1/models")
                    .header("Authorization", "Bearer ${cfg.apiKey}")
                    .build()
                try {
                    client.newCall(req).execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        if (!response.isSuccessful) return@withContext emptyList()
                        val data = JSONObject(raw).optJSONArray("data") ?: return@withContext emptyList()
                        val out = mutableListOf<String>()
                        for (i in 0 until data.length()) {
                            val id = data.optJSONObject(i)?.optString("id", "").orEmpty()
                            if (id.endsWith(":free")) out.add(id)
                        }
                        out.sorted().take(40)
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            Provider.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
            Provider.CUSTOM -> discoverOllamaModels(cfg.endpoint)
        }
    }

    private fun discoverOllamaModels(endpoint: String): List<String> {
        if (endpoint.isBlank()) return emptyList()
        val base = when {
            endpoint.contains("/v1/chat/completions") -> endpoint.substringBefore("/v1/chat/completions")
            endpoint.contains("/api/chat") -> endpoint.substringBefore("/api/chat")
            else -> endpoint.substringBeforeLast('/')
        }
        val urls = listOf("$base/api/tags", "$base/v1/models")
        for (url in urls) {
            try {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use
                    val root = JSONObject(raw)
                    val arr = root.optJSONArray("models") ?: return@use
                    val out = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val name = item.optString("name", item.optString("id", ""))
                        if (name.isNotBlank()) out.add(name)
                    }
                    if (out.isNotEmpty()) throw FoundModels(out)
                }
            } catch (e: FoundModels) {
                return e.models
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private class FoundModels(val models: List<String>) : Exception()
}
