package com.chatflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatflow.app.ble.BleMeshService
import com.chatflow.app.data.AppContainer
import com.chatflow.app.data.db.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    conversationId: String,
    onBack: () -> Unit
) {
    val messages by container.repo.observeMessages(conversationId).collectAsState(initial = emptyList())
    val peers by container.repo.observePeers().collectAsState(initial = emptyList())
    val isChannel = conversationId.startsWith("#")
    val title = if (isChannel) conversationId
        else peers.firstOrNull { it.peerId == conversationId }?.nickname ?: "peer-${conversationId.take(6)}"

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isChannel) "Group channel · AES-GCM" else "Direct · E2E encrypted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Type a message…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions.Default
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isEmpty()) return@FilledIconButton
                            val mesh = BleMeshService.INSTANCE?.mesh()
                            if (mesh != null) {
                                if (isChannel) mesh.sendChannel(conversationId.removePrefix("#"), text)
                                else mesh.sendPrivate(conversationId, text)
                            }
                            input = ""
                        }
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
        }
    }
}

@Composable
private fun MessageBubble(msg: MessageEntity) {
    val alignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isOutgoing)
        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (msg.isOutgoing)
        MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (!msg.isOutgoing) {
            Text(
                msg.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (msg.isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (msg.isOutgoing) 4.dp else 16.dp
            ),
            tonalElevation = 1.dp
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(msg.content, color = textColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.75f)
                    )
                    if (msg.isOutgoing) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (msg.isDelivered) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = textColor.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
