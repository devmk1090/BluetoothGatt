package com.devk.bluetoothgatt

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@AndroidEntryPoint
class BleGattService: Service() {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    companion object {
        const val SERVICE_UUID = "0000fff0"
        const val TX_UUID = "0000fff1"
        const val TX_UUID_NOTIFY = "00002902"
        const val RX_UUID = "0000fff2"

        val COMMAND: ByteArray = byteArrayOf(0X0A, 0x0B)

        const val BLE_GATT_START = "BLE_GATT_START"
        const val BLE_GATT_STOP = "BLE_GATT_STOP"

        const val RSSI = -40
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}