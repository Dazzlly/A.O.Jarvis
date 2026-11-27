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
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.assistant.DefaultAssistantStatus
import com.example.core.IntegrationResult

@Composable
fun DefaultAssistantCard(
    status: DefaultAssistantStatus,
    onRefreshStatus: () -> Unit,
    onRequestSetDefaultAssistant: () -> Unit,
    onOpenAssistantSettings: () -> IntegrationResult,
    onTestAssistantTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (status.isDefaultAssistant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("default_assistant_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Assistant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Assistente Padrão do Dispositivo",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Substitua o assistente digital do Android pelo A.O.Jarvis (gesto dos cantos, botão Home ou Energia)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefreshStatus,
                    modifier = Modifier.testTag("refresh_default_assistant_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Verificar status de Assistente Padrão")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (status.isDefaultAssistant) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (status.isDefaultAssistant) Icons.Rounded.CheckCircle else Icons.Rounded.Assistant,
                        contentDescription = null,
                        tint = if (status.isDefaultAssistant) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (status.isDefaultAssistant) {
                                "Ativo como Assistente Padrão"
                            } else {
                                "Assistente atual: ${status.currentSystemAssistant}"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = status.statusDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRequestSetDefaultAssistant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("set_default_assistant_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Assistant,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (status.isDefaultAssistant) {
                        "Alterar Assistente Padrão"
                    } else {
                        "Definir A.O.Jarvis como Assistente Padrão"
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val res = onOpenAssistantSettings()
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_assistant_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Config. do Sistema")
                }

                FilledTonalButton(
                    onClick = onTestAssistantTrigger,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("test_assistant_trigger_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Testar Assistente")
                }
            }
        }
    }
}
