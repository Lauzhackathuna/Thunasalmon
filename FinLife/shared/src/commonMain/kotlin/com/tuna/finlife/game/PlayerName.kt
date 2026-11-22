package com.tuna.finlife.game

/** First name displayed in the journey and supplied to the explanation-only assistant. */
internal object PlayerName {
    const val MAX_LENGTH = 32

    /** Keep the display name short and safe to interpolate into app and assistant text. */
    fun normalize(input: String): String? {
        val value = input.trim().replace(Regex("\\s+"), " ")
        return value.takeIf {
            it.length in 1..MAX_LENGTH &&
                it.any(Char::isLetter) &&
                it.all { char -> char.isLetter() || char == ' ' || char == '-' || char == '\'' || char == '’' }
        }
    }
}
