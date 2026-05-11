package com.chatflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatflow.app.ble.BleMeshService
import com.chatflow.app.data.AppContainer
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ConversationRow(
    val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long,
    val isChannel: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenChat: (String) -> Unit,
    onOpenPeers: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewChannel: () -> Unit
) {
    val conversationIds by container.repo.observeConversationIds().collectAsState(initial = emptyList())
    val peers by container.repo.observePeers().collectAsState(initial = emptyList())
    val nickname by container.prefs.nickname.collectAsState(initial = "")
    val status by (BleMeshService.INSTANCE?.mesh()?.status
        ?: flowOf(com.chatflow.app.ble.MeshEngine.Status())).collectAsState(initial = com.chatflow.app.ble.MeshEngine.Status())

    // Build conversation list rows (fetch last message for each)
    var rows by remember { mutableStateOf(emptyList<ConversationRow>()) }
    LaunchedEffect(conversationIds, peers) {
        rows = conversationIds.map { cid ->
            val last = container.repo.lastMessage(cid)
            val isCh = cid.startsWith("#")
            val title = if (isCh) cid else peers.firstOrNull { it.peerId == cid }?.nickname ?: "peer-${cid.take(6)}"
            ConversationRow(cid, title, last?.content ?: "", last?.timestamp ?: 0L, isCh)
        }.sortedByDescending { it.timestamp }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ChatFlow", fontWeight = FontWeight.Bold)
                        Text(
                            "${status.connected} connected · $nickname",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPeers) {
                        Icon(Icons.Default.People, contentDescription = "Peers")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChannel,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New channel") }
            )
        }
    ) { pad ->
        if (rows.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Open the peers list to message a nearby device, or create a public channel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onOpenPeers) { Text("See peers") }
                    Button(onClick = onNewChannel) { Text("New channel") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(rows, key = { it.id }) { row ->
                    ConversationCard(row) { onOpenChat(row.id) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(row: ConversationRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (row.isChannel) Icons.Default.Tag else Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                row.lastMessage.ifBlank { "No messages yet" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (row.timestamp > 0) {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(row.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
