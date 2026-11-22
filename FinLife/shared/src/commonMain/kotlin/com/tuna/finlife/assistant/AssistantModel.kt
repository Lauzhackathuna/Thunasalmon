package com.tuna.finlife.assistant

/**
 * What the player asked about. The language model only picks one of these and the months it concerns;
 * the app then gathers the matching engine figures. Wire names are the JSON values the model returns.
 */
internal enum class Topic(val wire: String) {
    /** One played month or a range of played months. */
    PlayedMonths("played_months"),
    /** The situation now (balance, net worth, unpaid bills, housing, studies). */
    Current("current"),
    /** The dotted line: the other side of the latest choice. */
    LatestChoice("latest_choice"),
    /** The decision waiting now and its options. */
    PendingDecision("pending_decision"),
    /** The engine's 12-month projection under its stated assumptions. */
    Projection("projection"),
    /** What a term or rule of the simulation means. */
    Concept("concept"),
    /** Anything the simulation does not cover. */
    OutOfScope("out_of_scope"),
    /** The model could not tell; it asks a clarification question. */
    Unclear("unclear");

    companion object {
        fun fromWire(value: String): Topic? = entries.firstOrNull { it.wire == value }
    }
}

/** Structured reading of a question. Month numbers are the ones the player sees (first played month = 1). */
internal data class Extraction(
    val topic: Topic,
    val month: Int? = null,
    val endMonth: Int? = null,
    val clarification: String? = null,
    /** For a projection: how many months ahead the question asks about ("next 3 months" = 3). */
    val monthsAhead: Int? = null,
)

internal enum class Speaker { User, Assistant }

internal data class ChatTurn(val speaker: Speaker, val text: String)

internal enum class ModelFailure {
    /** Nothing answers on the local Ollama address. */
    NotRunning,
    /** Ollama runs, but the model is not installed. */
    ModelMissing,
    /** No answer in time. */
    Timeout,
    /** Ollama answered with an error status. */
    ServerError,
    /** The answer could not be read (not the expected JSON, or empty). */
    Malformed,
}

internal sealed interface ModelResult<out T> {
    data class Ok<T>(val value: T) : ModelResult<T>
    data class Failed(val failure: ModelFailure) : ModelResult<Nothing>
}

/** A local language model. It reads and words; it never computes a financial figure. */
internal interface AssistantModel {
    val modelName: String

    /** Null when the model can be used now. */
    suspend fun check(): ModelFailure?

    /** Classifies the last user turn of [turns] (earlier turns give context) into an [Extraction]. */
    suspend fun extract(system: String, turns: List<ChatTurn>): ModelResult<Extraction>

    /** Free text answer to a single user message. */
    suspend fun explain(system: String, user: String): ModelResult<String>
}
