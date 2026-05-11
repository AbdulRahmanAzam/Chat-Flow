package com.chatflow.app.ble

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chatflow.app.ChatFlowApp
import com.chatflow.app.MainActivity
import com.chatflow.app.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BleMeshService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var engine: MeshEngine

    companion object {
        const val CHANNEL_ID = "chatflow_ble"
        const val NOTIF_ID = 101
        @Volatile var INSTANCE: BleMeshService? = null
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        val app = application as ChatFlowApp
        engine = MeshEngine(
            context = this,
            crypto = app.container.crypto,
            repo = app.container.repo,
            nicknameProvider = {
                runBlocking { app.container.prefs.nickname.first() }
                    .ifBlank { "peer-${app.container.crypto.peerId.take(4)}" }
            }
        )
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("ChatFlow mesh active"))
        engine.start()
        scope.launch {
            engine.status.collect { s ->
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification("Connected peers: ${s.connected}"))
            }
        }
    }

    fun mesh(): MeshEngine = engine

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        engine.stop()
        scope.cancel()
        INSTANCE = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID, "ChatFlow Mesh",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the BLE mesh alive" }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatFlow")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
