package com.tuna.finlife.assistant

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Android: a plain URL connection on the IO dispatcher (never on the main thread). */
internal class AndroidOllamaTransport(private val connectTimeoutMillis: Int = 2_000) : OllamaTransport {
    override suspend fun get(url: String, timeoutMillis: Long): HttpOutcome = request(url, null, timeoutMillis)

    override suspend fun post(url: String, json: String, timeoutMillis: Long): HttpOutcome = request(url, json, timeoutMillis)

    private suspend fun request(url: String, json: String?, timeoutMillis: Long): HttpOutcome = try {
        runInterruptible(Dispatchers.IO) { exchange(url, json, timeoutMillis) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HttpOutcome.Failed(ModelFailure.ServerError)
    }

    private fun exchange(url: String, json: String?, timeoutMillis: Long): HttpOutcome {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = timeoutMillis.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
            connection.requestMethod = if (json == null) "GET" else "POST"
            if (json != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
            }
            // Nothing answering on the address: Ollama is not reachable.
            try {
                connection.connect()
            } catch (_: IOException) {
                return HttpOutcome.Failed(ModelFailure.NotRunning)
            }
            return try {
                if (json != null) connection.outputStream.use { it.write(json.encodeToByteArray()) }
                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                HttpOutcome.Response(status, stream?.use { it.readBytes().decodeToString() }.orEmpty())
            } catch (_: SocketTimeoutException) {
                HttpOutcome.Failed(ModelFailure.Timeout)
            } catch (_: ConnectException) {
                HttpOutcome.Failed(ModelFailure.NotRunning)
            } catch (_: IOException) {
                HttpOutcome.Failed(ModelFailure.NotRunning)
            }
        } finally {
            connection.disconnect()
        }
    }
}
