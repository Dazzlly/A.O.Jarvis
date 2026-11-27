package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.BatteryOptimizationStatus
import com.example.core.IntegrationResult
import com.example.data.AppThemeMode
import com.example.ui.theme.LocalAppThemeMode

@Composable
fun BatteryOptimizationCard(
    status: BatteryOptimizationStatus,
    isBackgroundRunning: Boolean,
    onRefreshStatus: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onOpenBatterySettings: () -> IntegrationResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeMode = LocalAppThemeMode.current
    val isAccentMode = themeMode == AppThemeMode.TRON || themeMode == AppThemeMode.CUSTOM
    val borderColor = when {
        status.isIgnoringOptimizations -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
        isAccentMode -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("battery_optimization_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status.isIgnoringOptimizations) {
                        Icons.Rounded.BatteryChargingFull
                    } else {
                        Icons.Rounded.BatteryAlert
                    },
                    contentDescription = null,
                    tint = if (status.isIgnoringOptimizations) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.battery_opt_card_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.battery_opt_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefreshStatus,
                    modifier = Modifier.testTag("refresh_battery_optimization_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_battery_opt)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (status.isIgnoringOptimizations) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (status.isIgnoringOptimizations) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.BatteryAlert
                        },
                        contentDescription = null,
                        tint = if (status.isIgnoringOptimizations) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = status.statusLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = status.detailMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (status.isPowerSaveMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatterySaver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Modo Economia de Bateria do sistema está ativo; respostas em 2º plano podem sofrer atraso.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (isBackgroundRunning && !status.isIgnoringOptimizations) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "O serviço em segundo plano está ativo agora, mas sem isenção o Android pode encerrá-lo ao bloquear a tela.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!status.isIgnoringOptimizations) {
                    Button(
                        onClick = onRequestIgnoreBatteryOptimizations,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("request_battery_optimization_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.battery_opt_allow_cta))
                    }
                }
                OutlinedButton(
                    onClick = {
                        val res = onOpenBatterySettings()
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_battery_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (status.isIgnoringOptimizations) {
                            stringResource(R.string.battery_opt_manage_cta)
                        } else {
                            stringResource(R.string.battery_opt_settings_short)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationPromptDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("battery_opt_prompt_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Rounded.BatteryChargingFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.battery_opt_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.battery_opt_dialog_body),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                modifier = Modifier.testTag("battery_opt_prompt_allow_button")
            ) {
                Text(stringResource(R.string.battery_opt_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("battery_opt_prompt_dismiss_button")
            ) {
                Text(stringResource(R.string.battery_opt_dialog_later))
            }
        }
    )
}

@Composable
fun BatteryOptimizationWarningBanner(
    onRequestIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("battery_opt_warning_banner")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.BatteryAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.battery_opt_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_dismiss_battery_banner),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.battery_opt_banner_body),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onRequestIgnore,
                    modifier = Modifier.testTag("banner_ignore_battery_opt_button")
                ) {
                    Text(stringResource(R.string.battery_opt_allow_cta))
                }
            }
        }
    }
}
