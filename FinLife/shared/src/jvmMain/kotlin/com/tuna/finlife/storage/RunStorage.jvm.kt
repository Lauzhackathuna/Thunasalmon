package com.tuna.finlife.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Composable
internal actual fun rememberRunStorage(): RunStorage = remember {
    object : RunStorage {
        private val directory = System.getProperty("finlife.saveDirectory")?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".finlife")
        private val file = directory.resolve("journey-v1.txt")
        override fun read(): String? {
            if (!Files.exists(file)) return null
            require(Files.size(file) <= 2_000_000) { "Save exceeds size limit" }
            return Files.readString(file)
        }
        override fun write(value: String?) {
            if (value == null) { Files.deleteIfExists(file); return }
            Files.createDirectories(directory)
            val temporary = Files.createTempFile(directory, "journey-", ".tmp")
            try {
                Files.writeString(temporary, value)
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        }
    }
}
