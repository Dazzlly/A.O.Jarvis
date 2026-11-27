package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppUpdateState
import com.example.data.JarvisSettings

@Composable
fun RemoteUpdateCard(
    settings: JarvisSettings,
    currentVersionLabel: String,
    updateState: AppUpdateState,
    onUpdateRemoteConfig: (githubRepo: String, fdroidRepoUrl: String, autoCheck: Boolean) -> Unit,
    onCheckForUpdates: (String?) -> Unit,
    onBuildDefaultFdroidUrl: (String) -> String,
    onOpenGitHubRelease: (String) -> Result<String>,
    onOpenInFdroid: (String) -> Result<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var githubRepoDraft by remember { mutableStateOf(settings.githubRepo) }
    var fdroidUrlDraft by remember { mutableStateOf(settings.fdroidRepoUrl) }

    LaunchedEffect(settings.githubRepo, settings.fdroidRepoUrl) {
        githubRepoDraft = settings.githubRepo
        fdroidUrlDraft = settings.fdroidRepoUrl
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("remote_update_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.updates_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.updates_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Installed version pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Versão instalada",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = currentVersionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = githubRepoDraft,
                onValueChange = { githubRepoDraft = it },
                label = { Text("Repositório GitHub (usuario/repositorio)") },
                placeholder = { Text("seu-usuario/OpenJarvis-Android") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_repo_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = fdroidUrlDraft,
                onValueChange = { fdroidUrlDraft = it },
                label = { Text("URL do Repositório F-Droid (GitHub Pages)") },
                placeholder = { Text("https://seu-usuario.github.io/OpenJarvis-Android/fdroid/repo") },
                singleLine = true,
                trailingIcon = {
                    if (fdroidUrlDraft.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("F-Droid Repo URL", fdroidUrlDraft.trim()))
                                Toast.makeText(context, "URL do F-Droid copiada", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_fdroid_url),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fdroid_url_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {
                        val generated = onBuildDefaultFdroidUrl(githubRepoDraft)
                        if (generated.isBlank()) {
                            Toast.makeText(
                                context,
                                "Informe primeiro o repositório GitHub (ex: usuario/OpenJarvis)",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            fdroidUrlDraft = generated
                            Toast.makeText(context, "URL GitHub Pages preenchida", Toast.LENGTH_SHORT).show()
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Gerar URL GitHub Pages") },
                    modifier = Modifier.testTag("generate_fdroid_url_chip")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Verificar atualizações ao abrir o app",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Consulta a última GitHub Release quando o repositório estiver configurado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = settings.autoCheckUpdates,
                    onCheckedChange = { enabled ->
                        onUpdateRemoteConfig(githubRepoDraft, fdroidUrlDraft, enabled)
                    },
                    modifier = Modifier.testTag("switch_auto_check_updates")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isChecking = updateState is AppUpdateState.Checking
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onUpdateRemoteConfig(githubRepoDraft, fdroidUrlDraft, settings.autoCheckUpdates)
                        Toast.makeText(context, "Configuração de atualizações salva", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_updates_config_button")
                ) {
                    Text("Salvar")
                }

                Button(
                    onClick = {
                        onUpdateRemoteConfig(githubRepoDraft, fdroidUrlDraft, settings.autoCheckUpdates)
                        onCheckForUpdates(githubRepoDraft)
                    },
                    enabled = !isChecking,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("check_updates_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verificar agora")
                }
            }

            // Status & Action Surface
            when (updateState) {
                AppUpdateState.Idle -> Unit

                AppUpdateState.Checking -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Consultando última Release no GitHub…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is AppUpdateState.UpToDate -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "OpenJarvis atualizado! Versão local v${updateState.currentVersion} (última tag: ${updateState.latestTag}).",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                is AppUpdateState.UpdateAvailable -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Nova versão disponível: ${updateState.release.tagName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            if (updateState.release.publishedAt.isNotBlank()) {
                                Text(
                                    text = "Publicada em ${updateState.release.publishedAt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = updateState.release.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val activeFdroidUrl = fdroidUrlDraft.trim().ifBlank {
                                    onBuildDefaultFdroidUrl(githubRepoDraft)
                                }
                                if (activeFdroidUrl.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            val result = onOpenInFdroid(activeFdroidUrl)
                                            Toast.makeText(
                                                context,
                                                result.getOrElse { it.message ?: "Erro ao abrir F-Droid" },
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("open_fdroid_update_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Storefront,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("F-Droid")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val result = onOpenGitHubRelease(updateState.release.htmlUrl)
                                        Toast.makeText(
                                            context,
                                            result.getOrElse { it.message ?: "Erro ao abrir GitHub" },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_github_release_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("GitHub")
                                }
                            }
                        }
                    }
                }

                is AppUpdateState.Error -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = updateState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Direct F-Droid link shortcut whenever URL is already configured
            if (fdroidUrlDraft.isNotBlank() && updateState !is AppUpdateState.UpdateAvailable) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val result = onOpenInFdroid(fdroidUrlDraft)
                        Toast.makeText(
                            context,
                            result.getOrElse { it.message ?: "Erro ao abrir repositório no F-Droid" },
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_repo_to_fdroid_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Adicionar / Abrir Repositório no F-Droid")
                }
            }
        }
    }
}
