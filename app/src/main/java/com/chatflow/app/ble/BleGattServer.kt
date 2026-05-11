package com.chatflow.app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Build
import android.content.Context
import android.util.Log
import java.util.UUID

/**
 * GATT server — exposes one service with one writable/notifiable characteristic.
 * Remote peers WRITE packet bytes to this characteristic; we forward them to the
 * mesh engine via [onPacketIn]. We also relay outbound packets to every
 * subscribed central via notifications.
 */
class BleGattServer(
    private val context: Context,
    private val onPacketIn: (ByteArray, BluetoothDevice) -> Unit
) {
    private val tag = "ChatFlow/GattSrv"
    private val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var server: BluetoothGattServer? = null
    private lateinit var characteristic: BluetoothGattCharacteristic
    private val subscribers = mutableSetOf<BluetoothDevice>()
    private val rxBuffer = mutableMapOf<String, ByteArray>()

    private val callback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribers.remove(device)
                rxBuffer.remove(device.address)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            // Accept chunked writes — peer may split packets larger than MTU
            if (characteristic.uuid == BleConstants.CHARACTERISTIC_UUID) {
                val prior = rxBuffer[device.address] ?: ByteArray(0)
                val combined = prior + value
                // Try to decode; if it parses fully, dispatch, else buffer.
                val decoded = com.chatflow.app.protocol.Packet.decode(combined)
                if (decoded != null) {
                    rxBuffer.remove(device.address)
                    onPacketIn(combined, device)
                } else {
                    rxBuffer[device.address] = combined
                }
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (descriptor.uuid == BleConstants.CCCD_UUID) {
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    subscribers.add(device)
                } else {
                    subscribers.remove(device)
                }
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val gattServer = bm.openGattServer(context, callback) ?: run {
            Log.w(tag, "Could not open GATT server"); return
        }
        server = gattServer
        val service = BluetoothGattService(
            BleConstants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        characteristic = BluetoothGattCharacteristic(
            BleConstants.CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val cccd = BluetoothGattDescriptor(
            BleConstants.CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(cccd)
        service.addCharacteristic(characteristic)
        gattServer.addService(service)
        Log.i(tag, "GATT server ready")
    }

    @SuppressLint("MissingPermission")
    fun notifySubscribers(data: ByteArray) {
        val s = server ?: return
        subscribers.toList().forEach { dev ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    s.notifyCharacteristicChanged(dev, characteristic, false, data)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = data
                    @Suppress("DEPRECATION")
                    s.notifyCharacteristicChanged(dev, characteristic, false)
                }
            } catch (e: Exception) { Log.w(tag, "notify failed", e) }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
        subscribers.clear()
    }
}
