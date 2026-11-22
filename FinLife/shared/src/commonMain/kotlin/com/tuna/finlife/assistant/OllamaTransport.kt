package com.tuna.finlife.assistant

/** What came back from one HTTP request to Ollama. */
internal sealed interface HttpOutcome {
    class Response(val status: Int, val body: String) : HttpOutcome
    class Failed(val failure: ModelFailure) : HttpOutcome
}

/** Sends requests to Ollama; one implementation per platform (desktop JDK client, Android URL connection). */
internal interface OllamaTransport {
    suspend fun get(url: String, timeoutMillis: Long): HttpOutcome
    suspend fun post(url: String, json: String, timeoutMillis: Long): HttpOutcome
}
