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

/** Desktop: the JDK HTTP client. */
internal class JdkOllamaTransport : OllamaTransport {
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

    override suspend fun get(url: String, timeoutMillis: Long): HttpOutcome =
        send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMillis(timeoutMillis)).GET().build())

    override suspend fun post(url: String, json: String, timeoutMillis: Long): HttpOutcome =
        send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMillis(timeoutMillis))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build())

    private suspend fun send(request: HttpRequest): HttpOutcome = try {
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        HttpOutcome.Response(response.statusCode(), response.body())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HttpOutcome.Failed(failureOf(e))
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

/** Desktop model on localhost; FINLIFE_OLLAMA_MODEL can pick another installed model. */
internal fun OllamaAssistantModel.Companion.fromEnvironment(): OllamaAssistantModel = OllamaAssistantModel(
    modelName = System.getenv(OllamaAssistantModel.MODEL_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }
        ?: OllamaAssistantModel.DEFAULT_MODEL,
    transport = JdkOllamaTransport(),
)
