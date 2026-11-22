package com.tuna.finlife.assistant

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Talks to a fake Ollama on a free local port: checks the requests sent and how each failure is reported. */
class OllamaAssistantModelTest {
    private class Reply(val status: Int, val body: String, val delayMillis: Long = 0)

    private val requests = mutableListOf<Pair<String, String>>()
    private var replies: (path: String, body: String) -> Reply = { _, _ -> Reply(500, "") }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            val path = exchange.requestURI.path
            synchronized(requests) { requests += path to body }
            val reply = replies(path, body)
            Thread.sleep(reply.delayMillis)
            val bytes = reply.body.encodeToByteArray()
            exchange.sendResponseHeaders(reply.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        start()
    }

    private fun model(name: String = "qwen3:4b", timeout: Duration = Duration.ofSeconds(10)) =
        OllamaAssistantModel(name, "http://127.0.0.1:${server.address.port}", checkTimeout = timeout, requestTimeout = timeout)

    @AfterTest
    fun stop() = server.stop(0)

    private fun chatReply(content: String) = Reply(200, buildJsonObject {
        put("model", "qwen3:4b")
        putJsonObject("message") { put("role", "assistant"); put("content", content) }
        put("done", true)
    }.toString())

    private val tags = """{"models":[{"name":"qwen3:4b","model":"qwen3:4b"},{"name":"llama3.2:latest"}]}"""

    @Test
    fun checkFindsTheInstalledModel() = runBlocking {
        replies = { path, _ -> if (path == "/api/tags") Reply(200, tags) else Reply(404, "") }
        assertNull(model().check())
        assertNull(model("llama3.2").check())
        assertEquals(ModelFailure.ModelMissing, model("mistral").check())
        assertEquals("/api/tags", requests.first().first)
    }

    @Test
    fun nothingListeningIsNotRunning() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val absent = OllamaAssistantModel("qwen3:4b", "http://127.0.0.1:$port")
        assertEquals(ModelFailure.NotRunning, absent.check())
        assertEquals(ModelResult.Failed(ModelFailure.NotRunning), absent.explain("system", "question"))
    }

    @Test
    fun extractionSendsTheSchemaAndReadsTheAnswer() = runBlocking {
        replies = { _, _ -> chatReply("""{"topic":"played_months","month":3,"end_month":null,"clarification":null}""") }
        val turns = listOf(ChatTurn(Speaker.User, "earlier"), ChatTurn(Speaker.Assistant, "reply"), ChatTurn(Speaker.User, "month 3?"))
        assertEquals(ModelResult.Ok(Extraction(Topic.PlayedMonths, 3)), model().extract("route", turns))

        val (path, body) = requests.single()
        assertEquals("/api/chat", path)
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("qwen3:4b", json["model"]!!.jsonPrimitive.content)
        assertFalse(json["stream"]!!.jsonPrimitive.boolean)
        assertFalse(json["think"]!!.jsonPrimitive.boolean)
        val topics = json["format"]!!.jsonObject["properties"]!!.jsonObject["topic"]!!.jsonObject["enum"]!!.jsonArray
            .map { it.jsonPrimitive.content }
        assertEquals(Topic.entries.map { it.wire }, topics)
        val messages = json["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content to it.jsonObject["content"]!!.jsonPrimitive.content }
        assertEquals(listOf("system" to "route", "user" to "earlier", "assistant" to "reply", "user" to "month 3?"), messages)
    }

    @Test
    fun extractionToleratesThinkingAndNumberShapes() {
        assertEquals(Extraction(Topic.PlayedMonths, 2, 4),
            OllamaAssistantModel.parseExtraction("""{"topic":"played_months","month":2.0,"end_month":"4","clarification":null}"""))
        assertEquals(Extraction(Topic.Unclear, clarification = "Which month?"),
            OllamaAssistantModel.parseExtraction("""{"topic":"unclear","month":null,"end_month":null,"clarification":"Which month?"}"""))
        assertEquals(Extraction(Topic.Current, month = null),
            OllamaAssistantModel.parseExtraction("""{"topic":"current","month":2.5,"end_month":null,"clarification":null}"""))
        assertEquals("{}", OllamaAssistantModel.stripThinking("<think>\nhmm 1200\n</think>\n{}"))
        // Thinking-only models: the template opened the block, the reply only closes it.
        assertEquals("{}", OllamaAssistantModel.stripThinking("Okay, let's see: 1200.\n</think>\n\n{}"))
    }

    @Test
    fun unreadableAnswersAreMalformed() = runBlocking {
        for (content in listOf("not json", """{"topic":"weather"}""", "[1,2]", "")) {
            replies = { _, _ -> chatReply(content) }
            assertEquals(ModelResult.Failed(ModelFailure.Malformed), model().extract("route", emptyList()), content)
        }
        for (content in listOf("   ", """{"answer":"  "}""", "Maya received 1200 CHF.", """{"answer":"cut""")) {
            replies = { _, _ -> chatReply(content) }
            assertEquals(ModelResult.Failed(ModelFailure.Malformed), model().explain("system", "question"), content)
        }
        replies = { _, _ -> Reply(200, "<html>") }
        assertEquals(ModelResult.Failed(ModelFailure.Malformed), model().explain("system", "question"))
    }

    @Test
    fun explanationAsksForAJsonAnswerAndReturnsItsText() = runBlocking {
        replies = { _, _ -> chatReply("""<think>x</think>{"answer":" Maya received 1200 CHF. "}""") }
        assertEquals(ModelResult.Ok("Maya received 1200 CHF."), model().explain("system", "question"))
        val json = Json.parseToJsonElement(requests.single().second) as JsonObject
        assertEquals(listOf("answer"), json["format"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val options = json["options"]!!.jsonObject
        assertEquals(0, options["temperature"]!!.jsonPrimitive.content.toInt())
        assertEquals(OllamaAssistantModel.MAX_ANSWER_TOKENS, options["num_predict"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun errorStatusesAndSlowAnswersAreReported() = runBlocking {
        replies = { _, _ -> Reply(404, """{"error":"model 'qwen3:4b' not found"}""") }
        assertEquals(ModelResult.Failed(ModelFailure.ModelMissing), model().explain("system", "question"))
        replies = { _, _ -> Reply(500, """{"error":"out of memory"}""") }
        assertEquals(ModelResult.Failed(ModelFailure.ServerError), model().explain("system", "question"))
        replies = { _, _ -> Reply(500, "") }
        assertEquals(ModelFailure.ServerError, model().check())
        replies = { _, _ -> chatReply("late").let { Reply(it.status, it.body, delayMillis = 1500) } }
        assertEquals(ModelResult.Failed(ModelFailure.Timeout), model(timeout = Duration.ofMillis(300)).explain("system", "question"))
    }

    @Test
    fun aModelWithoutThinkingIsAskedAgainWithoutTheField() = runBlocking {
        replies = { _, body ->
            if ("\"think\"" in body) Reply(400, """{"error":"\"llama3.2\" does not support thinking"}""") else chatReply("""{"answer":"Done."}""")
        }
        assertEquals(ModelResult.Ok("Done."), model("llama3.2").explain("system", "question"))
        assertEquals(2, requests.size)
        assertTrue("\"think\"" !in requests[1].second)
    }
}
