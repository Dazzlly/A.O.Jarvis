package com.example.ui.components

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
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.voice.WakeWordDetectorState

@Composable
fun WakeWordHandsFreeStrip(
    wakeWordState: WakeWordDetectorState,
    enabled: Boolean,
    wakePhrase: String,
    hasMicPermission: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = enabled && hasMicPermission && (wakeWordState.isMonitoring || wakeWordState.isVerifyingPhrase)
    val accentColor = when {
        wakeWordState.isVerifyingPhrase -> MaterialTheme.colorScheme.tertiary
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (active) {
                accentColor.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .testTag("wake_word_handsfree_strip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (enabled && hasMicPermission) Icons.Rounded.Hearing else Icons.Rounded.MicOff,
                contentDescription = stringResource(R.string.cd_toggle_wake_word),
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                val phraseDisplay = wakePhrase.trim().ifBlank { "hey jarvis" }
                val headline = when {
                    !hasMicPermission -> "Permita o microfone para dizer \"$phraseDisplay\""
                    !enabled -> "Mãos livres (\"$phraseDisplay\") pausado"
                    wakeWordState.isVerifyingPhrase -> "Confirmando \"$phraseDisplay\"…"
                    wakeWordState.isMonitoring -> "AudioRecord · Diga \"$phraseDisplay\""
                    else -> wakeWordState.statusText
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (wakeWordState.isMonitoring) {
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { wakeWordState.normalizedLevel.coerceIn(0.05f, 1f) },
                        color = accentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            FilterChip(
                selected = enabled && hasMicPermission,
                onClick = onToggle,
                label = {
                    Text(
                        text = if (enabled && hasMicPermission) "Ativo" else "Off",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.testTag("wake_word_handsfree_toggle")
            )
        }
    }
}
