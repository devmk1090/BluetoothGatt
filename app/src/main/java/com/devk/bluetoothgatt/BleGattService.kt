package com.devk.bluetoothgatt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.devk.bluetoothgatt.response.DeviceResponse
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

    private var devices: List<DeviceResponse>? = null
    private var devicesName: ArrayList<String>? = null

    private var notification: NotificationCompat.Builder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BLE_GATT_START -> {
                devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra("name", DeviceResponse::class.java)
                } else {
                    intent.getParcelableArrayListExtra("name")
                }
                if (!devices.isNullOrEmpty()) {
                    devicesName = devices?.map { it.name } as ArrayList<String>
                    setNotification()
                }
            }
        }
        return START_STICKY
    }

    private fun setNotification() {
        if (notification == null) {
            val channelId = setNotificationChannel()
            notification = NotificationCompat.Builder(context, channelId)
            notification?.let {
                it.priority = NotificationCompat.PRIORITY_DEFAULT
                it.setOngoing(true)
                it.setSmallIcon(R.mipmap.ic_launcher)
                it.setContentTitle("Title")
                it.setContentText("Content...")

                val intent = Intent(context, MainActivity::class.java)
                intent.action = Intent.ACTION_VIEW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val pendingIntent =
                        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    it.setContentIntent(pendingIntent)
                } else {
                    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                    it.setContentIntent(pendingIntent)
                }
                startForeground(1, it.build())
            }
        }
    }

    private fun setNotificationChannel(): String {
        val id = "BluetoothGatt"
        val name = "BluetoothGatt Background Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(id, name, importance)
            channel.setShowBadge(false)
            channel.enableVibration(false)
            val notificationService = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationService.createNotificationChannel(channel)
        }
        return id
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}