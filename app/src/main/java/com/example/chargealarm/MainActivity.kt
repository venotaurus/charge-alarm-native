package com.example.chargealarm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChargeAlarmScreen()
                }
            }
        }
    }
}

@Composable
fun ChargeAlarmScreen() {
    val context = LocalContext.current
    var currentBattery by remember { mutableIntStateOf(getBatteryPercentage(context)) }
    var isCharging by remember { mutableStateOf(isDeviceCharging(context)) }
    var targetPercent by remember { mutableFloatStateOf(80f) }
    var serviceRunning by remember { mutableStateOf(BatteryMonitorService.isMonitoring) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                currentBattery = getBatteryPercentage(context)
                isCharging = isDeviceCharging(context)
                serviceRunning = BatteryMonitorService.isMonitoring
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$currentBattery%",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isCharging) "Charging" else "Not Charging",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Alarm Target: ${targetPercent.roundToInt()}%",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        Slider(
            value = targetPercent,
            onValueChange = { targetPercent = it },
            valueRange = 20f..100f,
            steps = 79,
            enabled = !serviceRunning,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            listOf(80, 85, 90, 100).forEach { preset ->
                FilterChip(
                    selected = targetPercent.roundToInt() == preset,
                    onClick = { if (!serviceRunning) targetPercent = preset.toFloat() },
                    label = { Text("$preset%") },
                    enabled = !serviceRunning
                )
            }
        }

        Button(
            onClick = {
                if (!serviceRunning) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    val startIntent = Intent(context, BatteryMonitorService::class.java).apply {
                        putExtra(BatteryMonitorService.EXTRA_TARGET_PERCENT, targetPercent.roundToInt())
                    }
                    ContextCompat.startForegroundService(context, startIntent)
                    serviceRunning = true
                } else {
                    val stopIntent = Intent(context, BatteryMonitorService::class.java).apply {
                        action = BatteryMonitorService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                    serviceRunning = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (serviceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (serviceRunning) "Stop Alarm / Cancel" else "Set Charge Alarm",
                fontSize = 18.sp
            )
        }
    }
}

private fun getBatteryPercentage(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}

private fun isDeviceCharging(context: Context): Boolean {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}
