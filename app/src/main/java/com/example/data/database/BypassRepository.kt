package com.example.data.database

import kotlinx.coroutines.flow.Flow

class BypassRepository(private val bypassDao: BypassDao) {
    val settingsFlow: Flow<BypassSettings?> = bypassDao.getSettingsFlow()
    val logsFlow: Flow<List<BypassLog>> = bypassDao.getLogsFlow()

    suspend fun getSettings(): BypassSettings {
        return bypassDao.getSettings() ?: BypassSettings()
    }

    suspend fun saveSettings(settings: BypassSettings) {
        bypassDao.insertSettings(settings)
    }

    suspend fun addLog(text: String, type: String = "INFO") {
        bypassDao.insertLog(BypassLog(logText = text, type = type))
    }

    suspend fun clearLogs() {
        bypassDao.clearLogs()
    }
}
