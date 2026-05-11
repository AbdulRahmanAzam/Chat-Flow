package com.chatflow.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatflow.app.ble.BleMeshService
import com.chatflow.app.ui.nav.ChatFlowNav
import com.chatflow.app.ui.theme.ChatFlowTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val app = applicationContext as ChatFlowApp
            val themeMode by app.container.prefs.themeMode.collectAsState(initial = "system")
            ChatFlowTheme(themeMode = themeMode) {
                val permState = rememberMultiplePermissionsState(permissions)

                LaunchedEffect(permState.allPermissionsGranted) {
                    if (permState.allPermissionsGranted) {
                        val intent = Intent(this@MainActivity, BleMeshService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else startService(intent)
                    }
                }

                if (!permState.allPermissionsGranted) {
                    PermissionGate(onGrant = { permState.launchMultiplePermissionRequest() })
                } else {
                    ChatFlowNav(container = app.container)
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("ChatFlow", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Offline Bluetooth LE Mesh Chat", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(32.dp))
            Text(
                "ChatFlow needs Bluetooth and nearby-devices permissions to discover peers and " +
                    "route encrypted messages across the mesh.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGrant) { Text("Grant Permissions") }
        }
    }
}
