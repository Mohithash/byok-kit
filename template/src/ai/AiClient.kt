package __PKG__.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

enum class AiProvider(val label: String, val defaultModel: String, val defaultBaseUrl: String) {
    ANTHROPIC("Anthropic (Claude)", "claude-opus-5", "https://api.anthropic.com"),
    OPENAI_COMPAT("OpenAI‑compatible", "gpt-4o-mini", "https://api.openai.com"),
}

@Serializable
data class AiSettings(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
) {
    val effectiveModel get() = model.ifBlank { provider.defaultModel }
    val effectiveBaseUrl get() = baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
    val configured get() = apiKey.isNotBlank()
}

/** One turn of a conversation. [imageJpegBase64] attaches a photo to a user turn. */
data class ChatMsg(val role: String, val text: String, val imageJpegBase64: String? = null)

/**
 * Bring‑your‑own‑key client. Raw HTTP keeps the APK small; the key goes only to the
 * provider the user picked. Supports plain text and JSON‑schema constrained replies.
 */
class AiClient(val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }) {

    class AiException(msg: String) : Exception(msg)

    suspend fun chat(
        s: AiSettings,
        system: String,
        messages: List<ChatMsg>,
        schema: JsonObject? = null,
        maxTokens: Int = 4096,
    ): String = withContext(Dispatchers.IO) {
        if (!s.configured) throw AiException("Add your API key in Settings first.")
        when (s.provider) {
            AiProvider.ANTHROPIC -> anthropic(s, system, messages, schema, maxTokens)
            AiProvider.OPENAI_COMPAT -> openai(s, system, messages, schema, maxTokens)
        }
    }

    /** Convenience: one user prompt, JSON reply decoded into [T]. */
    suspend inline fun <reified T> ask(s: AiSettings, system: String, user: String, schema: JsonObject, image: String? = null, maxTokens: Int = 4096): T =
        json.decodeFromString<T>(extractJson(chat(s, system, listOf(ChatMsg("user", user, image)), schema, maxTokens)))

    fun extractJson(text: String): String {
        val a = text.indexOf('{'); val b = text.lastIndexOf('}')
        val c = text.indexOf('['); val d = text.lastIndexOf(']')
        return when {
            a >= 0 && b > a && (c < 0 || a < c) -> text.substring(a, b + 1)
            c >= 0 && d > c -> text.substring(c, d + 1)
            else -> throw AiException("Model did not return JSON.")
        }
    }

    private fun anthropic(s: AiSettings, system: String, messages: List<ChatMsg>, schema: JsonObject?, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", s.effectiveModel)
            put("max_tokens", maxTokens)
            put("system", system)
            putJsonObject("output_config") {
                put("effort", "low")
                if (schema != null) putJsonObject("format") { put("type", "json_schema"); put("schema", schema) }
            }
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", buildJsonArray {
                            if (m.imageJpegBase64 != null) add(buildJsonObject {
                                put("type", "image")
                                putJsonObject("source") { put("type", "base64"); put("media_type", "image/jpeg"); put("data", m.imageJpegBase64) }
                            })
                            add(buildJsonObject { put("type", "text"); put("text", m.text) })
                        })
                    })
                }
            })
        }
        val resp = post("${s.effectiveBaseUrl}/v1/messages", body.toString(), mapOf("x-api-key" to s.apiKey, "anthropic-version" to "2023-06-01"))
        val obj = json.parseToJsonElement(resp).jsonObject
        obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content?.let { throw AiException(it) }
        if (obj["stop_reason"]?.jsonPrimitive?.content == "refusal") throw AiException("The model declined this request.")
        return obj["content"]?.jsonArray?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            ?.ifBlank { null } ?: throw AiException("Empty response from model.")
    }

    private fun openai(s: AiSettings, system: String, messages: List<ChatMsg>, schema: JsonObject?, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", s.effectiveModel)
            put("max_tokens", maxTokens)
            if (schema != null) putJsonObject("response_format") { put("type", "json_object") }
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", if (schema != null) "$system\n\nRespond with JSON only matching this schema: $schema" else system) })
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        if (m.imageJpegBase64 == null) put("content", m.text) else put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", m.text) })
                            add(buildJsonObject { put("type", "image_url"); putJsonObject("image_url") { put("url", "data:image/jpeg;base64,${m.imageJpegBase64}") } })
                        })
                    })
                }
            })
        }
        val resp = post("${s.effectiveBaseUrl}/v1/chat/completions", body.toString(), mapOf("Authorization" to "Bearer ${s.apiKey}"))
        val obj = json.parseToJsonElement(resp).jsonObject
        obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content?.let { throw AiException(it) }
        return obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw AiException("Empty response from model.")
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 120_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()
                throw AiException(msg ?: "HTTP $code: ${text.take(200)}")
            }
            return text
        } catch (e: java.io.IOException) {
            throw AiException("Network error: ${e.message}")
        } finally { conn.disconnect() }
    }
}

/** Tiny JSON-schema builder helpers so app code stays readable. */
object Schema {
    fun obj(vararg props: Pair<String, JsonObject>, required: List<String> = props.map { it.first }): JsonObject = buildJsonObject {
        put("type", "object"); put("additionalProperties", false)
        put("required", buildJsonArray { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
    }
    fun arr(items: JsonObject): JsonObject = buildJsonObject { put("type", "array"); put("items", items) }
    val str: JsonObject get() = buildJsonObject { put("type", "string") }
    val int: JsonObject get() = buildJsonObject { put("type", "integer") }
    val num: JsonObject get() = buildJsonObject { put("type", "number") }
    val bool: JsonObject get() = buildJsonObject { put("type", "boolean") }
    fun enum(vararg v: String): JsonObject = buildJsonObject { put("type", "string"); put("enum", buildJsonArray { v.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }) }
}
