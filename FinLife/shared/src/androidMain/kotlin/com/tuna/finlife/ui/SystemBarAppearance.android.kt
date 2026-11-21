package com.tuna.finlife.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Suppress("DEPRECATION")
@Composable
internal actual fun SystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    if (view.isInEditMode) return

    LaunchedEffect(activity, view, dark) {
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
        } else {
            val navigationFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
            val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or navigationFlag
            val flags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (dark) flags and mask.inv() else flags or mask
        }
    }
}
