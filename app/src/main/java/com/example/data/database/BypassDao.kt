package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BypassDao {
    @Query("SELECT * FROM bypass_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<BypassSettings?>

    @Query("SELECT * FROM bypass_settings WHERE id = 1")
    suspend fun getSettings(): BypassSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: BypassSettings)

    @Query("SELECT * FROM bypass_logs ORDER BY timestamp DESC LIMIT 200")
    fun getLogsFlow(): Flow<List<BypassLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BypassLog)

    @Query("DELETE FROM bypass_logs")
    suspend fun clearLogs()
}
