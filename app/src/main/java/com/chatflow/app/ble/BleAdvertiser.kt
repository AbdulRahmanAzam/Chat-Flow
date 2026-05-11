package com.chatflow.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission

/** Broadcasts ChatFlow's service UUID so other peers can discover us. */
class BleAdvertiser(private val adapter: BluetoothAdapter) {
    private val tag = "ChatFlow/Adv"
    private val advertiser: BluetoothLeAdvertiser? = adapter.bluetoothLeAdvertiser

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(tag, "Advertising started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.w(tag, "Advertise failed: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun start() {
        val adv = advertiser ?: run { Log.w(tag, "No BLE advertiser"); return }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()
        try {
            adv.startAdvertising(settings, data, callback)
        } catch (e: SecurityException) {
            Log.w(tag, "Missing BLUETOOTH_ADVERTISE permission", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try { advertiser?.stopAdvertising(callback) } catch (_: Exception) {}
    }
}
