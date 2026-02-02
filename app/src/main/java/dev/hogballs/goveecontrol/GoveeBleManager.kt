package dev.hogballs.goveecontrol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission")
class GoveeBleManager(
    private val context: Context,
    private val deviceConfig: DeviceConfig,
    private val modelConfig: ModelConfig
) {
    companion object {
        private const val TAG = "GoveeBleManager"
        private const val KEEPALIVE_INTERVAL_MS = 2000L
    }

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val keepaliveHandler = Handler(Looper.getMainLooper())
    private val keepalivePacket = GoveeCommandBuilder.build("aa01")
    private val keepaliveRunnable = object : Runnable {
        override fun run() {
            if (connected) {
                sendCommand(keepalivePacket)
                keepaliveHandler.postDelayed(this, KEEPALIVE_INTERVAL_MS)
            }
        }
    }

    var connected = false
        private set

    var onConnectionChange: ((Boolean) -> Unit)? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange ${deviceConfig.name}: status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Connected to ${deviceConfig.name}, discovering services")
                g.discoverServices()
            } else {
                Log.d(TAG, "Disconnected/failed ${deviceConfig.name} (status=$status)")
                connected = false
                writeChar = null
                stopKeepalive()
                g.close()
                onConnectionChange?.invoke(false)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val serviceUuid = UUID.fromString(modelConfig.serviceUuid)
                val charUuid = UUID.fromString(modelConfig.writeCharUuid)
                val service = g.getService(serviceUuid)
                writeChar = service?.getCharacteristic(charUuid)
                connected = writeChar != null
                Log.d(TAG, "Services discovered for ${deviceConfig.name}, ready=$connected")
                if (connected) startKeepalive()
                onConnectionChange?.invoke(connected)
            } else {
                Log.w(TAG, "Service discovery failed for ${deviceConfig.name}: $status")
                connected = false
                onConnectionChange?.invoke(false)
            }
        }
    }

    fun connect() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return
        val device = adapter.getRemoteDevice(deviceConfig.mac)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.d(TAG, "Connecting to ${deviceConfig.name} (${deviceConfig.mac})")
    }

    fun disconnect() {
        stopKeepalive()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        connected = false
    }

    private fun startKeepalive() {
        keepaliveHandler.removeCallbacks(keepaliveRunnable)
        keepaliveHandler.postDelayed(keepaliveRunnable, KEEPALIVE_INTERVAL_MS)
    }

    private fun stopKeepalive() {
        keepaliveHandler.removeCallbacks(keepaliveRunnable)
    }

    fun sendCommand(data: ByteArray) {
        val char = writeChar ?: run {
            Log.w(TAG, "sendCommand failed for ${deviceConfig.name}: writeChar is null")
            return
        }
        val g = gatt ?: run {
            Log.w(TAG, "sendCommand failed for ${deviceConfig.name}: gatt is null")
            return
        }
        Log.d(TAG, "sendCommand ${deviceConfig.name}: ${data.joinToString("") { "%02x".format(it) }}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(
                char, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
        } else {
            @Suppress("DEPRECATION")
            char.value = data
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            g.writeCharacteristic(char)
        }
    }
}
