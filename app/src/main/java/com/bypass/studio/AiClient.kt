package com.bypass.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
                val extra = if (cfg.provider == Provider.OPENROUTER) {
                    JSONObject().put("reasoning", JSONObject().put("effort", budgetLabel(cfg.budget)))
                } else JSONObject()
                JSONObject()
                    .put("model", cfg.model)
                    .put("stream", true)
                    .put("messages", arr)
                    .put("max_tokens", cfg.budget)
                    .let { if (cfg.provider == Provider.OPENROUTER) it.put("reasoning", extra.getJSONObject("reasoning")) else it }
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

    private fun extractChunk(provider: Provider, data: String): String = try {
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

    suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&format=json&srlimit=3"
        val req = Request.Builder().url(url).header("User-Agent", "BypassStudio/1.0").build()
        try {
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Search failed: HTTP ${response.code}"
                val root = JSONObject(response.body?.string().orEmpty())
                val results = root.optJSONObject("query")?.optJSONArray("search") ?: return@withContext "No results."
                val sb = StringBuilder("Web grounding results:\n")
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val snippet = item.optString("snippet", "").replace(Regex("<[^>]+>"), "")
                    sb.append("- ").append(item.optString("title")).append(": ").append(snippet).append('\n')
                }
                sb.toString()
            }
        } catch (e: Exception) {
            "Search error: ${e.message}"
        }
    }
}
