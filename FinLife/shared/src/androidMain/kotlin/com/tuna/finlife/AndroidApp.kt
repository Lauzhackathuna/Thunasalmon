package com.tuna.finlife

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tuna.finlife.assistant.AndroidOllamaTransport
import com.tuna.finlife.assistant.OllamaAssistantModel

/**
 * The Android app: the shared app plus the explain-only assistant. Ollama runs on the developer's computer;
 * the Android emulator reaches it at 10.0.2.2 (emulator only, as decided for the demo).
 */
@Composable
fun AndroidApp() {
    val model = remember {
        OllamaAssistantModel(baseUrl = OllamaAssistantModel.EMULATOR_HOST_URL, transport = AndroidOllamaTransport())
    }
    FinLifeApp(assistantModel = model)
}
