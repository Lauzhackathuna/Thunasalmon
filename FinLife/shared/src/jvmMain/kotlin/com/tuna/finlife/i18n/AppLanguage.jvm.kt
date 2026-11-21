package com.tuna.finlife.i18n

import java.util.Locale

internal actual fun overrideAppLocale(language: AppLanguage): () -> Unit {
    val previous = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag(language.languageTag))
    return { Locale.setDefault(previous) }
}
