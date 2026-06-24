package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BypassEngine
import com.example.data.database.AppDatabase
import com.example.data.database.BypassLog
import com.example.data.database.BypassRepository
import com.example.ui.BypassViewModel
import com.example.ui.BypassViewModelFactory
import com.example.ui.theme.Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(applicationContext)
        val repo = BypassRepository(db.bypassDao())
        val engine = BypassEngine()
        val factory = BypassViewModelFactory(application, repo, engine)

        setContent {
            Theme {
                val viewModel: BypassViewModel = viewModel(factory = factory)
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: BypassViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Database configurations flow
    val settings by viewModel.settingsFlow.collectAsState()
    val logs by viewModel.logsFlow.collectAsState()

    // Form field states
    val sessionUrlState by viewModel.sessionUrl.collectAsState()
    val macAddressState by viewModel.macAddress.collectAsState()
    val voucherState by viewModel.voucher.collectAsState()
    val gatewayIpState by viewModel.gatewayIp.collectAsState()
    val intervalState by viewModel.intervalSeconds.collectAsState()

    // Relogin, ping states
    val isBypassing by viewModel.isBypassing.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()
    val pingResult by viewModel.pingResult.collectAsState()
    val isAutoLoopRunning by viewModel.isAutoLoopRunning.collectAsState()
    val adminNotificationState by viewModel.adminNotification.collectAsState()

    var showHelpDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Header Hero Banner
            HeaderBanner(
                isConnected = pingResult?.isConnected ?: false,
                isPinging = isPinging,
                pingResultSummary = getPingSummary(pingResult),
                onPingClick = { viewModel.runPing() }
            )

            if (isBypassing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Body Layout splitting into config & log console
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Admin Notification banner
                adminNotificationState?.let { message ->
                    AdminNotificationCard(message = message)
                }

                // Relogin Control Hub
                ControlHubCard(
                    isAutoLoopRunning = isAutoLoopRunning,
                    isBypassing = isBypassing,
                    intervalSeconds = intervalState,
                    onManualBypass = {
                        if (sessionUrlState.isEmpty() || macAddressState.isEmpty() || voucherState.isEmpty()) {
                            Toast.makeText(context, "အချက်အလက်အားလုံး ပြည့်စုံအောင်ဖြည့်ပါ!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.runBypass()
                        }
                    },
                    onAutoLoopToggle = { enabled ->
                        if (enabled && (sessionUrlState.isEmpty() || macAddressState.isEmpty() || voucherState.isEmpty())) {
                            Toast.makeText(context, "အချက်အလက်မပြည့်စုံဘဲ Auto loop ဖွင့်၍မရပါ!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.toggleAutoLoop(enabled)
                        }
                    },
                    onIntervalChange = { seconds ->
                        viewModel.intervalSeconds.value = seconds
                        viewModel.saveSettings()
                    }
                )

                // Configuration Form Card
                ConfigFormCard(
                    sessionUrl = sessionUrlState,
                    macAddress = macAddressState,
                    voucher = voucherState,
                    gatewayIp = gatewayIpState,
                    onSessionUrlChange = { viewModel.sessionUrl.value = it },
                    onMacAddressChange = { viewModel.macAddress.value = it },
                    onVoucherChange = { viewModel.voucher.value = it },
                    onGatewayIpChange = { viewModel.gatewayIp.value = it },
                    onSaveClick = {
                        viewModel.saveSettings()
                        Toast.makeText(context, "အချက်အလက်များကို သိမ်းဆည်းလိုက်ပါပြီ ✓", Toast.LENGTH_SHORT).show()
                    },
                    onHelpClick = { showHelpDialog = true }
                )

                // STAR LINK BYPASS & Admin Support Section
                SupportCard()

                // Live Logger Console Terminal
                TerminalConsoleCard(
                    logs = logs,
                    onClearLogs = { viewModel.clearLogs() }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showHelpDialog) {
        HelpGuideDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
fun HeaderBanner(
    isConnected: Boolean,
    isPinging: Boolean,
    pingResultSummary: String,
    onPingClick: () -> Unit
) {
    val connectionColor by animateColorAsState(
        targetValue = if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(top = 40.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TTAK STAR LINK BYPASS",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "STAR LINK BYPASS UTILITY",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Live status tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(connectionColor.copy(alpha = 0.15f))
                        .border(1.dp, connectionColor.copy(alpha = 0.3f), RoundedCornerShape(30.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(connectionColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "ONLINE" else "OFFLINE",
                        color = connectionColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Diagnostic fast checker
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                IconButton(
                    onClick = onPingClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .testTag("ping_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Test network connection",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = if (isPinging) "Checking..." else pingResultSummary,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(110.dp)
                )
            }
        }
    }
}

@Composable
fun ControlHubCard(
    isAutoLoopRunning: Boolean,
    isBypassing: Boolean,
    intervalSeconds: Int,
    onManualBypass: () -> Unit,
    onAutoLoopToggle: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("control_hub_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "အချက်အလက်များအားလုံးဖြည့်ပြီး ချိတ်ဆက်ရန်နှိပ်ပါ",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Manual Bypass Action Button
                Button(
                    onClick = onManualBypass,
                    enabled = !isBypassing,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("bypass_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBypassing) "ချိတ်ဆက်နေသည်..." else "ချိတ်ဆက်မယ်",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            // Auto Daemon trigger Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "အလိုအလျောက် ပြန်ချိတ်စနစ် (Daemon)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "လိုင်းကျသွားလျှင် backup voucher ဖြင့် အဆက်မပြတ်ပြန်ချိတ်ပေးမည့် စနစ်",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = isAutoLoopRunning,
                    onCheckedChange = onAutoLoopToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = Color(0xFF10B981)
                    ),
                    modifier = Modifier.testTag("auto_loop_switch")
                )
            }

            // Interval Slider block
            if (isAutoLoopRunning) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ပြန်လည်စစ်ဆေးမည့် အချိန်သတ်မှတ်ချက်",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "$intervalSeconds စက္ကန့်",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = intervalSeconds.toFloat(),
                        onValueChange = { onIntervalChange(it.toInt()) },
                        valueRange = 15f..180f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigFormCard(
    sessionUrl: String,
    macAddress: String,
    voucher: String,
    gatewayIp: String,
    onSessionUrlChange: (String) -> Unit,
    onMacAddressChange: (String) -> Unit,
    onVoucherChange: (String) -> Unit,
    onGatewayIpChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("config_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WiFi ချိတ်ဆက်မှု အချက်အလက်များ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onHelpClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Usage guide",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }

            // Captive session URL input
            OutlinedTextField(
                value = sessionUrl,
                onValueChange = onSessionUrlChange,
                label = { Text("TOKEN KEY ထည့်ရန်") },
                placeholder = { Text("TOKEN KEY ကို ဤနေရာတွင် ထည့်ပါ...") },
                modifier = Modifier.fillMaxWidth().testTag("session_url_input"),
                shape = RoundedCornerShape(10.dp),
                maxLines = 1,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )

            // Spoof MAC address input
            OutlinedTextField(
                value = macAddress,
                onValueChange = onMacAddressChange,
                label = { Text("Phone Device Mac address") },
                placeholder = { Text("ဥပမာ: 10:3f:44:9d:b8:e4") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth().testTag("mac_input"),
                shape = RoundedCornerShape(10.dp),
                maxLines = 1,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )

            // Voucher key input
            OutlinedTextField(
                value = voucher,
                onValueChange = onVoucherChange,
                label = { Text("Voucher Code / accessCode") },
                placeholder = { Text("Voucher Code ဖြည့်သွင်းပါ") },
                modifier = Modifier.fillMaxWidth().testTag("voucher_input"),
                shape = RoundedCornerShape(10.dp),
                maxLines = 1,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )

            // Gateway IP address
            OutlinedTextField(
                value = gatewayIp,
                onValueChange = onGatewayIpChange,
                label = { Text("ချိတ်မဲ့ WIFI ရဲ့Gateway Web Server IP") },
                placeholder = { Text("မူရင်း: 192.168.60.1") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("gateway_ip_input"),
                shape = RoundedCornerShape(10.dp),
                maxLines = 1,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )

            // Save details to database button
            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "သိမ်းဆည်းမည် (Save Data)",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun TerminalConsoleCard(
    logs: List<BypassLog>,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()

    // Automatically scroll to the latest logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .testTag("terminal_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)) // Pure deep black terminal bg
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "စနစ်လည်ပတ်မှုမှတ်တမ်း (Terminal Log)",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear logs",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable Log entries
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF020617)) // Even deeper black to match cozy visual style
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                reverseLayout = true // Displays newest at bottom but loads nicely or vice versa
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "$ Logs history is currently empty.\nPress Manual Relogin or configure the parameters to begin.",
                            color = Color(0xFF475569),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    items(logs) { log ->
                        val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                        val logTextColor = when (log.type) {
                            "SUCCESS" -> Color(0xFF34D399) // Soft Green
                            "ERROR" -> Color(0xFFF87171)   // Soft Red
                            "PING" -> Color(0xFFFBBF24)    // Warm Gold
                            else -> Color(0xFF38BDF8)      // Cyan Info
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "[$formattedTime] ",
                                color = Color(0xFF475569),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = log.logText,
                                color = logTextColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HelpGuideDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "အသုံးပြုနည်း လမ်းညွှန်",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "၁။ Token Link ဖြည့်သွင်းပါ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "WiFi Captive Portal ဆီသို့ စတင်ဦးတည်သွားသည့်အခါ Copy ယူလာခဲ့သော URL စာသား (သို့မဟုတ်) Token စာသားကို ဤနေရာတွင် ထည့်ပါ။",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Text(
                    text = "၂။ Phone Device Mac address ဖြည့်သွင်းပါ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "မိမိဖုန်း၏ လက်ရှိ WiFi Mac Address (သို့မဟုတ်) Spoof လုပ်ထားသော client MAC ကို format မှန်ကန်စွာ (ဥပမာ: XX:XX:XX:XX:XX:XX) ဖြည့်တင်ပေးပါ။",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Text(
                    text = "၃။ Voucher / Access Code ဖြည့်သွင်းပါ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "အင်တာနက်ချိတ်ဆက်ရန် အသုံးပြုမည့် voucher coupon ကုဒ်ကို ထည့်ပါ။",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Text(
                    text = "၄။ Auto Daemon ကို ဖွင့်သတ်မှတ်ထားပါ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "စနစ်မှ နောက်ကွယ်တွင် စက္ကန့်အလိုက် အင်တာနက်ချိတ်ဆက်မှုကို စောင့်ကြည့်ပေးပြီး၊ လိုင်းပြတ်တောက်သွားပါက အလိုအလျောက် Relogin ချက်ချင်း ပြန်လုပ်ပေးသွားမည် ဖြစ်ပါသည်။",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("နားလည်ပါပြီ")
            }
        }
    )
}

private fun getPingSummary(result: com.example.data.NetworkPingResult?): String {
    if (result == null) return "Click refresh to diagnose network"
    if (!result.isConnected) return "No response from testing servers"
    val fastTarget = result.targets.firstOrNull { it.success }
    return "Fastest response:\n${fastTarget?.latencyMs}ms (${fastTarget?.host})"
}

@Composable
fun SupportCard() {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "STAR LINK BYPASS ဝယ်ယူရန်",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable {
                        try {
                            uriHandler.openUri("https://t.me/TTAK19")
                        } catch (e: Exception) {
                            // Fallback
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Admin Account",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "ADMIN ACCOUNT",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "@TTAK19",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "ဆက်သွယ်ရန်",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0088CC).copy(alpha = 0.08f))
                    .clickable {
                        try {
                            uriHandler.openUri("https://t.me/TTAKVPN")
                        } catch (e: Exception) {
                            // Fallback
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Telegram Channel",
                        tint = Color(0xFF0088CC),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Telegram Channel ဆိုင်ရာ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "t.me/TTAKVPN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0088CC)
                        )
                    }
                }
                Text(
                    text = "Channel သို့သွားရန်",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0088CC),
                    modifier = Modifier
                        .background(Color(0xFF0088CC).copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AdminNotificationCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_notification_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📢",
                    fontSize = 20.sp
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "📢 ADMIN NOTIFICATION",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

