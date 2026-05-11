package com.chatflow.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatflow.app.data.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val nickname by container.prefs.nickname.collectAsState(initial = "")
    val theme by container.prefs.themeMode.collectAsState(initial = "system")
    val scope = rememberCoroutineScope()
    val selfId = container.crypto.peerId

    var editing by remember { mutableStateOf(nickname) }
    LaunchedEffect(nickname) { editing = nickname }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {

            Column {
                Text("Nickname", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editing,
                    onValueChange = { editing = it.take(24) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch { container.prefs.setNickname(editing.trim()) }
                }) { Text("Save") }
            }

            Column {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (v, label) ->
                        FilterChip(
                            selected = theme == v,
                            onClick = { scope.launch { container.prefs.setTheme(v) } },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Column {
                Text("Identity", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Device ID:", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selfId, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This ID is derived from your local ECDH keypair. Messages to you are " +
                    "encrypted with AES-GCM using keys derived via ECDH + HKDF-SHA256.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text("Danger zone", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { container.repo.clearAll() } },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Clear all messages") }
            }
        }
    }
}
