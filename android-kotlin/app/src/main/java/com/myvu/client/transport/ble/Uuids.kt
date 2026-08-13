package com.myvu.client.transport.ble

import java.util.Locale
import java.util.UUID

object Uuids {
    @JvmStatic
    fun make(i: Int): UUID {
        return UUID.fromString(String.format(Locale.US, "0000%04x-0000-1000-8000-00805f9b34fb", i))
    }

    @JvmField
    val SERVICE: UUID = make(3025)

    @JvmField
    val AIR_INTERNAL: UUID = make(0x2020)

    @JvmField
    val AIR_EXTERNAL: UUID = make(0x2021)

    @JvmField
    val AIR_URGENT: UUID = make(0x2022)

    @JvmField
    val GLASS_WRITE: UUID = make(0x2023)

    @JvmField
    val V2_INTERNAL: UUID = make(0x2010)

    @JvmField
    val V2_EXTERNAL: UUID = make(0x2011)

    @JvmField
    val V2_URGENT: UUID = make(0x2012)

    @JvmField
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    @JvmField
    val CHANNEL_SETS: Array<Array<UUID>> = arrayOf(
        arrayOf(AIR_INTERNAL, AIR_EXTERNAL, AIR_URGENT),
        arrayOf(V2_INTERNAL, V2_EXTERNAL, V2_URGENT)
    )
}
