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
fun NewChannelScreen(
    container: AppContainer,
    onDone: (String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New channel", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Create or join a public group channel. Anyone on the mesh who knows " +
                    "the channel name can read and send messages to it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { s -> name = s.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(32) },
                singleLine = true,
                label = { Text("Channel name") },
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("#") }
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        container.repo.joinChannel(name.trim())
                        onDone(name.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Join channel") }
        }
    }
}
