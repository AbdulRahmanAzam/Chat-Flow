package com.chatflow.app.ble

import java.util.UUID

object BleConstants {
    /** 128-bit service UUID used for discovery & all ChatFlow traffic. */
    val SERVICE_UUID: UUID = UUID.fromString("6f9e1a8b-24a8-4f6e-9c2a-8b4d3e1f7c21")
    /** Single characteristic used for bidirectional packet exchange. */
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("6f9e1a8b-24a8-4f6e-9c2a-8b4d3e1f7c22")
    /** Client Characteristic Configuration Descriptor (standard). */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val MAX_PAYLOAD = 500            // safe under 512-byte ATT MTU
    const val ADVERTISE_INTERVAL_MS = 1000L
    const val RESCAN_INTERVAL_MS = 15_000L
}
