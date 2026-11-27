package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.BatteryStatusMonitor
import com.example.core.DeviceControlServiceState
import com.example.core.DeviceSettingsSnapshot
import com.example.core.IntegrationResult
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DeviceControlCard(
    snapshot: DeviceSettingsSnapshot,
    serviceState: DeviceControlServiceState,
    onToggleService: () -> Unit,
    onRefreshSnapshot: () -> Unit,
    onApplyPowerSaving: () -> IntegrationResult,
    onOpenWriteSettingsPermission: () -> IntegrationResult,
    onOpenDndPermission: () -> IntegrationResult,
    onSetBrightness: (Int) -> IntegrationResult,
    onSetVolume: (Int) -> IntegrationResult,
    onToggleWifi: (Boolean) -> IntegrationResult,
    onToggleBluetooth: (Boolean) -> IntegrationResult,
    onToggleTorch: (Boolean) -> IntegrationResult,
    onToggleAutoRotate: (Boolean) -> IntegrationResult,
    onToggleDnd: (Boolean) -> IntegrationResult,
    onOpenSystemPanel: (String) -> IntegrationResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var brightnessSlider by remember { mutableFloatStateOf(snapshot.brightnessPercent.toFloat()) }
    var volumeSlider by remember { mutableFloatStateOf(snapshot.mediaVolumePercent.toFloat()) }

    LaunchedEffect(snapshot.brightnessPercent, snapshot.mediaVolumePercent) {
        brightnessSlider = snapshot.brightnessPercent.toFloat()
        volumeSlider = snapshot.mediaVolumePercent.toFloat()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_control_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SettingsApplications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Controle do Dispositivo",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Permita que o A.O.Jarvis altere brilho, volume, Wi-Fi, Bluetooth, lanterna, rotação e Não Perturbe por voz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefreshSnapshot,
                    modifier = Modifier.testTag("refresh_device_settings_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar estado do dispositivo")
                }
            }

            // Dedicated Foreground Service Status & Toggle
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (serviceState.isRunning) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                border = BorderStroke(
                    1.dp,
                    if (serviceState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = if (serviceState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (serviceState.isRunning) {
                                    "Serviço de Controle Ativo (Foreground)"
                                } else {
                                    "Serviço de Controle em Primeiro Plano"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = serviceState.lastActionMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = serviceState.isRunning,
                            onCheckedChange = { onToggleService() },
                            modifier = Modifier.testTag("switch_device_control_service")
                        )
                    }
                    if (serviceState.missingPermissionsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pendentes: ${serviceState.missingPermissionsList.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Live Battery Status Monitor (Foreground Service -> AI Power-Saving Context)
            val battery = serviceState.batteryStatus
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    battery.needsPowerSaving -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    battery.isCharging -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                border = BorderStroke(
                    1.dp,
                    if (battery.needsPowerSaving) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("battery_monitor_panel")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                battery.isCharging -> Icons.Rounded.BatteryChargingFull
                                battery.needsPowerSaving -> Icons.Rounded.BatteryAlert
                                else -> Icons.Rounded.BatteryStd
                            },
                            contentDescription = null,
                            tint = when {
                                battery.needsPowerSaving -> MaterialTheme.colorScheme.error
                                battery.isCharging -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val levelTitle = if (battery.isKnown) {
                                val tempPart = if (battery.temperatureCelsius > 0f) {
                                    " · ${String.format(Locale.US, "%.1f", battery.temperatureCelsius)}°C"
                                } else ""
                                "Monitor de Bateria · ${battery.levelPercent}% (${battery.chargingSource}$tempPart)"
                            } else {
                                "Monitor de Bateria · Lendo telemetria…"
                            }
                            Text(
                                text = levelTitle,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = BatteryStatusMonitor.buildPowerSavingRecommendation(battery, snapshot),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (battery.isPowerSaveMode) {
                                "Economia do Android: ATIVA"
                            } else {
                                "Reportado ao motor de IA em tempo real"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val res = onApplyPowerSaving()
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("apply_power_saving_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Economizar")
                        }
                    }
                }
            }

            // Special Android Permissions Banners (WRITE_SETTINGS & DND)
            if (!snapshot.canWriteSystemSettings || !snapshot.hasDndAccess) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Autorizações especiais do Android para controle completo:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!snapshot.canWriteSystemSettings) {
                                FilledTonalButton(
                                    onClick = {
                                        val res = onOpenWriteSettingsPermission()
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("grant_write_settings_button")
                                ) {
                                    Text("Alterar Sistema")
                                }
                            }
                            if (!snapshot.hasDndAccess) {
                                FilledTonalButton(
                                    onClick = {
                                        val res = onOpenDndPermission()
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("grant_dnd_access_button")
                                ) {
                                    Text("Não Perturbe")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Brightness slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Brightness6,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Brilho da Tela", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = "${brightnessSlider.roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = brightnessSlider,
                onValueChange = { brightnessSlider = it },
                onValueChangeFinished = {
                    val res = onSetBrightness(brightnessSlider.roundToInt())
                    if (!res.success) {
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    }
                },
                valueRange = 5f..100f,
                modifier = Modifier.testTag("device_brightness_slider")
            )

            // Media Volume slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Volume de Mídia", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = "${volumeSlider.roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = volumeSlider,
                onValueChange = { volumeSlider = it },
                onValueChangeFinished = {
                    onSetVolume(volumeSlider.roundToInt())
                },
                valueRange = 0f..100f,
                modifier = Modifier.testTag("device_volume_slider")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick toggles: Wi-Fi, Bluetooth, Torch, Auto-Rotate, Do Not Disturb
            DeviceToggleRow(
                icon = Icons.Rounded.Wifi,
                title = "Wi-Fi",
                checked = snapshot.isWifiEnabled,
                onCheckedChange = { enabled ->
                    val res = onToggleWifi(enabled)
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                },
                testTag = "switch_device_wifi"
            )
            DeviceToggleRow(
                icon = Icons.Rounded.Bluetooth,
                title = "Bluetooth",
                checked = snapshot.isBluetoothEnabled,
                onCheckedChange = { enabled ->
                    val res = onToggleBluetooth(enabled)
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                },
                testTag = "switch_device_bluetooth"
            )
            DeviceToggleRow(
                icon = Icons.Rounded.FlashlightOn,
                title = "Lanterna (Flash)",
                checked = snapshot.isTorchOn,
                onCheckedChange = { enabled ->
                    val res = onToggleTorch(enabled)
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                },
                testTag = "switch_device_torch"
            )
            DeviceToggleRow(
                icon = Icons.Rounded.ScreenRotation,
                title = "Rotação Automática",
                checked = snapshot.isAutoRotate,
                onCheckedChange = { enabled ->
                    val res = onToggleAutoRotate(enabled)
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                },
                testTag = "switch_device_rotate"
            )
            DeviceToggleRow(
                icon = Icons.Rounded.DoNotDisturbOn,
                title = "Modo Não Perturbe (${snapshot.ringerModeLabel})",
                checked = snapshot.isDndEnabled,
                onCheckedChange = { enabled ->
                    val res = onToggleDnd(enabled)
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                },
                testTag = "switch_device_dnd"
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Painéis rápidos do Android",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Wi-Fi / Internet" to "wifi",
                    "Bluetooth" to "bluetooth",
                    "Volume" to "volume",
                    "Bateria" to "bateria",
                    "Tela" to "tela",
                    "Localização" to "localizacao"
                ).forEach { (label, target) ->
                    AssistChip(
                        onClick = {
                            val res = onOpenSystemPanel(target)
                            if (!res.success) {
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
