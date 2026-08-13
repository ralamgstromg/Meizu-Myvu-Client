package com.myvu.client.transport.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import java.util.UUID

abstract class GattOp {
    abstract fun execute(gatt: BluetoothGatt): Boolean
    abstract fun describe(): String

    override fun toString(): String = describe()

    companion object {
        @JvmStatic
        fun discoverServices(): GattOp = object : GattOp() {
            override fun execute(gatt: BluetoothGatt): Boolean = gatt.discoverServices()
            override fun describe(): String = "discoverServices"
        }

        @JvmStatic
        fun requestMtu(mtu: Int): GattOp = object : GattOp() {
            override fun execute(gatt: BluetoothGatt): Boolean = gatt.requestMtu(mtu)
            override fun describe(): String = "requestMtu($mtu)"
        }

        @JvmStatic
        fun enableNotifications(ch: BluetoothGattCharacteristic): GattOp = object : GattOp() {
            override fun execute(gatt: BluetoothGatt): Boolean {
                if (!gatt.setCharacteristicNotification(ch, true)) return false
                val cccd = ch.getDescriptor(Uuids.CCCD) ?: return false

                val canNotify = (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                val value = if (canNotify) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }

                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }

            override fun describe(): String = "enableNotifications(${shortUuid(ch.uuid)})"
        }

        @JvmStatic
        fun write(ch: BluetoothGattCharacteristic, value: ByteArray): GattOp = object : GattOp() {
            override fun execute(gatt: BluetoothGatt): Boolean {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    ch.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(ch)
                }
            }

            override fun describe(): String = "write(${shortUuid(ch.uuid)}, ${value.size}B)"
        }

        @JvmStatic
        fun shortUuid(uuid: UUID): String {
            val s = uuid.toString()
            return "0x" + s.substring(4, 8)
        }
    }
}
