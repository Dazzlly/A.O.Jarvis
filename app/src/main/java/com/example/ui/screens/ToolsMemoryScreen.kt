package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.EvolutionProposal
import com.example.core.SelfEvolutionState
import com.example.data.MemoryEntity
import com.example.data.VoiceCommandEntity
import com.example.engine.OfflineCommandEngine

@Composable
fun ToolsMemoryScreen(
    commands: List<VoiceCommandEntity>,
    memories: List<MemoryEntity>,
    selectedTabIndex: Int,
    onSelectTabIndex: (Int) -> Unit,
    githubRepo: String,
    hasGitHubToken: Boolean,
    selfEvolutionState: SelfEvolutionState,
    selfEvolutionDraft: String,
    onSaveGitHubToken: (String) -> Boolean,
    onClearGitHubToken: () -> Unit,
    onGenerateSelfEvolution: (String) -> Unit,
    onApproveSelfEvolution: (EvolutionProposal) -> Unit,
    onResetSelfEvolution: () -> Unit,
    onOpenGitHubUrl: (String) -> Result<String>,
    onGoToEnginesRepoConfig: () -> Unit,
    onAddCommand: (String, String, String) -> Unit,
    onToggleCommand: (VoiceCommandEntity) -> Unit,
    onDeleteCommand: (Long) -> Unit,
    onRunCommandNow: (String) -> Unit,
    onAddMemory: (String, String, String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddCommandDialog by remember { mutableStateOf(false) }
    var showAddMemoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Comandos & Memória",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Ferramentas locais, webhooks LAN e contexto persistente do OpenJarvis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedTabIndex < 2) {
                FilledTonalButton(
                    onClick = {
                        if (selectedTabIndex == 0) showAddCommandDialog = true else showAddMemoryDialog = true
                    },
                    modifier = Modifier.testTag("tools_add_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (selectedTabIndex == 0) "Comando" else "Memória")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTabIndex == 0,
                onClick = { onSelectTabIndex(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                icon = { Icon(Icons.Rounded.OfflineBolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(stringResource(R.string.tools_tab_commands), maxLines = 1) }
            )
            SegmentedButton(
                selected = selectedTabIndex == 1,
                onClick = { onSelectTabIndex(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                icon = { Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("${stringResource(R.string.tools_tab_memories)} (${memories.size})", maxLines = 1) }
            )
            SegmentedButton(
                selected = selectedTabIndex == 2,
                onClick = { onSelectTabIndex(2) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                icon = { Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(stringResource(R.string.tools_tab_autodev), maxLines = 1) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedTabIndex == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Comandos Personalizados (Offline / Rede Local)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (commands.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Nenhum comando personalizado criado. Toque em '+ Comando' para definir gatilhos de voz offline ou webhooks HTTP na sua LAN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(commands, key = { it.id }) { cmd ->
                        CustomCommandCard(
                            command = cmd,
                            onToggle = { onToggleCommand(cmd) },
                            onDelete = { onDeleteCommand(cmd.id) },
                            onTest = { onRunCommandNow(cmd.triggerPhrase) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Integrações com Apps, Autonavegação & Ferramentas Offline",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                items(OfflineCommandEngine.builtInExamples) { (phrase, desc) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "\"$phrase\"",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRunCommandNow(phrase) }) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Executar comando $phrase",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        } else if (selectedTabIndex == 1) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (memories.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Sem memórias salvas. Diga \"Lembre que...\" por voz ou adicione manualmente para dar contexto persistente ao Gemini e ao Ollama.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(memories, key = { it.id }) { mem ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = mem.key,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = mem.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = mem.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Excluir memória"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            SelfEvolutionPanel(
                githubRepo = githubRepo,
                hasGitHubToken = hasGitHubToken,
                evolutionState = selfEvolutionState,
                initialDraftRequest = selfEvolutionDraft,
                onSaveGitHubToken = onSaveGitHubToken,
                onClearGitHubToken = onClearGitHubToken,
                onGenerateProposal = onGenerateSelfEvolution,
                onApproveProposal = onApproveSelfEvolution,
                onResetEvolution = onResetSelfEvolution,
                onOpenGitHubUrl = onOpenGitHubUrl,
                onGoToEnginesRepoConfig = onGoToEnginesRepoConfig
            )
        }
    }

    if (showAddCommandDialog) {
        AddVoiceCommandDialog(
            onDismiss = { showAddCommandDialog = false },
            onSave = { trigger, type, payload ->
                onAddCommand(trigger, type, payload)
                showAddCommandDialog = false
            }
        )
    }

    if (showAddMemoryDialog) {
        AddMemoryDialog(
            onDismiss = { showAddMemoryDialog = false },
            onSave = { key, content, category ->
                onAddMemory(key, content, category)
                showAddMemoryDialog = false
            }
        )
    }
}

@Composable
private fun CustomCommandCard(
    command: VoiceCommandEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    val typeLabel = when (command.actionType) {
        "LOCAL_WEBHOOK" -> "Webhook LAN"
        "AGENT_PROMPT" -> "Prompt Agente"
        else -> "Resposta Offline"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "\"${command.triggerPhrase}\"",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = command.actionPayload,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = command.enabled, onCheckedChange = { onToggle() })
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onTest) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Testar")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remover")
                }
            }
        }
    }
}

@Composable
private fun AddVoiceCommandDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var trigger by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf("OFFLINE_RESPONSE") }
    var payload by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_add_command)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Frase gatilho por voz") },
                    placeholder = { Text("ex: acender luz escritorio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "OFFLINE_RESPONSE" to "Offline",
                        "LOCAL_WEBHOOK" to "LAN HTTP",
                        "AGENT_PROMPT" to "Agente"
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = actionType == id,
                            onClick = { actionType = id },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = {
                        Text(
                            when (actionType) {
                                "LOCAL_WEBHOOK" -> "URL local (http://192.168.x.x/...)"
                                "AGENT_PROMPT" -> "Prompt completo para Gemini/Ollama"
                                else -> "Resposta falada offline"
                            }
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(trigger, actionType, payload) },
                enabled = trigger.isNotBlank() && payload.isNotBlank()
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Preferência") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_add_memory)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Título da memória") },
                    placeholder = { Text("ex: Servidor PC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoria") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Fato ou preferência") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(key, content, category) },
                enabled = key.isNotBlank() && content.isNotBlank()
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
