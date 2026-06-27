package com.instrument.data.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.characteristicOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

private const val SERVICE_UUID = "0000FFE0-0000-1000-8000-00805F9B34FB"
private const val CHAR_UUID    = "0000FFE1-0000-1000-8000-00805F9B34FB"

class AndroidBleDataSource(private val context: Context) {

    private var peripheral: Peripheral? = null

    private fun hasPermissions(): Boolean {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun scanDevices(): Flow<List<GasDevice>> = flow {
        if (!hasPermissions()) {
            emit(emptyList())
            return@flow
        }
        val seen = mutableMapOf<String, GasDevice>()
        Scanner { }.advertisements
            .take(50)
            .collect { adv ->
                val id = adv.identifier.toString()
                seen[id] = GasDevice(id = id, name = adv.name ?: "Unknown", rssi = adv.rssi)
                emit(seen.values.toList())
            }
    }.flowOn(Dispatchers.IO)

    fun connect(deviceId: String): Flow<BleConnectionState> = flow {
        if (!hasPermissions()) {
            emit(BleConnectionState.Error("パーミッションが必要です"))
            return@flow
        }
        emit(BleConnectionState.Scanning)
        try {
            val adv = Scanner { }.advertisements.first { it.identifier.toString() == deviceId }
            emit(BleConnectionState.Connecting)
            val p = Peripheral(adv)
            peripheral = p
            p.connect()
            emit(BleConnectionState.Connected)
        } catch (e: Exception) {
            emit(BleConnectionState.Error(e.message ?: "接続エラー"))
        }
    }.flowOn(Dispatchers.IO)

    fun observeSensorData(): Flow<SensorReading> {
        val p = peripheral ?: return emptyFlow()
        return p.observe(characteristicOf(SERVICE_UUID, CHAR_UUID)).map { it.toSensorReading() }
    }

    suspend fun disconnect() {
        peripheral?.disconnect()
        peripheral = null
    }
}
