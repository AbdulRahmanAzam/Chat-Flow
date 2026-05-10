package com.chatflow.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.Bundle
import android.os.ParcelUuid
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.Charset
import java.util.*

class BleChatActivity : AppCompatActivity() {

    private val chatServiceUuid = UUID.fromString("E8F1787E-1243-4001-9050-05D97F9271BA")
    private val companyId = 0x00FF

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var bleAdvertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Layout setup would go here
        
        startScanning()
    }

    private fun startScanning() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(chatServiceUuid))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: "Unknown PC"
            val manufacturerData = result.scanRecord?.getManufacturerSpecificData(companyId)
            
            if (manufacturerData != null) {
                val message = String(manufacturerData, Charset.forName("UTF-8"))
                // In a real app, update UI here
                runOnUiThread {
                    Toast.makeText(this@BleChatActivity, "From PC: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val advertiser = bleAdvertiser ?: return
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(5000) // Advertise for 5 seconds
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(chatServiceUuid))
            .addManufacturerData(companyId, text.toByteArray(Charset.forName("UTF-8")))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
        }
    }
}
