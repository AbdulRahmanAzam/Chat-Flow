package com.chatflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatflow.app.data.AppContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val peers by container.repo.observePeers().collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby peers (${peers.size})", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        if (peers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(
                    "Scanning for peers…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(peers, key = { it.peerId }) { p ->
                    val active = now - p.lastSeen < 60_000
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onOpenChat(p.peerId) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.nickname, fontWeight = FontWeight.SemiBold)
                            Text(
                                "id ${p.peerId.take(8)}… · " +
                                    if (active) "online" else "last seen " +
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.lastSeen)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            Modifier.size(10.dp).clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
