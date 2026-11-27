package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Publish
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.EvolutionProposal
import com.example.core.SelfEvolutionState

@Composable
fun SelfEvolutionPanel(
    githubRepo: String,
    hasGitHubToken: Boolean,
    evolutionState: SelfEvolutionState,
    initialDraftRequest: String,
    onSaveGitHubToken: (String) -> Boolean,
    onClearGitHubToken: () -> Unit,
    onGenerateProposal: (String) -> Unit,
    onApproveProposal: (EvolutionProposal) -> Unit,
    onResetEvolution: () -> Unit,
    onOpenGitHubUrl: (String) -> Result<String>,
    onGoToEnginesRepoConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var tokenInput by remember { mutableStateOf("") }
    var evolutionRequest by rememberSaveable { mutableStateOf(initialDraftRequest) }

    LaunchedEffect(initialDraftRequest) {
        if (initialDraftRequest.isNotBlank()) {
            evolutionRequest = initialDraftRequest
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Explanation & Security Architecture Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-Evolução Segura (GitHub PR + F-Droid)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Por segurança do Android, apps não reescrevem binários diretamente no celular. Em vez disso, o A.O.Jarvis lê seu código no GitHub, usa Gemini para propor melhorias, mostra a revisão aqui e — somente após sua aprovação — abre um Pull Request real no GitHub para compilar e entregar via F-Droid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (githubRepo.isBlank()) {
                        OutlinedButton(
                            onClick = onGoToEnginesRepoConfig,
                            modifier = Modifier.testTag("evolve_configure_repo_button")
                        ) {
                            Text("Configurar repositório GitHub em Motores")
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = "Repositório alvo: $githubRepo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Encrypted GitHub Fine-Grained Token Vault
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasGitHubToken) {
                                "GitHub Token salvo (criptografado no Keystore)"
                            } else {
                                "Conectar GitHub Fine-Grained Token"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasGitHubToken) {
                            IconButton(
                                onClick = {
                                    onClearGitHubToken()
                                    Toast.makeText(context, "Token GitHub removido do cofre", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("clear_github_pat_button")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remover token GitHub")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Crie um Fine-Grained Token no GitHub restrito apenas a este repositório com permissão de Contents (Read & Write) e Pull Requests (Read & Write).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text(if (hasGitHubToken) "Substituir token" else "github_pat_…") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_pat_input")
                        )
                        Button(
                            onClick = {
                                val saved = onSaveGitHubToken(tokenInput)
                                tokenInput = ""
                                Toast.makeText(
                                    context,
                                    if (saved) "Token criptografado e salvo no Android Keystore" else "Falha ao criptografar token",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = tokenInput.isNotBlank(),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("save_github_pat_button")
                        ) {
                            Text("Salvar")
                        }
                    }
                }
            }
        }

        // Self-Development Command Composer
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Comando de Auto-Desenvolvimento",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = evolutionRequest,
                        onValueChange = { evolutionRequest = it },
                        label = { Text("O que o A.O.Jarvis deve desenvolver ou melhorar no código?") },
                        placeholder = { Text("Ex.: Adicionar um comando offline 'modo trabalho' que reduz brilho e ativa Não Perturbe") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("self_evolution_prompt_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Adicionar comando 'modo foco' com Não Perturbe e brilho 40%",
                            "Adicionar atalho de voz para resumo diário da agenda",
                            "Melhorar respostas curtas para o modo de direção"
                        ).forEach { suggestion ->
                            AssistChip(
                                onClick = { evolutionRequest = suggestion },
                                label = { Text(suggestion.take(36) + "…") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    val isWorking = evolutionState is SelfEvolutionState.Working
                    Button(
                        onClick = { onGenerateProposal(evolutionRequest) },
                        enabled = !isWorking && evolutionRequest.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_evolution_proposal_button")
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isWorking) "Analisando & Gerando…" else "Gerar Proposta de Código")
                    }
                }
            }
        }

        // State Feedback / Proposal Review / PR Result
        item {
            when (evolutionState) {
                SelfEvolutionState.Idle -> Unit

                is SelfEvolutionState.Working -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(evolutionState.step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                is SelfEvolutionState.ProposalReady -> {
                    val proposal = evolutionState.proposal
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("evolution_proposal_review_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Revisão Obrigatória · ${proposal.prTitle}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Branch: ${proposal.featureBranch} → ${proposal.baseBranch}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(proposal.summary, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(10.dp))

                            proposal.fileChanges.forEach { fileChange ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = fileChange.path,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = fileChange.changeSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = fileChange.updatedContent.take(360) +
                                                if (fileChange.updatedContent.length > 360) "\n…" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onResetEvolution,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("discard_evolution_proposal_button")
                                ) {
                                    Text("Descartar")
                                }
                                Button(
                                    onClick = { onApproveProposal(proposal) },
                                    modifier = Modifier
                                        .weight(1.6f)
                                        .height(48.dp)
                                        .testTag("approve_evolution_pr_button")
                                ) {
                                    Icon(Icons.Rounded.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Aprovar & Abrir PR")
                                }
                            }
                        }
                    }
                }

                is SelfEvolutionState.PullRequestCreated -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pull Request #${evolutionState.prNumber} criado!",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${evolutionState.title} (branch ${evolutionState.branchName}). Faça o merge no GitHub e crie a próxima tag v* para atualizar o app via F-Droid.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val res = onOpenGitHubUrl(evolutionState.prUrl)
                                    Toast.makeText(
                                        context,
                                        res.getOrElse { it.message ?: "Erro ao abrir PR" },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_created_pr_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Revisar Pull Request no GitHub")
                            }
                        }
                    }
                }

                is SelfEvolutionState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = evolutionState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
