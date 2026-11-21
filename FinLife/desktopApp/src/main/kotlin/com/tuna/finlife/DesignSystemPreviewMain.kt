package com.tuna.finlife

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tuna.finlife.ui.DesignSystemPreview

object DesignSystemPreviewMain {
    @JvmStatic
    fun main(args: Array<String>) = application {
        Window(onCloseRequest = ::exitApplication, title = "FinLife") {
            DesignSystemPreview()
        }
    }
}
