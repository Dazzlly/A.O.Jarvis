package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.cloud.CloudSyncSummary

@Composable
fun CloudAccountCard(
    cloudSyncSummary: CloudSyncSummary,
    isFirebaseAvailable: Boolean = true,
    onSyncNow: () -> Unit,
    onSignInGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onImportGoogleServicesJson: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_account_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!cloudSyncSummary.isAuthenticated) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = if (isFirebaseAvailable) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isFirebaseAvailable) {
                                "Modo Local (Sem Conta)"
                            } else {
                                "Firebase não configurado neste APK"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isFirebaseAvailable) {
                                "Backup e sincronização em nuvem desativados"
                            } else {
                                "Importe google-services.json para ativar Google Login, Firestore e Gemini Cloud"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isFirebaseAvailable) {
                            "Você pulou o login com Google. Seus comandos, memórias e histórico de voz ficam salvos apenas neste dispositivo. Conecte sua conta Google quando quiser liberar sincronização e backup no Firestore."
                        } else {
                            "Este APK foi compilado sem o arquivo app/google-services.json. Você pode ativar o Firebase agora mesmo importando o arquivo google-services.json neste aparelho ou configurando o secret GOOGLE_SERVICES_JSON no GitHub Actions."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isFirebaseAvailable) {
                    Button(
                        onClick = onImportGoogleServicesJson,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_google_services_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importar google-services.json")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSignInGoogle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("connect_google_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Entrar com Google")
                    }
                } else {
                    Button(
                        onClick = onSignInGoogle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("connect_google_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Entrar com Google")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cloudSyncSummary.displayName.ifBlank { "Conta Google Conectada" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = cloudSyncSummary.email.ifBlank { "Firebase Auth · Firestore Sync" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = "Firebase conectado",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Firestore: ${cloudSyncSummary.memoriesCount} memórias · ${cloudSyncSummary.commandsCount} comandos · ${cloudSyncSummary.sessionsCount} sessões",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = cloudSyncSummary.lastSyncMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!cloudSyncSummary.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cloudSyncSummary.errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !cloudSyncSummary.isSyncing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cloud_sync_now_button")
                    ) {
                        if (cloudSyncSummary.isSyncing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sincronizar")
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sair")
                    }
                }
            }
        }
    }
}
