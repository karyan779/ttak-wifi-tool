package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BypassEngine
import com.example.data.NetworkPingResult
import com.example.data.database.AppDatabase
import com.example.data.database.BypassLog
import com.example.data.database.BypassRepository
import com.example.data.database.BypassSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BypassViewModel(
    application: Application,
    private val repository: BypassRepository,
    private val engine: BypassEngine
) : AndroidViewModel(application) {

    // Exposure of database states
    val settingsFlow: StateFlow<BypassSettings?> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val logsFlow: StateFlow<List<BypassLog>> = repository.logsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Temporary form states for interactive binding
    val sessionUrl = MutableStateFlow("")
    val macAddress = MutableStateFlow("")
    val voucher = MutableStateFlow("")
    val gatewayIp = MutableStateFlow("192.168.60.1")
    val intervalSeconds = MutableStateFlow(60)

    private val _isBypassing = MutableStateFlow(false)
    val isBypassing: StateFlow<Boolean> = _isBypassing.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _pingResult = MutableStateFlow<NetworkPingResult?>(null)
    val pingResult: StateFlow<NetworkPingResult?> = _pingResult.asStateFlow()

    private val _isAutoLoopRunning = MutableStateFlow(false)
    val isAutoLoopRunning: StateFlow<Boolean> = _isAutoLoopRunning.asStateFlow()

    private val _adminNotification = MutableStateFlow<String?>(null)
    val adminNotification: StateFlow<String?> = _adminNotification.asStateFlow()

    private var loopJob: Job? = null

    init {
        // Hydrate inputs when database loaded
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                if (settings != null) {
                    sessionUrl.value = settings.sessionUrl
                    macAddress.value = settings.macAddress
                    voucher.value = settings.voucher
                    gatewayIp.value = settings.gatewayIp
                    intervalSeconds.value = settings.intervalSeconds
                    
                    // If DB says auto-loop should be enabled, trigger it on startup
                    if (settings.autoLoopEnabled && !_isAutoLoopRunning.value) {
                        startAutoLoop(settings.intervalSeconds)
                    }
                }
            }
        }

        // Execute initial ping check
        runPing()

        // Fetch custom admin notification from GitHub JSON
        fetchAdminNotification()
    }

    fun saveSettings() {
        viewModelScope.launch {
            val currentSettings = BypassSettings(
                sessionUrl = sessionUrl.value,
                macAddress = macAddress.value,
                voucher = voucher.value,
                gatewayIp = gatewayIp.value,
                autoLoopEnabled = _isAutoLoopRunning.value,
                intervalSeconds = intervalSeconds.value
            )
            repository.saveSettings(currentSettings)
            addLog("Settings updated & saved into database", "INFO")
        }
    }

    fun runBypass() {
        if (_isBypassing.value) return
        _isBypassing.value = true

        viewModelScope.launch {
            addLog("Starting manual bypass process...", "INFO")
            val success = executeBypassProcess()
            _isBypassing.value = false
            
            if (success) {
                runPing() // Immediately refresh ping
            }
        }
    }

    private suspend fun executeBypassProcess(): Boolean {
        val sUrl = sessionUrl.value.trim()
        val mAddress = macAddress.value.trim()
        val vCode = voucher.value.trim()
        val gIp = gatewayIp.value.trim()

        if (sUrl.isEmpty()) {
            addLog("Error: TOKEN KEY is empty!", "ERROR")
            return false
        }
        if (mAddress.isEmpty()) {
            addLog("Error: MAC Address is empty!", "ERROR")
            return false
        }
        if (vCode.isEmpty()) {
            addLog("Error: Voucher coupon code is empty!", "ERROR")
            return false
        }

        // Step 1: Decode or read URL & get sessionId
        addLog("Retrieving Captive Portal Session ID...", "INFO")
        val sessionId = engine.getSessionId(sUrl, mAddress)
        if (sessionId == null) {
            addLog("Failed to fetch Session ID. Check URL / decryption key.", "ERROR")
            return false
        }
        addLog("Found Captive Portal Session ID: $sessionId", "SUCCESS")

        // Step 2: Login via Voucher
        addLog("Authenticating voucher code '$vCode'...", "INFO")
        val (token, error) = engine.loginVoucher(sessionId, vCode)
        if (token == null) {
            val errMsg = error ?: "Voucher code may be expired or invalid."
            addLog("Authentication failed: $errMsg", "ERROR")
            return false
        }
        addLog("Authenticated successful! Token acquired.", "SUCCESS")

        // Step 3: Query Gateway Auth path
        addLog("Bypassing portal via gateway auth path ($gIp)...", "INFO")
        val (isBypassOk, redirectUrl) = engine.queryGateway(gIp, token)
        if (isBypassOk) {
            addLog("🌐 WiFi Bypassed successfully! Enjoy unrestricted internet.", "SUCCESS")
            return true
        } else {
            addLog("Gateway responded with redirect: $redirectUrl", "INFO")
            // Often, even if status returns unknown/redirect, it may have succeeded
            addLog("Checking real network connection status to confirm...", "INFO")
            val pResult = engine.getSmartPing()
            _pingResult.value = pResult
            if (pResult.isConnected) {
                addLog("🌐 Internet connection verified! Bypass is active.", "SUCCESS")
                return true
            } else {
                addLog("Bypass failed. Gateway did not authenticate properly.", "ERROR")
                return false
            }
        }
    }

    fun toggleAutoLoop(enabled: Boolean) {
        if (enabled == _isAutoLoopRunning.value) return

        if (enabled) {
            startAutoLoop(intervalSeconds.value)
        } else {
            stopAutoLoop()
        }

        // Save state trigger
        viewModelScope.launch {
            val currentSettings = BypassSettings(
                sessionUrl = sessionUrl.value,
                macAddress = macAddress.value,
                voucher = voucher.value,
                gatewayIp = gatewayIp.value,
                autoLoopEnabled = enabled,
                intervalSeconds = intervalSeconds.value
            )
            repository.saveSettings(currentSettings)
        }
    }

    private fun startAutoLoop(intervalSec: Int) {
        loopJob?.cancel()
        _isAutoLoopRunning.value = true
        addLog("Auto Bypass Daemon started. Run Interval: $intervalSec seconds.", "INFO")
        
        loopJob = viewModelScope.launch {
            var count = 1
            while (isActive) {
                addLog("──────────────────────────────", "INFO")
                addLog("🔄 Auto Relogin Daemon: Run #$count", "INFO")
                val success = executeBypassProcess()
                
                if (success) {
                    addLog("Relogin complete. Checking ping status...", "INFO")
                    val pResult = engine.getSmartPing()
                    _pingResult.value = pResult
                    val pSpeed = pResult.targets.firstOrNull { it.success }?.latencyMs ?: 0L
                    addLog("Network Ping: ${if (pResult.isConnected) "Online ($pSpeed ms)" else "Offline"}", "PING")
                } else {
                    addLog("Relogin run failed. Will attempt again in next schedule loop.", "ERROR")
                }
                
                count++
                delay(intervalSec * 1000L)
            }
        }
    }

    private fun stopAutoLoop() {
        loopJob?.cancel()
        loopJob = null
        _isAutoLoopRunning.value = false
        addLog("Auto Bypass Daemon stopped by user.", "INFO")
    }

    fun runPing() {
        if (_isPinging.value) return
        _isPinging.value = true
        
        viewModelScope.launch {
            val result = engine.getSmartPing()
            _pingResult.value = result
            _isPinging.value = false
            
            val statusStr = if (result.isConnected) {
                val fastTarget = result.targets.firstOrNull { it.success }
                "Online (via ${fastTarget?.host ?: "DNS"}: ${fastTarget?.latencyMs ?: 0}ms)"
            } else {
                "Offline"
            }
            addLog("Diagnostics complete. Internet network status: $statusStr", "PING")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            addLog("Status log history cleared.", "INFO")
        }
    }

    fun addLog(text: String, type: String = "INFO") {
        viewModelScope.launch {
            repository.addLog(text, type)
        }
    }

    fun fetchAdminNotification() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("https://raw.githubusercontent.com/karyan779/starlink-bypass-notification/master/notification.json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val regex = """"(?:message|notification)"\s*:\s*"([^"]+)"""".toRegex()
                            val match = regex.find(body)
                            val message = match?.groupValues?.get(1) ?: "STAR LINK BYPASS ဝန်ဆောင်မှု အသစ်များကို အသုံးပြုနိုင်ပါပြီ!"
                            _adminNotification.value = message
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loopJob?.cancel()
    }
}

class BypassViewModelFactory(
    private val application: Application,
    private val repository: BypassRepository,
    private val engine: BypassEngine
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BypassViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BypassViewModel(application, repository, engine) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
