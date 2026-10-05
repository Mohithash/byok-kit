package __PKG__.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

enum class AiProvider(val label: String, val defaultModel: String, val defaultBaseUrl: String, val suggestedModels: List<String>) {
    ANTHROPIC("Anthropic (Claude)", "claude-opus-5-5", "https://api.anthropic.com",
        listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-4-5", "claude-fable-5-1")),
    OPENAI_COMPAT("OpenAI‑compatible", "gpt-4o-mini", "https://api.openai.com", listOf("gpt-4o-mini", "gpt-4o")),
}

@Serializable
data class AiSettings(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
) {
    val effectiveModel get() = model.ifBlank { provider.defaultModel }
    /** Base URL without a trailing slash or trailing "/v1" (the client adds the API version itself). */
    val effectiveBaseUrl get() = baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
        .let { if (it.endsWith("/v1", ignoreCase = true)) it.dropLast(3).trimEnd('/') else it }
    /**
     * Root the OpenAI-compatible paths hang off: `<base>/v1`, unless the base URL already ends in its own API version
     * (`…/v2`, `…/v1beta`) or is a versioned `…/openai` compatibility path (Gemini's `…/v1beta/openai`, Cloudflare AI
     * Gateway's `…/v1/<account>/<gateway>/openai`). A version segment elsewhere (Cloudflare Workers AI's
     * `…/client/v4/accounts/<id>/ai`) doesn't count, so that one still gets `/v1`.
     */
    val openAiRoot: String get() = effectiveBaseUrl.let { b ->
        val path = "/" + b.substringAfter("://").substringAfter('/', "")
        val version = Regex("/v\\d+[a-z0-9]*(?=/|$)", RegexOption.IGNORE_CASE)
        val endsVersioned = Regex("/v\\d+[a-z0-9]*$", RegexOption.IGNORE_CASE).containsMatchIn(path)
        if (endsVersioned || (path.endsWith("/openai", ignoreCase = true) && version.containsMatchIn(path))) b else "$b/v1"
    }
    /** OpenAI's own API (as opposed to another OpenAI-compatible server). */
    val isOpenAi: Boolean get() = provider == AiProvider.OPENAI_COMPAT && effectiveBaseUrl.contains("api.openai.com", ignoreCase = true)
    val configured get() = apiKey.isNotBlank()
}

/** One turn of a conversation. [imageJpegBase64] attaches a photo to a user turn. */
data class ChatMsg(val role: String, val text: String, val imageJpegBase64: String? = null)

/** Token counts reported by the provider for one request (0 when the provider doesn't say). */
data class AiUsage(val inputTokens: Long = 0, val outputTokens: Long = 0)

/** A model reply: its text plus usage and the model that actually answered. */
data class AiReply(val text: String, val usage: AiUsage = AiUsage(), val model: String = "")

/**
 * Bring‑your‑own‑key client. Raw HTTP keeps the APK small; the key goes only to the
 * provider the user picked. Supports plain text and JSON‑schema constrained replies.
 * Transient failures (429, 5xx, 529 overloaded, dropped connections) are retried with backoff,
 * and a cancelled coroutine aborts the in-flight request.
 */
class AiClient(
    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true },
    /** How long a generation may take; answers aren't streamed, so nothing arrives until the whole document is written. */
    private val generationTimeoutMs: Int = 600_000,
) {

    class AiException(msg: String, val status: Int = 0, val retryAfterSeconds: Long? = null) : Exception(msg)

    /** Called after every successful request — the app uses it to keep a running usage tally. */
    @Volatile var onUsage: ((AiUsage) -> Unit)? = null

    /** Set once a server rejects the refusal-fallback beta, so we stop sending it for this process. */
    @Volatile private var fallbacksRejected = false

    suspend fun chat(
        s: AiSettings,
        system: String,
        messages: List<ChatMsg>,
        schema: JsonObject? = null,
        maxTokens: Int = 4096,
    ): String = chatFull(s, system, messages, schema, maxTokens).text

    suspend fun chatFull(
        s: AiSettings,
        system: String,
        messages: List<ChatMsg>,
        schema: JsonObject? = null,
        maxTokens: Int = 4096,
    ): AiReply = withContext(Dispatchers.IO) {
        if (!s.configured) throw AiException("Add your API key in Settings first.")
        val reply = withRetry {
            when (s.provider) {
                AiProvider.ANTHROPIC -> anthropic(s, system, messages, schema, maxTokens)
                AiProvider.OPENAI_COMPAT -> openai(s, system, messages, schema, maxTokens)
            }
        }
        onUsage?.invoke(reply.usage)
        reply
    }

    /** Convenience: one user prompt, JSON reply decoded into [T]. */
    suspend inline fun <reified T> ask(s: AiSettings, system: String, user: String, schema: JsonObject, image: String? = null, maxTokens: Int = 4096): T =
        json.decodeFromString<T>(extractJson(chat(s, system, listOf(ChatMsg("user", user, image)), schema, maxTokens)))

    /** Model ids the key can use, newest first as the provider lists them. */
    suspend fun listModels(s: AiSettings): List<String> = withContext(Dispatchers.IO) {
        if (!s.configured) throw AiException("Add your API key first.")
        val (url, headers) = when (s.provider) {
            AiProvider.ANTHROPIC -> "${s.effectiveBaseUrl}/v1/models?limit=100" to mapOf("x-api-key" to s.apiKey, "anthropic-version" to "2023-06-01")
            AiProvider.OPENAI_COMPAT -> "${s.openAiRoot}/models" to mapOf("Authorization" to "Bearer ${s.apiKey}")
        }
        val obj = json.parseToJsonElement(withRetry { request("GET", url, null, headers) }).jsonObject
        obj["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.orEmpty()
    }

    fun extractJson(text: String): String {
        val a = text.indexOf('{'); val b = text.lastIndexOf('}')
        val c = text.indexOf('['); val d = text.lastIndexOf(']')
        return when {
            a >= 0 && b > a && (c < 0 || a < c) -> text.substring(a, b + 1)
            c >= 0 && d > c -> text.substring(c, d + 1)
            else -> throw AiException("Model did not return JSON.")
        }
    }

    /** Retries transient failures: 408, 409, 429, 5xx, 529 and network errors, up to [attempts] tries. */
    private suspend fun <T> withRetry(attempts: Int = 3, block: suspend () -> T): T {
        var last: AiException? = null
        repeat(attempts) { i ->
            try { return block() } catch (e: AiException) {
                if (!isRetryable(e) || i == attempts - 1) throw e
                last = e
                val waitMs = (e.retryAfterSeconds?.times(1000) ?: (1500L shl i)).coerceIn(500L, 20_000L)
                delay(waitMs)
            }
        }
        throw last ?: AiException("Request failed.")
    }

    private fun isRetryable(e: AiException) = e.status == NETWORK || e.status == 408 || e.status == 409 || e.status == 429 || e.status >= 500

    private fun fallbacksFor(s: AiSettings): Boolean =
        !fallbacksRejected && s.baseUrl.isBlank() && s.effectiveModel in FALLBACK_MODELS

    /** OpenAI-compatible roots that asked for max_completion_tokens instead of max_tokens. */
    private val completionTokenRoots: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Set once an OpenAI model rejects `reasoning_effort`, so we stop sending it for this process. */
    @Volatile private var reasoningRejected = false

    /** Set once a model rejects `output_config.effort`, so we stop sending it for this process. */
    @Volatile private var effortRejected = false

    /** `effort` is not accepted by Haiku 4.5, Sonnet 4.5 or Claude 3.x models. */
    private fun effortFor(model: String): Boolean =
        !effortRejected && !model.contains("haiku") && !model.contains("sonnet-4-5") && !model.startsWith("claude-3")

    private suspend fun anthropic(s: AiSettings, system: String, messages: List<ChatMsg>, schema: JsonObject?, maxTokens: Int): AiReply {
        val useFallbacks = fallbacksFor(s)
        val useEffort = effortFor(s.effectiveModel)
        val body = buildJsonObject {
            put("model", s.effectiveModel)
            put("max_tokens", maxTokens)
            put("system", system)
            if (useFallbacks) put("fallbacks", "default")
            if (useEffort || schema != null) putJsonObject("output_config") {
                if (useEffort) put("effort", "low")
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
        val headers = buildMap {
            put("x-api-key", s.apiKey); put("anthropic-version", "2023-06-01")
            if (useFallbacks) put("anthropic-beta", FALLBACK_BETA)
        }
        val resp = try {
            request("POST", "${s.effectiveBaseUrl}/v1/messages", body.toString(), headers)
        } catch (e: AiException) {
            // An org or proxy that doesn't accept the refusal-fallback beta: drop it and send the plain request.
            val msg = e.message.orEmpty()
            if (useFallbacks && e.status == 400 && (msg.contains("fallback", true) || msg.contains("anthropic-beta", true))) {
                fallbacksRejected = true
                return anthropic(s, system, messages, schema, maxTokens)
            }
            if (useEffort && e.status == 400 && msg.contains("effort", true)) {
                effortRejected = true
                return anthropic(s, system, messages, schema, maxTokens)
            }
            throw e
        }
        val obj = json.parseToJsonElement(resp).jsonObject
        obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull?.let { throw AiException(it) }
        val u = obj["usage"]?.jsonObject
        val usage = AiUsage(u.long("input_tokens") + u.long("cache_read_input_tokens") + u.long("cache_creation_input_tokens"), u.long("output_tokens"))
        when (obj["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "refusal" -> {
                val why = obj["stop_details"]?.let { runCatching { it.jsonObject["explanation"]?.jsonPrimitive?.contentOrNull }.getOrNull() }
                billedFailure(usage, "The model declined this request." + (why?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""))
            }
            "max_tokens" -> if (schema != null) billedFailure(usage, TOO_LONG)
        }
        val text = obj["content"]?.jsonArray?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            ?.ifBlank { null } ?: billedFailure(usage, "Empty response from model.")
        return AiReply(text, usage, obj["model"]?.jsonPrimitive?.contentOrNull ?: s.effectiveModel)
    }

    private suspend fun openai(s: AiSettings, system: String, messages: List<ChatMsg>, schema: JsonObject?, maxTokens: Int): AiReply {
        // OpenAI's reasoning models (o-series, gpt-5…) reject max_tokens; its API accepts max_completion_tokens for every
        // chat model, and reasoning tokens count against it. Other servers mostly only know max_tokens and often cap it at 8K.
        val completionTokens = s.isOpenAi || s.openAiRoot in completionTokenRoots
        // Reasoning models only; "-chat" snapshots (gpt-5-chat-latest…) are non-reasoning and reject the parameter.
        val reasoning = s.isOpenAi && !reasoningRejected && Regex("^(o\\d|gpt-5)", RegexOption.IGNORE_CASE).containsMatchIn(s.effectiveModel) &&
            !s.effectiveModel.contains("chat", ignoreCase = true)
        val body = buildJsonObject {
            put("model", s.effectiveModel)
            val limit = if (s.isOpenAi) maxTokens else maxTokens.coerceAtMost(8000)
            if (completionTokens) put("max_completion_tokens", limit) else put("max_tokens", limit)
            if (reasoning) put("reasoning_effort", "low")
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
        val resp = try {
            request("POST", "${s.openAiRoot}/chat/completions", body.toString(), mapOf("Authorization" to "Bearer ${s.apiKey}"))
        } catch (e: AiException) {
            // A gateway in front of an OpenAI reasoning model: switch parameter once and remember it.
            if (!completionTokens && e.status == 400 && e.message.orEmpty().contains("max_completion_tokens")) {
                completionTokenRoots += s.openAiRoot
                return openai(s, system, messages, schema, maxTokens)
            }
            if (reasoning && e.status == 400 && e.message.orEmpty().contains("reasoning_effort")) {
                reasoningRejected = true
                return openai(s, system, messages, schema, maxTokens)
            }
            throw e
        }
        val obj = json.parseToJsonElement(resp).jsonObject
        obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull?.let { throw AiException(it) }
        val u = obj["usage"]?.jsonObject
        val usage = AiUsage(u.long("prompt_tokens"), u.long("completion_tokens"))
        val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        if (schema != null && choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull == "length") billedFailure(usage, TOO_LONG)
        val text = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: billedFailure(usage, "Empty response from model.")
        return AiReply(text, usage, obj["model"]?.jsonPrimitive?.contentOrNull ?: s.effectiveModel)
    }

    /** The provider answered (and billed) but the answer can't be used: count the tokens, then fail. */
    private fun billedFailure(usage: AiUsage, message: String): Nothing {
        if (usage.inputTokens + usage.outputTokens > 0) onUsage?.invoke(usage)
        throw AiException(message)
    }

    private fun JsonObject?.long(key: String): Long = this?.get(key)?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() } ?: 0L

    /** Blocking HTTP call made cancellable: cancelling the coroutine disconnects the socket at once. */
    private suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>): String =
        suspendCancellableCoroutine { cont ->
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                // Answers aren't streamed, so nothing arrives until the whole document is written: allow long generations.
                requestMethod = method; connectTimeout = 20_000; readTimeout = if (body != null) generationTimeoutMs else 60_000; doOutput = body != null
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            cont.invokeOnCancellation { runCatching { conn.disconnect() } }
            Dispatchers.IO.asExecutor().execute { cont.resumeWith(runCatching { blockingCall(conn, body) }) }
        }

    private fun blockingCall(conn: HttpURLConnection, body: String?): String {
        var sent = false
        try {
            if (body != null) { conn.outputStream.use { it.write(body.toByteArray()) }; sent = true }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
                val retryAfter = conn.getHeaderField("retry-after")?.trim()?.toLongOrNull()
                throw AiException(friendly(code, msg ?: text.take(200)), code, retryAfter)
            }
            return text
        } catch (e: java.net.SocketTimeoutException) {
            // Timing out after the request went out is not retried: re-sending would start (and bill) the same long answer again.
            if (sent) throw AiException("The provider took too long to answer. Try a faster model, or ask for a shorter answer.")
            throw AiException("Network error: ${e.message}", NETWORK)
        } catch (e: java.io.IOException) {
            throw AiException("Network error: ${e.message}", NETWORK)
        } finally { conn.disconnect() }
    }

    private fun friendly(code: Int, msg: String): String = when (code) {
        401 -> "Your API key was rejected ($msg). Check it in Settings."
        403 -> "This key isn't allowed to do that ($msg)."
        404 -> "Not found — check the model name and base URL in Settings ($msg)."
        429 -> "Rate limited by your provider. Try again in a moment. ($msg)"
        529 -> "The provider is overloaded right now. Try again shortly."
        else -> if (code >= 500) "Provider error $code. Try again shortly." else "HTTP $code: $msg"
    }

    companion object {
        const val NETWORK = -1
        const val TOO_LONG = "The answer ran past the length limit and was cut off. Try again, or ask for less."
        /** Server-side refusal fallback ("default" routing) — Claude API only, these models. */
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        val FALLBACK_MODELS = setOf("claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5")
    }
}

/** Tiny JSON-schema builder helpers so app code stays readable. */
object Schema {
    fun obj(vararg props: Pair<String, JsonObject>, required: List<String> = props.map { it.first }): JsonObject = buildJsonObject {
        put("type", "object"); put("additionalProperties", false)
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
    }
    fun arr(items: JsonObject): JsonObject = buildJsonObject { put("type", "array"); put("items", items) }
    val str: JsonObject get() = buildJsonObject { put("type", "string") }
    val int: JsonObject get() = buildJsonObject { put("type", "integer") }
    val num: JsonObject get() = buildJsonObject { put("type", "number") }
    val bool: JsonObject get() = buildJsonObject { put("type", "boolean") }
    fun enum(vararg v: String): JsonObject = buildJsonObject { put("type", "string"); put("enum", buildJsonArray { v.forEach { add(JsonPrimitive(it)) } }) }
}
