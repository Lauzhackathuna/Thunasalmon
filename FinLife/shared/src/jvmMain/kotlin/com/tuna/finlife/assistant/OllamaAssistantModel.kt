package com.tuna.finlife.assistant

import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The local Ollama chat API (`/api/chat`). No cloud endpoint and no credentials: the address is fixed to this
 * machine; only the model can be changed, with the FINLIFE_OLLAMA_MODEL environment variable.
 */
internal class OllamaAssistantModel(
    override val modelName: String = DEFAULT_MODEL,
    private val baseUrl: String = LOCAL_URL,
    private val checkTimeout: Duration = Duration.ofSeconds(3),
    private val requestTimeout: Duration = Duration.ofSeconds(120),
) : AssistantModel {
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

    override suspend fun check(): ModelFailure? {
        val request = HttpRequest.newBuilder(URI("$baseUrl/api/tags")).timeout(checkTimeout).GET().build()
        val body = when (val outcome = send(request)) {
            is Outcome.Failed -> return outcome.failure
            is Outcome.Response -> if (outcome.status == 200) outcome.body else return ModelFailure.ServerError
        }
        val names = try {
            Json.parseToJsonElement(body).jsonObject["models"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
        } catch (_: IllegalArgumentException) {
            return ModelFailure.Malformed
        }
        val wanted = if (':' in modelName) setOf(modelName) else setOf(modelName, "$modelName:latest")
        return if (names.any { it in wanted }) null else ModelFailure.ModelMissing
    }

    override suspend fun extract(system: String, turns: List<ChatTurn>): ModelResult<Extraction> {
        val content = when (val result = chat(system, turns, EXTRACTION_SCHEMA)) {
            is ModelResult.Ok -> result.value
            is ModelResult.Failed -> return result
        }
        return parseExtraction(content)?.let { ModelResult.Ok(it) } ?: ModelResult.Failed(ModelFailure.Malformed)
    }

    /**
     * The answer is requested as {"answer": "..."}: a JSON format leaves no room for a reasoning preamble, which
     * thinking-only models (such as qwen3:4b) otherwise write into the reply even with thinking switched off.
     */
    override suspend fun explain(system: String, user: String): ModelResult<String> {
        val content = when (val result = chat(system, listOf(ChatTurn(Speaker.User, user)), ANSWER_SCHEMA)) {
            is ModelResult.Ok -> result.value
            is ModelResult.Failed -> return result
        }
        val answer = parseAnswer(content)
        return if (answer.isNullOrBlank()) ModelResult.Failed(ModelFailure.Malformed) else ModelResult.Ok(answer.trim())
    }

    private suspend fun chat(system: String, turns: List<ChatTurn>, format: JsonObject?): ModelResult<String> {
        var outcome = send(chatRequest(chatBody(system, turns, format, think = false)))
        // Models without a thinking mode may refuse the "think" field: ask again without it.
        if (outcome is Outcome.Response && outcome.status == 400 && "think" in outcome.body) {
            outcome = send(chatRequest(chatBody(system, turns, format, think = null)))
        }
        val body = when (outcome) {
            is Outcome.Failed -> return ModelResult.Failed(outcome.failure)
            is Outcome.Response -> when (outcome.status) {
                200 -> outcome.body
                404 -> return ModelResult.Failed(ModelFailure.ModelMissing)
                else -> return ModelResult.Failed(ModelFailure.ServerError)
            }
        }
        val content = try {
            Json.parseToJsonElement(body).jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        } catch (_: IllegalArgumentException) {
            null
        } ?: return ModelResult.Failed(ModelFailure.Malformed)
        return ModelResult.Ok(stripThinking(content))
    }

    internal fun chatBody(system: String, turns: List<ChatTurn>, format: JsonObject?, think: Boolean?): JsonObject = buildJsonObject {
        put("model", modelName)
        put("stream", false)
        if (think != null) put("think", think)
        putJsonObject("options") {
            put("temperature", 0)
            // Bounds the time of an answer on CPU; a cut answer is not valid JSON and is reported as unreadable.
            put("num_predict", MAX_ANSWER_TOKENS)
            put("num_ctx", CONTEXT_TOKENS)
        }
        if (format != null) put("format", format)
        putJsonArray("messages") {
            addJsonObject { put("role", "system"); put("content", system) }
            for (turn in turns) addJsonObject {
                put("role", if (turn.speaker == Speaker.User) "user" else "assistant")
                put("content", turn.text)
            }
        }
    }

    private fun chatRequest(body: JsonObject): HttpRequest = HttpRequest.newBuilder(URI("$baseUrl/api/chat"))
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build()

    private sealed interface Outcome {
        class Response(val status: Int, val body: String) : Outcome
        class Failed(val failure: ModelFailure) : Outcome
    }

    private suspend fun send(request: HttpRequest): Outcome = try {
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        Outcome.Response(response.statusCode(), response.body())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failed(failureOf(e))
    }

    companion object {
        const val LOCAL_URL = "http://localhost:11434"
        const val DEFAULT_MODEL = "qwen3:4b"
        const val MODEL_VARIABLE = "FINLIFE_OLLAMA_MODEL"
        const val MAX_ANSWER_TOKENS = 700
        const val CONTEXT_TOKENS = 8192

        fun fromEnvironment(): OllamaAssistantModel =
            OllamaAssistantModel(System.getenv(MODEL_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL)

        /** JSON schema Ollama constrains the routing answer to. */
        val EXTRACTION_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("topic") {
                    put("type", "string")
                    putJsonArray("enum") { Topic.entries.forEach { add(it.wire) } }
                }
                putJsonObject("month") { putJsonArray("type") { add("integer"); add("null") } }
                putJsonObject("end_month") { putJsonArray("type") { add("integer"); add("null") } }
                putJsonObject("months_ahead") { putJsonArray("type") { add("integer"); add("null") } }
                putJsonObject("clarification") { putJsonArray("type") { add("string"); add("null") } }
            }
            putJsonArray("required") { listOf("topic", "month", "end_month", "months_ahead", "clarification").forEach { add(it) } }
        }

        /** JSON schema of an explanation. */
        val ANSWER_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { putJsonObject("answer") { put("type", "string") } }
            putJsonArray("required") { add("answer") }
        }

        private val thinking = Regex("""<think>[\s\S]*?</think>""")

        /** Drops reasoning blocks, including one whose opening tag the model's template already supplied. */
        internal fun stripThinking(content: String): String {
            val closed = content.replace(thinking, "")
            val end = closed.lastIndexOf("</think>")
            return (if (end >= 0) closed.substring(end + "</think>".length) else closed).trim()
        }

        internal fun parseAnswer(content: String): String? = try {
            ((Json.parseToJsonElement(content) as? JsonObject)?.get("answer") as? JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (_: IllegalArgumentException) {
            null
        }

        internal fun parseExtraction(content: String): Extraction? {
            val json = try {
                Json.parseToJsonElement(content) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null
            val topic = (json["topic"] as? JsonPrimitive)?.contentOrNull?.let { Topic.fromWire(it.trim()) } ?: return null
            return Extraction(topic, json["month"].asMonth(), json["end_month"].asMonth(),
                (json["clarification"] as? JsonPrimitive)?.takeIf { it.isString }?.content, json["months_ahead"].asMonth())
        }

        /** Whole numbers only (3, 3.0 or "3"); anything else counts as missing. */
        private fun JsonElement?.asMonth(): Int? {
            val primitive = this as? JsonPrimitive ?: return null
            if (primitive is JsonNull) return null
            val value = primitive.content.trim().toDoubleOrNull() ?: primitive.doubleOrNull ?: return null
            return value.takeIf { it == kotlin.math.floor(it) && it in -1e6..1e6 }?.toInt()
        }

        private fun failureOf(error: Throwable): ModelFailure {
            var cause: Throwable? = error
            while (cause != null) {
                when (cause) {
                    is HttpConnectTimeoutException -> return ModelFailure.NotRunning
                    is HttpTimeoutException -> return ModelFailure.Timeout
                    is ConnectException -> return ModelFailure.NotRunning
                }
                cause = cause.cause
            }
            return if (error is IOException || error.cause is IOException) ModelFailure.NotRunning else ModelFailure.ServerError
        }
    }
}
