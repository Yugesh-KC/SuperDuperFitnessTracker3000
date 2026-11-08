package com.example.superduperfitnesstracker3000.ai

import com.example.superduperfitnesstracker3000.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NoApiKeyException : Exception("No Gemini API key set. Add one in Settings.")

class GeminiException(message: String) : Exception(message)

private class GeminiHttpException(val status: Int, override val message: String) : Exception(message) {
    /** Errors where trying a different key might succeed (quota, invalid/blocked key, server overload). */
    val tryNextKey: Boolean
        get() = status in setOf(401, 403, 429, 500, 502, 503, 504) ||
            (status == 400 && message.contains("API key", ignoreCase = true))
}

/**
 * Minimal Gemini REST client. Requests are spread across all configured keys round-robin;
 * if a key fails with a quota/auth/server error the next key is tried, until all keys are exhausted.
 */
class GeminiClient(private val settings: SettingsRepository) {

    suspend fun generate(
        contents: JSONArray,
        systemInstruction: String? = null,
        responseSchema: JSONObject? = null,
        temperature: Double = 0.2,
    ): String = withContext(Dispatchers.IO) {
        val keys = settings.current.apiKeys
        if (keys.isEmpty()) throw NoApiKeyException()
        val body = JSONObject().apply {
            put("contents", contents)
            if (systemInstruction != null) {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            }
            if (responseSchema != null) {
                put(
                    "generationConfig", JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", responseSchema)
                        .put("temperature", temperature)
                )
            }
        }.toString()

        val start = settings.nextKeyIndex.mod(keys.size)
        val errors = mutableListOf<String>()
        for (i in keys.indices) {
            val idx = (start + i) % keys.size
            try {
                val text = post(keys[idx], settings.current.model, body)
                settings.nextKeyIndex = (idx + 1) % keys.size
                return@withContext text
            } catch (e: GeminiHttpException) {
                errors += "Key ${idx + 1} (…${keys[idx].takeLast(4)}): ${e.message}"
                if (!e.tryNextKey) {
                    settings.nextKeyIndex = (idx + 1) % keys.size
                    throw GeminiException(e.message)
                }
            } catch (e: IOException) {
                throw GeminiException("Network error: ${e.message}")
            }
        }
        settings.nextKeyIndex = (start + 1) % keys.size
        throw GeminiException(
            if (keys.size == 1) errors.first() else "All ${keys.size} API keys failed:\n" + errors.joinToString("\n")
        )
    }

    /** Sends a tiny request with one specific key. Returns null on success, or the error message. */
    suspend fun testKey(key: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("contents", userContent("Reply with the single word OK.")).toString()
        try {
            post(key, settings.current.model, body)
            null
        } catch (e: Exception) {
            e.message ?: e.toString()
        }
    }

    /** Lists model ids that support generateContent, using the first working key. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val keys = settings.current.apiKeys
        if (keys.isEmpty()) throw NoApiKeyException()
        var lastError: Exception? = null
        for (key in keys) {
            try {
                val conn = (URL("$BASE/models?pageSize=1000").openConnection() as HttpURLConnection).apply {
                    setRequestProperty("x-goog-api-key", key)
                    connectTimeout = 20_000
                    readTimeout = 30_000
                }
                val json = JSONObject(readResponse(conn))
                val models = json.optJSONArray("models") ?: JSONArray()
                return@withContext List(models.length()) { models.getJSONObject(it) }
                    .filter { m ->
                        val methods = m.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                        (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                    }
                    .map { it.getString("name").removePrefix("models/") }
                    .filter { it.startsWith("gemini") }
                    .sorted()
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw GeminiException(lastError?.message ?: "Could not list models")
    }

    private fun post(key: String, model: String, body: String): String {
        val conn = (URL("$BASE/models/$model:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", key)
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return extractText(JSONObject(readResponse(conn)))
    }

    private fun readResponse(conn: HttpURLConnection): String {
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                    .getOrElse { "HTTP $code" }
                throw GeminiHttpException(code, msg.lineSequence().first())
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun extractText(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason")
            throw GeminiException(if (reason.isNullOrEmpty()) "Empty response from Gemini" else "Blocked by Gemini: $reason")
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (!part.optBoolean("thought")) append(part.optString("text"))
            }
        }
        if (text.isBlank()) {
            throw GeminiException("Gemini returned no text (finish reason: ${candidate.optString("finishReason", "unknown")})")
        }
        return text
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

        fun userContent(text: String): JSONArray =
            JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text))))
    }
}
