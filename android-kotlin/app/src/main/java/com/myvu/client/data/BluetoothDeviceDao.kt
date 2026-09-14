package com.myvu.client.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BluetoothDeviceDao {

    @Query("SELECT * FROM bluetooth_devices ORDER BY isPrimary DESC, isConnected DESC, lastConnectedTime DESC")
    fun getAllDevicesFlow(): Flow<List<BluetoothDeviceEntity>>

    @Query("SELECT * FROM bluetooth_devices ORDER BY isPrimary DESC, isConnected DESC, lastConnectedTime DESC")
    suspend fun getAllDevices(): List<BluetoothDeviceEntity>

    @Query("SELECT * FROM bluetooth_devices WHERE macAddress = :mac LIMIT 1")
    suspend fun getDevice(mac: String): BluetoothDeviceEntity?

    @Query("SELECT * FROM bluetooth_devices WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryDevice(): BluetoothDeviceEntity?

    @Query("SELECT * FROM bluetooth_devices WHERE isConnected = 1 LIMIT 1")
    suspend fun getActiveConnectedDevice(): BluetoothDeviceEntity?

    @Query("SELECT * FROM bluetooth_devices WHERE isConnected = 1")
    suspend fun getConnectedDevices(): List<BluetoothDeviceEntity>

    @Query("SELECT * FROM bluetooth_devices WHERE isConnected = 1 AND deviceType = :type LIMIT 1")
    suspend fun getConnectedDeviceByType(type: String): BluetoothDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: BluetoothDeviceEntity)

    @Update
    suspend fun update(device: BluetoothDeviceEntity)

    @Query("UPDATE bluetooth_devices SET isPrimary = CASE WHEN macAddress = :mac THEN 1 ELSE 0 END")
    suspend fun setPrimaryDevice(mac: String)

    @Query("UPDATE bluetooth_devices SET isConnected = :connected, lastConnectedTime = :time WHERE macAddress = :mac")
    suspend fun updateConnectionState(mac: String, connected: Boolean, time: Long = System.currentTimeMillis())

    @Query("UPDATE bluetooth_devices SET batteryLevel = :battery WHERE macAddress = :mac")
    suspend fun updateBatteryLevel(mac: String, battery: Int?)

    @Query("UPDATE bluetooth_devices SET isConnected = 0 WHERE macAddress != :activeMac")
    suspend fun markOthersDisconnected(activeMac: String)

    @Query("UPDATE bluetooth_devices SET isConnected = 0")
    suspend fun markAllDisconnected()

    @Query("DELETE FROM bluetooth_devices WHERE macAddress = :mac")
    suspend fun deleteDevice(mac: String)
}
