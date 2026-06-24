package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bypass_logs")
data class BypassLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val logText: String,
    val type: String // "INFO", "SUCCESS", "ERROR", "PING"
)
