package com.tuna.finlife.storage

import androidx.compose.runtime.Composable

internal interface RunStorage {
    fun read(): String?
    /** Atomically replaces the save, or removes it when null. */
    fun write(value: String?)
}

@Composable
internal expect fun rememberRunStorage(): RunStorage
