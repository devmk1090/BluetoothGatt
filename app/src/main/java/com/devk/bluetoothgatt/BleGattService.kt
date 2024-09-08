package com.devk.bluetoothgatt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import com.devk.bluetoothgatt.response.DeviceResponse
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit
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

    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private var isRunning = false
    private var isBleEnabled = false

    private var bleManager: BluetoothManager? = null
    private val bleAdapter: BluetoothAdapter?
        get() = bleManager?.adapter
    private var bleLeScanner: BluetoothLeScanner? = null

    private var devices: List<DeviceResponse>? = null
    private var devicesName: ArrayList<String>? = null

    private val connectDeviceList = mutableSetOf<String>()
    private var connectBleList = mutableSetOf<BluetoothGatt>()


    private var notification: NotificationCompat.Builder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BLE_GATT_START -> {
                isRunning = true
                devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra("name", DeviceResponse::class.java)
                } else {
                    intent.getParcelableArrayListExtra("name")
                }
                if (!devices.isNullOrEmpty()) {
                    devicesName = devices?.map { it.name } as ArrayList<String>
                    setNotification()
                    startBle()
                }
            }
            BLE_GATT_STOP -> {
                isRunning = false
                stopService()
            }
        }
        return START_STICKY
    }

    private fun startBle() {
        bleManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (bleAdapter == null || !bleAdapter?.isEnabled!!) {
            return
        }

        if (isRunning) {
            coroutineScope.launch {
                val delayTime = TimeUnit.MINUTES.toMillis(5)
                while (isActive) {
                    delay(delayTime)
                    stopScan()
                    delay(1000)
                    startScan()
                }
            }
        }
    }

    private fun startScan() {
        isBleEnabled = true
        bleLeScanner = bleAdapter?.bluetoothLeScanner

        val filters: MutableList<ScanFilter> = ArrayList()
        val scanFilter: ScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
            .build()
        filters.add(scanFilter)

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        bleLeScanner?.startScan(filters, scanSettings, bleScanCallback)
    }

    /**
     * Scan Callback
     */
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                if (it.rssi >= RSSI) {
                    addScanResults(it)
                } else { }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            for (result in results!!) {
                if (result.rssi >= RSSI) {
                    addScanResults(result)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> disconnectScanAndBle()
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> disconnectScanAndBle()
                else -> {}
            }
        }
    }

    private fun addScanResults(result: ScanResult) {
        if (devicesName?.contains(result.device.name) == true) {
            connectGatt(result.device)
        }
    }

    /**
     * gattClientCallback
     */
    private val bleGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
        }
    }


    private fun stopScan() {
        if (bleLeScanner != null) {
            bleLeScanner?.stopScan(bleScanCallback)
            bleLeScanner == null
        }
    }

    private fun disconnectScanAndBle() {
        stopScan()
        coroutineScope.launch {
            delay(1000)
            disconnectBleAll()
            startBle()
        }
    }

    //연결된 특정 디바이스 하나만 Disconnect
    private fun disconnectBleEach(gatt: BluetoothGatt) {
        gatt.disconnect()
        gatt.close()
        connectBleList.remove(gatt)
        connectDeviceList.remove(gatt.device.name)
    }

    //연결된 모든 디바이스 Disconnect
    private fun disconnectBleAll() {
        coroutineScope.launch {
            connectBleList.forEach {
                it.disconnect()
                it.close()
            }
            connectBleList.clear()
            delay(100)
        }
    }

    private fun stopService() {
        isRunning = false
        stopScan()
        disconnectBleAll()
        coroutineScope.cancel()
        stopForeground(true)
        stopSelf()
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