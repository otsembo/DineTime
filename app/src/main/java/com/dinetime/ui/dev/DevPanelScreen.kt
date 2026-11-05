package com.dinetime.ui.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dinetime.domain.CommandDoc
import com.dinetime.ui.rememberContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevPanelScreen(onBack: () -> Unit) {
    val c = rememberContainer()
    val controls by c.fakeServer.controls.collectAsStateWithLifecycle()
    val status by c.fakeServer.status.collectAsStateWithLifecycle()
    val log by c.fakeServer.log.collectAsStateWithLifecycle()
    val sync by c.replicators.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val commands by remember { c.repository.observeCommands() }.collectAsStateWithLifecycle(initialValue = emptyList<CommandDoc>())

    Scaffold(topBar = {
        LargeFlexibleTopAppBar(title = { Text("Dev panel") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("Connectivity")
            ToggleRow("Replication on (off = go offline)", settings.syncEnabled) { v -> c.settings.update { it.copy(syncEnabled = v) } }
            Text("reads: ${sync.readsActivity} · writes: ${sync.writesActivity}", style = MaterialTheme.typography.bodySmall)
            sync.error?.let {
                Text(
                    "Replicator error: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section("Simulated server")
            ToggleRow("Server running", settings.simulatorEnabled) { v -> c.settings.update { it.copy(simulatorEnabled = v) } }
            status.lastError?.let {
                Text(
                    "Cannot reach Sync Gateway admin API: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Device ${c.settings.deviceId.take(6)} · only commands created on this install are processed",
                style = MaterialTheme.typography.bodySmall,
            )
            if (status.foreignIgnored >
                0
            ) {
                Text(
                    "${status.foreignIgnored} command(s) from other devices ignored",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            ToggleRow("Also process legacy commands (no device id)", settings.processLegacyCommands) { v ->
                c.settings.update {
                    it.copy(processLegacyCommands = v)
                }
            }
            ToggleRow(
                "Pause processing (requests stay PENDING)",
                controls.paused,
            ) { v -> c.fakeServer.updateControls { it.copy(paused = v) } }
            Text("Latency per step: ${controls.latencyMs} ms")
            Slider(
                controls.latencyMs.toFloat(),
                { v -> c.fakeServer.updateControls { it.copy(latencyMs = v.toLong()) } },
                valueRange =
                    0f..8000f,
            )

            Section("Failure injection")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { c.fakeServer.updateControls { it.copy(fail500Next = it.fail500Next + 1) } }) { Text("500 ×1") }
                OutlinedButton(onClick = { c.fakeServer.updateControls { it.copy(fail500Next = it.fail500Next + 3) } }) { Text("500 ×3") }
                Text("queued: ${controls.fail500Next}")
            }
            ToggleRow(
                "Reject next create/update with 400",
                controls.fail400Next,
            ) { v -> c.fakeServer.updateControls { it.copy(fail400Next = v) } }
            ToggleRow("Drop next acknowledgement", controls.dropNextAck) { v -> c.fakeServer.updateControls { it.copy(dropNextAck = v) } }
            ToggleRow("Fail every request with 500 (retries forever)", controls.persist500) { v ->
                c.fakeServer.updateControls {
                    it.copy(persist500 = v)
                }
            }
            ToggleRow(
                "Reject every create/update with 400",
                controls.persist400,
            ) { v -> c.fakeServer.updateControls { it.copy(persist400 = v) } }
            OutlinedButton(onClick = {
                c.fakeServer.updateControls {
                    it.copy(
                        fail500Next = 0,
                        fail400Next = false,
                        dropNextAck = false,
                        persist500 = false,
                        persist400 = false,
                        paused = false,
                    )
                }
            }) { Text("Reset all injections") }
            Text(
                "Natural 400s: blank title, planned time already passed, two meals at the same time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Section("Commands (writes DB)")
            if (commands.isEmpty()) Text("none", style = MaterialTheme.typography.bodySmall)
            commands.take(15).forEach {
                Text(
                    "${it.id.take(
                        6,
                    )} ${it.commandType.name.substringBefore(
                        '_',
                    )} ${it.state}${if (it.attempt > 0) " #${it.attempt}" else ""}${if (it.roundTripDone) " ✓round-trip" else ""}${it.handledBy?.let { h ->
                        " @$h"
                    }.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Section("Server log")
                }
                TextButton(onClick = { c.fakeServer.clearLog() }) { Text("Clear") }
            }
            log.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked, onChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}
