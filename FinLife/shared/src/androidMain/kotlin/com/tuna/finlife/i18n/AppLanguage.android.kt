package com.tuna.finlife.i18n

import android.os.LocaleList
import java.util.Locale

internal actual fun overrideAppLocale(language: AppLanguage): () -> Unit {
    val previous = LocaleList.getDefault()
    LocaleList.setDefault(LocaleList(Locale.forLanguageTag(language.languageTag)))
    return { LocaleList.setDefault(previous) }
}
