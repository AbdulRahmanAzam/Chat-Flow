package com.chatflow.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.ParcelUuid
import android.util.Log

/**
 * Scans for devices advertising the ChatFlow service UUID and reports them
 * to [onFound]. Duplicates are filtered out so the callback fires once per
 * newly-appearing MAC address.
 */
class BleScanner(
    private val adapter: BluetoothAdapter,
    private val onFound: (ScanResult) -> Unit
) {
    private val tag = "ChatFlow/Scan"
    private val scanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
    private val seen = mutableSetOf<String>()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = result.device.address ?: return
            if (seen.add(addr)) onFound(result)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.w(tag, "Scan failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val s = scanner ?: run { Log.w(tag, "No BLE scanner"); return }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        try { s.startScan(listOf(filter), settings, callback) }
        catch (e: SecurityException) { Log.w(tag, "No scan perm", e) }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try { scanner?.stopScan(callback) } catch (_: Exception) {}
    }

    fun resetCache() = seen.clear()
}
