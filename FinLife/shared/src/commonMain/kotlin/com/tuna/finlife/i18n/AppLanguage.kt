package com.tuna.finlife.i18n

enum class AppLanguage(val languageTag: String) {
    English("en"),
    French("fr"),
}

// Compose Resources reads the process locale on both targets. Restore it when the demo closes.
internal expect fun overrideAppLocale(language: AppLanguage): () -> Unit
