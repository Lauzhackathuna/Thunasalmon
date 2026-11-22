package com.tuna.finlife.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.util.AtomicFile
import java.io.File

@Composable
internal actual fun rememberRunStorage(): RunStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        object : RunStorage {
            private val file = AtomicFile(File(context.filesDir, "journey-v1.txt"))
            override fun read(): String? {
                if (!file.baseFile.exists()) return null
                require(file.baseFile.length() <= 2_000_000) { "Save exceeds size limit" }
                return file.openRead().use { it.readBytes().decodeToString() }
            }
            override fun write(value: String?) {
                if (value == null) { file.delete(); return }
                val stream = file.startWrite()
                try { stream.write(value.encodeToByteArray()); file.finishWrite(stream) }
                catch (failure: Exception) { file.failWrite(stream); throw failure }
            }
        }
    }
}
