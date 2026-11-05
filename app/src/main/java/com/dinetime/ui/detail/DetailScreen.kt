package com.dinetime.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dinetime.domain.MealItem
import com.dinetime.domain.MealKind
import com.dinetime.domain.MealStatus
import com.dinetime.ui.components.StatusBadge
import com.dinetime.ui.formatFull
import com.dinetime.ui.leadLabel
import com.dinetime.ui.rememberContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailScreen(mealId: String, onBack: () -> Unit, onEdit: (planned: Boolean, id: String) -> Unit) {
    val c = rememberContainer()
    val flow = remember(mealId) { c.repository.observeItem(mealId) }
    val item by flow.collectAsStateWithLifecycle(initialValue = null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        LargeFlexibleTopAppBar(
            title = { Text("Meal") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        val it = item
        if (it == null) {
            Text("This meal is no longer available.", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(it.meal.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                StatusBadge(it.status, it.retryAttempt)
            }
            Text(formatFull(it.meal.scheduledAt), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${it.meal.mealType.name.lowercase().replaceFirstChar { c -> c.uppercase() }} · " +
                    if (it.meal.kind == MealKind.PLANNED) "Planned · ${leadLabel(it.meal.reminderLeadMinutes)}" else "Logged",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (it.meal.notes.isNotBlank()) Text(it.meal.notes)

            Timeline(it)

            it.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Needs your attention (${err.code})", style = MaterialTheme.typography.titleSmall)
                        Text(err.message)
                        err.fields.forEach { (k, v) -> Text("• $k: $v", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (it.canEdit) {
                    Button(onClick = { onEdit(it.meal.kind == MealKind.PLANNED, it.id) }) {
                        Text(if (it.status == MealStatus.ERROR && it.meal.isTemp) "Fix & resubmit" else "Edit")
                    }
                }
                if (it.status == MealStatus.ERROR) {
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) { c.repository.discard(it) }
                        if (it.meal.isTemp) onBack()
                    }) { Text(if (it.deleting) "Dismiss" else "Discard") }
                }
                if (it.canEdit || (it.deleting && it.status == MealStatus.ERROR)) {
                    OutlinedButton(onClick = { confirmDelete = true }) { Text(if (it.meal.isTemp) "Discard" else "Delete") }
                }
            }
            if (!it.canEdit && it.status != MealStatus.ERROR) {
                Text("Syncing — editing is available once this request completes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(if (it.meal.isTemp) "Discard this meal?" else "Delete this meal?") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        scope.launch(Dispatchers.IO) { c.repository.deleteMeal(it) }
                        if (it.meal.isTemp) onBack()
                    }) { Text("Yes") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun Timeline(item: MealItem) {
    val steps = listOf("Pending (saved on device)", "Processing (server acknowledged)", "Active (confirmed)")
    val reached = when (item.status) {
        MealStatus.PENDING -> 0
        MealStatus.PROCESSING -> 1
        MealStatus.ACTIVE -> 2
        MealStatus.ERROR -> -1
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Status", style = MaterialTheme.typography.labelLarge)
        steps.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (i <= reached) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                    tint = if (i <= reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (item.retryAttempt > 0) Text("Server is retrying (attempt ${item.retryAttempt})", style = MaterialTheme.typography.bodySmall)
    }
}
