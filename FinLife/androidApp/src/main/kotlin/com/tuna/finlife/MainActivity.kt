package com.tuna.finlife

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    // The assistant reaches Ollama on the developer's computer (10.0.2.2), a local-network address.
    // Refusing only disables the assistant; the rest of the app works.
    private val askLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK &&
            checkSelfPermission(LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            askLocalNetwork.launch(LOCAL_NETWORK_PERMISSION)
        }

        setContent {
            AndroidApp()
        }
    }
}

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
/** Android 17 (API 37) requires the local-network permission for apps that target it. */
private const val LOCAL_NETWORK_PERMISSION_SDK = 37

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}