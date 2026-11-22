package com.tuna.finlife

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tuna.finlife.assistant.OllamaAssistantModel

/** The desktop app: the shared app plus the explain-only assistant on the local Ollama model. */
@Composable
fun DesktopApp() {
    val model = remember { OllamaAssistantModel.fromEnvironment() }
    FinLifeApp(assistantModel = model)
}
