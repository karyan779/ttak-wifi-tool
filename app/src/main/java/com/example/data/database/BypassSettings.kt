package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bypass_settings")
data class BypassSettings(
    @PrimaryKey val id: Int = 1,
    val sessionUrl: String = "",
    val macAddress: String = "",
    val voucher: String = "",
    val gatewayIp: String = "192.168.60.1",
    val autoLoopEnabled: Boolean = false,
    val intervalSeconds: Int = 60
)
