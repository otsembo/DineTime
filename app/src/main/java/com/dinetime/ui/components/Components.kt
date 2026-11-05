package com.dinetime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dinetime.data.couchbase.SyncState
import com.dinetime.domain.MealStatus

@Composable
fun StatusBadge(
    status: MealStatus,
    retryAttempt: Int = 0,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val retrying = retryAttempt > 0 && status != MealStatus.ERROR && status != MealStatus.ACTIVE
    val (label, container, content) =
        when (status) {
            MealStatus.PENDING -> {
                Triple(if (retrying) "Retrying ($retryAttempt)" else "Pending", cs.tertiaryContainer, cs.onTertiaryContainer)
            }

            MealStatus.PROCESSING -> {
                Triple(
                    if (retrying) "Retrying ($retryAttempt)" else "Processing",
                    cs.secondaryContainer,
                    cs.onSecondaryContainer,
                )
            }

            MealStatus.ACTIVE -> {
                Triple("Active", Color(0xFF009688), Color(0xFFFFFFFF))
            }

            MealStatus.ERROR -> {
                Triple("Error", cs.errorContainer, cs.onErrorContainer)
            }
        }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (status) {
                MealStatus.PROCESSING -> {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = content)
                }

                else -> {
                    Icon(
                        when (status) {
                            MealStatus.PENDING -> Icons.Default.Schedule
                            MealStatus.ACTIVE -> Icons.Default.CheckCircle
                            else -> Icons.Default.Error
                        },
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncBar(
    sync: SyncState,
    pendingCount: Int,
    modifier: Modifier = Modifier,
) {
    val offline = !sync.connected
    Surface(
        color = if (offline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!offline && pendingCount > 0) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                Icon(if (offline) Icons.Default.CloudOff else Icons.Default.CloudQueue, null, Modifier.size(16.dp))
            }
            val text =
                when {
                    !sync.enabled -> "Offline mode · changes stay on device"
                    offline -> "Can't reach server · working offline"
                    else -> "Synced · ${sync.writesActivity.lowercase()}"
                }
            Text(
                text + if (pendingCount > 0) " · $pendingCount in flight" else "",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Expressive connected toggle-button group acting as a single-select control. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedToggleGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, o ->
            ToggleButton(
                checked = o == selected,
                onCheckedChange = { onSelect(o) },
                modifier = Modifier.weight(1f),
                shapes =
                    when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(horizontal = 4.dp),
            ) { Text(label(o), maxLines = 1, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
