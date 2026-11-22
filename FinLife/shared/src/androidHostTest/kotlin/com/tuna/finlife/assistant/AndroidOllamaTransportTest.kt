package com.tuna.finlife.assistant

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The Android transport against a tiny local HTTP server standing in for Ollama. */
class AndroidOllamaTransportTest {
    private class Reply(val status: Int, val body: String, val delayMillis: Long = 0)

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val received = mutableListOf<String>()
    @Volatile private var reply: (request: String) -> Reply = { Reply(500, "") }

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) {
                    socket.use { s ->
                        val input = s.getInputStream().bufferedReader()
                        val head = generateSequence { input.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                        val length = head.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                            ?.substringAfter(':')?.trim()?.toInt() ?: 0
                        val body = CharArray(length).also { var read = 0; while (read < length) read += input.read(it, read, length - read) }
                        val request = head.first() + "\n" + String(body)
                        synchronized(received) { received += request }
                        val answer = reply(request)
                        Thread.sleep(answer.delayMillis)
                        val bytes = answer.body.encodeToByteArray()
                        val out = s.getOutputStream()
                        out.write("HTTP/1.1 ${answer.status} X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
                        out.write(bytes)
                        out.flush()
                    }
                }
            }
        }
    }

    @AfterTest
    fun stop() = server.close()

    private val url get() = "http://127.0.0.1:${server.localPort}"

    private fun model(timeoutMillis: Long = 10_000) =
        OllamaAssistantModel("qwen3:4b", url, AndroidOllamaTransport(), timeoutMillis, timeoutMillis)

    @Test
    fun findsTheInstalledModel() = runBlocking {
        reply = { Reply(200, """{"models":[{"name":"qwen3:4b"}]}""") }
        assertNull(model().check())
        assertTrue(received.single().startsWith("GET /api/tags"))
    }

    @Test
    fun postsTheChatRequestAndReadsTheAnswer() = runBlocking {
        reply = { Reply(200, """{"message":{"role":"assistant","content":"{\"answer\":\"Maya received 1200 CHF.\"}"}}""") }
        assertEquals(ModelResult.Ok("Maya received 1200 CHF."), model().explain("system", "question"))
        val request = received.single()
        assertTrue(request.startsWith("POST /api/chat"))
        assertTrue("\"model\":\"qwen3:4b\"" in request && "\"stream\":false" in request)
    }

    @Test
    fun reportsEachFailure() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }
        assertEquals(ModelFailure.NotRunning,
            OllamaAssistantModel("qwen3:4b", "http://127.0.0.1:$closedPort", AndroidOllamaTransport()).check())
        reply = { Reply(404, """{"error":"model not found"}""") }
        assertEquals(ModelResult.Failed(ModelFailure.ModelMissing), model().explain("system", "question"))
        reply = { Reply(500, """{"error":"out of memory"}""") }
        assertEquals(ModelResult.Failed(ModelFailure.ServerError), model().explain("system", "question"))
        reply = { Reply(200, "{}", delayMillis = 1_500) }
        assertEquals(ModelResult.Failed(ModelFailure.Timeout), model(timeoutMillis = 300).explain("system", "question"))
    }
}
