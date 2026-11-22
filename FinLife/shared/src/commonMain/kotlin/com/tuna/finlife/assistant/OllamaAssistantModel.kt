package com.tuna.finlife.assistant

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
 * The Ollama chat API (`/api/chat`) on the developer's own machine. No cloud endpoint and no credentials:
 * desktop uses localhost, the Android emulator the host machine's 10.0.2.2. Each platform supplies the
 * [transport] that sends the requests; everything else is shared.
 */
internal class OllamaAssistantModel(
    override val modelName: String = DEFAULT_MODEL,
    private val baseUrl: String = LOCAL_URL,
    private val transport: OllamaTransport,
    private val checkTimeoutMillis: Long = 3_000,
    private val requestTimeoutMillis: Long = 120_000,
) : AssistantModel {
    override suspend fun check(): ModelFailure? {
        val body = when (val outcome = transport.get("$baseUrl/api/tags", checkTimeoutMillis)) {
            is HttpOutcome.Failed -> return outcome.failure
            is HttpOutcome.Response -> if (outcome.status == 200) outcome.body else return ModelFailure.ServerError
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
        var outcome = post(chatBody(system, turns, format, think = false))
        // Models without a thinking mode may refuse the "think" field: ask again without it.
        if (outcome is HttpOutcome.Response && outcome.status == 400 && "think" in outcome.body) {
            outcome = post(chatBody(system, turns, format, think = null))
        }
        val body = when (outcome) {
            is HttpOutcome.Failed -> return ModelResult.Failed(outcome.failure)
            is HttpOutcome.Response -> when (outcome.status) {
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

    private suspend fun post(body: JsonObject): HttpOutcome =
        transport.post("$baseUrl/api/chat", body.toString(), requestTimeoutMillis)

    companion object {
        const val LOCAL_URL = "http://localhost:11434"
        /** The Android emulator's address for the host machine (where Ollama runs). */
        const val EMULATOR_HOST_URL = "http://10.0.2.2:11434"
        const val DEFAULT_MODEL = "qwen3:4b"
        const val MODEL_VARIABLE = "FINLIFE_OLLAMA_MODEL"
        const val MAX_ANSWER_TOKENS = 700
        const val CONTEXT_TOKENS = 8192

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
    }
}
